//! The port of `kotlin_preparation_test.go` and of the Kotlin half of `TestLayoutTransformExcludesEncoding`.
//!
//! The parity tests pack a hand-written recipe and compare the result with the golden under `testdata`. A golden is the
//! materialization of every fixture by the deleted Kotlin preparer, frozen before the Kotlin materialization stopped.
//! The goldens are read-only: this packer is the only producer left, so a re-recording would compare it with itself.

use std::path::Path;

use planfile::contract::{
    Catalogue, LayoutAssets, LayoutTransform, LayoutTransformKind, Operation, Recipe, Reference, Source, TREE_VERSION, VERSION,
};
use serde_json::{Value, json};

use super::*;

// The plan file of a fixture, in the shape that `PluginPackingProjectionEncoding.kt` emits. The fixtures compute the
// layout signature as `pluginPackingLayoutSignature` does, and each operation signature as
// `devPluginPreparationOperationSignature` does. So `plugin-model-tool --check` accepts a fixture as a plan file.

#[derive(Clone, Default)]
pub(crate) struct KotlinPlanFile {
    pub(crate) version: u32,
    pub(crate) plugin: String,
    pub(crate) variant: String,
    pub(crate) layout_signature: String,
    pub(crate) assets: Vec<KotlinPlanAsset>,
    pub(crate) preparations: Vec<KotlinPreparation>,
    /// The kotlinx text of each operation.
    pub(crate) operations: Vec<String>,
}

/// One plan asset. A zero mode is the default 420. An asset with a module is the compact form of a module's own jar.
/// `None` inputs are the recipe sources.
#[derive(Clone, Default)]
pub(crate) struct KotlinPlanAsset {
    pub(crate) module: String,
    pub(crate) destination: String,
    pub(crate) inputs: Option<Vec<String>>,
    pub(crate) recipe: Option<KotlinJarRecipe>,
    pub(crate) mode: u32,
    pub(crate) symlink_target: Option<String>,
    pub(crate) kind: String,
    pub(crate) class_path: Option<bool>,
    pub(crate) normalize_tree_modes: bool,
    pub(crate) scope: String,
}

#[derive(Clone, Default)]
pub(crate) struct KotlinJarRecipe {
    pub(crate) sources: Vec<KotlinJarSource>,
    pub(crate) writer: KotlinJarWriter,
}

#[derive(Clone, Default)]
pub(crate) struct KotlinJarSource {
    pub(crate) input: String,
    pub(crate) kind: String,
    pub(crate) filter: String,
    pub(crate) entry: String,
    pub(crate) options: Vec<String>,
    pub(crate) prepared_manifest: Option<KotlinPreparedManifest>,
}

#[derive(Clone, Default)]
pub(crate) struct KotlinPreparedManifest {
    pub(crate) original_meaningful_source_count: Option<i32>,
    pub(crate) source_manifest_policies: Vec<String>,
}

/// The writer of a jar recipe. An empty manifest is the default `single-meaningful-source`.
#[derive(Clone, Default)]
pub(crate) struct KotlinJarWriter {
    pub(crate) manifest: String,
    pub(crate) merge_entities: bool,
    pub(crate) directory_entries: bool,
}

#[derive(Clone, Default)]
pub(crate) struct KotlinPreparation {
    pub(crate) id: String,
    pub(crate) inputs: Vec<String>,
    pub(crate) outputs: Vec<String>,
    pub(crate) model_signature: String,
}

pub(crate) fn module_source(module: &str) -> KotlinJarSource {
    KotlinJarSource {
        input: module.to_owned(),
        kind: "module".to_owned(),
        filter: "module-v1".to_owned(),
        ..KotlinJarSource::default()
    }
}

pub(crate) fn prepared_source(input: &str) -> KotlinJarSource {
    KotlinJarSource {
        input: input.to_owned(),
        kind: "prepared".to_owned(),
        filter: "prepared".to_owned(),
        ..KotlinJarSource::default()
    }
}

fn module_jar_recipe(module: &str) -> KotlinJarRecipe {
    KotlinJarRecipe {
        sources: vec![module_source(module)],
        writer: KotlinJarWriter {
            merge_entities: true,
            ..KotlinJarWriter::default()
        },
    }
}

