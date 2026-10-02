//! The contract of one plugin remainder: the recipe that the packer executes, the asset rows, and the input catalogue.
//!
//! The recipe stays in the process, so it has no JSON form. The asset rows go to `assets.json`, and Starlark writes the
//! catalogue. An asset row writes its fields in declaration order. It omits an empty `artifact`, the kind `file` and an
//! absent `class_path`, and it writes `destination` and `producer` always. So `serde_json::to_vec` writes the bytes
//! that the former writer wrote. The former writer also escaped `<`, `>`, `&`, U+2028 and U+2029, and no plan file
//! holds one of them.

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
    pub assets: Vec<Asset>,
    pub operations: Vec<Operation>,
}

/// One row of `assets.json`. An independent asset names the module of its reused jar as the artifact. Every asset is
/// below the plugin directory. The reader requires the producer, which the writer always writes.
#[derive(Serialize, Deserialize, Clone, Debug, Default, PartialEq, Eq)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
pub struct Asset {
    #[serde(default)]
    pub destination: String,
    pub producer: Producer,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub artifact: String,
    /// The row omits the kind `file`, as the former writer did.
    #[serde(default, skip_serializing_if = "AssetKind::is_file")]
    pub kind: AssetKind,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub class_path: Option<bool>,
}

/// The producer of an asset. The reader refuses every other text by name.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, Default, PartialEq, Eq, Hash)]
#[serde(rename_all = "kebab-case", try_from = "String")]
pub enum Producer {
    /// The remainder packer writes the asset. It is the default of a test fixture.
    #[default]
    Remainder,
    /// Another action writes the asset: a reused module jar or its native tree.
    Independent,
}

impl TryFrom<String> for Producer {
    type Error = String;

    fn try_from(producer: String) -> Result<Self, String> {
        match producer.as_str() {
            "remainder" => Ok(Self::Remainder),
            "independent" => Ok(Self::Independent),
            _ => Err(format!("unknown asset producer {producer:?}")),
        }
    }
}

/// The kind of an asset. The reader takes an empty kind as `file`, and it refuses every other text by name.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, Default, PartialEq, Eq, Hash)]
#[serde(rename_all = "kebab-case", try_from = "String")]
pub enum AssetKind {
    #[default]
    File,
    Tree,
}

impl AssetKind {
    /// Reports whether the kind is `file`, the kind that the asset row omits.
    pub fn is_file(&self) -> bool {
        *self == Self::File
    }
}

impl TryFrom<String> for AssetKind {
    type Error = String;

    fn try_from(kind: String) -> Result<Self, String> {
        match kind.as_str() {
            "" | "file" => Ok(Self::File),
            "tree" => Ok(Self::Tree),
            _ => Err(format!("unknown asset kind {kind:?}; the packer writes only file and tree assets")),
        }
    }
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

/// One declared input. The reader requires the kind, which Starlark always writes.
#[derive(Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct Artifact {
    #[serde(default)]
    pub id: String,
    pub kind: ArtifactKind,
    #[serde(default)]
    pub root: String,
}

/// The kind of a declared input. The reader refuses every other text by name.
#[derive(Deserialize, Clone, Copy, Debug, PartialEq, Eq, Hash)]
#[serde(try_from = "String")]
pub enum ArtifactKind {
    File,
    Directory,
}

impl TryFrom<String> for ArtifactKind {
    type Error = String;

    fn try_from(kind: String) -> Result<Self, String> {
        match kind.as_str() {
            "file" => Ok(Self::File),
            "directory" => Ok(Self::Directory),
            _ => Err(format!("unknown artifact root kind {kind:?}")),
        }
    }
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
