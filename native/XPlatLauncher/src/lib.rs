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
use serde::Deserialize;

#[cfg(target_os = "windows")]
use {
    std::io::Write,
    std::ptr::null_mut,
    windows::Win32::Foundation,
    windows::Win32::Foundation::HANDLE,
    windows::Win32::System::Console::{ATTACH_PARENT_PROCESS, AttachConsole, SetStdHandle, STD_ERROR_HANDLE, STD_OUTPUT_HANDLE},
    windows::Win32::System::LibraryLoader,
    windows::Win32::UI::Shell,
    windows::core::{HSTRING, GUID, PCWSTR, PWSTR},
};

#[cfg(target_family = "unix")]
use std::os::unix::fs::PermissionsExt;

use crate::cef_sandbox::CefScopedSandboxInfo;
use crate::default::DefaultLaunchConfiguration;
use crate::remote_dev::RemoteDevLaunchConfiguration;

pub mod mini_logger;
pub mod ui;
pub mod default;
pub mod remote_dev;
pub mod java;
pub mod docker;
pub mod cef_sandbox;

pub const DEBUG_MODE_ENV_VAR: &str = "IJ_LAUNCHER_DEBUG";

#[cfg(target_os = "windows")]
const CLASS_PATH_SEPARATOR: &str = ";";
#[cfg(target_family = "unix")]
const CLASS_PATH_SEPARATOR: &str = ":";

pub fn main_lib() {
    let exe_path = env::current_exe().unwrap_or_else(|_| PathBuf::from(env::args().next().unwrap()));
    let remote_dev_launcher_used = exe_path.file_name().unwrap().to_string_lossy().starts_with("remote-dev-server");
    let server_mode_argument_used = env::args().nth(1).map(|x| x == "serverMode").unwrap_or(false);
    let remote_dev = remote_dev_launcher_used || server_mode_argument_used;
    let sandbox_subprocess = cfg!(target_os = "windows") && env::args().any(|arg| arg.contains("--type="));

    let debug_mode = remote_dev || env::var(DEBUG_MODE_ENV_VAR).is_ok();

    #[cfg(target_os = "windows")]
    {
        if debug_mode && !sandbox_subprocess {
            attach_console();
        }
    }

    if let Err(e) = main_impl(exe_path, remote_dev, debug_mode, sandbox_subprocess, remote_dev_launcher_used) {
        ui::show_error(!debug_mode, e);
        std::process::exit(1);
    }
}

#[cfg(target_os = "windows")]
fn attach_console() {
    unsafe {
        let mut err = None;
        if AttachConsole(ATTACH_PARENT_PROCESS).is_err() {
            err = Some(Foundation::GetLastError());
        }

        // case: races when restarting in remote-dev on Windows
        // (ssh session -> tb proxy -> tb agent -> IDE -> restarter.exe (dying too fast) -> IDE)
        // cannot repro on a smaller setup (e.g. cmd /C via ssh)
        // * case: console is alive, but in a state of being closed
        // * AttachConsole does not always error
        // * GetStdHandle for STD_OUT_HANDLE returns without errors and the handle is not zero/invalid
        // * println!/eprintln! panics when it can't write
        // * setting various process creation flags in restarter does not seem to help
        // this is the only reliable way I've found to check if it's possible to call println! without panic
        if writeln!(std::io::stderr(), ".").is_err() {
            // usually it's Os { code: 232, kind: BrokenPipe, message: "The pipe is being closed." }
            // but even if it's some other error, let's not write there
            // passing null here is not explicitly documented,
            // but it works and is consistent with the GetStdHandle returning null
            if SetStdHandle(STD_ERROR_HANDLE, HANDLE(null_mut())).is_err() {
                std::process::exit(1011)
            }
        }

        if writeln!(std::io::stdout(), ".").is_err() {
            if SetStdHandle(STD_OUTPUT_HANDLE, HANDLE(null_mut())).is_err() {
                std::process::exit(1012)
            }
        }

        if let Some(err) = err {
            eprintln!("AttachConsole(ATTACH_PARENT_PROCESS): {:?}", err)
        }
    }
}