impl KotlinPlanAsset {
    /// The full form, as the Kotlin codec decodes it. The compact module form becomes the module jar asset. Absent
    /// inputs are the recipe sources, and the decoder fills in the defaults.
    fn expanded(&self) -> Self {
        let mut asset = if self.module.is_empty() {
            self.clone()
        } else {
            Self {
                destination: format!("lib/modules/{}.jar", self.module),
                inputs: Some(vec![self.module.clone()]),
                recipe: Some(module_jar_recipe(&self.module)),
                ..Self::default()
            }
        };
        if asset.inputs.is_none()
            && let Some(recipe) = &asset.recipe
        {
            asset.inputs = Some(recipe.sources.iter().map(|source| source.input.clone()).collect());
        }
        if asset.mode == 0 {
            asset.mode = 420;
        }
        if asset.kind.is_empty() {
            asset.kind = "file".to_owned();
        }
        if asset.scope.is_empty() {
            asset.scope = "plugin".to_owned();
        }
        asset
    }
}

fn source_json(source: &KotlinJarSource) -> Value {
    let mut value = json!({"input": source.input, "kind": source.kind, "filter": source.filter});
    if !source.entry.is_empty() {
        value["entry"] = json!(source.entry);
    }
    if !source.options.is_empty() {
        value["options"] = json!(source.options);
    }
    if let Some(manifest) = &source.prepared_manifest {
        let mut prepared = json!({});
        if let Some(count) = manifest.original_meaningful_source_count {
            prepared["originalMeaningfulSourceCount"] = json!(count);
        }
        prepared["sourceManifestPolicies"] = json!(manifest.source_manifest_policies);
        value["preparedManifest"] = prepared;
    }
    value
}

fn asset_json(asset: &KotlinPlanAsset) -> Value {
    let mut value = json!({});
    if !asset.module.is_empty() {
        value["module"] = json!(asset.module);
    }
    if !asset.destination.is_empty() {
        value["destination"] = json!(asset.destination);
    }
    value["inputs"] = json!(asset.inputs);
    if let Some(recipe) = &asset.recipe {
        let mut writer = json!({});
        if !recipe.writer.manifest.is_empty() {
            writer["manifest"] = json!(recipe.writer.manifest);
        }
        writer["mergeEntities"] = json!(recipe.writer.merge_entities);
        if recipe.writer.directory_entries {
            writer["directoryEntries"] = json!(true);
        }
        let sources: Vec<Value> = recipe.sources.iter().map(source_json).collect();
        value["recipe"] = json!({"sources": sources, "writer": writer});
    }
    if asset.mode != 0 {
        value["mode"] = json!(asset.mode);
    }
    if let Some(target) = &asset.symlink_target {
        value["symlinkTarget"] = json!(target);
    }
    if !asset.kind.is_empty() {
        value["kind"] = json!(asset.kind);
    }
    if let Some(class_path) = asset.class_path {
        value["classPath"] = json!(class_path);
    }
    if asset.normalize_tree_modes {
        value["normalizeTreeModes"] = json!(true);
    }
    if !asset.scope.is_empty() {
        value["scope"] = json!(asset.scope);
    }
    value
}

/// The plan file text without the HTML escaping that kotlinx.serialization never applies.
pub(crate) fn plan_json(plan: &KotlinPlanFile) -> String {
    let assets: Vec<Value> = plan.assets.iter().map(asset_json).collect();
    let mut value = json!({
        "version": plan.version,
        "plugin": plan.plugin,
        "variant": plan.variant,
        "layoutSignature": plan.layout_signature,
        "assets": assets,
    });
    if !plan.preparations.is_empty() {
        let preparations: Vec<Value> = plan
            .preparations
            .iter()
            .map(|preparation| {
                json!({
                    "id": preparation.id,
                    "inputs": preparation.inputs,
                    "outputs": preparation.outputs,
                    "modelSignature": preparation.model_signature,
                })
            })
            .collect();
        value["preparations"] = json!(preparations);
    }
    if !plan.operations.is_empty() {
        let operations: Vec<Value> = plan
            .operations
            .iter()
            .map(|operation| serde_json::from_str(operation).unwrap())
            .collect();
        value["operations"] = json!(operations);
    }
    serde_json::to_string(&value).unwrap()
}

