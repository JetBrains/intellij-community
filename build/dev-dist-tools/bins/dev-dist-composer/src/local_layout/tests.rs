// The local layout that `compose_components` writes for launch metadata.

use std::collections::BTreeMap;
use std::path::Path;

use component::Result;
use component::manifest::ComponentEntry;
use component::plugin_classpath::PLUGIN_CLASSPATH;

use crate::compose::{ComposeOptions, ComposedBuild, DevBuildComponent, compose_with_merge};
use crate::fingerprint::compute_ide_fingerprint_from_components;
use crate::test_support::{
    TempDir, directory_entry, file_entry, link_entry, no_merge, read_text, require_absent, require_error, runfiles, skip_merge,
    sourced_entry, test_manifest, with_entries, write_file,
};

const NO_MODULES: &[&str] = &[];

fn layout_component(kind: &str, entries: Vec<ComponentEntry>) -> DevBuildComponent {
    DevBuildComponent::new(with_entries(test_manifest(kind), entries))
}

fn compose_local(components: &[DevBuildComponent], target: &Path, source_runfiles: BTreeMap<String, String>) -> Result<ComposedBuild> {
    let options = ComposeOptions {
        source_runfiles: Some(source_runfiles),
        ..ComposeOptions::default()
    };
    compose_with_merge(components, target, &options, no_merge)
}

#[track_caller]
fn require_layout(target: &Path, fragments: &[&str]) -> String {
    let layout = read_text(target.join("local-layout.json"));
    for fragment in fragments {
        assert!(layout.contains(fragment), "the local layout has no {fragment}: {layout}");
    }
    layout
}

