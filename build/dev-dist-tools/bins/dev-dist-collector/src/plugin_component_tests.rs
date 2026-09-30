#![allow(clippy::cast_possible_truncation, reason = "a fixture writes small lengths into record fields")]

use std::path::Path;

#[cfg(unix)]
use filemeta::Entry;
use serde_json::{Value, json};

use crate::test_support::*;

/// The record of `plugins/plugin-classpath.txt` for the descriptor `<idea-plugin/>\n`. The names are ASCII, so
/// their Java modified UTF-8 is their bytes.
fn class_path_fixture(plugin: &str, names: &[&str]) -> Vec<u8> {
    fn append_name(data: &mut Vec<u8>, value: &str) {
        data.extend_from_slice(&(value.len() as u16).to_be_bytes());
        data.extend_from_slice(value.as_bytes());
    }
    let mut data = (names.len() as u16).to_be_bytes().to_vec();
    append_name(&mut data, plugin);
    let descriptor = b"<idea-plugin/>\n";
    data.extend_from_slice(&(descriptor.len() as u32).to_be_bytes());
    data.extend_from_slice(descriptor);
    for name in names {
        append_name(&mut data, name);
    }
    data
}

#[cfg(unix)]
fn asset(destination: &str, producer: &str) -> Value {
    json!({"destination": destination, "producer": producer})
}

#[cfg(unix)]
fn independent_asset(destination: &str, artifact: &str) -> Value {
    json!({"destination": destination, "producer": "independent", "artifact": artifact})
}

/// A remainder asset of the kind `kind` that joins no classpath.
#[cfg(unix)]
fn excluded_asset(destination: &str, kind: &str) -> Value {
    json!({"destination": destination, "producer": "remainder", "kind": kind, "classPath": false})
}

/// The entry of a symbolic link with the target `target`, as the packer inventories it.
#[cfg(unix)]
fn inspected_link(relative_path: &str, target: &str) -> Entry {
    let directory = tempfile::tempdir().unwrap();
    let link = directory.path().join("link");
    std::os::unix::fs::symlink(target, &link).unwrap();
    filemeta::inspect(&link, relative_path).unwrap()
}

/// The prepared shape of `dev_plugin_component`: a remainder with a jar, a program and a link, and one reused jar
/// that two assets place.
#[cfg(unix)]
struct Prepared {
    spec: Value,
    assets: Vec<Value>,
    remainder: Vec<Entry>,
}

#[cfg(unix)]
impl Prepared {
    fn new() -> Self {
        let fixture = Self {
            spec: json!({
                "version": 1,
                "pluginDirectory": "plugins/demo",
                "remainder": {"directory": "payload/remainder", "metadata": "metadata/remainder.json"},
                "assets": "metadata/assets.json",
                "classpath": "metadata/plugin-classpath.txt",
                "independent": [{
                    "artifact": "shared",
                    "source": "payload/shared.jar",
                    "metadata": "metadata/shared.json",
                    "relativePath": "shared.jar",
                }],
            }),
            assets: vec![
                independent_asset("lib/member.jar", "shared"),
                asset("lib/demo.jar", "remainder"),
                asset("bin/tool", "remainder"),
                asset("bin/current", "remainder"),
                independent_asset("lib/nested/shared.jar", "shared"),
            ],
            remainder: vec![
                file_entry("lib/demo.jar", 10, 12, 0o644),
                file_entry("bin/tool", 11, 13, 0o750),
                inspected_link("bin/current", "./tool"),
            ],
        };
        write_inventory("metadata/shared.json", &[file_entry("shared.jar", 12, 14, 0o600)]);
        write_file(
            "metadata/plugin-classpath.txt",
            class_path_fixture("demo", &["lib/demo.jar", "lib/member.jar"]),
        );
        fixture.write();
        fixture
    }

    /// The tree fixture: version 2 with the owned trees `kotlinc` and `lib/resources.jar`.
    fn with_trees() -> Self {
        let mut fixture = Self::new();
        fixture.spec["version"] = json!(2);
        fixture
            .assets
            .extend([excluded_asset("kotlinc", "tree"), excluded_asset("lib/resources.jar", "tree")]);
        fixture.remainder.extend([
            directory_entry("kotlinc", 0o750),
            directory_entry("kotlinc/lib", 0o755),
            file_entry("kotlinc/lib/compiler.jar", 23, 13, 0o751),
            directory_entry("kotlinc/empty", 0o710),
            directory_entry("kotlinc/empty/nested", 0o700),
            directory_entry("lib/resources.jar", 0o710),
            inspected_link("kotlinc/current", "./lib/compiler.jar"),
            inspected_link("kotlinc/empty-link", "empty/nested"),
        ]);
        fixture.write();
        fixture
    }

    fn write(&self) {
        write_json("component-spec.json", &self.spec);
        write_json(self.spec["assets"].as_str().unwrap(), &Value::Array(self.assets.clone()));
        write_raw_inventory(self.spec["remainder"]["metadata"].as_str().unwrap(), &self.remainder);
    }

    fn classpath(&self) -> String {
        self.spec["classpath"].as_str().unwrap().to_owned()
    }
}

