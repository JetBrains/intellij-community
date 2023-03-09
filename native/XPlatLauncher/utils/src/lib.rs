// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use std::{env, fs};
use std::ffi::OsStr;
use std::fs::File;
use std::io::{BufReader, Read};
use std::path::{Path, PathBuf};
use anyhow::{bail, Result};
use log::debug;

#[cfg(target_os = "windows")]
pub fn canonical_non_unc(path: &Path) -> Result<String> {
    let canonical = path.canonicalize()?;
    let os_str = canonical.as_os_str().to_string_lossy().to_string();
    let stripped_unc = os_str.strip_prefix("\\\\?\\").unwrap().to_string();
    Ok(stripped_unc)
}

#[cfg(any(target_os = "macos", target_os = "linux"))]
pub fn canonical_non_unc(path: &Path) -> Result<String> {
    let canonical = path.canonicalize()?;
    let os_str = canonical.as_os_str().to_string_lossy().to_string();
    Ok(os_str)
}

pub fn get_readable_file_from_env_var<S:AsRef<OsStr>>(env_var_name: S) -> Result<PathBuf> {
    let file = get_path_from_env_var(env_var_name)?;
    let _path_buf = is_readable(&file)?;
    Ok(file)
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

pub fn get_path_from_env_var<S:AsRef<OsStr>>(env_var_name: S) -> Result<PathBuf> {
    let env_var_value = env::var(env_var_name)?;

    if env_var_value.is_empty() {
        bail!("Env var {env_var_value} is not set, skipping resolving path from it")
    }

    let path = PathBuf::from(env_var_value.as_str());
    Ok(path)
}

pub fn is_readable<P: AsRef<Path>>(file: P) -> Result<PathBuf> {
    // TODO: seems like the best possible x-plat way to verify whether the file is readable
    // note: file is closed when we exit the scope
    {
        let _file = fs::OpenOptions::new().read(true).open(&file)?;
    }

    Ok(file.as_ref().to_path_buf())
}

pub fn read_file_to_end(path: &Path) -> Result<String> {
    let file = File::open(path)?;
    let mut reader = BufReader::new(file);

    let mut content = String::from("");
    let bytes_read = reader.read_to_string(&mut content)?;

    debug!("Read {bytes_read} bytes from {path:?}");
    debug!("Contents of {path:?}: {content}");

    Ok(content)
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