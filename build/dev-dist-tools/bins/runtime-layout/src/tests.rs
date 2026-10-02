// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::ffi::OsString;
use std::path::{Path, PathBuf};

use tempfile::TempDir;

use crate::assemble::{AssembledPart, Layout, PluginLayout, assemble};
use crate::descriptor::ContentModule;
use crate::part::{LAYOUT_ORDER, Member, PART_VERSION, PLUGIN_ORDER, Part, PartJar};
use crate::run;
use crate::targets::{JpsLibrary, LibraryIndex, MODULE_LIBRARY_KIND, PROJECT_LIBRARY_KIND, apparent_label, read_library_index};

const TEST_TARGETS: &str = r#"{
  "modules": {
    "p.main": {"productionTargets": ["//p:main"], "moduleLibraries": {"b": {"target": "@lib//:p-main-b", "jars": ["external/lib+/b.jar"], "jarTargets": ["@lib//:b/b-1.0.jar"]}}},
    "p.other": {"productionTargets": [], "moduleLibraries": {"shared": {"target": "@lib//:p-other-shared", "jars": ["external/lib+/shared.jar"], "jarTargets": ["@lib//:shared.jar"]}}}
  },
  "projectLibraries": {
    "a": {"target": "@lib//:a", "jars": ["external/lib+/a1.jar", "external/lib+/a2.jar"], "jarTargets": ["@lib//:a/a1.jar", "@lib//:a/a2.jar"]},
    "alpha": {"target": "@lib//:alpha", "jars": ["external/lib+/alpha.jar"], "jarTargets": ["@lib//:alpha.jar"]},
    "zeta": {"target": "@lib//libs/zeta:zeta", "jars": ["external/lib+/zeta.jar"], "jarTargets": ["@lib//libs/zeta:zeta.jar"]},
    "shared1": {"target": "@lib//:shared1", "jars": ["external/lib+/shared.jar"], "jarTargets": ["@lib//:shared.jar"]},
    "shared2": {"target": "@lib//:shared2", "jars": ["external/lib+/shared.jar"], "jarTargets": ["@lib//:shared.jar"]}
  },
  "pluginDistributionTargets": {}
}"#;

const TEST_PLAN: &str = r#"{
  "version": 2,
  "plugin": "p.main",
  "variant": "",
  "assets": [
    {"module": "p.content"},
    {"destination": "lib/modules/p.natives.jar", "recipe": {"sources": [
      {"input": "p.natives", "kind": "module", "filter": "module-v1"},
      {"input": "@lib//:natives", "kind": "library", "filter": "library-v1"}
    ], "writer": {"mergeEntities": true, "nativeLib": "natives"}}},
    {"destination": "lib/p.jar", "recipe": {"sources": [
      {"input": "descriptor:p.main", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]},
      {"input": "p.main", "kind": "module", "filter": "module-v1"},
      {"input": "p.other", "kind": "module", "filter": "module-v1"},
      {"input": "@lib//:a", "kind": "library", "filter": "library-v1"}
    ], "writer": {"manifest": "drop", "mergeEntities": true}}},
    {"destination": "lib/a2.jar", "recipe": {"sources": [
      {"input": "@lib//:a/a2.jar", "kind": "archive", "filter": "library-v1"}
    ], "writer": {"mergeEntities": true}}},
    {"destination": "lib/resources.jar", "recipe": {"sources": [
      {"input": "layout-assets:0:output", "kind": "prepared", "filter": "prepared"}
    ], "writer": {"manifest": "drop", "mergeEntities": true}}},
    {"destination": "js", "inputs": ["module-resource:0:source"], "kind": "tree", "classPath": false}
  ],
  "operations": [
    {"id": "layout-assets:0", "kind": "layout-assets", "inputs": [{"artifact": "p.main"}], "output": "layout-assets:0:output", "manifest": "keep",
      "layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "archive-tree"}}]}}
  ]
}"#;

const TEST_CATALOGUE: &str = r#"{
  "version": 1,
  "artifacts": [
    {"id": "p.main", "kind": "file", "root": "bazel-out/bin/p/main.jar"},
    {"id": "@lib//:a/a1.jar", "kind": "file", "root": "external/lib+/a1.jar"},
    {"id": "@lib//:a/a2.jar", "kind": "file", "root": "external/lib+/a2.jar"}
  ],
  "libraries": [
    {"id": "@lib//:a", "files": [{"artifact": "@lib//:a/a1.jar"}, {"artifact": "@lib//:a/a2.jar"}]}
  ]
}"#;

/// A temporary directory that holds the input files of one test.
struct Files(TempDir);

impl Files {
    fn new() -> Self {
        Self(tempfile::tempdir().unwrap())
    }

    fn write(&self, name: &str, content: &str) -> String {
        let file = self.path(name);
        std::fs::write(&file, content).unwrap();
        file.display().to_string()
    }

    fn path(&self, name: &str) -> PathBuf {
        self.0.path().join(name)
    }
}

