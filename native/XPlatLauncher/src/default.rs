// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use std::env;
use std::fs::File;
use std::io::BufReader;
use std::path::{Path, PathBuf};
use log::{debug, warn};
use anyhow::{bail, Context, Result};
use utils::{canonical_non_unc, get_path_from_env_var, get_readable_file_from_env_var, is_readable, PathExt, read_file_to_end};
use crate::{LaunchConfiguration, ProductInfo};

pub struct DefaultLaunchConfiguration {
    pub product_info: ProductInfo,
    pub ide_home: PathBuf,
    pub config_home: PathBuf,
    pub ide_bin: PathBuf,
    pub args: Vec<String>,
    pub vm_options_base_filename: String
}

impl LaunchConfiguration for DefaultLaunchConfiguration {
    fn get_args(&self) -> &[String] {
        &self.args[..]
    }

    fn get_intellij_vm_options(&self) -> Result<Vec<String>> {
        let vm_options_from_files = self.get_merged_vm_options_from_files()?;
        let additional_jvm_arguments = &self.product_info.get_current_platform_launch_field()?.additionalJvmArguments;

        let mut result = Vec::with_capacity(vm_options_from_files.capacity() + additional_jvm_arguments.capacity());
        result.extend_from_slice(&vm_options_from_files);
        result.extend_from_slice(additional_jvm_arguments);

        for i in 0..result.len() {
            result[i] = self.expand_ide_home(&result[i]);
        }

        Ok(result)
    }

    fn get_properties_file(&self) -> Result<PathBuf> {
        let env_var_name = self.product_info.productCode.to_string() + "_PROPERTIES";
        let properties_path_from_env_var = get_path_from_env_var(&env_var_name);

        match &properties_path_from_env_var {
            Ok(x) => {
                debug!("IDE properties env var env_var_name is set to {x:?}")
            }
            Err(e) => {
                debug!("IDE properties env var {env_var_name} doesn't seem to be set, details: {e}");
            }
        };

        properties_path_from_env_var
    }

    fn get_class_path(&self) -> Result<Vec<String>> {
        let class_path = &self.product_info.launch[0].bootClassPathJarNames;
        let lib_path = get_lib_path(&self.ide_home);

        let lib_path_canonical = std::fs::canonicalize(lib_path)?;

        let mut canonical_class_path = Vec::new();

        for item in class_path {
            let item_path = lib_path_canonical.join(item);

            // JBR doesn't like UNC in classpath
            let item_canonical_path = match canonical_non_unc(&item_path) {
                Ok(x) => { x }
                Err(e) => {
                    match e.is::<std::io::Error>() {
                        true => {
                            // this handles non-existent file probably
                            warn!("{item_path:?}: IoError {e:?} when trying to get canonical path");
                            continue
                        }
                        false => bail!("Failed to get canonical non-UNC path for {item_path:?} {e:?}"),
                    }
                }
            };

            let expanded = self.expand_ide_home(&item_canonical_path);

            canonical_class_path.push(expanded);
        }

        Ok(canonical_class_path)
    }

    fn prepare_for_launch(&self) -> Result<PathBuf> {
        let java_executable = self.get_java_executable()?;
        let java_home = java_executable
            .parent_or_err()?
            .parent_or_err()?;

        return Ok(java_home.to_path_buf());
    }
}

impl DefaultLaunchConfiguration {
    pub fn new(args: Vec<String>) -> Result<Self> {
        let current_exe = &match get_path_from_env_var("XPLAT_LAUNCHER_CURRENT_EXE_PATH") {
            Ok(x) => {
                debug!("Using exe path from XPLAT_LAUNCHER_CURRENT_EXE_PATH: {x:?}");
                x
            }
            Err(_) => { env::current_exe()? }
        };

        debug!("Resolved current executable path as '{current_exe:?}'");

        let ide_home = get_ide_home(current_exe).context("Failed to resolve IDE home")?;
        debug!("Resolved ide home dir as '{ide_home:?}'");

        let ide_bin = ide_home.join("bin");
        debug!("Resolved ide bin dir as '{ide_bin:?}'");

        let config_home = get_config_home();
        debug!("Resolved config home as '{config_home:?}'");

        let product_info = get_product_info(&ide_home)?;
        assert!(!product_info.launch.is_empty());

        let vm_options_file_path = product_info.launch[0].vmOptionsFilePath.as_str();
        let vm_options_base_filename = Self::get_base_executable_name(vm_options_file_path);

        let config = DefaultLaunchConfiguration {
            product_info,
            ide_home,
            config_home,
            ide_bin,
            args,
            vm_options_base_filename
        };

        Ok(config)
    }

