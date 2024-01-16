// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#![warn(
absolute_paths_not_starting_with_crate,
elided_lifetimes_in_paths,
explicit_outlives_requirements,
keyword_idents,
macro_use_extern_crate,
meta_variable_misuse,
missing_abi,
missing_copy_implementations,
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
use std::path::{Path, PathBuf};

use anyhow::{anyhow, bail, Context, Result};
use log::{debug, LevelFilter, warn};
use serde::{Deserialize, Serialize};

#[cfg(target_os = "windows")]
use {
    windows::core::{GUID, PWSTR},
    windows::Win32::Foundation,
    windows::Win32::UI::Shell
};

#[cfg(target_family = "unix")]
use std::os::unix::fs::PermissionsExt;

use crate::default::DefaultLaunchConfiguration;
use crate::remote_dev::RemoteDevLaunchConfiguration;

pub mod mini_logger;
pub mod ui;
pub mod default;
pub mod remote_dev;
pub mod java;
pub mod docker;

pub const DEBUG_MODE_ENV_VAR: &str = "IJ_LAUNCHER_DEBUG";

#[cfg(target_os = "windows")]
const CLASS_PATH_SEPARATOR: &str = ";";
#[cfg(target_family = "unix")]
const CLASS_PATH_SEPARATOR: &str = ":";

pub fn main_lib() {
    let exe_path = env::current_exe().unwrap_or_else(|_| PathBuf::from(env::args().next().unwrap()));
    let remote_dev = exe_path.file_name().unwrap().to_string_lossy().starts_with("remote-dev-server");
    let debug_mode = remote_dev || env::var(DEBUG_MODE_ENV_VAR).is_ok();

    if let Err(e) = main_impl(exe_path, remote_dev, debug_mode) {
        ui::show_error(!debug_mode, e);
        std::process::exit(1);
    }
}

fn main_impl(exe_path: PathBuf, remote_dev: bool, debug_mode: bool) -> Result<()> {
    let level = if debug_mode { LevelFilter::Debug } else { LevelFilter::Error };
    mini_logger::init(level).expect("Cannot initialize the logger");
    debug!("Executable: {exe_path:?}");
    debug!("Mode: {}", if remote_dev { "remote-dev" } else { "standard" });

    #[cfg(target_os = "macos")]
    {
        // on macOS, `open` doesn't properly set a current working directory
        restore_working_directory()?;
    }
    debug!("Current directory: {:?}", env::current_dir());

    debug!("** Preparing launch configuration");
    let configuration = get_configuration(remote_dev, &exe_path.strip_ns_prefix()?).context("Cannot detect a launch configuration")?;

    debug!("** Locating runtime");
    let (jre_home, main_class) = configuration.prepare_for_launch().context("Cannot find a runtime")?;
    debug!("Resolved runtime: {jre_home:?}");

    debug!("** Collecting JVM options");
    let vm_options = get_full_vm_options(&*configuration).context("Cannot collect JVM options")?;
    debug!("VM options: {vm_options:?}");

    debug!("** Launching JVM");
    let args = configuration.get_args();
    java::run_jvm_and_event_loop(&jre_home, vm_options, main_class, args.to_vec(), debug_mode).context("Cannot start the runtime")?;

    Ok(())
}

#[cfg(target_os = "macos")]
fn restore_working_directory() -> Result<()> {
    let (cwd_res, pwd_var) = (env::current_dir(), env::var("PWD"));
    debug!("Adjusting current directory (current={:?} $PWD={:?})", cwd_res, pwd_var);

    if let Ok(cwd) = cwd_res {
        if cwd == PathBuf::from("/") {
            if let Ok(pwd) = pwd_var {
                env::set_current_dir(&pwd)
                    .with_context(|| format!("Cannot set current directory to '{pwd}'"))?;
            }
        }
    }

    Ok(())
}