fn test_index() -> LibraryIndex {
    let files = Files::new();
    read_library_index(Path::new(&files.write("bazel-targets.json", TEST_TARGETS))).unwrap()
}

fn modules(names: &[&str]) -> Vec<Member> {
    names
        .iter()
        .map(|name| Member {
            module: (*name).to_owned(),
            ..Member::default()
        })
        .collect()
}

fn library(label: &str, jars: &[&str]) -> Member {
    Member {
        library: label.to_owned(),
        jars: jars.iter().map(|jar| (*jar).to_owned()).collect(),
        ..Member::default()
    }
}

fn jar(destination: &str, members: Vec<Member>) -> PartJar {
    PartJar {
        destination: destination.to_owned(),
        members,
        reused: false,
    }
}

fn reused_jar(destination: &str, members: Vec<Member>) -> PartJar {
    PartJar {
        destination: destination.to_owned(),
        members,
        reused: true,
    }
}

fn plugin_part(descriptor_module: &str, directory: &str, jars: Vec<PartJar>) -> Part {
    Part {
        version: PART_VERSION,
        descriptor_module: descriptor_module.to_owned(),
        directory: directory.to_owned(),
        order: PLUGIN_ORDER.to_owned(),
        descriptor: "plugin.xml".to_owned(),
        jars,
    }
}

fn layout_part(descriptor_module: &str, jars: Vec<PartJar>) -> Part {
    Part {
        version: PART_VERSION,
        descriptor_module: descriptor_module.to_owned(),
        order: LAYOUT_ORDER.to_owned(),
        jars,
        ..Part::default()
    }
}

/// A layout part as the assembly takes it.
fn in_part_order(part: Part, frontend_only: bool) -> AssembledPart {
    AssembledPart {
        part,
        frontend_only,
        ..AssembledPart::default()
    }
}

fn content(names: &[(&str, &str)]) -> Vec<ContentModule> {
    names.iter().map(|(name, loading)| ContentModule::new(name, loading)).collect()
}

fn entry_modules(plugin: &PluginLayout) -> Vec<&str> {
    plugin
        .entries
        .iter()
        .filter(|entry| entry.kind == "module")
        .map(|entry| entry.name.as_str())
        .collect()
}

fn assemble_one(part: Part, content: Vec<ContentModule>) -> Layout {
    assemble(
        &[AssembledPart {
            part,
            content,
            ..AssembledPart::default()
        }],
        &test_index(),
    )
    .unwrap()
}

/// The jars of `intellij.dev` as `dev_plugin` states them: its own jars in plan order, then the reused content module
/// jars in label order. The expected order is the header that the Kotlin fragment writes for the plugin.
#[test]
fn reused_jars_follow_content_order() {
    let names = [
        "intellij.platform.statistics.devkit",
        "intellij.platform.statistics.devkit.backend",
        "intellij.platform.statistics.devkit.frontend",
        "intellij.platform.ide.ui.inspector",
        "intellij.dev.psiViewer",
        "intellij.dev.codeInsight",
        "intellij.dev.leakDetection",
        "intellij.dev.pluginLoading",
        "intellij.java.dev",
        "intellij.groovy.dev",
        "intellij.kotlin.dev",
        "intellij.php.dev",
        "intellij.dev.core",
    ];
    let reused = |module: &str| reused_jar(&format!("lib/modules/{module}.jar"), modules(&[module]));
    let part = plugin_part(
        "intellij.dev",
        "plugins/dev",
        vec![
            jar(
                "lib/dev.jar",
                modules(&[
                    "intellij.dev",
                    "intellij.dev.psiViewer",
                    "intellij.dev.codeInsight",
                    "intellij.java.dev",
                    "intellij.kotlin.dev",
                ]),
            ),
            jar("lib/modules/intellij.php.dev.jar", modules(&["intellij.php.dev"])),
            reused("intellij.platform.ide.ui.inspector"),
            reused("intellij.platform.statistics.devkit.backend"),
            reused("intellij.platform.statistics.devkit.frontend"),
            reused("intellij.platform.statistics.devkit"),
            reused("intellij.dev.core"),
            reused("intellij.dev.leakDetection"),
            reused("intellij.dev.pluginLoading"),
            reused("intellij.groovy.dev"),
        ],
    );
    let result = assemble_one(part, names.iter().map(|name| ContentModule::new(name, "")).collect());
    let expected = [
        "intellij.platform.statistics.devkit",
        "intellij.platform.statistics.devkit.backend",
        "intellij.platform.statistics.devkit.frontend",
        "intellij.platform.ide.ui.inspector",
        "intellij.dev.psiViewer",
        "intellij.dev.codeInsight",
        "intellij.java.dev",
        "intellij.kotlin.dev",
        "intellij.dev",
        "intellij.dev.leakDetection",
        "intellij.dev.pluginLoading",
        "intellij.groovy.dev",
        "intellij.php.dev",
        "intellij.dev.core",
    ];
    assert_eq!(entry_modules(&result.plugins[0]), expected);
    let first = &result.plugins[0].entries[0];
    assert_eq!(
        first.path.as_deref(),
        Some("plugins/dev/lib/modules/intellij.platform.statistics.devkit.jar")
    );
    assert_eq!(
        first.relative_output_file.as_deref(),
        Some("modules/intellij.platform.statistics.devkit.jar")
    );
}

