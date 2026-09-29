// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use anyhow::bail;
use appinfo::{ApplicationInfo, linux_frame_class};
use serde::Serialize;

use crate::model::{CustomCommand, IdeaProperties, JvmArguments, LaunchModel, LaunchProperty};

// OS names are `OsFamily.osName`, and architecture names are `JvmArchitecture.dirName`.
pub(crate) const OS_MAC: &str = "macOS";
pub(crate) const OS_LINUX: &str = "Linux";
pub(crate) const OS_WINDOWS: &str = "Windows";

/// One `HOST_PLATFORMS` entry: the OS and the architecture that a distribution is for.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Platform {
    pub os: &'static str,
    pub arch: &'static str,
}

pub(crate) fn parse_platform(name: &str) -> anyhow::Result<Platform> {
    let (os_token, arch_token) = name.split_once('_').unwrap_or((name, ""));
    let os = match os_token {
        "darwin" => Some(OS_MAC),
        "linux" => Some(OS_LINUX),
        "windows" => Some(OS_WINDOWS),
        _ => None,
    };
    let arch = match arch_token {
        "x64" => Some("amd64"),
        "aarch64" => Some("aarch64"),
        _ => None,
    };
    match (os, arch) {
        (Some(os), Some(arch)) => Ok(Platform { os, arch }),
        _ => bail!("{name:?} is not a host platform such as darwin_aarch64"),
    }
}

/// The four launch files of one product and one platform.
#[derive(Debug)]
pub(crate) struct LaunchFiles {
    pub build_txt: String,
    pub idea_properties: String,
    pub vm_options: String,
    pub product_info: String,
}

/// What the launch files of one product derive from: the launch model, the application info and the build number.
#[derive(Clone, Copy)]
pub(crate) struct Product<'a> {
    pub model: &'a LaunchModel,
    pub application_info: &'a ApplicationInfo,
    pub build_number: &'a str,
}

pub(crate) fn render_launch_files(
    product: &Product<'_>,
    target: Platform,
    opened_packages_file: &str,
    idea_properties_base: &str,
) -> anyhow::Result<LaunchFiles> {
    let model = product.model;
    let Some(vm_options) = model.vm_options.get(target.os) else {
        bail!("the model states no vmoptions for {}", target.os);
    };
    let separator = if target.os == OS_WINDOWS { "\r\n" } else { "\n" };
    let mut vm_options_text = String::new();
    for line in vm_options {
        if !line.is_ascii() {
            bail!("the vmoptions line {line:?} is not ASCII");
        }
        vm_options_text.push_str(line);
        vm_options_text.push_str(separator);
    }
    let product_info = render_product_info(product, target, &opened_packages(opened_packages_file, target.os)?)?;
    Ok(LaunchFiles {
        build_txt: format!("{}-{}", model.product_code, product.build_number),
        idea_properties: render_idea_properties(&model.idea_properties, idea_properties_base),
        vm_options: vm_options_text,
        product_info,
    })
}

fn render_idea_properties(properties: &IdeaProperties, base: &str) -> String {
    let mut text = base.to_owned();
    for addition in &properties.additions {
        text.push('\n');
        text.push_str(addition);
    }
    text.replace("@@settings_dir@@", &properties.settings_dir) + &properties.suffix
}

/// `JavaModuleOptions.readOptions` of the `OpenedPackages.txt` text for `os`: every line, minus the lines that name a
/// package of another OS.
///
/// `Files.lines` also ends a line at a lone `\r`. The file has none, so the function refuses one.
pub(crate) fn opened_packages(text: &str, os: &str) -> anyhow::Result<Vec<String>> {
    let mut exclusions = Vec::new();
    if os != OS_WINDOWS {
        exclusions.push("/sun.awt.windows");
    }
    if os != OS_MAC {
        exclusions.extend(["/sun.lwawt", "/com.apple"]);
    }
    if os != OS_LINUX {
        exclusions.extend(["/sun.awt.X11", "/com.sun.java.swing.plaf.gtk"]);
    }
    let mut result = Vec::new();
    for line in text.lines() {
        if line.contains('\r') {
            bail!("the opened packages line {line:?} has a carriage return that is not before a line feed");
        }
        if !exclusions.iter().any(|exclusion| line.contains(exclusion)) {
            result.push(line.to_owned());
        }
    }
    Ok(result)
}