fn plugin_component_args() -> Vec<String> {
    let mut args = base_args("--plugin-component=component-spec.json");
    args.push("--plugin-classpath-part=component.plugin-classpath-part".into());
    args
}

#[track_caller]
fn assert_no_outputs() {
    for file in ["component.json", "component.plugin-classpath-part"] {
        assert!(!exists(file), "a refused component wrote {file}");
    }
}

#[track_caller]
fn assert_class_path_part(expected: &[u8]) {
    assert_eq!(std::fs::read("component.plugin-classpath-part").unwrap(), expected);
}

#[cfg(unix)]
#[test]
fn prepared_component_expands_owned_trees_without_payloads() {
    let _directory = WorkDir::new();
    let fixture = Prepared::with_trees();
    run_collector(&plugin_component_args()).assert_success();
    let entries = manifest_entries("component.json");
    assert_eq!(entries.len(), fixture.remainder.len() + 2, "the tree inventory was not expanded");
    let link = &entries["plugins/demo/kotlinc/current"];
    assert_eq!(link["type"], "symlink");
    assert_eq!(link["symlinkTarget"], "lib/compiler.jar");
    assert_eq!(link["hash"], filemeta::hash_symlink_target("lib/compiler.jar"));
    assert!(
        !std::fs::read_to_string("component.json").unwrap().contains("symlinkSource"),
        "the tree links keep the payload provenance"
    );
    assert_class_path_part(&std::fs::read(fixture.classpath()).unwrap());
    assert!(!exists("payload"), "the collector read or wrote the payload");
}

#[cfg(unix)]
#[test]
fn prepared_component_expands_the_plugin_root_tree_after_the_claimed_assets() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture.spec["version"] = json!(2);
    fixture.assets.push(excluded_asset("", "tree"));
    fixture.remainder.extend([
        directory_entry("languageService", 0o755),
        file_entry("languageService/eslint.js", 23, 13, 0o644),
    ]);
    fixture.write();
    run_collector(&plugin_component_args()).assert_success();
    let entries = manifest_entries("component.json");
    assert_eq!(
        entries["plugins/demo/languageService/eslint.js"]["source"],
        "payload/remainder/languageService/eslint.js"
    );
}

#[cfg(unix)]
#[test]
fn prepared_component_assigns_nested_trees_by_specificity() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture.spec["version"] = json!(2);
    fixture
        .assets
        .extend([excluded_asset("resources", "tree"), excluded_asset("resources/nested", "tree")]);
    fixture.remainder.extend([
        directory_entry("resources", 0o755),
        file_entry("resources/outer.txt", 23, 13, 0o644),
        directory_entry("resources/nested", 0o755),
        file_entry("resources/nested/inner.txt", 24, 14, 0o644),
    ]);
    fixture.write();
    run_collector(&plugin_component_args()).assert_success();
    let entries = manifest_entries("component.json");
    for name in ["plugins/demo/resources/outer.txt", "plugins/demo/resources/nested/inner.txt"] {
        assert!(entries.contains_key(name), "the nested tree entry is missing: {name}");
    }
}

#[cfg(unix)]
#[test]
fn prepared_component_accepts_an_empty_owned_tree() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture.spec["version"] = json!(2);
    fixture.assets.push(excluded_asset("optional", "tree"));
    fixture.write();
    run_collector(&plugin_component_args()).assert_success();
}

#[cfg(unix)]
#[test]
fn prepared_component_refuses_an_independent_file_inside_a_tree_with_identical_metadata() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::with_trees();
    fixture.assets[0]["destination"] = json!("kotlinc/lib/compiler.jar");
    write_inventory("metadata/shared.json", &[file_entry("shared.jar", 23, 13, 0o751)]);
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("conflicting plugin destinations");
}

#[cfg(unix)]
#[test]
fn prepared_component_allows_remainder_files_below_a_tree() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::with_trees();
    fixture.assets.push(asset("kotlinc/generated.jar", "remainder"));
    fixture.remainder.push(file_entry("kotlinc/generated.jar", 25, 15, 0o644));
    fixture.write();
    run_collector(&plugin_component_args()).assert_success();
    assert!(manifest_entries("component.json").contains_key("plugins/demo/kotlinc/generated.jar"));
}

