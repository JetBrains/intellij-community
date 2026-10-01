use std::ffi::OsString;

use testkit::{WorkingDirectory, write_file};

use crate::test_support::*;
use crate::{Mode, parse_options};

fn parse(args: &[&str]) -> anyhow::Result<crate::Options<String>> {
    parse_options(args.iter().map(OsString::from).collect())
}

fn parse_with(base: &[String], extra: &[&str]) -> anyhow::Result<crate::Options<String>> {
    let args: Vec<&str> = base.iter().map(String::as_str).chain(extra.iter().copied()).collect();
    parse(&args)
}

#[track_caller]
fn require_error(result: anyhow::Result<crate::Options<String>>, message: &str) {
    match result {
        Ok(options) => panic!("accepted options {options:?}, expected {message:?}"),
        Err(error) => assert!(format!("{error:#}").contains(message), "error {error:#}, expected {message:?}"),
    }
}

const NEUTRAL_PLUGIN: [&str; 5] = [
    "--component-manifest=component.json",
    "--kind=plugins_json",
    "--platform-prefix=idea",
    "--plugin-component=spec.json",
    "--plugin-classpath-part=component.plugin-classpath-part",
];

#[test]
fn options_take_the_platform_that_the_rules_pass() {
    for (os, expected_os) in [("windows", "windows"), ("macos", "mac"), ("linux", "linux")] {
        for arch in ["x64", "aarch64"] {
            let mut args = base_args("--jars-file=jars.json");
            args[3] = format!("--os={os}");
            args[4] = format!("--arch={arch}");
            let options = parse_with(&args, &["--metadata-catalogue=catalogue.json", "--trace-file="]).unwrap();
            assert_eq!((options.os.as_str(), options.arch.as_str()), (expected_os, arch));
            assert_eq!(options.trace_file, None, "an empty value is an absent option");
        }
    }
    let options = parse(&[
        "--component-manifest=component.json",
        "--kind=files",
        "--platform-prefix=idea",
        "--files-file=files.json",
    ])
    .unwrap();
    assert!(!options.os.is_empty() && !options.arch.is_empty(), "no host platform: {options:?}");
}

#[test]
fn options_refuse_a_platform_spelling_that_no_rule_passes() {
    for os in ["WINDOWS", "win", "mac", "MAC", "Linux", "darwin"] {
        let mut args = base_args("--files-file=files.json");
        args[3] = format!("--os={os}");
        require_error(parse_with(&args, &[]), "unknown --os value");
    }
    for arch in ["X86_64", "amd64", "AArch64", "arm64"] {
        let mut args = base_args("--files-file=files.json");
        args[4] = format!("--arch={arch}");
        require_error(parse_with(&args, &[]), "unknown --arch value");
    }
}

#[test]
fn invalid_options() {
    let base = base_args("--files-file=files.json");
    for (extra, message) in [
        (&["--jars-file=jars.json"][..], "exactly one"),
        (&["--files-file=other"], "at most once"),
        (&["--kind="], "at most once"),
        (&["--unknown=value"], "unknown option"),
        (&["file"], "--key=value"),
        (&["-k"], "--key=value"),
        (&["--main-class=a", "--main-class=b"], "at most once"),
        (&["--main-class"], "takes a value"),
    ] {
        require_error(parse_with(&base, extra), message);
    }
    for index in 0..6 {
        let mut args = base.clone();
        let name = args[index].split('=').next().unwrap().to_owned();
        args[index] = if index < 3 || index == 5 {
            format!("{name}=")
        } else {
            format!("{name}=unknown")
        };
        assert!(parse_with(&args, &[]).is_err(), "accepted {args:?}");
    }
}

#[test]
fn every_mode_takes_only_the_options_its_rule_passes() {
    let jars = base_args("--jars-file=jars.json");
    require_error(parse_with(&jars, &[]), "require --metadata-catalogue");
    let files = base_args("--files-file=files.json");
    require_error(
        parse_with(&files, &["--metadata-catalogue=catalogue.json"]),
        "--files-file cannot use --metadata-catalogue",
    );
    require_error(parse_with(&files, &["--plugin-classpath-part=part"]), "required together");
    let plugin = base_args("--plugin-component=spec.json");
    require_error(parse_with(&plugin, &[]), "required together");
    let plugin_args = [plugin.as_slice(), &["--plugin-classpath-part=part".to_owned()]].concat();
    assert!(matches!(parse_with(&plugin_args, &[]).unwrap().mode, Mode::PluginComponent { .. }));
    require_error(
        parse_with(&plugin_args, &["--metadata-catalogue=catalogue.json"]),
        "cannot use --metadata-catalogue",
    );
    require_error(parse_with(&plugin_args, &["--main-class=Main"]), "cannot declare --main-class");
    require_error(parse_with(&plugin_args, &["--files-file=files.json"]), "exactly one");
}

#[test]
fn platform_neutral_options() {
    let options = parse(&[NEUTRAL_PLUGIN.as_slice(), &["--platform-neutral"]].concat()).unwrap();
    assert_eq!((options.os.as_str(), options.arch.as_str()), ("", ""));
    for extra in [
        &["--platform-neutral", "--os=linux"][..],
        &["--platform-neutral", "--arch=x64"],
        &["--platform-neutral", "--os=linux", "--arch=x64"],
    ] {
        require_error(parse(&[NEUTRAL_PLUGIN.as_slice(), extra].concat()), "cannot be combined");
    }
    for flag in ["--platform-neutral=true", "--platform-neutral=false"] {
        require_error(parse(&[NEUTRAL_PLUGIN.as_slice(), &[flag]].concat()), "takes no value");
    }
    let files = [
        "--component-manifest=component.json",
        "--kind=files",
        "--platform-prefix=idea",
        "--files-file=files.json",
        "--platform-neutral",
    ];
    require_error(parse(&files), "applies only to --plugin-component");
}

