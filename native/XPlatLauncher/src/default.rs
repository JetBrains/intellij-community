// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use log::debug;

use crate::*;

const IDE_HOME_LOOKUP_DEPTH: usize = 5;

#[cfg(not(target_os = "macos"))]
const PRODUCT_INFO_REL_PATH: &str = "product-info.json";
#[cfg(target_os = "macos")]
const PRODUCT_INFO_REL_PATH: &str = "Resources/product-info.json";

#[cfg(target_os = "windows")]
const PATH_MACRO: &str = "%IDE_HOME%";
#[cfg(target_os = "macos")]
const PATH_MACRO: &str = "$APP_PACKAGE/Contents";
#[cfg(target_os = "linux")]
const PATH_MACRO: &str = "$IDE_HOME";

pub struct DefaultLaunchConfiguration {
    pub product_info: ProductInfo,
    pub launch_info: ProductInfoLaunchField,
    pub ide_home: PathBuf,
    pub vm_options_path: PathBuf,
    pub user_config_dir: PathBuf,
    pub args: Vec<String>,
    pub launcher_base_name: String,
    pub env_var_base_name: String
}

impl LaunchConfiguration for DefaultLaunchConfiguration {
    fn get_args(&self) -> &[String] {
        &self.args[..]
    }

    fn get_vm_options(&self) -> Result<Vec<String>> {
        let mut vm_options = Vec::with_capacity(100);

        // error file locations go first (users should be able to override them)
        let user_home_path = get_user_home()?.to_string_checked()?;
        let slash = std::path::MAIN_SEPARATOR;
        vm_options.push(format!("-XX:ErrorFile={user_home_path}{slash}java_error_in_{}_%p.log", self.launcher_base_name));
        vm_options.push(format!("-XX:HeapDumpPath={user_home_path}{slash}java_error_in_{}.hprof", self.launcher_base_name));

        // collecting JVM options from user and distribution files
        self.collect_vm_options_from_files(&mut vm_options)?;

        // appending product-specific VM options (non-overridable, so should come last)
        debug!("Appending product-specific VM options");
        vm_options.extend_from_slice(&self.launch_info.additionalJvmArguments);

        for vm_option in vm_options.iter_mut() {
            *vm_option = self.expand_path_macro(vm_option)?;
        }

        vm_options.push(jvm_property!("ide.native.launcher", "true"));

        Ok(vm_options)
    }

    fn get_properties_file(&self) -> Result<PathBuf> {
        let env_var_name = self.env_var_base_name.to_owned() + "_PROPERTIES";
        get_path_from_env_var(&env_var_name, false)
    }

    fn get_class_path(&self) -> Result<Vec<String>> {
        let lib_path = self.ide_home.join("lib").to_string_checked()?;
        let class_path = self.launch_info.bootClassPathJarNames.iter()
            .map(|item| lib_path.to_string() + std::path::MAIN_SEPARATOR_STR + item)
            .collect();
        Ok(class_path)
    }

    fn prepare_for_launch(&self) -> Result<(PathBuf, &str)> {
        let jre_home = self.locate_runtime()?.strip_ns_prefix()?;
        Ok((jre_home, &self.launch_info.mainClass))
    }
}

impl DefaultLaunchConfiguration {
    pub fn new(exe_path: &Path, args: Vec<String>) -> Result<Self> {
        let (ide_home, product_info_file) = find_ide_home(exe_path)
            .with_context(|| format!("Cannot find a directory with a product descriptor near '{}'", exe_path.display()))?;
        debug!("IDE home dir: {ide_home:?}");

        let config_home = get_config_home()?;
        debug!("OS config dir: {config_home:?}");

        let product_info = read_product_info(&product_info_file)?;
        let (launch_info, custom_data_directory_name) =
            compute_launch_info(&product_info, args.first())?;
        let vm_options_rel_path = &launch_info.vmOptionsFilePath;
        let vm_options_path = product_info_file.parent().unwrap().join(vm_options_rel_path);
        let data_directory_name = custom_data_directory_name.unwrap_or(product_info.dataDirectoryName.clone());
        let user_config_dir = config_home.join(&product_info.productVendor).join(data_directory_name);
        let launcher_base_name = Self::get_launcher_base_name(vm_options_rel_path);
        let env_var_base_name = Self::get_env_var_base_name(&launcher_base_name);

        let config = DefaultLaunchConfiguration {
            product_info,
            launch_info,
            ide_home,
            vm_options_path,
            user_config_dir,
            args,
            launcher_base_name,
            env_var_base_name
        };

        Ok(config)
    }

