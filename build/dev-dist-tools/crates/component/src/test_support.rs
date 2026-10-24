//! The manifest entries of the tests in this crate. The helpers that other crates need too are in `testkit`.

use crate::manifest::ComponentEntry;

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

pub(crate) fn directory_entry(relative_path: &str, mode: u32) -> ComponentEntry {
    ComponentEntry::Directory {
        relative_path: relative_path.to_owned(),
        mode,
    }
}