#[cfg(unix)]
#[test]
fn prepared_component_refuses_a_stale_tree_inventory() {
    type Change = fn(&mut Prepared);
    let scenarios: [(&str, Change); 13] = [
        ("version 1", |fixture| fixture.spec["version"] = json!(1)),
        ("classpath", |fixture| {
            fixture.assets[5].as_object_mut().unwrap().remove("classPath");
        }),
        ("independent tree without its native tree", |fixture| {
            fixture.assets[5]["producer"] = json!("independent");
            fixture.assets[5]["artifact"] = json!("shared");
        }),
        ("nested asset", |fixture| fixture.assets.push(asset("kotlinc/empty", "remainder"))),
        ("independent overlap", |fixture| {
            fixture.assets[0]["destination"] = json!("kotlinc/lib/compiler.jar");
        }),
        ("root alias", |fixture| fixture.assets[5]["destination"] = json!("KOTLINC")),
        ("missing root", |fixture| {
            fixture.remainder.retain(|entry| entry.relative_path != "kotlinc");
        }),
        ("missing directory", |fixture| {
            fixture.remainder.retain(|entry| entry.relative_path != "kotlinc/lib");
        }),
        ("missing target", |fixture| {
            fixture.remainder.retain(|entry| entry.relative_path != "kotlinc/lib/compiler.jar");
        }),
        ("unclaimed prefix", |fixture| {
            fixture.remainder.push(file_entry("kotlinc-other/file", 0, 0, 0o644));
        }),
        ("independent inventory", |fixture| {
            fixture.remainder.push(file_entry("lib/member.jar", 0, 0, 0o644));
        }),
        ("duplicate inventory", |fixture| {
            let duplicate = fixture.remainder[3].clone();
            fixture.remainder.push(duplicate);
        }),
        ("the retired distribution scope", |fixture| {
            fixture.assets.push(json!({
                "destination": "lib/jna", "producer": "independent", "artifact": "shared", "kind": "tree",
                "classPath": false, "scope": "distribution",
            }));
        }),
    ];
    for (name, change) in scenarios {
        let _directory = WorkDir::new();
        let mut fixture = Prepared::with_trees();
        change(&mut fixture);
        fixture.write();
        let outcome = run_collector(&plugin_component_args());
        assert_ne!(outcome.code, 0, "{name}: accepted a stale tree ownership");
        assert_no_outputs();
    }
}

/// The packer writes only file and tree assets, and every asset is below the plugin directory. So the collector refuses
/// a directory asset and an asset row that states the retired scope.
#[cfg(unix)]
#[test]
fn prepared_component_refuses_the_asset_shapes_that_no_plan_file_has() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture.remainder.push(directory_entry("empty", 0o710));
    fixture
        .assets
        .push(json!({"destination": "empty", "producer": "remainder", "kind": "directory"}));
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("unknown asset kind");
    assert_no_outputs();

    let mut fixture = Prepared::new();
    fixture.assets.push(json!({
        "destination": "lib/native/tool", "producer": "remainder", "classPath": false, "scope": "distribution",
    }));
    fixture.remainder.push(file_entry("lib/native/tool", 31, 17, 0o755));
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("unknown field `scope`");
    assert_no_outputs();

    let mut fixture = Prepared::new();
    fixture.assets[0]["normalizeTreeModes"] = json!(true);
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("unknown field");
    assert_no_outputs();
}

#[cfg(unix)]
#[test]
fn prepared_component_class_path_uses_the_original_participation() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture
        .remainder
        .extend([file_entry("lib/foo.jar", 21, 0, 0o644), file_entry("lib/custom.jar", 22, 0, 0o644)]);
    fixture.assets.extend([
        json!({"destination": "lib/foo.jar", "producer": "remainder", "classPath": false}),
        asset("lib/custom.jar", "remainder"),
    ]);
    fixture.write();
    let valid = class_path_fixture("demo", &["lib/demo.jar", "lib/custom.jar", "lib/member.jar"]);
    let invalid: [Vec<u8>; 8] = [
        class_path_fixture("demo", &["lib/demo.jar", "lib/member.jar", "lib/foo.jar"]),
        class_path_fixture("demo", &["lib/demo.jar", "lib/member.jar", "lib/nested/shared.jar"]),
        class_path_fixture("demo", &["lib/demo.jar", "lib/member.jar", "lib/member.jar"]),
        class_path_fixture("demo", &["lib/demo.jar", "lib/member.jar"]),
        class_path_fixture("other", &["lib/demo.jar", "lib/custom.jar", "lib/member.jar"]),
        [valid.as_slice(), &[0]].concat(),
        valid[..valid.len() - 1].to_vec(),
        vec![0, 3, 0, 4, b'd', b'e', b'm', b'o', 0xff, 0xff, 0xff, 0xff],
    ];
    for record in invalid {
        write_file(fixture.classpath(), &record);
        run_collector(&plugin_component_args()).assert_error("classpath");
        assert_no_outputs();
    }
    write_file(fixture.classpath(), &valid);
    run_collector(&plugin_component_args()).assert_success();
    assert_class_path_part(&valid);
}

/// The file metadata supports ASCII names only, so a plugin directory with another character is refused. The Go
/// collector accepted it.
#[cfg(unix)]
#[test]
fn prepared_component_refuses_a_name_that_is_not_ascii() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture.spec["pluginDirectory"] = json!("plugins/d\u{e9}mo");
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("pluginDirectory must name plugins/<directory>");
    let mut fixture = Prepared::new();
    fixture.assets[0]["destination"] = json!("lib/\u{1f600}.jar");
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("unsupported character");
    assert_no_outputs();
}

