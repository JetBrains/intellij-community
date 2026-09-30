//! Helpers of the tests in this crate. A test never changes the working directory, so every path is absolute.

use std::fs;
use std::path::{Path, PathBuf};

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

pub(crate) fn file_entry(relative_path: &str) -> ComponentEntry {
    ComponentEntry {
        relative_path: relative_path.to_owned(),
        entry_type: ComponentEntryType::ComponentFile,
        hash: Some(1),
        ..ComponentEntry::default()
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