fn main_impl(exe_path: PathBuf, remote_dev: bool, debug_mode: bool, sandbox_subprocess: bool, started_via_remote_dev_launcher: bool) -> Result<()> {
    let level = if debug_mode { LevelFilter::Debug } else { LevelFilter::Error };
    mini_logger::init(level).expect("Cannot initialize the logger");
    debug!("Executable: {exe_path:?}");
    debug!("Mode: {}", if remote_dev { "remote-dev" } else { "standard" });

    #[cfg(target_os = "windows")]
    {
        // Windows namespace prefixes are misunderstood both by JVM and classloaders
        strip_working_directory_ns_prefix()?;
    }
    #[cfg(target_os = "macos")]
    {
        // on macOS, `open` doesn't preserve a current working directory
        restore_working_directory()?;
    }
    debug!("Current directory: {:?}", env::current_dir());

    #[cfg(target_os = "windows")]
    {
        // on Windows, the platform requires `[LOCAL]APPDATA` variables
        ensure_env_vars_set()?;
    }

    #[cfg(all(target_os = "linux", target_env = "gnu"))]
    {
        // on Linux, glibc allocates arenas too aggressively 
        if unsafe { libc::mallopt(libc::M_ARENA_MAX, 1) } == 0 {
            bail!(std::io::Error::last_os_error());
        }
    }

    debug!("** Preparing launch configuration");
    let configuration = get_configuration(remote_dev, &exe_path.strip_ns_prefix()?, started_via_remote_dev_launcher).context("Cannot detect a launch configuration")?;

    debug!("** Locating runtime");
    let (jre_home, main_class) = configuration.prepare_for_launch().context("Cannot find a runtime")?;
    debug!("Resolved runtime: {jre_home:?}");

    #[cfg(target_os = "windows")]
    {
        // on Windows, JRE and JCEF dependencies directory is not on the default DLL search path
        set_dll_search_path(&jre_home)?;
    }
    let cef_sandbox = init_cef_sandbox(&jre_home, sandbox_subprocess).context("Cannot initialize browser sandbox")?;

    debug!("** Collecting JVM options");
    let vm_options = get_full_vm_options(&*configuration, &cef_sandbox).context("Cannot collect JVM options")?;
    debug!("VM options: {vm_options:?}");

    debug!("** Launching JVM");
    let args = configuration.get_args();
    java::run_jvm_and_event_loop(&jre_home, vm_options, main_class, args.to_vec(), debug_mode).context("Cannot start the runtime")?;

    Ok(())
}

#[cfg(target_os = "windows")]
fn ensure_env_vars_set() -> Result<()> {
    let app_data = get_known_folder_path(&Shell::FOLDERID_RoamingAppData, "FOLDERID_RoamingAppData")?;
    env::set_var("APPDATA", app_data.strip_ns_prefix()?.to_string_checked()?);

    let local_app_data = get_known_folder_path(&Shell::FOLDERID_LocalAppData, "FOLDERID_LocalAppData")?;
    env::set_var("LOCALAPPDATA", local_app_data.strip_ns_prefix()?.to_string_checked()?);

    Ok(())
}

#[cfg(target_os = "windows")]
fn strip_working_directory_ns_prefix() -> Result<()> {
    let cwd_res = env::current_dir();
    debug!("Adjusting current directory {:?}", cwd_res);

    if let Ok(cwd) = cwd_res {
        let orig_len = cwd.as_os_str().len();
        if let Ok(stripped) = cwd.strip_ns_prefix() {
            if stripped.as_os_str().len() < orig_len {
                debug!("  ... to {:?}", stripped);
                env::set_current_dir(&stripped)
                    .with_context(|| format!("Cannot set current directory to '{}'", stripped.display()))?;
            }
        }
    }

    Ok(())
}

