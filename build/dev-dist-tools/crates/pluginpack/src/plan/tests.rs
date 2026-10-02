//! The port of `plan_test.go`.

use std::collections::BTreeMap;
use std::path::Path;

use planfile::contract::{
    ArtifactKind, Asset, AssetKind, Catalogue, Library, Manifest, Operation, Producer, Recipe, Reference, Source, TREE_VERSION, VERSION,
};

use super::{plan, validate_plugin_links};
use crate::tests::*;

fn tree_plan(root: &Path) -> (Recipe, Catalogue) {
    let recipe = Recipe {
        version: TREE_VERSION,
        plugin: "tree".to_owned(),
        assets: vec![tree_asset("kotlinc"), independent("lib/independent.jar", "independent")],
        operations: vec![Operation::CopyTree {
            destination: "kotlinc".to_owned(),
            input: Reference::artifact("tree"),
        }],
    };
    (recipe, catalogue(vec![directory_artifact("tree", root)]))
}

/// The input of the first archive source of the first operation.
fn first_input(recipe: &mut Recipe) -> &mut Reference {
    match &mut jar_sources(recipe, 0)[0] {
        Source::Archive { input, .. } | Source::Patch { input, .. } => input,
        source @ Source::Layout(_) => panic!("no input: {source:?}"),
    }
}

fn set_destination(operation: &mut Operation, value: &str) {
    match operation {
        Operation::Jar { destination, .. }
        | Operation::Copy { destination, .. }
        | Operation::CopyTree { destination, .. }
        | Operation::LayoutTree { destination, .. } => *destination = value.to_owned(),
    }
}

#[test]
fn tree_planning_is_versioned_and_does_not_read_directories() {
    let root = temp();
    let (mut recipe, catalogue) = tree_plan(&root.path().join("missing"));
    let execution = plan(&recipe, &catalogue).unwrap();
    assert_eq!(execution.inputs, catalogue.artifacts, "tree planning changed the ownership");
    // Version 3 is retired, so a tree has only version 2.
    for version in [VERSION, 3] {
        recipe.version = version;
        assert!(plan(&recipe, &catalogue).is_err(), "accepted tree version {version}");
    }
    let (mut legacy, legacy_catalogue) = sample_plan(root.path());
    legacy.version = TREE_VERSION;
    plan(&legacy, &legacy_catalogue).expect("version 2 rejected non-tree operations");
}

#[test]
fn tree_plan_allows_descendant_assets_without_reading_tree() {
    let root = temp();
    let (mut recipe, catalogue) = tree_plan(&root.path().join("missing"));
    recipe.assets[1].destination = "kotlinc/lib/compiler.jar".to_owned();
    plan(&recipe, &catalogue).unwrap();
}

/// Also covers the former "unicode alias" case: the plan refuses a destination that is not ASCII, because the inventory
/// cannot hold it. The former cases "mode", "sources", "target", "options" and "no input" have no port: the typed
/// copy-tree operation has none of these fields.
#[test]
fn tree_plan_rejects_unsafe_ownership_and_operation_options() {
    type Mutation = fn(&mut Recipe, &mut Catalogue);
    let scenarios: [(&str, Mutation); 9] = [
        ("root collision", |recipe, _| recipe.assets[1].destination = "kotlinc".to_owned()),
        ("case alias", |recipe, _| {
            recipe.assets[1].destination = "KOTLINC/lib/compiler.jar".to_owned();
        }),
        ("unicode alias", |recipe, _| {
            recipe.assets[0].destination = "é".to_owned();
            set_destination(&mut recipe.operations[0], "é");
            recipe.assets[1].destination = "e\u{301}/nested".to_owned();
        }),
        ("file ancestor", |recipe, _| {
            recipe.assets[0].destination = "lib/kotlinc".to_owned();
            set_destination(&mut recipe.operations[0], "lib/kotlinc");
            recipe.assets[1].destination = "lib".to_owned();
        }),
        ("independent tree", |recipe, _| {
            recipe.assets[0].producer = Producer::Independent;
            recipe.assets[0].artifact = "independent-tree".to_owned();
        }),
        ("classpath", |recipe, _| recipe.assets[0].class_path = None),
        ("path", |recipe, _| {
            if let Operation::CopyTree { input, .. } = &mut recipe.operations[0] {
                input.path = "child".to_owned();
            }
        }),
        ("file input", |_, catalogue| catalogue.artifacts[0].kind = ArtifactKind::File),
        ("ordinary copy", |recipe, _| {
            recipe.operations[0] = Operation::Copy {
                destination: "kotlinc".to_owned(),
                mode: 0o644,
                input: Reference::artifact("tree"),
            };
        }),
    ];
    for (name, mutate) in scenarios {
        let root = temp();
        let (mut recipe, mut catalogue) = tree_plan(&root.path().join("missing"));
        mutate(&mut recipe, &mut catalogue);
        assert!(plan(&recipe, &catalogue).is_err(), "{name}: accepted an unsafe tree contract");
    }
    let root = temp();
    let (recipe, mut catalogue) = tree_plan(&root.path().join("missing"));
    catalogue.artifacts.push(file_artifact("independent", "absent.jar"));
    assert!(plan(&recipe, &catalogue).is_err(), "accepted an independent input");
}