/// A reused jar with a custom path is a jar of the layout pass, as for `intellij.java.plugin`. The part states it in
/// classpath order, and it keeps that place. A reused jar that the content pass places goes among the jars of that pass.
#[test]
fn reused_jar_with_custom_path_keeps_its_place() {
    let part = plugin_part(
        "p.main",
        "plugins/p",
        vec![
            jar("lib/modules/p.content.jar", modules(&["p.content"])),
            reused_jar("lib/modules/p.reused.jar", modules(&["p.reused"])),
            jar("lib/launcher.jar", modules(&["p.launcher"])),
            reused_jar("lib/p_rt.jar", modules(&["p.rt"])),
            reused_jar("lib/resources/p.annotations.jar", modules(&["p.annotations"])),
            jar("lib/p.jar", modules(&["p.main"])),
        ],
    );
    let content = vec![
        ContentModule::new("p.content", ""),
        ContentModule::new("p.reused", ""),
        ContentModule::new("p.annotations", "embedded"),
    ];
    let result = assemble_one(part, content);
    assert_eq!(
        entry_modules(&result.plugins[0]),
        ["p.content", "p.reused", "p.launcher", "p.rt", "p.annotations", "p.main"]
    );
    assert_eq!(result.plugins[0].entries[3].path.as_deref(), Some("plugins/p/lib/p_rt.jar"));
    assert_eq!(
        result.plugins[0].entries[4].relative_output_file.as_deref(),
        Some("resources/p.annotations.jar")
    );
}

/// A plugin part keeps the plan order of its own jars, library jars included. A reused jar goes among the jars of the
/// content pass, before the first jar of the layout pass. A library reports one entry for each of its files.
#[test]
fn plugin_jars_keep_plan_order() {
    let mut main_members = modules(&["p.main", "p.other"]);
    main_members.push(library("@lib//:a", &["external/lib+/a1.jar", "external/lib+/a2.jar"]));
    let part = plugin_part(
        "p.main",
        "plugins/p",
        vec![
            jar("lib/modules/p.content.jar", modules(&["p.content"])),
            jar("lib/p.jar", main_members),
            jar("lib/b.jar", vec![library("@lib//:p-main-b", &["external/lib+/b.jar"])]),
            jar("lib/zeta.jar", vec![library("@lib//libs/zeta:zeta", &[])]),
            jar("lib/alpha.jar", vec![library("@@lib+//:alpha", &["external/lib+/alpha.jar"])]),
            reused_jar("lib/modules/p.reused.jar", modules(&["p.reused"])),
        ],
    );
    let result = assemble_one(part, content(&[("p.content", ""), ("p.reused", "")]));
    let actual: Vec<String> = result.plugins[0]
        .entries
        .iter()
        .map(|entry| format!("{}:{}@{}", entry.kind, entry.name, entry.relative_output_file.as_deref().unwrap()))
        .collect();
    let expected = [
        "module:p.content@modules/p.content.jar",
        "module:p.reused@modules/p.reused.jar",
        "module:p.main@p.jar",
        "module:p.other@p.jar",
        "projectLibrary:a@p.jar",
        "projectLibrary:a@p.jar",
        "moduleLibrary:p.main@b.jar",
        "projectLibrary:zeta@zeta.jar",
        "projectLibrary:alpha@alpha.jar",
    ];
    assert_eq!(actual, expected);
}

/// The platform states its jars in the order of its layout. A frontend-only plugin is not in the distribution, so its
/// entries state no path.
#[test]
fn layout_order_and_frontend_only_plugins() {
    let platform = layout_part(
        "intellij.idea.customization",
        vec![
            jar("lib/util.jar", modules(&["intellij.platform.util", "intellij.platform.util.base"])),
            jar(
                "lib/app.jar",
                modules(&["intellij.platform.ide.impl", "intellij.idea.customization"]),
            ),
        ],
    );
    let frontend = layout_part(
        "intellij.frontend.split.customization",
        vec![jar("lib/frontend.jar", modules(&["intellij.frontend.split.customization"]))],
    );
    let parts = [in_part_order(platform.clone(), false), in_part_order(frontend, true)];
    let result = assemble(&parts, &test_index()).unwrap();
    assert_eq!(
        entry_modules(&result.plugins[0]),
        [
            "intellij.platform.util",
            "intellij.platform.util.base",
            "intellij.platform.ide.impl",
            "intellij.idea.customization"
        ]
    );
    assert_eq!(result.plugins[0].entries[2].path.as_deref(), Some("lib/app.jar"));
    let frontend_plugin = &result.plugins[1];
    assert!(frontend_plugin.additional_frontend_only_plugin);
    assert_eq!(frontend_plugin.entries[0].path, None);
    assert_eq!(frontend_plugin.entries[0].relative_output_file.as_deref(), Some("frontend.jar"));

    let duplicate = [in_part_order(platform.clone(), false), in_part_order(platform, false)];
    let error = assemble(&duplicate, &test_index()).unwrap_err();
    assert!(error.to_string().contains("two parts"), "a duplicate plugin was accepted: {error}");
}