    // "bin/idea64.exe.vmoptions" -> idea
    // "bin/idea64.vmoptions -> idea
    // "../bin/idea.vmoptions" -> idea
    fn get_base_executable_name(vm_options_file_path: &str) -> String {
        debug!("vm_options_file_path={vm_options_file_path}");

        // split on last path separator
        // bin/idea64.exe.vmoptions -> idea64.exe.vmoptions
        let vm_options_filename = match vm_options_file_path.rsplit_once("/") {
            Some((_, suffix)) => { suffix }
            None => vm_options_file_path
        };
        debug!("vm_options_filename={vm_options_filename}");

        // split on first dot
        // idea64.exe.vmoptions -> idea64
        let vm_options_filename_no_last_extension = match vm_options_filename.split_once(".") {
            Some((prefix, _)) => { prefix }
            None => vm_options_filename
        };
        debug!("vm_options_filename_no_last_extension={vm_options_filename_no_last_extension}");

        // drop the 64 if it's there
        // idea64 -> idea
        let base_product_name = match vm_options_filename_no_last_extension.split_once("64") {
            Some((prefix, _)) => { prefix }
            None => vm_options_filename_no_last_extension
        };
        debug!("base_product_name={base_product_name}");

        base_product_name.to_string()
    }

    // TODO: potentially a behaviour change if _JDK env var is defined
    fn get_java_executable(&self) -> Result<PathBuf> {
        let product_code = &self.product_info.productCode;
        let product_code_env_var = &(product_code.to_owned() + "_JDK");

        debug!("Trying to resolve runtime from product code env var {product_code_env_var}");
        match self.get_java_executable_from_java_root_env_var(product_code_env_var) {
            Ok(p) => { return Ok(p); }
            Err(e) => { debug!("Didn't find runtime from env var: {product_code_env_var}, error: {e}") }
        }

        debug!("Trying to resolve runtime from custom user file");
        match self.get_user_jre() {
            Ok(p) => { return Ok(p) }
            Err(e) => { debug!("Didn't find runtime from custom user file, error: {e}") }
        }

        debug!("Resolving runtime jbr dir in ide home");
        match self.get_from_jbr_dir() {
            Ok(p) => { return Ok(p) }
            Err(e) => { debug!("Didn't find runtime from jbr dir in ide home. Error: {e}") }
        }

        debug!("Resolving runtime from default env vars");
        match self.get_from_java_env_vars() {
            Ok(p) => { return Ok(p) }
            Err(e) => { debug!("Didn't find runtime from jbr dir in ide home. Error: {e}") }
        }

        // TODO: timeout?
        // if [ -z "$JRE" ]; then
        //   JAVA_BIN=$(command -v java)
        // else
        //   JAVA_BIN="$JRE/bin/java"
        // fi

        let default_java_output = std::process::Command::new("command")
            .arg("-v")
            .arg("java")
            .output()?;

        let stderr = String::from_utf8_lossy(&default_java_output.stderr);
        let stdout = String::from_utf8_lossy(&default_java_output.stdout);
        let exit_code = match default_java_output.status.code() {
            None => { "None".to_string() }
            Some(i) => { i.to_string() }
        };

        if !default_java_output.status.success() {
            bail!("'command -v java' didn't succeed, exit code: {exit_code}, stdout: {stdout}, stderr: {stderr}");
        }

        // TODO: check if it's executable? (will be a behaviour change)
        let java_bin = Path::new(&stdout.to_string()).join("bin").join("java");
        Ok(java_bin)
    }

