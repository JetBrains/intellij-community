// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use anyhow::bail;
use appinfo::{ApplicationInfo, linux_frame_class};
use serde::Serialize;

use crate::model::{
    BUILD_NUMBER_TOKEN, CustomCommand, IdeaProperties, JvmArguments, JvmArgumentsRef, LaunchModel, LaunchProperty, VmOptions,
};

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

/// The launch files of one product and one platform.
#[derive(Debug)]
pub(crate) struct LaunchFiles {
    pub build_txt: String,
    pub idea_properties: String,
    pub vm_options: String,
    /// The vmoptions file of the embedded frontend, when the model states `frontendVmOptions`.
    pub frontend_vm_options: Option<String>,
    pub product_info: String,
}

/// What the launch files of one product derive from: the launch model, the application info, the build number, the
/// core classpath of the distribution and the dev JVM arguments.
#[derive(Clone, Copy)]
pub(crate) struct Product<'a> {
    pub model: &'a LaunchModel,
    pub application_info: &'a ApplicationInfo,
    pub build_number: &'a str,
    /// The `lib/` jars of the core classpath. A classic-loader launch needs them, and a modular-loader launch reads none.
    pub core_class_path: Option<&'a [String]>,
    /// The JVM arguments that mark a dev distribution, such as `-Didea.use.dev.build.server=true`. Production states
    /// none. They go after the `additionalJvmArguments` of the launch and of each custom command with a list of its own.
    /// The launcher gives a command with an empty list the list of the launch, so that command gets them too.
    pub dev_jvm_arguments: &'a [String],
}

/// `CORE_CLASSPATH_LEADING_JARS` of `classpath.kt`: the jars that start the boot class path of a launch, in this order.
const LEADING_JARS: [&str; 4] = ["platform-loader.jar", "util-8.jar", "util.jar", "product-backend.jar"];

/// `PLATFORM_CORE_NIO_FS`: the jar that `-Xbootclasspath/a:` names for the multi-routing file system.
pub(crate) const NIO_FS_JAR: &str = "nio-fs.jar";

/// Renders the files. `common_vm_options_file` is the text of `bin/common.vmoptions`.
pub(crate) fn render_launch_files(
    product: &Product<'_>,
    target: Platform,
    opened_packages_file: &str,
    idea_properties_base: &str,
    common_vm_options_file: &str,
) -> anyhow::Result<LaunchFiles> {
    let model = product.model;
    let is_eap = product.application_info.is_eap;
    let vm_options = render_vm_options(&model.vm_options, target, common_vm_options_file, is_eap, model.language_server)?;
    // The production writer reads the EAP flag of the frontend context. The plan generator checks that it is the flag
    // of the product. The generator also refuses a frontend that is a language server, so the headless rule is off.
    let frontend_vm_options = match &model.frontend_vm_options {
        Some(parts) => Some(render_vm_options(parts, target, common_vm_options_file, is_eap, false)?),
        None => None,
    };
    let product_info = render_product_info(product, target, &opened_packages(opened_packages_file, target.os)?)?;
    Ok(LaunchFiles {
        build_txt: format!("{}-{}", model.product_code, product.build_number),
        idea_properties: render_idea_properties(
            &model.idea_properties,
            idea_properties_base,
            &model.data_directory_base_name,
            is_eap,
        ),
        vm_options,
        frontend_vm_options,
        product_info,
    })
}

/// `generateVmOptions` of `VmOptionsGenerator.kt` for one OS: the memory lines, the common lines, the product lines and
/// the OS lines of `parts`, then the EAP line and the headless rule. Each line ends with the separator of the OS.
fn render_vm_options(
    parts: &VmOptions,
    target: Platform,
    common_vm_options_file: &str,
    is_eap: bool,
    headless: bool,
) -> anyhow::Result<String> {
    let separator = if target.os == OS_WINDOWS { "\r\n" } else { "\n" };
    let mut lines = parts.memory.clone();
    lines.extend(common_vm_options(common_vm_options_file));
    lines.extend(parts.product.iter().cloned());
    if let Some(os_vm_options) = parts.os.get(target.os) {
        lines.extend(os_vm_options.iter().cloned());
    }
    if is_eap {
        insert_eap_vm_options(&mut lines);
    }
    if headless {
        apply_headless_vm_options(&mut lines);
    }
    let mut text = String::new();
    for line in &lines {
        if !line.is_ascii() {
            bail!("the vmoptions line {line:?} is not ASCII");
        }
        text.push_str(line);
        text.push_str(separator);
    }
    Ok(text)
}

/// The data directory name of the product: the base name, then the major version and the main part of the minor version.
///
/// It equals `ProductProperties.getSystemSelector`. The plan generator checks that for every product.
fn data_directory_name(product: &Product<'_>) -> String {
    let application_info = product.application_info;
    format!(
        "{}{}.{}",
        product.model.data_directory_base_name, application_info.major_version, application_info.minor_version_main_part
    )
}