#[test]
fn directory_components_preserve_modes_in_the_local_layout_without_payload_trees() {
    let directory = TempDir::new();
    let entries = vec![directory_entry("resources/empty", 0o710), directory_entry("resources", 0o700)];
    let plugin = layout_component("plugin", entries.clone());
    let metadata = directory.path().join("metadata");
    compose_local(std::slice::from_ref(&plugin), &metadata, runfiles(&[])).unwrap();
    require_layout(&metadata, &[r#""kind":"directory""#, r#""mode":456"#, r#""mode":448"#]);
    require_absent(metadata.join("resources"));
    let mut conventional = plugin.manifest.clone();
    for entry in &mut conventional.entries {
        entry.mode = Some(0o755);
    }
    let fingerprint = |manifest| compute_ide_fingerprint_from_components(&[manifest], None, NO_MODULES).unwrap();
    assert_ne!(
        fingerprint(&plugin.manifest),
        fingerprint(&conventional),
        "the fingerprint ignores directory modes"
    );
    for change in [
        |entry: &mut ComponentEntry| entry.hash = Some(0),
        |entry: &mut ComponentEntry| entry.source = Some("tree".into()),
        |entry: &mut ComponentEntry| entry.executable = true,
        |entry: &mut ComponentEntry| entry.symlink_target = Some("other".into()),
    ] {
        let mut invalid = entries[0].clone();
        change(&mut invalid);
        let target = directory.path().join("invalid");
        let result = compose_with_merge(
            &[layout_component("invalid", vec![invalid])],
            &target,
            &ComposeOptions::default(),
            no_merge,
        );
        require_error(result, "Invalid directory");
    }
}

#[test]
fn a_manifest_only_link_reaches_the_local_layout_without_a_payload() {
    let directory = TempDir::new();
    let target = directory.path().join("metadata");
    compose_local(
        &[layout_component("plugin", vec![link_entry("plugins/demo/current", "lib/payload")])],
        &target,
        runfiles(&[]),
    )
    .unwrap();
    require_layout(&target, &[r#""symlinkTarget":"lib/payload""#]);
}

#[test]
fn exact_modes_remain_in_launch_metadata_without_reading_payloads() {
    let directory = TempDir::new();
    let source = directory.join("absent-tool");
    let mut file = sourced_entry("plugins/demo/bin/tool", &source);
    file.executable = true;
    file.mode = Some(0o750);
    let target = directory.path().join("metadata");
    compose_local(
        &[layout_component("plugin", vec![file])],
        &target,
        runfiles(&[(&source, "_main/absent-tool")]),
    )
    .unwrap();
    require_layout(&target, &[r#""mode":488"#]);
    require_absent(&source);
}

#[test]
fn canonical_modes_retain_the_existing_local_linking_policy() {
    let directory = TempDir::new();
    let (jar, executable) = (directory.join("absent.jar"), directory.join("absent-tool"));
    let mut jar_entry = sourced_entry("plugins/demo/lib/main.jar", &jar);
    jar_entry.mode = Some(0o644);
    let mut tool_entry = sourced_entry("plugins/demo/bin/tool", &executable);
    tool_entry.mode = Some(0o755);
    tool_entry.executable = true;
    let target = directory.path().join("metadata");
    let source_runfiles = runfiles(&[(&jar, "_main/absent.jar"), (&executable, "_main/absent-tool")]);
    compose_local(&[layout_component("plugin", vec![jar_entry, tool_entry])], &target, source_runfiles).unwrap();
    let layout = require_layout(&target, &[r#""executable":false"#, r#""executable":true"#]);
    assert!(
        !layout.contains(r#""mode":420"#) && !layout.contains(r#""mode":493"#),
        "the local layout names a canonical mode: {layout}"
    );
}

#[test]
fn invalid_or_conflicting_modes_fail_before_creating_launch_metadata() {
    let directory = TempDir::new();
    for (index, (mode, executable)) in [(512, false), (0o755, false), (0o644, true)].into_iter().enumerate() {
        let mut file = file_entry("bin/tool");
        file.mode = Some(mode);
        file.executable = executable;
        let target = directory.path().join(format!("metadata-{index}"));
        require_error(
            compose_local(&[layout_component("plugin", vec![file])], &target, runfiles(&[])),
            "file mode",
        );
        require_absent(&target);
    }
    let mut link = link_entry("bin/link", "tool");
    link.mode = Some(0);
    let target = directory.path().join("metadata-link");
    require_error(
        compose_local(&[layout_component("plugin", vec![link])], &target, runfiles(&[])),
        "file mode",
    );
    require_absent(&target);
}

#[test]
fn local_composition_reads_metadata_without_staging_payload() {
    let directory = TempDir::new();
    let (root, packed) = (directory.join("absent-tree"), directory.join("absent.jar"));
    let app = directory.join("absent-tree/lib/app.jar");
    let components = [
        layout_component("platform", vec![sourced_entry("lib/app.jar", &app)]),
        layout_component("packed", vec![sourced_entry("plugins/demo/lib/demo.jar", &packed)]),
    ];
    let target = directory.path().join("metadata");
    let options = ComposeOptions {
        expected_fragments: vec!["platform".into(), "packed".into()],
        source_runfiles: Some(runfiles(&[(&app, "_main/tree/lib/app.jar"), (&packed, "community+/packed.jar")])),
        ..ComposeOptions::default()
    };
    compose_with_merge(&components, &target, &options, no_merge).unwrap();
    require_layout(
        &target,
        &[r#""runfile":"_main/tree/lib/app.jar""#, r#""runfile":"community+/packed.jar""#],
    );
    for absent in [target.join("lib"), target.join("plugins"), root.into(), packed.into()] {
        require_absent(absent);
    }
}

#[test]
fn local_and_exported_compositions_have_the_same_metadata() {
    let directory = TempDir::new();
    let app = directory.join("tree/lib/app.jar");
    write_file(&app, "bytes");
    let (prefix, part) = (directory.join("prefix"), directory.join("part"));
    write_file(&prefix, [1, 2, 3]);
    write_file(&part, [4, 5, 6]);
    let mut platform = layout_component("platform", vec![sourced_entry("lib/app.jar", &app)]);
    platform.manifest.plugin_count = 1;
    platform.plugin_classpath_part = Some(part.into());
    let components = [platform];
    let exported_options = ComposeOptions {
        plugin_classpath_prefix: Some(prefix.clone().into()),
        ..ComposeOptions::default()
    };
    let exported = compose_with_merge(&components, &directory.path().join("dist"), &exported_options, skip_merge).unwrap();
    std::fs::remove_file(&app).unwrap();
    let local_options = ComposeOptions {
        plugin_classpath_prefix: Some(prefix.into()),
        source_runfiles: Some(runfiles(&[(&app, "_main/tree/lib/app.jar")])),
        ..ComposeOptions::default()
    };
    let local = compose_with_merge(&components, &directory.path().join("metadata"), &local_options, no_merge).unwrap();
    assert_eq!(local, exported);
    assert_eq!(
        read_text(directory.path().join("metadata").join(PLUGIN_CLASSPATH)),
        read_text(directory.path().join("dist").join(PLUGIN_CLASSPATH)),
        "the plugin classpath differs"
    );
}

#[test]
fn local_composition_preserves_genuine_relative_links() {
    let directory = TempDir::new();
    let target = directory.path().join("metadata");
    compose_local(
        &[layout_component("platform", vec![link_entry("lib/current", "versions/A")])],
        &target,
        runfiles(&[]),
    )
    .unwrap();
    require_layout(&target, &[r#""symlinkTarget":"versions/A""#, r#""runfile":null"#]);
}

#[test]
fn local_composition_rejects_undeclared_inputs() {
    let directory = TempDir::new();
    let component = layout_component("platform", vec![sourced_entry("lib/app.jar", &directory.join("tree/lib/app.jar"))]);
    require_error(
        compose_local(&[component], &directory.path().join("metadata"), runfiles(&[])),
        "undeclared source",
    );
}

#[test]
fn local_composition_resolves_files_inside_declared_directories_without_reading_them() {
    let directory = TempDir::new();
    let plugin_directory = directory.join("absent-plugin");
    let packed = format!("{plugin_directory}/lib/nested/plugin.jar");
    let target = directory.path().join("metadata");
    let options = ComposeOptions {
        source_runfiles: Some(runfiles(&[])),
        source_directory_runfiles: Some(runfiles(&[(&plugin_directory, "_main/plugin")])),
        ..ComposeOptions::default()
    };
    let components = [layout_component(
        "plugin",
        vec![sourced_entry("plugins/demo/lib/plugin.jar", &packed)],
    )];
    compose_with_merge(&components, &target, &options, no_merge).unwrap();
    require_layout(&target, &[r#""runfile":"_main/plugin/lib/nested/plugin.jar""#]);
    require_absent(&plugin_directory);
    require_absent(target.join("plugins"));
}

#[test]
fn local_composition_does_not_treat_a_file_declaration_as_a_directory() {
    let directory = TempDir::new();
    let file = directory.join("file");
    let component = layout_component("plugin", vec![sourced_entry("lib/plugin.jar", &format!("{file}/nested.jar"))]);
    let result = compose_local(&[component], &directory.path().join("metadata"), runfiles(&[(&file, "_main/file")]));
    require_error(result, "undeclared source");
}

#[test]
fn the_deepest_declared_directory_names_the_runfile() {
    let directory = TempDir::new();
    let (outer, inner) = (directory.join("plugin"), directory.join("plugin/lib"));
    let options = ComposeOptions {
        source_runfiles: Some(runfiles(&[])),
        source_directory_runfiles: Some(runfiles(&[(&outer, "_main/plugin"), (&inner, "_main/lib")])),
        ..ComposeOptions::default()
    };
    let target = directory.path().join("metadata");
    let components = [layout_component(
        "plugin",
        vec![sourced_entry("lib/plugin.jar", &format!("{inner}/plugin.jar"))],
    )];
    compose_with_merge(&components, &target, &options, no_merge).unwrap();
    require_layout(&target, &[r#""runfile":"_main/lib/plugin.jar""#]);
}

#[test]
fn directory_references_reject_sibling_prefixes_and_escaping_paths() {
    let directory = TempDir::new();
    let plugin_directory = directory.join("plugin");
    for source in [
        directory.join("plugin-other/file.jar"),
        format!("{plugin_directory}/../other.jar"),
        format!("{plugin_directory}/lib/../plugin.jar"),
    ] {
        let options = ComposeOptions {
            source_runfiles: Some(runfiles(&[])),
            source_directory_runfiles: Some(runfiles(&[(&plugin_directory, "_main/plugin")])),
            ..ComposeOptions::default()
        };
        let components = [layout_component("plugin", vec![sourced_entry("lib/plugin.jar", &source)])];
        let result = compose_with_merge(&components, &directory.path().join("metadata"), &options, no_merge);
        assert!(result.is_err(), "accepted the source {source}");
    }
}

#[test]
fn manifest_only_links_preserve_their_spelling_without_a_payload_tree() {
    let directory = TempDir::new();
    let metadata = directory.path().join("metadata");
    let target = "lib/../lib/plugin.jar";
    compose_local(
        &[layout_component("plugin", vec![link_entry("plugins/demo/current", target)])],
        &metadata,
        runfiles(&[]),
    )
    .unwrap();
    require_layout(&metadata, &[&format!(r#""symlinkTarget":"{target}""#)]);
}

#[test]
fn manifest_only_links_reject_a_target_with_an_empty_segment() {
    let directory = TempDir::new();
    let result = compose_local(
        &[layout_component(
            "plugin",
            vec![link_entry("plugins/demo/current", "lib//payload/")],
        )],
        &directory.path().join("metadata"),
        runfiles(&[]),
    );
    require_error(result, "has an empty segment");
}

#[test]
fn manifest_only_links_reject_file_sources_and_escapes() {
    let directory = TempDir::new();
    let mut with_source = link_entry("plugins/demo/current", "lib/plugin.jar");
    with_source.source = Some("ambiguous".into());
    for entry in [with_source, link_entry("plugins/demo/current", "../../../outside")] {
        let result = compose_local(
            &[layout_component("plugin", vec![entry.clone()])],
            &directory.path().join("metadata"),
            runfiles(&[]),
        );
        assert!(result.is_err(), "accepted {entry:?}");
    }
}

// No payload has a link chain, so the inventory rules refuse one. A refused chain cannot escape or cycle.
#[test]
fn link_chains_are_refused() {
    let directory = TempDir::new();
    for entries in [
        vec![
            link_entry("plugins/demo/current", "../.."),
            link_entry("plugins/demo/escape", "current/../outside"),
        ],
        vec![
            link_entry("plugins/demo/first", "second"),
            link_entry("plugins/demo/second", "first"),
        ],
        vec![
            link_entry("plugins/demo/current", "../.."),
            link_entry("plugins/demo/escape", "CURRENT/../outside"),
        ],
    ] {
        let result = compose_local(
            &[layout_component("plugin", entries)],
            &directory.path().join("metadata"),
            runfiles(&[]),
        );
        require_error(result, "unsupported symbolic link chain");
    }
}

// A distribution path is ASCII, so no Unicode alias can bypass the link checks.
#[test]
fn a_path_that_is_not_ascii_is_refused() {
    let directory = TempDir::new();
    for alias in ["curre\u{301}nt", "σ", "straẞe", "ı"] {
        let links = vec![link_entry(&format!("plugins/demo/{alias}"), "../..")];
        let result = compose_local(
            &[layout_component("plugin", links)],
            &directory.path().join("metadata"),
            runfiles(&[]),
        );
        require_error(result, "unsupported character");
    }
}

#[test]
fn local_composition_rejects_unsafe_and_conflicting_paths() {
    let directory = TempDir::new();
    let source = directory.join("tree/file.jar");
    let file = |relative_path: &str| sourced_entry(relative_path, &source);
    let cases = [
        vec![file("../outside")],
        vec![file("/absolute")],
        vec![file("dir/../outside")],
        vec![file("lib/app.jar"), file("lib/app.jar")],
        vec![file("lib/cafe\u{301}.jar"), file("lib/café.jar")],
        vec![file("lib/first.jar"), file("Lib/second.jar")],
        vec![file("cafe\u{301}/first.jar"), file("café/second.jar")],
        vec![file("lib"), file("lib/app.jar")],
        vec![file("fingerprint.txt")],
        vec![file("local-layout.json")],
        vec![link_entry("lib/link", "")],
        vec![link_entry("lib/link", "../../outside")],
    ];
    for entries in cases {
        let result = compose_local(
            &[layout_component("platform", entries.clone())],
            &directory.path().join("metadata"),
            runfiles(&[(&source, "_main/tree/file.jar")]),
        );
        assert!(result.is_err(), "accepted {entries:?}");
    }
}

// The `local-home` step reads this exact shape, so the bytes are pinned.
#[test]
fn local_layout_bytes() {
    let directory = TempDir::new();
    let (app, packed) = (directory.join("tree/lib/app.jar"), directory.join("packed.jar"));
    let mut file = sourced_entry("plugins/demo/lib/demo \"quoted\"\t.jar", &packed);
    file.mode = Some(0o750);
    file.executable = true;
    let target = directory.path().join("metadata");
    let components = [
        layout_component(
            "platform",
            vec![
                sourced_entry("lib/app.jar", &app),
                link_entry("lib/current", "app.jar"),
                directory_entry("lib/empty", 0o700),
            ],
        ),
        layout_component("packed", vec![file]),
    ];
    compose_local(
        &components,
        &target,
        runfiles(&[(&app, "_main/tree/lib/app.jar"), (&packed, "_main/packed.jar")]),
    )
    .unwrap();
    let expected = concat!(
        r#"{"version":1,"files":["#,
        r#"{"path":"lib/app.jar","runfile":"_main/tree/lib/app.jar","symlinkTarget":null,"executable":false,"mode":null},"#,
        r#"{"path":"lib/current","runfile":null,"symlinkTarget":"app.jar","executable":false,"mode":null},"#,
        r#"{"path":"lib/empty","runfile":null,"symlinkTarget":null,"executable":false,"mode":448,"kind":"directory"},"#,
        r#"{"path":"plugins/demo/lib/demo \"quoted\"\t.jar","runfile":"_main/packed.jar","symlinkTarget":null,"executable":true,"mode":488}"#,
        r#"],"metadata":["core-classpath.txt","fingerprint.txt"]}"#
    );
    assert_eq!(read_text(target.join("local-layout.json")), expected);
}

#[test]
fn a_plugin_classpath_joins_the_metadata_list() {
    let directory = TempDir::new();
    let (prefix, part) = (directory.join("prefix"), directory.join("part"));
    write_file(&prefix, [3, 1]);
    write_file(&part, [7]);
    let mut plugins = layout_component("plugins", vec![]);
    plugins.manifest.plugin_count = 1;
    plugins.plugin_classpath_part = Some(part.into());
    let target = directory.path().join("metadata");
    let options = ComposeOptions {
        plugin_classpath_prefix: Some(prefix.into()),
        source_runfiles: Some(runfiles(&[])),
        ..ComposeOptions::default()
    };
    compose_with_merge(&[plugins], &target, &options, no_merge).unwrap();
    require_layout(
        &target,
        &[r#""metadata":["core-classpath.txt","fingerprint.txt","plugins/plugin-classpath.txt"]"#],
    );
    assert_eq!(std::fs::read(target.join(PLUGIN_CLASSPATH)).unwrap(), [3, 1, 0, 1, 7]);
}