    // # shellcheck disable=SC2153
    // if [ -z "$JRE" ]; then
    //   if [ -n "$JDK_HOME" ] && [ -x "$JDK_HOME/bin/java" ]; then
    //     JRE="$JDK_HOME"
    //   elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    //     JRE="$JAVA_HOME"
    //   fi
    // fi
    fn get_from_java_env_vars(&self) -> Result<PathBuf> {
        match self.get_java_executable_from_java_root_env_var("JDK_HOME") {
            Ok(p) => { return Ok(p) }
            Err(e) => { debug!("Didn't find a valid runtime from JDK_HOME env var, error: {e}") }
        }

        return self.get_java_executable_from_java_root_env_var("JAVA_HOME");
    }

    // if [ -z "$JRE" ] && [ "$OS_TYPE" = "Linux" ] && [ "$OS_ARCH" = "x86_64" ] && [ -d "$IDE_HOME/jbr" ]; then
    // JRE="$IDE_HOME/jbr"
    // fi
    fn get_from_jbr_dir(&self) -> Result<PathBuf> {
        // TODO: check if that's actually used in other launchers, it probably is
        // if env::consts::OS != "linux" && env::consts::ARCH != "x86_64" {
        //     let message = format!("Can't use jbr dir in ide home for OS {} and ARCH {}", env::consts::OS, env::consts::ARCH);
        //     return err_from_string(message)
        // }

        let jbr_dir = self.ide_home.join("jbr");
        if !jbr_dir.is_dir() {
            bail!("{jbr_dir:?} is not a directory");
        };

        // TODO: non-mac
        let java_executable = get_bin_java_path(&jbr_dir);

        match is_executable::is_executable(&java_executable) {
            true => Ok(java_executable),
            // TODO: check if exists, separate method
            false => bail!("{java_executable:?} is not an executable")
        }
    }

    // if [ -z "$JRE" ] && [ -s "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__.jdk" ]; then
    // USER_JRE=$(cat "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__.jdk")
    // if [ -x "$USER_JRE/bin/java" ]; then
    // JRE="$USER_JRE"
    // fi

    // seems different from windows:
    // GetModuleFileName(NULL, buffer, _MAX_PATH);
    // std::wstring module(buffer);
    // IDS_VM_OPTIONS_PATH=%APPDATA%\\\\${context.applicationInfo.shortCompanyName}\\\\${context.systemSelector}
    // if (LoadString(hInst, IDS_VM_OPTIONS_PATH, buffer, _MAX_PATH)) {
    // ExpandEnvironmentStrings(buffer, copy, _MAX_PATH - 1);
    // std::wstring path(copy);
    // path += module.substr(module.find_last_of('\\')) + L".jdk";

    fn get_user_jre(&self) -> Result<PathBuf> {
        let jre_path_file_name = self.vm_options_base_filename.to_owned() + ".jdk";
        let jre_path_file = self.config_home
            .join(&self.product_info.productVendor)
            .join(&self.product_info.dataDirectoryName)
            .join(jre_path_file_name);

        let metadata = jre_path_file.metadata()?;
        if metadata.len() == 0 {
            bail!("vmoptions file by path {jre_path_file:?} has zero length, will not try to resolve runtime from it");
        }

        let content = read_file_to_end(jre_path_file.as_path())?;
        let user_jre_path = Path::new(content.as_str());

        let java_executable = get_bin_java_path(user_jre_path);

        match is_executable::is_executable(&java_executable) {
            true => { Ok(java_executable) }
            false => {
                bail!("{java_executable:?} specified in {jre_path_file:?} is not a valid executable");
            }
        }
    }

