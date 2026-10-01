//! The port of `kotlin_derivation_test.go`, and the `pluginpack.Plan` check of the Go `mustDerive` in
//! `planfile_test.go`. The binary half of the Go derivation test is in `bins/plugin-remainder-packer/tests`.

use planfile::contract::{Artifact, Catalogue, Library, Reference, TREE_VERSION, VERSION};

use super::kotlin::{
    KotlinJarRecipe, KotlinJarSource, KotlinJarWriter, KotlinPlanAsset, KotlinPlanFile, KotlinPreparedManifest,
    kotlin_layout_assets_operation, module_source, plan_json, prepared_source,
};
use super::*;

/// The kotlinx encoding of a `module-filter` operation, the retired kind. No generator writes it, so the text lives
/// here alone, and the derivation test proves that the plan file reader refuses it by name.
fn retired_module_filter_operation(id: &str, input: &str, output: &str, manifest: &str, excludes: &[&str]) -> String {
    format!(
        r#"{{"id":{},"kind":"module-filter","input":{{"artifact":{},"path":""}},"output":{},"manifest":{},"excludes":{}}}"#,
        serde_json::to_string(id).unwrap(),
        serde_json::to_string(input).unwrap(),
        serde_json::to_string(output).unwrap(),
        serde_json::to_string(manifest).unwrap(),
        serde_json::to_string(excludes).unwrap()
    )
}

/// The plan of a golden fixture of the deleted Kotlin preparer: one module-filter operation whose prepared source enters
/// `lib/main.jar` beside a module source. The reader refuses the kind, so the fixture needs no input on disk.
fn module_filter_plan(manifest: &str, prepared_manifest: Option<KotlinPreparedManifest>) -> KotlinPlanFile {
    let extra = if prepared_manifest.is_some() {
        "intellij.libraries.bar"
    } else {
        "demo.extra"
    };
    let operation_manifest = if manifest.is_empty() { "keep" } else { manifest };
    KotlinPlanFile {
        version: VERSION,
        plugin: "filtered".to_owned(),
        assets: vec![KotlinPlanAsset {
            destination: "lib/main.jar".to_owned(),
            recipe: Some(KotlinJarRecipe {
                sources: vec![
                    KotlinJarSource {
                        prepared_manifest,
                        ..prepared_source("filtered:output")
                    },
                    module_source(extra),
                ],
                writer: KotlinJarWriter {
                    manifest: manifest.to_owned(),
                    merge_entities: true,
                },
            }),
            ..KotlinPlanAsset::default()
        }],
        operations: vec![retired_module_filter_operation(
            "filter",
            "raw",
            "filtered:output",
            operation_manifest,
            &["drop/**"],
        )],
        ..KotlinPlanFile::default()
    }
}