/// The lines of `bin/common.vmoptions`, without the blank lines and the `#` lines, as `VmOptionsGenerator.kt` reads them.
pub(crate) fn common_vm_options(text: &str) -> impl Iterator<Item = String> + '_ {
    text.lines()
        .filter(|line| !line.trim().is_empty() && !line.starts_with('#'))
        .map(str::to_owned)
}

/// The headless rule of `generateVmOptions` in `VmOptionsGenerator.kt` for a language server: drop every line that
/// names an AWT, Swing, Java2D or Skiko property, then add `-Djava.awt.headless=true`.
fn apply_headless_vm_options(lines: &mut Vec<String>) {
    lines.retain(|line| !["awt.", "swing.", "java2d.", "skiko."].iter().any(|part| line.contains(part)));
    lines.push("-Djava.awt.headless=true".to_owned());
}

/// `insertEapVmOptions` of `VmOptionsGenerator.kt`: the line of an EAP build goes before `-ea`, else before the first
/// `-D` line, else at the end.
pub(crate) fn insert_eap_vm_options(lines: &mut Vec<String>) {
    let index = lines
        .iter()
        .position(|line| line == "-ea")
        .or_else(|| lines.iter().position(|line| line.starts_with("-D")))
        .unwrap_or(lines.len());
    // It must be consistent with `ConfigImportHelper#updateVMOptions`.
    lines.insert(index, "-XX:MaxJavaStackTraceDepth=10000".to_owned());
}

/// The block of `ideaPropertiesFatalErrorNotification` (`BuildTasksImpl.kt`) for an EAP build.
const FATAL_ERROR_NOTIFICATION_EAP: &str = "\n#-----------------------------------------------------------------------\n\
# Change to 'disabled' if you don't want to receive instant visual notifications\n\
# about fatal errors that happen to an IDE or plugins installed.\n\
#-----------------------------------------------------------------------\n\
idea.fatal.error.notification=enabled\n";

/// The block of `ideaPropertiesFatalErrorNotification` (`BuildTasksImpl.kt`) for a release build.
const FATAL_ERROR_NOTIFICATION_RELEASE: &str = "\n#-----------------------------------------------------------------------\n\
# Change to 'enabled' if you want to receive instant visual notifications\n\
# about fatal errors that happen to an IDE or plugins installed.\n\
#-----------------------------------------------------------------------\n\
idea.fatal.error.notification=disabled\n";