#[test]
fn library_resolution() {
    let index = test_index();
    let project = |name: &str| JpsLibrary::new(PROJECT_LIBRARY_KIND, name);
    let module = |name: &str| JpsLibrary::new(MODULE_LIBRARY_KIND, name);
    let cases: [(Member, &[&str], JpsLibrary); 9] = [
        (library("@lib//:a", &[]), &[], project("a")),
        (library("@@lib+//:a", &[]), &[], project("a")),
        (library("@@lib+//libs/zeta:zeta", &[]), &[], project("zeta")),
        (library("@lib//:p-main-b", &[]), &[], module("p.main")),
        (
            library("@@lib+//:b/b-1.0.jar", &["bazel-out/bin/external/lib+/b/b-1.0.jar"]),
            &[],
            module("p.main"),
        ),
        (library("@lib//:a/a2.jar", &["external/lib+/a2.jar"]), &[], project("a")),
        (
            library("//unknown:label", &["external/lib+/a1.jar", "external/lib+/a2.jar"]),
            &[],
            project("a"),
        ),
        (library("@lib//:shared.jar", &[]), &["p.main", "p.other"], module("p.other")),
        (
            library("//unknown:label", &["external/lib+/shared.jar"]),
            &["p.other"],
            module("p.other"),
        ),
    ];
    for (member, jar_modules, expected) in cases {
        match index.resolve(&member, jar_modules) {
            Ok(library) => assert_eq!(library, expected, "{member:?}"),
            Err(error) => panic!("resolve({member:?}) failed: {error:#}"),
        }
    }
    for (member, message) in [
        (library("//unknown:label", &[]), "states no jar"),
        (library("@lib//libs/zeta", &[]), "does not name its target"),
        (library("//unknown:label", &["external/lib+/other.jar"]), "neither is its jar"),
        (library("//unknown:label", &["external/lib+/shared.jar"]), "belongs to 3 libraries"),
        (library("@lib//:shared.jar", &[]), "and 0 of them are module libraries"),
        (
            library("//unknown:label", &["external/lib+/a1.jar", "external/lib+/alpha.jar"]),
            "different libraries",
        ),
    ] {
        let error = index.resolve(&member, &["p.main"]).unwrap_err();
        assert!(
            format!("{error:#}").contains(message),
            "resolve({member:?}) error = {error:#}, expected {message:?}"
        );
    }
    let error = index.resolve(&library("@lib//:shared.jar", &[]), &["p.main"]).unwrap_err();
    assert_eq!(
        error.to_string(),
        "the jar @lib//:shared.jar belongs to 3 libraries of bazel-targets.json, and 0 of them are module libraries of the modules of its jar"
    );
}

#[test]
fn apparent_label_spells_the_bazel_targets_form() {
    for (input, expected) in [
        ("@@lib+//:x", "@lib//:x"),
        ("@@community+//platform/x:y", "@community//platform/x:y"),
        ("@@//build:x", "//build:x"),
        ("@lib//:x", "@lib//:x"),
        ("//plugins/p:p", "//plugins/p:p"),
    ] {
        assert_eq!(apparent_label(input).unwrap(), expected, "{input}");
    }
    for (input, message) in [
        ("//plugins/p", "the label //plugins/p does not name its target after a ':'"),
        (
            "@@lib+//libs/zeta",
            "the label @@lib+//libs/zeta does not name its target after a ':'",
        ),
        ("no-separator", "the label no-separator does not name its target after a ':'"),
        ("@@lib+", "the label @@lib+ has no package"),
        (
            "@@rules_jvm_external++maven+x//:y",
            "the label @@rules_jvm_external++maven+x//:y is not in the repository of a Bazel module",
        ),
        ("@@lib//:x", "the label @@lib//:x is not in the repository of a Bazel module"),
    ] {
        assert_eq!(apparent_label(input).unwrap_err().to_string(), message, "{input}");
    }
}

