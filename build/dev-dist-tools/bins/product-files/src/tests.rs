// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::HashMap;

use appinfo::{ApplicationInfo, Replacement};
use serde_json::Value;
use testkit::{Run, read_text, testdata};

use crate::model::{JvmArguments, JvmArgumentsRef, LaunchModel, VmOptions, parse_launch_model};
use crate::render::{
    LaunchFiles, LaunchNames, OS_LINUX, OS_MAC, OS_WINDOWS, Platform, Product, additional_jvm_arguments, common_vm_options,
    insert_eap_vm_options, launch_boot_class_path, opened_packages, parse_platform, render_launch_files,
};
use crate::run;

/// `DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS`: 2026-01-01T00:00:00Z.
const PINNED_BUILD_DATE_SECONDS: i64 = 1_767_225_600;

const HOST_PLATFORMS: [&str; 6] = [
    "darwin_aarch64",
    "darwin_x64",
    "linux_aarch64",
    "linux_x64",
    "windows_aarch64",
    "windows_x64",
];

fn test_model(name: &str) -> LaunchModel {
    parse_launch_model(read_text(testdata(name)).as_bytes()).unwrap()
}

/// The core classpath of the test distribution, in the sorted order of the payload. It holds `nio-fs.jar`.
const CORE_CLASS_PATH_FILE: &str = "core-classpath.txt";

fn test_core_class_path() -> Vec<String> {
    read_text(testdata(CORE_CLASS_PATH_FILE)).lines().map(str::to_owned).collect()
}

/// One test product: a launch model, its application info, the markers of that application info and its build number.
struct Fixture {
    model: &'static str,
    application_info: &'static str,
    replacements: &'static [(&'static str, &'static str)],
    build_number: &'static str,
}

/// A plain EAP product without a release date.
const IDEA: Fixture = Fixture {
    model: "model.json",
    application_info: "idea",
    replacements: &[],
    build_number: "build.txt",
};

/// A release product with a release date.
const RELEASE: Fixture = Fixture {
    model: "model.json",
    application_info: "release",
    replacements: &[],
    build_number: "build.txt",
};

/// The fatal error block of an EAP build, as `ideaPropertiesFatalErrorNotification` writes it.
const EAP_BLOCK: &str = "\n#-----------------------------------------------------------------------\n\
# Change to 'disabled' if you don't want to receive instant visual notifications\n\
# about fatal errors that happen to an IDE or plugins installed.\n\
#-----------------------------------------------------------------------\n\
idea.fatal.error.notification=enabled\n";

/// The fatal error block of a release build.
const RELEASE_BLOCK: &str = "\n#-----------------------------------------------------------------------\n\
# Change to 'enabled' if you want to receive instant visual notifications\n\
# about fatal errors that happen to an IDE or plugins installed.\n\
#-----------------------------------------------------------------------\n\
idea.fatal.error.notification=disabled\n";

/// The line that an EAP build inserts into the vmoptions.
const EAP_LINE: &str = "-XX:MaxJavaStackTraceDepth=10000";

/// A language server whose markers state the EAP flag and the product name.
const SERVER: Fixture = Fixture {
    model: "full-model.json",
    application_info: "server",
    replacements: &[("BUNDLE_EAP", "false"), ("BUNDLE_NAME", "Server")],
    build_number: "server-build.txt",
};

impl Fixture {
    fn application_info_file(&self) -> String {
        testdata(&format!("application-info/{}.xml", self.application_info))
            .display()
            .to_string()
    }

    fn read_application_info(&self) -> ApplicationInfo {
        let replacements: Vec<Replacement> = self.replacements.iter().map(|(key, value)| Replacement::new(key, value)).collect();
        let file = testdata(&format!("application-info/{}.xml", self.application_info));
        ApplicationInfo::load(&file, &replacements, None, PINNED_BUILD_DATE_SECONDS).unwrap_or_else(|error| panic!("{error:#}"))
    }

    fn build_number(&self) -> String {
        read_text(testdata(self.build_number)).trim().to_owned()
    }

    fn render(&self, model: &LaunchModel, platform: &str, opened: &str, idea_properties: &str) -> anyhow::Result<LaunchFiles> {
        self.render_with_common(model, platform, opened, idea_properties, "")
    }

    /// Renders with `common` as the text of `bin/common.vmoptions`.
    fn render_with_common(
        &self,
        model: &LaunchModel,
        platform: &str,
        opened: &str,
        idea_properties: &str,
        common: &str,
    ) -> anyhow::Result<LaunchFiles> {
        let application_info = self.read_application_info();
        let build_number = self.build_number();
        // A classic-loader launch reads the core classpath, as the action passes it for such a model only.
        let core_class_path = test_core_class_path();
        let product = Product {
            model,
            application_info: &application_info,
            build_number: &build_number,
            core_class_path: model
                .launch
                .boot_class_path_jar_names
                .is_none()
                .then_some(core_class_path.as_slice()),
            dev_jvm_arguments: &[],
        };
        render_launch_files(&product, parse_platform(platform)?, opened, idea_properties, common)
    }