#[cfg(unix)]
#[test]
fn prepared_component_uses_only_metadata() {
    let _directory = WorkDir::new();
    let fixture = Prepared::new();
    std::fs::create_dir_all("payload").unwrap();
    for source in ["payload/remainder", "payload/shared.jar"] {
        symlink(source.rsplit('/').next().unwrap(), source);
    }
    let mut args = plugin_component_args();
    args.push("--trace-file=component.spans.json".into());
    run_collector(&args).assert_success();
    let manifest = read_json("component.json");
    assert_eq!((&manifest["version"], &manifest["pluginCount"]), (&json!(9), &json!(1)));
    let entries = manifest_entries("component.json");
    assert_eq!(entries.len(), fixture.assets.len());
    for (destination, source, mode, hash) in [
        ("lib/member.jar", "payload/shared.jar", 0o600, 12),
        ("lib/demo.jar", "payload/remainder/lib/demo.jar", 0o644, 10),
        ("bin/tool", "payload/remainder/bin/tool", 0o750, 11),
        ("lib/nested/shared.jar", "payload/shared.jar", 0o600, 12),
    ] {
        let entry = &entries[&format!("plugins/demo/{destination}")];
        assert_eq!(entry["type"], "component-file", "{entry}");
        assert_eq!(entry["source"], source, "{entry}");
        assert_eq!(entry["hash"], hash, "{entry}");
        let expected_mode = if mode == 0o644 { Value::Null } else { json!(mode) };
        assert_eq!(entry.get("mode").cloned().unwrap_or(Value::Null), expected_mode, "{entry}");
        assert_eq!(entry.get("executable").is_some(), mode & 0o111 != 0, "{entry}");
    }
    let link = &entries["plugins/demo/bin/current"];
    assert_eq!(link["type"], "symlink");
    assert_eq!(link["symlinkTarget"], "tool");
    assert_eq!(link["hash"], filemeta::hash_symlink_target("tool"));
    for field in ["source", "mode", "executable"] {
        assert!(link.get(field).is_none(), "{link}");
    }
    assert_class_path_part(&std::fs::read(fixture.classpath()).unwrap());
    let spans = read_spans("component.spans.json");
    assert_eq!(
        spans.iter().map(span_name).collect::<Vec<_>>(),
        [
            "collect plugin component metadata",
            "merge plugin component metadata",
            "inventory dev build component",
        ]
    );
    for span in &spans {
        assert_eq!(span_tag(span, "byteCount"), Some("0"), "payload reads were reported: {span}");
    }
}

/// A reused natives jar of version 2 places its tree for the platform of the component below the plugin directory.
#[cfg(unix)]
#[test]
fn prepared_component_places_the_native_tree_of_a_reused_jar() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture.spec["version"] = json!(2);
    fixture.spec["independent"][0]["nativeTree"] = json!({"source": "payload/jna/native", "metadata": "metadata/jna-native.json"});
    fixture.assets.push(json!({
        "destination": "lib/jna", "producer": "independent", "artifact": "shared", "kind": "tree", "classPath": false,
    }));
    write_inventory(
        "metadata/jna-native.json",
        &[
            directory_entry("native", 0o755),
            directory_entry("native/aarch64", 0o755),
            file_entry("native/aarch64/libjnidispatch.jnilib", 41, 20, 0o644),
        ],
    );
    fixture.write();
    run_collector(&plugin_component_args()).assert_success();
    let entries = manifest_entries("component.json");
    let native = &entries["plugins/demo/lib/jna/aarch64/libjnidispatch.jnilib"];
    assert_eq!(native["source"], "payload/jna/native/aarch64/libjnidispatch.jnilib");
    assert_eq!(native["hash"], 41);
    assert_eq!(entries["plugins/demo/lib/jna"]["type"], "directory");
    assert_eq!(entries["plugins/demo/lib/jna/aarch64"]["type"], "directory");
    assert!(!entries.contains_key("lib/jna"), "the native tree is at the distribution root");

    // The retired version 3 is unsupported, and version 1 has no tree.
    let mut retired = Prepared {
        spec: fixture.spec.clone(),
        assets: fixture.assets.clone(),
        remainder: fixture.remainder.clone(),
    };
    retired.spec["version"] = json!(3);
    retired.write();
    run_collector(&plugin_component_args()).assert_error("unsupported plugin component version: 3");
    retired.spec["version"] = json!(1);
    retired.write();
    run_collector(&plugin_component_args()).assert_error(r#"tree "lib/jna" requires version 2,"#);

    // A tree that no asset places is stale, and so is a tree that is not named `native`.
    let mut unused = fixture.spec["independent"][0].clone();
    unused["artifact"] = json!("other");
    unused["source"] = json!("payload/other.jar");
    unused["metadata"] = json!("metadata/other.json");
    unused["relativePath"] = json!("other.jar");
    unused["nativeTree"] = json!({"source": "payload/other/native", "metadata": "metadata/jna-native.json"});
    write_inventory("metadata/other.json", &[file_entry("other.jar", 51, 10, 0o644)]);
    let mut stale = Prepared {
        spec: fixture.spec.clone(),
        assets: fixture.assets.clone(),
        remainder: fixture.remainder.clone(),
    };
    stale.spec["independent"].as_array_mut().unwrap().push(unused);
    stale.assets.push(independent_asset("lib/other.jar", "other"));
    stale.write();
    run_collector(&plugin_component_args()).assert_error("1 unused native trees");
    fixture.spec["independent"][0]["nativeTree"]["source"] = json!("payload/jna/other");
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("is not named native");
}