#[cfg(target_os = "windows")]
fn set_dll_search_path(jre_home: &Path) -> Result<()> {
    debug!("Setting DLL search path");
    let flags = LibraryLoader::LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LibraryLoader::LOAD_LIBRARY_SEARCH_USER_DIRS;
    unsafe { LibraryLoader::SetDefaultDllDirectories(flags) }
        .context("Failed to set JRE DLL dependencies search path")?;

    let jre_bin_dir = jre_home.join("bin");
    debug!("[JVM] Adding {:?} to the DLL search path", jre_bin_dir);
    let jre_bin_dir_cookie = unsafe { LibraryLoader::AddDllDirectory(&HSTRING::from(jre_bin_dir.as_path())) };
    if jre_bin_dir_cookie.is_null() {
        return Err(anyhow::Error::from(std::io::Error::last_os_error()))
            .context(format!("Failed to add '{}' to 'jvm.dll' dependencies search path", jre_bin_dir.display()));
    }

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
#[derive(Deserialize, Clone, Debug)]
pub struct ProductInfo {
    pub productCode: String,
    pub productVendor: String,
    pub envVarBaseName: String,
    pub dataDirectoryName: String,
    pub launch: Vec<ProductInfoLaunchField>
}

#[allow(non_snake_case)]
#[derive(Deserialize, Clone, Debug)]
pub struct ProductInfoLaunchField {
    pub vmOptionsFilePath: String,
    pub bootClassPathJarNames: Vec<String>,
    pub additionalJvmArguments: Vec<String>,
    pub mainClass: String,
    pub customCommands: Option<Vec<ProductInfoCustomCommandField>>,
}

#[allow(non_snake_case)]
#[derive(Deserialize, Clone, Debug)]
pub struct ProductInfoCustomCommandField {
    pub commands: Vec<String>,
    pub vmOptionsFilePath: Option<String>,
    #[serde(default = "Vec::new")]
    pub bootClassPathJarNames: Vec<String>,
    #[serde(default = "Vec::new")]
    pub additionalJvmArguments: Vec<String>,
    pub mainClass: Option<String>,
    pub envVarBaseName: Option<String>,
    pub dataDirectoryName: Option<String>,
}

pub trait LaunchConfiguration {
    fn get_args(&self) -> &[String];
    fn get_vm_options(&self) -> Result<Vec<String>>;
    fn get_properties_file(&self) -> Result<PathBuf>;
    fn get_class_path(&self) -> Result<Vec<String>>;
    fn prepare_for_launch(&self) -> Result<(PathBuf, &str)>;
}

fn get_configuration(is_remote_dev: bool, exe_path: &Path, started_via_remote_dev_launcher: bool) -> Result<Box<dyn LaunchConfiguration>> {
    let cmd_args: Vec<String> = env::args().collect();
    debug!("Args: {:?}", &cmd_args);

    if is_remote_dev {
        RemoteDevLaunchConfiguration::new(exe_path, cmd_args, started_via_remote_dev_launcher)
    } else {
        let configuration = DefaultLaunchConfiguration::new(exe_path, cmd_args[1..].to_vec())?;
        Ok(Box::new(configuration))
    }
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn init_cef_sandbox(jre_home: &Path, sandbox_subprocess: bool) -> Result<Option<CefScopedSandboxInfo>> {
    debug!("** Initializing CEF sandbox");
    let cef_sandbox = CefScopedSandboxInfo::new();

    if sandbox_subprocess {
        debug!("Starting a subprocess");
        let exit_code = unsafe {
            let helper_path = jre_home.join("bin\\jcef_helper.dll");
            let lib = libloading::Library::new(&helper_path)
                .with_context(|| format!("Cannot load '{:#?}'", helper_path))?;

            let proc: libloading::Symbol<'_, unsafe extern "system" fn(*mut std::os::raw::c_void, *mut std::os::raw::c_void) -> i32> = lib.get(b"execute_subprocess\0")
                .context("Cannot find 'execute_subprocess' in 'jcef_helper.dll'")?;

            let mut h_instance = LibraryLoader::GetModuleHandleW(PCWSTR::null())?;
            proc(&mut h_instance as *mut _ as *mut std::os::raw::c_void, cef_sandbox.ptr)
        };
        debug!("  finished: {}", exit_code);
        std::process::exit(exit_code);
    }

    Ok(Some(cef_sandbox))
}

#[cfg(not(all(target_os = "windows", feature = "cef")))]
fn init_cef_sandbox(_jre_home: &Path, _sandbox_subprocess: bool) -> Result<Option<CefScopedSandboxInfo>> {
    Ok(None)
}

fn get_full_vm_options(configuration: &dyn LaunchConfiguration, _cef_sandbox: &Option<CefScopedSandboxInfo>) -> Result<Vec<String>> {
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
    
    #[cfg(all(target_os = "windows", feature = "cef"))]
    {
        if let Some(cef_sandbox) = _cef_sandbox {
            vm_options.push(jvm_property!("jcef.sandbox.ptr", format!("{:016X}", cef_sandbox.ptr as usize)));
            vm_options.push(jvm_property!("jcef.sandbox.cefVersion", env!("CEF_VERSION")));
        }
    }

    Ok(vm_options)
}

#[cfg(target_os = "windows")]
pub fn get_config_home() -> Result<PathBuf> {
    get_known_folder_path(&Shell::FOLDERID_RoamingAppData, "FOLDERID_RoamingAppData")
}

#[cfg(target_os = "windows")]
pub fn get_caches_home() -> Result<PathBuf> {
    get_known_folder_path(&Shell::FOLDERID_LocalAppData, "FOLDERID_LocalAppData")
}

#[cfg(target_os = "windows")]
fn get_known_folder_path(rfid: &GUID, rfid_debug_name: &str) -> Result<PathBuf> {
    debug!("Calling SHGetKnownFolderPath({})", rfid_debug_name);
    let result: PWSTR = unsafe { Shell::SHGetKnownFolderPath(rfid, Shell::KF_FLAG_CREATE, Foundation::HANDLE::default()) }?;
    let result_str = unsafe { result.to_string() }?;
    debug!("  result: {}", result_str);
    Ok(PathBuf::from(result_str))
}

#[cfg(target_os = "macos")]
pub fn get_config_home() -> Result<PathBuf> {
    Ok(get_user_home()?.join("Library/Application Support"))
}

#[cfg(target_os = "macos")]
pub fn get_caches_home() -> Result<PathBuf> {
    Ok(get_user_home()?.join("Library/Caches"))
}

#[cfg(target_os = "linux")]
pub fn get_config_home() -> Result<PathBuf> {
    get_xdg_dir("XDG_CONFIG_HOME", ".config")
}

#[cfg(target_os = "linux")]
pub fn get_caches_home() -> Result<PathBuf> {
    get_xdg_dir("XDG_CACHE_HOME", ".cache")
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
        .map(PathBuf::from)
        .context("Cannot detect a user home directory")
}

#[cfg(target_family = "windows")]
fn win_user_profile_dir() -> Result<String> {
    let token = Foundation::HANDLE(-4isize as *mut std::ffi::c_void);  // as defined in `GetCurrentProcessToken()`
    let mut buf = [0u16; Foundation::MAX_PATH as usize];
    let mut size = buf.len() as u32;
    debug!("Calling GetUserProfileDirectoryW({:?})", token);
    let result = unsafe {
        Shell::GetUserProfileDirectoryW(token, PWSTR::from_raw(buf.as_mut_ptr()), std::ptr::addr_of_mut!(size))
    };
    debug!("  result: {:?}, size: {}", result, size);
    if result.is_ok() {
        Ok(String::from_utf16(&buf[0..(size - 1) as usize])?)
    } else {
        bail!("GetUserProfileDirectoryW(): {:?}", std::io::Error::last_os_error())
    }
}

#[cfg(target_family = "unix")]
#[allow(deprecated)]
fn get_user_home() -> Result<PathBuf> {
    env::home_dir().context("Cannot detect a user home directory")
}

pub fn get_path_from_env_var(env_var_name: &str, expecting_dir: bool) -> Result<PathBuf> {
    let env_var = env::var(env_var_name);
    debug!("${env_var_name} = {env_var:?}");
    get_path_from_user_config(&env_var?, expecting_dir)
}

pub fn get_path_from_user_config(config_raw: &str, expecting_dir: bool) -> Result<PathBuf> {
    let config_value = config_raw.trim();
    if config_value.is_empty() {
        bail!("Empty path");
    }

    let path = PathBuf::from(config_value);
    if expecting_dir && !path.is_dir() {
        bail!("Not a directory: {:?}", path);
    } else if !expecting_dir && !path.is_file() {
        bail!("Not a file: {:?}", path);
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
        Ok(if let Some(tail) = path_str.strip_prefix("\\\\?\\UNC\\") {
            PathBuf::from("\\\\".to_string() + tail)
        } else if let Some(tail) = path_str.strip_prefix("\\\\?\\") {
            PathBuf::from(tail)
        } else {
            self.to_path_buf()
        })
    }

    #[cfg(target_family = "unix")]
    fn strip_ns_prefix(&self) -> Result<PathBuf> {
        Ok(self.to_path_buf())
    }
}