/// Frames values the way a hash4j `HashStream` does, so [`signature128`] over its bytes equals the Kotlin
/// `devDistSignature` over the same puts.
#[derive(Default)]
struct Hash4jStream(Vec<u8>);

impl Hash4jStream {
    fn put_int(&mut self, value: i32) {
        self.0.extend_from_slice(&value.to_le_bytes());
    }

    fn put_boolean(&mut self, value: bool) {
        self.0.push(u8::from(value));
    }

    /// hash4j `putString`: the UTF-16 code units, then their count.
    fn put_string(&mut self, value: &str) {
        let mut count = 0;
        for unit in value.encode_utf16() {
            self.0.extend_from_slice(&unit.to_le_bytes());
            count += 1;
        }
        self.put_int(count);
    }

    fn put_strings(&mut self, values: &[String]) {
        self.put_int(values.len() as i32);
        for value in values {
            self.put_string(value);
        }
    }
}

/// `devDistSignature` of the Kotlin plugin-preparation module: hash4j `Hashing.xxh3_128()` over the stream bytes, as one
/// base-36 number of the 128-bit value.
fn signature128(stream: &[u8]) -> String {
    let mut value = xxhash_rust::xxh3::xxh3_128(stream);
    if value == 0 {
        return "0".to_owned();
    }
    let mut digits = Vec::new();
    while value != 0 {
        digits.push(char::from_digit((value % 36) as u32, 36).unwrap());
        value /= 36;
    }
    digits.iter().rev().collect()
}

