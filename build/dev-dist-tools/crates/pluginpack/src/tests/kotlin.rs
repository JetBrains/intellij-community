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

// The plan file of a fixture, in the shape that `PluginPackingProjectionEncoding.kt` emits.

#[derive(Clone, Default)]
pub(crate) struct KotlinPlanFile {
    pub(crate) version: u32,
    pub(crate) plugin: String,
    pub(crate) variant: String,
    pub(crate) assets: Vec<KotlinPlanAsset>,
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
    /// The retired asset scope. Only the frozen fixture of version 3 states it, and the layout signature ignores it.
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
        "assets": assets,
    });
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

/// The Kotlin half of the former `TestLayoutTransformExcludesEncoding`: the kotlinx operation holds the transform in
/// the encoding that the `planfile` crate reads. The encoding half is in the `planfile` crate.
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

/// A plan of one operation in its kotlinx text.
fn kotlin_plan(version: u32, plugin: &str, assets: Vec<KotlinPlanAsset>, operation: &str) -> KotlinPlanFile {
    KotlinPlanFile {
        version,
        plugin: plugin.to_owned(),
        assets,
        operations: vec![operation.to_owned()],
        ..KotlinPlanFile::default()
    }
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
/// Four golden fixtures have no port. "strip and mapping selection with normalized tree modes": no plan file
/// normalizes the modes of a tree, so the typed layout-tree operation has no mode. The two `tree-map` fixtures: the
/// localization trees have the jar layout, so the transform is gone. "plain overlay of two trees": no plan file copies
/// two trees onto one root without a mode.
const LAYOUT_PARITY_FIXTURES: [(&str, FixtureBuilder); 4] = [
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
    ("plain file copies inside a tree", |inputs| {
        let (launcher, tool) = (inputs.join("launcher"), inputs.join("tool.jar"));
        write_file(&launcher, b"launcher");
        write_file(&tool, b"tool");
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

/// Packs the hand-written recipe of every layout-assets fixture: the layout-tree operation or the layout source. The
/// packer executes it from the raw inputs, and the result must have the bytes, the modes and the links of the golden.
#[test]
fn kotlin_layout_materialization_matches_the_transforms() {
    let golden = Golden::open("kotlin-layout");
    const OUTPUT: &str = "layout-assets:output";
    for (name, build) in LAYOUT_PARITY_FIXTURES {
        let inputs = temp();
        let fixture = build(inputs.path());
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
                &operation,
            );
            Recipe {
                version: TREE_VERSION,
                plugin: plan_file.plugin,
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
                        },
                    }),
                    ..KotlinPlanAsset::default()
                }],
                &operation,
            );
            Recipe {
                version: VERSION,
                plugin: plan_file.plugin,
                assets: vec![remainder(&jar)],
                operations: vec![Operation::Jar {
                    destination: jar,
                    mode: 0o644,
                    sources: vec![Source::Layout(fixture.layout.clone())],
                    merge_entities: true,
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
        "plain overlay of two trees keeps the first claim and a relative link",
    ] {
        assert!(
            golden.fixture_names().contains(&name),
            "the golden lost the dropped fixture {name:?}"
        );
    }
    assert_eq!(golden.fixture_names().len(), LAYOUT_PARITY_FIXTURES.len() + 4);
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
        &kotlin_layout_assets_operation("layout", OUTPUT, "tree", "jcef", &normalized_layout),
    );
    let error = planfile::from_slice(plan_json(&normalized).as_bytes()).map(|_| ()).unwrap_err();
    assert!(
        format!("{error:#}").contains("unknown field `normalizeTreeModes`"),
        "{dropped}: {error:#}"
    );
}
