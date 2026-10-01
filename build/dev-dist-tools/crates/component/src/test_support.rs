//! The manifest entries of the tests in this crate. The helpers that other crates need too are in `testkit`.

use crate::manifest::{ComponentEntry, ComponentManifest, MANIFEST_VERSION};

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
