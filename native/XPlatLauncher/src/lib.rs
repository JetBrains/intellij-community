// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#![warn(
absolute_paths_not_starting_with_crate,
elided_lifetimes_in_paths,
explicit_outlives_requirements,
keyword_idents,
macro_use_extern_crate,
meta_variable_misuse,
missing_abi,
missing_copy_implementations,
missing_debug_implementations,
non_ascii_idents,
noop_method_call,
pointer_structural_match,
rust_2021_incompatible_closure_captures,
rust_2021_incompatible_or_patterns,
rust_2021_prefixes_incompatible_syntax,
rust_2021_prelude_collisions,
single_use_lifetimes,
trivial_numeric_casts,
unsafe_op_in_unsafe_fn,
unstable_features,
unused_crate_dependencies,
unused_extern_crates,
unused_import_braces,
unused_lifetimes,
unused_macro_rules,
unused_qualifications,
unused_results,
variant_size_differences
)]

use std::env;
use std::path::PathBuf;

use anyhow::Result;
use log::{debug, error, LevelFilter, warn};
use serde::{Deserialize, Serialize};
use utils::get_current_exe;

#[cfg(target_os = "windows")]
use {
    windows::core::{GUID, PWSTR},
    windows::Win32::Foundation::HANDLE,
    windows::Win32::UI::Shell
};

#[cfg(target_family = "unix")]
use anyhow::Context;

use crate::default::DefaultLaunchConfiguration;
use crate::remote_dev::RemoteDevLaunchConfiguration;

mod mini_logger;
mod ui;
mod java;
mod remote_dev;
mod default;
mod docker;

const CANNOT_START_TITLE: &'static str = "Cannot start the IDE";
const SUPPORT_CONTACT: &'static str = "For support, please refer to https://jb.gg/ide/critical-startup-errors";

#[cfg(target_os = "windows")]
const CLASS_PATH_SEPARATOR: &'static str = ";";
#[cfg(target_family = "unix")]
const CLASS_PATH_SEPARATOR: &'static str = ":";

pub fn main_lib() {
    let remote_dev = is_remote_dev();
    let show_error_ui = env::var(DO_NOT_SHOW_ERROR_UI_ENV_VAR).is_err() && !remote_dev;
    let verbose = env::var(VERBOSE_LOGGING_ENV_VAR).is_ok() || remote_dev;
    if let Err(e) = main_impl(remote_dev, verbose) {
        let text = format!("{:?}\n\n{}", e, SUPPORT_CONTACT);
        if show_error_ui {
            ui::show_fail_to_start_message(CANNOT_START_TITLE, text.as_str())
        } else if verbose {
            error!("{:?}", e);
        } else {
            eprintln!("{}\n{}", CANNOT_START_TITLE, text);
        }
        std::process::exit(1);
    }
}

fn is_remote_dev() -> bool {
    let exe_path = get_current_exe();
    if let Some(exe_name_value) = exe_path.file_name() {
        exe_name_value.to_string_lossy().starts_with("remote-dev-server")
    } else if let Some(exe_name_value) = env::args_os().next() {
        exe_name_value.to_string_lossy().starts_with("remote-dev-server")
    } else {
        false
    }
}

