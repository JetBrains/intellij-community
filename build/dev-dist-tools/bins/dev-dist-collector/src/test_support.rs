//! The fakes of the collector tests. The collector reads the relative paths of Bazel, so a test of the command line
//! runs in a [`testkit::WorkingDirectory`]. A test of [`crate::inventory`] or [`crate::plugin_classpath`] uses absolute
//! paths in a [`testkit::TempDir`] instead. It holds a working directory only when it gives a relative source.

use std::ffi::OsString;
use std::path::Path;

use filemeta::{Entry, EntryType};
use serde_json::{Value, json};
use testkit::write_file;

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
