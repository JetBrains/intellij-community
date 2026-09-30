//! The helpers of the collector tests. The collector reads the relative paths of Bazel, so each test runs in a
//! temporary working directory. The working directory belongs to the process, so the tests take it one at a time.
//! The lock also keeps a test thread without a dispatcher from caching the interest "never" for a call site while
//! another test creates its dispatcher. So a test that reaches a span must hold a [`WorkDir`].
//!
//! A test of [`crate::inventory`] or [`crate::plugin_classpath`] uses absolute paths in a [`TempDir`] instead. It holds
//! a [`WorkDir`] only when it gives a relative source.

use std::ffi::OsString;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, MutexGuard};

use filemeta::{Entry, EntryType};
use serde_json::{Value, json};

static WORKING_DIRECTORY: Mutex<()> = Mutex::new(());

/// A temporary working directory for one test. The drop restores the previous working directory.
pub(crate) struct WorkDir {
    previous: PathBuf,
    _directory: tempfile::TempDir,
    _lock: MutexGuard<'static, ()>,
}

impl WorkDir {
    pub(crate) fn new() -> Self {
        let lock = WORKING_DIRECTORY.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        let directory = tempfile::tempdir().unwrap();
        let previous = std::env::current_dir().unwrap();
        std::env::set_current_dir(directory.path()).unwrap();
        Self {
            previous,
            _directory: directory,
            _lock: lock,
        }
    }

    /// Reads a file of the `testdata` directory of this crate. Bazel sets `DDT_TESTDATA_DIR` relative to the working
    /// directory before the test, and `cargo test` sets `CARGO_MANIFEST_DIR`.
    pub(crate) fn read_testdata(&self, name: &str) -> Vec<u8> {
        let directory = std::env::var_os("DDT_TESTDATA_DIR").map_or_else(
            || {
                let crate_directory = std::env::var_os("CARGO_MANIFEST_DIR").expect("cargo test sets CARGO_MANIFEST_DIR");
                PathBuf::from(crate_directory).join("testdata")
            },
            PathBuf::from,
        );
        std::fs::read(self.previous.join(directory).join(name)).unwrap()
    }
}

impl Drop for WorkDir {
    fn drop(&mut self) {
        let _ = std::env::set_current_dir(&self.previous);
    }
}

pub(crate) fn write_file(name: impl AsRef<Path>, data: impl AsRef<[u8]>) {
    let name = name.as_ref();
    if let Some(parent) = name.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        std::fs::create_dir_all(parent).unwrap();
    }
    std::fs::write(name, data).unwrap();
}

pub(crate) fn write_json(name: impl AsRef<Path>, value: &Value) {
    write_file(name, value.to_string());
}

/// The jar records that the packing rule writes for a jar that keeps its own name.
pub(crate) fn write_jar_records(name: &str, sources: &[&str]) {
    let records: Vec<Value> = sources
        .iter()
        .map(|source| json!({"source": source, "relativePath": source.rsplit('/').next().unwrap()}))
        .collect();
    write_json(name, &Value::Array(records));
}

/// Writes an inventory through the writer of the packer, which merges the entries.
pub(crate) fn write_inventory(name: &str, entries: &[Entry]) {
    filemeta::write(Path::new(name), entries).unwrap();
}

/// Writes an inventory as the entries state it, without the checks of the writer.
#[cfg(unix)]
pub(crate) fn write_raw_inventory(name: &str, entries: &[Entry]) {
    let entries: Vec<Value> = entries.iter().map(entry_json).collect();
    write_json(name, &json!({"version": 1, "entries": entries}));
}

#[cfg(unix)]
fn entry_json(entry: &Entry) -> Value {
    let entry_type = match entry.entry_type {
        EntryType::File => "file",
        EntryType::Directory => "directory",
        EntryType::Symlink => "symlink",
    };
    let mut value = json!({"relativePath": entry.relative_path, "type": entry_type});
    let object = value.as_object_mut().unwrap();
    if entry.entry_type != EntryType::Directory {
        object.insert("hash".into(), json!(entry.hash));
    }
    object.insert("size".into(), json!(entry.size));
    object.insert("mode".into(), json!(entry.mode));
    object.insert("executable".into(), json!(entry.executable));
    if !entry.symlink_target.is_empty() {
        object.insert("symlinkTarget".into(), json!(entry.symlink_target));
    }
    value
}

pub(crate) fn file_entry(relative_path: &str, hash: i64, size: u64, mode: u32) -> Entry {
    Entry {
        relative_path: relative_path.into(),
        entry_type: EntryType::File,
        hash,
        size,
        mode,
        executable: mode & 0o111 != 0,
        symlink_target: String::new(),
    }
}

#[cfg(unix)]
pub(crate) fn directory_entry(relative_path: &str, mode: u32) -> Entry {
    Entry {
        relative_path: relative_path.into(),
        entry_type: EntryType::Directory,
        mode,
        ..Entry::default()
    }
}