/// The converter writes every field that the tool reads, and never `null`. A file with another shape fails.
#[test]
fn bazel_targets_json_must_have_the_converter_shape() {
    let files = Files::new();
    for (targets, message) in [
        (
            r#"{"modules": {}, "projectLibraries": {"a": {"target": null, "jars": [], "jarTargets": []}}}"#,
            "invalid type: null, expected a string",
        ),
        (
            r#"{"modules": {}, "projectLibraries": {"a": {"target": "@lib//:a", "jars": []}}}"#,
            "missing field `jarTargets`",
        ),
        (r#"{"projectLibraries": {}}"#, "missing field `modules`"),
    ] {
        let error = read_library_index(Path::new(&files.write("bazel-targets.json", targets))).unwrap_err();
        assert!(format!("{error:#}").contains(message), "{targets}: {error:#}");
    }
}

struct Run {
    code: u8,
    output: String,
    errors: String,
}

fn run_tool(args: &[String]) -> Run {
    let mut output = Vec::new();
    let mut errors = Vec::new();
    let code = run(args.iter().map(OsString::from), &mut output, &mut errors);
    Run {
        code,
        output: String::from_utf8(output).unwrap(),
        errors: String::from_utf8(errors).unwrap(),
    }
}

/// The layout file keeps the field order and the formatting of the Kotlin serialization, with a final newline.
#[test]
fn assemble_command() {
    let files = Files::new();
    let descriptor = files.write(
        "plugin.xml",
        r#"<idea-plugin><content><module name="p.content"/></content></idea-plugin>"#,
    );
    let part = files.write(
        "part.json",
        &format!(
            r#"{{"version":1,"descriptorModule":"p.main","directory":"plugins/p","order":"plugin","descriptor":{descriptor:?},"jars":[{{"destination":"lib/p.jar","members":[{{"module":"p.main"}}]}},{{"destination":"lib/modules/p.content.jar","members":[{{"module":"p.content"}}],"reused":true}}]}}"#
        ),
    );
    let platform = files.write(
        "platform.json",
        r#"{"version":1,"descriptorModule":"intellij.idea.customization","directory":"","order":"layout","jars":[{"destination":"lib/util.jar","members":[{"module":"intellij.platform.util"}]},{"destination":"lib/app.jar","members":[{"module":"intellij.idea.customization"},{"library":"@@lib+//:alpha","jars":["external/lib+/alpha.jar"]}]}]}"#,
    );
    let targets = files.write("bazel-targets.json", TEST_TARGETS);
    let output = files.path("layout.json").display().to_string();
    let result = run_tool(&[
        format!("--part={platform}"),
        format!("--part={part}"),
        format!("--bazel-targets={targets}"),
        format!("--output={output}"),
    ]);
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(
        result.output,
        format!("Wrote the runtime module repository layout of 2 plugins to {output}\n")
    );
    let expected = r#"{
  "version": 1,
  "plugins": [
    {
      "descriptorModule": "intellij.idea.customization",
      "additionalFrontendOnlyPlugin": false,
      "entries": [
        {
          "kind": "module",
          "name": "intellij.platform.util",
          "path": "lib/util.jar",
          "relativeOutputFile": "util.jar"
        },
        {
          "kind": "module",
          "name": "intellij.idea.customization",
          "path": "lib/app.jar",
          "relativeOutputFile": "app.jar"
        },
        {
          "kind": "projectLibrary",
          "name": "alpha",
          "path": "lib/app.jar",
          "relativeOutputFile": "app.jar"
        }
      ]
    },
    {
      "descriptorModule": "p.main",
      "additionalFrontendOnlyPlugin": false,
      "entries": [
        {
          "kind": "module",
          "name": "p.content",
          "path": "plugins/p/lib/modules/p.content.jar",
          "relativeOutputFile": "modules/p.content.jar"
        },
        {
          "kind": "module",
          "name": "p.main",
          "path": "plugins/p/lib/p.jar",
          "relativeOutputFile": "p.jar"
        }
      ]
    }
  ]
}
"#;
    assert_eq!(std::fs::read_to_string(&output).unwrap(), expected);
}

