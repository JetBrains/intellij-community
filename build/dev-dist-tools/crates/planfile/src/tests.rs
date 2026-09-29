//! The port of `planfile_test.go`. The Go `mustDerive` also plans the derived recipe with `pluginpack.Plan`. That check
//! needs the `pluginpack` crate, so its tests hold it.

mod corpus;

use crate::contract::{
    self, Artifact, Catalogue, Filter, LayoutAsset, LayoutAssets, LayoutTransform, LayoutTransformKind, Library, Manifest, Reference,
    Source,
};
use crate::plan::LAYOUT_ASSETS_KIND;
use crate::{
    DEFAULT_MODE, Derivation, EXECUTABLE_MODE, Error, JarWriter, ManifestPolicy, PlanFile, classpath, derive, module_jar_asset, read,
};

fn read_plan(text: &str) -> Result<PlanFile, Error> {
    let directory = tempfile::tempdir().unwrap();
    let file = directory.path().join("plan.json");
    std::fs::write(&file, text).unwrap();
    read(&file)
}

fn must_read_plan(text: &str) -> PlanFile {
    read_plan(text).unwrap_or_else(|error| panic!("{error}"))
}

/// Wraps the assets and the optional sections into one neutral plan file text.
fn plan(version: u32, assets: &str, sections: &[&str]) -> String {
    let mut text =
        format!(r#"{{"version": {version}, "plugin": "demo", "variant": "", "layoutSignature": "signature", "assets": [{assets}]"#);
    for section in sections {
        text.push_str(", ");
        text.push_str(section);
    }
    text.push('}');
    text
}

fn file_artifact(id: &str) -> Artifact {
    Artifact {
        id: id.to_owned(),
        kind: "file".to_owned(),
        root: format!("inputs/{}", id.replace('/', "_")),
    }
}

fn directory_artifact(id: &str) -> Artifact {
    Artifact {
        id: id.to_owned(),
        kind: "directory".to_owned(),
        root: format!("inputs/{id}"),
    }
}

fn catalogue(artifacts: Vec<Artifact>) -> Catalogue {
    Catalogue {
        version: contract::VERSION,
        artifacts,
        libraries: Vec::new(),
    }
}

fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

/// A layout-assets entries operation over the raw input. Its output is the one prepared source of [`FILTERED_JAR`].
const ENTRIES_OPERATION: &str = r#"{"id": "filter", "kind": "layout-assets", "inputs": [{"artifact": "raw"}], "output": "filtered", "manifest": "keep", "layoutAssets": {"format": "entries", "assets": [{"destination": "raw.txt", "sources": [0]}]}}"#;

const ENTRIES_SECTION: &str = r#""preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"}],
  "operations": [{"id": "filter", "kind": "layout-assets", "inputs": [{"artifact": "raw"}], "output": "filtered", "manifest": "keep", "layoutAssets": {"format": "entries", "assets": [{"destination": "raw.txt", "sources": [0]}]}}]"#;

const FILTERED_JAR: &str = r#"{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "drop"}}}"#;

const RT_RECIPE: &str =
    r#"{"sources": [{"input": "demo.rt", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}"#;

/// A reused natives jar and its native tree next to the jar.
const NATIVES: &str = r#"{"destination": "lib/modules/demo.natives.jar", "recipe": {"sources": [{"input": "demo.natives", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true, "nativeLib": "native"}}},
  {"destination": "lib/native", "inputs": ["native-tree:demo.natives"], "kind": "tree", "classPath": false}"#;

fn derive_plan(text: &str, inputs: &Catalogue, version: u32, independent_modules: &[&str]) -> Result<Derivation, Error> {
    derive_refusing(text, inputs, version, independent_modules, &[])
}

fn derive_refusing(
    text: &str,
    inputs: &Catalogue,
    version: u32,
    independent_modules: &[&str],
    refused_modules: &[&str],
) -> Result<Derivation, Error> {
    derive(
        &must_read_plan(text),
        inputs,
        "plugins/demo",
        b"<idea-plugin/>",
        version,
        &strings(independent_modules),
        &strings(refused_modules),
    )
}

fn must_derive(text: &str, inputs: &Catalogue, version: u32, independent_modules: &[&str]) -> Derivation {
    derive_plan(text, inputs, version, independent_modules).unwrap_or_else(|error| panic!("{error}"))
}

fn expect_error(result: Result<Derivation, Error>, message: &str) {
    match result {
        Ok(_) => panic!("expected {message:?}, got a derivation"),
        Err(error) => assert!(error.message().contains(message), "expected {message:?}, got {error}"),
    }
}

fn row(destination: &str, producer: &str, artifact: &str) -> contract::Asset {
    contract::Asset {
        destination: destination.to_owned(),
        producer: producer.to_owned(),
        artifact: artifact.to_owned(),
        ..contract::Asset::default()
    }
}

fn tree_row(destination: &str, producer: &str, artifact: &str) -> contract::Asset {
    contract::Asset {
        kind: "tree".to_owned(),
        class_path: Some(false),
        ..row(destination, producer, artifact)
    }
}

fn jar(destination: &str, directory_entries: bool, sources: Vec<Source>) -> contract::Operation {
    contract::Operation::Jar {
        destination: destination.to_owned(),
        mode: DEFAULT_MODE,
        sources,
        merge_entities: false,
        directory_entries,
    }
}

fn archive(input: &str, filter: Filter, manifest: Manifest) -> Source {
    Source::Archive {
        input: Reference::artifact(input),
        filter,
        manifest,
    }
}

fn layout_source(inputs: &[&str], assets: Vec<LayoutAsset>) -> Source {
    Source::Layout(LayoutAssets {
        inputs: inputs.iter().map(|input| Reference::artifact(*input)).collect(),
        assets,
    })
}

/// The one layout asset of [`ENTRIES_OPERATION`]: the plain copy of the raw input at `raw.txt`.
fn raw_entry() -> LayoutAsset {
    LayoutAsset {
        destination: "raw.txt".to_owned(),
        sources: vec![0],
        transform: None,
        mode: 0,
    }
}

fn transform_asset(sources: Vec<usize>, transform: LayoutTransform) -> LayoutAsset {
    LayoutAsset {
        destination: String::new(),
        sources,
        transform: Some(transform),
        mode: 0,
    }
}

fn transform(kind: LayoutTransformKind) -> LayoutTransform {
    LayoutTransform {
        kind,
        strip_components: 0,
        mappings: Vec::new(),
        includes: Vec::new(),
        executables: Vec::new(),
    }
}

#[test]
fn read_expands_the_compact_forms() {
    let file = must_read_plan(&plan(
        2,
        r#"{"module": "demo.content"},
    {"destination": "lib/demo.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}},
    {"destination": "bin/tool", "inputs": ["native"], "mode": 493, "classPath": false},
    {"destination": "lib/native", "inputs": ["native-tree:demo.natives"], "kind": "tree", "classPath": false}"#,
        &[&format!(r#""operations": [{ENTRIES_OPERATION}]"#)],
    ));
    let content = module_jar_asset("demo.content");
    assert_eq!(file.assets[0], content);
    let writer = &content.recipe.as_ref().unwrap().writer;
    assert_eq!(content.destination, "lib/modules/demo.content.jar");
    assert_eq!(content.inputs, ["demo.content"]);
    assert_eq!(
        (content.mode, content.kind.as_str(), content.class_path),
        (DEFAULT_MODE, "file", true)
    );
    assert_eq!(
        (writer.manifest, writer.merge_entities),
        (ManifestPolicy::SingleMeaningfulSource, true)
    );

    let demo = &file.assets[1];
    assert_eq!(demo.inputs, ["demo.main"], "the inputs of a recipe asset repeat its sources");
    assert_eq!((demo.mode, demo.kind.as_str(), demo.class_path), (DEFAULT_MODE, "file", true));

    let tool = &file.assets[2];
    assert_eq!(tool.inputs, ["native"]);
    assert_eq!((tool.mode, tool.class_path), (EXECUTABLE_MODE, false));
    assert!(tool.recipe.is_none());

    let native = &file.assets[3];
    assert_eq!((native.kind.as_str(), native.class_path), ("tree", false));

    let operation = &file.operations[0];
    assert_eq!(operation.kind, LAYOUT_ASSETS_KIND);
    assert_eq!(operation.inputs, [Reference::artifact("raw")]);
    assert_eq!(operation.layout_assets.root, "", "an entries operation has no tree root");
    assert_eq!(operation.layout_assets.assets[0].destination, "raw.txt");
}

/// The Go table, with a message for each refusal. The decoder refuses every shape that no checked-in plan file uses.
#[test]
fn read_refuses_malformed_forms() {
    let one_module = r#"{"module": "m"}"#;
    let asset = |text: &str| plan(1, text, &[]);
    let source = |text: &str| {
        plan(
            1,
            &format!(r#"{{"destination": "lib/x.jar", "recipe": {{"sources": [{text}]}}}}"#),
            &[],
        )
    };
    let writer = |text: &str| {
        plan(
            1,
            &format!(
                r#"{{"destination": "lib/x.jar", "recipe": {{"sources": [{{"input": "m", "kind": "module", "filter": "module-v1"}}], "writer": {text}}}}}"#
            ),
            &[],
        )
    };
    let operation = |text: &str| plan(1, one_module, &[&format!(r#""operations": [{text}]"#)]);
    let preparation = |text: &str| plan(1, one_module, &[&format!(r#""preparations": [{text}]"#)]);
    for (name, text, message) in [
        (
            "a module asset with another field",
            asset(r#"{"module": "m", "destination": "lib/x.jar"}"#),
            r#"the module jar asset "m" states more than its module"#,
        ),
        (
            "a module asset that states a default",
            asset(r#"{"module": "m", "mode": 420, "classPath": true}"#),
            "states more than its module",
        ),
        (
            "an asset without destination",
            asset(r#"{"inputs": ["x"]}"#),
            "requires a destination or a module",
        ),
        (
            "an asset without inputs and recipe",
            asset(r#"{"destination": "bin/tool"}"#),
            "without inputs requires a recipe",
        ),
        (
            "inputs next to a recipe",
            asset(
                r#"{"destination": "lib/x.jar", "inputs": ["m"], "recipe": {"sources": [{"input": "m", "kind": "module", "filter": "module-v1"}]}}"#,
            ),
            "states inputs and a recipe",
        ),
        (
            "an unknown asset field",
            asset(r#"{"destination": "bin/tool", "inputs": ["x"], "producer": "remainder"}"#),
            "unknown field `producer`",
        ),
        (
            "a link",
            asset(r#"{"destination": "bin/current", "inputs": [], "symlinkTarget": "./tool"}"#),
            "unknown field `symlinkTarget`",
        ),
        (
            "a tree with normalized modes",
            asset(r#"{"destination": "t", "inputs": ["t"], "kind": "tree", "classPath": false, "normalizeTreeModes": true}"#),
            "unknown field `normalizeTreeModes`",
        ),
        (
            "a directory asset",
            asset(r#"{"destination": "lib/empty", "inputs": [], "kind": "directory"}"#),
            r#"has the kind "directory"; the packer writes only file and tree assets"#,
        ),
        (
            "the retired distribution scope",
            asset(r#"{"destination": "bin/tool", "inputs": ["x"], "scope": "distribution"}"#),
            "unknown field `scope`",
        ),
        (
            "the plugin scope",
            asset(r#"{"destination": "bin/tool", "inputs": ["x"], "scope": "plugin"}"#),
            "unknown field `scope`",
        ),
        (
            "another mode",
            asset(r#"{"destination": "bin/tool", "inputs": ["x"], "mode": 384}"#),
            "has the mode 600; the packer writes only the modes 644 and 755",
        ),
        (
            "a reusable artifact",
            plan(1, one_module, &[r#""reusableArtifacts": [{"label": "l", "module": "m"}]"#]),
            "unknown field `reusableArtifacts`",
        ),
        (
            "preparation roots",
            plan(1, one_module, &[r#""preparationRoots": ["x"]"#]),
            "unknown field `preparationRoots`",
        ),
        (
            "a recipe without sources",
            asset(r#"{"destination": "lib/x.jar", "recipe": {"sources": []}}"#),
            "requires ordered sources",
        ),
        (
            "a library source with an expansion",
            source(r#"{"input": "l", "kind": "library", "filter": "library-v1", "expansion": ["l/a.jar"]}"#),
            "unknown field `expansion`",
        ),
        (
            "a prepared manifest",
            source(r#"{"input": "p", "kind": "prepared", "filter": "prepared", "preparedManifest": {"sourceManifestPolicies": ["keep"]}}"#),
            "unknown field `preparedManifest`",
        ),
        (
            "a zip source",
            source(r#"{"input": "z", "kind": "zip", "filter": "none"}"#),
            r#"has the kind "zip""#,
        ),
        (
            "a module source with another filter",
            source(r#"{"input": "m", "kind": "module", "filter": "none"}"#),
            r#"has the filter "none"; the packer reads it only with module-v1"#,
        ),
        (
            "a lib-module option",
            source(r#"{"input": "m", "kind": "module", "filter": "module-v1", "options": ["lib-module"]}"#),
            r#"the options ["lib-module"]; only a file source has an entry"#,
        ),
        (
            "a manifest option",
            source(r#"{"input": "m", "kind": "module", "filter": "module-v1", "options": ["manifest=drop"]}"#),
            r#"the options ["manifest=drop"]"#,
        ),
        (
            "a file source without patch",
            source(r#"{"input": "d", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml"}"#),
            "it requires the option patch",
        ),
        (
            "a file source without entry",
            source(r#"{"input": "d", "kind": "file", "filter": "none", "options": ["patch"]}"#),
            r#"has the entry "" and the options ["patch"]"#,
        ),
        (
            "a boot class path rewrite",
            writer(r#"{"rewriteBootClassPath": true}"#),
            "unknown field `rewriteBootClassPath`",
        ),
        ("an output name", writer(r#"{"outputName": "x.jar"}"#), "unknown field `outputName`"),
        (
            "a coverage agent manifest",
            writer(r#"{"manifest": "coverage-agent"}"#),
            "unknown variant `coverage-agent`",
        ),
        ("an empty native library", writer(r#"{"nativeLib": ""}"#), "empty native library"),
        (
            "a Kotlin operation kind",
            operation(r#"{"id": "n", "kind": "native-archive", "output": "o", "manifest": "keep"}"#),
            r#"has the kind "native-archive"; the packer executes only layout-assets"#,
        ),
        (
            "the retired module-filter kind",
            operation(r#"{"id": "n", "kind": "module-filter", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "keep"}"#),
            r#"operation "n" has the kind "module-filter"; the packer executes only layout-assets"#,
        ),
        (
            "the primary input of the retired module-filter kind",
            operation(
                r#"{"id": "n", "kind": "module-filter", "input": {"artifact": "a"}, "output": "o", "manifest": "keep", "excludes": ["drop/**"]}"#,
            ),
            "unknown field `input`",
        ),
        (
            "an operation without a kind",
            operation(r#"{"id": "n", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "keep"}"#),
            "missing field `kind`",
        ),
        (
            "a Kotlin operation field",
            operation(
                r#"{"id": "n", "kind": "native-presigned", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "keep", "filter": "library"}"#,
            ),
            "unknown field `filter`",
        ),
        (
            "a callback operation",
            operation(r#"{"id": "n", "kind": "library-layout-patches", "output": "o", "manifest": "keep", "libraryLayout": {"any": 1}}"#),
            "unknown field `libraryLayout`",
        ),
        (
            "a Kotlin field with the value null",
            operation(
                r#"{"id": "n", "kind": "layout-assets", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "keep", "entry": null}"#,
            ),
            "unknown field `entry`",
        ),
        (
            "an operation that drops the manifest",
            operation(
                r#"{"id": "n", "kind": "layout-assets", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "drop",
          "layoutAssets": {"format": "tree", "assets": []}}"#,
            ),
            r#"has the manifest "drop"; the packer keeps the manifest of a prepared output"#,
        ),
        (
            "a layout-assets without layout assets",
            operation(r#"{"id": "n", "kind": "layout-assets", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "keep"}"#),
            r#"layout-assets operation "n" requires layoutAssets"#,
        ),
        (
            "a layout file",
            operation(
                r#"{"id": "n", "kind": "layout-assets", "output": "o", "manifest": "keep",
          "layoutAssets": {"format": "file", "root": "x.txt", "assets": []}}"#,
            ),
            "unknown variant `file`, expected `tree` or `entries`",
        ),
        (
            "an inline-text transform",
            operation(
                r#"{"id": "n", "kind": "layout-assets", "output": "o", "manifest": "keep",
          "layoutAssets": {"format": "entries", "assets": [{"destination": "x.txt", "transform": {"kind": "inline-text", "text": "1"}}]}}"#,
            ),
            "unknown variant `inline-text`",
        ),
        (
            "a root of entries",
            operation(
                r#"{"id": "n", "kind": "layout-assets", "output": "o", "manifest": "keep",
          "layoutAssets": {"format": "entries", "root": "x", "assets": []}}"#,
            ),
            "must not declare a tree root for its entries",
        ),
        (
            "a preparation without signature",
            preparation(r#"{"id": "p", "inputs": [], "outputs": ["o"]}"#),
            "missing field `modelSignature`",
        ),
        (
            "an always-run preparation",
            preparation(r#"{"id": "p", "inputs": [], "outputs": ["o"], "modelSignature": "x", "alwaysRun": true}"#),
            "unknown field `alwaysRun`",
        ),
        (
            "no version",
            r#"{"plugin": "demo", "variant": "", "layoutSignature": "s", "assets": []}"#.to_owned(),
            "missing field `version`",
        ),
        (
            "a duplicate key",
            r#"{"version": 1, "version": 1, "plugin": "demo", "variant": "", "layoutSignature": "s", "assets": []}"#.to_owned(),
            "duplicate field `version`",
        ),
        (
            "a nested duplicate key",
            asset(r#"{"destination": "bin/tool", "inputs": ["x"], "mode": 493, "mode": 420}"#),
            "duplicate field `mode`",
        ),
        (
            "a second document",
            format!("{} {{}}", plan(1, one_module, &[])),
            "trailing characters",
        ),
        (
            "null for a list",
            plan(1, one_module, &[r#""preparations": null"#]),
            "invalid type: null, expected a sequence",
        ),
    ] {
        match read_plan(&text) {
            Ok(_) => panic!("{name}: accepted {text}"),
            Err(error) => assert!(error.message().contains(message), "{name}: expected {message:?}, got {error}"),
        }
    }
}

#[test]
fn read_names_the_file_and_the_operation() {
    let error = read_plan(&plan(
        1,
        r#"{"module": "m"}"#,
        &[r#""operations": [{"id": "n", "kind": "native-presigned", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "keep"}]"#],
    ))
    .unwrap_err();
    assert!(
        error.message().contains("plan.json: operation 0: "),
        "the refusal names the file and the operation: {error}"
    );
}

/// A `null` for a Go pointer field is an absent key.
#[test]
fn read_treats_null_as_absent_for_an_optional_field() {
    let file = must_read_plan(&plan(
        1,
        r#"{"module": null, "destination": "bin/tool", "inputs": null,
      "recipe": {"sources": [{"input": "x", "kind": "module", "filter": "module-v1", "entry": null}], "writer": null},
      "mode": null, "kind": null, "classPath": null},
    {"destination": "lib/y.jar", "recipe": {"sources": [{"input": "y", "kind": "module", "filter": "module-v1"}],
      "writer": {"manifest": null, "mergeEntities": null, "directoryEntries": null, "nativeLib": null}}}"#,
        &[
            r#""operations": [{"id": "n", "kind": "layout-assets", "inputs": [{"artifact": "a"}], "output": "o", "manifest": "keep",
          "layoutAssets": {"format": "tree", "root": null, "assets": [{"destination": "", "sources": [0], "transform": null}]}}]"#,
        ],
    ));
    let tool = &file.assets[0];
    assert_eq!(tool.inputs, ["x"]);
    assert_eq!((tool.mode, tool.kind.as_str(), tool.class_path), (DEFAULT_MODE, "file", true));
    assert_eq!(tool.recipe.as_ref().unwrap().writer, JarWriter::default());
    assert_eq!(file.assets[1].recipe.as_ref().unwrap().writer, JarWriter::default());
    let layout = &file.operations[0].layout_assets;
    assert_eq!(layout.root, "", "a null root is an absent root");
    assert!(layout.assets[0].transform.is_none(), "a null transform is an absent transform");
}

/// A content_module_jar target packs the module output first and then the library containers. An asset of that shape
/// is the reused jar. Any other source order, another writer or another mode keeps it in the remainder.
#[test]
fn derive_reuses_a_module_jar_that_merges_libraries() {
    let library = r#"{"input": "@lib//:demo-lib", "kind": "library", "filter": "library-v1"}"#;
    let module = r#"{"input": "demo.rt", "kind": "module", "filter": "module-v1"}"#;
    let assets = format!(
        r#"{{"destination": "lib/modules/demo.rt.jar", "recipe": {{"sources": [{module}, {library}], "writer": {{"mergeEntities": true}}}}}},
    {{"destination": "lib/rt-first.jar", "recipe": {{"sources": [{library}, {module}], "writer": {{"mergeEntities": true}}}}}},
    {{"destination": "lib/rt-kept.jar", "recipe": {{"sources": [{module}, {library}], "writer": {{"manifest": "keep", "mergeEntities": true}}}}}}"#
    );
    let derivation = must_derive(&plan(1, &assets, &[]), &reused_library_catalogue(), 1, &["demo.rt"]);
    assert_eq!(
        derivation.assets,
        [
            row("lib/modules/demo.rt.jar", "independent", "demo.rt"),
            row("lib/rt-first.jar", "remainder", ""),
            row("lib/rt-kept.jar", "remainder", ""),
        ]
    );
}

/// The module `demo.rt` and the one-jar library container `@lib//:demo-lib`.
fn reused_library_catalogue() -> Catalogue {
    let mut inputs = catalogue(vec![file_artifact("demo.rt"), file_artifact("@lib//:demo-lib/a.jar")]);
    inputs.libraries = vec![Library {
        id: "@lib//:demo-lib".to_owned(),
        files: vec![Reference::artifact("@lib//:demo-lib/a.jar")],
    }];
    inputs
}

/// The chain names the reused modules. An asset whose recipe and mode are the plain module jar of one is independent,
/// under the module name. A jar at another destination with the same recipe is independent too. A jar at another mode,
/// or with another writer, is remainder.
#[test]
fn derive_matches_ownership_by_recipe_and_mode() {
    let assets = format!(
        r#"{{"module": "demo.content"}}, {{"destination": "lib/rt.jar", "recipe": {RT_RECIPE}}},
    {{"destination": "lib/rt-exec.jar", "recipe": {RT_RECIPE}, "mode": 493}},
    {{"destination": "lib/rt-kept.jar", "recipe": {{"sources": [{{"input": "demo.rt", "kind": "module", "filter": "module-v1"}}], "writer": {{"manifest": "keep", "mergeEntities": true}}}}}}"#
    );
    let derivation = must_derive(
        &plan(1, &assets, &[]),
        &catalogue(vec![file_artifact("demo.rt")]),
        1,
        &["demo.content", "demo.rt"],
    );
    let want = [
        row("lib/modules/demo.content.jar", "independent", "demo.content"),
        row("lib/rt.jar", "independent", "demo.rt"),
        row("lib/rt-exec.jar", "remainder", ""),
        row("lib/rt-kept.jar", "remainder", ""),
    ];
    assert_eq!(derivation.assets, want);
    assert_eq!(derivation.recipe.assets, want);
    let operations = &derivation.recipe.operations;
    assert_eq!(
        operations.iter().map(contract::Operation::destination).collect::<Vec<_>>(),
        ["lib/rt-exec.jar", "lib/rt-kept.jar"]
    );
    assert!(
        matches!(operations[0], contract::Operation::Jar { mode: EXECUTABLE_MODE, .. }),
        "{operations:?}"
    );

    let module_at_another_mode = format!(r#"{{"destination": "lib/rt.jar", "recipe": {RT_RECIPE}, "mode": 493}}"#);
    for (name, assets, modules, message) in [
        (
            "a module at another mode",
            module_at_another_mode.as_str(),
            &["demo.rt"][..],
            r#"independent module "demo.rt" matches no module jar asset"#,
        ),
        (
            "a module without an asset",
            r#"{"module": "demo.content"}"#,
            &["demo.rt"][..],
            r#"independent module "demo.rt" matches no module jar asset"#,
        ),
        (
            "a module named twice",
            r#"{"module": "demo.content"}"#,
            &["demo.content", "demo.content"][..],
            r#"independent module "demo.content" is named twice"#,
        ),
        (
            "an empty module",
            r#"{"module": "demo.content"}"#,
            &[""][..],
            "an independent module requires a name",
        ),
    ] {
        let result = derive_plan(&plan(1, assets, &[]), &catalogue(vec![file_artifact("demo.rt")]), 1, modules);
        match result {
            Ok(_) => panic!("{name}: expected {message:?}"),
            Err(error) => assert!(error.message().contains(message), "{name}: expected {message:?}, got {error}"),
        }
    }
}

#[test]
fn derive_requires_the_execution_version_of_the_assets() {
    let excluded_tree = r#"{"destination": "lib/tree", "inputs": ["tree"], "kind": "tree", "classPath": false}"#;
    for (name, text, inputs, version, independent) in [
        (
            "files",
            plan(1, r#"{"destination": "bin/tool", "inputs": ["tool"]}"#, &[]),
            catalogue(vec![file_artifact("tool")]),
            1,
            &[][..],
        ),
        (
            "a tree",
            plan(2, excluded_tree, &[]),
            catalogue(vec![directory_artifact("tree")]),
            2,
            &[][..],
        ),
        (
            "a native tree",
            plan(2, NATIVES, &[]),
            catalogue(Vec::new()),
            2,
            &["demo.natives"][..],
        ),
    ] {
        assert_eq!(must_derive(&text, &inputs, version, independent).recipe.version, version, "{name}");
        for wrong in [1, 2, 3].into_iter().filter(|wrong| *wrong != version) {
            expect_error(derive_plan(&text, &inputs, wrong, independent), "stale execution version");
        }
    }
    expect_error(
        derive_plan(
            &plan(2, r#"{"destination": "bin/tool", "inputs": ["tool"]}"#, &[]),
            &catalogue(vec![file_artifact("tool")]),
            2,
            &[],
        ),
        "stale execution version",
    );
    // Version 3 is retired. A plan file of version 3 and a chain that declares it are stale.
    expect_error(
        derive_plan(&plan(3, NATIVES, &[]), &catalogue(Vec::new()), 3, &["demo.natives"]),
        "stale execution version: file=3 declared=3 required=2",
    );

    let derivation = must_derive(&plan(2, NATIVES, &[]), &catalogue(Vec::new()), 2, &["demo.natives"]);
    assert_eq!(
        derivation.assets,
        [
            row("lib/modules/demo.natives.jar", "independent", "demo.natives"),
            tree_row("lib/native", "independent", "demo.natives"),
        ]
    );
    assert!(derivation.recipe.operations.is_empty(), "the remainder writes no native file");

    // The tree names a reused jar without a native library, so it is not a native tree.
    let not_native = format!(
        r#"{{"destination": "lib/rt.jar", "recipe": {RT_RECIPE}}},
  {{"destination": "lib/native", "inputs": ["native-tree:demo.rt"], "kind": "tree", "classPath": false}}"#
    );
    expect_error(
        derive_plan(&plan(2, &not_native, &[]), &catalogue(Vec::new()), 2, &["demo.rt"]),
        r#"lib/native: the native tree of "demo.rt" requires its reused natives jar"#,
    );
}

fn jar_manifests(derivation: &Derivation, destination: &str) -> Vec<Manifest> {
    let Some(contract::Operation::Jar { sources, .. }) = derivation
        .recipe
        .operations
        .iter()
        .find(|operation| operation.destination() == destination)
    else {
        panic!("no jar operation at {destination}");
    };
    sources
        .iter()
        .map(|source| match source {
            Source::Archive { manifest, .. } | Source::Patch { manifest, .. } => *manifest,
            Source::Layout(_) => Manifest::Keep,
        })
        .collect()
}

#[test]
fn derive_counts_meaningful_sources_for_the_manifest() {
    let mut inputs = catalogue(vec![
        file_artifact("demo.main"),
        file_artifact("intellij.libraries.foo"),
        file_artifact("@lib//:two/a.jar"),
        file_artifact("@lib//:two/b.jar"),
        file_artifact("descriptor"),
    ]);
    inputs.libraries = vec![Library {
        id: "@lib//:two".to_owned(),
        files: vec![Reference::artifact("@lib//:two/a.jar"), Reference::artifact("@lib//:two/b.jar")],
    }];
    let main = r#"{"input": "demo.main", "kind": "module", "filter": "module-v1"}"#;
    let two = r#"{"input": "@lib//:two", "kind": "library", "filter": "library-v1"}"#;
    let foo = r#"{"input": "intellij.libraries.foo", "kind": "module", "filter": "module-v1"}"#;
    let patch = r#"{"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]}"#;
    let assets = format!(
        r#"{{"destination": "lib/one.jar", "recipe": {{"sources": [{main}]}}}},
    {{"destination": "lib/library.jar", "recipe": {{"sources": [{two}]}}}},
    {{"destination": "lib/lib-module.jar", "recipe": {{"sources": [{foo}, {main}]}}}},
    {{"destination": "lib/patched.jar", "recipe": {{"sources": [{main}, {patch}]}}}},
    {{"destination": "lib/kept.jar", "recipe": {{"sources": [{main}, {two}], "writer": {{"manifest": "keep"}}}}}}"#
    );
    let derivation = must_derive(&plan(1, &assets, &[]), &inputs, 1, &[]);
    for (destination, want) in [
        ("lib/one.jar", &[Manifest::Keep][..]),
        ("lib/library.jar", &[Manifest::Drop, Manifest::Drop][..]),
        ("lib/lib-module.jar", &[Manifest::Keep, Manifest::Keep][..]),
        ("lib/patched.jar", &[Manifest::Drop, Manifest::Drop][..]),
        ("lib/kept.jar", &[Manifest::Keep, Manifest::Keep, Manifest::Keep][..]),
    ] {
        assert_eq!(jar_manifests(&derivation, destination), want, "{destination}");
    }
    assert_eq!(
        derivation.recipe.operations[1],
        jar(
            "lib/library.jar",
            false,
            vec![
                archive("@lib//:two/a.jar", Filter::Library, Manifest::Drop),
                archive("@lib//:two/b.jar", Filter::Library, Manifest::Drop),
            ]
        ),
        "a library source merges its member files"
    );
    assert!(
        derivation.catalogue.libraries.is_empty(),
        "the remainder catalogue drops the libraries"
    );
    assert_eq!(
        derivation.catalogue.artifacts, inputs.artifacts,
        "the remainder catalogue keeps the artifacts"
    );
}

/// A catalogue of one library per name with the given member files. The member IDs follow the Starlark catalogue
/// rule: `<library id>/<jar basename>`.
fn library_catalogue(libraries: &[(&str, &[&str])]) -> Catalogue {
    let mut inputs = catalogue(Vec::new());
    let mut sorted = libraries.to_vec();
    sorted.sort_by_key(|(id, _)| *id);
    for (id, members) in sorted {
        let mut library = Library {
            id: id.to_owned(),
            files: Vec::new(),
        };
        for member in members {
            let member_id = format!("{id}/{member}");
            inputs.artifacts.push(file_artifact(&member_id));
            library.files.push(Reference::artifact(member_id));
        }
        inputs.libraries.push(library);
    }
    inputs
}

/// The plan names the library, and the derivation reads the member files from the catalogue. A layout input stands
/// for every member, and the asset sources follow the expanded positions.
#[test]
fn derive_resolves_a_library_input_to_its_members() {
    let entries = r#""preparations": [{"id": "entries", "inputs": ["@lib//:one", "raw"], "outputs": ["entries:output"], "modelSignature": "e"}],
    "operations": [{"id": "entries", "kind": "layout-assets", "inputs": [{"artifact": "@lib//:one"}, {"artifact": "raw"}], "output": "entries:output", "manifest": "keep",
      "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [1], "transform": {"kind": "archive-tree"}},
        {"destination": "", "sources": [0], "transform": {"kind": "archive-tree"}}]}}]"#;
    let jar_asset = r#"{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "entries:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "keep"}}}"#;
    for (name, members, inputs, sources) in [
        (
            "one member",
            &["one-1.0.jar"][..],
            &["@lib//:one/one-1.0.jar", "raw"][..],
            [vec![1], vec![0]],
        ),
        (
            "two members",
            &["one-1.0.jar", "one-api-1.0.jar"][..],
            &["@lib//:one/one-1.0.jar", "@lib//:one/one-api-1.0.jar", "raw"][..],
            [vec![2], vec![0, 1]],
        ),
    ] {
        let mut catalogue = library_catalogue(&[("@lib//:one", members)]);
        catalogue.artifacts.push(file_artifact("raw"));
        let derivation = must_derive(&plan(1, jar_asset, &[entries]), &catalogue, 1, &[]);
        let want = vec![jar(
            "lib/x.jar",
            false,
            vec![layout_source(
                inputs,
                sources
                    .map(|sources| transform_asset(sources, transform(LayoutTransformKind::ArchiveTree)))
                    .to_vec(),
            )],
        )];
        assert_eq!(derivation.recipe.operations, want, "{name}");
    }
}

#[test]
fn derive_compiles_every_operation_kind() {
    let inputs = catalogue(vec![
        file_artifact("raw"),
        file_artifact("descriptor"),
        file_artifact("native"),
        directory_artifact("dsls"),
        file_artifact("archive"),
        directory_artifact("properties"),
    ]);
    let tree = r#"{"id": "tree", "kind": "layout-assets", "inputs": [{"artifact": "archive"}], "output": "tree:output", "manifest": "keep",
    "layoutAssets": {"format": "tree", "root": "payload", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "archive-tree", "stripComponents": 1}}]}}"#;
    let entries = r#"{"id": "entries", "kind": "layout-assets", "inputs": [{"artifact": "properties"}], "output": "entries:output", "manifest": "keep",
    "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [0]}]}}"#;
    let operations = format!(r#""operations": [{ENTRIES_OPERATION}, {tree}, {entries}]"#);
    let derivation = must_derive(
        &plan(
            2,
            r#"{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"},
        {"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]}], "writer": {"manifest": "drop", "directoryEntries": true}}},
      {"destination": "lib/l10n.jar", "recipe": {"sources": [{"input": "entries:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "keep"}}},
      {"destination": "payload", "inputs": ["tree:output"], "kind": "tree", "classPath": false},
      {"destination": "lib/standardDsls", "inputs": ["dsls"], "kind": "tree", "classPath": false},
      {"destination": "bin/tool", "inputs": ["native"], "mode": 493}"#,
            &[
                r#""preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"},
          {"id": "tree", "inputs": ["archive"], "outputs": ["tree:output"], "modelSignature": "y"},
          {"id": "entries", "inputs": ["properties"], "outputs": ["entries:output"], "modelSignature": "z"}]"#,
                &operations,
            ],
        ),
        &inputs,
        2,
        &[],
    );
    let plain_copy = LayoutAsset {
        destination: String::new(),
        sources: vec![0],
        transform: None,
        mode: 0,
    };
    let want = vec![
        jar(
            "lib/main.jar",
            true,
            vec![
                layout_source(&["raw"], vec![raw_entry()]),
                Source::Patch {
                    entry: "META-INF/plugin.xml".to_owned(),
                    input: Reference::artifact("descriptor"),
                    manifest: Manifest::Drop,
                },
            ],
        ),
        jar("lib/l10n.jar", false, vec![layout_source(&["properties"], vec![plain_copy])]),
        contract::Operation::LayoutTree {
            destination: "payload".to_owned(),
            layout: LayoutAssets {
                inputs: vec![Reference::artifact("archive")],
                assets: vec![transform_asset(
                    vec![0],
                    LayoutTransform {
                        strip_components: 1,
                        ..transform(LayoutTransformKind::ArchiveTree)
                    },
                )],
            },
        },
        contract::Operation::CopyTree {
            destination: "lib/standardDsls".to_owned(),
            input: Reference::artifact("dsls"),
        },
        contract::Operation::Copy {
            destination: "bin/tool".to_owned(),
            mode: EXECUTABLE_MODE,
            input: Reference::artifact("native"),
        },
    ];
    assert_eq!(derivation.recipe.operations, want);

    let rows = vec![
        row("lib/main.jar", "remainder", ""),
        row("lib/l10n.jar", "remainder", ""),
        tree_row("payload", "remainder", ""),
        tree_row("lib/standardDsls", "remainder", ""),
        row("bin/tool", "remainder", ""),
    ];
    assert_eq!(derivation.assets, rows);
    // The asset rows are the `assets.json` of the packer: Go `json.Marshal` of the rows.
    assert_eq!(
        serde_json::to_string(&derivation.assets).unwrap(),
        concat!(
            r#"[{"destination":"lib/main.jar","producer":"remainder"},{"destination":"lib/l10n.jar","producer":"remainder"},"#,
            r#"{"destination":"payload","producer":"remainder","kind":"tree","classPath":false},"#,
            r#"{"destination":"lib/standardDsls","producer":"remainder","kind":"tree","classPath":false},"#,
            r#"{"destination":"bin/tool","producer":"remainder"}]"#,
        )
    );
}

#[test]
fn derive_writes_the_plan_scope_class_path() {
    let inputs = catalogue(vec![file_artifact("demo.main"), file_artifact("run")]);
    let derivation = must_derive(
        &plan(
            1,
            r#"{"destination": "lib/util.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
      {"destination": "lib/demo.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
      {"destination": "lib/modules/demo.main.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
      {"destination": "lib/hidden.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}, "classPath": false},
      {"destination": "lib/data.txt", "inputs": ["run"]},
      {"module": "demo.content"}"#,
            &[],
        ),
        &inputs,
        1,
        &["demo.content"],
    );
    let want = classpath::record("demo", b"<idea-plugin/>", &["lib/util.jar", "lib/demo.jar"]).unwrap();
    assert_eq!(derivation.class_path, want);
    assert!(
        want.windows(26).any(|window| window == b"lib/demo.jar\x00\x0clib/util.jar"),
        "the plugin jar goes first"
    );
}

#[test]
fn derive_refuses_what_the_packer_does_not_execute() {
    let filter_inputs = || catalogue(vec![file_artifact("raw")]);
    let layout_jar = r#"{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "layout:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "drop"}}}"#;
    let native_tree = r#"{"destination": "lib/native", "inputs": ["native-tree:demo.natives"], "kind": "tree", "classPath": false"#;
    let scenarios = [
        (
            "a prepared source without a producer",
            plan(1, FILTERED_JAR, &[]),
            filter_inputs(),
            "has no producer in the plan file",
        ),
        (
            "a copy of a Go-executed output",
            plan(1, r#"{"destination": "lib/x.jar", "inputs": ["filtered"]}"#, &[ENTRIES_SECTION]),
            filter_inputs(),
            "requires a prepared jar source",
        ),
        (
            "an operation no asset needs",
            plan(1, r#"{"destination": "bin/tool", "inputs": ["raw"]}"#, &[ENTRIES_SECTION]),
            filter_inputs(),
            "unexpected preparation operation",
        ),
        (
            "a preparation without operation",
            plan(
                1,
                FILTERED_JAR,
                &[r#""preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"}]"#],
            ),
            filter_inputs(),
            "missing preparation operation",
        ),
        (
            "stale preparation inputs",
            plan(1, FILTERED_JAR, &[ENTRIES_SECTION]),
            catalogue(vec![file_artifact("raw"), file_artifact("extra")]),
            "stale preparation inputs",
        ),
        (
            "a missing catalogue input",
            plan(1, FILTERED_JAR, &[ENTRIES_SECTION]),
            catalogue(Vec::new()),
            "stale preparation inputs",
        ),
        (
            "a definition with other inputs",
            plan(
                1,
                FILTERED_JAR,
                &[&format!(
                    r#""preparations": [{{"id": "filter", "inputs": ["raw", "more"], "outputs": ["filtered"], "modelSignature": "x"}}],
          "operations": [{ENTRIES_OPERATION}]"#
                )],
            ),
            filter_inputs(),
            r#"must declare exactly the inputs ["raw"]"#,
        ),
        (
            "a tree with another root",
            plan(
                2,
                r#"{"destination": "other", "inputs": ["layout:output"], "kind": "tree", "classPath": false}"#,
                &[
                    r#""preparations": [{"id": "layout", "inputs": ["source"], "outputs": ["layout:output"], "modelSignature": "x"}],
          "operations": [{"id": "layout", "kind": "layout-assets", "inputs": [{"artifact": "source"}], "output": "layout:output", "manifest": "keep",
            "layoutAssets": {"format": "tree", "root": "payload", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "archive-tree"}}]}}]"#,
                ],
            ),
            catalogue(vec![file_artifact("source")]),
            "requires one tree asset",
        ),
        (
            "a tree of a file input",
            plan(
                2,
                r#"{"destination": "tree", "inputs": ["source"], "kind": "tree", "classPath": false}"#,
                &[],
            ),
            catalogue(vec![file_artifact("source")]),
            "requires a directory artifact",
        ),
        (
            "a prepared source under the default manifest",
            plan(
                1,
                r#"{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"}]}}"#,
                &[ENTRIES_SECTION],
            ),
            filter_inputs(),
            "explicit manifest policy",
        ),
        (
            "a tree at another mode",
            plan(
                2,
                r#"{"destination": "tree", "inputs": ["source"], "kind": "tree", "classPath": false, "mode": 493}"#,
                &[],
            ),
            catalogue(vec![directory_artifact("source")]),
            "no mode override",
        ),
        // The refusals below are Rust additions to the Go table.
        (
            "a layout input the operation lacks",
            plan(
                1,
                layout_jar,
                &[
                    r#""preparations": [{"id": "layout", "inputs": ["source"], "outputs": ["layout:output"], "modelSignature": "x"}],
          "operations": [{"id": "layout", "kind": "layout-assets", "inputs": [{"artifact": "source"}], "output": "layout:output", "manifest": "keep",
            "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [1], "transform": {"kind": "archive-tree"}}]}}]"#,
                ],
            ),
            catalogue(vec![file_artifact("source")]),
            r#"layout asset "" names the input 1, which the operation lacks"#,
        ),
        (
            "a native library outside a reused jar",
            plan(
                1,
                r#"{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "raw", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true, "nativeLib": "libx.so"}}}"#,
                &[],
            ),
            filter_inputs(),
            "a jar with a native library must be a reused content_module_jar",
        ),
        (
            "a preparation chain",
            plan(
                1,
                FILTERED_JAR,
                &[r#""preparations": [{"id": "filter", "inputs": ["filtered"], "outputs": ["filtered"], "modelSignature": "x"}]"#],
            ),
            filter_inputs(),
            r#"preparation "filter" reads the output "filtered" of a preparation; the packer executes no preparation chain"#,
        ),
        (
            "a native tree without its jar",
            plan(2, &format!("{native_tree}}}"), &[]),
            catalogue(Vec::new()),
            "requires its reused natives jar",
        ),
    ];
    for (name, text, inputs, message) in scenarios {
        let version = must_read_plan(&text).version;
        match derive_plan(&text, &inputs, version, &[]) {
            Ok(_) => panic!("{name}: expected {message:?}"),
            Err(error) => assert!(error.message().contains(message), "{name}: expected {message:?}, got {error}"),
        }
    }
}

#[test]
fn derive_refuses_an_invalid_plugin_directory() {
    let text = plan(1, r#"{"destination": "bin/tool", "inputs": ["tool"]}"#, &[]);
    for directory in [
        "",
        ".",
        "..",
        "/",
        "demo",
        "plugins/",
        "plugins/.",
        "plugins/..",
        "plugins/a/b",
        "plugins\\demo",
        "plugins/a\\b",
    ] {
        let result = derive(
            &must_read_plan(&text),
            &catalogue(vec![file_artifact("tool")]),
            directory,
            b"",
            1,
            &[],
            &[],
        );
        assert!(result.unwrap_err().message().contains("is not plugins/<name>"), "{directory:?}");
    }
}

/// The chain names the modules its product mode refuses. An asset whose every module is refused is omitted: no row, no
/// classpath jar, no operation, and the inputs that only it reads leave the catalogue of the derivation. An asset that
/// merges a refused module with a kept one stays whole, and a jar without a module is never omitted. A refused module
/// whose jar the chain reuses is omitted as well.
#[test]
fn derive_omits_an_asset_whose_every_module_is_refused() {
    let backend = r#"{"input": "demo.backend", "kind": "module", "filter": "module-v1"}"#;
    let only = r#"{"input": "demo.only", "kind": "module", "filter": "module-v1"}"#;
    let main = r#"{"input": "demo.main", "kind": "module", "filter": "module-v1"}"#;
    let library = r#"{"input": "@lib//:demo-lib", "kind": "library", "filter": "library-v1"}"#;
    let assets = format!(
        r#"{{"module": "demo.content"}}, {{"module": "demo.shared"}},
    {{"destination": "lib/backend.jar", "recipe": {{"sources": [{backend}, {only}], "writer": {{"mergeEntities": true}}}}}},
    {{"destination": "lib/demo.jar", "recipe": {{"sources": [{main}, {backend}], "writer": {{"mergeEntities": true}}}}}},
    {{"destination": "lib/demo-lib.jar", "recipe": {{"sources": [{library}], "writer": {{"mergeEntities": true}}}}}},
    {FILTERED_JAR}"#
    );
    let mut inputs = catalogue(vec![
        file_artifact("demo.backend"),
        file_artifact("demo.only"),
        file_artifact("demo.main"),
        file_artifact("@lib//:demo-lib/a.jar"),
        file_artifact("raw"),
    ]);
    inputs.libraries = vec![Library {
        id: "@lib//:demo-lib".to_owned(),
        files: vec![Reference::artifact("@lib//:demo-lib/a.jar")],
    }];
    let text = plan(1, &assets, &[ENTRIES_SECTION]);
    let refused = ["demo.backend", "demo.only", "demo.content"];
    let derivation =
        derive_refusing(&text, &inputs, 1, &["demo.content", "demo.shared"], &refused).unwrap_or_else(|error| panic!("{error}"));
    assert_eq!(
        derivation.assets,
        [
            row("lib/modules/demo.shared.jar", "independent", "demo.shared"),
            row("lib/demo.jar", "remainder", ""),
            row("lib/demo-lib.jar", "remainder", ""),
            row("lib/main.jar", "remainder", ""),
        ]
    );
    assert_eq!(derivation.recipe.assets, derivation.assets);
    assert_eq!(
        derivation
            .recipe
            .operations
            .iter()
            .map(contract::Operation::destination)
            .collect::<Vec<_>>(),
        ["lib/demo.jar", "lib/demo-lib.jar", "lib/main.jar"]
    );
    assert_eq!(
        derivation
            .catalogue
            .artifacts
            .iter()
            .map(|artifact| artifact.id.as_str())
            .collect::<Vec<_>>(),
        ["demo.backend", "demo.main", "@lib//:demo-lib/a.jar", "raw"],
        "the module input that only the omitted jar reads left the catalogue, and the merged input stayed"
    );
    let expected = classpath::record("demo", b"<idea-plugin/>", &["lib/demo.jar", "lib/demo-lib.jar", "lib/main.jar"]).unwrap();
    assert_eq!(derivation.class_path, expected);

    // Without a refusal the same plan derives every asset, so the refused list alone changes the derivation.
    let complete = derive_plan(&text, &inputs, 1, &["demo.content", "demo.shared"]).unwrap_or_else(|error| panic!("{error}"));
    assert_eq!(complete.assets.len(), 6);
    assert_eq!(complete.catalogue.artifacts.len(), 5);

    for (name, refused, message) in [
        (
            "a module without an asset",
            &["demo.other"][..],
            r#"refused module "demo.other" matches no asset of the plan"#,
        ),
        (
            "a module named twice",
            &["demo.backend", "demo.backend"][..],
            r#"refused module "demo.backend" is named twice"#,
        ),
        ("an empty module", &[""][..], "a refused module requires a name"),
    ] {
        let result = derive_refusing(&text, &inputs, 1, &["demo.content", "demo.shared"], refused);
        match result {
            Ok(_) => panic!("{name}: expected {message:?}"),
            Err(error) => assert!(error.message().contains(message), "{name}: expected {message:?}, got {error}"),
        }
    }
}
