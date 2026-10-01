//! Helpers of the tests in this crate. A test never changes the working directory, so every path is absolute.

use std::fs;
use std::path::{Path, PathBuf};

use crate::manifest::{ComponentEntry, ComponentManifest, MANIFEST_VERSION};

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
}

pub(crate) fn test_manifest(kind: &str) -> ComponentManifest {
    ComponentManifest {
        version: MANIFEST_VERSION,
        kind: kind.to_owned(),
        platform_prefix: "idea".to_owned(),
        os: "linux".to_owned(),
        arch: "x64".to_owned(),
        main_class: Some("com.intellij.idea.Main".to_owned()),
        ..ComponentManifest::default()
    }
}

/// A file entry with the hash 1 and a source below `inputs/`.
pub(crate) fn file_entry(relative_path: &str) -> ComponentEntry {
    file_with_mode(relative_path, false, None)
}

pub(crate) fn file_with_mode(relative_path: &str, executable: bool, mode: Option<u32>) -> ComponentEntry {
    ComponentEntry::ComponentFile {
        relative_path: relative_path.to_owned(),
        hash: 1,
        executable,
        source: format!("inputs/{relative_path}"),
        mode,
    }
}

/// A link entry with the hash that the collector writes for its target.
pub(crate) fn link_entry(relative_path: &str, target: &str) -> ComponentEntry {
    ComponentEntry::Symlink {
        relative_path: relative_path.to_owned(),
        hash: filemeta::hash_symlink_target(target),
        symlink_target: target.to_owned(),
    }
}

pub(crate) fn directory_entry(relative_path: &str, mode: u32) -> ComponentEntry {
    ComponentEntry::Directory {
        relative_path: relative_path.to_owned(),
        mode,
    }
}

pub(crate) fn write_file(path: impl AsRef<Path>, content: impl AsRef<[u8]>) {
    let path = path.as_ref();
    fs::create_dir_all(path.parent().expect("a parent")).expect("the parent directory");
    fs::write(path, content).expect("the file");
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
