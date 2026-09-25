//! The port of `execute_test.go`. The tests that run the packer binary are in `bins/plugin-remainder-packer/tests`.

use std::fs;
use std::path::Path;

use planfile::contract::{
    Asset, Catalogue, DISTRIBUTION_SCOPE, Filter, Manifest, Operation, Recipe, Reference, SCOPED_VERSION, Source, VERSION,
};

use super::planning::{jar_sources, sample_plan};
use super::*;

fn tree_plan(root: &Path) -> (Recipe, Catalogue) {
    let recipe = Recipe {
        version: TREE_VERSION,
        plugin: "tree".to_owned(),
        layout_signature: "tree-v2".to_owned(),
        assets: vec![tree_asset("kotlinc"), independent("lib/independent.jar", "independent")],
        operations: vec![Operation::CopyTree {
            destination: "kotlinc".to_owned(),
            input: Reference::artifact("tree"),
        }],
    };
    (recipe, catalogue(vec![directory_artifact("tree", root)]))
}

/// The Go test `checkAssetNamespace` created the remainder entries and the independent files in a probe directory. The
/// port compares the case identities, so each collision fails on every file system.
#[test]
fn independent_destinations_collide_with_remainder_tree_entries() {
    type Case<'a> = (&'a str, &'a str, &'a [&'a str], &'a str);
    let cases: [Case<'_>; 4] = [
        (
            "case alias",
            "lib/independent.jar",
            &["Independent.jar"],
            r#"conflicting output destination "lib/independent.jar": the remainder writes "lib/Independent.jar""#,
        ),
        (
            "directory at the file",
            "lib/independent.jar",
            &["independent.jar/inner.txt"],
            r#"conflicting output destination "lib/independent.jar": the remainder writes "lib/independent.jar""#,
        ),
        (
            "file at the parent",
            "lib/modules/independent.jar",
            &["modules"],
            r#"conflicting output directory "lib/modules" of "lib/modules/independent.jar": the remainder writes the file "lib/modules""#,
        ),
        (
            "shared parent directory",
            "lib/modules/independent.jar",
            &["other.jar", "modules/other.jar"],
            "",
        ),
    ];
    for (name, destination, entries, want) in cases {
        let root = temp();
        let tree = root.path().join("tree");
        fs::create_dir_all(&tree).unwrap();
        for entry in entries {
            write_test_file(&tree.join(entry), b"entry");
        }
        let recipe = Recipe {
            version: TREE_VERSION,
            plugin: "tree".to_owned(),
            layout_signature: "tree-v2".to_owned(),
            assets: vec![tree_asset("lib"), independent(destination, "independent")],
            operations: vec![Operation::CopyTree {
                destination: "lib".to_owned(),
                input: Reference::artifact("tree"),
            }],
        };
        let catalogue = catalogue(vec![directory_artifact("tree", &tree)]);
        if want.is_empty() {
            let written = write_execution(&recipe, &catalogue);
            assert_content(&written.output.join("lib/modules/other.jar"), "entry");
            continue;
        }
        let execution = plan(&recipe, &catalogue).unwrap_or_else(|error| panic!("{name}: plan: {error}"));
        let error = execution
            .write(&root.path().join("output"), &root.path().join("inventory.json"))
            .unwrap_err();
        assert!(error.message().contains(want), "{name}: expected {want:?}, got {error}");
        assert_no_published_outputs(root.path());
    }
}

/// The Go test wrote a distribution-scope copy beside a plugin copy of the same destination. The remainder writes only
/// plugin files now, so the plan refuses a remainder asset of the distribution scope.
#[test]
fn distribution_scope_is_refused_for_a_remainder_asset() {
    let root = temp();
    let input = root.path().join("input");
    write_test_file(&input, b"distribution");
    let recipe = Recipe {
        version: SCOPED_VERSION,
        plugin: "scoped".to_owned(),
        layout_signature: "scoped-v3".to_owned(),
        assets: vec![Asset {
            class_path: Some(false),
            scope: DISTRIBUTION_SCOPE.to_owned(),
            ..remainder("lib/native.bin")
        }],
        operations: vec![Operation::Copy {
            destination: "lib/native.bin".to_owned(),
            mode: 0o644,
            input: Reference::artifact("input"),
        }],
    };
    expect_plan_error(
        &recipe,
        &catalogue(vec![file_artifact("input", &input)]),
        "the remainder writes only plugin files",
    );
}

fn copy_recipe(mode: u32) -> Recipe {
    Recipe {
        version: VERSION,
        plugin: "resource".to_owned(),
        layout_signature: "resource-v1".to_owned(),
        assets: vec![remainder("resource.txt")],
        operations: vec![Operation::Copy {
            destination: "resource.txt".to_owned(),
            mode,
            input: Reference::artifact("resource"),
        }],
    }
}

