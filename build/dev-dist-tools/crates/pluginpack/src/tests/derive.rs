//! The port of `kotlin_derivation_test.go`, and the `pluginpack.Plan` check of the Go `mustDerive` in
//! `planfile_test.go`. The binary half of the Go derivation test is in `bins/plugin-remainder-packer/tests`.

use std::path::Path;

use planfile::contract::{Artifact, Catalogue, Library, Reference, SCOPED_VERSION, TREE_VERSION, VERSION};
use serde::Serialize;

use super::kotlin::{
    KotlinJarRecipe, KotlinJarSource, KotlinJarWriter, KotlinPlanAsset, KotlinPlanFile, KotlinPreparation, KotlinPreparedManifest,
    kotlin_layout_assets_operation, kotlin_module_filter_operation, module_source, plan_json, prepared_source, signed_plan,
};
use super::*;

/// One plan file with its raw inputs on disk. `inputs` is the Starlark-shaped input catalogue, libraries included.
/// `reused` names the modules whose plain module jar the chain reuses. `present` lists the output paths that the
/// fixture exists for.
pub(crate) struct DerivationFixture {
    pub(crate) plan: KotlinPlanFile,
    pub(crate) inputs: Catalogue,
    pub(crate) reused: Vec<String>,
    pub(crate) present: Vec<&'static str>,
}

/// A module output jar with one class under the package of the module.
pub(crate) fn module_jar(inputs: &Path, module: &str) -> Artifact {
    let jar = inputs.join(format!("{module}.jar"));
    let class = format!("{}/Main.class", module.replace('.', "/"));
    let manifest = format!("Manifest-Version: 1.0\r\nModule: {module}\r\n\r\n");
    let class: &'static str = Box::leak(class.into_boxed_str());
    archive_file(&jar, &[(class, &format!("class of {module}")), ("META-INF/MANIFEST.MF", &manifest)]);
    file_artifact(module, &jar)
}

/// One module-filter operation whose prepared source enters `lib/main.jar` beside a module source. The writer manifest
/// and the prepared manifest select the manifest policy of every source.
pub(crate) fn module_filter_fixture(inputs: &Path, manifest: &str, prepared_manifest: Option<KotlinPreparedManifest>) -> DerivationFixture {
    let raw = inputs.join("raw.jar");
    archive_file(
        &raw,
        &[
            ("keep/Service.class", "retained"),
            ("drop/Ignore.class", "excluded"),
            ("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"),
            ("META-INF/listOfEntities.txt", "keep.Service\n"),
        ],
    );
    let extra = if prepared_manifest.is_some() {
        "intellij.libraries.bar"
    } else {
        "demo.extra"
    };
    let operation_manifest = if manifest.is_empty() { "keep" } else { manifest };
    let plan = KotlinPlanFile {
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
                    ..KotlinJarWriter::default()
                },
            }),
            ..KotlinPlanAsset::default()
        }],
        preparations: vec![KotlinPreparation {
            id: "filter".to_owned(),
            inputs: strings(&["raw"]),
            outputs: strings(&["filtered:output"]),
            ..KotlinPreparation::default()
        }],
        ..KotlinPlanFile::default()
    };
    let operation = kotlin_module_filter_operation("filter", "raw", "filtered:output", operation_manifest, &strings(&["drop/**"]));
    DerivationFixture {
        plan: signed_plan(plan, &[operation]),
        inputs: catalogue(vec![file_artifact("raw", &raw), module_jar(inputs, extra)]),
        reused: Vec::new(),
        present: vec!["lib/main.jar"],
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
    let plan = KotlinPlanFile {
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
    };
    signed_plan(plan, &[])
}