#[test]
fn invalid_input() {
    let files = Files::new();
    let targets = files.write("bazel-targets.json", TEST_TARGETS);
    for (part, message) in [
        (
            r#"{"version":2,"descriptorModule":"p","directory":"","order":"layout","jars":[]}"#,
            "version 2",
        ),
        (
            r#"{"version":1,"descriptorModule":"p","order":"layout","jars":[]}"#,
            "missing field `directory`",
        ),
        (
            r#"{"version":1,"descriptorModule":"p","directory":"plugins/p","order":"plugin","jars":[]}"#,
            "names no descriptor",
        ),
        (
            r#"{"version":1,"descriptorModule":"p","directory":"","order":"layout","jars":[{"destination":"p.jar","members":[{"module":"p"}]}]}"#,
            "not a jar under lib/",
        ),
        (
            r#"{"version":1,"descriptorModule":"p","directory":"","order":"layout","jars":[{"destination":"lib/p.jar","members":[]}]}"#,
            "merges nothing",
        ),
        (
            r#"{"version":1,"descriptorModule":"p","directory":"","order":"layout","jars":[{"destination":"lib/p.jar","members":[{"module":"p","library":"@lib//:a"}]}]}"#,
            "one module or one library",
        ),
        (
            r#"{"version":1,"descriptorModule":"p","directory":"","order":"layout","extra":true,"jars":[]}"#,
            "unknown field",
        ),
        (
            r#"{"version":1,"descriptorModule":"p","directory":"","order":"sorted","jars":[]}"#,
            r#"has the order "sorted", but "plugin" or "layout" is expected"#,
        ),
    ] {
        let part_file = files.write("part.json", part);
        let args = [
            format!("--part={part_file}"),
            format!("--bazel-targets={targets}"),
            format!("--output={}", files.path("layout.json").display()),
        ];
        let result = run_tool(&args);
        assert!(
            result.code != 0 && result.errors.contains(message),
            "part {part}: exit {}, stderr {:?}, expected {message:?}",
            result.code,
            result.errors
        );
        assert!(result.errors.starts_with(&format!("ERROR: {part_file}: ")), "{}", result.errors);
    }
    for (args, message) in [
        (
            vec![format!("--bazel-targets={targets}"), "--output=out.json".to_owned()],
            "ERROR: --part is required\n",
        ),
        (
            vec!["--part=a.json".to_owned(), "--output=out.json".to_owned()],
            "ERROR: --bazel-targets is required\n",
        ),
        (
            vec![
                "--part=a.json".to_owned(),
                format!("--bazel-targets={targets}"),
                format!("--bazel-targets={targets}"),
                "--output=out.json".to_owned(),
            ],
            "ERROR: --bazel-targets must be specified at most once\n",
        ),
        (
            vec![
                format!("--bazel-targets={targets}"),
                "--output=out.json".to_owned(),
                "--unknown=x".to_owned(),
            ],
            "ERROR: unknown option: --unknown\n",
        ),
        (
            vec![
                format!("--bazel-targets={targets}"),
                "--output=out.json".to_owned(),
                "part.json".to_owned(),
            ],
            "ERROR: expected an option in the form --key=value, but got \"part.json\"\n",
        ),
        (vec!["--part=".to_owned()], "ERROR: --part must not be empty\n"),
        (vec!["--part".to_owned()], "ERROR: --part takes a value, as in --part=<value>\n"),
    ] {
        let result = run_tool(&args);
        assert_eq!((result.code, result.errors.as_str()), (1, message), "{args:?}");
    }
}

/// The content pass does not place a content module at a custom path, so its jar starts the layout pass. A reused jar
/// goes before it. An embedded content module is placed at `lib/<module>.jar`, and a frontend member at
/// `lib/<main>-frontend.jar`.
#[test]
fn custom_path_content_modules() {
    let part = plugin_part(
        "p.main",
        "plugins/p",
        vec![
            reused_jar("lib/modules/p.late.jar", modules(&["p.late"])),
            jar("lib/p.embedded.jar", modules(&["p.embedded"])),
            jar("lib/p-frontend.jar", modules(&["p.frontend"])),
            jar("lib/p.jar", modules(&["p.main", "p.shared"])),
            jar("lib/custom.jar", modules(&["p.custom"])),
        ],
    );
    let all = content(&[
        ("p.custom", ""),
        ("p.embedded", "embedded"),
        ("p.frontend", ""),
        ("p.shared", ""),
        ("p.late", ""),
    ]);
    let result = assemble_one(part, all);
    assert_eq!(
        entry_modules(&result.plugins[0]),
        ["p.embedded", "p.frontend", "p.shared", "p.main", "p.late", "p.custom"]
    );
}

/// A jar of project libraries starts the layout pass, so a reused jar goes before it. The generator makes each project
/// library a module of the plugin header, so this order is in the bytes of the repository. A jar of module libraries
/// does not end the content pass: the embedded content module after it keeps its place before the reused jar.
#[test]
fn project_library_jar_starts_the_layout_pass() {
    let part = plugin_part(
        "p.main",
        "plugins/p",
        vec![
            jar("lib/modules/p.content.jar", modules(&["p.content"])),
            jar("lib/b.jar", vec![library("@lib//:p-main-b", &[])]),
            jar("lib/p.embedded.jar", modules(&["p.embedded"])),
            jar("lib/alpha.jar", vec![library("@lib//:alpha", &[])]),
            reused_jar("lib/modules/p.reused.jar", modules(&["p.reused"])),
        ],
    );
    let result = assemble_one(part, content(&[("p.content", ""), ("p.embedded", "embedded"), ("p.reused", "")]));
    let actual: Vec<String> = result.plugins[0]
        .entries
        .iter()
        .map(|entry| format!("{}:{}", entry.kind, entry.name))
        .collect();
    assert_eq!(
        actual,
        [
            "module:p.content",
            "moduleLibrary:p.main",
            "module:p.embedded",
            "module:p.reused",
            "projectLibrary:alpha"
        ]
    );
}