/// Hashes a preparation recipe of format 2 that holds one operation in its kotlinx encoding.
pub(crate) fn kotlin_model_signature(operation: &str) -> String {
    let mut stream = Hash4jStream::default();
    stream.put_string(&format!(r#"{{"version":2,"operations":[{operation}]}}"#));
    signature128(&stream.0)
}

fn kotlin_json<T: serde::Serialize + ?Sized>(value: &T) -> String {
    serde_json::to_string(value).unwrap()
}

fn transform_kind_name(kind: LayoutTransformKind) -> &'static str {
    match kind {
        LayoutTransformKind::ArchiveTree => "archive-tree",
    }
}

/// The kotlinx encoding of a layout-assets operation. A default is written when its class encodes defaults, and it is
/// left out when the field is marked never.
pub(crate) fn kotlin_layout_assets_operation(id: &str, output: &str, format: &str, root: &str, layout: &LayoutAssets) -> String {
    let inputs: Vec<String> = layout
        .inputs
        .iter()
        .map(|reference| {
            format!(
                r#"{{"artifact":{},"path":{}}}"#,
                kotlin_json(&reference.artifact),
                kotlin_json(&reference.path)
            )
        })
        .collect();
    let assets: Vec<String> = layout
        .assets
        .iter()
        .map(|asset| {
            let mut text = format!(
                r#"{{"destination":{},"sources":{}"#,
                kotlin_json(&asset.destination),
                kotlin_json(&asset.sources)
            );
            if let Some(transform) = &asset.transform {
                text += &format!(r#","transform":{{"kind":{}"#, kotlin_json(transform_kind_name(transform.kind)));
                if transform.strip_components != 0 {
                    text += &format!(r#","stripComponents":{}"#, transform.strip_components);
                }
                if !transform.mappings.is_empty() {
                    let mappings: Vec<String> = transform
                        .mappings
                        .iter()
                        .map(|mapping| {
                            format!(
                                r#"{{"pattern":{},"stripComponents":{},"destination":{}}}"#,
                                kotlin_json(plan::mapping_pattern(mapping)),
                                mapping.strip_components,
                                kotlin_json(&mapping.destination)
                            )
                        })
                        .collect();
                    text += &format!(r#","mappings":[{}]"#, mappings.join(","));
                }
                for (key, values) in [("includes", &transform.includes), ("executables", &transform.executables)] {
                    if !values.is_empty() {
                        text += &format!(r#","{key}":{}"#, kotlin_json(values));
                    }
                }
                text += "}";
            }
            if asset.mode != 0 {
                text += &format!(r#","mode":{}"#, asset.mode);
            }
            text + "}"
        })
        .collect();
    let inputs_field = if inputs.is_empty() {
        String::new()
    } else {
        format!(r#","inputs":[{}]"#, inputs.join(","))
    };
    format!(
        r#"{{"id":{},"kind":"layout-assets"{inputs_field},"output":{},"manifest":"keep","layoutAssets":{{"format":{},"root":{},"assets":[{}]}}}}"#,
        kotlin_json(id),
        kotlin_json(output),
        kotlin_json(format),
        kotlin_json(root),
        assets.join(",")
    )
}

/// `pluginPackingLayoutSignature` over the plan file in its full form.
pub(crate) fn kotlin_layout_signature(plan: &KotlinPlanFile) -> String {
    let mut stream = Hash4jStream::default();
    let assets: Vec<KotlinPlanAsset> = plan.assets.iter().map(KotlinPlanAsset::expanded).collect();
    let scoped_assets = assets.iter().any(|asset| asset.scope != "plugin");
    let trees = assets.iter().any(|asset| asset.kind == "tree");
    let prepared_manifests = trees
        || assets.iter().any(|asset| {
            asset
                .recipe
                .as_ref()
                .is_some_and(|recipe| recipe.sources.iter().any(|source| source.prepared_manifest.is_some()))
        });
    let class_path_facts = prepared_manifests || assets.iter().any(|asset| asset.class_path == Some(false));
    let directories = class_path_facts || assets.iter().any(|asset| asset.kind != "file");
    stream.put_int(if scoped_assets {
        6
    } else if trees {
        5
    } else if prepared_manifests {
        4
    } else if class_path_facts {
        3
    } else if directories {
        2
    } else {
        1
    });
    stream.put_string(&plan.plugin);
    stream.put_string(&plan.variant);
    stream.put_int(assets.len() as i32);
    for asset in &assets {
        stream.put_string(&asset.destination);
        if scoped_assets {
            stream.put_string(&asset.scope);
        }
        if directories {
            stream.put_string(&asset.kind);
        }
        if class_path_facts {
            stream.put_boolean(asset.class_path != Some(false));
        }
        stream.put_int(asset.mode as i32);
        stream.put_boolean(asset.symlink_target.is_some());
        if let Some(target) = &asset.symlink_target {
            stream.put_string(target);
        }
        stream.put_strings(asset.inputs.as_deref().unwrap_or_default());
        stream.put_boolean(asset.recipe.is_some());
        if let Some(recipe) = &asset.recipe {
            stream.put_int(recipe.sources.len() as i32);
            for source in &recipe.sources {
                stream.put_string(&source.input);
                stream.put_string(&source.kind);
                stream.put_string(&source.filter);
                stream.put_string(&source.entry);
                stream.put_strings(&source.options);
                if prepared_manifests {
                    stream.put_boolean(source.prepared_manifest.is_some());
                    if let Some(manifest) = &source.prepared_manifest {
                        stream.put_int(1);
                        stream.put_int(manifest.original_meaningful_source_count.unwrap_or(-1));
                        stream.put_strings(&manifest.source_manifest_policies);
                    }
                }
            }
            let manifest = if recipe.writer.manifest.is_empty() {
                "single-meaningful-source"
            } else {
                &recipe.writer.manifest
            };
            stream.put_string(manifest);
            stream.put_boolean(recipe.writer.merge_entities);
            stream.put_boolean(recipe.writer.directory_entries);
            stream.put_boolean(false);
            stream.put_string("");
        }
    }
    stream.put_int(plan.preparations.len() as i32);
    for preparation in &plan.preparations {
        stream.put_string(&preparation.id);
        stream.put_strings(&preparation.inputs);
        stream.put_strings(&preparation.outputs);
        stream.put_string(&preparation.model_signature);
        if directories {
            stream.put_boolean(false);
        }
    }
    stream.put_strings(&[]);
    signature128(&stream.0)
}

/// Signs every preparation from its operation by position, and signs the layout.
pub(crate) fn signed_plan(mut plan: KotlinPlanFile, operations: &[String]) -> KotlinPlanFile {
    assert_eq!(operations.len(), plan.preparations.len(), "one operation per preparation");
    for (preparation, operation) in plan.preparations.iter_mut().zip(operations) {
        preparation.model_signature = kotlin_model_signature(operation);
        plan.operations.push(operation.clone());
    }
    plan.layout_signature = kotlin_layout_signature(&plan);
    plan
}

/// The one operation of the filtered demo projection: the entries of the raw input become `raw.txt` of `lib/main.jar`.
/// `PluginPackingProjectionEncodingTest` of the Kotlin build scripts states the same operation.
pub(crate) fn kotlin_filter_operation(output: &str) -> String {
    let layout = LayoutAssets {
        inputs: vec![Reference::artifact("raw")],
        assets: vec![layout_asset("raw.txt", &[0], None)],
    };
    kotlin_layout_assets_operation("filter", output, "entries", "", &layout)
}

/// Pins the two signature helpers against the constants that `PluginPackingProjectionEncodingTest` of the Kotlin build
/// scripts pins for the filtered demo projection.
#[test]
fn kotlin_signature_helpers_reproduce_the_fixture_constants() {
    let filter = kotlin_filter_operation("filtered");
    assert_eq!(kotlin_model_signature(&filter), "5nm0sqi8af0srealvdtoxpzqm");
    let filtered = KotlinPlanFile {
        version: VERSION,
        plugin: "filtered-plugin".to_owned(),
        variant: "linux".to_owned(),
        assets: vec![KotlinPlanAsset {
            destination: "lib/main.jar".to_owned(),
            inputs: Some(strings(&["filtered"])),
            recipe: Some(KotlinJarRecipe {
                sources: vec![prepared_source("filtered")],
                writer: KotlinJarWriter {
                    manifest: "drop".to_owned(),
                    ..KotlinJarWriter::default()
                },
            }),
            ..KotlinPlanAsset::default()
        }],
        preparations: vec![KotlinPreparation {
            id: "filter".to_owned(),
            inputs: strings(&["raw"]),
            outputs: strings(&["filtered"]),
            model_signature: kotlin_model_signature(&filter),
        }],
        ..KotlinPlanFile::default()
    };
    assert_eq!(kotlin_layout_signature(&filtered), "abd1jj2mitamhwlzozt2d0zlo");
}

/// The Kotlin half of the Go `TestLayoutTransformExcludesEncoding`: the kotlinx operation holds the transform in the
/// encoding that the `planfile` crate reads. The encoding half is in the `planfile` crate.
#[test]
fn layout_transform_encoding_in_a_kotlin_operation() {
    for (transform, want) in [
        (transform(LayoutTransformKind::ArchiveTree), r#"{"kind":"archive-tree"}"#),
        (
            LayoutTransform {
                includes: strings(&["bin/**", "!bin/LLDBFrontend"]),
                executables: strings(&["bin/*"]),
                ..transform(LayoutTransformKind::ArchiveTree)
            },
            r#"{"kind":"archive-tree","includes":["bin/**","!bin/LLDBFrontend"],"executables":["bin/*"]}"#,
        ),
        (
            archive_tree(1, vec![mapping("dlv/**", 1, "")]),
            r#"{"kind":"archive-tree","stripComponents":1,"mappings":[{"pattern":"dlv/**","stripComponents":1,"destination":""}]}"#,
        ),
    ] {
        let layout = LayoutAssets {
            inputs: Vec::new(),
            assets: vec![layout_asset("", &[], Some(transform.clone()))],
        };
        let operation: Value =
            serde_json::from_str(&kotlin_layout_assets_operation("layout", "output", "tree", "payload", &layout)).unwrap();
        let encoded = &operation["layoutAssets"]["assets"][0]["transform"];
        assert_eq!(serde_json::to_string(encoded).unwrap(), want);
        let decoded: LayoutTransform = planfile::json::from_slice(want.as_bytes()).unwrap();
        assert_eq!(decoded, transform);
    }
}

/// A plan of one preparation whose operation is the kotlinx text, signed.
fn kotlin_plan(
    version: u32,
    plugin: &str,
    assets: Vec<KotlinPlanAsset>,
    preparation: KotlinPreparation,
    operation: &str,
) -> KotlinPlanFile {
    signed_plan(
        KotlinPlanFile {
            version,
            plugin: plugin.to_owned(),
            assets,
            preparations: vec![preparation],
            ..KotlinPlanFile::default()
        },
        &[operation.to_owned()],
    )
}

/// One layout-assets operation with its raw inputs on disk. A tree fixture names its root, and an entries fixture names
/// its jar. `present` lists the output paths that the fixture exists for. `host_order` marks a jar whose entry order
/// follows the readdir order of the host, so the golden holds the sorted entries.
struct LayoutParityFixture {
    format: &'static str,
    root: &'static str,
    host_order: bool,
    layout: LayoutAssets,
    inputs: Catalogue,
    present: Vec<&'static str>,
}

type FixtureBuilder = fn(&Path) -> LayoutParityFixture;

fn tree_fixture(root: &'static str, layout: LayoutAssets, inputs: Catalogue, present: Vec<&'static str>) -> LayoutParityFixture {
    LayoutParityFixture {
        format: "tree",
        root,
        host_order: false,
        layout,
        inputs,
        present,
    }
}

/// The layout-assets operations that the packer executes: one per transform, per archive reader rule, and per format.
/// Three golden fixtures have no port. "strip and mapping selection with normalized tree modes": no plan file
/// normalizes the modes of a tree, so the typed layout-tree operation has no mode. The two `tree-map` fixtures: the
/// localization trees have the jar layout, so the transform is gone.
const LAYOUT_PARITY_FIXTURES: [(&str, FixtureBuilder); 5] = [
    ("archive-tree from a tar.gz keeps modes, a link, and an empty directory", |inputs| {
        // The link `latest` carries a trailing slash, which the Kotlin writer removed through Path.of.
        let archive = inputs.join("assets.tar.gz");
        write_tar_gz(
            &archive,
            &[
                tar_directory("top/", 0o755),
                tar_directory("top/bin/", 0o755),
                tar_file("top/bin/tool", "tool", 0o751),
                tar_link("top/bin/current", "tool"),
                tar_link("top/bin/latest", "tool/"),
                tar_file("top/data.txt", "data", 0o664),
                tar_file("top/private.txt", "secret", 0o600),
                tar_directory("top/docs/", 0o750),
                tar_file("top/docs/readme.md", "readme", 0o644),
                tar_directory("top/empty/", 0o755),
            ],
        );
        tree_fixture(
            "payload",
            layout(
                &[Reference::artifact("archive")],
                vec![layout_asset("", &[0], Some(archive_tree(1, Vec::new())))],
            ),
            catalogue(vec![file_artifact("archive", &archive)]),
            vec![
                "payload/bin/tool",
                "payload/bin/current",
                "payload/bin/latest",
                "payload/data.txt",
                "payload/private.txt",
                "payload/docs/readme.md",
                "payload/empty",
            ],
        )
    }),
    (
        "zip creators: Unix and MacOSX carry modes and a link, Windows is flattened",
        |inputs| {
            let (unix, macos, windows) = (inputs.join("unix.zip"), inputs.join("macos.zip"), inputs.join("windows.zip"));
            write_zip(
                &unix,
                &[
                    unix_zip_entry("bin/", "", 0o775, 3),
                    unix_zip_entry("bin/tool", "tool", 0o775, 3),
                    unix_zip_entry("data.txt", "data", 0o664, 3),
                    zip_link("bin/current", "tool", 3),
                ],
            );
            write_zip(
                &macos,
                &[unix_zip_entry("bin/tool", "tool", 0o755, 19), zip_link("bin/current", "tool", 19)],
            );
            write_zip(
                &windows,
                &[unix_zip_entry("bin/tool", "tool", 0o755, 10), zip_link("bin/current", "tool", 10)],
            );
            tree_fixture(
                "archives",
                layout(
                    &[
                        Reference::artifact("unix"),
                        Reference::artifact("macos"),
                        Reference::artifact("windows"),
                    ],
                    vec![
                        layout_asset("unix", &[0], Some(archive_tree(0, Vec::new()))),
                        layout_asset("macos", &[1], Some(archive_tree(0, Vec::new()))),
                        layout_asset("windows", &[2], Some(archive_tree(0, Vec::new()))),
                    ],
                ),
                catalogue(vec![
                    file_artifact("unix", &unix),
                    file_artifact("macos", &macos),
                    file_artifact("windows", &windows),
                ]),
                vec![
                    "archives/unix/bin/tool",
                    "archives/unix/bin/current",
                    "archives/unix/data.txt",
                    "archives/macos/bin/tool",
                    "archives/macos/bin/current",
                    "archives/windows/bin/tool",
                    "archives/windows/bin/current",
                ],
            )
        },
    ),
    ("zip.zst modes and a link-typed entry become 0644 files", |inputs| {
        let archive = inputs.join("libghostty.zip.zst");
        write_zstd(
            &archive,
            &zip_bytes(&[
                unix_zip_entry("linux-x64/", "", 0o755, 3),
                unix_zip_entry("linux-x64/libghostty.so", "native", 0o755, 3),
                zip_link("linux-x64/libghostty.so.1", "libghostty.so", 3),
                unix_zip_entry("darwin-aarch64/libghostty.dylib", "other", 0o755, 3),
            ]),
        );
        tree_fixture(
            "terminal",
            layout(
                &[Reference::artifact("archive")],
                vec![layout_asset("", &[0], Some(archive_tree(0, vec![mapping("linux-x64/**", 1, "")])))],
            ),
            catalogue(vec![file_artifact("archive", &archive)]),
            vec!["terminal/libghostty.so", "terminal/libghostty.so.1"],
        )
    }),
    ("plain overlay of two trees keeps the first claim and a relative link", |inputs| {
        let (first, second) = (inputs.join("first"), inputs.join("second"));
        write_test_file(&first.join("shared.txt"), b"first");
        write_test_file(&first.join("a.txt"), b"a");
        symlink("a.txt", &first.join("link.txt"));
        write_test_file(&first.join("sub/inner.txt"), b"inner");
        write_test_file(&second.join("shared.txt"), b"second");
        write_test_file(&second.join("b.txt"), b"b");
        write_test_file(&second.join("bin/tool"), b"tool");
        chmod_tree(&first);
        chmod_tree(&second);
        chmod(&first.join("sub/inner.txt"), 0o600);
        chmod(&first.join("sub"), 0o750);
        chmod(&second.join("bin/tool"), 0o755);
        tree_fixture(
            "overlay",
            layout(
                &[Reference::artifact("first"), Reference::artifact("second")],
                vec![layout_asset("", &[0], None), layout_asset("", &[1], None)],
            ),
            catalogue(vec![directory_artifact("first", &first), directory_artifact("second", &second)]),
            vec![
                "overlay/shared.txt",
                "overlay/a.txt",
                "overlay/link.txt",
                "overlay/sub/inner.txt",
                "overlay/b.txt",
                "overlay/bin/tool",
            ],
        )
    }),
    ("plain file copies inside a tree", |inputs| {
        let (launcher, tool) = (inputs.join("launcher"), inputs.join("tool.jar"));
        write_test_file(&launcher, b"launcher");
        write_test_file(&tool, b"tool");
        chmod(&launcher, 0o755);
        chmod(&tool, 0o755);
        tree_fixture(
            "jbr",
            layout(
                &[Reference::artifact("launcher"), Reference::artifact("tool")],
                vec![
                    layout_asset("bin/launcher", &[0], None),
                    LayoutAsset {
                        mode: 0o644,
                        ..layout_asset("lib/tool.jar", &[1], None)
                    },
                ],
            ),
            catalogue(vec![file_artifact("launcher", &launcher), file_artifact("tool", &tool)]),
            vec!["jbr/bin/launcher", "jbr/lib/tool.jar"],
        )
    }),
];

fn layout_input_ids(layout: &LayoutAssets) -> Vec<String> {
    let mut ids: Vec<String> = Vec::new();
    for reference in &layout.inputs {
        if !ids.contains(&reference.artifact) {
            ids.push(reference.artifact.clone());
        }
    }
    ids
}

/// Packs the hand-written recipe of every layout-assets fixture: the layout-tree operation or the layout source. The
/// packer executes it from the raw inputs, and the result must have the bytes, the modes and the links of the golden.
#[test]
fn kotlin_layout_materialization_matches_the_transforms() {
    let golden = Golden::open("kotlin-layout");
    const OUTPUT: &str = "layout-assets:output";
    for (name, build) in LAYOUT_PARITY_FIXTURES {
        let inputs = temp();
        let fixture = build(inputs.path());
        let preparation = KotlinPreparation {
            id: "layout".to_owned(),
            inputs: layout_input_ids(&fixture.layout),
            outputs: strings(&[OUTPUT]),
            ..KotlinPreparation::default()
        };
        let recipe = if fixture.format == "tree" {
            let operation = kotlin_layout_assets_operation("layout", OUTPUT, "tree", fixture.root, &fixture.layout);
            let plan_file = kotlin_plan(
                TREE_VERSION,
                "layout-plugin",
                vec![KotlinPlanAsset {
                    destination: fixture.root.to_owned(),
                    inputs: Some(strings(&[OUTPUT])),
                    kind: "tree".to_owned(),
                    class_path: Some(false),
                    ..KotlinPlanAsset::default()
                }],
                preparation,
                &operation,
            );
            Recipe {
                version: TREE_VERSION,
                plugin: plan_file.plugin,
                layout_signature: plan_file.layout_signature,
                assets: vec![tree_asset(fixture.root)],
                operations: vec![Operation::LayoutTree {
                    destination: fixture.root.to_owned(),
                    layout: fixture.layout.clone(),
                }],
            }
        } else {
            let jar = format!("lib/{}", fixture.root);
            let operation = kotlin_layout_assets_operation("layout", OUTPUT, "entries", "", &fixture.layout);
            let plan_file = kotlin_plan(
                VERSION,
                "layout-plugin",
                vec![KotlinPlanAsset {
                    destination: jar.clone(),
                    inputs: Some(strings(&[OUTPUT])),
                    recipe: Some(KotlinJarRecipe {
                        sources: vec![prepared_source(OUTPUT)],
                        writer: KotlinJarWriter {
                            manifest: "drop".to_owned(),
                            merge_entities: true,
                            ..KotlinJarWriter::default()
                        },
                    }),
                    ..KotlinPlanAsset::default()
                }],
                preparation,
                &operation,
            );
            Recipe {
                version: VERSION,
                plugin: plan_file.plugin,
                layout_signature: plan_file.layout_signature,
                assets: vec![remainder(&jar)],
                operations: vec![Operation::Jar {
                    destination: jar,
                    mode: 0o644,
                    sources: vec![Source::Layout(fixture.layout.clone())],
                    merge_entities: true,
                    directory_entries: false,
                }],
            }
        };
        let written = write_execution(&recipe, &fixture.inputs);
        let record = materialization_record(&written.output);
        require_inventory_matches_tree(&written.output, &written.inventory);
        require_recorded_paths(&record, &fixture.present);
        let golden_record = if fixture.host_order {
            jar_entry_record(&written.output.join("lib").join(fixture.root))
        } else {
            record
        };
        golden.check(name, &golden_record);
    }
    let dropped = "strip and mapping selection with normalized tree modes";
    for name in [
        dropped,
        "tree-map entries keep the readdir order, the first source, and a transport link",
        "tree-map tree keeps source modes, the first source, and a transport link",
    ] {
        assert!(
            golden.fixture_names().contains(&name),
            "the golden lost the dropped fixture {name:?}"
        );
    }
    assert_eq!(golden.fixture_names().len(), LAYOUT_PARITY_FIXTURES.len() + 3);
    // The dropped fixture is a tree asset that normalizes the copied modes. `planfile` refuses that shape.
    let normalized_layout = layout(
        &[Reference::artifact("selected")],
        vec![layout_asset("", &[0], Some(archive_tree(1, Vec::new())))],
    );
    let normalized = kotlin_plan(
        TREE_VERSION,
        "layout-plugin",
        vec![KotlinPlanAsset {
            destination: "jcef".to_owned(),
            inputs: Some(strings(&[OUTPUT])),
            kind: "tree".to_owned(),
            class_path: Some(false),
            normalize_tree_modes: true,
            ..KotlinPlanAsset::default()
        }],
        KotlinPreparation {
            id: "layout".to_owned(),
            inputs: strings(&["selected"]),
            outputs: strings(&[OUTPUT]),
            ..KotlinPreparation::default()
        },
        &kotlin_layout_assets_operation("layout", OUTPUT, "tree", "jcef", &normalized_layout),
    );
    let error = planfile::from_slice(plan_json(&normalized).as_bytes()).map(|_| ()).unwrap_err();
    assert!(error.message().contains("unknown field `normalizeTreeModes`"), "{dropped}: {error}");
}