/// The plan of the Go fixture of jars, ownership rows and the classpath order. It states a link and a directory
/// asset. No plan file states either, so the `planfile` decoder refuses it before any input is read.
fn jars_ownership_rows_and_classpath_order_plan() -> KotlinPlanFile {
    let rt = KotlinJarRecipe {
        sources: vec![module_source("demo.rt")],
        writer: KotlinJarWriter {
            merge_entities: true,
            ..KotlinJarWriter::default()
        },
    };
    let jar = |destination: &str, sources: Vec<KotlinJarSource>| KotlinPlanAsset {
        destination: destination.to_owned(),
        recipe: Some(KotlinJarRecipe {
            sources,
            writer: KotlinJarWriter {
                merge_entities: true,
                ..KotlinJarWriter::default()
            },
        }),
        ..KotlinPlanAsset::default()
    };
    let library = |input: &str| KotlinJarSource {
        input: input.to_owned(),
        kind: "library".to_owned(),
        filter: "library-v1".to_owned(),
        ..KotlinJarSource::default()
    };
    KotlinPlanFile {
        version: VERSION,
        plugin: "demo".to_owned(),
        assets: vec![
            KotlinPlanAsset {
                module: "demo.content".to_owned(),
                ..KotlinPlanAsset::default()
            },
            jar(
                "lib/demo.jar",
                vec![
                    library("@lib//:two"),
                    module_source("demo.main"),
                    KotlinJarSource {
                        input: "descriptor".to_owned(),
                        kind: "file".to_owned(),
                        filter: "none".to_owned(),
                        entry: "META-INF/plugin.xml".to_owned(),
                        options: strings(&["patch"]),
                        ..KotlinJarSource::default()
                    },
                ],
            ),
            KotlinPlanAsset {
                destination: "lib/rt.jar".to_owned(),
                recipe: Some(rt.clone()),
                ..KotlinPlanAsset::default()
            },
            KotlinPlanAsset {
                destination: "lib/rt-exec.jar".to_owned(),
                recipe: Some(rt),
                mode: 0o755,
                ..KotlinPlanAsset::default()
            },
            jar(
                "lib/intellij.libraries.foo.jar",
                vec![library("@lib//:one"), module_source("intellij.libraries.foo")],
            ),
            KotlinPlanAsset {
                class_path: Some(false),
                ..jar("lib/side.jar", vec![module_source("demo.side")])
            },
            jar("lib/nested/inner.jar", vec![module_source("demo.main")]),
            KotlinPlanAsset {
                destination: "bin/tool".to_owned(),
                inputs: Some(strings(&["native"])),
                mode: 0o755,
                ..KotlinPlanAsset::default()
            },
            KotlinPlanAsset {
                destination: "bin/current".to_owned(),
                inputs: Some(Vec::new()),
                symlink_target: Some("./tool".to_owned()),
                ..KotlinPlanAsset::default()
            },
            KotlinPlanAsset {
                destination: "lib/empty".to_owned(),
                inputs: Some(Vec::new()),
                kind: "directory".to_owned(),
                ..KotlinPlanAsset::default()
            },
        ],
        ..KotlinPlanFile::default()
    }
}

/// The plan of the Go fixture of a layout-assets tree and entries beside a raw copy-tree. Its tree normalizes the
/// copied modes. No plan file does, so the `planfile` decoder refuses it.
fn layout_assets_beside_a_raw_copy_tree_plan() -> KotlinPlanFile {
    let tree = layout(
        &[Reference::artifact("archive")],
        vec![layout_asset("", &[0], Some(archive_tree(1, Vec::new())))],
    );
    let entries = layout(&[Reference::artifact("properties")], vec![layout_asset("", &[0], None)]);
    KotlinPlanFile {
        version: TREE_VERSION,
        plugin: "layout".to_owned(),
        assets: vec![
            KotlinPlanAsset {
                destination: "payload".to_owned(),
                inputs: Some(strings(&["layout-tree:output"])),
                kind: "tree".to_owned(),
                class_path: Some(false),
                normalize_tree_modes: true,
                ..KotlinPlanAsset::default()
            },
            KotlinPlanAsset {
                destination: "lib/localization.jar".to_owned(),
                recipe: Some(KotlinJarRecipe {
                    sources: vec![prepared_source("layout-entries:output"), module_source("demo.l10n")],
                    writer: KotlinJarWriter {
                        manifest: "drop".to_owned(),
                        merge_entities: true,
                    },
                }),
                ..KotlinPlanAsset::default()
            },
            KotlinPlanAsset {
                destination: "lib/standardDsls".to_owned(),
                inputs: Some(strings(&["dsls"])),
                kind: "tree".to_owned(),
                class_path: Some(false),
                ..KotlinPlanAsset::default()
            },
        ],
        operations: vec![
            kotlin_layout_assets_operation("layout-tree", "layout-tree:output", "tree", "payload", &tree),
            kotlin_layout_assets_operation("layout-entries", "layout-entries:output", "entries", "", &entries),
        ],
        ..KotlinPlanFile::default()
    }
}