#[cfg(unix)]
#[test]
fn prepared_component_refuses_a_stale_ownership() {
    type Change = fn(&mut Prepared);
    let scenarios: [(&str, Change, &str); 19] = [
        ("version", |fixture| fixture.spec["version"] = json!(3), "version"),
        (
            "plugin escape",
            |fixture| fixture.spec["pluginDirectory"] = json!("plugins/../outside"),
            "pluginDirectory",
        ),
        (
            "unknown producer",
            |fixture| fixture.assets[0]["producer"] = json!("kotlin"),
            "unknown asset producer",
        ),
        (
            "unsupported version",
            |fixture| fixture.spec["version"] = json!(4),
            "unsupported plugin component version: 4",
        ),
        (
            "missing independent",
            |fixture| fixture.assets[0]["artifact"] = json!("missing"),
            "missing independent artifact",
        ),
        (
            "unused independent",
            |fixture| fixture.assets = fixture.assets[1..4].to_vec(),
            "unused independent",
        ),
        (
            "unclaimed remainder",
            |fixture| {
                fixture.assets.remove(1);
            },
            "unclaimed remainder",
        ),
        (
            "missing remainder",
            |fixture| fixture.assets[1]["destination"] = json!("lib/missing.jar"),
            "stale remainder ownership",
        ),
        (
            "unsafe reuse",
            |fixture| fixture.assets[1]["artifact"] = json!("shared"),
            "stale remainder ownership",
        ),
        (
            "duplicate destination",
            |fixture| {
                let duplicate = fixture.assets[0].clone();
                fixture.assets.push(duplicate);
            },
            "destination collision",
        ),
        (
            "case collision",
            |fixture| fixture.assets[4]["destination"] = json!("lib/MEMBER.jar"),
            "destination collision",
        ),
        (
            "text that is not ASCII",
            |fixture| fixture.assets[4]["destination"] = json!("lib/\u{3c2}.jar"),
            "unsupported character",
        ),
        (
            "directory spelling",
            |fixture| fixture.assets[4]["destination"] = json!("LIB/another.jar"),
            "conflicting directory spellings",
        ),
        (
            "parent collision",
            |fixture| fixture.assets[4]["destination"] = json!("lib/demo.jar/child"),
            "conflicting destinations",
        ),
        (
            "escaping destination",
            |fixture| fixture.assets[0]["destination"] = json!("../outside"),
            "relative path",
        ),
        (
            "metadata in payload",
            |fixture| fixture.spec["classpath"] = json!("payload/remainder/classpath"),
            "overlaps payload",
        ),
        (
            "escaping source root",
            |fixture| fixture.spec["remainder"]["directory"] = json!("../outside"),
            "invalid declared artifact path",
        ),
        (
            "source root backslash",
            |fixture| fixture.spec["remainder"]["directory"] = json!(r"payload\remainder"),
            "invalid declared artifact path",
        ),
        (
            "independent in remainder",
            |fixture| fixture.spec["independent"][0]["source"] = json!("payload/remainder/shared.jar"),
            "overlaps the remainder",
        ),
    ];
    for (name, change, message) in scenarios {
        let _directory = WorkDir::new();
        let mut fixture = Prepared::new();
        change(&mut fixture);
        fixture.write();
        let outcome = run_collector(&plugin_component_args());
        assert!(
            outcome.code != 0 && outcome.errors.contains(message),
            "{name}: exit {}, error {:?}, expected {message:?}",
            outcome.code,
            outcome.errors
        );
        assert_no_outputs();
    }
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    let duplicate = fixture.spec["independent"][0].clone();
    fixture.spec["independent"].as_array_mut().unwrap().push(duplicate);
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("duplicate independent artifact");
}

#[cfg(unix)]
#[test]
fn prepared_component_refuses_conflicting_metadata() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    write_inventory("metadata/conflict.json", &[file_entry("shared.jar", 99, 14, 0o600)]);
    let mut conflict = fixture.spec["independent"][0].clone();
    conflict["artifact"] = json!("conflict");
    conflict["metadata"] = json!("metadata/conflict.json");
    fixture.spec["independent"].as_array_mut().unwrap().push(conflict);
    fixture.assets[4]["artifact"] = json!("conflict");
    fixture.write();
    run_collector(&plugin_component_args()).assert_error("conflicting metadata");
}

#[cfg(unix)]
#[test]
fn prepared_component_protects_the_inputs_and_the_payload_from_the_outputs() {
    for (name, value) in [
        ("--trace-file", "metadata/shared.json"),
        ("--trace-file", "payload/remainder/trace.json"),
        ("--trace-file", "payload/shared.jar"),
        ("--trace-file", "payload"),
        ("--trace-file", "component.plugin-classpath-part"),
        ("--plugin-classpath-part", "metadata/plugin-classpath.txt"),
        ("--plugin-classpath-part", "metadata/./plugin-classpath.txt"),
        ("--component-manifest", "metadata/remainder.json"),
    ] {
        let _directory = WorkDir::new();
        let fixture = Prepared::new();
        let inputs = [
            fixture.spec["remainder"]["metadata"].as_str().unwrap().to_owned(),
            "metadata/shared.json".to_owned(),
            fixture.classpath(),
        ];
        let before: Vec<Vec<u8>> = inputs.iter().map(|input| std::fs::read(input).unwrap()).collect();
        let mut args = plugin_component_args();
        let option = format!("{name}={value}");
        match args.iter_mut().find(|arg| arg.starts_with(&format!("{name}="))) {
            Some(arg) => *arg = option,
            None => args.push(option),
        }
        let outcome = run_collector(&args);
        assert_ne!(outcome.code, 0, "{name}={value}: accepted an output that overlaps an input");
        for (input, expected) in inputs.iter().zip(&before) {
            assert_eq!(
                &std::fs::read(input).unwrap(),
                expected,
                "{name}={value}: the input {input} changed"
            );
        }
    }
}