    /// The options of the tool, without the model and the outputs.
    fn inputs(&self) -> Vec<String> {
        let mut args = vec![
            format!("--application-info={}", self.application_info_file()),
            format!("--build-number={}", testdata(self.build_number).display()),
            format!("--build-date-seconds={PINNED_BUILD_DATE_SECONDS}"),
        ];
        args.extend(self.replacements.iter().map(|(key, value)| format!("--replacement={key}={value}")));
        args
    }
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
    let text = read_text(testdata("opened-packages.txt"));
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
    // The defaults turn on the multi-routing file system and the native access, and name the path class loader.
    let jvm = JvmArguments {
        jna_native_dir: Some("plugins/jna-plugin/lib/jna".to_owned()),
        ..JvmArguments::default()
    };
    let names = LaunchNames {
        vendor_name: "JetBrains",
        data_directory_name: "IntelliJIdea2026.3",
    };
    let windows = additional_jvm_arguments(
        &jvm,
        &names,
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
        "-Djna.boot.library.path=%IDE_HOME%/plugins/jna-plugin/lib/jna/amd64",
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
        &names,
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
    let model = test_model(IDEA.model);
    let files = IDEA
        .render(
            &model,
            "linux_aarch64",
            "--add-opens=java.base/java.lang=ALL-UNNAMED\n",
            "a=@@settings_dir@@\n",
        )
        .unwrap();
    assert_eq!(files.build_txt, "IU-263.SNAPSHOT");
    // The fixture is EAP and states no `-ea`, so the EAP line goes before the first `-D` line.
    assert_eq!(files.idea_properties, format!("a=IntelliJIdea\n\nb=1{EAP_BLOCK}"));
    assert_eq!(files.vm_options, format!("-Xmx2048m\n{EAP_LINE}\n-Dx=linux\n"));
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

/// The goldens hold the bytes of each test model on each host platform. `DevDistProductLaunchModelTest` checks the tool
/// against the production writers.
#[test]
fn product_info_matches_the_recorded_bytes_on_every_platform() {
    let opened = read_text(testdata("opened-packages.txt"));
    for (fixture, golden_dir) in [(IDEA, "product-info"), (SERVER, "full-product-info")] {
        let model_name = fixture.model;
        let model = test_model(model_name);
        for platform in HOST_PLATFORMS {
            let files = fixture.render(&model, platform, &opened, "a=@@settings_dir@@\n").unwrap();
            let golden = read_text(testdata(&format!("{golden_dir}/{platform}.json")));
            // An editor can add a final newline to the golden. `rendered_files_of_one_model` pins that there is none.
            let expected = golden.strip_suffix('\n').unwrap_or(&golden);
            assert_eq!(files.product_info, expected, "{model_name} on {platform}");
        }
    }
}

#[test]
fn full_model_renders_the_other_three_files() {
    // The server is a release build and a language server, so it gets no EAP line and no fatal error block.
    let model = test_model(SERVER.model);
    let windows = SERVER.render(&model, "windows_x64", "", "a=@@settings_dir@@\n").unwrap();
    assert_eq!(windows.build_txt, "IIS-263.1234");
    assert_eq!(windows.vm_options, "-Xmx4g\r\n-Dwindows=\"1\"\r\n-Djava.awt.headless=true\r\n");
    assert_eq!(windows.idea_properties, "a=IntelliJServer\n\nx=IntelliJServer/x\n");
    // The model states no Linux lines, so the file has the memory lines and the headless line.
    let linux = SERVER.render(&model, "linux_x64", "", "").unwrap();
    assert_eq!(linux.vm_options, "-Xmx4g\n-Djava.awt.headless=true\n");
}

/// The vmoptions file is the memory lines, the common lines, the product lines, then the lines of the OS.
#[test]
fn vm_options_compose_the_common_file_and_the_model() {
    let common = "# A note.\n\n-XX:+Common\n  \n-ea\n-Dsun.java2d.metal=true\n";
    assert_eq!(
        common_vm_options(common).collect::<Vec<_>>(),
        strings(&["-XX:+Common", "-ea", "-Dsun.java2d.metal=true"])
    );
    let mut model = test_model(RELEASE.model);
    model.vm_options.product = strings(&["-Dproduct=1"]);
    let mac = RELEASE.render_with_common(&model, "darwin_aarch64", "", "", common).unwrap();
    assert_eq!(
        mac.vm_options,
        "-Xmx2048m\n-XX:+Common\n-ea\n-Dsun.java2d.metal=true\n-Dproduct=1\n-Dx=mac\n"
    );
    // The model states no Windows lines.
    let windows = RELEASE.render_with_common(&model, "windows_x64", "", "", common).unwrap();
    assert_eq!(
        windows.vm_options,
        "-Xmx2048m\r\n-XX:+Common\r\n-ea\r\n-Dsun.java2d.metal=true\r\n-Dproduct=1\r\n"
    );
}

/// A language server drops the lines of a desktop toolkit after the EAP line goes in, then adds the headless line.
#[test]
fn language_server_vm_options_are_headless() {
    let mut model = test_model(IDEA.model);
    model.language_server = true;
    model.vm_options.product = strings(&["-Dawt.lock.fair=true", "-Dswing.x=1", "-Dskiko.y=2", "-Dkept=1"]);
    let files = IDEA
        .render_with_common(&model, "linux_x64", "", "", "-ea\n-Dsun.java2d.metal=true\n")
        .unwrap();
    assert_eq!(
        files.vm_options,
        format!("-Xmx2048m\n{EAP_LINE}\n-ea\n-Dkept=1\n-Dx=linux\n-Djava.awt.headless=true\n")
    );
}

/// The vmoptions file of the embedded frontend has its own parts, the common lines and the EAP flag of the product.
/// The headless rule of a language server applies to the file of the product only.
#[test]
fn frontend_vm_options_follow_the_pipeline_of_the_product() {
    let mut model = test_model(IDEA.model);
    let common = "-XX:+Common\n";
    let plain = IDEA.render_with_common(&model, "linux_x64", "", "", common).unwrap();
    assert_eq!(
        plain.frontend_vm_options, None,
        "a model without frontendVmOptions renders no frontend file"
    );
    model.frontend_vm_options = Some(VmOptions {
        memory: strings(&["-Xms256m", "-Xmx1024m"]),
        product: strings(&["-Dfrontend=1", "-Dawt.x=1"]),
        os: HashMap::from([(OS_MAC.to_owned(), strings(&["-Dmac=frontend"]))]),
    });
    let mac = IDEA.render_with_common(&model, "darwin_aarch64", "", "", common).unwrap();
    assert_eq!(mac.vm_options, format!("-Xmx2048m\n-XX:+Common\n{EAP_LINE}\n-Dx=mac\n"));
    assert_eq!(
        mac.frontend_vm_options,
        Some(format!(
            "-Xms256m\n-Xmx1024m\n-XX:+Common\n{EAP_LINE}\n-Dfrontend=1\n-Dawt.x=1\n-Dmac=frontend\n"
        ))
    );
    // A release product gets no EAP line, and the model states no Windows lines of the frontend.
    let windows = RELEASE.render_with_common(&model, "windows_x64", "", "", common).unwrap();
    assert_eq!(
        windows.frontend_vm_options.as_deref(),
        Some("-Xms256m\r\n-Xmx1024m\r\n-XX:+Common\r\n-Dfrontend=1\r\n-Dawt.x=1\r\n")
    );
    model.language_server = true;
    let server = RELEASE.render_with_common(&model, "linux_x64", "", "", common).unwrap();
    assert_eq!(server.vm_options, "-Xmx2048m\n-XX:+Common\n-Dx=linux\n-Djava.awt.headless=true\n");
    assert_eq!(
        server.frontend_vm_options.as_deref(),
        Some("-Xms256m\n-Xmx1024m\n-XX:+Common\n-Dfrontend=1\n-Dawt.x=1\n")
    );
}

/// A classic-loader launch takes the core classpath in the production order. The multi-routing file system puts
/// `nio-fs.jar` on `-Xbootclasspath/a:`, so the list drops it then.
#[test]
fn launch_boot_class_path_follows_the_production_order() {
    fn product<'a>(model: &'a LaunchModel, application_info: &'a ApplicationInfo, core: Option<&'a [String]>) -> Product<'a> {
        Product {
            model,
            application_info,
            build_number: "263.SNAPSHOT",
            core_class_path: core,
            dev_jvm_arguments: &[],
        }
    }
    let mut model = test_model(IDEA.model);
    let application_info = IDEA.read_application_info();
    let core = strings(&[
        "b.jar",
        "util.jar",
        "nio-fs.jar",
        "a.jar",
        "platform-loader.jar",
        "a.jar",
        "util-8.jar",
    ]);
    assert_eq!(
        launch_boot_class_path(&product(&model, &application_info, Some(&core))).unwrap(),
        strings(&["platform-loader.jar", "util-8.jar", "util.jar", "a.jar", "b.jar"])
    );