/// `renderAdditionalJvmArguments` of `ProductLaunchRenderer.kt`, for a launcher that is not a script and a
/// distribution that is not portable.
pub(crate) fn additional_jvm_arguments(jvm: &JvmArguments, target: Platform, opened_packages: &[String], qodana: bool) -> Vec<String> {
    let mut result = Vec::new();
    let macro_name = match target.os {
        OS_WINDOWS => "%IDE_HOME%",
        OS_MAC => "$APP_PACKAGE/Contents",
        _ => "$IDE_HOME",
    };

    if !qodana && jvm.multi_routing_file_system {
        let separator = if target.os == OS_WINDOWS { "\\" } else { "/" };
        result.push(format!("-Xbootclasspath/a:{}", [macro_name, "lib", "nio-fs.jar"].join(separator)));
    }
    if let Some(class_loader) = &jvm.class_loader {
        result.push(format!("-Djava.system.class.loader={class_loader}"));
    }

    result.push(format!("-Didea.vendor.name={}", jvm.vendor_name));
    result.push(format!("-Didea.paths.selector={}", jvm.paths_selector));
    if let Some(jna_native_dir) = &jvm.jna_native_dir {
        result.push(format!("-Djna.boot.library.path={macro_name}/{jna_native_dir}/{}", target.arch));
        result.push("-Djna.nosys=true".to_owned());
        result.push("-Djna.noclasspath=true".to_owned());
    }
    if let Some(pty4j_native_dir) = &jvm.pty4j_native_dir {
        result.push(format!("-Dpty4j.preferred.native.folder={macro_name}/{pty4j_native_dir}"));
    }
    result.push("-Dio.netty.allocator.type=pooled".to_owned());
    if let Some(skiko_native_dir) = &jvm.skiko_native_dir {
        result.push(format!("-Dskiko.library.path={macro_name}/{skiko_native_dir}"));
    }
    if jvm.runtime_module_repository {
        result.push(format!(
            "-Dintellij.platform.runtime.repository.path={macro_name}/modules/module-descriptors.dat"
        ));
    }
    if let Some(root_module) = &jvm.root_module {
        result.push(format!("-Dintellij.platform.root.module={root_module}"));
        result.push(format!(
            "-Dintellij.platform.product.mode={}",
            jvm.product_mode.as_deref().unwrap_or("")
        ));
    }
    if let Some(platform_prefix) = &jvm.platform_prefix {
        result.push(format!("-Didea.platform.prefix={platform_prefix}"));
    }
    result.extend(jvm.additional.iter().cloned());
    if jvm.splash {
        result.push("-Dsplash=true".to_owned());
    }
    result.push("-Daether.connector.resumeDownloads=false".to_owned());
    result.push("-Dcompose.swing.render.on.graphics=true".to_owned());
    if jvm.native_access {
        result.push("--enable-native-access=ALL-UNNAMED".to_owned());
    }
    result.extend(opened_packages.iter().cloned());
    result
}

/// `ProductInfoData` of `ProductInfoGenerator.kt` for a launch that bundles a runtime, with no built-in modules.
///
/// The fields keep the declaration order of the Kotlin class. A field that kotlinx leaves out at its default value
/// skips serialization at that value.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ProductInfo<'a> {
    name: &'a str,
    version: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    version_suffix: Option<&'a str>,
    build_number: &'a str,
    product_code: &'a str,
    env_var_base_name: &'a str,
    data_directory_name: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    svg_icon_path: Option<String>,
    product_vendor: &'a str,
    major_version_release_date: &'a str,
    min_required_java_version: i32,
    launch: [Launch<'a>; 1],
    #[serde(skip_serializing_if = "<[_]>::is_empty")]
    custom_properties: &'a [LaunchProperty],
    #[serde(skip_serializing_if = "Vec::is_empty")]
    flavors: Vec<Flavor<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Launch<'a> {
    os: &'static str,
    arch: &'static str,
    launcher_path: String,
    java_executable_path: String,
    vm_options_file_path: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    startup_wm_class: Option<String>,
    #[serde(skip_serializing_if = "<[_]>::is_empty")]
    boot_class_path_jar_names: &'a [String],
    #[serde(skip_serializing_if = "Vec::is_empty")]
    additional_jvm_arguments: Vec<String>,
    main_class: &'a str,
    #[serde(skip_serializing_if = "Option::is_none")]
    stdio_redirect_arg: Option<&'a str>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    custom_commands: Vec<Command<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Command<'a> {
    commands: &'a [String],
    #[serde(skip_serializing_if = "Option::is_none")]
    vm_options_file_path: Option<&'a str>,
    #[serde(skip_serializing_if = "<[_]>::is_empty")]
    boot_class_path_jar_names: &'a [String],
    #[serde(skip_serializing_if = "Vec::is_empty")]
    additional_jvm_arguments: Vec<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    main_class: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    env_var_base_name: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    data_directory_name: Option<&'a str>,
}

#[derive(Serialize)]
struct Flavor<'a> {
    id: &'a str,
}