/// A layout part keeps its part order: the platform part states its jars in the platform jar order. Inside a jar, the
/// modules keep their merge order.
#[test]
fn layout_part_keeps_its_part_order() {
    let part = layout_part(
        "intellij.idea.customization",
        vec![
            jar("lib/nio-fs.jar", modules(&["intellij.platform.core.nio.fs"])),
            jar("lib/util.jar", modules(&["intellij.platform.util.base", "intellij.platform.util"])),
            jar("lib/ext/platform-main.jar", modules(&["intellij.platform.main"])),
        ],
    );
    let result = assemble(&[in_part_order(part, false)], &test_index()).unwrap();
    assert_eq!(
        entry_modules(&result.plugins[0]),
        [
            "intellij.platform.core.nio.fs",
            "intellij.platform.util.base",
            "intellij.platform.util",
            "intellij.platform.main"
        ]
    );
    assert_eq!(result.plugins[0].entries[3].path.as_deref(), Some("lib/ext/platform-main.jar"));
}

fn plan_part_args(files: &Files, plan: &str, independent: &str, descriptor: &str, output: &str) -> Vec<String> {
    vec![
        "plan-part".to_owned(),
        format!("--plan={}", files.write("plan.json", plan)),
        format!("--catalogue={}", files.write("catalogue.json", TEST_CATALOGUE)),
        format!("--independent-libraries={}", files.write("independent.json", independent)),
        "--descriptor-module=p.main".to_owned(),
        "--plugin-directory=plugins/p".to_owned(),
        format!("--descriptor={descriptor}"),
        format!("--output={output}"),
    ]
}

