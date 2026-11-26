// The tests of the command line. Each test runs in its own working directory, because Bazel gives the action paths
// relative to the execution root.

use std::ffi::OsString;

use testkit::{WorkingDirectory, read_text, require_absent, write_file};

use crate::test_support::*;

fn cli_args(extra: &[&str]) -> Vec<String> {
    let mut args: Vec<String> = [
        "--composition-spec=composition.json",
        "--output-dir=out/dist",
        "--ide-config=out/dist.ide.config",
        "--fingerprint=out/dist.fingerprint",
    ]
    .map(str::to_owned)
    .to_vec();
    args.extend(extra.iter().map(|arg| (*arg).to_owned()));
    args
}

/// [`cli_args`] of a full distribution, which also names the list of the Mach-O files.
fn full_cli_args(extra: &[&str]) -> Vec<String> {
    let mut args = cli_args(&["--mac-binaries=out/dist.mac-binaries.json"]);
    args.extend(extra.iter().map(|arg| (*arg).to_owned()));
    args
}

/// Writes two components, the sources that their manifests name, and the plugin classpath inputs. The paths are
/// relative to the working directory, as Bazel gives them to an action.
///
/// A full distribution gets the source bindings file that the Starlark rule writes. Launch metadata gets the source
/// runfiles instead.
fn write_cli_fixture(local_launch: bool) {
    write_file("components/core/lib/app.jar", "app");
    write_file("components/core/lib/util.jar", "util");
    write_file("components/core/bin/idea.properties", "properties");
    write_file("inputs/packed.jar", "packed");
    write_file("components/prefix.bin", [3, 1]);
    write_file("components/plugins.part", [7]);
    write_file(
        "components/core.json",
        concat!(
            r#"{"kind":"platform_core","os":"linux","arch":"x64","plugin":false,"#,
            r#""mainClass":"com.intellij.idea.Main","coreClassPath":["lib/util.jar","lib/app.jar"],"entries":["#,
            r#"{"type":"component-file","relativePath":"bin/idea.properties","hash":1,"source":"components/core/bin/idea.properties"},"#,
            r#"{"type":"component-file","relativePath":"lib/app.jar","hash":4,"source":"components/core/lib/app.jar"},"#,
            r#"{"type":"component-file","relativePath":"lib/util.jar","hash":2,"source":"components/core/lib/util.jar"}]}"#
        ),
    );
    write_file(
        "components/plugins.json",
        concat!(
            r#"{"kind":"plugins","os":"","arch":"","plugin":true,"#,
            r#""mainClass":null,"coreClassPath":[],"entries":["#,
            r#"{"type":"component-file","relativePath":"plugins/packed/lib/packed.jar","hash":3,"source":"inputs/packed.jar"}]}"#
        ),
    );
    let bindings = [
        ("components/core.json", "components/core/bin/idea.properties"),
        ("components/core.json", "components/core/lib/app.jar"),
        ("components/core.json", "components/core/lib/util.jar"),
        ("components/plugins.json", "inputs/packed.jar"),
    ]
    .map(|(component, source)| binding_line(component, source, source, "file", &[]))
    .join("\n");
    write_file("dist.source-bindings.jsonl", bindings);
    let (source_runfiles, source_bindings) = if local_launch {
        (
            concat!(
                r#"{"components/core/bin/idea.properties":"_main/components/core/bin/idea.properties","#,
                r#""components/core/lib/app.jar":"_main/components/core/lib/app.jar","#,
                r#""components/core/lib/util.jar":"_main/components/core/lib/util.jar","inputs/packed.jar":"_main/inputs/packed.jar"}"#
            ),
            "null",
        )
    } else {
        ("null", r#""dist.source-bindings.jsonl""#)
    };
    write_file(
        "composition.json",
        format!(
            concat!(
                r#"{{"platformPrefix":"idea","expectedComponents":["platform_core","plugins"],"#,
                r#""additionalModules":["intellij.packed","intellij.bundled"],"bundledModules":["plugins"],"components":["#,
                r#"{{"manifest":"components/core.json","pluginClasspathPart":null}},"#,
                r#"{{"manifest":"components/plugins.json","pluginClasspathPart":"components/plugins.part"}}],"#,
                r#""pluginClasspathPrefix":"components/prefix.bin","sourceRunfiles":{},"sourceDirectoryRunfiles":{{}},"sourceBindings":{}}}"#
            ),
            source_runfiles, source_bindings
        ),
    );
}

fn run_cli(args: &[String]) -> (u8, String) {
    let mut errors = Vec::new();
    let code = crate::run(args.iter().map(OsString::from), &mut errors);
    (code, String::from_utf8(errors).expect("UTF-8 errors"))
}

#[test]
fn compose_cli_writes_the_distribution_and_its_launch_files() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    write_file("out/dist/stale.txt", "stale");
    let (code, errors) = run_cli(&full_cli_args(&["--trace-file=out/spans.json"]));
    assert_eq!(code, 0, "{errors}");
    require_absent("out/dist/stale.txt");
    let fingerprint = read_text("out/dist.fingerprint");
    assert!(fingerprint.starts_with("v5:"), "fingerprint = {fingerprint:?}");
    for (name, content) in [
        ("out/dist/lib/app.jar", "app"),
        ("out/dist/lib/util.jar", "util"),
        ("out/dist/bin/idea.properties", "properties"),
        ("out/dist/plugins/packed/lib/packed.jar", "packed"),
        ("out/dist/plugins/plugin-classpath.txt", "\x03\x01\x00\x01\x07"),
        ("out/dist/core-classpath.txt", "lib/util.jar\nlib/app.jar"),
        (
            "out/dist.ide.config",
            "home.path=dist\nmain.class.name=com.intellij.idea.Main\nplatform.prefix=idea\nadditional.modules=intellij.packed,intellij.bundled\nbundled.modules=plugins\n",
        ),
        ("out/dist/fingerprint.txt", fingerprint.as_str()),
    ] {
        assert_eq!(read_text(name), content, "{name}");
    }

    let trace: serde_json::Value = serde_json::from_str(&read_text("out/spans.json")).expect("the trace");
    let data = trace["data"].as_array().expect("the data");
    assert_eq!(data.len(), 1);
    let spans: Vec<(&str, Vec<(&str, &str)>)> = data[0]["spans"]
        .as_array()
        .expect("the spans")
        .iter()
        .map(|span| {
            let tags = span["tags"].as_array().map_or_else(Vec::new, |tags| {
                tags.iter()
                    .map(|tag| (tag["key"].as_str().unwrap(), tag["value"].as_str().unwrap()))
                    .collect()
            });
            (span["operationName"].as_str().unwrap(), tags)
        })
        .collect();
    assert_eq!(
        spans,
        [
            (crate::JOB_NAME, vec![("componentCount", "2")]),
            (
                "merge build component",
                vec![("kind", "platform_core"), ("fileCount", "3"), ("byteCount", "17")]
            ),
            (
                "merge build component",
                vec![("kind", "plugins"), ("fileCount", "1"), ("byteCount", "6")]
            ),
        ]
    );
}

