//! Helpers of the composer tests. Only the tests of the command line change the working directory. They hold
//! [`WorkingDirectory`], and every other test uses absolute paths.

use std::collections::{BTreeMap, HashSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, MutexGuard};

use component::manifest::{ComponentEntry, ComponentEntryType, ComponentManifest, MANIFEST_VERSION};
use component::paths;

use crate::compose::{self, ComposeOptions, ComposedBuild, DevBuildComponent};
use crate::spec::{self, ComponentSources, CompositionComponent};

/// A test directory whose path has no symbolic link.
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
        let path = self.path.join(paths::from_slash(relative).as_ref());
        path.to_str().expect("a UTF-8 path").to_owned()
    }
}

static WORKING_DIRECTORY_LOCK: Mutex<()> = Mutex::new(());

/// A fresh working directory for one test. The previous one comes back when the value drops.
pub(crate) struct WorkingDirectory {
    previous: PathBuf,
    directory: TempDir,
    _lock: MutexGuard<'static, ()>,
}

impl WorkingDirectory {
    pub(crate) fn enter() -> Self {
        let lock = WORKING_DIRECTORY_LOCK.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        let previous = std::env::current_dir().expect("the working directory");
        let directory = TempDir::new();
        std::env::set_current_dir(directory.path()).expect("a new working directory");
        Self {
            previous,
            directory,
            _lock: lock,
        }
    }

    pub(crate) fn path(&self) -> &Path {
        self.directory.path()
    }
}

impl Drop for WorkingDirectory {
    fn drop(&mut self) {
        let _ = std::env::set_current_dir(&self.previous);
    }
}

pub(crate) fn test_manifest(kind: &str) -> ComponentManifest {
    ComponentManifest {
        version: Some(MANIFEST_VERSION),
        kind: kind.to_owned(),
        platform_prefix: "idea".to_owned(),
        os: "linux".to_owned(),
        arch: "x64".to_owned(),
        main_class: Some("com.intellij.idea.Main".to_owned()),
        ..ComponentManifest::default()
    }
}

pub(crate) fn with_entries(mut manifest: ComponentManifest, entries: Vec<ComponentEntry>) -> ComponentManifest {
    manifest.entries = entries;
    manifest
}

pub(crate) fn file_entry(relative_path: &str) -> ComponentEntry {
    ComponentEntry {
        relative_path: relative_path.to_owned(),
        entry_type: ComponentEntryType::ComponentFile,
        hash: Some(1),
        ..ComponentEntry::default()
    }
}

pub(crate) fn sourced_entry(relative_path: &str, source: &str) -> ComponentEntry {
    ComponentEntry {
        source: Some(source.to_owned()),
        ..file_entry(relative_path)
    }
}

pub(crate) fn link_entry(relative_path: &str, target: &str) -> ComponentEntry {
    ComponentEntry {
        relative_path: relative_path.to_owned(),
        entry_type: ComponentEntryType::Symlink,
        hash: Some(filemeta::hash_symlink_target(target)),
        symlink_target: Some(target.to_owned()),
        ..ComponentEntry::default()
    }
}

pub(crate) fn directory_entry(relative_path: &str, mode: u32) -> ComponentEntry {
    ComponentEntry {
        relative_path: relative_path.to_owned(),
        entry_type: ComponentEntryType::Directory,
        mode: Some(mode),
        ..ComponentEntry::default()
    }
}

/// A component whose sources are bound as file artifacts, as the Starlark rule binds a full distribution.
///
/// Bazel stages only the declared inputs, so the bindings hold only the sources that exist and are valid host paths.
pub(crate) fn bound(directory: &TempDir, manifest: ComponentManifest) -> DevBuildComponent {
    let sources: Vec<&str> = manifest
        .entries
        .iter()
        .filter_map(|entry| entry.source.as_deref())
        .filter(|source| paths::host_path(source).is_ok() && Path::new(source).exists())
        .collect();
    let bindings = bind_files(directory, &manifest.kind, &sources);
    DevBuildComponent {
        source_bindings: Some(bindings),
        ..DevBuildComponent::new(manifest)
    }
}

/// A component with empty source bindings, for a component with links and directories only.
pub(crate) fn unbound_files(manifest: ComponentManifest) -> DevBuildComponent {
    DevBuildComponent {
        source_bindings: Some(ComponentSources::default()),
        ..DevBuildComponent::new(manifest)
    }
}