/// The plan of the Go fixture of a layout-assets tree and entries beside a raw copy-tree. Its tree normalizes the
/// copied modes. No plan file does, so the `planfile` decoder refuses it.
fn layout_assets_beside_a_raw_copy_tree_plan() -> KotlinPlanFile {
    let tree = layout(
        &[Reference::artifact("archive")],
        vec![layout_asset("", &[0], Some(archive_tree(1, Vec::new())))],
    );
    let entries = layout(&[Reference::artifact("properties")], vec![layout_asset("", &[0], None)]);
    let plan = KotlinPlanFile {
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
                        ..KotlinJarWriter::default()
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
        preparations: vec![
            KotlinPreparation {
                id: "layout-tree".to_owned(),
                inputs: strings(&["archive"]),
                outputs: strings(&["layout-tree:output"]),
                ..KotlinPreparation::default()
            },
            KotlinPreparation {
                id: "layout-entries".to_owned(),
                inputs: strings(&["properties"]),
                outputs: strings(&["layout-entries:output"]),
                ..KotlinPreparation::default()
            },
        ],
        ..KotlinPlanFile::default()
    };
    signed_plan(
        plan,
        &[
            kotlin_layout_assets_operation("layout-tree", "layout-tree:output", "tree", "payload", &tree),
            kotlin_layout_assets_operation("layout-entries", "layout-entries:output", "entries", "", &entries),
        ],
    )
}

/// The plan of the Go fixture of version 3 with a distribution-scope copy. The remainder writes only plugin files, so
/// `planfile::derive` refuses it.
fn distribution_scope_copy_plan() -> KotlinPlanFile {
    let plan = KotlinPlanFile {
        version: SCOPED_VERSION,
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
    };
    signed_plan(plan, &[])
}

/// Renders the asset rows the way Go `json.MarshalIndent(assets, "", " ")` did, the bytes that the golden hashes.
fn canonical_rows(assets: &[Asset]) -> String {
    let mut buffer = Vec::new();
    let mut serializer = serde_json::Serializer::with_formatter(&mut buffer, serde_json::ser::PrettyFormatter::with_indent(b" "));
    assets.serialize(&mut serializer).unwrap();
    String::from_utf8(buffer).unwrap()
}

/// The golden record of one packed plugin directory with its asset rows and classpath record. A jar is listed by its
/// entries, sorted by name, so the record does not follow the readdir order of the host.
fn derivation_record(output: &Path, assets: &[Asset], class_path: &[u8]) -> Vec<String> {
    let mut record = Vec::new();
    for line in materialization_record(output) {
        let path = line.split('\t').next().unwrap();
        if path.ends_with(".jar") && line.contains("\tfile\t") {
            for entry in jar_entry_record(&crate::paths::host(output, path)) {
                record.push(format!("{path}!{entry}"));
            }
            continue;
        }
        record.push(line);
    }
    record.push(format!("assets.json\tjson\t-\t{}", sha256_hex(canonical_rows(assets).as_bytes())));
    record.push(format!("plugin-classpath.txt\tfile\t-\t{}", sha256_hex(class_path)));
    record
}

/// The descriptor of a fixture plugin.
pub(crate) fn fixture_descriptor(plugin: &str) -> Vec<u8> {
    format!("<idea-plugin><id>{plugin}</id><version>1</version></idea-plugin>").into_bytes()
}

/// Runs the derivation on the fixture and compares the result with the golden that the deleted Kotlin preparer wrote.
/// The result is the packed directory, the asset rows and the plugin classpath record. Five of the six golden fixtures
/// state a shape that no plan file uses. `planfile` refuses them, and the test pins the refusal that names the shape.
#[test]
fn plan_derivation_matches_the_kotlin_preparer() {
    let golden = Golden::open("kotlin-derivation");
    let inputs = temp();
    let fixture = module_filter_fixture(inputs.path(), "keep", None);
    let file = planfile::from_slice(plan_json(&fixture.plan).as_bytes()).unwrap_or_else(|error| panic!("{error}"));
    let descriptor = fixture_descriptor(&fixture.plan.plugin);
    let derivation = planfile::derive(
        &file,
        &fixture.inputs,
        &format!("plugins/{}", fixture.plan.plugin),
        &descriptor,
        fixture.plan.version,
        &fixture.reused,
        &[],
    )
    .unwrap_or_else(|error| panic!("{error}"));
    let written = write_execution(&derivation.recipe, &derivation.catalogue);
    let record = materialization_record(&written.output);
    require_inventory_matches_tree(&written.output, &written.inventory);
    require_recorded_paths(&record, &fixture.present);
    golden.check(
        "module-filter with manifest keep",
        &derivation_record(&written.output, &derivation.assets, &derivation.class_path),
    );

    let refused_inputs = temp();
    let refused = [
        (
            "jars, ownership rows, and the classpath order",
            jars_ownership_rows_and_classpath_order_plan(),
            "unknown field `symlinkTarget`",
        ),
        (
            "module-filter with manifest drop",
            module_filter_fixture(refused_inputs.path(), "drop", None).plan,
            "has the manifest \"drop\"",
        ),
        (
            "module-filter with a prepared manifest under single-meaningful-source",
            module_filter_fixture(
                refused_inputs.path(),
                "",
                Some(KotlinPreparedManifest {
                    source_manifest_policies: strings(&["keep"]),
                    ..KotlinPreparedManifest::default()
                }),
            )
            .plan,
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
            "only a reused native tree has the distribution scope",
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
                error.message().contains(shape),
                "{name}: the refusal does not name {shape:?}: {error}"
            ),
            Ok(_) => panic!("{name}: planfile accepted a shape that no plan file uses"),
        }
    }
    assert_eq!(golden.fixture_names().len(), refused.len() + 1);
}