#[test]
fn compose_cli_writes_local_launch_metadata_without_payload() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(true);
    let (code, errors) = run_cli(&cli_args(&[]));
    assert_eq!(code, 0, "{errors}");
    require_absent("out/dist/lib");
    let layout = read_text("out/dist/local-layout.json");
    for part in [
        r#""runfile":"_main/components/core/lib/util.jar""#,
        r#""runfile":"_main/inputs/packed.jar""#,
        r#""metadata":["core-classpath.txt","fingerprint.txt","plugins/plugin-classpath.txt"]"#,
    ] {
        assert!(layout.contains(part), "the local layout has no {part}: {layout}");
    }
    assert_eq!(read_text("out/dist/plugins/plugin-classpath.txt"), "\x03\x01\x00\x01\x07");
}

/// The children of `directory`, sorted by name.
fn children(directory: &str) -> Vec<String> {
    let mut names: Vec<String> = std::fs::read_dir(directory)
        .unwrap_or_else(|error| panic!("{directory}: {error}"))
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .collect();
    names.sort();
    names
}

// A macOS app has its home in `Contents`. The home holds the same files as a flat home, so the fingerprint stays.
#[test]
fn compose_cli_writes_the_distribution_below_the_home_prefix() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    let (code, errors) = run_cli(&full_cli_args(&[]));
    assert_eq!(code, 0, "{errors}");
    let flat_fingerprint = read_text("out/dist.fingerprint");

    let (code, errors) = run_cli(&full_cli_args(&["--home-prefix=Contents"]));
    assert_eq!(code, 0, "{errors}");
    assert_eq!(children("out/dist"), ["Contents"]);
    assert_eq!(read_text("out/dist.fingerprint"), flat_fingerprint);
    for (name, content) in [
        ("out/dist/Contents/lib/app.jar", "app"),
        ("out/dist/Contents/bin/idea.properties", "properties"),
        ("out/dist/Contents/plugins/packed/lib/packed.jar", "packed"),
        ("out/dist/Contents/plugins/plugin-classpath.txt", "\x03\x01\x00\x01\x07"),
        ("out/dist/Contents/core-classpath.txt", "lib/util.jar\nlib/app.jar"),
        ("out/dist/Contents/fingerprint.txt", flat_fingerprint.as_str()),
        (
            "out/dist.ide.config",
            "home.path=dist/Contents\nmain.class.name=com.intellij.idea.Main\nplatform.prefix=idea\nadditional.modules=intellij.packed,intellij.bundled\nbundled.modules=plugins\n",
        ),
    ] {
        assert_eq!(read_text(name), content, "{name}");
    }
}