/// The Go test reserved the transport root `.distribution-root` for version 3. The remainder writes only plugin files,
/// so no transport root exists. The native tree of a reused natives jar lies below the plugin directory. Such a plan
/// has version 2, and the tree requires an independent jar of the same module.
#[test]
fn plan_accepts_a_reused_native_tree_next_to_its_jar() {
    let native_tree = Asset {
        kind: AssetKind::Tree,
        class_path: Some(false),
        ..independent("lib/native", "demo.natives")
    };
    let recipe = |version: u32, assets: Vec<Asset>| Recipe {
        version,
        plugin: "natives".to_owned(),
        assets,
        operations: Vec::new(),
    };
    let jar = independent("lib/modules/demo.natives.jar", "demo.natives");
    plan(
        &recipe(TREE_VERSION, vec![native_tree.clone(), jar.clone()]),
        &catalogue(Vec::new()),
    )
    .unwrap();
    expect_plan_error(
        &recipe(VERSION, vec![jar.clone(), native_tree.clone()]),
        &catalogue(Vec::new()),
        r#"tree "lib/native" requires version 2,"#,
    );
    expect_plan_error(
        &recipe(3, vec![jar.clone(), native_tree.clone()]),
        &catalogue(Vec::new()),
        "the recipe must use version 1 or 2",
    );
    expect_plan_error(
        &recipe(TREE_VERSION, vec![native_tree.clone()]),
        &catalogue(Vec::new()),
        "remainder or native tree ownership",
    );
    expect_plan_error(
        &recipe(
            TREE_VERSION,
            vec![independent("lib/modules/other.jar", "other"), native_tree.clone()],
        ),
        &catalogue(Vec::new()),
        "remainder or native tree ownership",
    );
    let class_path = Asset {
        class_path: None,
        ..native_tree
    };
    expect_plan_error(
        &recipe(TREE_VERSION, vec![jar, class_path]),
        &catalogue(Vec::new()),
        "and classPath false",
    );
}

