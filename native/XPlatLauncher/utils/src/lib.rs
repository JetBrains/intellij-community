// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::env;
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use log::debug;

#[cfg(target_family = "unix")]
use std::os::unix::fs::PermissionsExt;

#[cfg(target_os = "windows")]
pub fn canonical_non_unc(path: &Path) -> Result<String> {
    let canonical = path.canonicalize()?;
    let os_str = canonical.as_os_str().to_string_lossy().to_string();
    let stripped_unc = os_str.strip_prefix("\\\\?\\").unwrap().to_string();
    Ok(stripped_unc)
}

#[cfg(target_family = "unix")]
pub fn canonical_non_unc(path: &Path) -> Result<String> {
    let canonical = path.canonicalize()?;
    let os_str = canonical.as_os_str().to_string_lossy().to_string();
    Ok(os_str)
}

#[cfg(target_family = "unix")]
pub fn is_executable(path: &Path) -> Result<bool> {
    let permissions = path.metadata()?.permissions();
    let is_executable = permissions.mode() & 0o111 != 0;
    Ok(path.is_file() && is_executable)
}

#[cfg(any(target_os = "windows"))]
pub fn is_executable(_path: &Path) -> Result<bool> {
    Ok(true)
}

pub fn get_current_exe() -> PathBuf {
    match get_path_from_env_var("XPLAT_LAUNCHER_CURRENT_EXE_PATH") {
        Ok(x) => {
            debug!("Using exe path from XPLAT_LAUNCHER_CURRENT_EXE_PATH: {x:?}");
            x
        }
        Err(_) => { env::current_exe().expect("Failed to get current executable path") }
    }
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
}

impl PathExt for Path {
    fn parent_or_err(&self) -> Result<PathBuf> {
        match self.parent() {
            None => bail!("No parent dir for '{self:?}'"),
            Some(s) => Ok(s.to_path_buf()),
        }
    }
}

#[macro_export]
macro_rules! jvm_property {
    ( $name:expr, $value:expr ) => { format!("-D{}={}", $name, $value) }
}
