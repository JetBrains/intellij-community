//! The writer of `local-layout.json` for local launch metadata. [`component::layout`] holds the types and the reader,
//! and the launcher links a local home from the file.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::layout::{
    CORE_CLASSPATH_FILE, FINGERPRINT_FILE, LOCAL_LAYOUT_FILE, LOCAL_LAYOUT_VERSION, LocalFileKind, LocalLayout, LocalLayoutFile,
};
use component::manifest::{self, ComponentEntry, ComponentManifest};
use component::paths;
use component::plugin_classpath::PLUGIN_CLASSPATH;

/// The bytes of the local layout. The caller checked the destinations with `compose::validate_destinations`.
///
/// Every entry lists `path`, `runfile`, `symlinkTarget`, `executable` and `mode`, `null` included, and `kind` only for
/// a directory. The metadata list names the files that the composer writes beside the layout.
pub(crate) fn encode_local_layout(
    components: &[&ComponentManifest],
    source_runfiles: &BTreeMap<PathBuf, String>,
    has_plugin_classpath: bool,
    source_directory_runfiles: Option<&BTreeMap<PathBuf, String>>,
) -> Result<Vec<u8>> {
    let mut files = Vec::new();
    for entry in components.iter().flat_map(|component| &component.entries) {
        let path = entry.relative_path().to_owned();
        files.push(match entry {
            ComponentEntry::Directory { mode, .. } => LocalLayoutFile {
                path,
                mode: Some(*mode),
                kind: Some(LocalFileKind::Directory),
                ..LocalLayoutFile::default()
            },
            ComponentEntry::Symlink { symlink_target, .. } => LocalLayoutFile {
                path,
                symlink_target: Some(symlink_target.clone()),
                ..LocalLayoutFile::default()
            },
            ComponentEntry::ComponentFile {
                source, executable, mode, ..
            } => LocalLayoutFile {
                runfile: Some(resolve_source_runfile(source, source_runfiles, source_directory_runfiles, &path)?),
                path,
                executable: *executable,
                mode: mode.filter(|&mode| mode != manifest::conventional_mode(*executable)),
                ..LocalLayoutFile::default()
            },
        });
    }
    let mut metadata = vec![CORE_CLASSPATH_FILE.to_owned(), FINGERPRINT_FILE.to_owned()];
    if has_plugin_classpath {
        metadata.push(PLUGIN_CLASSPATH.to_owned());
    }
    let layout = LocalLayout {
        version: LOCAL_LAYOUT_VERSION,
        files,
        metadata,
    };
    Ok(serde_json::to_vec(&layout).expect("a layout always encodes"))
}

/// Writes `local-layout.json` into `target`, which must exist.
pub(crate) fn write_local_layout(
    components: &[&ComponentManifest],
    target: &Path,
    source_runfiles: &BTreeMap<PathBuf, String>,
    has_plugin_classpath: bool,
    source_directory_runfiles: Option<&BTreeMap<PathBuf, String>>,
) -> Result<()> {
    let content = encode_local_layout(components, source_runfiles, has_plugin_classpath, source_directory_runfiles)?;
    let file = target.join(LOCAL_LAYOUT_FILE);
    std::fs::write(&file, content).with_context(|| file.display().to_string())
}

/// The runfile of a source: an exact file declaration, or a file inside the deepest declared directory. The keys of
/// both maps are absolute paths. The function looks up each ancestor of the source, the deepest first.
pub(crate) fn resolve_source_runfile(
    source: &str,
    files: &BTreeMap<PathBuf, String>,
    directories: Option<&BTreeMap<PathBuf, String>>,
    name: &str,
) -> Result<String> {
    let Ok(absolute) = paths::absolute_path(source) else {
        bail!("Dev-build component entry '{name}' has an unsafe source: {source}");
    };
    if let Some(exact) = files.get(&absolute) {
        distpath::validate_path(exact)?;
        return Ok(exact.clone());
    }
    let directory = directories.and_then(|directories| {
        absolute
            .ancestors()
            .skip(1)
            .find_map(|ancestor| directories.get(ancestor).map(|runfile| (ancestor, runfile)))
    });
    let Some((directory, runfile)) = directory else {
        bail!("Dev-build component entry '{name}' names an undeclared source: {source}");
    };
    distpath::validate_path(runfile)?;
    let child = absolute
        .strip_prefix(directory)
        .ok()
        .and_then(distpath::slash_path)
        .unwrap_or_default();
    distpath::validate_path(&child)?;
    Ok(format!("{runfile}/{child}"))
}

#[cfg(test)]
mod tests;
