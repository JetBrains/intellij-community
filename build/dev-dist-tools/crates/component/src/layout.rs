//! `local-layout.json`: the file that names the runfile of each distribution file instead of a copy. The composer
//! writes it for local launch metadata. The generation writer and the updater read it with [`read_layout`].

use std::fs;
use std::path::Path;

use anyhow::{Context as _, Result, bail};
use serde::{Deserialize, Serialize};

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
/// conventional mode of a file. `hash` is the content hash of a runfile, the `hash` of its component entry, so a launch
/// keys the file without reading it.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct LocalLayoutFile {
    pub path: String,
    pub runfile: Option<String>,
    pub symlink_target: Option<String>,
    pub executable: bool,
    pub mode: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub hash: Option<i64>,
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

/// Reads a layout file. A layout of another version fails with its version.
pub fn read_layout(path: &Path) -> Result<LocalLayout> {
    let text = fs::read_to_string(path).with_context(|| format!("cannot read {}", path.display()))?;
    let layout: LocalLayout = serde_json::from_str(&text).with_context(|| format!("{} is not a local layout", path.display()))?;
    if layout.version != LOCAL_LAYOUT_VERSION {
        bail!("{} has the version {}, not {LOCAL_LAYOUT_VERSION}", path.display(), layout.version);
    }
    Ok(layout)
}

#[cfg(test)]
mod tests;