/// The outcome of one run of the collector.
pub(crate) struct Outcome {
    pub(crate) code: u8,
    pub(crate) output: String,
    pub(crate) errors: String,
}

impl Outcome {
    /// Fails the test unless the run failed with an error that holds `message`.
    #[track_caller]
    pub(crate) fn assert_error(&self, message: &str) {
        assert!(
            self.code != 0 && self.errors.contains(message),
            "exit {}, errors {:?}, expected {message:?}",
            self.code,
            self.errors
        );
    }

    #[track_caller]
    pub(crate) fn assert_success(&self) {
        assert_eq!(self.code, 0, "errors: {}", self.errors);
    }
}

pub(crate) fn run_collector<S: AsRef<str>>(args: &[S]) -> Outcome {
    let args: Vec<OsString> = args.iter().map(|arg| OsString::from(arg.as_ref())).collect();
    let (mut output, mut errors) = (Vec::new(), Vec::new());
    let code = crate::run(args, &mut output, &mut errors);
    Outcome {
        code,
        output: String::from_utf8(output).unwrap(),
        errors: String::from_utf8(errors).unwrap(),
    }
}

pub(crate) fn base_args(mode: &str) -> Vec<String> {
    [
        "--component-manifest=component.json",
        "--kind=files",
        "--platform-prefix=idea",
        "--os=linux",
        "--arch=x64",
        mode,
    ]
    .map(str::to_owned)
    .to_vec()
}

pub(crate) fn read_json(name: &str) -> Value {
    serde_json::from_slice(&std::fs::read(name).unwrap()).unwrap()
}

/// The entries of a component manifest, keyed by their relative path.
pub(crate) fn manifest_entries(name: &str) -> std::collections::BTreeMap<String, Value> {
    read_json(name)["entries"]
        .as_array()
        .unwrap()
        .iter()
        .map(|entry| (entry["relativePath"].as_str().unwrap().to_owned(), entry.clone()))
        .collect()
}

/// The spans of a trace file.
pub(crate) fn read_spans(name: &str) -> Vec<Value> {
    read_json(name)["data"][0]["spans"].as_array().unwrap().clone()
}

pub(crate) fn span_name(span: &Value) -> &str {
    span["operationName"].as_str().unwrap()
}

pub(crate) fn span_tag<'a>(span: &'a Value, name: &str) -> Option<&'a str> {
    span["tags"]
        .as_array()
        .unwrap()
        .iter()
        .find(|tag| tag["key"] == name)
        .and_then(|tag| tag["value"].as_str())
}

pub(crate) fn exists(name: &str) -> bool {
    std::fs::symlink_metadata(name).is_ok()
}

#[cfg(unix)]
pub(crate) fn symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    std::os::unix::fs::symlink(target.as_ref(), link.as_ref()).unwrap();
}

/// A test directory without symbolic links in its path, as `toRealPath` would give it.
pub(crate) struct TempDir {
    _directory: tempfile::TempDir,
    path: PathBuf,
}

impl TempDir {
    pub(crate) fn new() -> Self {
        let directory = tempfile::tempdir().expect("a temporary directory");
        let path = fscopy::resolve_links(directory.path()).expect("a real path");
        Self {
            _directory: directory,
            path,
        }
    }

    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    /// The absolute path of this directory, as text.
    pub(crate) fn root(&self) -> String {
        self.path.to_str().expect("a UTF-8 path").to_owned()
    }

    /// The absolute path of `relative`, a path in slash form, in this directory. The text has native separators.
    pub(crate) fn join(&self, relative: &str) -> String {
        let path = self.path.join(component::paths::from_slash(relative).as_ref());
        path.to_str().expect("a UTF-8 path").to_owned()
    }
}

/// Fails unless `result` is an error whose text contains `message`.
#[track_caller]
pub(crate) fn require_error<T: std::fmt::Debug, E: std::fmt::Display>(result: Result<T, E>, message: &str) {
    match result {
        Ok(value) => panic!("expected an error with {message:?}, got {value:?}"),
        Err(error) => {
            let text = error.to_string();
            assert!(text.contains(message), "error = {text:?}, expected a message with {message:?}");
        }
    }
}

#[cfg(unix)]
pub(crate) fn set_mode(path: impl AsRef<Path>, mode: u32) {
    use std::os::unix::fs::PermissionsExt;
    std::fs::set_permissions(path.as_ref(), std::fs::Permissions::from_mode(mode)).expect("the mode");
}

/// The bytes of the reference vectors of the Kotlin content hash.
#[expect(clippy::cast_possible_truncation, reason = "the vector keeps the low byte of each value")]
pub(crate) fn reference_bytes(size: usize) -> Vec<u8> {
    (0..size).map(|index| (index.wrapping_mul(31).wrapping_add(7)) as u8).collect()
}