/// Renders `product-info.json` in the `prettyPrint` form of kotlinx.serialization, with no final newline.
///
/// The names, the version, the build number, the icon, the vendor, the release date and the window class come from
/// the application info and `build.txt`, as `ProductInfoGenerator.kt` takes them from `appInfo` and the build context.
///
/// `serde_json::to_string_pretty` indents by two spaces and escapes as kotlinx does: the quote, the backslash and the
/// control characters, with lowercase hex digits.
fn render_product_info(product: &Product<'_>, target: Platform, opened_packages: &[String]) -> anyhow::Result<String> {
    let model = product.model;
    let application_info = product.application_info;
    let to_root = if target.os == OS_MAC && !model.language_server { "../" } else { "" };
    let (launcher_path, java_executable_path) = match target.os {
        OS_MAC => {
            let launcher_dir = if model.language_server { "bin" } else { "../MacOS" };
            (
                format!("{launcher_dir}/{}", model.base_file_name),
                format!("{to_root}jbr/Contents/Home/bin/java"),
            )
        }
        OS_LINUX => (format!("bin/{}", model.base_file_name), "jbr/bin/java".to_owned()),
        _ => (
            format!("bin/{}.exe", add_64_if_needed(&model.base_file_name, model.language_server)),
            "jbr/bin/java.exe".to_owned(),
        ),
    };
    let launch = Launch {
        os: target.os,
        arch: target.arch,
        launcher_path,
        java_executable_path,
        vm_options_file_path: vm_options_file_path(target.os, &model.base_file_name, model.language_server),
        startup_wm_class: (target.os == OS_LINUX).then(|| linux_frame_class(&application_info.product_name_with_edition())),
        boot_class_path_jar_names: &model.launch.boot_class_path_jar_names,
        additional_jvm_arguments: additional_jvm_arguments(&model.launch.jvm_arguments, target, opened_packages, false),
        main_class: &model.launch.main_class,
        stdio_redirect_arg: model.launch.stdio_redirect_arg.as_deref(),
        custom_commands: model
            .custom_commands
            .iter()
            .map(|command| render_custom_command(command, target, opened_packages))
            .collect(),
    };
    let info = ProductInfo {
        name: &application_info.full_product_name,
        version: &application_info.version,
        version_suffix: application_info.version_suffix.as_deref(),
        build_number: product.build_number,
        product_code: &model.product_code,
        env_var_base_name: &model.env_var_base_name,
        data_directory_name: &model.data_directory_name,
        svg_icon_path: application_info
            .svg_icon
            .as_ref()
            .map(|_| format!("{to_root}bin/{}.svg", model.base_file_name)),
        product_vendor: &application_info.short_company_name,
        major_version_release_date: &application_info.major_release_date,
        min_required_java_version: model.min_required_java_version,
        launch: [launch],
        custom_properties: &model.custom_properties,
        flavors: model.flavors.iter().map(|id| Flavor { id }).collect(),
    };
    Ok(serde_json::to_string_pretty(&info)?)
}

fn render_custom_command<'a>(command: &'a CustomCommand, target: Platform, opened_packages: &[String]) -> Command<'a> {
    let mut arguments = Vec::new();
    if let Some(jvm) = &command.jvm_arguments {
        arguments.extend(additional_jvm_arguments(jvm, target, opened_packages, command.qodana));
    }
    if target.os == OS_MAC {
        arguments.extend(command.mac_jvm_arguments.iter().cloned());
    }
    arguments.extend(command.extra_jvm_arguments.iter().cloned());
    Command {
        commands: &command.commands,
        vm_options_file_path: command.vm_options_file_path.get(target.os).map(String::as_str),
        boot_class_path_jar_names: &command.boot_class_path_jar_names,
        additional_jvm_arguments: arguments,
        main_class: command.main_class.as_deref(),
        env_var_base_name: command.env_var_base_name.as_deref(),
        data_directory_name: command.data_directory_name.as_deref(),
    }
}

/// `vmOptionsFilePath` of `ProductLaunchModel.kt` for a launch of the product itself.
fn vm_options_file_path(os: &str, base_file_name: &str, language_server: bool) -> String {
    match os {
        OS_MAC if language_server => format!("bin/{base_file_name}.vmoptions"),
        OS_MAC => format!("../bin/{base_file_name}.vmoptions"),
        OS_LINUX => format!("bin/{}.vmoptions", add_64_if_needed(base_file_name, language_server)),
        _ => format!("bin/{}.exe.vmoptions", add_64_if_needed(base_file_name, language_server)),
    }
}

fn add_64_if_needed(name: &str, language_server: bool) -> String {
    if language_server { name.to_owned() } else { format!("{name}64") }
}