    model.launch.jvm_arguments.multi_routing_file_system = false;
    assert_eq!(
        launch_boot_class_path(&product(&model, &application_info, Some(&core))).unwrap(),
        strings(&["platform-loader.jar", "util-8.jar", "util.jar", "a.jar", "b.jar", "nio-fs.jar"])
    );

    // A modular-loader launch states its list and reads no core classpath.
    model.launch.boot_class_path_jar_names = Some(strings(&["platform-loader.jar"]));
    assert_eq!(
        launch_boot_class_path(&product(&model, &application_info, None)).unwrap(),
        strings(&["platform-loader.jar"])
    );
    let error = launch_boot_class_path(&product(&model, &application_info, Some(&core))).unwrap_err();
    assert_eq!(
        error.to_string(),
        "the launch states bootClassPathJarNames, so it reads no core classpath"
    );
    model.launch.boot_class_path_jar_names = None;
    let error = launch_boot_class_path(&product(&model, &application_info, None)).unwrap_err();
    assert_eq!(
        error.to_string(),
        "the launch states no bootClassPathJarNames, so it needs the core classpath"
    );
}

/// A command with additional jars takes the boot class path of the launch first. A frontend command keeps its own list.
#[test]
fn custom_commands_extend_the_boot_class_path_of_the_launch() {
    let mut model = test_model(IDEA.model);
    let files = IDEA.render(&model, "linux_x64", "", "").unwrap();
    let info: Value = serde_json::from_str(&files.product_info).unwrap();
    let launch = strings(&["platform-loader.jar", "util-8.jar", "util.jar", "product-backend.jar", "app.jar"]);
    assert_eq!(info["launch"][0]["bootClassPathJarNames"], serde_json::json!(launch));
    let mut stdio = launch;
    stdio.push("../plugins/mcpserver-plugin/lib/intellij.mcpserver.stdio.jar".to_owned());
    assert_eq!(
        info["launch"][0]["customCommands"][0]["bootClassPathJarNames"],
        serde_json::json!(stdio)
    );

    // A Qodana launch adds `nio-fs.jar` after the list, which has no `nio-fs.jar` with the multi-routing file system.
    let server = test_model(SERVER.model);
    let files = SERVER.render(&server, "linux_x64", "", "").unwrap();
    let info: Value = serde_json::from_str(&files.product_info).unwrap();
    assert_eq!(
        info["launch"][0]["customCommands"][0]["bootClassPathJarNames"],
        serde_json::json!(["platform-loader.jar", "nio-fs.jar"])
    );

    model.custom_commands[0].boot_class_path_jar_names = strings(&["own.jar"]);
    let error = IDEA.render(&model, "linux_x64", "", "").unwrap_err();
    assert_eq!(
        error.to_string(),
        r#"the custom command ["stdioMcpServer"] states both bootClassPathJarNames and additionalBootClassPathJarNames"#
    );
    model.custom_commands[0].additional_boot_class_path_jar_names.clear();
    let files = IDEA.render(&model, "linux_x64", "", "").unwrap();
    let info: Value = serde_json::from_str(&files.product_info).unwrap();
    assert_eq!(
        info["launch"][0]["customCommands"][0]["bootClassPathJarNames"],
        serde_json::json!(["own.jar"])
    );
}