#[macro_export]
macro_rules! jvm_property {
    ( $name:expr, $value:expr ) => { format!("-D{}={}", $name, $value) }
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ProductInfo {
    pub productCode: String,
    pub productVendor: String,
    pub dataDirectoryName: String,
    pub launch: Vec<ProductInfoLaunchField>
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ProductInfoLaunchField {
    pub vmOptionsFilePath: String,
    pub bootClassPathJarNames: Vec<String>,
    pub additionalJvmArguments: Vec<String>,
    pub mainClass: String,
    pub customCommands: Option<Vec<ProductInfoCustomCommandField>>,
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ProductInfoCustomCommandField {
    pub commands: Vec<String>,
    pub vmOptionsFilePath: Option<String>,
    #[serde(default = "Vec::new")]
    pub bootClassPathJarNames: Vec<String>,
    #[serde(default = "Vec::new")]
    pub additionalJvmArguments: Vec<String>,
    pub mainClass: Option<String>,
    pub dataDirectoryName: Option<String>,
}

pub trait LaunchConfiguration {
    fn get_args(&self) -> &[String];
    fn get_vm_options(&self) -> Result<Vec<String>>;
    fn get_properties_file(&self) -> Result<PathBuf>;
    fn get_class_path(&self) -> Result<Vec<String>>;
    fn prepare_for_launch(&self) -> Result<(PathBuf, &str)>;
}

fn get_configuration(is_remote_dev: bool, exe_path: &Path) -> Result<Box<dyn LaunchConfiguration>> {
    let cmd_args: Vec<String> = env::args().collect();
    debug!("Args: {:?}", &cmd_args);

    if is_remote_dev {
        RemoteDevLaunchConfiguration::new(exe_path, cmd_args)
    } else {
        let configuration = DefaultLaunchConfiguration::new(exe_path, cmd_args[1..].to_vec())?;
        Ok(Box::new(configuration))
    }
}

fn get_full_vm_options(configuration: &dyn LaunchConfiguration) -> Result<Vec<String>> {
    let mut vm_options = configuration.get_vm_options()?;

    debug!("Looking for custom properties environment variable");
    match configuration.get_properties_file() {
        Ok(path) => {
            debug!("Custom properties file: {:?}", path);
            vm_options.push(jvm_property!("idea.properties.file", path.to_string_checked()?));
        }
        Err(e) => { debug!("Failed: {}", e.to_string()); }
    }

    debug!("Assembling classpath");
    let class_path = configuration.get_class_path()?.join(CLASS_PATH_SEPARATOR);
    vm_options.push(jvm_property!("java.class.path", class_path));

    Ok(vm_options)
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
    let result: PWSTR = unsafe { Shell::SHGetKnownFolderPath(rfid, Shell::KF_FLAG_CREATE, Foundation::HANDLE(0)) }?;
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

#[cfg(target_family = "windows")]
fn get_user_home() -> Result<PathBuf> {
    env::var("USERPROFILE")
        .or_else(|_| win_user_profile_dir())
        .map(|s| PathBuf::from(s))
        .context("Cannot detect a user home directory")
}

#[cfg(target_family = "windows")]
fn win_user_profile_dir() -> Result<String> {
    let token = Foundation::HANDLE(-4);  // as defined in `GetCurrentProcessToken()`; Windows 8+/Server 2012+
    let mut buf: [u16; Foundation::MAX_PATH as usize] = unsafe { std::mem::zeroed() };
    let mut size = buf.len() as u32;
    debug!("Calling GetUserProfileDirectoryW({:?})", token);
    let result = unsafe {
        Shell::GetUserProfileDirectoryW(token, PWSTR::from_raw(buf.as_mut_ptr()), std::ptr::addr_of_mut!(size))
    };
    debug!("  result: {:?}, size: {}", result, size);
    if result.is_ok() {
        Ok(String::from_utf16(&buf[0..(size - 1) as usize])?)
    } else {
        bail!("GetUserProfileDirectoryW(): {:?}", unsafe { Foundation::GetLastError() })
    }
}

#[cfg(target_family = "unix")]
#[allow(deprecated)]
fn get_user_home() -> Result<PathBuf> {
    env::home_dir().context("Cannot detect a user home directory")
}

pub fn get_path_from_env_var(env_var_name: &str, expecting_dir: Option<bool>) -> Result<PathBuf> {
    let env_var = env::var(env_var_name);
    debug!("${env_var_name} = {env_var:?}");
    get_path_from_user_config(&env_var?, expecting_dir)
}

pub fn get_path_from_user_config(config_raw: &str, expecting_dir: Option<bool>) -> Result<PathBuf> {
    let config_value = config_raw.trim();
    if config_value.is_empty() {
        bail!("Empty path");
    }

    let path = PathBuf::from(config_value);
    if let Some(expecting_dir) = expecting_dir {
        if expecting_dir && !path.is_dir() {
            bail!("Not a directory: {:?}", path);
        } else if !expecting_dir && !path.is_file() {
            bail!("Not a file: {:?}", path);
        }
    }

    Ok(path)
}

pub trait PathExt {
    fn parent_or_err(&self) -> Result<PathBuf>;
    fn to_string_checked(&self) -> Result<String>;
    fn is_executable(&self) -> Result<bool>;
    fn strip_ns_prefix(&self) -> Result<PathBuf>;
}

impl PathExt for Path {
    fn parent_or_err(&self) -> Result<PathBuf> {
        self.parent()
            .map(|p| p.to_path_buf())
            .ok_or_else(|| anyhow!("No parent dir for '{self:?}'"))
    }

    fn to_string_checked(&self) -> Result<String> {
        self.to_str()
            .map(|s| s.to_string())
            .ok_or_else(|| anyhow!("Inconvertible path: {:?}", self))
    }

    #[cfg(target_os = "windows")]
    fn is_executable(&self) -> Result<bool> {
        Ok(true)
    }

    #[cfg(target_family = "unix")]
    fn is_executable(&self) -> Result<bool> {
        let metadata = self.metadata()?;
        Ok(metadata.is_file() && (metadata.permissions().mode() & 0o111 != 0))
    }

    #[cfg(target_os = "windows")]
    fn strip_ns_prefix(&self) -> Result<PathBuf> {
        let path_str = self.to_string_checked()?;
        // Windows namespace prefixes are misunderstood both by JVM and classloaders
        Ok(if path_str.starts_with("\\\\?\\UNC\\") {
            PathBuf::from("\\\\".to_string() + &path_str[8..])
        } else if path_str.starts_with("\\\\?\\") {
            PathBuf::from(&path_str[4..])
        } else {
            self.to_path_buf()
        })
    }

    #[cfg(target_family = "unix")]
    fn strip_ns_prefix(&self) -> Result<PathBuf> {
        Ok(self.to_path_buf())
    }
}
