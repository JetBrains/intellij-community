//! Helpers of the tests in this crate. A test never changes the working directory, so every path is absolute.

#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use std::fs;
use std::path::{Path, PathBuf};

use std::collections::BTreeMap;

use crate::manifest::{ComponentEntry, ComponentEntryType, ComponentManifest, MANIFEST_VERSION};

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
        let path = self.path.join(crate::paths::from_slash(relative).as_ref());
        path.to_str().expect("a UTF-8 path").to_owned()
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

/// A link entry with the hash that the collector writes for its target.
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

pub(crate) fn write_file(path: impl AsRef<Path>, content: impl AsRef<[u8]>) {
    let path = path.as_ref();
    fs::create_dir_all(path.parent().expect("a parent")).expect("the parent directory");
    fs::write(path, content).expect("the file");
}

pub(crate) fn read_text(path: impl AsRef<Path>) -> String {
    fs::read_to_string(path.as_ref()).unwrap_or_else(|error| panic!("{}: {error}", path.as_ref().display()))
}

pub(crate) fn require_absent(path: impl AsRef<Path>) {
    assert!(fs::symlink_metadata(path.as_ref()).is_err(), "{} exists", path.as_ref().display());
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
    fs::set_permissions(path.as_ref(), fs::Permissions::from_mode(mode)).expect("the mode");
}

#[cfg(unix)]
pub(crate) fn symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    fscopy::symlink(target.as_ref(), link.as_ref(), false).expect("the link");
}

/// The bytes of the reference vectors of the Kotlin content hash.
pub(crate) fn reference_bytes(size: usize) -> Vec<u8> {
    (0..size).map(|index| (index.wrapping_mul(31).wrapping_add(7)) as u8).collect()
}

/// A merge step that must not run, for launch metadata and for the checks before the first write.
pub(crate) fn no_merge(_: &[crate::compose::DevBuildComponent], _: &Path) -> crate::Result<()> {
    panic!("the composition merged component files")
}

/// A merge step that copies nothing. The copy of the component files belongs to the composer binary.
#[expect(clippy::unnecessary_wraps, reason = "the signature of a merge step")]
pub(crate) fn skip_merge(_: &[crate::compose::DevBuildComponent], _: &Path) -> crate::Result<()> {
    Ok(())
}

pub(crate) fn runfiles(pairs: &[(&str, &str)]) -> BTreeMap<String, String> {
    pairs.iter().map(|(key, value)| ((*key).to_owned(), (*value).to_owned())).collect()
}

/// The bytes of a file in `testdata/`. Bazel names the directory in `DDT_TESTDATA_DIR`, and Cargo gives the run-time
/// `CARGO_MANIFEST_DIR`.
pub(crate) fn testdata(name: &str) -> Vec<u8> {
    let directory = std::env::var_os("DDT_TESTDATA_DIR").map_or_else(
        || PathBuf::from(std::env::var_os("CARGO_MANIFEST_DIR").expect("Cargo or Bazel names the test data")).join("testdata"),
        PathBuf::from,
    );
    let file = directory.join(name);
    fs::read(&file).unwrap_or_else(|error| panic!("{}: {error}", file.display()))
}