/// The Go case of mode zero kept the source mode. The plan files state 0644 or 0755, so mode zero is refused.
#[test]
fn copy_modes_override_the_source() {
    for (input_mode, output_mode) in [(0o555, 0o644), (0o644, 0o755)] {
        let root = temp();
        let input = root.path().join("resource.txt");
        write_test_file(&input, b"resource");
        chmod(&input, input_mode);
        let written = write_execution(&copy_recipe(output_mode), &catalogue(vec![file_artifact("resource", &input)]));
        assert_mode(&written.output.join("resource.txt"), output_mode);
        assert_eq!(written.inventory[0].mode, output_mode);
    }
    let root = temp();
    let input = root.path().join("resource.txt");
    write_test_file(&input, b"resource");
    expect_plan_error(
        &copy_recipe(0),
        &catalogue(vec![file_artifact("resource", &input)]),
        "unsupported file mode 0",
    );
}

#[test]
fn copy_tree_preserves_its_own_outputs_and_empty_root() {
    for empty in [false, true] {
        let root = temp();
        let source = root.path().join("source");
        fs::create_dir_all(&source).unwrap();
        chmod(&source, 0o750);
        if !empty {
            write_test_file(&source.join("bin/tool"), b"executable");
            chmod(&source.join("bin/tool"), 0o751);
            write_test_file(&source.join("lib/compiler.jar"), b"not classpath");
            fs::create_dir_all(source.join("empty/nested")).unwrap();
            chmod(&source.join("empty/nested"), 0o710);
            symlink("./bin/tool", &source.join("current"));
            symlink("empty", &source.join("empty-link"));
        }
        let (recipe, catalogue) = tree_plan(&source);
        let execution = plan(&recipe, &catalogue).unwrap();
        let output = root.path().join("plugin");
        let metadata = root.path().join("metadata.json");
        execution.write(&output, &metadata).unwrap();
        let inventory = filemeta::read(&metadata).unwrap();
        assert!(!inventory.is_empty());
        for entry in &inventory {
            assert!(
                entry.relative_path == "kotlinc" || entry.relative_path.starts_with("kotlinc/"),
                "unowned output: {entry:?}"
            );
            let name = entry.relative_path.trim_start_matches("kotlinc").trim_start_matches('/');
            let actual = filemeta::inspect(&crate::paths::host(&source, name), &entry.relative_path).unwrap();
            assert_eq!(&actual, entry, "tree parity");
        }
        if empty {
            assert_eq!(inventory.len(), 1, "the empty tree lost its root");
        }
        assert!(!exists(&output.join("lib")), "an independent output entered the remainder");
    }
}

fn boundary_execution(source: &Path, tree: bool) -> crate::Execution {
    let (mut recipe, catalogue) = tree_plan(source);
    if !tree {
        recipe.version = VERSION;
        recipe.assets[0].kind = String::new();
        recipe.assets[0].class_path = None;
        recipe.operations[0] = Operation::Copy {
            destination: "kotlinc".to_owned(),
            mode: 0o644,
            input: member("tree", "file"),
        };
    }
    plan(&recipe, &catalogue).unwrap()
}

fn aliases(first: &Path, second: &Path) -> bool {
    matches!((crate::paths::file_id(first), crate::paths::file_id(second)), (Ok(first), Ok(second)) if first == second)
}

#[test]
fn filesystem_aliases_cannot_put_outputs_inside_inputs() {
    for tree in [false, true] {
        for (name, alias_name) in [("Source", "source"), ("Caf\u{e9}", "Cafe\u{301}")] {
            for output_kind in ["payload", "inventory"] {
                let root = temp();
                let source = root.path().join(name);
                let alias = root.path().join(alias_name);
                write_test_file(&source.join("file"), b"immutable source");
                if !exists(&alias) {
                    eprintln!("The file system distinguishes {name:?} and {alias_name:?}");
                    continue;
                }
                assert!(aliases(&source, &alias));
                let execution = boundary_execution(&source, tree);
                let (mut output, mut inventory) = (root.path().join("output"), root.path().join("inventory.json"));
                if output_kind == "payload" {
                    output = alias.join("missing/generated-output");
                } else {
                    inventory = alias.join("missing/generated-inventory.json");
                }
                let before = materialization_record(root.path());
                let error = execution.write(&output, &inventory).unwrap_err();
                assert!(
                    error.message().contains("overlaps input"),
                    "tree={tree}/{name}/{output_kind}: {error}"
                );
                assert_eq!(materialization_record(root.path()), before, "the boundary failure wrote files");
            }
        }
    }
}

