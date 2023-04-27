// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::env;
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use log::{debug, warn};
use utils::{canonical_non_unc, get_current_exe, get_path_from_env_var, get_path_from_user_config, is_executable, jvm_property, PathExt};

use crate::{get_config_home, LaunchConfiguration, ProductInfo, ProductInfoLaunchField};

const IDE_HOME_LOOKUP_DEPTH: usize = 5;

pub struct DefaultLaunchConfiguration {
    pub product_info: ProductInfo,
    launch_data_idx: usize,
    pub ide_home: PathBuf,
    pub vm_options_path: PathBuf,
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

    fn get_vm_options(&self) -> Result<Vec<String>> {
        let mut vm_options = Vec::with_capacity(100);

        // collecting JVM options from user and distribution files
        self.collect_vm_options_from_files(&mut vm_options)?;

        // appending product-specific VM options (non-overridable, so should come last)
        debug!("Appending product-specific VM options");
        vm_options.extend_from_slice(&self.launch_data().additionalJvmArguments);

        for vm_option in vm_options.iter_mut() {
            *vm_option = self.expand_vars(&vm_option)?;
        }

        Ok(vm_options)
    }

    fn get_properties_file(&self) -> Result<PathBuf> {
        let env_var_name = self.env_var_base_name.to_owned() + "_PROPERTIES";
        get_path_from_env_var(&env_var_name, Some(false))
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
        debug!("Executable path: {current_exe:?}");

        let (ide_home, product_info_file) = find_ide_home(&current_exe)?;
        debug!("IDE home dir: {ide_home:?}");

        let config_home = get_config_home()?;
        debug!("OS config dir: {config_home:?}");

        let product_info = read_product_info(&product_info_file)?;
        let launch_data_idx = Self::get_launch_data_idx(&product_info)?;
        let vm_options_rel_path = &product_info.launch[launch_data_idx].vmOptionsFilePath;
        let vm_options_path = product_info_file.parent().unwrap().join(vm_options_rel_path);
        let user_config_dir = config_home.join(&product_info.productVendor).join(&product_info.dataDirectoryName);
        let launcher_base_name = Self::get_launcher_base_name(vm_options_rel_path);
        let env_var_base_name = Self::get_env_var_base_name(&launcher_base_name);

        let config = DefaultLaunchConfiguration {
            product_info,
            launch_data_idx,
            ide_home,
            vm_options_path,
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
        let path = get_path_from_env_var(env_var_name, Some(true))?;
        Self::check_runtime_dir(&path)
    }

    fn get_runtime_from_user_config(&self) -> Result<PathBuf> {
        let config_ext = if env::consts::OS == "windows" { "64.exe.jdk" } else { ".jdk" };
        let config_name = self.launcher_base_name.to_owned() + config_ext;
        let config_path = self.user_config_dir.join(config_name);
        debug!("Reading {config_path:?}");
        let mut config_raw = String::new();
        let n = BufReader::new(File::open(&config_path)?).read_line(&mut config_raw)?;
        debug!("  {n} bytes");
        let path = get_path_from_user_config(&config_raw, Some(true))?;
        Self::check_runtime_dir(&path)
    }

    fn get_bundled_runtime(&self) -> Result<PathBuf> {
        let jbr_dir = self.ide_home.join("jbr");
        debug!("Checking {jbr_dir:?}");
        Self::check_runtime_dir(&jbr_dir)
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

    /// Reads VM options from both distribution and user-specific files and puts them into the given vector.
    ///
    /// When `<product>_VM_OPTIONS` environment variable points to an existing file, only its content is used;
    /// otherwise, the launcher merges the distribution and user-specific files.
    ///
    /// Distribution options come first, so users have can override default options with their own ones.
    /// This works, because JVM processes arguments in first-to-last, so the last one wins.
    /// The only exception is setting a garbage collector, so when a user sets one,
    /// the corresponding distribution option must be omitted.
    fn collect_vm_options_from_files(&self, vm_options: &mut Vec<String>) -> Result<()> {
        debug!("[1] Looking for custom VM options environment variable");
        let env_var_name = self.env_var_base_name.to_owned() + "_VM_OPTIONS";
        match get_path_from_env_var(&env_var_name, Some(false)) {
            Ok(path) => {
                debug!("Custom VM options file: {:?}", path);
                read_vm_options(&path, vm_options)?;
                let path_string = path.to_str().expect(&format!("Inconvertible path: {:?}", path));
                vm_options.push(jvm_property!("jb.vmOptionsFile", path_string));
                return Ok(());
            }
            Err(e) => { debug!("Failed: {}", e.to_string()); }
        }


        debug!("[2] Reading main VM options file: {:?}", self.vm_options_path);
        let mut dist_vm_options = Vec::with_capacity(50);
        read_vm_options(&self.vm_options_path, &mut dist_vm_options)?;
        debug!("{} lines", dist_vm_options.len());

        debug!("[3] Looking for user VM options file");
        let (user_vm_options, vm_options_path) = match self.get_user_vm_options_file() {
            Ok(path) => {
                debug!("Reading user VM options file: {:?}", path);
                let mut user_vm_options = Vec::with_capacity(50);
                read_vm_options(&path, &mut user_vm_options)?;
                debug!("{} lines", user_vm_options.len());
                (user_vm_options, path)
            }
            Err(e) => {
                debug!("Failed: {}", e.to_string());
                (Vec::new(), self.vm_options_path.clone())
            }
        };

        let has_user_gc = user_vm_options.iter().any(|l| is_gc_vm_option(l));
        if has_user_gc {
            vm_options.extend(dist_vm_options.into_iter().filter(|l| !is_gc_vm_option(l)))
        } else {
            vm_options.extend(dist_vm_options);
        }

        vm_options.extend(user_vm_options);

        let path_string = vm_options_path.to_str().expect(&format!("Inconvertible path: {:?}", vm_options_path));
        vm_options.push(jvm_property!("jb.vmOptionsFile", path_string));

        return Ok(());
    }

    /// Looks for user-editable config files near the installation (Toolbox-style)
    /// or under the OS standard configuration directory.
    fn get_user_vm_options_file(&self) -> Result<PathBuf> {
        let real_ide_home = if env::consts::OS == "macos" { self.ide_home.parent().unwrap() } else { &self.ide_home };
        let tb_file_base = real_ide_home.file_name().unwrap().to_str().unwrap();
        let tb_file_path = real_ide_home.parent().unwrap().join(tb_file_base.to_string() + ".vmoptions");
        debug!("Checking {:?}", tb_file_path);
        if tb_file_path.is_file() {
            return Ok(tb_file_path);
        }

        let user_file_name = self.vm_options_path.file_name().unwrap();
        let user_file_path = self.user_config_dir.join(user_file_name);
        debug!("Checking {:?}", user_file_path);
        if user_file_path.is_file() {
            return Ok(user_file_path);
        }

        bail!("User-editable config files not found");
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

fn read_vm_options(path: &Path, vm_options: &mut Vec<String>) -> Result<()> {
    let file = File::open(path)?;
    let error = format!("Cannot read: {:?}", path);
    BufReader::new(file).lines()
        .map(|l| l.expect(&error).trim().to_string())
        .filter(|l| !(l.is_empty() || l.starts_with("#")))
        .for_each(|l| vm_options.push(l));

    Ok(())
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
