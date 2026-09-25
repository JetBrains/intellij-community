// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::ffi::OsString;
use std::path::PathBuf;

use serde_json::Value;

use crate::model::{JvmArguments, LaunchModel, parse_launch_model};
use crate::render::{
    OS_LINUX, OS_MAC, OS_WINDOWS, Platform, additional_jvm_arguments, opened_packages, parse_platform, render_launch_files,
};
use crate::run;

const HOST_PLATFORMS: [&str; 6] = [
    "darwin_aarch64",
    "darwin_x64",
    "linux_aarch64",
    "linux_x64",
    "windows_aarch64",
    "windows_x64",
];

/// The `testdata` directory: `DDT_TESTDATA_DIR` under Bazel, the crate directory under `cargo test`.
fn testdata(name: &str) -> PathBuf {
    let directory = std::env::var_os("DDT_TESTDATA_DIR").map_or_else(
        || {
            let crate_directory = std::env::var_os("CARGO_MANIFEST_DIR").expect("cargo test sets CARGO_MANIFEST_DIR");
            PathBuf::from(crate_directory).join("testdata")
        },
        PathBuf::from,
    );
    directory.join(name)
}

fn read_testdata(name: &str) -> String {
    let file = testdata(name);
    std::fs::read_to_string(&file).unwrap_or_else(|error| panic!("{}: {error}", file.display()))
}

fn test_model(name: &str) -> LaunchModel {
    parse_launch_model(read_testdata(name).as_bytes()).unwrap()
}

fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

