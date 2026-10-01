//! `local-layout.json`: the file that names the runfile of each distribution file instead of a copy. The composer
//! writes it for local launch metadata, and the launcher reads it with [`read_local_layout`].

use std::path::Path;

use anyhow::Result;
use serde::{Deserialize, Serialize};

use crate::json;

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

/// Reads a local layout. The error names the file. The caller checks the version.
pub fn read_local_layout(path: &Path) -> Result<LocalLayout> {
    json::read(path)
}