    /// Extracts a base name (i.e., a name without the extension and architecture suffix)
    /// from a relative path to the VM options file.
    ///
    /// Example: `"bin/idea64.exe.vmoptions"` (Windows), `"bin/idea.vmoptions"` (macOS),
    /// and`"bin/idea64.vmoptions"` (Linux) should all return `"idea"`.
    fn get_launcher_base_name(vm_options_rel_path: &str) -> String {
        // split on the last path separator ("bin/idea64.exe.vmoptions" -> "idea64.exe.vmoptions")
        let vm_options_filename = match vm_options_rel_path.rsplit_once('/') {
            Some((_, suffix)) => suffix,
            None => vm_options_rel_path
        };

        // split on the first dot ("idea64.exe.vmoptions" -> "idea64")
        let vm_options_filename_no_last_extension = match vm_options_filename.split_once('.') {
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

    /// Locates the Java runtime and returns a path to the standard launcher (`bin/java` or `bin\\java.exe`).
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
        let path = get_path_from_env_var(env_var_name, true)?;
        Self::check_runtime_dir(&path)
    }

    fn get_runtime_from_user_config(&self) -> Result<PathBuf> {
        let config_ext = if cfg!(target_os = "windows") { "64.exe.jdk" } else { ".jdk" };
        let config_name = self.launcher_base_name.to_owned() + config_ext;
        let config_path = self.user_config_dir.join(config_name);
        debug!("Reading {config_path:?}");
        let mut config_raw = String::new();
        let n = BufReader::new(File::open(&config_path)?).read_line(&mut config_raw)?;
        debug!("  {n} bytes");
        let path = get_path_from_user_config(&config_raw, true)?;
        Self::check_runtime_dir(&path)
    }

    fn get_bundled_runtime(&self) -> Result<PathBuf> {
        let jbr_dir = self.ide_home.join("jbr");
        debug!("Checking {jbr_dir:?}");
        Self::check_runtime_dir(&jbr_dir)
    }

    fn check_runtime_dir(runtime_home: &Path) -> Result<PathBuf> {
        let adjusted_home = if cfg!(target_os = "macos") { runtime_home.join("Contents/Home") } else { runtime_home.to_path_buf() };
        let java_executable = adjusted_home.join(if cfg!(target_os = "windows") { "bin\\java.exe" } else { "bin/java" });
        if !java_executable.exists() {
            bail!("Java executable not found at {java_executable:?}");
        }
        if !java_executable.is_executable()? {
            bail!("Not an executable file: {java_executable:?}");
        }
        Ok(adjusted_home)
    }

    /// Reads VM options from both distribution and user-specific files and merges them into the given vector.
    ///
    /// Distribution options come first, so users can override default options with their own ones.
    /// This works because JVM processes arguments first-to-last, so the last one wins.
    /// Exceptions: garbage collector and RAM percentage options, so when a user sets one,
    /// the corresponding distribution option must be omitted.
    fn collect_vm_options_from_files(&self, vm_options: &mut Vec<String>) -> Result<()> {
        debug!("[1] Reading main VM options file: {:?}", self.vm_options_path);
        let (dist_vm_options, _) = read_vm_options(&self.vm_options_path)?;

        debug!("[2] Looking for user VM options file");
        let ((user_vm_options, corrupted), vm_options_path) = match self.get_user_vm_options_file() {
            Ok(path) => {
                debug!("Reading user VM options file: {:?}", path);
                (read_vm_options(&path)?, path)
            }
            Err(e) => {
                debug!("Failed: {}", e.to_string());
                ((Vec::new(), false), self.vm_options_path.clone())
            }
        };

        let mut filters: Vec<fn(&str) -> bool> = vec![];
        for line in &user_vm_options {
            if line.starts_with("-XX:+") && line.ends_with("GC") {
                filters.push(|l| l.starts_with("-XX:+") && l.ends_with("GC"));
            }
            if line.starts_with("-XX:InitialRAMPercentage=") {
                filters.push(|l| l.starts_with("-Xms"));
            }
            if line.starts_with("-XX:MinRAMPercentage=") || line.starts_with("-XX:MaxRAMPercentage=") {
                filters.push(|l| l.starts_with("-Xmx"));
            }
        }
        vm_options.extend(dist_vm_options.into_iter().filter(|l| !filters.iter().any(|filter| filter(l))));
        vm_options.extend(user_vm_options);

        vm_options.push(jvm_property!("jb.vmOptionsFile", vm_options_path.to_string_checked()?));
        if corrupted {
            vm_options.push(jvm_property!("jb.vmOptionsFile.corrupted", "true"))
        }

        Ok(())
    }

    /// Looks for user-editable config files in `<product>_VM_OPTIONS` environment variable,
    /// near the installation (Toolbox-style), or under the OS standard configuration directory.
    fn get_user_vm_options_file(&self) -> Result<PathBuf> {
        let env_var_name = self.env_var_base_name.to_owned() + "_VM_OPTIONS";
        debug!("Checking ${:?}", env_var_name);
        match get_path_from_env_var(&env_var_name, false) {
            Ok(env_file_path) => { return Ok(env_file_path); }
            Err(e) => { debug!("Failed: {}", e.to_string()); }
        }

        let real_ide_home = if cfg!(target_os = "macos") { self.ide_home.parent().unwrap() } else { &self.ide_home };
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

    fn expand_path_macro(&self, value: &str) -> Result<String> {
        let ide_home_path = self.ide_home.to_string_checked()?;
        Ok(value.replace(PATH_MACRO, &ide_home_path))
    }
}

fn read_vm_options(path: &Path) -> Result<(Vec<String>, bool)> {
    let file = File::open(path)?;

    let mut vm_options = Vec::with_capacity(50);
    for line in BufReader::new(file).lines() {
        let line = line.with_context(|| format!("Cannot read: {:?}", path))?.trim().to_string();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if line.contains('\0') {
            return Ok((Vec::new(), true));
        }
        vm_options.push(line);
    }
    debug!("{} line(s)", vm_options.len());

    Ok((vm_options, false))
}

pub fn read_product_info(product_info_path: &Path) -> Result<ProductInfo> {
    let file = File::open(product_info_path)?;
    let product_info: ProductInfo = serde_json::from_reader(BufReader::new(file))?;
    debug!("{:?}", product_info);
    Ok(product_info)
}

pub fn compute_launch_info(product_info: &ProductInfo, command_name: Option<&String>)
    -> Result<(ProductInfoLaunchField, Option<String>)> {

    if product_info.launch.len() != 1 {
        bail!("Malformed product descriptor (expecting 1 'launch' record, got {})", product_info.launch.len())
    }

    let launch_data = &product_info.launch[0];
    let custom_command_data = match command_name {
        Some(command_name) => {
            match &launch_data.customCommands {
                Some(commands) => commands.iter().find(
                    |custom| custom.commands.contains(command_name)
                ),
                None => None
            }
        },
        None => None
    };

    let result = match custom_command_data {
        None => (launch_data.clone(), None),
        Some(custom_command_data) => {
            (ProductInfoLaunchField {
                vmOptionsFilePath: custom_command_data.vmOptionsFilePath.clone().unwrap_or(launch_data.vmOptionsFilePath.clone()),
                bootClassPathJarNames: if !custom_command_data.bootClassPathJarNames.is_empty() {
                    custom_command_data.bootClassPathJarNames.clone()
                }
                else {
                    launch_data.bootClassPathJarNames.clone()
                },
                additionalJvmArguments: if !custom_command_data.additionalJvmArguments.is_empty() {
                    custom_command_data.additionalJvmArguments.clone()
                }
                else {
                    launch_data.additionalJvmArguments.clone()
                },
                mainClass: custom_command_data.mainClass.clone().unwrap_or(launch_data.mainClass.clone()),
                customCommands: None
            }, custom_command_data.dataDirectoryName.clone())
        }
    };
    Ok(result)
}

fn find_ide_home(current_exe: &Path) -> Result<(PathBuf, PathBuf)> {
    debug!("Looking for: '{PRODUCT_INFO_REL_PATH}'");

    let mut candidate = current_exe
        .canonicalize().with_context(|| format!("Resolving symlinks in '{}'", current_exe.display()))?
        .strip_ns_prefix().with_context(|| format!("Resolving symlinks in '{}'", current_exe.display()))?;
    for _ in 0..IDE_HOME_LOOKUP_DEPTH {
        candidate = candidate.parent_or_err()?;
        debug!("Probing for IDE home: {:?}", candidate);
        let product_info_path = candidate.join(PRODUCT_INFO_REL_PATH);
        if product_info_path.is_file() {
            return Ok((candidate, product_info_path))
        }
    }

    bail!("Max lookup depth ({IDE_HOME_LOOKUP_DEPTH}) reached")
}