/// The packed shape of `_dev_plugin`: three jars, one of them below `lib/modules/`, and two copied files.
fn packed_spec(with_files: bool) -> Value {
    let mut spec = json!({
        "version": 1,
        "pluginDirectory": "plugins/json",
        "descriptor": "metadata/plugin.xml",
        "jars": [
            {"destination": "lib/json-rpc-1.0.jar", "source": "payload/json-rpc-1.0.jar", "metadata": "metadata/rpc.json"},
            {"destination": "lib/json.jar", "source": "payload/intellij.json.jar", "metadata": "metadata/main.json"},
            {"destination": "lib/modules/intellij.json.split.jar", "source": "payload/intellij.json.split.jar", "metadata": "metadata/split.json"},
        ],
    });
    if with_files {
        spec["files"] = json!([
            {"destination": "bin/helper.sh", "source": "resources/helper.sh", "executable": true},
            {"destination": "openui/renderer.zip", "source": "resources/renderer.zip", "executable": false},
        ]);
    }
    spec
}

/// The copied files of the packed fixture share this content, so the inventory hashes it once per source.
const COPIED_FILE_TEXT: &str = "#!/bin/sh\n";

fn write_packed_fixture(spec: &Value) {
    for (metadata, entry) in [
        ("metadata/rpc.json", file_entry("json-rpc-1.0.jar", 30, 15, 0o644)),
        ("metadata/main.json", file_entry("intellij.json.jar", 10, 12, 0o600)),
        ("metadata/split.json", file_entry("intellij.json.split.jar", 20, 13, 0o644)),
    ] {
        write_inventory(metadata, &[entry]);
    }
    write_file("metadata/plugin.xml", "<idea-plugin/>\n");
    write_file("resources/helper.sh", COPIED_FILE_TEXT);
    write_file("resources/renderer.zip", COPIED_FILE_TEXT);
    write_json("component-spec.json", spec);
}

#[test]
fn packed_component_writes_the_manifest_and_the_classpath() {
    let _directory = WorkDir::new();
    write_packed_fixture(&packed_spec(true));
    let mut args = plugin_component_args();
    args.push("--trace-file=component.spans.json".into());
    let outcome = run_collector(&args);
    outcome.assert_success();
    let manifest = read_json("component.json");
    assert_eq!(
        [&manifest["version"], &manifest["pluginCount"], &manifest["os"], &manifest["arch"]],
        [&json!(9), &json!(1), &json!("linux"), &json!("x64")]
    );
    let copied_hash = xxh3::hash_file(Path::new("resources/helper.sh")).unwrap();
    let entries = manifest_entries("component.json");
    assert_eq!(
        entries,
        [
            json!({"relativePath": "plugins/json/bin/helper.sh", "type": "component-file", "hash": copied_hash, "executable": true, "source": "resources/helper.sh"}),
            json!({"relativePath": "plugins/json/lib/json-rpc-1.0.jar", "type": "component-file", "hash": 30, "source": "payload/json-rpc-1.0.jar"}),
            json!({"relativePath": "plugins/json/lib/json.jar", "type": "component-file", "hash": 10, "source": "payload/intellij.json.jar", "mode": 0o600}),
            json!({"relativePath": "plugins/json/lib/modules/intellij.json.split.jar", "type": "component-file", "hash": 20, "source": "payload/intellij.json.split.jar"}),
            json!({"relativePath": "plugins/json/openui/renderer.zip", "type": "component-file", "hash": copied_hash, "source": "resources/renderer.zip"}),
        ]
        .into_iter()
        .map(|entry| (entry["relativePath"].as_str().unwrap().to_owned(), entry))
        .collect()
    );
    // The main jar goes first by the plugin name and the versioned library last. The `lib/modules` jar and the
    // copied files are absent.
    assert_class_path_part(&class_path_fixture("json", &["lib/json.jar", "lib/json-rpc-1.0.jar"]));
    assert!(!exists("payload"), "the collector read or wrote the payload");
    // The jar payloads stay unread. The inventory hashes the two copied files.
    let copied_bytes = (2 * COPIED_FILE_TEXT.len()).to_string();
    for span in read_spans("component.spans.json") {
        let expected = if span_name(&span) == "inventory dev build component" {
            copied_bytes.as_str()
        } else {
            "0"
        };
        assert_eq!(span_tag(&span, "byteCount"), Some(expected), "{span}");
    }
    assert!(outcome.output.contains("named 5 plugin files"), "{}", outcome.output);
}