fn plan_text(version: u32, assets: &str, sections: &[&str]) -> String {
    let mut text =
        format!(r#"{{"version": {version}, "plugin": "demo", "variant": "", "layoutSignature": "signature", "assets": [{assets}]"#);
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
  {"destination": "lib/native", "inputs": ["native-tree:demo.natives"], "kind": "tree", "classPath": false, "scope": "distribution"}"#;
    const RT_RECIPE: &str =
        r#"{"sources": [{"input": "demo.rt", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}"#;
    let main = r#"{"input": "demo.main", "kind": "module", "filter": "module-v1"}"#;
    let two = r#"{"input": "@lib//:two", "kind": "library", "filter": "library-v1"}"#;
    let foo = r#"{"input": "intellij.libraries.foo", "kind": "module", "filter": "module-v1"}"#;
    let patch = r#"{"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]}"#;
    let demo_lib = r#"{"input": "@lib//:demo-lib", "kind": "library", "filter": "library-v1"}"#;
    let rt = r#"{"input": "demo.rt", "kind": "module", "filter": "module-v1"}"#;
    let entries = r#""preparations": [{"id": "entries", "inputs": ["@lib//:one", "raw"], "outputs": ["entries:output"], "modelSignature": "e"}],
    "operations": [{"id": "entries", "kind": "layout-assets", "inputs": [{"artifact": "@lib//:one"}, {"artifact": "raw"}], "output": "entries:output", "manifest": "keep",
      "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [1], "transform": {"kind": "archive-tree"}},
        {"destination": "", "sources": [0], "transform": {"kind": "archive-tree"}}]}}]"#;
    let entries_jar = r#"{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "entries:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "keep"}}}"#;
    let every_kind_preparations = r#""preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"},
      {"id": "tree", "inputs": ["archive"], "outputs": ["tree:output"], "modelSignature": "y"},
      {"id": "entries", "inputs": ["properties"], "outputs": ["entries:output"], "modelSignature": "z"}]"#;
    let every_kind_operations = r#""operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "keep", "excludes": ["drop/**"]},
      {"id": "tree", "kind": "layout-assets", "inputs": [{"artifact": "archive"}], "output": "tree:output", "manifest": "keep",
        "layoutAssets": {"format": "tree", "root": "payload", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "archive-tree", "stripComponents": 1}}]}},
      {"id": "entries", "kind": "layout-assets", "inputs": [{"artifact": "properties"}], "output": "entries:output", "manifest": "keep",
        "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [0]}]}}]"#;
    let every_kind_assets = r#"{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"},
        {"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]}], "writer": {"manifest": "drop", "directoryEntries": true}}},
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
            "a native tree of the distribution scope",
            plan_text(3, NATIVES, &[]),
            catalogue(Vec::new()),
            3,
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
            plan_text(2, every_kind_assets, &[every_kind_preparations, every_kind_operations]),
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
        plan(&derivation.recipe, &derivation.catalogue).unwrap_or_else(|error| panic!("{name}: the derived recipe does not plan: {error}"));
    }
}