#[test]
fn output_and_inventory_reserve_aliased_roots_before_writing() {
    for (name, alias_name) in [("Output", "output"), ("Caf\u{e9}", "Cafe\u{301}")] {
        for existing in [false, true] {
            let root = temp();
            let source = root.path().join("source");
            write_test_file(&source.join("file"), b"immutable source");
            let output = root.path().join(name);
            let alias = root.path().join(alias_name);
            fs::create_dir(&output).unwrap();
            let aliased = aliases(&output, &alias);
            if !existing {
                fs::remove_dir(&output).unwrap();
            }
            let before = materialization_record(root.path());
            let result = boundary_execution(&source, true).write(&output, &alias.join("inventory.json"));
            if !aliased {
                result.unwrap_or_else(|error| panic!("distinct output names were rejected: {error}"));
                continue;
            }
            let error = result.unwrap_err();
            assert!(
                error.message().contains("outside the payload"),
                "{name}/existing={existing}: {error}"
            );
            assert_eq!(materialization_record(root.path()), before, "the boundary failure wrote files");
        }
    }
}

#[test]
fn output_root_symlinks_reject_trailing_separators() {
    for tree in [false, true] {
        for suffix in ["", "/", "//", "/."] {
            let root = temp();
            let source = root.path().join("source");
            write_test_file(&source.join("file"), b"immutable source");
            fs::create_dir(root.path().join("target")).unwrap();
            let output = root.path().join("output");
            symlink("target", &output);
            let before = materialization_record(root.path());
            let output = Path::new(&format!("{}{suffix}", output.display())).to_path_buf();
            let error = boundary_execution(&source, tree)
                .write(&output, &root.path().join("inventory.json"))
                .unwrap_err();
            assert!(
                error.message().contains("not a real directory"),
                "tree={tree}/suffix={suffix:?}: {error}"
            );
            assert_eq!(materialization_record(root.path()), before, "the boundary failure wrote files");
        }
    }
}

#[test]
fn input_root_links_reject_output_aliases() {
    for destination in ["payload", "inventory"] {
        let root = temp();
        let source = root.path().join("raw");
        write_test_file(&source.join("file"), b"immutable raw input");
        let alias = root.path().join("transport");
        symlink("raw", &alias);
        let execution = boundary_execution(&source, true);
        let (mut output, mut inventory) = (root.path().join("output"), root.path().join("inventory.json"));
        if destination == "payload" {
            output = alias.join("nested/output");
        } else {
            inventory = alias.join("nested/inventory.json");
        }
        let before = materialization_record(root.path());
        let error = execution.write(&output, &inventory).unwrap_err();
        assert!(error.message().contains("overlaps input"), "{destination}: {error}");
        assert_eq!(materialization_record(root.path()), before, "the raw inputs changed");
    }
}

/// The Go test names the output `Café` beside the source `Cafe\u{301}`. The inventory holds no such name, so only the
/// host names of the source and the output differ in case or in normalization.
#[test]
fn filesystem_boundaries_accept_distinct_source_names() {
    for (name, output_name) in [("Source", "source"), ("Caf\u{e9}", "Cafe\u{301}")] {
        let root = temp();
        let source = root.path().join(name);
        let output = root.path().join(output_name);
        write_test_file(&source.join("file"), b"immutable source");
        if exists(&output) {
            eprintln!("The file system aliases {name:?} and {output_name:?}");
            continue;
        }
        let before = filemeta::inventory(&source).unwrap();
        boundary_execution(&source, true)
            .write(&output, &root.path().join("inventory.json"))
            .unwrap_or_else(|error| panic!("distinct names were rejected: {error}"));
        assert_eq!(filemeta::inventory(&source).unwrap(), before, "the source changed");
    }
}

#[test]
fn output_boundaries_retain_parent_transport_links() {
    for tree in [false, true] {
        let root = temp();
        let source = root.path().join("source");
        write_test_file(&source.join("file"), b"resource");
        fs::create_dir(root.path().join("transport")).unwrap();
        symlink("transport", &root.path().join("alias"));
        let output = format!("{}/", root.path().join("alias/output").display());
        boundary_execution(&source, tree)
            .write(Path::new(&output), &root.path().join("alias/inventory.json"))
            .unwrap();
        filemeta::read(&root.path().join("transport/inventory.json")).unwrap();
    }
}

#[test]
fn copy_tree_materializes_bazel_transport_files_and_keeps_payload_links() {
    let root = temp();
    let license = root.path().join("backing/adoc/LICENSE");
    write_test_file(&license, b"license text");
    chmod(&license, 0o751);
    let transport = root.path().join("transport");
    symlink(&license, &transport.join("adoc/LICENSE"));
    symlink("adoc/LICENSE", &transport.join("current"));
    let (recipe, catalogue) = tree_plan(&transport);
    let written = write_execution(&recipe, &catalogue);
    let materialized = written.output.join("kotlinc/adoc/LICENSE");
    assert_content(&materialized, "license text");
    assert_mode(&materialized, 0o751);
    assert_link(&written.output.join("kotlinc/current"), "adoc/LICENSE");
    for entry in &written.inventory {
        if entry.relative_path == "kotlinc/adoc/LICENSE" {
            assert_eq!(
                entry.entry_type,
                filemeta::EntryType::File,
                "the transport file entered the inventory as a link"
            );
        }
    }
}