#[test]
fn packed_component_writes_jars_only() {
    let _directory = WorkDir::new();
    write_packed_fixture(&packed_spec(false));
    let mut args = plugin_component_args();
    args.push("--trace-file=component.spans.json".into());
    let outcome = run_collector(&args);
    outcome.assert_success();
    assert_eq!(manifest_entries("component.json").len(), 3, "the manifest lists more than the jars");
    for span in read_spans("component.spans.json") {
        assert_eq!(span_tag(&span, "byteCount"), Some("0"), "payload reads were reported: {span}");
    }
    assert!(outcome.output.contains("named 3 plugin files"), "{}", outcome.output);
}

#[test]
fn packed_component_accepts_the_declared_spec_shape() {
    let _directory = WorkDir::new();
    write_packed_fixture(&packed_spec(true));
    write_file(
        "component-spec.json",
        r#"{"version": 1, "pluginDirectory": "plugins/json", "descriptor": "metadata/plugin.xml",
 "jars": [{"destination": "lib/json.jar", "source": "payload/intellij.json.jar", "metadata": "metadata/main.json"}]}"#,
    );
    run_collector(&plugin_component_args()).assert_success();
    assert_class_path_part(&class_path_fixture("json", &["lib/json.jar"]));
}

#[test]
fn packed_component_keeps_the_descriptor_bytes() {
    let _directory = WorkDir::new();
    write_packed_fixture(&packed_spec(true));
    let descriptor = "<idea-plugin>\n  <id>json</id>\n  <description><![CDATA[<b>x</b>]]></description>\n</idea-plugin>";
    write_file("metadata/plugin.xml", descriptor);
    run_collector(&plugin_component_args()).assert_success();
    let mut prefix = vec![0, 2, 0, 4];
    prefix.extend_from_slice(b"json");
    prefix.extend_from_slice(&(descriptor.len() as u32).to_be_bytes());
    prefix.extend_from_slice(descriptor.as_bytes());
    let actual = std::fs::read("component.plugin-classpath-part").unwrap();
    assert!(actual.starts_with(&prefix), "classpath {actual:x?}");
}

#[test]
fn packed_component_is_platform_neutral() {
    let _directory = WorkDir::new();
    write_packed_fixture(&packed_spec(true));
    run_collector(&[
        "--component-manifest=component.json",
        "--kind=plugins_json",
        "--platform-prefix=idea",
        "--platform-neutral",
        "--plugin-component=component-spec.json",
        "--plugin-classpath-part=component.plugin-classpath-part",
    ])
    .assert_success();
    let manifest = read_json("component.json");
    assert_eq!(
        [&manifest["os"], &manifest["arch"], &manifest["kind"]],
        [&json!(""), &json!(""), &json!("plugins_json")]
    );
}

#[test]
fn packed_component_refuses_stale_inputs() {
    type Change = fn(&mut Value);
    let scenarios: [(&str, Change, &str); 24] = [
        (
            "version",
            |spec| spec["version"] = json!(2),
            "unsupported packed plugin component version",
        ),
        (
            "mixed shape",
            |spec| spec["classpath"] = json!("metadata/plugin-classpath.txt"),
            "names only its descriptor, jars and files",
        ),
        (
            "mixed remainder",
            |spec| spec["remainder"] = json!({"directory": "payload/remainder", "metadata": "metadata/remainder.json"}),
            "names only its descriptor, jars and files",
        ),
        ("no jars", |spec| spec["jars"] = json!([]), "at least one jar"),
        (
            "files without jars",
            |spec| {
                spec.as_object_mut().unwrap().remove("jars");
            },
            "names no copied files",
        ),
        (
            "plugin escape",
            |spec| spec["pluginDirectory"] = json!("plugins/../outside"),
            "pluginDirectory",
        ),
        (
            "escaping destination",
            |spec| spec["jars"][0]["destination"] = json!("../outside.jar"),
            "invalid relative path",
        ),
        (
            "case collision",
            |spec| spec["jars"][0]["destination"] = json!("lib/JSON.jar"),
            "conflicting plugin destinations",
        ),
        (
            "metadata for another jar",
            |spec| spec["jars"][0]["metadata"] = spec["jars"][1]["metadata"].clone(),
            "exactly one regular file",
        ),
        (
            "missing metadata",
            |spec| spec["jars"][0]["metadata"] = json!("metadata/missing.json"),
            "missing.json",
        ),
        (
            "missing descriptor",
            |spec| spec["descriptor"] = json!("metadata/missing.xml"),
            "missing.xml",
        ),
        (
            "descriptor in payload",
            |spec| spec["descriptor"] = json!("payload/intellij.json.jar"),
            "overlaps payload",
        ),
        (
            "empty descriptor",
            |spec| spec["descriptor"] = json!(""),
            "invalid declared artifact path",
        ),
        (
            "absent descriptor",
            |spec| {
                spec.as_object_mut().unwrap().remove("descriptor");
            },
            "invalid declared artifact path",
        ),
        (
            "empty source",
            |spec| spec["jars"][0]["source"] = json!(""),
            "invalid declared artifact path",
        ),
        (
            "file at a jar destination",
            |spec| spec["files"][0]["destination"] = json!("lib/json.jar"),
            "conflicting plugin destinations: lib/json.jar and lib/json.jar",
        ),
        (
            "file case collision",
            |spec| spec["files"][1]["destination"] = json!("bin/Helper.sh"),
            "conflicting plugin destinations",
        ),
        (
            "file below a jar",
            |spec| spec["files"][0]["destination"] = json!("lib/json.jar/helper.sh"),
            "lib/json.jar contains lib/json.jar/helper.sh",
        ),
        (
            "jar below a file",
            |spec| spec["files"][0]["destination"] = json!("lib"),
            "lib contains lib/json-rpc-1.0.jar",
        ),
        (
            "file directory spelled twice",
            |spec| spec["files"][1]["destination"] = json!("Bin/renderer.zip"),
            "conflicting plugin destinations: bin and Bin",
        ),
        (
            "escaping file destination",
            |spec| spec["files"][0]["destination"] = json!("../helper.sh"),
            "invalid relative path",
        ),
        (
            "empty file source",
            |spec| spec["files"][0]["source"] = json!(""),
            "invalid declared artifact path",
        ),
        (
            "missing file source",
            |spec| spec["files"][0]["source"] = json!("resources/missing.sh"),
            "is not a regular file: resources/missing.sh",
        ),
        (
            "directory file source",
            |spec| spec["files"][0]["source"] = json!("resources"),
            "is not a regular file: resources",
        ),
    ];
    for (name, change, message) in scenarios {
        let _directory = WorkDir::new();
        let mut spec = packed_spec(true);
        write_packed_fixture(&spec);
        change(&mut spec);
        write_json("component-spec.json", &spec);
        let outcome = run_collector(&plugin_component_args());
        assert!(
            outcome.code != 0 && outcome.errors.contains(message),
            "{name}: exit {}, error {:?}, expected {message:?}",
            outcome.code,
            outcome.errors
        );
        assert_no_outputs();
    }
    let _directory = WorkDir::new();
    let mut spec = packed_spec(true);
    write_packed_fixture(&spec);
    spec["files"][0]["source"] = spec["descriptor"].clone();
    write_json("component-spec.json", &spec);
    run_collector(&plugin_component_args()).assert_error("overlaps payload");
}

