//! The contract of one plugin remainder: the recipe that the packer executes, the asset rows, and the input catalogue.
//!
//! The recipe stays in the process, so it has no JSON form. The asset rows go to `assets.json`, and Starlark writes the
//! catalogue. The asset rows keep the field order and the omitted defaults of the Go `json` tags, so
//! `serde_json::to_vec` writes the bytes of Go `json.Marshal`. Go also escapes `<`, `>`, `&`, U+2028 and U+2029, and no
//! plan file holds one of them.

use serde::{Deserialize, Serialize};

/// The execution version of a plan with files only.
pub const VERSION: u32 = 1;
/// The execution version of a plan with a tree. The native tree of a reused natives jar is such a tree.
pub const TREE_VERSION: u32 = 2;

/// The remainder recipe of one plugin. It keeps the complete asset order and the producer of each asset. It holds the
/// operations of the remainder assets in plan order.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Recipe {
    pub version: u32,
    pub plugin: String,
    pub layout_signature: String,
    pub assets: Vec<Asset>,
    pub operations: Vec<Operation>,
}

/// One row of `assets.json`. The producer is `remainder` or `independent`. An independent asset names the module of
/// its reused jar as the artifact. An empty kind is `file`. Every asset is below the plugin directory.
#[derive(Serialize, Deserialize, Clone, Debug, Default, PartialEq, Eq)]
#[serde(default, deny_unknown_fields, rename_all = "camelCase")]
pub struct Asset {
    pub destination: String,
    pub producer: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub artifact: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub kind: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub class_path: Option<bool>,
}

/// The input catalogue that the Starlark rule writes. Only this document holds file system roots. Its artifacts are the
/// complete remainder input set in execution order.
#[derive(Deserialize, Clone, Debug, Default, PartialEq, Eq)]
#[serde(default, deny_unknown_fields)]
pub struct Catalogue {
    pub version: u32,
    pub artifacts: Vec<Artifact>,
    /// The libraries that [`crate::derive`] expands into their files. The derived catalogue holds none.
    pub libraries: Vec<Library>,
}

/// One declared input. The kind is `file` or `directory`.
#[derive(Deserialize, Clone, Debug, Default, PartialEq, Eq)]
#[serde(default, deny_unknown_fields)]
pub struct Artifact {
    pub id: String,
    pub kind: String,
    pub root: String,
}

/// One library and its member files in their expansion order.
#[derive(Deserialize, Clone, Debug, Default, PartialEq, Eq)]
#[serde(default, deny_unknown_fields)]
pub struct Library {
    pub id: String,
    pub files: Vec<Reference>,
}

/// Identifies a declared file, or one relative file inside a declared directory.
#[derive(Deserialize, Clone, Debug, Default, PartialEq, Eq, Hash)]
#[serde(default, deny_unknown_fields)]
pub struct Reference {
    pub artifact: String,
    pub path: String,
}

impl Reference {
    /// The reference of a whole artifact.
    pub fn artifact(artifact: impl Into<String>) -> Self {
        Self {
            artifact: artifact.into(),
            path: String::new(),
        }
    }
}

/// One remainder operation. It writes one asset at its destination in the plugin directory.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Operation {
    /// Packs the sources into one jar.
    Jar {
        destination: String,
        mode: u32,
        sources: Vec<Source>,
        merge_entities: bool,
    },
    /// Copies one declared file.
    Copy { destination: String, mode: u32, input: Reference },
    /// Copies one declared directory with its source modes.
    CopyTree { destination: String, input: Reference },
    /// Writes the layout assets under the destination, with their source modes.
    LayoutTree { destination: String, layout: LayoutAssets },
}

impl Operation {
    pub fn destination(&self) -> &str {
        match self {
            Self::Jar { destination, .. }
            | Self::Copy { destination, .. }
            | Self::CopyTree { destination, .. }
            | Self::LayoutTree { destination, .. } => destination,
        }
    }
}

/// One jar source.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Source {
    /// The entries of one archive through the filter.
    Archive {
        input: Reference,
        filter: Filter,
        manifest: Manifest,
    },
    /// One file at the entry name. The jar writer patches it.
    Patch {
        entry: String,
        input: Reference,
        manifest: Manifest,
    },
    /// The entries that the layout assets write. The source keeps their manifests.
    Layout(LayoutAssets),
}

/// The entry filter of an archive source.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Filter {
    Module,
    Library,
}

/// The manifest policy of one jar source.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Manifest {
    Keep,
    Drop,
}

/// The `layoutAssets` payload of a plan file with its inputs turned into catalogue references. The assets are written
/// in their order, and the first claim of a destination wins.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct LayoutAssets {
    pub inputs: Vec<Reference>,
    pub assets: Vec<LayoutAsset>,
}

/// Writes one file or one tree under the destination. The sources index [`LayoutAssets::inputs`]. No transform is a
/// plain copy of one file, link or directory. An empty destination is the output root. Mode zero keeps the source mode.
/// A declared mode of a directory copy sets its regular files, and its directories get 0755.
#[derive(Deserialize, Clone, Debug, Default, PartialEq, Eq)]
#[serde(default, deny_unknown_fields)]
pub struct LayoutAsset {
    pub destination: String,
    pub sources: Vec<usize>,
    pub transform: Option<LayoutTransform>,
    pub mode: u32,
}

/// One layout transform. The Kotlin `DevPluginLayoutAssetTransform` states the rules of each field, and the executor
/// applies them. A tree needs no transform, because a plain copy places it.
#[derive(Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
pub struct LayoutTransform {
    pub kind: LayoutTransformKind,
    #[serde(default)]
    pub strip_components: u32,
    #[serde(default)]
    pub mappings: Vec<LayoutMapping>,
    #[serde(default)]
    pub includes: Vec<String>,
    #[serde(default)]
    pub executables: Vec<String>,
}

#[derive(Deserialize, Clone, Copy, Debug, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub enum LayoutTransformKind {
    ArchiveTree,
}

/// Selects entries by a java.nio glob over the whole relative path. An empty pattern is `**`.
#[derive(Deserialize, Clone, Debug, Default, PartialEq, Eq)]
#[serde(default, deny_unknown_fields, rename_all = "camelCase")]
pub struct LayoutMapping {
    pub pattern: String,
    pub strip_components: u32,
    pub destination: String,
}

#[cfg(test)]
mod tests;
