// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::env;
use std::fs::File;
use std::io::BufReader;
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use log::{debug, warn};
use utils::{canonical_non_unc, get_current_exe, get_path_from_env_var, get_readable_file_from_env_var, is_executable, is_readable, PathExt, read_file_to_end};

use crate::{get_config_home, LaunchConfiguration, ProductInfo, ProductInfoLaunchField};

const IDE_HOME_LOOKUP_DEPTH: usize = 5;

pub struct DefaultLaunchConfiguration {
    pub product_info: ProductInfo,
    launch_data_idx: usize,
    pub ide_home: PathBuf,
    pub ide_bin: PathBuf,
    pub user_config_dir: PathBuf,
    pub args: Vec<String>,
    pub launcher_base_name: String,
    pub env_var_base_name: String
}

impl DefaultLaunchConfiguration {
    fn launch_data(&self) -> &ProductInfoLaunchField {
        &self.product_info.launch[self.launch_data_idx]
    }
}

impl LaunchConfiguration for DefaultLaunchConfiguration {
    fn get_args(&self) -> &[String] {
        &self.args[..]
    }

    fn get_intellij_vm_options(&self) -> Result<Vec<String>> {
        let vm_options_from_files = self.get_merged_vm_options_from_files()?;
        let additional_jvm_arguments = &self.launch_data().additionalJvmArguments;

        let mut result = Vec::with_capacity(vm_options_from_files.capacity() + additional_jvm_arguments.capacity());
        result.extend_from_slice(&vm_options_from_files);
        result.extend_from_slice(additional_jvm_arguments);

        for i in 0..result.len() {
            result[i] = self.expand_vars(&result[i])?;
        }

        Ok(result)
    }

    fn get_properties_file(&self) -> Result<Option<PathBuf>> {
        let env_var_name = self.product_info.productCode.to_string() + "_PROPERTIES";
        let properties_path_from_env_var = get_path_from_env_var(&env_var_name);

        let properties_file = match properties_path_from_env_var {
            Ok(x) => Some(x),
            Err(_) => {
                debug!("Properties env var {env_var_name} is not set, skipping reading properties file from it");
                None
            }
        };

        Ok(properties_file)
    }

    fn get_class_path(&self) -> Result<Vec<String>> {
        let class_path = &self.launch_data().bootClassPathJarNames;
        let lib_path = &self.ide_home.join("lib");

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

            let expanded = self.expand_vars(&item_canonical_path)?;

            canonical_class_path.push(expanded);
        }

        Ok(canonical_class_path)
    }

    fn prepare_for_launch(&self) -> Result<PathBuf> {
        let java_executable = self.locate_runtime()?;
        let java_home = java_executable
            .parent_or_err()?
            .parent_or_err()?;

        return Ok(java_home.to_path_buf());
    }
}

impl DefaultLaunchConfiguration {
    pub fn new(args: Vec<String>) -> Result<Self> {
        let current_exe = get_current_exe();
        debug!("Executable path: '{current_exe:?}'");

        let (ide_home, product_info_file) = find_ide_home(&current_exe)?;
        debug!("IDE home dir: '{ide_home:?}'");

        let ide_bin = ide_home.join("bin");
        debug!("IDE bin dir: '{ide_bin:?}'");

        let config_home = get_config_home()?;
        debug!("OS config dir: '{config_home:?}'");

        let product_info = read_product_info(&product_info_file)?;
        let launch_data_idx = Self::get_launch_data_idx(&product_info)?;

        let user_config_dir = config_home.join(&product_info.productVendor).join(&product_info.dataDirectoryName);

        let vm_options_file_path = product_info.launch[launch_data_idx].vmOptionsFilePath.as_str();
        let launcher_base_name = Self::get_launcher_base_name(vm_options_file_path);
        let env_var_base_name = Self::get_env_var_base_name(&launcher_base_name);

        let config = DefaultLaunchConfiguration {
            product_info,
            launch_data_idx,
            ide_home,
            ide_bin,
            user_config_dir,
            args,
            launcher_base_name,
            env_var_base_name
        };

        Ok(config)
    }