fn main_impl(remote_dev: bool, verbose: bool) -> Result<()> {
    let level = if verbose { LevelFilter::Debug } else { LevelFilter::Error };
    mini_logger::init(level).expect("Cannot initialize the logger");
    debug!("Mode: {}", if remote_dev { "remote-dev" } else { "standard" });

    // lets the panic on JVM thread crash the launcher (or not?)
    let orig_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |panic_info| {
        error!("{panic_info:?}");
        // TODO: crash on JVM thread
        // for l in &loggers {
        //     l.flush()
        // }

        orig_hook(panic_info);
        std::process::exit(1);
    }));

    debug!("** Preparing configuration");
    let configuration = &get_configuration(remote_dev)?;

    debug!("** Locating runtime");
    let java_home = &configuration.prepare_for_launch()?;
    debug!("Resolved runtime: {java_home:?}");

    debug!("** Resolving VM options");
    let vm_options = get_full_vm_options(configuration)?;

    debug!("** Launching JVM");
    let args = configuration.get_args();
    let result = java::run_jvm_and_event_loop(java_home, vm_options, args.to_vec());

    log::logger().flush();

    result
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ProductInfo {
    pub productCode: String,
    pub productVendor: String,
    pub dataDirectoryName: String,
    pub launch: Vec<ProductInfoLaunchField>,
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ProductInfoLaunchField {
    pub os: String,
    pub vmOptionsFilePath: String,
    pub bootClassPathJarNames: Vec<String>,
    pub additionalJvmArguments: Vec<String>
}

trait LaunchConfiguration {
    fn get_args(&self) -> &[String];

    fn get_intellij_vm_options(&self) -> Result<Vec<String>>;
    fn get_properties_file(&self) -> Result<Option<PathBuf>>;
    fn get_class_path(&self) -> Result<Vec<String>>;

    fn prepare_for_launch(&self) -> Result<PathBuf>;
}

fn get_configuration(is_remote_dev: bool) -> Result<Box<dyn LaunchConfiguration>> {
    let cmd_args: Vec<String> = env::args().collect();
    debug!("args={:?}", &cmd_args[1..]);

    let (remote_dev_project_path, jvm_args) = match is_remote_dev {
        true => {
            let remote_dev_args = RemoteDevLaunchConfiguration::parse_remote_dev_args(&cmd_args)?;
            (remote_dev_args.project_path, remote_dev_args.ij_args)
        },
        false => (None, cmd_args[1..].to_vec())
    };

    if is_remote_dev {
        // required for the most basic launch (e.g. showing help)
        // as there may be nothing on user system and we'll crash
        RemoteDevLaunchConfiguration::setup_font_config()?;
    }

    let default = DefaultLaunchConfiguration::new(jvm_args)?;

    match remote_dev_project_path {
        None => Ok(Box::new(default)),
        Some(x) => {
            let config = RemoteDevLaunchConfiguration::new(&x, default)?;
            Ok(Box::new(config))
        }
    }
}

pub const DO_NOT_SHOW_ERROR_UI_ENV_VAR: &str = "DO_NOT_SHOW_ERROR_UI";
pub const VERBOSE_LOGGING_ENV_VAR: &str = "IJ_LAUNCHER_DEBUG";

fn get_full_vm_options(configuration: &Box<dyn LaunchConfiguration>) -> Result<Vec<String>> {
    let mut full_vm_options: Vec<String> = Vec::new();

    debug!("Resolving IDE properties file");
    // 1. properties file
    match configuration.get_properties_file()? {
        Some(p) => {
            let path_string = p.to_string_lossy();
            let vm_option = format!("-Didea.properties.file={path_string}");
            full_vm_options.push(vm_option);
        }
        None => {
            debug!("IDE properties file is not set, skipping setting vm option")
        }
    };

    debug!("Resolving classpath");
    // 2. classpath
    let class_path = configuration.get_class_path()?.join(CLASS_PATH_SEPARATOR);
    let class_path_vm_option = "-Djava.class.path=".to_string() + class_path.as_str();
    full_vm_options.push(class_path_vm_option);

    debug!("Resolving IDE VM options");
    // 3. vmoptions
    let intellij_vm_options = configuration.get_intellij_vm_options()?;
    full_vm_options.extend_from_slice(&intellij_vm_options);

    Ok(full_vm_options)
}

#[cfg(target_os = "windows")]
pub fn get_config_home() -> Result<PathBuf> {
    get_known_folder_path(&Shell::FOLDERID_LocalAppData, "FOLDERID_LocalAppData")
}

#[cfg(target_os = "windows")]
pub fn get_cache_home() -> Result<PathBuf> {
    get_known_folder_path(&Shell::FOLDERID_RoamingAppData, "FOLDERID_RoamingAppData")
}

#[cfg(target_os = "windows")]
pub fn get_logs_home() -> Result<Option<PathBuf>> {
    Ok(None)
}

#[cfg(target_os = "windows")]
fn get_known_folder_path(rfid: &GUID, rfid_debug_name: &str) -> Result<PathBuf> {
    debug!("Calling SHGetKnownFolderPath({})", rfid_debug_name);
    let result: PWSTR = unsafe { Shell::SHGetKnownFolderPath(rfid, Shell::KF_FLAG_CREATE, HANDLE(0)) }?;
    let result_str = unsafe { result.to_string() }?;
    debug!("  result: {}", result_str);
    Ok(PathBuf::from(result_str))
}

#[cfg(target_os = "macos")]
pub fn get_config_home() -> Result<PathBuf> {
    Ok(get_user_home()?.join("Library/Application Support"))
}

#[cfg(target_os = "macos")]
pub fn get_cache_home() -> Result<PathBuf> {
    Ok(get_user_home()?.join("Library/Caches"))
}

#[cfg(target_os = "macos")]
pub fn get_logs_home() -> Result<Option<PathBuf>> {
    Ok(Some(get_user_home()?.join("Library/Logs")))
}

#[cfg(target_os = "linux")]
pub fn get_config_home() -> Result<PathBuf> {
    get_xdg_dir("XDG_CONFIG_HOME", ".config")
}

#[cfg(target_os = "linux")]
pub fn get_cache_home() -> Result<PathBuf> {
    get_xdg_dir("XDG_CACHE_HOME", ".cache")
}

#[cfg(target_os = "linux")]
pub fn get_logs_home() -> Result<Option<PathBuf>> {
    Ok(None)
}

#[cfg(target_os = "linux")]
fn get_xdg_dir(env_var_name: &str, fallback: &str) -> Result<PathBuf> {
    if let Ok(value) = env::var(env_var_name) {
        let path = PathBuf::from(value);
        if path.is_absolute() {
            return Ok(path)
        }
        warn!("{env_var_name} is not set to an absolute path ({path:?}), this is likely a misconfiguration");
    }

    Ok(get_user_home()?.join(fallback))
}

#[cfg(target_family = "unix")]
#[allow(deprecated)]
fn get_user_home() -> Result<PathBuf> {
    env::home_dir().context("Cannot detect a user home directory")
}

#[cfg(any(target_os = "linux", target_os = "windows"))]
pub fn is_running_in_docker() -> Result<bool> {
    docker::is_running_in_docker()
}

#[cfg(any(target_os = "linux"))]
pub fn is_docker_env_file_exist(home_path: Option<PathBuf>) -> Result<bool> {
    docker::is_docker_env_file_exist(home_path)
}

#[cfg(any(target_os = "linux"))]
pub fn is_docker_init_file_exist(home_path: Option<PathBuf>) -> Result<bool> {
    docker::is_docker_init_file_exist(home_path)
}

#[cfg(any(target_os = "linux"))]
pub fn is_control_group_matches_docker(cgroup_path: Option<PathBuf>) -> bool {
    docker::is_control_group_matches_docker(cgroup_path)
        .expect("Unable to detect Docker environment by cgroup file.")
}

#[cfg(any(target_os = "windows"))]
pub fn is_service_present(service_name: &str) -> bool {
    docker::is_service_present(service_name)
        .expect("Unable to detect Docker environment by getting windows service 'cexecsvc'.")
}