#[test]
fn plan_part() {
    let files = Files::new();
    let output = files.path("part.json").display().to_string();
    let args = plan_part_args(
        &files,
        TEST_PLAN,
        r#"{"version": 1, "libraries": [{"library": "@@lib+//:natives", "jars": ["external/lib+/natives.jar"]}]}"#,
        "bazel-out/bin/p/plugin.classpath.xml",
        &output,
    );
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(result.output, format!("Wrote the layout part of p.main with 4 jars to {output}\n"));
    let data = std::fs::read_to_string(&output).unwrap();
    let actual: Part = serde_json::from_str(&data).unwrap();
    let mut natives = modules(&["p.natives"]);
    natives.push(library("@lib//:natives", &["external/lib+/natives.jar"]));
    let mut main = modules(&["p.main", "p.other"]);
    main.push(library("@lib//:a", &["external/lib+/a1.jar", "external/lib+/a2.jar"]));
    let expected = Part {
        version: PART_VERSION,
        descriptor_module: "p.main".to_owned(),
        directory: "plugins/p".to_owned(),
        order: PLUGIN_ORDER.to_owned(),
        descriptor: "bazel-out/bin/p/plugin.classpath.xml".to_owned(),
        jars: vec![
            jar("lib/modules/p.content.jar", modules(&["p.content"])),
            jar("lib/modules/p.natives.jar", natives),
            jar("lib/p.jar", main),
            jar("lib/a2.jar", vec![library("@lib//:a/a2.jar", &["external/lib+/a2.jar"])]),
        ],
    };
    assert_eq!(actual, expected);
    assert!(data.ends_with("}\n"), "the part does not end with a newline: {data:?}");
    // The compact form of the Go encoder, in the field order of the part.
    assert!(
        data.starts_with(r#"{"version":1,"descriptorModule":"p.main","directory":"plugins/p","order":"plugin","descriptor":"bazel-out/bin/p/plugin.classpath.xml","jars":[{"destination":"lib/modules/p.content.jar","members":[{"module":"p.content"}]}"#),
        "{data}"
    );
}

/// `--refused-module` names the content modules that the product mode of the chain refuses. The part leaves out the
/// jars that the packer omits for them: a jar whose every module is refused. A jar that merges a refused module with a
/// kept one stays, and a refused module that no jar merges fails.
#[test]
fn plan_part_leaves_out_the_jars_of_refused_modules() {
    let independent = r#"{"version": 1, "libraries": [{"library": "@lib//:natives", "jars": ["external/lib+/natives.jar"]}]}"#;
    let files = Files::new();
    let output = files.path("part.json").display().to_string();
    let mut args = plan_part_args(&files, TEST_PLAN, independent, "plugin.xml", &output);
    args.push("--refused-module=p.content".to_owned());
    args.push("--refused-module=p.other".to_owned());
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(result.output, format!("Wrote the layout part of p.main with 3 jars to {output}\n"));
    let actual: Part = serde_json::from_str(&std::fs::read_to_string(&output).unwrap()).unwrap();
    assert_eq!(
        actual.jars.iter().map(|jar| jar.destination.as_str()).collect::<Vec<_>>(),
        ["lib/modules/p.natives.jar", "lib/p.jar", "lib/a2.jar"]
    );
    let files = Files::new();
    let output = files.path("part.json").display().to_string();
    let mut args = plan_part_args(&files, TEST_PLAN, independent, "plugin.xml", &output);
    args.push("--refused-module=p.unknown".to_owned());
    let result = run_tool(&args);
    assert!(
        result.code != 0 && result.errors.contains(r#"refused module "p.unknown" matches no asset of the plan"#),
        "exit {}, stderr {:?}",
        result.code,
        result.errors
    );
}

/// The native tree of the reused natives jar is not a jar, so the part is the same with and without the tree.
#[test]
fn plan_part_skips_the_native_tree() {
    let independent = r#"{"version": 1, "libraries": [{"library": "@lib//:natives", "jars": ["external/lib+/natives.jar"]}]}"#;
    let native_tree = r#"{"destination": "lib/native", "inputs": ["native-tree:p.natives"], "kind": "tree", "classPath": false"#;
    let part = |plan: &str| {
        let files = Files::new();
        let output = files.path("part.json").display().to_string();
        let result = run_tool(&plan_part_args(&files, plan, independent, "plugin.xml", &output));
        assert_eq!(result.code, 0, "{}", result.errors);
        std::fs::read_to_string(&output).unwrap()
    };
    let expected = part(TEST_PLAN);
    let tree_anchor = r#"    {"destination": "js","#;
    let with_tree = TEST_PLAN.replacen(tree_anchor, &format!("    {native_tree}}},\n{tree_anchor}"), 1);
    assert_ne!(with_tree, TEST_PLAN);
    assert_eq!(part(&with_tree), expected, "a native tree");
}

#[test]
fn plan_part_refusals() {
    let independent = r#"{"version": 1, "libraries": [{"library": "@lib//:natives", "jars": ["external/lib+/natives.jar"]}]}"#;
    for (plan, message) in [
        (
            TEST_PLAN.replacen(r#""@lib//:a", "kind": "library""#, r#""@lib//:unknown", "kind": "library""#, 1),
            "neither the catalogue nor a reused jar",
        ),
        (
            TEST_PLAN.replacen(
                r#""@lib//:a/a2.jar", "kind": "archive""#,
                r#""@lib//:a/unknown.jar", "kind": "archive""#,
                1,
            ),
            "which the catalogue does not list",
        ),
        (
            TEST_PLAN.replacen(
                r#""@lib//:a/a2.jar", "kind": "archive""#,
                r#""@lib//:a/a2.jar", "kind": "unknown""#,
                1,
            ),
            // The plan file reader refuses the kind before the derivation reads it.
            r#"jar source "@lib//:a/a2.jar" has the kind "unknown""#,
        ),
        (
            TEST_PLAN.replacen(
                r#""layout-assets:0:output", "kind": "prepared""#,
                r#""other:0:output", "kind": "prepared""#,
                1,
            ),
            "which no layout-assets operation writes",
        ),
        (
            TEST_PLAN.replacen(
                r#""kind": "layout-assets", "inputs": [{"artifact": "p.main"}]"#,
                r#""kind": "module-filter", "inputs": [{"artifact": "p.main"}]"#,
                1,
            ),
            // The plan file reader refuses the retired kind by name.
            r#"operation "layout-assets:0" has the kind "module-filter"; the packer executes only layout-assets"#,
        ),
        (
            TEST_PLAN.replacen(
                r#"{"destination": "lib/p.jar","#,
                r#"{"scope": "distribution", "destination": "lib/p.jar","#,
                1,
            ),
            // The plan file reader refuses the retired scope as an unknown key.
            "unknown field `scope`",
        ),
        (
            TEST_PLAN.replacen(r#"{"destination": "lib/p.jar","#, r#"{"destination": "p.jar","#, 1),
            "not under lib/",
        ),
        (
            TEST_PLAN.replacen(
                r#"{"destination": "lib/p.jar","#,
                r#"{"kind": "tree", "destination": "lib/p.jar","#,
                1,
            ),
            "lib/p.jar has a jar recipe, but it is a tree asset, not a file",
        ),
        (
            TEST_PLAN.replacen(r#"{"destination": "lib/p.jar","#, r#"{"destination": "lib/p.zip","#, 1),
            "lib/p.zip has a jar recipe, but its name does not end in .jar",
        ),
    ] {
        let files = Files::new();
        let output = files.path("part.json").display().to_string();
        let result = run_tool(&plan_part_args(&files, &plan, independent, "plugin.xml", &output));
        assert!(
            result.code != 0 && result.errors.contains(message),
            "exit {}, stderr {:?}, expected {message:?}",
            result.code,
            result.errors
        );
    }
    let files = Files::new();
    let output = files.path("part.json").display().to_string();
    let result = run_tool(&plan_part_args(
        &files,
        TEST_PLAN,
        r#"{"version": 2, "libraries": []}"#,
        "plugin.xml",
        &output,
    ));
    assert!(result.errors.contains("has version 2, but 1 is expected"), "{}", result.errors);
    let result = run_tool(&["plan-part".to_owned(), "--plan=plan.json".to_owned()]);
    assert_eq!((result.code, result.errors.as_str()), (1, "ERROR: --catalogue is required\n"));
}