    // if [ -n "$__product_uc___JDK" ] && [ -x "$__product_uc___JDK/bin/java" ]; then
    // JRE="$__product_uc___JDK"
    // fi
    fn get_java_executable_from_java_root_env_var(&self, env_var_name: &str) -> Result<PathBuf> {
        let env_var_value = env::var(env_var_name)?;

        if env_var_value.is_empty() {
            bail!("Env var {env_var_value} is not set, skipping JDK detection from it");
        }

        let product_jdk_dir = Path::new(env_var_value.as_str());
        let java_executable = get_bin_java_path(product_jdk_dir);

        if !java_executable.exists() {
            bail!("Java executable from JDK {java_executable:?} does not exist");
        }

        // TODO: write the same code ourselves instead of using is_executable crate?
        if !is_executable::is_executable(&java_executable) {
            bail!("{java_executable:?} is not an executable file");
        }

        return Ok(java_executable);
    }

    // VM_OPTIONS=""
    // USER_GC=""
    // if [ -n "$USER_VM_OPTIONS_FILE" ]; then
    //   grep -E -q -e "-XX:\+.*GC" "$USER_VM_OPTIONS_FILE" && USER_GC="yes"
    // fi
    // if [ -n "$VM_OPTIONS_FILE" ] || [ -n "$USER_VM_OPTIONS_FILE" ]; then
    //   if [ -z "$USER_GC" ] || [ -z "$VM_OPTIONS_FILE" ]; then
    //     VM_OPTIONS=$(cat "$VM_OPTIONS_FILE" "$USER_VM_OPTIONS_FILE" 2> /dev/null | grep -E -v -e "^#.*")
    //   else
    //     VM_OPTIONS=$({ grep -E -v -e "-XX:\+Use.*GC" "$VM_OPTIONS_FILE"; cat "$USER_VM_OPTIONS_FILE"; } 2> /dev/null | grep -E -v -e "^#.*")
    //   fi
    // else
    //   message "Cannot find a VM options file"
    // fi
    pub fn get_merged_vm_options_from_files(&self) -> Result<Vec<String>> {
        let vm_options_file = self.get_vm_options_file();
        let user_vm_options_file = self.get_user_vm_options_file();

        let mut errors = Vec::new();

        let user_vm_options = match read_vm_options(user_vm_options_file) {
            Ok(opts) => { opts }
            Err(e) => {
                errors.push(e);
                Vec::new()
            }
        };

        let has_user_gc = user_vm_options.iter().any(|l| is_gc_vm_option(l));
        let vm_options = match read_vm_options(vm_options_file) {
            Ok(opts) => {
                if has_user_gc {
                    opts.into_iter().filter(|l| !is_gc_vm_option(l)).collect()
                } else {
                    opts
                }
            }
            Err(e) => {
                errors.push(e);
                Vec::new()
            }
        };

        if errors.len() > 1 {
            let user_vm_options_error = &errors[0];
            let vm_options_error = &errors[1];

            bail!("Failed to resolve any vmoptions files, user_vm_options: {user_vm_options_error:?}, vm_options: {vm_options_error:?}");
        }

        return Ok([vm_options, user_vm_options].concat());
    }

    fn get_vm_options_file_name(&self) -> Result<String> {
        let vm_options_base_file_name = (&self.vm_options_base_filename).to_owned();
        // TODO: there is a relative path in product-info json (launch), maybe use that?
        let vm_options_file_name = vm_options_base_file_name +
            match env::consts::OS {
                "linux" => "64.vmoptions",
                "macos" => ".vmoptions",
                //TODO: check if that's actual for Windows
                "windows" => "64.exe.vmoptions",
                unsupported_os => bail!("Unsupported OS: {unsupported_os}"),
            };

        Ok(vm_options_file_name)
    }

    // # ... [+ <IDE_HOME>.vmoptions (Toolbox) || <config_directory>/<bin_name>.vmoptions]
    // if [ -r "${IDE_HOME}.vmoptions" ]; then
    //   USER_VM_OPTIONS_FILE="${IDE_HOME}.vmoptions"
    // elif [ -r "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__64.vmoptions" ]; then
    //   USER_VM_OPTIONS_FILE="${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__64.vmoptions"
    // fi
    fn get_user_vm_options_file(&self) -> Result<PathBuf> {
        let options_from_toolbox = &self.ide_home.join(".vmoptions");
        match is_readable(options_from_toolbox) {
            Ok(f) => { return Ok(f) }
            Err(e) => {
                debug!("Didn't resolve vmoptions from {options_from_toolbox:?}, details: {e}")
            }
        }

        let vm_options_file_name = self.get_vm_options_file_name()?;

        let options_from_ide = self.config_home
            .join(&self.product_info.productVendor)
            .join(&self.product_info.dataDirectoryName)
            .join(vm_options_file_name);

        is_readable(options_from_ide)
    }

