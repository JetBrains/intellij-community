//! The writer of `local-layout.json` for local launch metadata. [`component::layout`] holds the types and the reader,
//! and the launcher links a local home from the file.

use std::collections::BTreeMap;
use std::path::Path;

use component::layout::{
    CORE_CLASSPATH_FILE, FINGERPRINT_FILE, LOCAL_LAYOUT_FILE, LOCAL_LAYOUT_VERSION, LocalFileKind, LocalLayout, LocalLayoutFile,
};
use component::manifest::{ComponentEntryType, ComponentManifest};
use component::plugin_classpath::PLUGIN_CLASSPATH;
use component::{Error, Result, fail, paths};

use crate::compose::validate_destinations;

/// The bytes of the local layout.
///
/// Every entry lists `path`, `runfile`, `symlinkTarget`, `executable` and `mode`, `null` included, and `kind` only for
/// a directory. The metadata list names the files that the composer writes beside the layout.
pub(crate) fn encode_local_layout(
    components: &[&ComponentManifest],
    source_runfiles: &BTreeMap<String, String>,
    has_plugin_classpath: bool,
    source_directory_runfiles: Option<&BTreeMap<String, String>>,
) -> Result<Vec<u8>> {
    validate_destinations(components)?;
    let mut files = Vec::new();
    for entry in components.iter().flat_map(|component| &component.entries) {
        let name = entry.relative_path.as_str();
        let mut runfile = None;
        match entry.entry_type {
            ComponentEntryType::Directory => {}
            ComponentEntryType::Symlink => {
                if entry.source.is_some() {
                    fail!("Dev-build component must declare the symbolic link '{name}' without a file source");
                }
            }
            ComponentEntryType::ComponentFile => {
                let Some(source) = &entry.source else {
                    fail!("Dev-build component entry '{name}' has no source");
                };
                runfile = Some(resolve_source_runfile(source, source_runfiles, source_directory_runfiles, name)?);
            }
        }
        let directory = entry.entry_type == ComponentEntryType::Directory;
        files.push(LocalLayoutFile {
            path: name.to_owned(),
            runfile,
            symlink_target: entry.symlink_target.clone(),
            executable: entry.executable,
            mode: entry
                .mode
                .filter(|&mode| directory || mode != fscopy::conventional_mode(entry.executable)),
            kind: directory.then_some(LocalFileKind::Directory),
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
    source_runfiles: &BTreeMap<String, String>,
    has_plugin_classpath: bool,
    source_directory_runfiles: Option<&BTreeMap<String, String>>,
) -> Result<()> {
    let content = encode_local_layout(components, source_runfiles, has_plugin_classpath, source_directory_runfiles)?;
    let file = target.join(LOCAL_LAYOUT_FILE);
    std::fs::write(&file, content).map_err(|error| Error::io(&file, error))
}

/// The runfile of a source: an exact file declaration, or a file inside the deepest declared directory. The keys of
/// both maps are absolute paths.
pub(crate) fn resolve_source_runfile(
    source: &str,
    files: &BTreeMap<String, String>,
    directories: Option<&BTreeMap<String, String>>,
    name: &str,
) -> Result<String> {
    let Ok(absolute) = paths::absolute_path(source) else {
        fail!("Dev-build component entry '{name}' has an unsafe source: {source}");
    };
    if let Some(exact) = files.get(&absolute) {
        distpath::validate_path(exact).map_err(Error::msg)?;
        return Ok(exact.clone());
    }
    let directory = directories
        .into_iter()
        .flatten()
        .filter(|(candidate, _)| absolute != **candidate && Path::new(&absolute).starts_with(candidate.as_str()));
    let Some((directory, runfile)) = directory.max_by_key(|(candidate, _)| candidate.len()) else {
        fail!("Dev-build component entry '{name}' names an undeclared source: {source}");
    };
    distpath::validate_path(runfile).map_err(Error::msg)?;
    let child = paths::to_slash(absolute[directory.len()..].trim_start_matches(paths::SEPARATOR));
    distpath::validate_path(&child).map_err(Error::msg)?;
    Ok(format!("{runfile}/{child}"))
}

#[cfg(test)]
mod tests;
