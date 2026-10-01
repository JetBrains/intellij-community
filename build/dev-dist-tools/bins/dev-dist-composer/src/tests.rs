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

/// Writes two components, the sources that their manifests name, and the plugin classpath inputs. The paths are
/// relative to the working directory, as Bazel gives them to an action.
///
/// A full distribution gets the source bindings file that the Starlark rule writes. Launch metadata gets the source
/// runfiles instead.
fn write_cli_fixture(local_launch: bool) {
    write_file("fragments/core/lib/app.jar", "app");
    write_file("fragments/core/lib/util.jar", "util");
    write_file("fragments/core/bin/idea.properties", "properties");
    write_file("inputs/packed.jar", "packed");
    write_file("fragments/prefix.bin", [3, 1]);
    write_file("fragments/plugins.part", [7]);
    write_file(
        "fragments/core.json",
        concat!(
            r#"{"version":10,"kind":"platform_core","platformPrefix":"idea","os":"linux","arch":"x64","plugin":false,"#,
            r#""mainClass":"com.intellij.idea.Main","coreClassPath":["lib/util.jar","lib/app.jar"],"entries":["#,
            r#"{"type":"component-file","relativePath":"bin/idea.properties","hash":1,"source":"fragments/core/bin/idea.properties"},"#,
            r#"{"type":"component-file","relativePath":"lib/app.jar","hash":4,"source":"fragments/core/lib/app.jar"},"#,
            r#"{"type":"component-file","relativePath":"lib/util.jar","hash":2,"source":"fragments/core/lib/util.jar"}]}"#
        ),
    );
    write_file(
        "fragments/plugins.json",
        concat!(
            r#"{"version":10,"kind":"plugins","platformPrefix":"idea","os":"","arch":"","plugin":true,"#,
            r#""mainClass":null,"coreClassPath":[],"entries":["#,
            r#"{"type":"component-file","relativePath":"plugins/packed/lib/packed.jar","hash":3,"source":"inputs/packed.jar"}]}"#
        ),
    );
    let bindings = [
        ("fragments/core.json", "fragments/core/bin/idea.properties"),
        ("fragments/core.json", "fragments/core/lib/app.jar"),
        ("fragments/core.json", "fragments/core/lib/util.jar"),
        ("fragments/plugins.json", "inputs/packed.jar"),
    ]
    .map(|(component, source)| binding_line(component, source, source, "file", &[]))
    .join("\n");
    write_file("dist.source-bindings.jsonl", bindings);
    let (source_runfiles, source_bindings) = if local_launch {
        (
            concat!(
                r#"{"fragments/core/bin/idea.properties":"_main/fragments/core/bin/idea.properties","#,
                r#""fragments/core/lib/app.jar":"_main/fragments/core/lib/app.jar","#,
                r#""fragments/core/lib/util.jar":"_main/fragments/core/lib/util.jar","inputs/packed.jar":"_main/inputs/packed.jar"}"#
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
                r#"{{"version":1,"expectedFragments":["platform_core","plugins"],"#,
                r#""additionalModules":["intellij.packed","intellij.bundled"],"components":["#,
                r#"{{"manifest":"fragments/core.json","pluginClasspathPart":null}},"#,
                r#"{{"manifest":"fragments/plugins.json","pluginClasspathPart":"fragments/plugins.part"}}],"#,
                r#""pluginClasspathPrefix":"fragments/prefix.bin","sourceRunfiles":{},"sourceDirectoryRunfiles":{{}},"sourceBindings":{}}}"#
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
    let (code, errors) = run_cli(&cli_args(&["--trace-file=out/spans.json"]));
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
            "home.path=dist\nmain.class.name=com.intellij.idea.Main\nplatform.prefix=idea\nadditional.modules=intellij.packed,intellij.bundled\n",
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
                "merge dev build component",
                vec![("kind", "platform_core"), ("fileCount", "3"), ("byteCount", "17")]
            ),
            (
                "merge dev build component",
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
    for fragment in [
        r#""runfile":"_main/fragments/core/lib/util.jar""#,
        r#""runfile":"_main/inputs/packed.jar""#,
        r#""metadata":["core-classpath.txt","fingerprint.txt","plugins/plugin-classpath.txt"]"#,
    ] {
        assert!(layout.contains(fragment), "the local layout has no {fragment}: {layout}");
    }
    assert_eq!(read_text("out/dist/plugins/plugin-classpath.txt"), "\x03\x01\x00\x01\x07");
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

// Only the composer reads a manifest, so a manifest of another version is a stale input. The composer names its version
// before any other key of the old shape.
#[test]
fn compose_cli_refuses_a_version_9_manifest() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    write_file(
        "fragments/plugins.json",
        concat!(
            r#"{"version":9,"kind":"plugins","platformPrefix":"idea","os":"","arch":"","additionalModules":[],"#,
            r#""mainClass":null,"coreClassPath":[],"pluginCount":1,"entries":["#,
            r#"{"relativePath":"plugins/packed/lib/packed.jar","type":"component-file","hash":3,"source":"inputs/packed.jar"}]}"#
        ),
    );
    let (code, errors) = run_cli(&cli_args(&[]));
    assert_eq!(code, 1);
    let message = format!(
        "{}: Unsupported dev-build component manifest version 9\n",
        component::paths::from_slash("fragments/plugins.json")
    );
    assert!(errors.starts_with("ERROR: ") && errors.ends_with(&message), "errors = {errors:?}");
    require_absent("out/dist");
}

// The reader of a manifest is the one place that checks the entry modes, and the composer reads every manifest before
// it removes the output of an earlier run.
#[test]
fn compose_cli_refuses_an_invalid_manifest_before_it_removes_the_output() {
    let _directory = WorkingDirectory::enter();
    write_cli_fixture(false);
    write_file("out/dist/stale.txt", "stale");
    let core = read_text("fragments/core.json").replace(r#""hash":1,"#, r#""hash":1,"mode":493,"#);
    write_file("fragments/core.json", core);
    let (code, errors) = run_cli(&cli_args(&[]));
    assert_eq!(code, 1);
    let message = format!(
        "{}: Dev-build component entry 'bin/idea.properties' has an invalid or conflicting file mode: 493\n",
        component::paths::from_slash("fragments/core.json")
    );
    assert!(errors.starts_with("ERROR: ") && errors.ends_with(&message), "errors = {errors:?}");
    assert_eq!(read_text("out/dist/stale.txt"), "stale");
}