    // # shellcheck disable=SC2154
    // if [ -n "$__product_uc___VM_OPTIONS" ] && [ -r "$__product_uc___VM_OPTIONS" ]; then
    //   # 1. $<IDE_NAME>_VM_OPTIONS
    //   VM_OPTIONS_FILE="$__product_uc___VM_OPTIONS"
    // else
    //   # 2. <IDE_HOME>/bin/[<os>/]<bin_name>.vmoptions ...
    //   if [ -r "${IDE_BIN_HOME}/__vm_options__64.vmoptions" ]; then
    //     VM_OPTIONS_FILE="${IDE_BIN_HOME}/__vm_options__64.vmoptions"
    //   else
    //     test "${OS_TYPE}" = "Darwin" && OS_SPECIFIC="mac" || OS_SPECIFIC="linux"
    //     if [ -r "${IDE_BIN_HOME}/${OS_SPECIFIC}/__vm_options__64.vmoptions" ]; then
    //       VM_OPTIONS_FILE="${IDE_BIN_HOME}/${OS_SPECIFIC}/__vm_options__64.vmoptions"
    //     fi
    //   fi
    fn get_vm_options_file(&self) -> Result<PathBuf> {
        let product_code = (&self.product_info.productCode).to_owned();
        let env_var_name = product_code + "_VM_OPTIONS";

        match get_readable_file_from_env_var(env_var_name.as_str()) {
            Ok(f) => { return Ok(f) }
            Err(e) => { debug!("Didn't resolve vm options file from {env_var_name} env var, details: {e}") }
        };

        let vm_options_file_name = self.get_vm_options_file_name()?;
        let vm_options_from_ide_bin = &self.ide_bin.join(vm_options_file_name.as_str());
        match is_readable(vm_options_from_ide_bin) {
            Ok(f) => { return Ok(f); }
            Err(e) => { debug!("Didn't resolve vm options file from base bin dir {vm_options_from_ide_bin:?}, details: {e}") }
        }

        let os_specific_dir = match env::consts::OS {
            "linux" => "linux",
            "macos" => "mac",
            //TODO: check if that's actual for Windows
            "windows" => "windows",
            unsupported_os => bail!("Unsupported OS: {unsupported_os}"),
        };

        let os_specific_vm_options = self.ide_bin.join(os_specific_dir).join(vm_options_file_name);
        is_readable(os_specific_vm_options)
    }

    #[cfg(any(target_os = "linux", target_os = "macos"))]
    fn expand_ide_home(&self, value: &str) -> String {
        value.to_string()
    }

    #[cfg(target_os = "windows")]
    fn expand_ide_home(&self, value: &str) -> String {
        value.replace("%IDE_HOME%", &self.ide_home.to_string_lossy())
    }
}


#[cfg(target_os = "linux")]
fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home
        .join("bin")
        .join("java")
}

#[cfg(target_os = "windows")]
fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home
        .join("bin")
        .join("java.exe")
}

#[cfg(target_os = "macos")]
fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home
        .join("Contents")
        .join("Home")
        .join("bin")
        .join("java")
}

fn read_vm_options(path: Result<PathBuf>) -> Result<Vec<String>> {
    // let file = File::open(path?)?;
    let content = read_file_to_end(&path?)?;

    // TODO: Lines(self.split_terminator('\n').map(LinesAnyMap))
    let lines = content.lines()
        .filter(|l| !l.starts_with("#"))
        .map(|l| l.to_string())
        .collect();

    Ok(lines)
}

