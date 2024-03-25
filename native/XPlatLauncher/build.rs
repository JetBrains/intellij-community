// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// technically we shouldn't use #cfg in build.rs due to cross-compilation,
// but the only we do is windows x64 -> arm64, so it's fine for our purposes

use anyhow::{Context, Result};
use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::PathBuf;

#[cfg(target_os = "windows")]
use {
    anyhow::bail,
    bzip2::read::BzDecoder,
    reqwest::blocking::Client,
    sha1::{Digest, Sha1},
    std::env,
    std::io::{BufRead, BufReader, Read},
    std::path::Path,
    windows::Win32::System::SystemInformation::GetLocalTime,
    winresource::WindowsResource,
};

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    main_os_specific()
        .expect("Failed to execute buildscript");
}

#[cfg(not(target_os = "windows"))]
fn main_os_specific() -> Result<()> {
    write_cef_version("NOT_SUPPORTED")
}

fn write_cef_version(cef_version: &str) -> Result<()> {
    let generated_file = PathBuf::from("./src/cef_generated.rs");
    let out_file = File::create(&generated_file)?;

    let mut buf_writer = BufWriter::new(out_file);

    writeln!(buf_writer, "pub static CEF_VERSION: &str = \"{cef_version}\";")?;

    buf_writer.flush()
        .context(format!("Failed to write CEF version to {generated_file:?}"))
}

#[cfg(target_os = "windows")]
fn main_os_specific() -> Result<()> {
    println!("cargo:rustc-link-lib=legacy_stdio_definitions");

    let cef_arch_string = match env::var("CARGO_CFG_TARGET_ARCH")?.as_str() {
        "x86_64" => "windows64",
        "aarch64" => "windowsarm64",
        e => bail!("Unknown target arch: {e}")
    };

    let cef_version = "122.1.9+gd14e051+chromium-122.0.6261.94";

    let cef_download_root = PathBuf::from("./deps/cef");
    let cef_dir = download_cef(cef_version, cef_arch_string, &cef_download_root)?;

    link_cef_sandbox(&cef_dir)?;
    write_cef_version(cef_version)?;

    // metadata embedding breaks incremental build due to verbose output of the used tools
    // for the convenience of the development we'll avoid this
    // for the builds which are explicitly marked as debug by cargo
    let is_debug =  env::var("DEBUG")? == "true";
    if !is_debug {
        embed_metadata()?;
    }

    Ok(())
}

#[cfg(target_os = "windows")]
pub fn download_cef(version: &str, platform: &str, working_dir: &Path) -> Result<PathBuf> {
    let cef_distribution = &format!("cef_binary_{version}_{platform}_minimal");

    let extract_dir = working_dir.join(cef_distribution);
    let extract_marker = extract_dir.join(".extracted");
    if extract_marker.exists() {
        return Ok(extract_dir);
    }

    fs_remove(&extract_dir)?;
    std::fs::create_dir_all(working_dir)?;

    let client = Client::new();

    let archive_url = format!("https://cef-builds.spotifycdn.com/{cef_distribution}.tar.bz2");
    let archive_file = working_dir.join(format!("{cef_distribution}.tar.bz2"));
    download_to_file(&client, &archive_url, &archive_file)?;

    let checksum_url = format!("{archive_url}.sha1");
    let checksum_file = working_dir.join(format!("{cef_distribution}.tar.bz2.sha1"));
    download_to_file(&client, &checksum_url, &checksum_file)?;

    let checksum = std::fs::read_to_string(&checksum_file)?;

    verify_sha1_checksum(&archive_file, &checksum)?;

    extract_tar_bz2(&archive_file, &extract_dir, &extract_marker)?;

    fs_remove(&checksum_file)?;
    fs_remove(&archive_file)?;

    Ok(extract_dir)
}

#[cfg(target_os = "windows")]
fn download_to_file(client: &Client, src: &str, dest: &Path) -> Result<u64> {
    fs_remove(dest)?;

    let mut response = client.get(src).send()?.error_for_status()?;
    let mut file = File::create(dest)
        .context(format!("Failed to create file at {dest:?}"))?;

    std::io::copy(&mut response, &mut file)
        .context(format!("Failed to copy response from {src} to {dest:?}"))
}

#[cfg(target_os = "windows")]
fn verify_sha1_checksum(file: &Path, expected: &str) -> Result<()> {
    let mut file = File::open(file)?;
    let mut buffer = Vec::new();
    file.read_to_end(&mut buffer)?;

    let digest = Sha1::digest(&buffer);
    let actual = format!("{digest:x}");
    if actual != expected {
        bail!("Checksum mismatch. Expected: '{expected}', actual: '{actual}'");
    }

    Ok(())
}

#[cfg(target_os = "windows")]
fn extract_tar_bz2(archive: &Path, dest: &Path, extract_marker: &Path) -> Result<()> {
    assert!(!extract_marker.exists());
    assert!(!dest.exists());

    let archive_file_name = get_file_name(&archive)?;

    let tarball_top_directory = archive_file_name
        .strip_suffix(".tar.bz2")
        .context("No .tar.bz2 suffix on the archive")?;

    let dest_file_name = get_file_name(&dest)?;

    let tmp_dest = dest.parent()
        .context(format!("No parent for {dest:?}"))?
        .join(format!("{dest_file_name}.tmp"));

    fs_remove(&tmp_dest)?;

    let file = File::open(archive)?;
    let decompressed = BzDecoder::new(file);
    let mut tarball = tar::Archive::new(decompressed);

    tarball.unpack(&tmp_dest)?;

    let tarball_stripped = tmp_dest.join(tarball_top_directory);
    assert!(tarball_stripped.exists());
    assert!(tarball_stripped.is_dir());

    std::fs::rename(&tarball_stripped, &dest)?;
    File::create(&extract_marker)?;

    fs_remove(&tmp_dest)?;

    Ok(())
}

