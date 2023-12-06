// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use anyhow::Result;

#[cfg(target_os = "windows")]
use {
    anyhow::bail,
    log::info,
    windows::core::{Error, HSTRING, PCWSTR},
    windows::Win32::Foundation::ERROR_SERVICE_DOES_NOT_EXIST,
    windows::Win32::System::Services::{CloseServiceHandle, OpenSCManagerW, OpenServiceW, SC_MANAGER_CONNECT}
};

#[cfg(target_os = "linux")]
use {
    anyhow::Context,
    log::{debug, info},
    std::fs,
    std::path::PathBuf,
};

#[cfg(target_os = "linux")]
pub fn is_running_in_docker() -> Result<bool> {
    Ok(is_control_group_matches_docker(None).expect("Unable to read cgroup file")
        || is_docker_env_file_exist(None).expect("Unable to read .dockerenv file")
        || is_docker_init_file_exist(None).expect("Unable to read .dockerinit file")
    )
}

#[cfg(target_os = "windows")]
pub fn is_running_in_docker() -> Result<bool> {
    is_service_present("cexecsvc")
}

#[cfg(target_os = "macos")]
pub fn is_running_in_docker() -> Result<bool> {
    Ok(false)
}

/**
 * Docker environment set control groups with a special names that include docker as a suffix.
 * Check Linux control groups and look over file content for Docker or lxc.
 */
#[cfg(target_os = "linux")]
pub fn is_control_group_matches_docker(cgroup_parent_path: Option<PathBuf>) -> Result<bool> {
    info!("Checking control group file...");

    let cgroup_dir_to_use = cgroup_parent_path.unwrap_or(PathBuf::from("/proc/1/"));
    let cgroup_path = cgroup_dir_to_use.join("cgroup");
    debug!("Got 'cgroup' file path: {}", cgroup_path.display());

    if !cgroup_path.exists() {
        debug!("Unable to find control group file by path: '{}'", cgroup_path.display());
        return Ok(false);
    }

    // Read cgroup file content
    let cgroup_content = fs::read_to_string(&cgroup_path)
        .with_context(|| format!("Unable to read file '{}'", cgroup_path.display()))?;

    // Check file contains any groups with 'docker' or 'lxc' suffix that define a container environment.
    let mut contains_docker_anchors = false;
    cgroup_content.lines().for_each(|line| {
        if line.contains("docker") || line.contains("lxc") {
            contains_docker_anchors = true;
            debug!("Found a line matching the Docker anchor: {}", line);
        }
    });

    if !contains_docker_anchors {
        debug!("Control group output contains no Docker anchors in file output.");
        return Ok(false);
    }

    Ok(true)
}

/**
 * Docker containers should store Docker environment configuration in /.dockerenv file.
 * Check for this file existence.
 */
#[cfg(target_os = "linux")]
pub fn is_docker_env_file_exist(root_dir: Option<PathBuf>) -> Result<bool> {
    is_docker_file_exist(".dockerenv", root_dir)
}

/**
 * Docker containers should store Docker environment configuration in /.dockerinit file.
 * Check for this file existence.
 */
#[cfg(target_os = "linux")]
pub fn is_docker_init_file_exist(root_dir: Option<PathBuf>) -> Result<bool> {
    is_docker_file_exist(".dockerinit", root_dir)
}

#[cfg(target_os = "linux")]
fn is_docker_file_exist(file_name: &str, root_dir: Option<PathBuf>) -> Result<bool> {
    let root_dir_to_use = root_dir.unwrap_or(PathBuf::from("/"));
    debug!("Got root directory path: {}", root_dir_to_use.display());

    if !root_dir_to_use.exists() {
        info!("Root directory does not exist: {}", root_dir_to_use.display());
        return Ok(false);
    }

    let docker_file_path = root_dir_to_use.join(file_name);
    let file_exist = docker_file_path.exists();
    debug!("Docker Environment file '{}' exists: {}", docker_file_path.display(), file_exist);

    Ok(file_exist)
}

/**
 * Check if Windows service present.
 */
#[cfg(target_os = "windows")]
pub fn is_service_present(service_name: &str) -> Result<bool> {
    info!("Checking if Windows service '{service_name}' present on machine");

    unsafe {
        let svc_manager = OpenSCManagerW(PCWSTR::null(), PCWSTR::null(), SC_MANAGER_CONNECT)?;
        let svc_handle = match OpenServiceW(svc_manager, &HSTRING::from(service_name), SC_MANAGER_CONNECT) {
            Ok(handle) => { handle }
            Err(e) => {
                if e == Error::from(ERROR_SERVICE_DOES_NOT_EXIST) {
                    let _ = CloseServiceHandle(svc_manager);
                    return Ok(false);
                }
                bail!("Failed to get Windows service. Error: {e:?}");
            }
        };

        let _ = CloseServiceHandle(svc_manager);
        let _ = CloseServiceHandle(svc_handle);
    }

    Ok(true)
}
