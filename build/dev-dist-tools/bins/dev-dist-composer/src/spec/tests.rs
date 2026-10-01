use testkit::{TempDir, require_error, write_file};
#[cfg(unix)]
use testkit::{file_symlink, read_text};

use super::*;

fn write_spec(directory: &TempDir, content: &str) -> PathBuf {
    let file = directory.path().join("composition.json");
    write_file(&file, content);
    file
}

/// The shape of `json.encode` in `intellij_dev_dist.bzl`: every key, with `null` for an absent file or map.
const STARLARK_SPEC: &str = concat!(
    r#"{"version":1,"expectedFragments":["platform_core","intellij.java.plugin"],"additionalModules":["intellij.air.plugin"],"#,
    r#""components":[{"manifest":"core.json","pluginClasspathPart":null},{"manifest":"plugins.json","pluginClasspathPart":"plugins.part"}],"#,
    r#""pluginClasspathPrefix":"prefix.bin","sourceRunfiles":null,"sourceDirectoryRunfiles":{},"sourceBindings":null}"#
);

#[test]
fn composition_spec_decodes_the_starlark_shape() {
    let directory = TempDir::new();
    let spec = read_composition_spec(&write_spec(&directory, STARLARK_SPEC)).unwrap();
    assert_eq!(spec.expected_fragments, ["platform_core", "intellij.java.plugin"]);
    assert_eq!(spec.additional_modules, ["intellij.air.plugin"]);
    assert_eq!(spec.plugin_classpath_prefix.as_deref(), Some("prefix.bin"));
    assert_eq!(spec.source_runfiles, None);
    assert!(spec.source_directory_runfiles.is_empty());
    assert_eq!(spec.source_bindings, None);
    assert_eq!(
        spec.components,
        [
            CompositionComponent {
                manifest: "core.json".into(),
                plugin_classpath_part: None
            },
            CompositionComponent {
                manifest: "plugins.json".into(),
                plugin_classpath_part: Some("plugins.part".into())
            },
        ]
    );
    let local = STARLARK_SPEC.replace(r#""sourceRunfiles":null"#, r#""sourceRunfiles":{"b":"_main/b","a":"_main/a"}"#);
    let spec = read_composition_spec(&write_spec(&directory, &local)).unwrap();
    let runfiles = spec.source_runfiles.unwrap();
    assert_eq!(runfiles.get("a").map(String::as_str), Some("_main/a"));
    assert_eq!(runfiles.get("b").map(String::as_str), Some("_main/b"));
}

#[test]
fn composition_spec_rejects_invalid_content() {
    let cases = [
        (
            r#""version":1"#,
            r#""version":2"#,
            "Unsupported dev-build composition spec version 2 in ",
        ),
        (r#""version":1"#, r#""version":"1""#, "invalid type: string \"1\", expected i32"),
        (r#""version":1"#, r#""version":1,"version":1"#, "duplicate field `version`"),
        (r#""version":1,"#, "", "missing field `version`"),
        (
            r#""additionalModules":["intellij.air.plugin"],"#,
            "",
            "missing field `additionalModules`",
        ),
        (r#","sourceDirectoryRunfiles":{}"#, "", "missing field `sourceDirectoryRunfiles`"),
        (
            r#""sourceBindings":null"#,
            r#""sourceBindings":null,"extra":1"#,
            "unknown field `extra`",
        ),
        (r#""components""#, r#""Components""#, "unknown field `Components`"),
        (r#"{"manifest":"core.json","#, "{", "missing field `manifest`"),
        (
            r#"{"manifest":"core.json","#,
            r#"{"root":"core","manifest":"core.json","#,
            "unknown field `root`",
        ),
        (
            r#""expectedFragments":["platform_core","intellij.java.plugin"]"#,
            r#""expectedFragments":null"#,
            "invalid type: null",
        ),
        (
            r#""additionalModules":["intellij.air.plugin"]"#,
            r#""additionalModules":null"#,
            "invalid type: null",
        ),
        (
            r#""sourceDirectoryRunfiles":{}"#,
            r#""sourceDirectoryRunfiles":null"#,
            "invalid type: null",
        ),
        (
            r#""expectedFragments":["platform_core","#,
            r#""expectedFragments":[1,"#,
            "invalid type: integer `1`, expected a string",
        ),
        (r#""sourceRunfiles":null"#, r#""sourceRunfiles":{"a":1}"#, "expected a string"),
        (r#""sourceBindings":null}"#, r#""sourceBindings":null} {}"#, "trailing characters"),
    ];
    for (from, to, message) in cases {
        assert!(STARLARK_SPEC.contains(from), "the spec has no {from}");
        let directory = TempDir::new();
        require_error(
            read_composition_spec(&write_spec(&directory, &STARLARK_SPEC.replacen(from, to, 1))),
            message,
        );
    }
    let empty = STARLARK_SPEC.replace(
        r#"[{"manifest":"core.json","pluginClasspathPart":null},{"manifest":"plugins.json","pluginClasspathPart":"plugins.part"}]"#,
        "[]",
    );
    let directory = TempDir::new();
    require_error(read_composition_spec(&write_spec(&directory, &empty)), "has no components");
}

#[cfg(unix)]
struct StagedTree {
    physical: PathBuf,
    staged: PathBuf,
    file: PathBuf,
}

#[cfg(unix)]
#[derive(Debug)]
struct BoundTree {
    physical: PathBuf,
    staged: PathBuf,
    bindings: ComponentSources,
    file: PathBuf,
}

#[cfg(unix)]
impl BoundTree {
    fn staged(&self, member: &str) -> String {
        text(&self.staged.join(member))
    }
}

#[cfg(unix)]
fn text(path: &Path) -> String {
    path.to_str().unwrap().to_owned()
}

/// Stages one tree as Bazel does in a sandbox: the staged members link to the physical outputs, and the bindings file
/// describes the tree. A test can change the tree before [`bind`] reads the bindings.
#[cfg(unix)]
fn stage_tree(directory: &Path, members: &[&str]) -> StagedTree {
    let physical = directory.join("physical/trees/plugin");
    let staged = directory.join("sandbox/trees/plugin");
    for tree in [&physical, &staged] {
        fs::create_dir_all(tree.join("lib")).unwrap();
    }
    write_file(physical.join("lib/native.jar"), "native bytes");
    file_symlink(physical.join("lib/native.jar"), staged.join("lib/native.jar"));
    let physical_metadata = directory.join("physical/metadata/bindings.jsonl");
    let staged_metadata = directory.join("sandbox/metadata/bindings.jsonl");
    let line = serde_json::json!({
        "component": "plugin",
        "source": text(&staged),
        "anchorRelativePath": "../trees/plugin",
        "type": "directory",
        "members": members,
    });
    write_file(&physical_metadata, format!("{line}\n"));
    fs::create_dir_all(staged_metadata.parent().unwrap()).unwrap();
    file_symlink(&physical_metadata, &staged_metadata);
    StagedTree {
        physical,
        staged,
        file: staged_metadata,
    }
}

#[cfg(unix)]
fn bind(tree: StagedTree) -> Result<BoundTree> {
    let mut bindings = read_source_bindings(&text(&tree.file), &components(&["plugin"]))?;
    Ok(BoundTree {
        physical: tree.physical,
        staged: tree.staged,
        bindings: bindings.remove("plugin").unwrap(),
        file: tree.file,
    })
}

#[cfg(unix)]
fn create_bound_tree(directory: &Path, members: &[&str]) -> Result<BoundTree> {
    bind(stage_tree(directory, members))
}

#[cfg(unix)]
fn must_bound_tree(directory: &Path) -> BoundTree {
    create_bound_tree(directory, &["lib/native.jar"]).unwrap()
}

#[cfg(unix)]
fn components(names: &[&str]) -> Vec<CompositionComponent> {
    names
        .iter()
        .map(|name| CompositionComponent {
            manifest: (*name).into(),
            plugin_classpath_part: None,
        })
        .collect()
}

#[cfg(unix)]
#[test]
fn source_bindings_resolve_a_bound_member() {
    let directory = TempDir::new();
    let fixture = must_bound_tree(directory.path());
    let staged = fixture.staged.join("lib/native.jar");
    assert_eq!(
        fixture.bindings.resolve(&text(&staged)).unwrap(),
        (fixture.physical.join("lib/native.jar"), 12)
    );
    assert_eq!(fixture.bindings.sources[&staged].directory, Some(fixture.physical.clone()));
    assert!(!fixture.bindings.sources.contains_key(&fixture.staged));
    let lib = &fixture.bindings.sources[&fixture.staged.join("lib")];
    assert_eq!(lib.kind, SourceKind::Directory);
}

#[cfg(unix)]
#[test]
fn source_bindings_reject_outside_sources_and_member_tampering() {
    let directory = TempDir::new();
    let fixture = must_bound_tree(&directory.path().join("tree"));
    let outside = directory.join("outside");
    write_file(&outside, "native bytes");
    for source in [outside.clone(), fixture.staged("lib/Native.jar"), text(&fixture.staged)] {
        require_error(fixture.bindings.resolve(&source), "Missing declared artifact binding");
    }
    let other = read_source_bindings(&text(&fixture.file), &components(&["plugin", "other"])).unwrap();
    require_error(
        other["other"].resolve(&fixture.staged("lib/native.jar")),
        "Missing declared artifact binding",
    );
    let staged = fixture.staged("lib/native.jar");
    fs::remove_file(&staged).unwrap();
    file_symlink(&outside, &staged);
    require_error(fixture.bindings.resolve(&staged), "Staged source differs");
}

// A member that becomes a link fails when the composer resolves it. The reader checks the directories of a tree once,
// so a directory becomes a link before the reader runs, and the member below it fails when the composer resolves it.
#[cfg(unix)]
#[test]
fn source_bindings_reject_genuine_file_links_and_directory_escapes() {
    let directory = TempDir::new();
    let fixture = must_bound_tree(&directory.path().join("file"));
    let outside = directory.path().join("outside-file/lib");
    write_file(outside.join("native.jar"), "native bytes");
    let member = fixture.physical.join("lib/native.jar");
    fs::remove_file(&member).unwrap();
    file_symlink(outside.join("native.jar"), &member);
    require_error(fixture.bindings.resolve(&fixture.staged("lib/native.jar")), "not a regular file");

    for (escape, message) in [("directory", "escaping directory alias"), ("root", "escapes its artifact binding")] {
        let tree = stage_tree(&directory.path().join(escape), &["lib/native.jar"]);
        let outside = directory.path().join(format!("outside-{escape}/lib"));
        write_file(outside.join("native.jar"), "native bytes");
        let member_directory = tree.physical.join("lib");
        fs::remove_file(member_directory.join("native.jar")).unwrap();
        fs::remove_dir(&member_directory).unwrap();
        if escape == "directory" {
            file_symlink(&outside, &member_directory);
        } else {
            fs::remove_dir(&tree.physical).unwrap();
            file_symlink(outside.parent().unwrap(), &tree.physical);
        }
        let fixture = bind(tree).unwrap();
        require_error(fixture.bindings.resolve(&fixture.staged("lib/native.jar")), message);
    }
}

#[cfg(unix)]
#[test]
fn source_bindings_reject_missing_members_and_aliases() {
    let directory = TempDir::new();
    let fixture = create_bound_tree(&directory.path().join("missing"), &[]).unwrap();
    require_error(
        fixture.bindings.resolve(&fixture.staged("lib/native.jar")),
        "Missing declared artifact binding",
    );
    let invalid: [&[&str]; 7] = [
        &["lib/native.jar", "lib/native.jar"],
        &["lib/native.jar", "lib/Native.jar"],
        &["lib/native.jar", "Lib/second.jar"],
        &["lib/e\u{301}.jar", "lib/é.jar"],
        &["lib/../outside"],
        &["lib//native.jar"],
        &["lib", "lib/native.jar"],
    ];
    for (index, members) in invalid.iter().enumerate() {
        let result = create_bound_tree(&directory.path().join(format!("invalid-{index}")), members);
        assert!(result.is_err(), "accepted members {members:?}");
    }
}

#[cfg(unix)]
#[test]
fn source_bindings_reject_changed_owners_and_anchor_paths() {
    let directory = TempDir::new();
    for (index, (from, to)) in [
        (r#""component":"plugin""#, r#""component":"other""#),
        ("../trees/plugin", "../trees/other"),
        (r#""type":"directory""#, r#""type":"file""#),
        (r#""type":"directory""#, r#""type":"tree""#),
    ]
    .into_iter()
    .enumerate()
    {
        let fixture = must_bound_tree(&directory.path().join(format!("tamper-{index}")));
        let physical = fscopy::resolve_links(&fixture.file).unwrap();
        write_file(&physical, read_text(&physical).replacen(from, to, 1));
        assert!(
            read_source_bindings(&text(&fixture.file), &components(&["plugin"])).is_err(),
            "accepted the mutation {from} -> {to}"
        );
    }
}

#[cfg(unix)]
#[test]
fn source_bindings_accept_a_file_artifact_and_reject_its_members() {
    let directory = TempDir::new();
    let root = directory.root();
    write_file(format!("{root}/physical/packed.jar"), "packed");
    let physical_file = format!("{root}/physical/metadata/bindings.jsonl");
    let staged_file = format!("{root}/sandbox/metadata/bindings.jsonl");
    fs::create_dir_all(format!("{root}/sandbox/metadata")).unwrap();
    file_symlink(format!("{root}/physical/packed.jar"), format!("{root}/sandbox/packed.jar"));
    let line = |members: &str| {
        format!(
            r#"{{"component":"plugin","source":"{root}/sandbox/packed.jar","anchorRelativePath":"../packed.jar","type":"file","members":[{members}]}}"#
        )
    };
    write_file(&physical_file, format!("{}\r\n", line("")));
    file_symlink(&physical_file, &staged_file);
    let bindings = read_source_bindings(&staged_file, &components(&["plugin"])).unwrap();
    let resolved = bindings["plugin"].resolve(&format!("{root}/sandbox/packed.jar")).unwrap();
    assert_eq!(resolved, (PathBuf::from(format!("{root}/physical/packed.jar")), 6));
    write_file(&physical_file, line(r#""a""#));
    require_error(
        read_source_bindings(&staged_file, &components(&["plugin"])),
        "File source artifact lists members",
    );
    write_file(&physical_file, line("").replace(r#""type":"file""#, r#""type":"symlink""#));
    require_error(
        read_source_bindings(&staged_file, &components(&["plugin"])),
        "unknown variant `symlink`, expected `file` or `directory`",
    );
    write_file(&physical_file, format!("{}\n{}", line(""), line("")));
    require_error(
        read_source_bindings(&staged_file, &components(&["plugin"])),
        "Duplicate source artifact binding",
    );
    require_error(
        read_source_bindings(&staged_file, &components(&["plugin", "plugin"])),
        "Duplicate component manifest",
    );
}