/// The JVM arguments of a JSON array at `pointer`, or none when the field is absent.
fn json_strings(info: &Value, pointer: &str) -> Vec<String> {
    info.pointer(pointer)
        .and_then(Value::as_array)
        .map(|values| values.iter().map(|value| value.as_str().unwrap().to_owned()).collect())
        .unwrap_or_default()
}

/// The dev JVM arguments go after the list of the launch and of each custom command with a list of its own. The
/// launcher gives a command with an empty list the list of the launch, so that list stays empty.
#[test]
fn dev_jvm_arguments_follow_each_list_of_its_own() {
    let model = test_model(SERVER.model);
    let application_info = SERVER.read_application_info();
    let build_number = SERVER.build_number();
    let dev = strings(&["-Didea.use.dev.build.server=true", "-Dsecond=1"]);
    let render = |dev_jvm_arguments: &[String]| -> Value {
        let product = Product {
            model: &model,
            application_info: &application_info,
            build_number: &build_number,
            core_class_path: None,
            dev_jvm_arguments,
        };
        let files = render_launch_files(&product, parse_platform("linux_x64").unwrap(), "", "", "").unwrap();
        serde_json::from_str(&files.product_info).unwrap()
    };
    let plain = render(&[]);
    let marked = render(&dev);
    for pointer in [
        "/launch/0/additionalJvmArguments",
        "/launch/0/customCommands/0/additionalJvmArguments",
    ] {
        let mut expected = json_strings(&plain, pointer);
        assert!(!expected.is_empty(), "the fixture states a list at {pointer}");
        expected.extend(dev.iter().cloned());
        assert_eq!(json_strings(&marked, pointer), expected, "{pointer}");
    }
    assert_eq!(
        marked["launch"][0]["customCommands"][1]["additionalJvmArguments"],
        Value::Null,
        "a command with an empty list takes the list of the launch in the launcher"
    );
}

/// The tool appends each `--dev-jvm-argument` in its order, and refuses an empty one.
#[test]
fn the_tool_appends_the_dev_jvm_arguments() {
    let tool = ToolRun::new();
    let mut args = tool.base_args();
    args.extend(IDEA.inputs());
    args.extend(strings(&[
        "--dev-jvm-argument=-Didea.use.dev.build.server=true",
        "--dev-jvm-argument=-Dx=1",
    ]));
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    let info = tool.product_info();
    let launch = json_strings(&info, "/launch/0/additionalJvmArguments");
    assert_eq!(launch[launch.len() - 2..], strings(&["-Didea.use.dev.build.server=true", "-Dx=1"]));
    // `stdioMcpServer` states no JVM arguments, so it takes the list of the launch in the launcher.
    assert_eq!(info["launch"][0]["customCommands"][0]["additionalJvmArguments"], Value::Null);

    args.push("--dev-jvm-argument=".to_owned());
    let result = run_tool(&args);
    assert_eq!(
        (result.code, result.errors.as_str()),
        (2, "ERROR: --dev-jvm-argument must not be empty\n")
    );
}

/// A custom command renders the block it names, and the build number replaces the token in its extra arguments.
#[test]
fn custom_commands_resolve_the_jvm_arguments_block() {
    let mut model = test_model(SERVER.model);
    let qodana = &mut model.custom_commands[0];
    qodana.jvm_arguments = Some(JvmArgumentsRef::Launch);
    qodana.extra_jvm_arguments = strings(&["-Dqodana.build.number=QDJVM-@@build_number@@"]);
    let files = SERVER.render(&model, "linux_x64", "", "").unwrap();
    let info: Value = serde_json::from_str(&files.product_info).unwrap();
    let command = &info["launch"][0]["customCommands"][0];
    let arguments: Vec<&str> = command["additionalJvmArguments"]
        .as_array()
        .unwrap()
        .iter()
        .map(|argument| argument.as_str().unwrap())
        .collect();
    assert_eq!(arguments[0], "-Djava.system.class.loader=com.example.ServerClassLoader");
    assert!(
        arguments.contains(&"-Dintellij.platform.root.module=intellij.server.main"),
        "{arguments:?}"
    );
    assert_eq!(arguments.last(), Some(&"-Dqodana.build.number=QDJVM-263.1234"));
    assert_eq!(command["dataDirectoryName"], "IntelliJServer2026.3");
    // A command without an environment variable name states no data directory name.
    assert_eq!(info["launch"][0]["customCommands"][1]["dataDirectoryName"], Value::Null);

    model.frontend_jvm_arguments = None;
    model.custom_commands[0].jvm_arguments = Some(JvmArgumentsRef::Frontend);
    let error = SERVER.render(&model, "linux_x64", "", "").unwrap_err();
    assert_eq!(
        error.to_string(),
        r#"the custom command ["qodana", "inspect"] names the frontend JVM arguments, but the model states no frontendJvmArguments"#
    );
}