/// `refusedModules` names the content modules that the product mode of the component refuses. The packer omitted every
/// asset of those modules, so the spec's reused jar of such a module places nothing and its rows are absent.
#[cfg(unix)]
#[test]
fn prepared_component_drops_the_reused_jars_of_refused_modules() {
    let _directory = WorkDir::new();
    let mut fixture = Prepared::new();
    fixture.spec["refusedModules"] = json!(["shared"]);
    fixture.assets.retain(|asset| asset["producer"] != "independent");
    write_file("metadata/plugin-classpath.txt", class_path_fixture("demo", &["lib/demo.jar"]));
    fixture.write();
    run_collector(&plugin_component_args()).assert_success();
    let entries = manifest_entries("component.json");
    assert_eq!(entries.len(), 3);
    assert!(
        !entries.contains_key("plugins/demo/lib/member.jar"),
        "the reused jar of a refused module was placed"
    );
    assert_class_path_part(&class_path_fixture("demo", &["lib/demo.jar"]));

    for file in ["component.json", "component.plugin-classpath-part"] {
        std::fs::remove_file(file).unwrap();
    }

    // A refused module whose rows the packer still states is a stale ownership, as an unused reused jar is.
    let mut stale = Prepared::new();
    stale.spec["refusedModules"] = json!(["shared"]);
    stale.write();
    run_collector(&plugin_component_args()).assert_error("missing independent artifact shared");
    for (modules, message) in [
        (json!(["shared", "shared"]), r#"refused module "shared" is named twice"#),
        (json!([" "]), "a refused module requires a name"),
    ] {
        let mut fixture = Prepared::new();
        fixture.spec["refusedModules"] = modules;
        fixture.write();
        run_collector(&plugin_component_args()).assert_error(message);
    }
    assert_no_outputs();
}

/// A prepared spec with a key of the packed shape, or the other way round, matches no rule and is refused.
#[test]
fn a_spec_takes_only_the_keys_of_its_shape() {
    let _directory = WorkDir::new();
    write_json(
        "component-spec.json",
        &json!({"version": 1, "pluginDirectory": "plugins/demo", "descriptor": "metadata/plugin.xml",
                "remainder": {"directory": "payload/remainder", "metadata": "metadata/remainder.json"},
                "assets": "metadata/assets.json", "classpath": "metadata/plugin-classpath.txt", "independent": []}),
    );
    run_collector(&plugin_component_args()).assert_error("names no copied files and no descriptor");
    let mut spec = packed_spec(false);
    spec["independent"] = json!([]);
    write_json("component-spec.json", &spec);
    run_collector(&plugin_component_args()).assert_error("names only its descriptor, jars and files");
    let mut spec = packed_spec(false);
    spec["refusedModules"] = json!([]);
    write_json("component-spec.json", &spec);
    run_collector(&plugin_component_args()).assert_error("names only its descriptor, jars and files");
    write_json(
        "component-spec.json",
        &json!({"version": 1, "pluginDirectory": "plugins/demo", "extra": 1}),
    );
    run_collector(&plugin_component_args()).assert_error("unknown field");
}