#[test]
fn quote_escapes_like_kotlinx() {
    for (input, expected) in [
        ("plain $APP_PACKAGE/x", r#""plain $APP_PACKAGE/x""#),
        ("a\"b", r#""a\"b""#),
        ("%IDE_HOME%\\lib", r#""%IDE_HOME%\\lib""#),
        ("tab\tnew\nline", r#""tab\tnew\nline""#),
        ("\u{8}\u{c}\r", r#""\b\f\r""#),
        ("\u{1}", r#""\u0001""#),
        ("\u{1f}\u{7f}", "\"\\u001f\u{7f}\""),
        ("ünïcode", r#""ünïcode""#),
    ] {
        assert_eq!(serde_json::to_string_pretty(input).unwrap(), expected, "{input:?}");
    }
}

#[test]
fn opened_packages_split_like_files_lines() {
    let cases: [(&str, &[&str]); 4] = [
        ("a\nb\n", &["a", "b"]),
        ("a\r\nb", &["a", "b"]),
        ("a\n\nb", &["a", "", "b"]),
        ("", &[]),
    ];
    for (input, expected) in cases {
        assert_eq!(opened_packages(input, OS_LINUX).unwrap(), expected, "{input:?}");
    }
    let error = opened_packages("a\rb\n", OS_LINUX).unwrap_err();
    assert_eq!(
        error.to_string(),
        r#"the opened packages line "a\rb" has a carriage return that is not before a line feed"#
    );
}

#[test]
fn opened_packages_drop_the_packages_of_other_systems() {
    let text = read_testdata("opened-packages.txt");
    let common = "--add-opens=java.base/java.lang=ALL-UNNAMED";
    for (os, expected) in [
        (
            OS_MAC,
            [
                common,
                "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED",
            ]
            .as_slice(),
        ),
        (
            OS_LINUX,
            [
                common,
                "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
                "--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED",
            ]
            .as_slice(),
        ),
        (
            OS_WINDOWS,
            [common, "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED"].as_slice(),
        ),
    ] {
        assert_eq!(opened_packages(&text, os).unwrap(), strings(expected), "{os}");
    }
}

#[test]
fn jvm_arguments_follow_the_system_conventions() {
    let jvm = JvmArguments {
        multi_routing_file_system: true,
        class_loader: Some("com.intellij.util.lang.PathClassLoader".to_owned()),
        vendor_name: "JetBrains".to_owned(),
        paths_selector: "IntelliJIdea2026.3".to_owned(),
        jna: true,
        native_access: true,
        ..JvmArguments::default()
    };
    let windows = additional_jvm_arguments(
        &jvm,
        Platform {
            os: OS_WINDOWS,
            arch: "amd64",
        },
        &strings(&["--add-opens=x"]),
        false,
    );
    let expected = strings(&[
        r"-Xbootclasspath/a:%IDE_HOME%\lib\nio-fs.jar",
        "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
        "-Didea.vendor.name=JetBrains",
        "-Didea.paths.selector=IntelliJIdea2026.3",
        "-Djna.boot.library.path=%IDE_HOME%/lib/jna/amd64",
        "-Djna.nosys=true",
        "-Djna.noclasspath=true",
        "-Dio.netty.allocator.type=pooled",
        "-Daether.connector.resumeDownloads=false",
        "-Dcompose.swing.render.on.graphics=true",
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=x",
    ]);
    assert_eq!(windows, expected);
    let qodana = additional_jvm_arguments(
        &jvm,
        Platform {
            os: OS_MAC,
            arch: "aarch64",
        },
        &[],
        true,
    );
    assert!(
        !qodana[0].starts_with("-Xbootclasspath"),
        "a Qodana launch must not load the multi-routing file system, got {}",
        qodana[0]
    );
}

#[test]
fn rendered_files_of_one_model() {
    let model = test_model("model.json");
    let files = render_launch_files(
        &model,
        Platform {
            os: OS_LINUX,
            arch: "aarch64",
        },
        "--add-opens=java.base/java.lang=ALL-UNNAMED\n",
        "a=@@settings_dir@@\n",
    )
    .unwrap();
    assert_eq!(files.build_txt, "IU-263.SNAPSHOT");
    assert_eq!(files.idea_properties, "a=IntelliJIdea\n\nb=1#end\n");
    assert_eq!(files.vm_options, "-Xmx2048m\n-Dx=linux\n");
    let info = &files.product_info;
    assert!(
        !info.ends_with('\n'),
        "product-info.json must not end with a newline, as kotlinx writes it"
    );
    serde_json::from_str::<Value>(info).unwrap_or_else(|error| panic!("product-info.json is not JSON: {error}\n{info}"));
    assert!(
        info.starts_with("{\n  \"name\": \"IntelliJ IDEA\",\n  \"version\": \"2026.3\",\n  \"versionSuffix\": \"EAP\",\n"),
        "product-info.json does not start with the fields in declaration order:\n{info}"
    );
    for fragment in [
        r#""startupWmClass": "jetbrains-idea""#,
        r#""launcherPath": "bin/idea""#,
        r#""vmOptionsFilePath": "bin/idea64.vmoptions""#,
        "\"commands\": [\n            \"stdioMcpServer\"",
    ] {
        assert!(info.contains(fragment), "product-info.json lacks {fragment}:\n{info}");
    }
    assert!(
        !info.contains("customProperties") && !info.contains("flavors"),
        "product-info.json states an empty default list:\n{info}"
    );
}

/// The goldens are the bytes that the Go tool wrote for each test model on each host platform.
/// `DevDistProductLaunchModelTest` checked the Go tool against the production writers.
#[test]
fn product_info_matches_the_recorded_bytes_on_every_platform() {
    let opened = read_testdata("opened-packages.txt");
    for (model_name, golden_dir) in [("model.json", "product-info"), ("full-model.json", "full-product-info")] {
        let model = test_model(model_name);
        for platform in HOST_PLATFORMS {
            let files = render_launch_files(&model, parse_platform(platform).unwrap(), &opened, "a=@@settings_dir@@\n").unwrap();
            let golden = read_testdata(&format!("{golden_dir}/{platform}.json"));
            // An editor can add a final newline to the golden. `rendered_files_of_one_model` pins that there is none.
            let expected = golden.strip_suffix('\n').unwrap_or(&golden);
            assert_eq!(files.product_info, expected, "{model_name} on {platform}");
        }
    }
}

#[test]
fn full_model_renders_the_other_three_files() {
    let model = test_model("full-model.json");
    let windows = render_launch_files(&model, parse_platform("windows_x64").unwrap(), "", "a=@@settings_dir@@\n").unwrap();
    assert_eq!(windows.build_txt, "IIS-263.1234");
    assert_eq!(windows.vm_options, "-Xmx4g\r\n-Dwindows=\"1\"\r\n");
    assert_eq!(windows.idea_properties, "a=IntelliJServer\n\nx=IntelliJServer/x\n");
    let linux = render_launch_files(&model, parse_platform("linux_x64").unwrap(), "", "").unwrap();
    assert_eq!(linux.vm_options, "");
}

#[test]
fn render_refusals() {
    let mut model = test_model("model.json");
    model.vm_options.get_mut(OS_MAC).unwrap().push("-Dname=ü".to_owned());
    let error = render_launch_files(&model, parse_platform("darwin_x64").unwrap(), "", "").unwrap_err();
    assert_eq!(error.to_string(), r#"the vmoptions line "-Dname=ü" is not ASCII"#);
    model.vm_options.get_mut(OS_MAC).unwrap().pop();
    model.vm_options.remove(OS_LINUX);
    let error = render_launch_files(&model, parse_platform("linux_x64").unwrap(), "", "").unwrap_err();
    assert_eq!(error.to_string(), "the model states no vmoptions for Linux");
    model.major_version_release_date = "2026010".to_owned();
    let error = render_launch_files(&model, parse_platform("darwin_x64").unwrap(), "", "").unwrap_err();
    assert_eq!(error.to_string(), r#"the major release date "2026010" is not yyyyMMdd"#);
    for name in ["darwin", "darwin_x86", "macos_x64", "linux-x64", ""] {
        let error = parse_platform(name).unwrap_err();
        assert_eq!(error.to_string(), format!("{name:?} is not a host platform such as darwin_aarch64"));
    }
    // The dev-dist models set none of these fields, so the model has no field for them.
    for (field, name) in [
        (r#""jbr17": true"#, "jbr17"),
        (
            r#""launch": {"jvmArguments": {"xBootClassPathJarNames": ["a.jar"]}}"#,
            "xBootClassPathJarNames",
        ),
        (
            r#""launch": {"jvmArguments": {"cdsArchiveFileName": "idea.jsa"}}"#,
            "cdsArchiveFileName",
        ),
        (r#""unknown": 1"#, "unknown"),
    ] {
        let error = parse_launch_model(format!(r#"{{"productCode": "IU", {field}}}"#).as_bytes()).unwrap_err();
        assert!(
            error
                .to_string()
                .starts_with(&format!("cannot read the launch model: unknown field `{name}`")),
            "{error}"
        );
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

#[test]
fn the_tool_writes_the_four_files() {
    let dir = tempfile::tempdir().unwrap();
    let write = |name: &str, content: &str| {
        let path = dir.path().join(name);
        std::fs::write(&path, content).unwrap();
        path.display().to_string()
    };
    let out = |name: &str| dir.path().join(name).display().to_string();
    let model = write("model.json", &read_testdata("model.json"));
    let args = vec![
        format!("--model={model}"),
        "--platform=darwin_aarch64".to_owned(),
        format!(
            "--opened-packages={}",
            write("opened.txt", "--add-opens=java.base/java.lang=ALL-UNNAMED\n")
        ),
        format!("--idea-properties={}", write("idea.properties", "a=@@settings_dir@@\n")),
        format!("--build-txt-out={}", out("build.txt")),
        format!("--idea-properties-out={}", out("out.properties")),
        format!("--vmoptions-out={}", out("out.vmoptions")),
        format!("--product-info-out={}", out("product-info.json")),
    ];
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(result.output, format!("Rendered the launch files of {model} for darwin_aarch64\n"));
    assert_eq!(std::fs::read_to_string(out("out.vmoptions")).unwrap(), "-Xmx2048m\n-Dx=mac\n");
    assert_eq!(std::fs::read_to_string(out("build.txt")).unwrap(), "IU-263.SNAPSHOT");
    assert_eq!(
        std::fs::read_to_string(out("out.properties")).unwrap(),
        "a=IntelliJIdea\n\nb=1#end\n"
    );

    let result = run_tool(&args[..1]);
    assert_eq!(result.code, 2, "a missing option must fail with exit code 2");
    assert_eq!(result.errors, "ERROR: --platform is required\n");

    let mut broken = args;
    broken[1] = "--platform=solaris_x64".to_owned();
    let result = run_tool(&broken);
    assert_eq!(result.code, 1);
    assert_eq!(
        result.errors,
        "ERROR: \"solaris_x64\" is not a host platform such as darwin_aarch64\n"
    );
}

#[test]
fn options_refuse_every_other_form() {
    for (args, expected) in [
        (
            vec!["--unknown=x"],
            r#"ERROR: expected one of the options in the '--key=value' form, but got "--unknown=x""#,
        ),
        (
            vec!["--model"],
            r#"ERROR: expected one of the options in the '--key=value' form, but got "--model""#,
        ),
        (
            vec!["model.json"],
            r#"ERROR: expected one of the options in the '--key=value' form, but got "model.json""#,
        ),
        (
            vec!["-m"],
            r#"ERROR: expected one of the options in the '--key=value' form, but got "-m""#,
        ),
        (vec!["--model=a", "--model=b"], "ERROR: --model must be specified at most once"),
        (vec!["--model="], "ERROR: --model is required"),
        (vec![], "ERROR: --model is required"),
    ] {
        let result = run_tool(&strings(&args));
        assert_eq!(
            (result.code, result.errors.as_str()),
            (2, format!("{expected}\n").as_str()),
            "{args:?}"
        );
    }
}