fn is_gc_vm_option(s: &str) -> bool {
    s.starts_with("-XX:+") && s.ends_with("GC")
}

#[cfg(target_os = "macos")]
fn get_lib_path(ide_home: &Path) -> PathBuf {
    ide_home
        .join("lib")
}

#[cfg(any(target_os = "linux", target_os = "windows"))]
fn get_lib_path(ide_home: &Path) -> PathBuf {
    ide_home.join("lib")
}

fn get_product_info_home(ide_home: &Path) -> Result<PathBuf> {
    let parent = match env::consts::OS {
        "linux" => ide_home.to_path_buf(),
        "macos" => ide_home.join("Resources"),
        //TODO: check if that's actual for Windows
        "windows" => ide_home.to_path_buf(),
        unsupported_os => bail!("Unsupported OS: {unsupported_os}"),
    };

    Ok(parent)
}

fn get_product_info(ide_home: &Path) -> Result<ProductInfo> {
    let product_info_path = get_product_info_home(ide_home)?.join("product-info.json");

    let file = File::open(product_info_path)?;

    let reader = BufReader::new(file);
    let product_info: ProductInfo = serde_json::from_reader(reader)?;
    let serialized = serde_json::to_string(&product_info)?;
    debug!("{serialized}");

    return Ok(product_info);
}

fn get_ide_home(current_exe: &Path) -> Result<PathBuf> {
    let max_lookup_count = 5;
    let mut ide_home = current_exe.parent_or_err()?;
    for _ in 0..max_lookup_count {
        debug!("Resolving ide_home, candidate: {ide_home:?}");

        let product_info_path = get_product_info_home(&ide_home)?.join("product-info.json");
        if product_info_path.exists() {
            return Ok(ide_home)
        }

        ide_home = ide_home.parent_or_err()?;
    }

    bail!("Failed to resolve ide_home in {max_lookup_count} attempts")
}

#[cfg(target_os = "windows")]
pub fn get_config_home() -> PathBuf {
    get_user_home().join(".config")
}

#[cfg(target_os = "macos")]
pub fn get_config_home() -> PathBuf {
    get_user_home().join(".config")
}

// CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/.config}"
#[cfg(target_os = "linux")]
pub fn get_config_home() -> PathBuf {
    let xdg_config_home = get_xdg_config_home();

    match xdg_config_home {
        Some(p) => { p }
        None => { get_user_home().join(".config") }
    }
}

#[cfg(target_os = "linux")]
fn get_xdg_config_home() -> Option<PathBuf> {
    let xdg_config_home = env::var("XDG_CONFIG_HOME").unwrap_or(String::from(""));
    debug!("XDG_CONFIG_HOME={xdg_config_home}");

    if xdg_config_home.is_empty() {
        return None
    }

    let path = PathBuf::from(xdg_config_home);
    if !path.is_absolute() {
        // TODO: consider change
        warn!("XDG_CONFIG_HOME is not set to an absolute path, this may be a misconfiguration");
    }

    Some(path)
}


// used in ${HOME}/.config
// TODO: is this the same as env:
#[cfg(any(target_os = "linux", target_os = "macos"))]
fn get_user_home() -> PathBuf {
    // TODO: dirs::home_dir seems better then just simply using $HOME as it checks `getpwuid_r`

    match env::var("HOME") {
        Ok(s) => {
            debug!("User home directory resolved as '{s}'");
            let path = PathBuf::from(s);
            if !path.is_absolute() {
                warn!("User home directory is not absolute, this may be a misconfiguration");
            }

            path
        }
        Err(e) => {
            // TODO: this seems wrong
            warn!("Failed to get $HOME env var value: {e}, using / as home dir");

            PathBuf::from("/")
        }
    }
}

#[cfg(target_os = "windows")]
fn get_user_home() -> PathBuf {
    match dirs::home_dir() {
        Some(path) => {
            debug!("User home directory resolved as '{path:?}'");

            path
        }
        None => {
            warn!("Failed to get User Home dir. Using '/' as home dir");

            PathBuf::from("/")
        }
    }
}