#[test]
fn copy_tree_copies_into_plugin_root() {
    let root = temp();
    let source = root.path().join("source");
    write_test_file(&source.join("bin/tool"), b"tool");
    let (mut recipe, catalogue) = tree_plan(&source);
    recipe.assets[0].destination = String::new();
    recipe.operations[0] = Operation::CopyTree {
        destination: String::new(),
        input: Reference::artifact("tree"),
    };
    let written = write_execution(&recipe, &catalogue);
    assert_eq!(read_test_file(&written.output.join("bin/tool")), b"tool");
    assert!(
        written.inventory.iter().all(|entry| !entry.relative_path.is_empty()),
        "the plugin root entered the inventory"
    );
}

#[test]
fn copy_tree_rejects_declared_descendant_collision_before_writing() {
    let root = temp();
    let source = root.path().join("source");
    write_test_file(&source.join("lib/compiler.jar"), b"tree content");
    let (mut recipe, catalogue) = tree_plan(&source);
    recipe.assets[1] = remainder("kotlinc/lib/compiler.jar");
    recipe.operations.push(jar("kotlinc/lib/compiler.jar", Vec::new()));
    let execution = plan(&recipe, &catalogue).unwrap();
    let error = execution
        .write(&root.path().join("output"), &root.path().join("inventory.json"))
        .unwrap_err();
    assert!(error.message().contains("conflicting output destination"), "{error}");
    assert_no_published_outputs(root.path());
}

#[test]
fn copy_tree_rejects_unsafe_bazel_transport_files_before_writing() {
    type Prepare = fn(&Path, &Path);
    let scenarios: [(&str, Prepare); 3] = [
        ("path mismatch", |root, transport| {
            let target = root.join("backing/other/LICENSE");
            write_test_file(&target, b"license");
            symlink(&target, &transport.join("adoc/LICENSE"));
        }),
        ("conflicting roots", |root, transport| {
            for name in ["adoc/LICENSE", "css/LICENSE"] {
                let target = root.join(crate::paths::dir(name)).join(name);
                write_test_file(&target, name.as_bytes());
                symlink(&target, &transport.join(name));
            }
        }),
        ("linked parent", |root, transport| {
            let real_parent = root.join("real/adoc");
            write_test_file(&real_parent.join("LICENSE"), b"license");
            let backing = root.join("backing");
            fs::create_dir(&backing).unwrap();
            symlink(&real_parent, &backing.join("adoc"));
            symlink(backing.join("adoc/LICENSE"), &transport.join("adoc/LICENSE"));
        }),
    ];
    for (name, prepare) in scenarios {
        let root = temp();
        let transport = root.path().join("transport");
        fs::create_dir(&transport).unwrap();
        prepare(root.path(), &transport);
        let (recipe, catalogue) = tree_plan(&transport);
        let execution = plan(&recipe, &catalogue).unwrap();
        assert!(
            execution
                .write(&root.path().join("output"), &root.path().join("inventory.json"))
                .is_err(),
            "{name}: accepted an unsafe transport tree"
        );
        assert_no_published_outputs(root.path());
    }
}