    /// Locates the OS-specific launch information block, when there are more than one.
    fn get_launch_data_idx(product_info: &ProductInfo) -> Result<usize> {
        match product_info.launch.len() {
            0 => bail!("Product descriptor is corrupted: 'launch' field is missing"),
            1 => Ok(0),
            _ => match product_info.launch.iter().enumerate().find(|(_, rec)| rec.os.to_lowercase() == env::consts::OS) {
                Some((idx, _)) => Ok(idx),
                None => bail!("Product descriptor is corrupted: no 'launch' field for '{}'", env::consts::OS)
            },
        }
    }

    /// Extracts a base name (i.e. a name without the extension and architecture suffix)
    /// from a relative path to the VM options file.
    ///
    /// Example: `"bin/idea64.exe.vmoptions"` (Windows), `"bin/idea.vmoptions"` (macOS),
    /// and`"bin/idea64.vmoptions"` (Linux) should all return `"idea"`.
    fn get_launcher_base_name(vm_options_rel_path: &str) -> String {
        // split on the last path separator ("bin/idea64.exe.vmoptions" -> "idea64.exe.vmoptions")
        let vm_options_filename = match vm_options_rel_path.rsplit_once("/") {
            Some((_, suffix)) => suffix,
            None => vm_options_rel_path
        };

        // split on the first dot ("idea64.exe.vmoptions" -> "idea64")
        let vm_options_filename_no_last_extension = match vm_options_filename.split_once(".") {
            Some((prefix, _)) => prefix,
            None => vm_options_filename
        };

        // strip the "64" suffix ("idea64" -> "idea")
        let base_product_name = match vm_options_filename_no_last_extension.split_once("64") {
            Some((prefix, _)) => prefix,
            None => vm_options_filename_no_last_extension
        };

        debug!("get_launcher_base_name('{vm_options_rel_path}') -> {base_product_name}");
        base_product_name.to_string()
    }

    /// Converts a launcher base name (extracted from a VM options relative path),
    /// to a base name of product-specific environment variables (like `<PRODUCT>_JDK`).
    ///
    /// See also: `org.jetbrains.intellij.build.ProductProperties#getEnvironmentVariableBaseName`.
    fn get_env_var_base_name(launcher_base_name: &str) -> String {
        match launcher_base_name {
            "webstorm" => "WEBIDE".to_string(),
            "idea-dbst" => "IDEA".to_string(),
            _ => launcher_base_name.to_ascii_uppercase().replace('-', "_")
        }
    }

    /// Locates the Java runtime and returns a path tpo the standard launcher (`bin/java` or `bin\\java.exe`).
    /// The lookup sequence is described in the [support article](https://intellij-support.jetbrains.com/hc/en-us/articles/206544879-Selecting-the-JDK-version-the-IDE-will-run-under).
    fn locate_runtime(&self) -> Result<PathBuf> {
        debug!("[1] Looking for runtime at product-specific environment variable");
        let product_env_var = self.env_var_base_name.to_owned() + "_JDK";
        match self.get_runtime_from_env_var(&product_env_var) {
            Ok(p) => { return Ok(p); }
            Err(e) => { debug!("Failed: {}", e.to_string()); }
        }

        debug!("[2] Looking for runtime in a user configuration file");
        match self.get_runtime_from_user_config() {
            Ok(p) => { return Ok(p) }
            Err(e) => { debug!("Failed: {}", e.to_string()); }
        }

        debug!("[3] Looking for bundled runtime");
        match self.get_bundled_runtime() {
            Ok(p) => { return Ok(p) }
            Err(e) => { debug!("Failed: {}", e.to_string()); }
        }

        debug!("[4] Looking for runtime at JDK_HOME");
        match self.get_runtime_from_env_var("JDK_HOME") {
            Ok(p) => { return Ok(p); }
            Err(e) => { debug!("Failed: {}", e.to_string()); }
        }

        debug!("[5] Looking for runtime at JAVA_HOME");
        match self.get_runtime_from_env_var("JAVA_HOME") {
            Ok(p) => { return Ok(p); }
            Err(e) => { debug!("Failed: {}", e.to_string()); }
        }

        bail!("Runtime not found")
    }