/// The collector checks the manifest path as an output. Bazel declares it in slash form, and an absolute form has
/// backslashes on Windows, so the parser keeps the declared value in every mode.
#[test]
fn options_keep_the_declared_manifest_path() {
    let base = [
        "--component-manifest=out/component.json",
        "--kind=files",
        "--platform-prefix=idea",
        "--files-file=files.json",
    ];
    for extra in [&["--os=linux", "--arch=x64"][..], &[]] {
        let options = parse(&[base.as_slice(), extra].concat()).unwrap();
        assert_eq!(options.manifest, "out/component.json");
    }
    let options = parse(
        &[
            &NEUTRAL_PLUGIN[1..],
            &["--component-manifest=out/component.json", "--platform-neutral"],
        ]
        .concat(),
    )
    .unwrap();
    assert_eq!(options.manifest, "out/component.json");
}

#[test]
fn file_mode_writes_the_manifest() {
    let _directory = WorkingDirectory::enter();
    write_file("inputs/ijent", "binary bytes");
    write_file(
        "files.json",
        r#"[{"source":"inputs/ijent","relativePath":"bin/ijent","executable":true}]"#,
    );
    let outcome = run_collector(&base_args("--files-file=files.json"));
    outcome.assert_success();
    let entries = manifest_entries("component.json");
    assert_eq!(entries.len(), 1);
    let entry = &entries["bin/ijent"];
    assert_eq!((&entry["executable"], &entry["source"]), (&true.into(), &"inputs/ijent".into()));
    assert_eq!(
        outcome.output,
        "Dev distribution component 'files' named 1 files in component.json\n"
    );
    assert_eq!(outcome.errors, "");
    let names: Vec<_> = std::fs::read_dir(".").unwrap().map(|entry| entry.unwrap().file_name()).collect();
    assert_eq!(names.len(), 3, "unexpected outputs: {names:?}");
}

#[test]
fn a_failed_run_writes_the_trace_without_the_manifest() {
    let _directory = WorkingDirectory::enter();
    write_file(
        "files.json",
        r#"[{"source":"missing","relativePath":"bin/ijent","executable":true}]"#,
    );
    let mut args = base_args("--files-file=files.json");
    args.push("--trace-file=trace.json".into());
    let outcome = run_collector(&args);
    outcome.assert_error("missing");
    assert!(!exists("component.json"), "a failed action wrote a manifest");
    let spans = read_spans("trace.json");
    assert_eq!(span_name(&spans[0]), "collect files");
    assert_eq!(span_tag(&spans[0], "error"), Some("true"));
    assert_eq!(outcome.output, "");
}

#[test]
fn an_unwritable_trace_fails_the_run() {
    let _directory = WorkingDirectory::enter();
    write_file("files.json", "[]");
    write_file("not-a-directory", "bytes");
    let mut args = base_args("--files-file=files.json");
    args.push("--trace-file=not-a-directory/trace.json".into());
    run_collector(&args).assert_error("writing the span file");
}

#[test]
fn the_trace_names_the_spans_of_the_jar_mode() {
    let _directory = WorkingDirectory::enter();
    write_file("payload/a.jar", "packed bytes");
    let entry = filemeta::inspect(std::path::Path::new("payload/a.jar"), "a.jar").unwrap();
    std::fs::remove_file("payload/a.jar").unwrap();
    write_inventory("a.metadata.json", &[entry]);
    write_file(
        "catalogue.json",
        r#"[{"source":"payload/a.jar","metadata":"a.metadata.json","relativePath":"a.jar"}]"#,
    );
    write_jar_records("jars.json", &["payload/a.jar"]);
    let mut args = base_args("--jars-file=jars.json");
    args.extend(["--metadata-catalogue=catalogue.json".into(), "--trace-file=trace.json".into()]);
    let outcome = run_collector(&args);
    outcome.assert_success();
    assert_eq!(
        outcome.output,
        "Dev distribution component 'files' named 1 packed jars in component.json\n"
    );
    let spans = read_spans("trace.json");
    let names: Vec<&str> = spans.iter().map(span_name).collect();
    assert_eq!(
        names,
        ["collect packed jars", "collect platform jars", "inventory dev build component"]
    );
    assert_eq!(span_tag(&spans[0], "kind"), Some("files"));
    assert_eq!(span_tag(&spans[1], "jarCount"), Some("1"));
    assert_eq!(span_tag(&spans[2], "fileCount"), Some("1"));
    for span in &spans[1..] {
        assert_eq!(span_tag(span, "byteCount"), Some("0"), "payload bytes were read: {span}");
    }
}

#[test]
fn the_manifest_of_a_platform_component_names_os_and_arch() {
    let _directory = WorkingDirectory::enter();
    write_file("inputs/shared.jar", "jar bytes");
    write_file(
        "files.json",
        r#"[{"source":"inputs/shared.jar","relativePath":"lib/shared.jar","executable":false}]"#,
    );
    let mut args = base_args("--files-file=files.json");
    args.push("--main-class=com.intellij.idea.Main".into());
    run_collector(&args).assert_success();
    let manifest = read_json("component.json");
    assert_eq!((&manifest["os"], &manifest["arch"]), (&"linux".into(), &"x64".into()));
    assert_eq!(manifest["mainClass"], "com.intellij.idea.Main");
}