#[test]
fn copy_tree_rejects_unsafe_entries_before_writing() {
    type Mutate = fn(&Path) -> std::io::Result<()>;
    let scenarios: [(&str, Mutate); 14] = [
        ("missing", |source| fs::remove_dir(source)),
        ("root link", |source| {
            fs::remove_dir(source)?;
            symlink(".", source);
            Ok(())
        }),
        ("root file", |source| {
            fs::remove_dir(source)?;
            fs::write(source, b"file")
        }),
        ("missing link", |source| {
            symlink("missing", &source.join("link"));
            Ok(())
        }),
        ("escaping link", |source| {
            symlink("../outside", &source.join("link"));
            Ok(())
        }),
        ("absolute link", |source| {
            symlink(source, &source.join("link"));
            Ok(())
        }),
        ("directory cycle", |source| {
            symlink(".", &source.join("link"));
            Ok(())
        }),
        ("file traversal", |source| {
            fs::write(source.join("file"), b"")?;
            symlink("file/../file", &source.join("link"));
            Ok(())
        }),
        ("hard link", |source| {
            fs::write(source.join("file"), b"")?;
            fs::hard_link(source.join("file"), source.join("alias"))
        }),
        ("case alias", |source| {
            fs::write(source.join("file"), b"")?;
            symlink("FILE", &source.join("alias"));
            Ok(())
        }),
        ("special file", |source| {
            let status = std::process::Command::new("mkfifo").arg(source.join("pipe")).status()?;
            assert!(status.success());
            Ok(())
        }),
        ("unsafe name", |source| fs::write(source.join("bad:name"), b"")),
        ("nested collision", |source| {
            fs::create_dir(source.join("nested"))?;
            fs::write(source.join("nested/Stra\u{df}e"), b"")?;
            fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(source.join("nested/STRASSE"))
                .map(|_| ())
        }),
        ("link cycle", |source| {
            symlink("second", &source.join("first"));
            symlink("first", &source.join("second"));
            Ok(())
        }),
    ];
    for (name, mutate) in scenarios {
        let root = temp();
        let source = root.path().join("source");
        fs::create_dir(&source).unwrap();
        let (recipe, catalogue) = tree_plan(&source);
        let execution = plan(&recipe, &catalogue).unwrap();
        if let Err(error) = mutate(&source) {
            if name == "nested collision" && error.kind() == std::io::ErrorKind::AlreadyExists {
                eprintln!("The file system prevents two aliased source names");
                continue;
            }
            panic!("{name}: {error}");
        }
        let output = root.path().join("plugin");
        let metadata = root.path().join("metadata.json");
        assert!(execution.write(&output, &metadata).is_err(), "{name}: accepted an unsafe tree");
        for file in [&output, &metadata] {
            assert!(!exists(file), "{name}: wrote output before the tree validation: {}", file.display());
        }
    }
}

/// The Go test also packed prepared `file` entries and a link. The plan files state neither, so the typed recipe has
/// no such source or operation.
#[test]
fn batch_writes_only_its_assets_in_layout_order() {
    let root = temp();
    let (mut recipe, mut catalogue) = sample_plan(root.path());
    archive_file(
        &root.path().join("module.jar"),
        &[
            ("META-INF/plugin.xml", "original descriptor"),
            ("spring/security/Mvc.class", "shared content module"),
            ("native/lib.so", "unsigned"),
            ("native/extracted.so", "extract me"),
            ("module/After.class", "after native"),
            ("META-INF/listOfEntities.txt", " Module "),
            ("icon-robots.txt", "excluded"),
        ],
    );
    archive_file(
        &root.path().join("first.jar"),
        &[
            ("first/Library.class", "first library"),
            ("shared.txt", "first wins"),
            ("META-INF/listOfEntities.txt", " First "),
            ("LICENSE", "excluded"),
        ],
    );
    archive_file(
        &root.path().join("second.jar"),
        &[
            ("second/Library.class", "second library"),
            ("shared.txt", "second loses"),
            ("META-INF/listOfEntities.txt", " Second "),
        ],
    );
    let prepared = root.path().join("prepared");
    for (name, content) in [
        ("plugin.xml", "patched descriptor"),
        ("signed.so", "signed native"),
        ("custom", "custom value"),
    ] {
        write_test_file(&prepared.join(name), content.as_bytes());
    }
    catalogue.artifacts.extend([
        file_artifact("first", root.path().join("first.jar")),
        file_artifact("second", root.path().join("second.jar")),
        directory_artifact("prepared", &prepared),
    ]);
    let module = jar_sources(&mut recipe, 0)[0].clone();
    *jar_sources(&mut recipe, 0) = vec![
        Source::Patch {
            entry: "META-INF/plugin.xml".to_owned(),
            input: member("prepared", "plugin.xml"),
            manifest: Manifest::Keep,
        },
        archive_source(Reference::artifact("first"), Filter::Library, Manifest::Drop),
        archive_source(Reference::artifact("second"), Filter::Library, Manifest::Drop),
        module.clone(),
        module,
    ];
    recipe.assets.extend([
        remainder("lib/nested/custom.jar"),
        remainder("lib/first-library.jar"),
        remainder("bin/native"),
    ]);
    recipe.operations.extend([
        Operation::Copy {
            destination: "bin/native".to_owned(),
            mode: 0o755,
            input: member("prepared", "signed.so"),
        },
        jar(
            "lib/first-library.jar",
            vec![archive_source(Reference::artifact("first"), Filter::Library, Manifest::Keep)],
        ),
        Operation::Jar {
            destination: "lib/nested/custom.jar".to_owned(),
            mode: 0o644,
            sources: vec![Source::Patch {
                entry: "custom/Value.class".to_owned(),
                input: member("prepared", "custom"),
                manifest: Manifest::Keep,
            }],
            merge_entities: false,
            directory_entries: true,
        },
    ]);
    let written = write_execution(&recipe, &catalogue);
    assert!(
        !exists(&written.output.join("lib/modules/separate.jar")),
        "the independent asset reached the remainder"
    );
    require_inventory_matches_tree(&written.output, &written.inventory);
    let paths: Vec<&str> = written.inventory.iter().map(|entry| entry.relative_path.as_str()).collect();
    assert_eq!(
        paths,
        ["bin/native", "lib/first-library.jar", "lib/nested/custom.jar", "lib/plugin.jar"]
    );
    assert_eq!(written.inventory[0].mode, 0o755, "lost the mode");
    let (names, entries) = read_archive(&written.output.join("lib/plugin.jar"));
    assert_eq!(
        names,
        [
            "META-INF/plugin.xml",
            "first/Library.class",
            "shared.txt",
            "second/Library.class",
            "spring/security/Mvc.class",
            "native/lib.so",
            "native/extracted.so",
            "module/After.class",
            "META-INF/listOfEntities.txt",
            "__index__",
        ],
        "the source order changed"
    );
    assert_eq!(text(&entries["META-INF/plugin.xml"]), "patched descriptor");
    assert_eq!(text(&entries["native/lib.so"]), "unsigned");
    assert_eq!(text(&entries["shared.txt"]), "first wins");
    assert_eq!(text(&entries["META-INF/listOfEntities.txt"]), "First\nSecond\nModule\nModule");
    let (custom, _) = read_archive(&written.output.join("lib/nested/custom.jar"));
    assert_eq!(
        custom,
        ["custom/Value.class", "custom/", "__index__"],
        "the directory entries of a test plugin"
    );
    let reference = root.path().join("reference/library.jar");
    jarpack::MergeSpec {
        output: reference.clone(),
        keep_manifest: true,
        sources: vec![jarpack::Source::archive(root.path().join("first.jar"), jarpack::library_filter())],
        ..jarpack::MergeSpec::default()
    }
    .pack()
    .unwrap();
    assert_eq!(
        read_test_file(&reference),
        read_test_file(&written.output.join("lib/first-library.jar")),
        "the batch writer changed ordinary jar bytes"
    );
}