/// The EAP flag of the application info decides the vmoptions line and the fatal error block. The model states neither.
#[test]
fn eap_parts_follow_the_application_info() {
    let mut model = test_model(IDEA.model);
    model
        .vm_options
        .os
        .insert(OS_MAC.to_owned(), strings(&["-XX:+A", "-ea", "-Dx=mac"]));
    let eap = IDEA.render(&model, "darwin_aarch64", "", "a=@@settings_dir@@\n").unwrap();
    assert_eq!(eap.vm_options, format!("-Xmx2048m\n-XX:+A\n{EAP_LINE}\n-ea\n-Dx=mac\n"));
    assert_eq!(eap.idea_properties, format!("a=IntelliJIdea\n\nb=1{EAP_BLOCK}"));

    let release = RELEASE.render(&model, "darwin_aarch64", "", "a=@@settings_dir@@\n").unwrap();
    assert_eq!(release.vm_options, "-Xmx2048m\n-XX:+A\n-ea\n-Dx=mac\n");
    assert_eq!(release.idea_properties, format!("a=IntelliJIdea\n\nb=1{RELEASE_BLOCK}"));
    let info: Value = serde_json::from_str(&release.product_info).unwrap();
    assert_eq!(
        (&info["versionSuffix"], &info["majorVersionReleaseDate"]),
        (&Value::Null, &Value::from("20261201"))
    );

    // A model without the flag gets no block, whatever the application info states.
    model.idea_properties.fatal_error_notification = false;
    let eap = IDEA.render(&model, "darwin_aarch64", "", "a=@@settings_dir@@\n").unwrap();
    assert_eq!(eap.idea_properties, "a=IntelliJIdea\n\nb=1");

    // With neither `-ea` nor a `-D` line, the EAP line goes to the end.
    let mut lines = strings(&["-Xmx2048m", "-XX:+A"]);
    insert_eap_vm_options(&mut lines);
    assert_eq!(lines, strings(&["-Xmx2048m", "-XX:+A", EAP_LINE]));
}

