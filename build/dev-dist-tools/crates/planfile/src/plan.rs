//! The decoder of one plan file. It expands the compact forms into the full form.
//!
//! The decoder accepts only the shapes that the checked-in plan files use. `serde` refuses an unknown key. So a key of
//! `PluginPackingProjectionEncoding.kt` that no plan file states is an error, for example `symlinkTarget` or a field of
//! a Kotlin-executed operation. The decoder refuses an unused value of a known key by name. A `null` value of an
//! `Option` field is an absent key, the way Go decodes `null` into a pointer field.

use std::path::Path;

use serde::Deserialize;

use crate::contract::{LayoutAsset, Reference};
use crate::{Error, fail, json};

/// The mode of a plan asset that states none: 0644, or 420 in the plan file.
pub const DEFAULT_MODE: u32 = 0o644;
/// The mode of an executable plan asset: 0755, or 493 in the plan file.
pub const EXECUTABLE_MODE: u32 = 0o755;

pub(crate) const LAYOUT_ASSETS_KIND: &str = "layout-assets";

/// One decoded plan file in its full form. A reused jar is one of its module assets. The chain names the reused
/// modules to the packer, and the plan states no label for them.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PlanFile {
    pub version: u32,
    pub plugin: String,
    pub layout_signature: String,
    pub assets: Vec<Asset>,
    pub preparations: Vec<Preparation>,
    pub operations: Vec<Operation>,
}

/// One plan asset below the plugin directory. The kind is `file` or `tree`, and the mode is [`DEFAULT_MODE`] or
/// [`EXECUTABLE_MODE`]. The inputs of a jar asset are the inputs of its recipe sources.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Asset {
    pub destination: String,
    pub inputs: Vec<String>,
    pub recipe: Option<JarRecipe>,
    pub mode: u32,
    pub kind: String,
    pub class_path: bool,
}

/// The canonical recipe of one jar. An asset whose recipe and mode are the plain module jar of a reused module is
/// independent.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JarRecipe {
    pub sources: Vec<JarSource>,
    pub writer: JarWriter,
}

/// One ordered jar source. The kind is `module`, `library`, `archive`, `file` or `prepared`. Only a file source has an
/// entry, and the jar writer patches that file into the jar at the entry. A prepared source names a preparation
/// output.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JarSource {
    pub input: String,
    pub kind: String,
    pub entry: String,
}

/// The writer options of one jar recipe.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct JarWriter {
    pub manifest: ManifestPolicy,
    pub merge_entities: bool,
    /// The presigned native library whose native entries the jar leaves out, or empty. Only a reused
    /// `content_module_jar` packs such a jar.
    pub native_lib: String,
}

/// The manifest policy of a jar writer. `single-meaningful-source` keeps the manifest of a jar with one meaningful
/// source and drops it otherwise.
#[derive(Deserialize, Clone, Copy, Debug, Default, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub enum ManifestPolicy {
    #[default]
    SingleMeaningfulSource,
    Keep,
    Drop,
}

/// One preparation definition. Its operation is in [`PlanFile::operations`] under the same ID.
#[derive(Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
pub struct Preparation {
    pub id: String,
    pub inputs: Vec<String>,
    pub outputs: Vec<String>,
    pub model_signature: String,
}

/// One preparation operation. The one kind is `layout-assets`: the operation reads the inputs into the layout assets.
/// The packer keeps the manifest of every operation output.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Operation {
    pub id: String,
    pub kind: String,
    pub inputs: Vec<Reference>,
    pub output: String,
    pub layout_assets: LayoutAssetPreparation,
}

/// The `layoutAssets` payload of one operation. Only a tree has a root.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct LayoutAssetPreparation {
    pub format: LayoutFormat,
    pub root: String,
    pub assets: Vec<LayoutAsset>,
}