#[test]
fn failed_merge_publishes_nothing() {
    for name in [
        "missing input",
        "late patch",
        "conflicting patches",
        "unsafe archive name",
        "unsafe patch entry",
    ] {
        let root = temp();
        let (mut recipe, mut catalogue) = sample_plan(root.path());
        archive_file(&root.path().join("module.jar"), &[("entry.txt", "original")]);
        let patch = Source::Patch {
            entry: "entry.txt".to_owned(),
            input: Reference::artifact("module"),
            manifest: Manifest::Keep,
        };
        match name {
            "missing input" => catalogue.artifacts[0].root = root.path().join("missing.jar").display().to_string(),
            "late patch" => jar_sources(&mut recipe, 0).push(patch),
            "conflicting patches" => *jar_sources(&mut recipe, 0) = vec![patch.clone(), patch],
            "unsafe archive name" => archive_file(&root.path().join("module.jar"), &[("../outside", "bad")]),
            _ => {
                *jar_sources(&mut recipe, 0) = vec![Source::Patch {
                    entry: "../outside".to_owned(),
                    input: Reference::artifact("module"),
                    manifest: Manifest::Drop,
                }];
            }
        }
        let output = root.path().join("output");
        let inventory = root.path().join("inventory.json");
        let result = plan(&recipe, &catalogue).and_then(|execution| execution.write(&output, &inventory));
        assert!(result.is_err(), "{name}: accepted an invalid merge");
        for file in [&output, &inventory, &root.path().join("outside")] {
            assert!(!exists(file), "{name}: published partial output {}", file.display());
        }
        assert_no_published_outputs(root.path());
    }
}

#[test]
fn directory_boundary_and_declared_root_symlinks() {
    for escape in [false, true] {
        let root = temp();
        let (mut recipe, mut catalogue) = sample_plan(root.path());
        let declared = root.path().join("declared");
        archive_file(&declared.join("module.jar"), &[("file", "content")]);
        catalogue.artifacts[0].kind = "directory".to_owned();
        let link = root.path().join("bazel-root-link");
        catalogue.artifacts[0].root = link.display().to_string();
        symlink(&declared, &link);
        let target = if escape {
            archive_file(&root.path().join("outside.jar"), &[("file", "outside")]);
            "../outside.jar"
        } else {
            "module.jar"
        };
        symlink(target, &declared.join("input.jar"));
        if let Source::Archive { input, .. } = &mut jar_sources(&mut recipe, 0)[0] {
            input.path = "input.jar".to_owned();
        }
        let result = plan(&recipe, &catalogue)
            .unwrap()
            .write(&root.path().join("output"), &root.path().join("inventory.json"));
        match (escape, result) {
            (false, Ok(())) => {}
            (true, Err(error)) => assert!(error.message().contains("escapes"), "{error}"),
            (escape, result) => panic!("escape={escape}: {result:?}"),
        }
    }
}