/// Binds each source as a file artifact. The bindings file is in `directory`, and each source must be below it.
pub(crate) fn bind_files(directory: &TempDir, component: &str, sources: &[&str]) -> ComponentSources {
    let file = directory.path().join(format!("{component}.source-bindings.jsonl"));
    let mut lines = String::new();
    let mut known = HashSet::new();
    for source in sources.iter().filter(|source| known.insert(**source)) {
        let relative = Path::new(source)
            .strip_prefix(directory.path())
            .expect("a source in the test directory");
        let relative = paths::to_slash(relative.to_str().expect("a UTF-8 path")).into_owned();
        lines.push_str(&binding_line(component, source, &relative, "file", &[]));
        lines.push('\n');
    }
    write_file(&file, lines);
    read_bindings(&file, component)
}

/// One line of the source bindings file, as `_add_source_bindings` in `intellij_dev_dist.bzl` writes it.
pub(crate) fn binding_line(component: &str, source: &str, anchor_relative: &str, kind: &str, members: &[&str]) -> String {
    format!(
        r#"{{"component":{},"source":{},"anchorRelativePath":{},"type":{},"members":{}}}"#,
        json(component),
        json(source),
        json(anchor_relative),
        json(kind),
        serde_json::to_string(members).expect("JSON")
    )
}

pub(crate) fn read_bindings(file: &Path, component: &str) -> ComponentSources {
    let components = [CompositionComponent {
        manifest: component.to_owned(),
        plugin_classpath_part: None,
    }];
    let mut bindings = spec::read_source_bindings(file.to_str().expect("a UTF-8 path"), &components).expect("the source bindings");
    bindings.remove(component).expect("the bindings of the component")
}

pub(crate) fn json(value: &str) -> String {
    serde_json::to_string(value).expect("JSON")
}

/// A tree artifact that Bazel stages in a sandbox: each staged member links to the physical output, and the bindings
/// file describes the tree.
pub(crate) struct BoundTree {
    pub(crate) physical: PathBuf,
    pub(crate) staged: PathBuf,
    pub(crate) bindings: ComponentSources,
}

impl BoundTree {
    pub(crate) fn new(directory: &Path, members: &[&str]) -> Self {
        let physical = directory.join("physical/trees/plugin");
        let staged = directory.join("sandbox/trees/plugin");
        for tree in [&physical, &staged] {
            fs::create_dir_all(tree.join("lib")).expect("the tree");
        }
        write_file(physical.join("lib/native.jar"), "native bytes");
        file_symlink(physical.join("lib/native.jar"), staged.join("lib/native.jar"));
        let physical_metadata = directory.join("physical/metadata/bindings.jsonl");
        let staged_metadata = directory.join("sandbox/metadata/bindings.jsonl");
        let line = binding_line(
            "plugin",
            staged.to_str().expect("a UTF-8 path"),
            "../trees/plugin",
            "directory",
            members,
        );
        write_file(&physical_metadata, line);
        fs::create_dir_all(staged_metadata.parent().expect("a parent")).expect("the directory");
        file_symlink(&physical_metadata, &staged_metadata);
        let bindings = read_bindings(&staged_metadata, "plugin");
        Self {
            physical,
            staged,
            bindings,
        }
    }

    pub(crate) fn staged(&self, member: &str) -> String {
        self.staged.join(member).to_str().expect("a UTF-8 path").to_owned()
    }
}

/// Composes a full distribution with the merge step of the composer.
pub(crate) fn compose(components: &[DevBuildComponent], target: impl AsRef<Path>) -> anyhow::Result<ComposedBuild> {
    compose_with(components, target, ComposeOptions::default())
}

/// Composes under a tracer that records, as `main` does with `--trace-file`, so each test also runs the span calls.
#[expect(clippy::needless_pass_by_value, reason = "the tests build the options inline")]
pub(crate) fn compose_with(
    components: &[DevBuildComponent],
    target: impl AsRef<Path>,
    options: ComposeOptions,
) -> anyhow::Result<ComposedBuild> {
    let root = trace::Tracer::new("dev-dist-composer test").span(crate::JOB_NAME);
    compose::compose_components(components, target.as_ref(), &options, &root)
}