#[test]
fn render_refusals() {
    let model = test_model(IDEA.model);
    let error = IDEA.render_with_common(&model, "darwin_x64", "", "", "-Dname=ü\n").unwrap_err();
    assert_eq!(error.to_string(), r#"the vmoptions line "-Dname=ü" is not ASCII"#);
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
        // The action derives these from the application info and `build.txt`.
        (r#""buildNumber": "263.SNAPSHOT""#, "buildNumber"),
        (r#""productName": "IntelliJ IDEA""#, "productName"),
        (r#""version": "2026.3""#, "version"),
        (r#""versionSuffix": "EAP""#, "versionSuffix"),
        (r#""svgIcon": true"#, "svgIcon"),
        (r#""productVendor": "JetBrains""#, "productVendor"),
        (r#""majorVersionReleaseDate": "20260101""#, "majorVersionReleaseDate"),
        (r#""launch": {"linuxStartupWmClass": "jetbrains-idea"}"#, "linuxStartupWmClass"),
        // The EAP flag of the application info decides the fatal error block.
        (r#""ideaProperties": {"suffix": "x"}"#, "suffix"),
        // The action appends the version to the base name and reads the vendor from the application info.
        (r#""dataDirectoryName": "IntelliJIdea2026.3""#, "dataDirectoryName"),
        (
            r#""launch": {"jvmArguments": {"pathsSelector": "IntelliJIdea2026.3"}}"#,
            "pathsSelector",
        ),
        (r#""launch": {"jvmArguments": {"vendorName": "JetBrains"}}"#, "vendorName"),
        (r#""ideaProperties": {"settingsDir": "IntelliJIdea"}"#, "settingsDir"),
        (
            r#""customCommands": [{"commands": [], "dataDirectoryName": "IntelliJIdea2026.3"}]"#,
            "dataDirectoryName",
        ),
        // The model states the common vmoptions lines once, in a file of their own, not once per OS.
        (r#""vmOptions": {"macOS": ["-Xmx2048m"]}"#, "macOS"),
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

fn run_tool(args: &[String]) -> Run {
    testkit::run_tool(run, args)
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
    let model = write("model.json", &read_text(testdata("model.json")));
    let mut args = vec![
        format!("--model={model}"),
        "--platform=darwin_aarch64".to_owned(),
        format!(
            "--opened-packages={}",
            write("opened.txt", "--add-opens=java.base/java.lang=ALL-UNNAMED\n")
        ),
        format!("--idea-properties={}", write("idea.properties", "a=@@settings_dir@@\n")),
        format!("--common-vmoptions={}", write("common.vmoptions", "# A note.\n-XX:+Common\n")),
        format!("--build-txt-out={}", out("build.txt")),
        format!("--idea-properties-out={}", out("out.properties")),
        format!("--vmoptions-out={}", out("out.vmoptions")),
        format!("--product-info-out={}", out("product-info.json")),
        format!("--core-classpath-jar-names={}", testdata(CORE_CLASS_PATH_FILE).display()),
    ];
    args.extend(IDEA.inputs());
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(result.output, format!("Rendered the launch files of {model} for darwin_aarch64\n"));
    assert_eq!(
        std::fs::read_to_string(out("out.vmoptions")).unwrap(),
        format!("-Xmx2048m\n-XX:+Common\n{EAP_LINE}\n-Dx=mac\n")
    );
    assert_eq!(std::fs::read_to_string(out("build.txt")).unwrap(), "IU-263.SNAPSHOT");
    assert_eq!(
        std::fs::read_to_string(out("out.properties")).unwrap(),
        format!("a=IntelliJIdea\n\nb=1{EAP_BLOCK}")
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

/// A model with `frontendVmOptions` needs `--frontend-vmoptions-out`, and a model without it refuses the option.
#[test]
fn the_tool_writes_the_frontend_vmoptions_file_when_the_model_states_it() {
    let tool = ToolRun::new();
    let mut model: Value = serde_json::from_str(&read_text(testdata("model.json"))).unwrap();
    model["frontendVmOptions"] = serde_json::json!({"memory": ["-Xmx1024m"], "os": {"Linux": ["-Dx=frontend"]}});
    let frontend_model = tool.write("frontend-model.json", &model.to_string());
    let plain_model = testdata("model.json").display().to_string();
    let args_of = |model: &str, extra: &[String]| -> Vec<String> {
        let mut args = tool.base_args();
        args[0] = format!("--model={model}");
        args.extend(IDEA.inputs());
        args.extend(extra.iter().cloned());
        args
    };
    let frontend_out = format!("--frontend-vmoptions-out={}", tool.out("frontend.vmoptions"));

    let result = run_tool(&args_of(&frontend_model, std::slice::from_ref(&frontend_out)));
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(
        std::fs::read_to_string(tool.out("frontend.vmoptions")).unwrap(),
        format!("-Xmx1024m\n{EAP_LINE}\n-Dx=frontend\n")
    );

    for (model, extra, want) in [
        (
            &frontend_model,
            Vec::new(),
            (
                1,
                format!("ERROR: {frontend_model} states frontendVmOptions, so --frontend-vmoptions-out is required\n"),
            ),
        ),
        (
            &plain_model,
            vec![frontend_out],
            (
                1,
                format!("ERROR: --frontend-vmoptions-out is set, but {plain_model} states no frontendVmOptions\n"),
            ),
        ),
        (
            &frontend_model,
            strings(&["--frontend-vmoptions-out="]),
            (2, "ERROR: --frontend-vmoptions-out must not be empty\n".to_owned()),
        ),
    ] {
        let result = run_tool(&args_of(model, &extra));
        assert_eq!((result.code, result.errors), want, "{extra:?}");
    }
}

/// A classic-loader launch needs `--core-classpath-jar-names`, and a modular-loader launch refuses it.
#[test]
fn the_tool_reads_the_core_classpath_for_a_classic_launch() {
    let tool = ToolRun::new();
    let mut model: Value = serde_json::from_str(&read_text(testdata("model.json"))).unwrap();
    model["launch"]["bootClassPathJarNames"] = serde_json::json!(["platform-loader.jar"]);
    let modular_model = tool.write("modular-model.json", &model.to_string());
    let classic_model = testdata("model.json").display().to_string();
    let args_of = |model: &str, core_class_path: Option<String>| -> Vec<String> {
        let mut args: Vec<String> = tool
            .base_args()
            .into_iter()
            .filter(|arg| !arg.starts_with("--core-classpath-jar-names="))
            .collect();
        args[0] = format!("--model={model}");
        args.extend(IDEA.inputs());
        args.extend(core_class_path.map(|file| format!("--core-classpath-jar-names={file}")));
        args
    };
    let core_file = testdata(CORE_CLASS_PATH_FILE).display().to_string();

    let result = run_tool(&args_of(&modular_model, None));
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(
        tool.product_info()["launch"][0]["customCommands"][0]["bootClassPathJarNames"],
        serde_json::json!([
            "platform-loader.jar",
            "../plugins/mcpserver-plugin/lib/intellij.mcpserver.stdio.jar"
        ])
    );

    for (model, core_class_path, want) in [
        (
            classic_model.as_str(),
            None,
            (
                1,
                format!(
                    "ERROR: {classic_model} states no bootClassPathJarNames of the launch, so --core-classpath-jar-names is required\n"
                ),
            ),
        ),
        (
            modular_model.as_str(),
            Some(core_file),
            (
                1,
                format!("ERROR: --core-classpath-jar-names is set, but {modular_model} states the bootClassPathJarNames of the launch\n"),
            ),
        ),
        (
            classic_model.as_str(),
            Some(String::new()),
            (2, "ERROR: --core-classpath-jar-names must not be empty\n".to_owned()),
        ),
    ] {
        let result = run_tool(&args_of(model, core_class_path.clone()));
        assert_eq!((result.code, result.errors), want, "{model} {core_class_path:?}");
    }

    for (name, content, want) in [
        ("empty.txt", "", "the core classpath names no jar"),
        ("gap.txt", "a.jar\n\nb.jar\n", "the core classpath has an empty line"),
    ] {
        let file = tool.write(name, content);
        let result = run_tool(&args_of(&classic_model, Some(file.clone())));
        assert_eq!((result.code, result.errors), (1, format!("ERROR: {want}: {file}\n")), "{name}");
    }
}

#[test]
fn options_refuse_every_other_form() {
    for (args, expected) in [
        (vec!["--model"], "ERROR: --model takes a value, as in --model=<value>"),
        (vec!["-m"], r#"ERROR: expected an option in the form --key=value, but got "-m""#),
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
    // A request with every required option refuses what no option takes.
    let tool = ToolRun::new();
    for (extra, expected) in [
        ("--unknown=x", "ERROR: unknown option: --unknown"),
        (
            "model.json",
            r#"ERROR: expected an option in the form --key=value, but got "model.json""#,
        ),
    ] {
        let mut args = tool.base_args();
        args.extend(IDEA.inputs());
        args.push(extra.to_owned());
        let result = run_tool(&args);
        assert_eq!(
            (result.code, result.errors.as_str()),
            (2, format!("{expected}\n").as_str()),
            "{extra}"
        );
    }
}

/// The facts of `product-info.json` that the application info and `build.txt` give, on Linux.
fn derived_facts(files: &LaunchFiles) -> Vec<(String, Value)> {
    let info: Value = serde_json::from_str(&files.product_info).unwrap();
    let mut facts: Vec<(String, Value)> = [
        "name",
        "version",
        "versionSuffix",
        "buildNumber",
        "svgIconPath",
        "productVendor",
        "majorVersionReleaseDate",
    ]
    .into_iter()
    .map(|key| (key.to_owned(), info[key].clone()))
    .collect();
    facts.push(("startupWmClass".to_owned(), info["launch"][0]["startupWmClass"].clone()));
    facts
}

fn facts(values: &[(&str, Value)]) -> Vec<(String, Value)> {
    values.iter().map(|(key, value)| ((*key).to_owned(), value.clone())).collect()
}

/// A plain product without a release date takes the pinned build date. A language server applies its markers before
/// the XML is read, and its `yyyyMMddHHmm` release date loses the time.
#[test]
fn product_info_takes_the_facts_of_the_application_info() {
    let idea = IDEA.render(&test_model(IDEA.model), "linux_x64", "", "").unwrap();
    assert_eq!(
        derived_facts(&idea),
        facts(&[
            ("name", "IntelliJ IDEA".into()),
            ("version", "2026.3".into()),
            ("versionSuffix", "EAP".into()),
            ("buildNumber", "263.SNAPSHOT".into()),
            ("svgIconPath", "bin/idea.svg".into()),
            ("productVendor", "JetBrains".into()),
            ("majorVersionReleaseDate", "20260101".into()),
            ("startupWmClass", "jetbrains-idea".into()),
        ])
    );
    let server = SERVER.render(&test_model(SERVER.model), "linux_x64", "", "").unwrap();
    assert_eq!(
        derived_facts(&server),
        facts(&[
            ("name", "Server \"Q\"\\\t\u{1}\u{7f} ü".into()),
            ("version", "2026.3".into()),
            ("versionSuffix", Value::Null),
            ("buildNumber", "263.1234".into()),
            ("svgIconPath", "bin/server.svg".into()),
            ("productVendor", "JetBrains".into()),
            ("majorVersionReleaseDate", "20261231".into()),
            ("startupWmClass", "jetbrains-server-\"q\"\\\t\u{1}\u{7f}-ü".into()),
        ])
    );
}

/// The files of the tool in a temporary directory, with the model and the platform of [`IDEA`].
struct ToolRun {
    dir: tempfile::TempDir,
}

impl ToolRun {
    fn new() -> Self {
        Self {
            dir: tempfile::tempdir().unwrap(),
        }
    }

    fn write(&self, name: &str, content: &str) -> String {
        let path = self.dir.path().join(name);
        std::fs::write(&path, content).unwrap();
        path.display().to_string()
    }

    fn out(&self, name: &str) -> String {
        self.dir.path().join(name).display().to_string()
    }

    /// The model, the platform, the base files and the outputs, without the application info options.
    fn base_args(&self) -> Vec<String> {
        vec![
            format!("--model={}", testdata("model.json").display()),
            "--platform=linux_x64".to_owned(),
            format!("--opened-packages={}", self.write("opened.txt", "")),
            format!("--idea-properties={}", self.write("idea.properties", "")),
            format!("--common-vmoptions={}", self.write("common.vmoptions", "")),
            format!("--build-txt-out={}", self.out("build.txt")),
            format!("--idea-properties-out={}", self.out("out.properties")),
            format!("--vmoptions-out={}", self.out("out.vmoptions")),
            format!("--product-info-out={}", self.out("product-info.json")),
            format!("--core-classpath-jar-names={}", testdata(CORE_CLASS_PATH_FILE).display()),
        ]
    }

    fn product_info(&self) -> Value {
        serde_json::from_str(&std::fs::read_to_string(self.out("product-info.json")).unwrap()).unwrap()
    }
}

/// A frontend takes the names, the version and the release date of its host. A part that the host does not state
/// comes from the frontend.
#[test]
fn the_tool_reads_the_host_of_a_frontend() {
    let tool = ToolRun::new();
    let mut args = tool.base_args();
    args.extend([
        format!("--application-info={}", testdata("application-info/client.xml").display()),
        format!("--host-application-info={}", testdata("application-info/host.xml").display()),
        format!("--build-number={}", testdata("build.txt").display()),
        format!("--build-date-seconds={PINNED_BUILD_DATE_SECONDS}"),
    ]);
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    let info = tool.product_info();
    assert_eq!(
        (
            &info["name"],
            &info["version"],
            &info["versionSuffix"],
            &info["majorVersionReleaseDate"],
            &info["svgIconPath"],
            &info["launch"][0]["startupWmClass"],
        ),
        (
            &Value::from("JetBrains Rider"),
            &Value::from("2026.4.9"),
            &Value::from("EAP 6 D"),
            &Value::from("20261001"),
            &Value::from("bin/idea.svg"),
            &Value::from("jetbrains-rider"),
        )
    );
}

/// The tool replaces the markers in their order, and a later marker sees the text of an earlier one.
#[test]
fn the_tool_applies_the_replacements() {
    let tool = ToolRun::new();
    let xml = read_text(testdata("application-info/server.xml")).replace("__BUNDLE_EAP__", "__FIRST__");
    let mut args = tool.base_args();
    args.extend([
        format!("--application-info={}", tool.write("server.xml", &xml)),
        format!("--build-number={}", testdata("build.txt").display()),
        format!("--build-date-seconds={PINNED_BUILD_DATE_SECONDS}"),
        "--replacement=FIRST=__SECOND__".to_owned(),
        "--replacement=SECOND=true".to_owned(),
        "--replacement=BUNDLE_NAME=".to_owned(),
    ]);
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    assert_eq!(tool.product_info()["versionSuffix"], "EAP");
}

/// A release product without a release date takes the pinned build date, as an EAP product does. A release build
/// takes the date from a system property, and a dev distribution sets none.
#[test]
fn the_tool_accepts_a_release_without_a_release_date() {
    let tool = ToolRun::new();
    let xml = read_text(testdata("application-info/idea.xml")).replace(r#"eap="true""#, r#"eap="false""#);
    let mut args = tool.base_args();
    args.extend([
        format!("--application-info={}", tool.write("idea.xml", &xml)),
        format!("--build-number={}", testdata("build.txt").display()),
        format!("--build-date-seconds={PINNED_BUILD_DATE_SECONDS}"),
    ]);
    let result = run_tool(&args);
    assert_eq!(result.code, 0, "{}", result.errors);
    let info = tool.product_info();
    assert_eq!(
        (&info["versionSuffix"], &info["majorVersionReleaseDate"]),
        (&Value::Null, &Value::from("20260101"))
    );
}

/// A render error of the application info exits with code 1 and names the file.
#[test]
fn the_tool_refuses_an_application_info_it_cannot_read() {
    let idea = read_text(testdata("application-info/idea.xml"));
    for (name, content, want) in [
        (
            "pattern",
            idea.replace(r#"minor="3""#, r#"minor="3" full="{0}'{1}'""#),
            r#"the version pattern "{0}'{1}'" is not literal text with the elements {0} to {3}"#,
        ),
        (
            "date",
            idea.replace(r#"date="__BUILD_DATE__""#, r#"majorReleaseDate="2026-10-01""#),
            r#"the major release date "2026-10-01" is neither yyyyMMdd nor yyyyMMddHHmm"#,
        ),
        ("marker", idea.replace(r#"minor="3""#, r#"minor="3" suffix="__SUFFIX__""#), ""),
    ] {
        let tool = ToolRun::new();
        let file = tool.write("idea.xml", &content);
        let mut args = tool.base_args();
        args.extend([
            format!("--application-info={file}"),
            format!("--build-number={}", testdata("build.txt").display()),
            format!("--build-date-seconds={PINNED_BUILD_DATE_SECONDS}"),
        ]);
        let result = run_tool(&args);
        if want.is_empty() {
            // A marker without a replacement stays in the text, as `BuildUtils.replaceAll` leaves it.
            assert_eq!(result.code, 0, "{name}: {}", result.errors);
            assert_eq!(tool.product_info()["versionSuffix"], "__SUFFIX__", "{name}");
            continue;
        }
        assert_eq!(result.code, 1, "{name}: {}", result.errors);
        assert!(result.errors.contains(want), "{name}: {}", result.errors);
        assert!(
            result.errors.contains(&file),
            "{name}: the error must name the file: {}",
            result.errors
        );
    }

    let tool = ToolRun::new();
    let mut args = tool.base_args();
    args.extend([
        format!("--application-info={}", testdata("application-info/idea.xml").display()),
        format!("--build-number={}", tool.write("empty.txt", " \n")),
        format!("--build-date-seconds={PINNED_BUILD_DATE_SECONDS}"),
    ]);
    let result = run_tool(&args);
    assert_eq!(result.code, 1);
    assert!(result.errors.starts_with("ERROR: the build number is empty: "), "{}", result.errors);
}

/// The application info options are required, except the host and the replacements.
#[test]
fn the_application_info_options_are_checked() {
    let tool = ToolRun::new();
    let inputs = IDEA.inputs();
    for (skipped, want) in [
        (0, "ERROR: --application-info is required"),
        (1, "ERROR: --build-number is required"),
        (2, "ERROR: --build-date-seconds is required"),
    ] {
        let mut args = tool.base_args();
        args.extend(
            inputs
                .iter()
                .enumerate()
                .filter(|(index, _)| *index != skipped)
                .map(|(_, arg)| arg.clone()),
        );
        let result = run_tool(&args);
        assert_eq!(
            (result.code, result.errors.as_str()),
            (2, format!("{want}\n").as_str()),
            "{skipped}"
        );
    }
    for (extra, want) in [
        (
            "--build-date-seconds=soon",
            r#"ERROR: --build-date-seconds is not a number of seconds: "soon""#,
        ),
        ("--host-application-info=", "ERROR: --host-application-info must not be empty"),
        (
            "--replacement=VALUE",
            r#"ERROR: a replacement is '<key>=<value>', and "VALUE" is not"#,
        ),
        (
            "--replacement==value",
            r#"ERROR: a replacement is '<key>=<value>', and "=value" is not"#,
        ),
        ("--replacement=A=1", r#"ERROR: the replacement "A" is stated more than once"#),
    ] {
        let mut args = tool.base_args();
        args.extend(
            inputs
                .iter()
                .filter(|arg| !(extra.starts_with("--build-date") && arg.starts_with("--build-date")))
                .cloned(),
        );
        args.push("--replacement=A=0".to_owned());
        args.push(extra.to_owned());
        let result = run_tool(&args);
        assert_eq!((result.code, result.errors.as_str()), (2, format!("{want}\n").as_str()), "{extra}");
    }
}