#[test]
fn output_boundaries_and_existing_outputs() {
    for scenario in [
        "inventory inside output",
        "output inside input",
        "output symlink",
        "nonempty output",
        "existing inventory",
        "empty output",
    ] {
        let root = temp();
        let (mut recipe, mut catalogue) = sample_plan(root.path());
        archive_file(&root.path().join("module.jar"), &[("file", "content")]);
        let output = root.path().join("output");
        let mut inventory = root.path().join("inventory.json");
        match scenario {
            "inventory inside output" => inventory = output.join("inventory.json"),
            "output inside input" => {
                catalogue.artifacts[0].kind = "directory".to_owned();
                catalogue.artifacts[0].root = root.path().display().to_string();
                if let Source::Archive { input, .. } = &mut jar_sources(&mut recipe, 0)[0] {
                    input.path = "module.jar".to_owned();
                }
            }
            "output symlink" => symlink(root.path(), &output),
            "nonempty output" => write_test_file(&output.join("keep"), b"keep"),
            "existing inventory" => write_test_file(&inventory, b"keep"),
            _ => fs::create_dir(&output).unwrap(),
        }
        let result = plan(&recipe, &catalogue).unwrap().write(&output, &inventory);
        assert_eq!(result.is_ok(), scenario == "empty output", "{scenario}: {result:?}");
        if scenario == "nonempty output" {
            assert_eq!(read_test_file(&output.join("keep")), b"keep", "changed an existing output");
        }
    }
}

#[test]
fn empty_remainder_and_empty_jar() {
    for with_jar in [false, true] {
        let root = temp();
        let (mut recipe, mut catalogue) = sample_plan(root.path());
        catalogue.artifacts.clear();
        if with_jar {
            jar_sources(&mut recipe, 0).clear();
        } else {
            recipe.operations.clear();
            recipe.assets.truncate(1);
        }
        let written = write_execution(&recipe, &catalogue);
        if with_jar {
            let (names, _) = read_archive(&written.output.join("lib/plugin.jar"));
            assert!(names.is_empty(), "expected one empty jar: {names:?}");
            assert_eq!(written.inventory.len(), 1);
        } else {
            assert!(written.inventory.is_empty(), "expected an empty inventory");
        }
    }
}

/// The Go tests of Unicode aliases wrote NFC and NFD spellings of one name. The inventory holds only ASCII names, so
/// the plan refuses such a destination before any reservation, and an ASCII case alias is a destination collision.
#[test]
fn write_rejects_aliases_before_opening_jar_inputs() {
    for destinations in [
        &["lib/caf\u{e9}.jar", "lib/cafe\u{301}.jar"][..],
        &["lib/caf\u{e9}/first.jar", "lib/cafe\u{301}/second.jar"][..],
        &["lib/Plugin.jar", "lib/plugin.jar"][..],
    ] {
        let root = temp();
        let (mut recipe, catalogue) = sample_plan(root.path());
        write_test_file(
            Path::new(&catalogue.artifacts[0].root),
            b"invalid jar: the plan fails before this input opens",
        );
        let operation = recipe.operations[0].clone();
        recipe.assets.clear();
        recipe.operations.clear();
        for destination in destinations {
            recipe.assets.push(remainder(destination));
            let mut operation = operation.clone();
            if let Operation::Jar { destination: value, .. } = &mut operation {
                *value = (*destination).to_owned();
            }
            recipe.operations.push(operation);
        }
        assert!(
            plan(&recipe, &catalogue).is_err(),
            "accepted the aliased destinations {destinations:?}"
        );
        assert_no_published_outputs(root.path());
    }
}

/// An independent file asset and a remainder asset below it, or the reverse, collide in the plan. The collision fails
/// before a merge opens an input.
#[test]
fn write_rejects_file_directory_collisions_across_independent_assets() {
    for (independent_destination, remainder_destination) in [("lib/a", "lib/a/remainder.jar"), ("lib/a/independent.jar", "lib/a")] {
        for reverse in [false, true] {
            let root = temp();
            let (mut recipe, catalogue) = sample_plan(root.path());
            write_test_file(
                Path::new(&catalogue.artifacts[0].root),
                b"invalid jar: reservations fail before this input opens",
            );
            recipe.assets[0].destination = independent_destination.to_owned();
            recipe.assets[1].destination = remainder_destination.to_owned();
            if let Operation::Jar { destination, .. } = &mut recipe.operations[0] {
                *destination = remainder_destination.to_owned();
            }
            if reverse {
                recipe.assets.reverse();
            }
            let error = plan(&recipe, &catalogue).map(|_| ()).unwrap_err();
            assert!(error.message().contains("destination collision"), "{error}");
            assert_no_published_outputs(root.path());
        }
    }
}