    fn get_runtime_from_env_var(&self, env_var_name: &str) -> Result<PathBuf> {
        let env_var = env::var(env_var_name);
        debug!("${env_var_name} = {env_var:?}");
        let env_var_value = env_var?;
        Self::check_runtime(&env_var_value)
    }

    fn get_runtime_from_user_config(&self) -> Result<PathBuf> {
        let config_ext = if env::consts::OS == "windows" { "64.exe.jdk" } else { ".jdk" };
        let config_name = self.launcher_base_name.to_owned() + config_ext;
        let config_path = self.user_config_dir.join(config_name);
        debug!("Reading {config_path:?}");
        let config_value = read_file_to_end(config_path.as_path())?;
        debug!("Content: {:?}", config_value.trim());
        Self::check_runtime(&config_value)
    }

    fn get_bundled_runtime(&self) -> Result<PathBuf> {
        let jbr_dir = self.ide_home.join("jbr");
        debug!("Checking {jbr_dir:?}");
        Self::check_runtime_dir(&jbr_dir)
    }

    fn check_runtime(path: &str) -> Result<PathBuf> {
        let path_trimmed = path.trim();
        if path_trimmed.is_empty() {
            bail!("Path is empty");
        }
        Self::check_runtime_dir(Path::new(path_trimmed))
    }

    fn check_runtime_dir(runtime_home: &Path) -> Result<PathBuf> {
        let java_executable = get_bin_java_path(&runtime_home);
        if !java_executable.exists() {
            bail!("Java executable not found at {java_executable:?}");
        }
        if !is_executable(&java_executable)? {
            bail!("Not an executable file: {java_executable:?}");
        }
        Ok(java_executable)
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
        let vm_options_base_file_name = (&self.launcher_base_name).to_owned();
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

        let options_from_ide = self.user_config_dir.join(vm_options_file_name);

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

    fn expand_vars(&self, value: &str) -> Result<String> {
        let (from, to) = match env::consts::OS {
            "macos"   => ("$APP_PACKAGE/Contents", self.ide_home.to_string_lossy()),
            "windows" => ("%IDE_HOME%", self.ide_home.to_string_lossy()),
            "linux"   => return Ok(value.to_string()),
            unsupported_os => bail!("Unsupported OS: {unsupported_os}"),
        };

        Ok(value.replace(from, &to))
    }
}

#[cfg(target_os = "linux")]
fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home.join("bin/java")
}

#[cfg(target_os = "windows")]
fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home.join("bin\\java.exe")
}

#[cfg(target_os = "macos")]
fn get_bin_java_path(java_home: &Path) -> PathBuf {
    java_home.join("Contents/Home/bin/java")
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

fn read_product_info(product_info_path: &Path) -> Result<ProductInfo> {
    let file = File::open(product_info_path)?;
    let reader = BufReader::new(file);
    let product_info: ProductInfo = serde_json::from_reader(reader)?;
    debug!("{:?}", serde_json::to_string(&product_info));
    return Ok(product_info);
}

fn find_ide_home(current_exe: &Path) -> Result<(PathBuf, PathBuf)> {
    let product_info_rel_path = if env::consts::OS == "macos" { "Resources/product-info.json" } else { "product-info.json" };
    debug!("Looking for: '{product_info_rel_path}'");

    let mut candidate = current_exe.parent_or_err()?;
    for _ in 0..IDE_HOME_LOOKUP_DEPTH {
        debug!("Probing for IDE home: {:?}", candidate);
        let product_info_path = candidate.join(product_info_rel_path);
        if product_info_path.is_file() {
            return Ok((candidate, product_info_path))
        }
        candidate = candidate.parent_or_err()?;
    }

    bail!("Cannot find a directory with a product descriptor.\nPlease try to reinstall the IDE.")
}
