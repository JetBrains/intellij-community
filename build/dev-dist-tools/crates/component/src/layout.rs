//! `local-layout.json`: the file that names the runfile of each distribution file instead of a copy. The composer
//! writes it for local launch metadata, and [`crate::local_home`] reads it.

use std::collections::BTreeMap;
use std::path::Path;

use serde::{Deserialize, Serialize};

use crate::compose::validate_destinations;
use crate::error::{Error, Result};
use crate::fail;
use crate::manifest::{ComponentEntryType, ComponentManifest};
use crate::paths;
use crate::plugin_classpath::PLUGIN_CLASSPATH;

/// The name of the layout file in the metadata tree.
pub const LOCAL_LAYOUT_FILE: &str = "local-layout.json";
/// The only layout version.
pub const LOCAL_LAYOUT_VERSION: u32 = 1;
/// The core classpath file that the composer writes beside the layout.
pub const CORE_CLASSPATH_FILE: &str = "core-classpath.txt";
/// The fingerprint file that the composer writes beside the layout.
pub const FINGERPRINT_FILE: &str = "fingerprint.txt";

/// The layout. `metadata` names the files that the composer writes beside the layout.
///
/// The field order and the `null` values are the bytes of the Kotlin writer, and serde_json writes the same compact
/// form.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct LocalLayout {
    pub version: u32,
    pub files: Vec<LocalLayoutFile>,
    pub metadata: Vec<String>,
}

/// One file of the layout: a runfile, a symbolic link, or a directory. `mode` is an exact mode or `None` for the
/// conventional mode of a file.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct LocalLayoutFile {
    pub path: String,
    pub runfile: Option<String>,
    pub symlink_target: Option<String>,
    pub executable: bool,
    pub mode: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub kind: Option<LocalFileKind>,
}

/// The kind of a layout file. The composer writes it only for a directory.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LocalFileKind {
    Directory,
}

impl LocalLayoutFile {
    pub fn is_directory(&self) -> bool {
        self.kind == Some(LocalFileKind::Directory)
    }
}

/// The bytes of the local layout.
///
/// Every entry lists `path`, `runfile`, `symlinkTarget`, `executable` and `mode`, `null` included, and `kind` only for
/// a directory. The metadata list names the files that the composer writes beside the layout.
pub fn encode_local_layout(
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
pub fn write_local_layout(
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
pub fn resolve_source_runfile(
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