pub(crate) fn with_directory_runfiles(directory: impl AsRef<Path>, runfile: &str) -> ComposeOptions {
    let runfiles = BTreeMap::from([(directory.as_ref().to_str().expect("a UTF-8 path").to_owned(), runfile.to_owned())]);
    ComposeOptions {
        source_directory_runfiles: Some(runfiles),
        ..ComposeOptions::default()
    }
}

pub(crate) fn write_file(path: impl AsRef<Path>, content: impl AsRef<[u8]>) {
    let path = path.as_ref();
    if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        fs::create_dir_all(parent).expect("the parent directory");
    }
    fs::write(path, content).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
}

pub(crate) fn read_text(path: impl AsRef<Path>) -> String {
    let path = path.as_ref();
    fs::read_to_string(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

#[track_caller]
pub(crate) fn require_absent(path: impl AsRef<Path>) {
    let path = path.as_ref();
    assert!(fs::symlink_metadata(path).is_err(), "{} exists", path.display());
}

/// Fails unless `result` is an error whose text contains `message`.
#[track_caller]
pub(crate) fn require_error<T: std::fmt::Debug, E: std::fmt::Display>(result: Result<T, E>, message: &str) {
    match result {
        Ok(value) => panic!("expected an error with {message:?}, got {value:?}"),
        Err(error) => {
            let text = format!("{error:#}");
            assert!(text.contains(message), "error = {text:?}, expected a message with {message:?}");
        }
    }
}

#[track_caller]
pub(crate) fn require_link(path: impl AsRef<Path>, expected: &str) {
    let path = path.as_ref();
    let target = fs::read_link(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    let target = target.to_str().expect("a UTF-8 target");
    assert_eq!(paths::to_slash(target), expected, "the link {}", path.display());
}

/// Checks the permission bits of a file. The check does nothing on Windows, because the composer sets no bits there.
#[track_caller]
pub(crate) fn require_mode(path: impl AsRef<Path>, expected: u32) {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        let path = path.as_ref();
        let mode = fs::metadata(path).expect("the metadata").permissions().mode() & 0o7777;
        assert_eq!(mode, expected, "the mode of {} is {mode:o}", path.display());
    }
    #[cfg(not(unix))]
    let _ = (path, expected);
}

/// Sets the permission bits of a file. It does nothing on Windows.
pub(crate) fn set_mode(path: impl AsRef<Path>, mode: u32) {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        fs::set_permissions(path.as_ref(), fs::Permissions::from_mode(mode)).expect("the mode");
    }
    #[cfg(not(unix))]
    let _ = (path, mode);
}

/// Creates a symbolic link to a file.
pub(crate) fn file_symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    create_symlink(target.as_ref(), link.as_ref(), false);
}

/// Creates a symbolic link to a directory.
pub(crate) fn directory_symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    create_symlink(target.as_ref(), link.as_ref(), true);
}

fn create_symlink(target: &Path, link: &Path, target_is_directory: bool) {
    fscopy::symlink(target, link, target_is_directory).unwrap_or_else(|error| panic!("{error}"));
}

/// The bytes of the reference vectors of the Kotlin content hash.
#[expect(clippy::cast_possible_truncation, reason = "the vector keeps the low byte of each value")]
pub(crate) fn reference_bytes(size: usize) -> Vec<u8> {
    (0..size).map(|index| (index.wrapping_mul(31).wrapping_add(7)) as u8).collect()
}

/// A copy step that must not run, for launch metadata and for the checks before the first write.
pub(crate) fn no_merge(_: &[DevBuildComponent], _: &Path) -> anyhow::Result<()> {
    panic!("the composition merged component files")
}

/// A copy step that copies nothing, for a test of the metadata of a full distribution without payload.
#[expect(clippy::unnecessary_wraps, reason = "the signature of a copy step")]
pub(crate) fn skip_merge(_: &[DevBuildComponent], _: &Path) -> anyhow::Result<()> {
    Ok(())
}

pub(crate) fn runfiles(pairs: &[(&str, &str)]) -> BTreeMap<String, String> {
    pairs.iter().map(|(key, value)| ((*key).to_owned(), (*value).to_owned())).collect()
}