#[cfg(target_os = "windows")]
fn link_cef_sandbox(cef_dir: &Path) -> Result<()> {
    let cef_lib_search_path = &cef_dir.join("Release").canonicalize()?;
    let cef_lib_search_path_string = get_non_unc_string(cef_lib_search_path)?;
    println!("cargo:rustc-link-search=native={cef_lib_search_path_string}");

    let lib_name_without_extension = "cef_sandbox";
    let lib_name = format!("{lib_name_without_extension}.lib");
    let lib_file = &cef_lib_search_path.join(lib_name);
    assert_exists_and_file(lib_file)?;

    println!("cargo:rustc-link-lib=static={lib_name_without_extension}");

    let cef_sandbox_dependencies = [
        "Advapi32",
        "dbghelp",
        "Delayimp",
        "ntdll",
        "OleAut32",
        "PowrProf",
        "Propsys",
        "psapi",
        "SetupAPI",
        "Shell32",
        "shlwapi",
        "Shcore",
        "Userenv",
        "version",
        "wbemuuid",
        "winmm",
        "ws2_32",
        "WindowsApp",
    ];

    // Link each of the standard libraries
    for lib in cef_sandbox_dependencies.iter() {
        println!("cargo:rustc-link-lib={}", lib);
    }

    Ok(())
}

#[cfg(target_os = "windows")]
fn get_file_name(path: &Path) -> Result<String> {
    let result = path.file_name()
        .context(format!("Failed to get filename from {path:?}"))?
        .to_str()
        .context("Failed to get &str from &OsStr")?
        .to_string();

    Ok(result)
}

#[cfg(target_os = "windows")]
fn embed_metadata() -> Result<()> {
    let cargo_root_env_var = env::var("CARGO_MANIFEST_DIR")?;
    let cargo_root = PathBuf::from(cargo_root_env_var);

    let manifest_file = cargo_root.join("./resources/windows/WinLauncher.manifest");
    assert_exists_and_file(&manifest_file)?;

    let rc_template_file = PathBuf::from("./resources/windows/WinLauncher.rc");
    assert_exists_and_file(&rc_template_file)?;

    let rc_file = process_rc_template(&rc_template_file)?;

    let mut res = WindowsResource::new();
    res.set_resource_file(rc_file.to_str().context("Failed to get &str from rc file path")?);
    res.set_manifest_file(manifest_file.to_str().context("Failed to get &str from manifest file path")?);
    res.compile().context("Failed to embed resource table and/or application manifest")
}

#[cfg(target_os = "windows")]
fn process_rc_template(template: &Path) -> Result<PathBuf> {
    let file = File::open(template)?;

    let current_year = get_current_year();
    let package_name = env::var("CARGO_PKG_NAME")?;


    let mut processed_lines = Vec::with_capacity(60);
    for line in BufReader::new(file).lines() {
        let processed_line = line?
            .replace("@YEAR@", &current_year)
            .replace("@FILE_NAME@", &package_name);
        processed_lines.push(processed_line)
    }

    let out_dir = PathBuf::from(env::var("OUT_DIR")?);

    let out_file_path = out_dir.join("xplat-launcher.rc");
    let out_file = File::create(&out_file_path)?;

    let mut buf_writer = BufWriter::new(out_file);

    for line in processed_lines {
        writeln!(buf_writer, "{line}")?;
    }

    buf_writer.flush()?;

    let rc_dependencies = vec!["resource.h", "WinLauncher.ico"];
    for dep in rc_dependencies {
        let src = PathBuf::from(format!("./resources/windows/{dep}"));
        let dest = out_dir.join(dep);
        std::fs::copy(&src, &dest)?;
    }

    Ok(out_file_path)
}

#[cfg(target_os = "windows")]
fn get_current_year() -> String {
    let system_time = unsafe {
        GetLocalTime()
    };

    let current_year = system_time.wYear as u32;
    current_year.to_string()
}

#[cfg(target_os = "windows")]
fn get_non_unc_string(path: &Path) -> Result<String> {
    let result = path
        .to_str()
        .context("Failed to get &str from &OsStr")?
        .to_string()
        .replace("\\\\?\\", "");

    Ok(result)
}

#[cfg(target_os = "windows")]
fn fs_remove(path: &Path) -> Result<()> {
    if path.exists() {
        if path.is_dir() {
            std::fs::remove_dir_all(path)?;
        } else {
            std::fs::remove_file(path)?;
        }
    }

    Ok(())
}

#[cfg(target_os = "windows")]
fn assert_exists_and_file(path: &Path) -> Result<()> {
    if !path.exists() {
        bail!("File '{path:?}' does not exist")
    }
    if !path.is_file() {
        bail!("'{path:?}' is not a file")
    }

    Ok(())
}