#[test]
fn compose_cli_writes_local_launch_metadata_below_the_home_prefix() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(true);
    let (code, errors) = run_cli(&cli_args(&[
        "--home-prefix=Contents",
        "--core-classpath-file=out/dist.core-classpath.txt",
        "--plugin-classpath-file=out/dist.plugin-classpath.txt",
    ]));
    assert_eq!(code, 0, "{errors}");
    assert_eq!(children("out/dist"), ["Contents"]);
    assert_eq!(
        children("out/dist/Contents"),
        ["core-classpath.txt", "fingerprint.txt", "local-layout.json", "plugins"]
    );
    assert_eq!(read_text("out/dist.core-classpath.txt"), "lib/util.jar\nlib/app.jar");
    assert_eq!(read_text("out/dist.plugin-classpath.txt"), "\x03\x01\x00\x01\x07");
    assert!(read_text("out/dist.ide.config").starts_with("home.path=dist/Contents\n"));
}

// The check of the launch class path reads `product-info.json` below the home prefix.
#[test]
fn compose_cli_checks_the_product_info_below_the_home_prefix() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    let core = read_text("components/core.json").replace(
        r#""entries":["#,
        r#""entries":[{"type":"component-file","relativePath":"Resources/product-info.json","hash":5,"source":"components/core/product-info.json"},"#,
    );
    write_file("components/core.json", core);
    let binding = binding_line(
        "components/core.json",
        "components/core/product-info.json",
        "components/core/product-info.json",
        "file",
        &[],
    );
    write_file(
        "dist.source-bindings.jsonl",
        format!("{binding}\n{}", read_text("dist.source-bindings.jsonl")),
    );
    let launch = |names: &str| format!(r#"{{"launch":[{{"mainClass":"com.intellij.idea.Main","bootClassPathJarNames":[{names}]}}]}}"#);

    write_file("components/core/product-info.json", launch(r#""app.jar""#));
    let (code, errors) = run_cli(&full_cli_args(&["--home-prefix=Contents"]));
    assert_eq!(code, 1);
    assert!(
        errors.contains(r#"Resources/product-info.json: the bootClassPathJarNames of the main launch differ from the core classpath; missing: ["util.jar"]"#),
        "errors = {errors:?}"
    );

    write_file("components/core/product-info.json", launch(r#""app.jar","util.jar""#));
    let (code, errors) = run_cli(&full_cli_args(&["--home-prefix=Contents"]));
    assert_eq!(code, 0, "{errors}");
    assert!(read_text("out/dist/Contents/Resources/product-info.json").contains("util.jar"));
}

#[test]
fn compose_cli_rejects_invalid_options() {
    let directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    let spec = |args: &[&str]| args.iter().map(|arg| (*arg).to_owned()).collect::<Vec<_>>();
    for (args, message) in [
        (
            cli_args(&["composition.json"]),
            "ERROR: expected an option in the form --key=value, but got \"composition.json\"",
        ),
        // The Starlark caller writes every option with a value.
        (
            spec(&["--composition-spec"]),
            "ERROR: --composition-spec takes a value, as in --composition-spec=<value>",
        ),
        (cli_args(&["--alpha"]), "ERROR: unknown option: --alpha"),
        (
            cli_args(&["--trace-file=a", "--trace-file=b"]),
            "ERROR: --trace-file must be specified at most once",
        ),
        (cli_args(&["--zeta=1", "--alpha=2"]), "ERROR: unknown options: --alpha, --zeta"),
        (
            spec(&["--composition-spec=composition.json", "--output-dir="]),
            "ERROR: --output-dir is required",
        ),
        (spec(&["--output-dir=out"]), "ERROR: --composition-spec is required"),
        (spec(&["--composition-spec=absent.json"]), "absent.json"),
        (
            cli_args(&["--home-prefix=Contents/MacOS"]),
            "ERROR: --home-prefix must be one directory name, not \"Contents/MacOS\"",
        ),
        (
            cli_args(&["--home-prefix=.."]),
            "ERROR: --home-prefix must be one directory name, not \"..\"",
        ),
    ] {
        let (code, errors) = run_cli(&args);
        assert_eq!(code, 1, "{args:?}: {errors}");
        assert!(errors.contains(message), "{args:?}: errors = {errors:?}");
    }
    // A bare `--` is not the end of the options, and the other options do not change the error.
    for args in [cli_args(&["--"]), cli_args(&["--", "--zeta=1"])] {
        assert_eq!(
            run_cli(&args),
            (1, "ERROR: expected an option in the form --key=value, but got \"--\"\n".to_owned()),
            "{args:?}"
        );
    }
    require_absent(directory.path().join("out"));
}

#[test]
fn compose_cli_rejects_source_bindings_for_local_launch_metadata() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(true);
    let spec = read_text("composition.json").replace(r#""sourceBindings":null"#, r#""sourceBindings":"dist.source-bindings.jsonl""#);
    write_file("composition.json", spec);
    let (code, errors) = run_cli(&cli_args(&[]));
    assert_eq!(code, 1);
    assert!(
        errors.contains("ERROR: Local launch metadata must not expand source bindings"),
        "errors = {errors:?}"
    );
}

// The Starlark caller always binds the sources of a full distribution.
#[test]
fn compose_cli_rejects_a_full_distribution_without_source_bindings() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    let spec = read_text("composition.json").replace(r#""sourceBindings":"dist.source-bindings.jsonl""#, r#""sourceBindings":null"#);
    write_file("composition.json", spec);
    let (code, errors) = run_cli(&cli_args(&["--trace-file=out/spans.json"]));
    assert_eq!(code, 1);
    assert!(
        errors.contains("requests a full distribution without source bindings"),
        "errors = {errors:?}"
    );
    require_absent("out/dist");
    // The span file names the failure.
    let trace = read_text("out/spans.json");
    assert!(trace.contains(r#""key":"error.message""#), "trace = {trace}");
}

// The reader of a manifest is the one place that checks the entry modes, and the composer reads every manifest before
// it removes the output of an earlier run.
#[test]
fn compose_cli_refuses_an_invalid_manifest_before_it_removes_the_output() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    write_file("out/dist/stale.txt", "stale");
    let core = read_text("components/core.json").replace(r#""hash":1,"#, r#""hash":1,"mode":493,"#);
    write_file("components/core.json", core);
    let (code, errors) = run_cli(&full_cli_args(&[]));
    assert_eq!(code, 1);
    let message = format!(
        "{}: Dev-build component entry 'bin/idea.properties' has an invalid or conflicting file mode: 493\n",
        component::paths::from_slash("components/core.json")
    );
    assert!(errors.starts_with("ERROR: ") && errors.ends_with(&message), "errors = {errors:?}");
    assert_eq!(read_text("out/dist/stale.txt"), "stale");
}

#[test]
fn compose_cli_writes_the_mac_binaries_of_a_full_distribution() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    let (code, errors) = run_cli(&cli_args(&[]));
    assert_eq!((code, errors.as_str()), (1, "ERROR: A full distribution requires --mac-binaries\n"));
    let (code, errors) = run_cli(&full_cli_args(&[]));
    assert_eq!(code, 0, "{errors}");
    // The fixture is a Linux composition, so the list is empty.
    assert_eq!(read_text("out/dist.mac-binaries.json"), "{\n  \"version\": 1,\n  \"files\": []\n}");
}

#[test]
fn compose_cli_refuses_the_mac_binaries_for_local_launch_metadata() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(true);
    let (code, errors) = run_cli(&full_cli_args(&[]));
    assert_eq!(
        (code, errors.as_str()),
        (1, "ERROR: Local launch metadata must not name --mac-binaries\n")
    );
}

#[test]
fn compose_cli_writes_only_the_executable_list_of_a_context_plugin_pool() {
    let _directory = WorkingDirectory::enter();
    write_file(
        "components/tool.json",
        concat!(
            r#"{"kind":"tool","os":"","arch":"","plugin":true,"#,
            r#""mainClass":null,"coreClassPath":[],"entries":["#,
            r#"{"type":"directory","relativePath":"plugins/tool/bin","mode":493},"#,
            r#"{"type":"component-file","relativePath":"plugins/tool/bin/tool","hash":3,"executable":true,"source":"inputs/tool"},"#,
            r#"{"type":"component-file","relativePath":"plugins/tool/lib/tool.jar","hash":4,"source":"inputs/tool.jar"}]}"#
        ),
    );
    let args = [
        "--context-plugin-executables=pool.context-plugin-executables.txt",
        "--component-manifest=components/tool.json",
    ]
    .map(str::to_owned);
    let (code, errors) = run_cli(&args);
    assert_eq!(code, 0, "{errors}");
    assert_eq!(read_text("pool.context-plugin-executables.txt"), "tool/bin/tool\n");
    require_absent("out/dist");

    let mixed = [args[0].clone(), args[1].clone(), "--output-dir=out/dist".to_owned()];
    let (code, errors) = run_cli(&mixed);
    assert_eq!((code, errors.as_str()), (1, "ERROR: unknown option: --output-dir\n"));
}