/// The Go test covered the directory operation. The typed recipe has none, and the asset kind has no directory, so the
/// refusal is in the reader of the asset rows.
#[test]
fn directory_assets_are_refused() {
    let error = planfile::json::from_slice::<Asset>(br#"{"destination":"dir","producer":"remainder","kind":"directory"}"#).unwrap_err();
    assert!(format!("{error:#}").contains(r#"unknown asset kind "directory""#), "{error:#}");
}

#[test]
fn plan_does_not_read_payloads() {
    let root = temp();
    let (mut recipe, mut catalogue) = sample_plan(&root.path().join("does-not-exist"));
    let execution = plan(&recipe, &catalogue).unwrap();
    assert_eq!(execution.inputs, catalogue.artifacts);
    catalogue.artifacts[0].id = "mutated".to_owned();
    first_input(&mut recipe).artifact = "mutated".to_owned();
    recipe.assets[1].destination = "mutated".to_owned();
    assert_eq!(execution.inputs[0].id, "module", "a caller changed the validated plan");
    assert_eq!(execution.recipe.assets[1].destination, "lib/plugin.jar");
}

/// Eight former cases have no port, because the typed recipe cannot state them. Four are "unknown operation", "unknown
/// source", "missing reference" and "unknown filter". The other four are "unknown manifest", "excludes on prepared
/// entries", "unknown directory mode" and "mixed operation".
#[test]
fn plan_rejects_invalid_contracts() {
    type Change = fn(&mut Recipe, &mut Catalogue);
    // The contract types cannot state an unknown producer or an unknown root kind, so the reader of `assets.json` and
    // of the catalogue refuses them by name, see the `planfile` contract tests.
    let tests: [(&str, Change, &str); 17] = [
        ("recipe version", |recipe, _| recipe.version = 0, "version"),
        ("catalogue version", |_, catalogue| catalogue.version = 2, "version"),
        ("missing operation", |recipe, _| recipe.operations.clear(), "missing remainder"),
        (
            "extra operation",
            |recipe, _| {
                let operation = recipe.operations[0].clone();
                recipe.operations.push(operation);
            },
            "conflicting",
        ),
        (
            "independent destination",
            |recipe, _| {
                let destination = recipe.assets[0].destination.clone();
                set_destination(&mut recipe.operations[0], &destination);
            },
            "unowned",
        ),
        (
            "unknown reference",
            |recipe, _| first_input(recipe).artifact = "other".to_owned(),
            "unresolved input",
        ),
        (
            "missing catalogue input",
            |_, catalogue| catalogue.artifacts.clear(),
            "unresolved input",
        ),
        (
            "undeclared catalogue input",
            |_, catalogue| catalogue.artifacts[0].id = "other".to_owned(),
            "unresolved input",
        ),
        (
            "unused input",
            |_, catalogue| catalogue.artifacts.push(file_artifact("unused", "/unused.jar")),
            "unused inputs",
        ),
        (
            "invalid catalogue input",
            |_, catalogue| catalogue.artifacts[0].id = " module ".to_owned(),
            "invalid",
        ),
        (
            "unclean root",
            |_, catalogue| catalogue.artifacts[0].root = format!("./{}", catalogue.artifacts[0].root),
            "invalid root",
        ),
        (
            "file child",
            |recipe, _| first_input(recipe).path = "child".to_owned(),
            "relative path",
        ),
        (
            "catalogue library",
            |_, catalogue| {
                catalogue.libraries = vec![Library {
                    id: "library".to_owned(),
                    files: vec![Reference::artifact("module")],
                }];
            },
            "names a library",
        ),
        (
            "setuid",
            |recipe, _| {
                if let Operation::Jar { mode, .. } = &mut recipe.operations[0] {
                    *mode = 0o4755;
                }
            },
            "file mode",
        ),
        (
            "mode zero",
            |recipe, _| {
                if let Operation::Jar { mode, .. } = &mut recipe.operations[0] {
                    *mode = 0;
                }
            },
            "file mode",
        ),
        (
            "unsafe patch entry",
            |recipe, _| {
                jar_sources(recipe, 0)[0] = Source::Patch {
                    entry: "../outside".to_owned(),
                    input: Reference::artifact("module"),
                    manifest: Manifest::Drop,
                };
            },
            "unsafe entry name",
        ),
        (
            "remainder artifact",
            |recipe, _| recipe.assets[1].artifact = "module".to_owned(),
            "must not name an independent artifact",
        ),
    ];
    for (name, change, want) in tests {
        let root = temp();
        let (mut recipe, mut catalogue) = sample_plan(root.path());
        change(&mut recipe, &mut catalogue);
        match plan(&recipe, &catalogue) {
            Ok(_) => panic!("{name}: expected {want:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{name}: expected {want:?}, got {error:#}"),
        }
    }
}

/// The artifact of an independent asset is the module name of its reused jar. The same module output can be a
/// catalogue input of the remainder.
#[test]
fn plan_reads_the_module_of_a_reused_jar_as_a_plain_input() {
    let root = temp();
    let (mut recipe, catalogue) = sample_plan(root.path());
    recipe.assets[0].artifact = "module".to_owned();
    let execution = plan(&recipe, &catalogue).unwrap();
    assert_eq!(execution.inputs, catalogue.artifacts);
}

#[test]
fn plan_rejects_unsafe_destinations_and_collisions() {
    for destination in [
        "",
        "/absolute",
        "../escape",
        "lib/../../escape",
        "lib//double",
        "lib/./dot",
        "lib/trailing/",
        r"lib\windows",
        "C:drive",
        "lib/zero\0",
        "lib/new\nline",
        "lib/modules/separate.jar",
        "LIB/MODULES/SEPARATE.JAR",
        "lib/modules/separate.jar/child",
        "lib/modules",
        "lib/trailing.",
        "lib/trailing ",
        "lib/CON.jar",
        "lib/NUL",
        "lib/LPT1.txt",
        "lib/<file>",
        "lib/caf\u{e9}.jar",
        "lib/a&b.jar",
    ] {
        let root = temp();
        let (mut recipe, catalogue) = sample_plan(root.path());
        recipe.assets[1].destination = destination.to_owned();
        set_destination(&mut recipe.operations[0], destination);
        assert!(
            plan(&recipe, &catalogue).is_err(),
            "accepted the unsafe destination {destination:?}"
        );
    }
}

#[test]
fn plan_rejects_unsafe_directory_references() {
    for relative in ["", "../escape", "/absolute", "nested/../../escape", r"nested\escape", "./file"] {
        let root = temp();
        let (mut recipe, mut catalogue) = sample_plan(root.path());
        catalogue.artifacts[0].kind = ArtifactKind::Directory;
        first_input(&mut recipe).path = relative.to_owned();
        assert!(
            plan(&recipe, &catalogue).is_err(),
            "accepted the unsafe directory reference {relative:?}"
        );
    }
}

/// The Go test planned symlink operations. The typed recipe has none, so the port checks the same graphs through the
/// link check of a tree: `distpath::validate_links`, then `planfile::validate::validate_link_graph`. `distpath` refuses a link chain,
/// so a graph where one link resolves through another is refused, and a cycle is a chain. It also refuses an empty
/// segment in a target, so the former case of the exact spelling `./dir///` is a refusal.
#[test]
fn link_graph_uses_raw_components_and_known_directories() {
    type Case<'a> = (&'a str, &'a [&'a str], &'a [(&'a str, &'a str)], &'a str);
    let tests: [Case<'_>; 19] = [
        (
            "framework",
            &["Framework/Versions/A/binary", "Framework/Versions/A/Headers/header.h"],
            &[
                ("Framework/Versions/Current", "A"),
                ("Framework/binary", "Versions/A/binary"),
                ("Framework/Headers", "Versions/A/Headers"),
            ],
            "",
        ),
        (
            "framework chain",
            &["Framework/Versions/A/binary"],
            &[("Framework/Versions/Current", "A"), ("Framework/binary", "Versions/Current/binary")],
            "chain",
        ),
        ("dot spelling", &["dir/file"], &[("alias", "./dir"), ("file", "./dir/./file")], ""),
        ("repeated slashes", &["dir/file"], &[("alias", "./dir///")], "empty segment"),
        (
            "dot dot after expansion",
            &["shallow/file"],
            &[
                ("deep/inside/redirect", "../../shallow"),
                ("file", "deep/inside/redirect/../shallow/file"),
            ],
            "chain",
        ),
        ("directory boundary", &["directory/file"], &[("alias", "dir")], "unresolved"),
        ("missing child", &["dir/file"], &[("alias", "dir/absent")], "unresolved"),
        ("file traversal", &["file"], &[("alias", "file/child")], "non-directory"),
        ("file trailing slash", &["file"], &[("alias", "file/")], "empty segment"),
        ("file dot dot", &["file"], &[("alias", "file/../file")], "non-directory"),
        (
            "missing cancelled directory",
            &["file"],
            &[("alias", "missing/../file")],
            "unresolved",
        ),
        ("direct cycle", &[], &[("a", "b"), ("b", "a")], "chain"),
        ("reviewer raw dot dot cycle", &["file"], &[("a", "b/../file"), ("b", "a")], "chain"),
        ("ancestor cycle", &["dir/file"], &[("dir/back", "..")], "cycle"),
        ("root cycle", &["file"], &[("back", ".")], "cycle"),
        (
            "directory cross cycle",
            &["left/file", "right/file"],
            &[("left/to-right", "../right"), ("right/to-left", "../left")],
            "cycle",
        ),
        (
            "directory chain cycle",
            &["dir/file"],
            &[("alias", "dir"), ("dir/back", "../alias")],
            "chain",
        ),
        ("escape", &["shallow/file"], &[("escape", "../outside")], "escapes"),
        (
            "ambiguous directory casing",
            &["Dir/first", "dir/second"],
            &[("alias", "Dir")],
            "casing",
        ),
    ];
    for (name, files, links, want) in tests {
        let mut nodes: Vec<(String, bool)> = files.iter().map(|file| ((*file).to_owned(), false)).collect();
        let links: BTreeMap<String, String> = links
            .iter()
            .map(|(link, target)| ((*link).to_owned(), (*target).to_owned()))
            .collect();
        nodes.extend(links.keys().map(|link| (link.clone(), false)));
        let result = validate_plugin_links(&nodes, &links);
        match (result, want) {
            (Ok(()), "") => {}
            (Ok(()), want) => panic!("{name}: expected {want:?}"),
            (Err(error), "") => panic!("{name}: {error}"),
            (Err(error), want) => assert!(format!("{error:#}").contains(want), "{name}: expected {want:?}, got {error:#}"),
        }
    }
}