/// One module jar in a deliberate central-directory order. The Kotlin preparer reads a module jar in this order, and so
/// does jarpack.
const MODULE_EXCLUDES_FIXTURE: [(&str, &str); 8] = [
    ("drop/Ignore.class", "excluded by the glob"),
    ("keep/Service.class", "kept"),
    ("META-INF/listOfEntities.txt", " Module "),
    ("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
    ("icon-robots.txt", "excluded by the module filter"),
    ("drop/nested/Deep.class", "excluded by the glob"),
    ("keep/drop/Kept.class", "kept: the glob is anchored"),
    ("module-info.class", "excluded by the module filter"),
];

/// The Go test also compared the jar with the jar of the prepared `file` entries that the Kotlin preparer wrote. The
/// typed recipe has no prepared file entry, so that half has no port.
#[test]
fn module_excludes_select_entries_in_central_directory_order() {
    type Case<'a> = (&'a str, Manifest, bool, &'a [&'a str], &'a [&'a str]);
    let tests: [Case<'_>; 3] = [
        (
            "keep",
            Manifest::Keep,
            true,
            &["drop/**"],
            &[
                "keep/Service.class",
                "META-INF/MANIFEST.MF",
                "keep/drop/Kept.class",
                "META-INF/listOfEntities.txt",
                "__index__",
            ],
        ),
        (
            "drop",
            Manifest::Drop,
            true,
            &["drop/**"],
            &[
                "keep/Service.class",
                "keep/drop/Kept.class",
                "META-INF/listOfEntities.txt",
                "__index__",
            ],
        ),
        (
            "entities survive an exclude",
            Manifest::Keep,
            false,
            &["drop/**", "META-INF/**"],
            &[
                "keep/Service.class",
                "META-INF/listOfEntities.txt",
                "keep/drop/Kept.class",
                "__index__",
            ],
        ),
    ];
    for (name, manifest, entities, excludes, want) in tests {
        let root = temp();
        let (mut recipe, catalogue) = sample_plan(root.path());
        archive_file(&root.path().join("module.jar"), &MODULE_EXCLUDES_FIXTURE);
        if let Operation::Jar {
            merge_entities, sources, ..
        } = &mut recipe.operations[0]
        {
            *merge_entities = entities;
            *sources = vec![Source::Archive {
                input: Reference::artifact("module"),
                filter: Filter::Module,
                excludes: strings(excludes),
                manifest,
            }];
        }
        let written = write_execution(&recipe, &catalogue);
        let (names, entries) = read_archive(&written.output.join("lib/plugin.jar"));
        assert_eq!(names, want, "{name}: the selected entries");
        assert_eq!(text(&entries["keep/drop/Kept.class"]), "kept: the glob is anchored", "{name}");
    }
}

/// Pins that `jarpack::module_output_name_filter` is `commonModuleExcludes`. A module-filter source composes both, so
/// the two statements of the common excludes must agree on every name.
#[test]
fn module_filter_agrees_with_common_module_excludes() {
    let common: Vec<javaglob::JavaGlob> = [
        "**/icon-robots.txt",
        "icon-robots.txt",
        ".unmodified",
        ".hash",
        "classpath.index",
        "module-info.class",
    ]
    .iter()
    .map(|pattern| javaglob::JavaGlob::compile(pattern).unwrap())
    .collect();
    for name in [
        "icon-robots.txt",
        "icons/icon-robots.txt",
        "a/b/icon-robots.txt",
        "xicon-robots.txt",
        "icon-robots.txt.bak",
        ".unmodified",
        "a/.unmodified",
        ".hash",
        "a/.hash",
        "classpath.index",
        "a/classpath.index",
        "module-info.class",
        "META-INF/versions/9/module-info.class",
        "META-INF/MANIFEST.MF",
        "META-INF/listOfEntities.txt",
        "com/example/Service.class",
        "standardDsls/a.gdsl",
        "js/index.js",
    ] {
        let excluded = common.iter().any(|matcher| matcher.matches(name));
        assert_eq!(jarpack::module_output_name_filter(name), !excluded, "{name:?}");
    }
}

/// The plan/write half of the Go `TestKotlinDefaultFieldEncoding`: a jar recipe without sources plans and writes one
/// empty jar. The decode half is in the `planfile` crate.
#[test]
fn default_field_recipe_plans_and_writes() {
    let recipe = Recipe {
        version: VERSION,
        plugin: "example".to_owned(),
        layout_signature: "signature".to_owned(),
        assets: vec![remainder("lib/plugin.jar")],
        operations: vec![jar("lib/plugin.jar", Vec::new())],
    };
    let written = write_execution(&recipe, &catalogue(Vec::new()));
    assert_eq!(written.inventory.len(), 1);
}
