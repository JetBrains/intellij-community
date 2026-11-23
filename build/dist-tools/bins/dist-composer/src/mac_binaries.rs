//! The Mach-O files of a composed home: the record that the macOS signing of an installer reads.

use std::fs;
use std::path::Path;

use anyhow::{Context as _, Result};
use component::manifest::{ComponentEntry, ComponentManifest};
use filemeta::macho::MacBinary;
use serde::Serialize;

/// The version of the file that [`write_mac_binaries`] writes.
const VERSION: i32 = 1;

#[derive(Serialize)]
struct Document<'a> {
    version: i32,
    files: Vec<MacBinaryFile<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct MacBinaryFile<'a> {
    relative_path: &'a str,
    kind: MacBinary,
}

/// Returns the files of the components with a [`MacBinary`] kind, sorted by their destination below the home.
///
/// Only a composition whose platform component has the `os` `mac` lists a file. The platform component is the first
/// component that is not platform-neutral, as for the platform check of the composition. Every other composition gives
/// an empty list, because the installer of another OS signs no Mach-O file.
pub(crate) fn mac_binaries<'a>(manifests: &[&'a ComponentManifest]) -> Vec<(&'a str, MacBinary)> {
    let mac = manifests
        .iter()
        .find(|manifest| !manifest.platform_neutral())
        .is_some_and(|manifest| manifest.os == "mac");
    if !mac {
        return Vec::new();
    }
    let mut files: Vec<(&str, MacBinary)> = manifests
        .iter()
        .flat_map(|manifest| &manifest.entries)
        .filter_map(|entry| match entry {
            ComponentEntry::ComponentFile {
                relative_path,
                mac_binary: Some(kind),
                ..
            } => Some((relative_path.as_str(), *kind)),
            _ => None,
        })
        .collect();
    files.sort_unstable_by(|first, second| first.0.cmp(second.0));
    files.dedup();
    files
}

/// Writes `{"version":1,"files":[{"relativePath":...,"kind":...}]}` of [`mac_binaries`] to `path`, with a two-space
/// indent and no trailing newline, as a component manifest.
pub(crate) fn write_mac_binaries(path: &Path, manifests: &[&ComponentManifest]) -> Result<()> {
    let document = Document {
        version: VERSION,
        files: mac_binaries(manifests)
            .into_iter()
            .map(|(relative_path, kind)| MacBinaryFile { relative_path, kind })
            .collect(),
    };
    let data = serde_json::to_vec_pretty(&document).expect("the list always encodes");
    fs::write(path, data).with_context(|| path.display().to_string())
}

#[cfg(test)]
mod tests;