/// The plan of the Go fixture of version 3 with a distribution-scope copy. The distribution scope is retired, so
/// `planfile` refuses the `scope` key.
fn distribution_scope_copy_plan() -> KotlinPlanFile {
    KotlinPlanFile {
        version: 3,
        plugin: "scoped".to_owned(),
        assets: vec![
            KotlinPlanAsset {
                destination: "lib/scoped.jar".to_owned(),
                recipe: Some(KotlinJarRecipe {
                    sources: vec![module_source("demo.main")],
                    writer: KotlinJarWriter {
                        merge_entities: true,
                        ..KotlinJarWriter::default()
                    },
                }),
                ..KotlinPlanAsset::default()
            },
            KotlinPlanAsset {
                destination: "bin/launcher.sh".to_owned(),
                inputs: Some(strings(&["launcher"])),
                mode: 0o755,
                class_path: Some(false),
                scope: "distribution".to_owned(),
                ..KotlinPlanAsset::default()
            },
        ],
        ..KotlinPlanFile::default()
    }
}

/// Every golden fixture of the deleted Kotlin preparer states a shape that no plan file uses. `planfile` refuses each
/// one, and the test pins the refusal that names the shape. The module-filter fixtures are refused by the retired kind,
/// or by a field of the retired kind.
#[test]
fn plan_derivation_refuses_every_kotlin_preparer_fixture() {
    let golden = Golden::open("kotlin-derivation");
    let refused = [
        (
            "jars, ownership rows, and the classpath order",
            jars_ownership_rows_and_classpath_order_plan(),
            "unknown field `symlinkTarget`",
        ),
        (
            "module-filter with manifest keep",
            module_filter_plan("keep", None),
            "unknown field `input`",
        ),
        (
            "module-filter with manifest drop",
            module_filter_plan("drop", None),
            "unknown field `input`",
        ),
        (
            "module-filter with a prepared manifest under single-meaningful-source",
            module_filter_plan(
                "",
                Some(KotlinPreparedManifest {
                    source_manifest_policies: strings(&["keep"]),
                    ..KotlinPreparedManifest::default()
                }),
            ),
            "unknown field `preparedManifest`",
        ),
        (
            "layout-assets tree and entries beside a raw copy-tree",
            layout_assets_beside_a_raw_copy_tree_plan(),
            "unknown field `normalizeTreeModes`",
        ),
        (
            "version 3 with a distribution-scope asset",
            distribution_scope_copy_plan(),
            "unknown field `scope`",
        ),
    ];
    for (name, plan, shape) in &refused {
        assert!(golden.fixture_names().contains(name), "the golden lost the fixture {name:?}");
        let result = planfile::from_slice(plan_json(plan).as_bytes()).and_then(|file| {
            planfile::derive(
                &file,
                &catalogue(Vec::new()),
                &format!("plugins/{}", plan.plugin),
                b"<idea-plugin/>",
                plan.version,
                &[],
                &[],
            )
        });
        match result {
            Err(error) => assert!(
                format!("{error:#}").contains(shape),
                "{name}: the refusal does not name {shape:?}: {error:#}"
            ),
            Ok(_) => panic!("{name}: planfile accepted a shape that no plan file uses"),
        }
    }
    assert_eq!(golden.fixture_names().len(), refused.len());
}