fn render_idea_properties(properties: &IdeaProperties, base: &str, data_directory_base_name: &str, is_eap: bool) -> String {
    let mut text = base.to_owned();
    for addition in &properties.additions {
        text.push('\n');
        text.push_str(addition);
    }
    let mut text = text.replace("@@settings_dir@@", data_directory_base_name);
    if properties.fatal_error_notification {
        text.push_str(if is_eap {
            FATAL_ERROR_NOTIFICATION_EAP
        } else {
            FATAL_ERROR_NOTIFICATION_RELEASE
        });
    }
    text
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
pub(crate) fn additional_jvm_arguments(
    jvm: &JvmArguments,
    names: &LaunchNames<'_>,
    target: Platform,
    opened_packages: &[String],
    qodana: bool,
) -> Vec<String> {
    let mut result = Vec::new();
    let macro_name = match target.os {
        OS_WINDOWS => "%IDE_HOME%",
        OS_MAC => "$APP_PACKAGE/Contents",
        _ => "$IDE_HOME",
    };

    if !qodana && jvm.multi_routing_file_system {
        let separator = if target.os == OS_WINDOWS { "\\" } else { "/" };
        result.push(format!("-Xbootclasspath/a:{}", [macro_name, "lib", NIO_FS_JAR].join(separator)));
    }
    if let Some(class_loader) = &jvm.class_loader {
        result.push(format!("-Djava.system.class.loader={class_loader}"));
    }

    result.push(format!("-Didea.vendor.name={}", names.vendor_name));
    result.push(format!("-Didea.paths.selector={}", names.data_directory_name));
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

/// The names that every launch of a product states: the vendor and the data directory name, which is the paths selector.
pub(crate) struct LaunchNames<'a> {
    pub vendor_name: &'a str,
    pub data_directory_name: &'a str,
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
    #[serde(skip_serializing_if = "Vec::is_empty")]
    boot_class_path_jar_names: Vec<String>,
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
    #[serde(skip_serializing_if = "Vec::is_empty")]
    boot_class_path_jar_names: Vec<String>,
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
    let data_directory_name = data_directory_name(product);
    let names = LaunchNames {
        vendor_name: &application_info.short_company_name,
        data_directory_name: &data_directory_name,
    };
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
    let boot_class_path_jar_names = launch_boot_class_path(product)?;
    let mut launch_jvm_arguments = additional_jvm_arguments(&model.launch.jvm_arguments, &names, target, opened_packages, false);
    launch_jvm_arguments.extend(product.dev_jvm_arguments.iter().cloned());
    let launch = Launch {
        os: target.os,
        arch: target.arch,
        launcher_path,
        java_executable_path,
        vm_options_file_path: vm_options_file_path(target.os, &model.base_file_name, model.language_server),
        startup_wm_class: (target.os == OS_LINUX).then(|| linux_frame_class(&application_info.product_name_with_edition())),
        additional_jvm_arguments: launch_jvm_arguments,
        main_class: &model.launch.main_class,
        stdio_redirect_arg: model.launch.stdio_redirect_arg.as_deref(),
        custom_commands: model
            .custom_commands
            .iter()
            .map(|command| render_custom_command(command, product, &boot_class_path_jar_names, &names, target, opened_packages))
            .collect::<anyhow::Result<_>>()?,
        boot_class_path_jar_names,
    };
    let info = ProductInfo {
        name: &application_info.full_product_name,
        version: &application_info.version,
        version_suffix: application_info.version_suffix.as_deref(),
        build_number: product.build_number,
        product_code: &model.product_code,
        env_var_base_name: &model.env_var_base_name,
        data_directory_name: &data_directory_name,
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

/// The `bootClassPathJarNames` of the main launch.
///
/// A modular-loader launch states its list. A classic-loader launch takes the list of `launchBootClassPathJarNames` in
/// `classpath.kt` over the core classpath. The list has no `nio-fs.jar` when the multi-routing file system puts the jar
/// on `-Xbootclasspath/a:`. The leading jars come first, then the other names in bytewise order, each name once.
pub(crate) fn launch_boot_class_path(product: &Product<'_>) -> anyhow::Result<Vec<String>> {
    let launch = &product.model.launch;
    let core_class_path = match (&launch.boot_class_path_jar_names, product.core_class_path) {
        (Some(names), None) => return Ok(names.clone()),
        (None, Some(core_class_path)) => core_class_path,
        (Some(_), Some(_)) => bail!("the launch states bootClassPathJarNames, so it reads no core classpath"),
        (None, None) => bail!("the launch states no bootClassPathJarNames, so it needs the core classpath"),
    };
    let skip_nio_fs = launch.jvm_arguments.multi_routing_file_system;
    let mut rest: Vec<&str> = core_class_path
        .iter()
        .map(String::as_str)
        .filter(|name| !(skip_nio_fs && *name == NIO_FS_JAR))
        .collect();
    rest.sort_unstable();
    rest.dedup();
    let mut result: Vec<String> = LEADING_JARS
        .iter()
        .filter(|jar| rest.contains(jar))
        .map(|jar| (*jar).to_owned())
        .collect();
    result.extend(rest.into_iter().filter(|name| !LEADING_JARS.contains(name)).map(str::to_owned));
    Ok(result)
}

/// Renders one custom command. The command takes the JVM arguments of the block it names, and a command that states an
/// environment variable name states the data directory name of the product. A command with additional jars takes
/// `launch_boot_class_path_jar_names`, then these jars. A command with JVM arguments of its own also takes the dev JVM
/// arguments.
fn render_custom_command<'a>(
    command: &'a CustomCommand,
    product: &Product<'a>,
    launch_boot_class_path_jar_names: &[String],
    names: &LaunchNames<'a>,
    target: Platform,
    opened_packages: &[String],
) -> anyhow::Result<Command<'a>> {
    let model = product.model;
    let boot_class_path_jar_names = if command.additional_boot_class_path_jar_names.is_empty() {
        command.boot_class_path_jar_names.clone()
    } else {
        if !command.boot_class_path_jar_names.is_empty() {
            bail!(
                "the custom command {:?} states both bootClassPathJarNames and additionalBootClassPathJarNames",
                command.commands
            );
        }
        launch_boot_class_path_jar_names
            .iter()
            .chain(&command.additional_boot_class_path_jar_names)
            .cloned()
            .collect()
    };
    let mut arguments = Vec::new();
    let jvm = match command.jvm_arguments {
        None => None,
        Some(JvmArgumentsRef::Launch) => Some(&model.launch.jvm_arguments),
        Some(JvmArgumentsRef::Frontend) => {
            let Some(jvm) = &model.frontend_jvm_arguments else {
                bail!(
                    "the custom command {:?} names the frontend JVM arguments, but the model states no frontendJvmArguments",
                    command.commands
                );
            };
            Some(jvm)
        }
    };
    if let Some(jvm) = jvm {
        arguments.extend(additional_jvm_arguments(jvm, names, target, opened_packages, command.qodana));
    }
    if target.os == OS_MAC {
        arguments.extend(command.mac_jvm_arguments.iter().cloned());
    }
    arguments.extend(
        command
            .extra_jvm_arguments
            .iter()
            .map(|argument| argument.replace(BUILD_NUMBER_TOKEN, product.build_number)),
    );
    if !arguments.is_empty() {
        arguments.extend(product.dev_jvm_arguments.iter().cloned());
    }
    Ok(Command {
        commands: &command.commands,
        vm_options_file_path: command.vm_options_file_path.get(target.os).map(String::as_str),
        boot_class_path_jar_names,
        additional_jvm_arguments: arguments,
        main_class: command.main_class.as_deref(),
        env_var_base_name: command.env_var_base_name.as_deref(),
        data_directory_name: command.env_var_base_name.as_ref().map(|_| names.data_directory_name),
    })
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
