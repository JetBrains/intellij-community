// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `ProductLaunchModel` of `ProductLaunchModel.kt`. The Kotlin encoder leaves out every field that holds its
//! default value, so a field with a Kotlin default takes that default here. A field without a Kotlin default is
//! required, because the encoder always writes it.
//!
//! The model has no `jbr17`, `xBootClassPathJarNames` or `cdsArchiveFileName`, because no dev-dist model sets them.
//! Thus the parser refuses a model that sets one as an unknown field.
//!
//! The model states each product fact once, and it states no fact of the application info and no build number. The
//! tool reads them from the declared sources, so the parser also refuses a model that states `version`, `versionSuffix`
//! or `linuxStartupWmClass`. The EAP flag decides the fatal error block of `idea.properties`, so the parser refuses its
//! old field `suffix` too. The tool appends the version to the data directory base name and reads the vendor from the
//! application info, so the parser refuses the old fields `dataDirectoryName`, `pathsSelector`, `vendorName` and
//! `settingsDir`.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct LaunchModel {
    pub product_code: String,
    pub env_var_base_name: String,
    /// The data directory name without the version. The tool appends `<major>.<minor main part>` of the application info.
    pub data_directory_base_name: String,
    pub min_required_java_version: i32,
    #[serde(default)]
    pub custom_properties: Vec<LaunchProperty>,
    #[serde(default)]
    pub flavors: Vec<String>,
    pub base_file_name: String,
    #[serde(default)]
    pub language_server: bool,
    pub launch: LaunchCommand,
    /// The JVM arguments of the frontend that a custom command names by [`JvmArgumentsRef::Frontend`].
    pub frontend_jvm_arguments: Option<JvmArguments>,
    #[serde(default)]
    pub custom_commands: Vec<CustomCommand>,
    pub vm_options: VmOptions,
    pub idea_properties: IdeaProperties,
}

/// A custom property. `product-info.json` writes it with the same fields.
#[derive(Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct LaunchProperty {
    pub key: String,
    pub value: String,
}

/// The parts of the vmoptions file of a release build: `memory`, the common lines, `product`, then the lines of the OS.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct VmOptions {
    pub memory: Vec<String>,
    /// The multi-routing file system lines, then the additional lines of the product.
    #[serde(default)]
    pub product: Vec<String>,
    /// The lines of each OS, keyed by `OsFamily.osName`. An OS without an entry adds no line.
    #[serde(default)]
    pub os: HashMap<String, Vec<String>>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct LaunchCommand {
    pub main_class: String,
    #[serde(default = "default_boot_class_path_jar_names")]
    pub boot_class_path_jar_names: Vec<String>,
    pub jvm_arguments: JvmArguments,
    pub stdio_redirect_arg: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct CustomCommand {
    pub commands: Vec<String>,
    /// The vmoptions file of the command, keyed by `OsFamily.osName`. A command with no entry names none.
    #[serde(default)]
    pub vm_options_file_path: HashMap<String, String>,
    #[serde(default)]
    pub boot_class_path_jar_names: Vec<String>,
    /// The block of JVM arguments that the command renders.
    pub jvm_arguments: Option<JvmArgumentsRef>,
    /// Renders `jvm_arguments` the way Qodana starts: without the multi-routing file system.
    #[serde(default)]
    pub qodana: bool,
    #[serde(default)]
    pub mac_jvm_arguments: Vec<String>,
    /// The tool replaces [`BUILD_NUMBER_TOKEN`] in each argument with the build number.
    #[serde(default)]
    pub extra_jvm_arguments: Vec<String>,
    pub main_class: Option<String>,
    /// A command that states it also states the data directory name of the product.
    pub env_var_base_name: Option<String>,
}

/// The text in an extra JVM argument of a custom command that the tool replaces with the build number.
pub(crate) const BUILD_NUMBER_TOKEN: &str = "@@build_number@@";

/// The block of JVM arguments that a custom command renders.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum JvmArgumentsRef {
    /// The JVM arguments of the main launch.
    Launch,
    /// The JVM arguments of the frontend.
    Frontend,
}

/// The JVM argument facts of a launch. A missing field takes its Kotlin default, which [`Default`] states.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", default, deny_unknown_fields)]
#[expect(clippy::struct_excessive_bools, reason = "each field is one JVM switch of the product model")]
pub(crate) struct JvmArguments {
    pub multi_routing_file_system: bool,
    pub class_loader: Option<String>,
    /// The JNA native tree relative to the IDE home, when the product bundles the JNA plugin.
    pub jna_native_dir: Option<String>,
    /// The pty4j native tree relative to the IDE home, when the product bundles the pty4j plugin.
    pub pty4j_native_dir: Option<String>,
    /// The Skiko native tree relative to the IDE home, when the product bundles the Skiko plugin.
    pub skiko_native_dir: Option<String>,
    pub runtime_module_repository: bool,
    pub root_module: Option<String>,
    pub product_mode: Option<String>,
    pub platform_prefix: Option<String>,
    pub additional: Vec<String>,
    pub splash: bool,
    pub native_access: bool,
}

impl Default for JvmArguments {
    /// The defaults of `ProductJvmArguments`.
    fn default() -> Self {
        Self {
            multi_routing_file_system: true,
            class_loader: Some("com.intellij.util.lang.PathClassLoader".to_owned()),
            jna_native_dir: None,
            pty4j_native_dir: None,
            skiko_native_dir: None,
            runtime_module_repository: false,
            root_module: None,
            product_mode: None,
            platform_prefix: None,
            additional: Vec::new(),
            splash: false,
            native_access: true,
        }
    }
}

/// The default of `ProductLaunchCommand.bootClassPathJarNames`: `PLATFORM_LOADER_JAR`.
fn default_boot_class_path_jar_names() -> Vec<String> {
    vec!["platform-loader.jar".to_owned()]
}

/// The parts of `bin/idea.properties`: the base file, then each addition after a newline, with `@@settings_dir@@`
/// replaced by the data directory base name, then the fatal error block when `fatal_error_notification` is set.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct IdeaProperties {
    /// The base file is `language-server/build/idea.properties`, not `community/bin/idea.properties`. The caller
    /// passes the base file, so this tool does not read the flag.
    #[serde(default)]
    #[expect(dead_code, reason = "the caller picks the base file by this field")]
    pub language_server_base: bool,
    #[serde(default)]
    pub additions: Vec<String>,
    /// Appends the fatal error block, whose text follows the EAP flag of the application info.
    #[serde(default)]
    pub fatal_error_notification: bool,
}

pub(crate) fn parse_launch_model(text: &[u8]) -> anyhow::Result<LaunchModel> {
    serde_json::from_slice(text).map_err(|error| anyhow::anyhow!("cannot read the launch model: {error}"))
}