fn plan_text(version: u32, assets: &str, sections: &[&str]) -> String {
    let mut text = format!(r#"{{"version": {version}, "plugin": "demo", "variant": "", "assets": [{assets}]"#);
    for section in sections {
        text.push_str(", ");
        text.push_str(section);
    }
    text.push('}');
    text
}

fn inputs_artifact(id: &str) -> Artifact {
    file_artifact(id, format!("inputs/{}", id.replace('/', "_")))
}

fn inputs_directory(id: &str) -> Artifact {
    directory_artifact(id, format!("inputs/{id}"))
}

fn library(id: &str, members: &[&str]) -> Library {
    Library {
        id: id.to_owned(),
        files: members.iter().map(|member| Reference::artifact(*member)).collect(),
    }
}

/// The Go `mustDerive` also planned every derivation of `planfile_test.go`. The `planfile` crate cannot depend on this
/// crate, so this test plans the derivations of its tests: the plan-file shapes that `planfile` accepts must plan. The
/// derivation of a library input of two members has no case here. No layout transform reads two archive files.
#[test]
fn every_planfile_derivation_plans() {
    const NATIVES: &str = r#"{"destination": "lib/modules/demo.natives.jar", "recipe": {"sources": [{"input": "demo.natives", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true, "nativeLib": "native"}}},
  {"destination": "lib/native", "inputs": ["native-tree:demo.natives"], "kind": "tree", "classPath": false}"#;
    const RT_RECIPE: &str =
        r#"{"sources": [{"input": "demo.rt", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}"#;
    let main = r#"{"input": "demo.main", "kind": "module", "filter": "module-v1"}"#;
    let two = r#"{"input": "@lib//:two", "kind": "library", "filter": "library-v1"}"#;
    let foo = r#"{"input": "intellij.libraries.foo", "kind": "module", "filter": "module-v1"}"#;
    let patch = r#"{"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]}"#;
    let demo_lib = r#"{"input": "@lib//:demo-lib", "kind": "library", "filter": "library-v1"}"#;
    let rt = r#"{"input": "demo.rt", "kind": "module", "filter": "module-v1"}"#;
    let entries = r#""operations": [{"id": "entries", "kind": "layout-assets", "inputs": [{"artifact": "@lib//:one"}, {"artifact": "raw"}], "output": "entries:output", "manifest": "keep",
      "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [1], "transform": {"kind": "archive-tree"}},
        {"destination": "", "sources": [0], "transform": {"kind": "archive-tree"}}]}}]"#;
    let entries_jar = r#"{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "entries:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "keep"}}}"#;
    let every_kind_operations = r#""operations": [{"id": "filter", "kind": "layout-assets", "inputs": [{"artifact": "raw"}], "output": "filtered", "manifest": "keep",
        "layoutAssets": {"format": "entries", "assets": [{"destination": "raw.txt", "sources": [0]}]}},
      {"id": "tree", "kind": "layout-assets", "inputs": [{"artifact": "archive"}], "output": "tree:output", "manifest": "keep",
        "layoutAssets": {"format": "tree", "root": "payload", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "archive-tree", "stripComponents": 1}}]}},
      {"id": "entries", "kind": "layout-assets", "inputs": [{"artifact": "properties"}], "output": "entries:output", "manifest": "keep",
        "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [0]}]}}]"#;
    let every_kind_assets = r#"{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"},
        {"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]}], "writer": {"manifest": "drop"}}},
      {"destination": "lib/l10n.jar", "recipe": {"sources": [{"input": "entries:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "keep"}}},
      {"destination": "payload", "inputs": ["tree:output"], "kind": "tree", "classPath": false},
      {"destination": "lib/standardDsls", "inputs": ["dsls"], "kind": "tree", "classPath": false},
      {"destination": "bin/tool", "inputs": ["native"], "mode": 493}"#;
    let mut two_library = catalogue(vec![
        inputs_artifact("demo.main"),
        inputs_artifact("intellij.libraries.foo"),
        inputs_artifact("@lib//:two/a.jar"),
        inputs_artifact("@lib//:two/b.jar"),
        inputs_artifact("descriptor"),
    ]);
    two_library.libraries = vec![library("@lib//:two", &["@lib//:two/a.jar", "@lib//:two/b.jar"])];
    let mut demo_library = catalogue(vec![inputs_artifact("demo.rt"), inputs_artifact("@lib//:demo-lib/a.jar")]);
    demo_library.libraries = vec![library("@lib//:demo-lib", &["@lib//:demo-lib/a.jar"])];
    let one_library = |members: &[&str]| {
        let mut inputs = catalogue(members.iter().map(|member| inputs_artifact(member)).collect());
        inputs.artifacts.push(inputs_artifact("raw"));
        inputs.libraries = vec![library("@lib//:one", members)];
        inputs
    };
    let scenarios: Vec<(&str, String, Catalogue, u32, &[&str])> = vec![
        (
            "files",
            plan_text(1, r#"{"destination": "bin/tool", "inputs": ["tool"]}"#, &[]),
            catalogue(vec![inputs_artifact("tool")]),
            1,
            &[],
        ),
        (
            "a tree",
            plan_text(
                2,
                r#"{"destination": "lib/tree", "inputs": ["tree"], "kind": "tree", "classPath": false}"#,
                &[],
            ),
            catalogue(vec![inputs_directory("tree")]),
            2,
            &[],
        ),
        (
            "a native tree",
            plan_text(2, NATIVES, &[]),
            catalogue(Vec::new()),
            2,
            &["demo.natives"],
        ),
        (
            "a reused module jar that merges libraries",
            plan_text(
                1,
                &format!(
                    r#"{{"destination": "lib/modules/demo.rt.jar", "recipe": {{"sources": [{rt}, {demo_lib}], "writer": {{"mergeEntities": true}}}}}},
            {{"destination": "lib/rt-first.jar", "recipe": {{"sources": [{demo_lib}, {rt}], "writer": {{"mergeEntities": true}}}}}},
            {{"destination": "lib/rt-kept.jar", "recipe": {{"sources": [{rt}, {demo_lib}], "writer": {{"manifest": "keep", "mergeEntities": true}}}}}}"#
                ),
                &[],
            ),
            demo_library,
            1,
            &["demo.rt"],
        ),
        (
            "ownership by recipe and mode",
            plan_text(
                1,
                &format!(
                    r#"{{"module": "demo.content"}}, {{"destination": "lib/rt.jar", "recipe": {RT_RECIPE}}},
            {{"destination": "lib/rt-exec.jar", "recipe": {RT_RECIPE}, "mode": 493}},
            {{"destination": "lib/rt-kept.jar", "recipe": {{"sources": [{rt}], "writer": {{"manifest": "keep", "mergeEntities": true}}}}}}"#
                ),
                &[],
            ),
            catalogue(vec![inputs_artifact("demo.rt")]),
            1,
            &["demo.content", "demo.rt"],
        ),
        (
            "meaningful sources",
            plan_text(
                1,
                &format!(
                    r#"{{"destination": "lib/one.jar", "recipe": {{"sources": [{main}]}}}},
            {{"destination": "lib/library.jar", "recipe": {{"sources": [{two}]}}}},
            {{"destination": "lib/lib-module.jar", "recipe": {{"sources": [{foo}, {main}]}}}},
            {{"destination": "lib/patched.jar", "recipe": {{"sources": [{main}, {patch}]}}}},
            {{"destination": "lib/kept.jar", "recipe": {{"sources": [{main}, {two}], "writer": {{"manifest": "keep"}}}}}}"#
                ),
                &[],
            ),
            two_library,
            1,
            &[],
        ),
        (
            "a library input of one member",
            plan_text(1, entries_jar, &[entries]),
            one_library(&["@lib//:one/one-1.0.jar"]),
            1,
            &[],
        ),
        (
            "every operation kind",
            plan_text(2, every_kind_assets, &[every_kind_operations]),
            catalogue(vec![
                inputs_artifact("raw"),
                inputs_artifact("descriptor"),
                inputs_artifact("native"),
                inputs_directory("dsls"),
                inputs_artifact("archive"),
                inputs_directory("properties"),
            ]),
            2,
            &[],
        ),
        (
            "the plan scope classpath",
            plan_text(
                1,
                r#"{"destination": "lib/util.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
            {"destination": "lib/demo.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
            {"destination": "lib/modules/demo.main.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
            {"destination": "lib/hidden.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}, "classPath": false},
            {"destination": "lib/data.txt", "inputs": ["run"]},
            {"module": "demo.content"}"#,
                &[],
            ),
            catalogue(vec![inputs_artifact("demo.main"), inputs_artifact("run")]),
            1,
            &["demo.content"],
        ),
    ];
    for (name, text, inputs, version, independent) in scenarios {
        let file = planfile::from_slice(text.as_bytes()).unwrap_or_else(|error| panic!("{name}: {error}"));
        let derivation = planfile::derive(
            &file,
            &inputs,
            "plugins/demo",
            b"<idea-plugin/>",
            version,
            &strings(independent),
            &[],
        )
        .unwrap_or_else(|error| panic!("{name}: {error}"));
        plan(&derivation.recipe, &derivation.catalogue)
            .unwrap_or_else(|error| panic!("{name}: the derived recipe does not plan: {error:#}"));
    }
}
