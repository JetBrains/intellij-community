// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `ProductLaunchModel` of `ProductLaunchModel.kt`. The Kotlin encoder leaves out every field that holds its
//! default value, so a field with a Kotlin default takes that default here. A field without a Kotlin default is
//! required, because the encoder always writes it.
//!
//! The model has no `jbr17`, `xBootClassPathJarNames` or `cdsArchiveFileName`, because no dev-dist model sets them.
//! Thus the parser refuses a model that sets one as an unknown field.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct LaunchModel {
    pub product_code: String,
    pub build_number: String,
    pub product_name: String,
    pub version: String,
    pub version_suffix: Option<String>,
    pub env_var_base_name: String,
    pub data_directory_name: String,
    #[serde(default)]
    pub svg_icon: bool,
    pub product_vendor: String,
    pub major_version_release_date: String,
    pub min_required_java_version: i32,
    #[serde(default)]
    pub custom_properties: Vec<LaunchProperty>,
    #[serde(default)]
    pub flavors: Vec<String>,
    pub base_file_name: String,
    #[serde(default)]
    pub language_server: bool,
    pub launch: LaunchCommand,
    #[serde(default)]
    pub custom_commands: Vec<CustomCommand>,
    /// The lines of the vmoptions file, keyed by `OsFamily.osName`.
    pub vm_options: HashMap<String, Vec<String>>,
    pub idea_properties: IdeaProperties,
}

/// A custom property. `product-info.json` writes it with the same fields.
#[derive(Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct LaunchProperty {
    pub key: String,
    pub value: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub(crate) struct LaunchCommand {
    pub main_class: String,
    pub boot_class_path_jar_names: Vec<String>,
    pub jvm_arguments: JvmArguments,
    pub stdio_redirect_arg: Option<String>,
    pub linux_startup_wm_class: String,
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
    pub jvm_arguments: Option<JvmArguments>,
    /// Renders `jvm_arguments` the way Qodana starts: without the multi-routing file system.
    #[serde(default)]
    pub qodana: bool,
    #[serde(default)]
    pub mac_jvm_arguments: Vec<String>,
    #[serde(default)]
    pub extra_jvm_arguments: Vec<String>,
    pub main_class: Option<String>,
    pub env_var_base_name: Option<String>,
    pub data_directory_name: Option<String>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
#[expect(clippy::struct_excessive_bools, reason = "each field is one JVM switch of the product model")]
pub(crate) struct JvmArguments {
    #[serde(default)]
    pub multi_routing_file_system: bool,
    pub class_loader: Option<String>,
    pub vendor_name: String,
    pub paths_selector: String,
    #[serde(default)]
    pub jna: bool,
    #[serde(default)]
    pub pty4j: bool,
    #[serde(default)]
    pub skiko: bool,
    #[serde(default)]
    pub runtime_module_repository: bool,
    pub root_module: Option<String>,
    pub product_mode: Option<String>,
    pub platform_prefix: Option<String>,
    #[serde(default)]
    pub additional: Vec<String>,
    #[serde(default)]
    pub splash: bool,
    #[serde(default)]
    pub native_access: bool,
}

/// The parts of `bin/idea.properties`: the base file, then each addition after a newline, with `@@settings_dir@@`
/// replaced by `settings_dir`, then `suffix`.
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
    pub settings_dir: String,
    #[serde(default)]
    pub suffix: String,
}

pub(crate) fn parse_launch_model(text: &[u8]) -> anyhow::Result<LaunchModel> {
    serde_json::from_slice(text).map_err(|error| anyhow::anyhow!("cannot read the launch model: {error}"))
}