/// The output of a layout-assets operation: a tree under the root, or the entries of one jar.
#[derive(Deserialize, Clone, Copy, Debug, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum LayoutFormat {
    Tree,
    Entries,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct RawFile {
    version: u32,
    plugin: String,
    #[serde(rename = "variant")]
    _variant: String,
    layout_signature: String,
    assets: Vec<RawAsset>,
    #[serde(default)]
    preparations: Vec<Preparation>,
    #[serde(default)]
    operations: Vec<RawOperation>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct RawAsset {
    module: Option<String>,
    destination: Option<String>,
    inputs: Option<Vec<String>>,
    recipe: Option<RawJarRecipe>,
    mode: Option<u32>,
    kind: Option<String>,
    class_path: Option<bool>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct RawJarRecipe {
    sources: Vec<RawJarSource>,
    writer: Option<RawJarWriter>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct RawJarSource {
    input: String,
    kind: String,
    filter: String,
    entry: Option<String>,
    #[serde(default)]
    options: Vec<String>,
}

#[derive(Default, Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct RawJarWriter {
    manifest: Option<ManifestPolicy>,
    merge_entities: Option<bool>,
    /// The reader keeps the key to refuse `true`. No plan file states it.
    directory_entries: Option<bool>,
    native_lib: Option<String>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct RawOperation {
    id: String,
    kind: String,
    #[serde(default)]
    inputs: Vec<Reference>,
    output: String,
    manifest: String,
    layout_assets: Option<RawLayoutAssets>,
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct RawLayoutAssets {
    format: LayoutFormat,
    root: Option<String>,
    assets: Vec<LayoutAsset>,
}

/// Reads one plan file with [`json::read`] and expands its compact forms. Errors start with the path.
pub fn read(path: &Path) -> Result<PlanFile, Error> {
    let raw: RawFile = json::read(path)?;
    raw.decode().map_err(|error| error.context(path.display()))
}

/// Decodes one plan file with [`json::from_slice`] and expands its compact forms.
pub fn from_slice(data: &[u8]) -> Result<PlanFile, Error> {
    json::from_slice::<RawFile>(data)?.decode()
}

impl RawFile {
    fn decode(self) -> Result<PlanFile, Error> {
        let assets = self
            .assets
            .into_iter()
            .enumerate()
            .map(|(index, asset)| asset.decode().map_err(|error| error.context(format_args!("asset {index}"))))
            .collect::<Result<_, _>>()?;
        let operations = self
            .operations
            .into_iter()
            .enumerate()
            .map(|(index, operation)| operation.decode().map_err(|error| error.context(format_args!("operation {index}"))))
            .collect::<Result<_, _>>()?;
        Ok(PlanFile {
            version: self.version,
            plugin: self.plugin,
            layout_signature: self.layout_signature,
            assets,
            preparations: self.preparations,
            operations,
        })
    }
}

/// The recipe of a module's own jar, the commonest asset, which the compact form states as its module.
pub fn module_jar_recipe(module: &str) -> JarRecipe {
    JarRecipe {
        sources: vec![JarSource {
            input: module.to_owned(),
            kind: "module".to_owned(),
            entry: String::new(),
        }],
        writer: JarWriter {
            merge_entities: true,
            ..JarWriter::default()
        },
    }
}

/// The asset of a module's own jar at its default destination.
pub fn module_jar_asset(module: &str) -> Asset {
    Asset {
        destination: format!("lib/modules/{module}.jar"),
        inputs: vec![module.to_owned()],
        recipe: Some(module_jar_recipe(module)),
        mode: DEFAULT_MODE,
        kind: "file".to_owned(),
        class_path: true,
    }
}

impl RawAsset {
    fn decode(self) -> Result<Asset, Error> {
        if let Some(module) = self.module {
            if self.destination.is_some()
                || self.inputs.is_some()
                || self.recipe.is_some()
                || self.mode.is_some()
                || self.kind.is_some()
                || self.class_path.is_some()
            {
                fail!("the module jar asset {module:?} states more than its module");
            }
            return Ok(module_jar_asset(&module));
        }
        let Some(destination) = self.destination else {
            fail!("a plan asset requires a destination or a module");
        };
        let kind = self.kind.unwrap_or_else(|| "file".to_owned());
        if kind != "file" && kind != "tree" {
            fail!("asset {destination:?} has the kind {kind:?}; the packer writes only file and tree assets");
        }
        let mode = self.mode.unwrap_or(DEFAULT_MODE);
        if mode != DEFAULT_MODE && mode != EXECUTABLE_MODE {
            fail!("asset {destination:?} has the mode {mode:o}; the packer writes only the modes 644 and 755");
        }
        let (inputs, recipe) = match (self.inputs, self.recipe) {
            (Some(inputs), None) => (inputs, None),
            (None, Some(recipe)) => {
                let recipe = recipe.decode().map_err(|error| error.context(&destination))?;
                let inputs = recipe.sources.iter().map(|source| source.input.clone()).collect();
                (inputs, Some(recipe))
            }
            (Some(_), Some(_)) => {
                fail!("asset {destination:?} states inputs and a recipe; the inputs of a jar are its recipe sources")
            }
            (None, None) => fail!("a plan asset without inputs requires a recipe: {destination}"),
        };
        Ok(Asset {
            destination,
            inputs,
            recipe,
            mode,
            kind,
            class_path: self.class_path.unwrap_or(true),
        })
    }
}

impl RawJarRecipe {
    fn decode(self) -> Result<JarRecipe, Error> {
        if self.sources.is_empty() {
            fail!("a jar recipe requires ordered sources");
        }
        let writer = self.writer.unwrap_or_default();
        if writer.directory_entries == Some(true) {
            fail!("a jar writer states directoryEntries; the packer writes no directory entries into a plan jar");
        }
        let native_lib = match writer.native_lib {
            Some(native_lib) if native_lib.is_empty() => fail!("a jar writer states an empty native library"),
            native_lib => native_lib.unwrap_or_default(),
        };
        Ok(JarRecipe {
            sources: self.sources.into_iter().map(RawJarSource::decode).collect::<Result<_, _>>()?,
            writer: JarWriter {
                manifest: writer.manifest.unwrap_or_default(),
                merge_entities: writer.merge_entities == Some(true),
                native_lib,
            },
        })
    }
}

impl RawJarSource {
    fn decode(self) -> Result<JarSource, Error> {
        let Self {
            input,
            kind,
            filter,
            entry,
            options,
        } = self;
        if input.is_empty() {
            fail!("a jar source requires an input");
        }
        let only_filter = match kind.as_str() {
            "module" => "module-v1",
            "library" | "archive" => "library-v1",
            "file" => "none",
            "prepared" => "prepared",
            _ => fail!(
                "jar source {input:?} has the kind {kind:?}; the packer reads only module, library, archive, file and prepared sources"
            ),
        };
        if filter != only_filter {
            fail!("jar source {input:?} of the kind {kind} has the filter {filter:?}; the packer reads it only with {only_filter}");
        }
        let entry = entry.unwrap_or_default();
        let valid = if kind == "file" {
            !entry.is_empty() && options == ["patch"]
        } else {
            entry.is_empty() && options.is_empty()
        };
        if !valid {
            fail!(
                "jar source {input:?} of the kind {kind} has the entry {entry:?} and the options {options:?}; only a file source has an entry, and it requires the option patch"
            );
        }
        Ok(JarSource { input, kind, entry })
    }
}

impl RawOperation {
    fn decode(self) -> Result<Operation, Error> {
        let Self {
            id,
            kind,
            inputs,
            output,
            manifest,
            layout_assets,
        } = self;
        if kind != LAYOUT_ASSETS_KIND {
            fail!("operation {id:?} has the kind {kind:?}; the packer executes only layout-assets");
        }
        if manifest != "keep" {
            fail!("operation {id:?} has the manifest {manifest:?}; the packer keeps the manifest of a prepared output");
        }
        let Some(layout) = layout_assets else {
            fail!("layout-assets operation {id:?} requires layoutAssets");
        };
        let root = layout.root.unwrap_or_default();
        if layout.format == LayoutFormat::Entries && !root.is_empty() {
            fail!("operation {id:?} must not declare a tree root for its entries");
        }
        Ok(Operation {
            id,
            kind,
            inputs,
            output,
            layout_assets: LayoutAssetPreparation {
                format: layout.format,
                root,
                assets: layout.assets,
            },
        })
    }
}
