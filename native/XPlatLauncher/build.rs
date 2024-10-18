// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// technically we shouldn't use #cfg in build.rs due to cross-compilation,
// but the only we do is windows x64 -> arm64, so it's fine for our purposes

#[cfg(target_os = "windows")]
use {
    anyhow::{bail, Context, Result},
    std::env,
    std::path::{Path, PathBuf},
    winresource::WindowsResource,
};

#[cfg(all(target_os = "windows", feature = "cef"))]
use {
    reqwest::blocking::Client,
    sha1::{Digest, Sha1},
    std::fs::File,
    std::io::Read,
    std::process::Command,
};

#[cfg(target_os = "windows")]
macro_rules! trace {
    ($($arg:tt)*) => {
        println!("TRACE: {}", format_args!($($arg)*));
    };
}

#[cfg(target_os = "windows")]
macro_rules! cargo {
    ($($arg:tt)*) => {
        println!("cargo:{}", format_args!($($arg)*));
    };
}

fn main() {
    #[cfg(target_os = "windows")]
    {
        cargo!("rerun-if-changed=build.rs");
        
        #[cfg(feature = "cef")]
        link_cef().expect("Failed to link with CEF");
        
        embed_metadata().expect("Failed to embed metadata");
    }
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn link_cef() -> Result<()> {
    let cef_version = "122.1.9+gd14e051+chromium-122.0.6261.94";

    let cef_arch_string = match env::var("CARGO_CFG_TARGET_ARCH")?.as_str() {
        "x86_64" => "windows64",
        "aarch64" => "windowsarm64",
        e => panic!("Unsupported arch: {}", e)
    };

    let cef_download_root = PathBuf::from("./deps/cef");
    let cef_dir = download_cef(cef_version, cef_arch_string, &cef_download_root)?;
    link_cef_sandbox(&cef_dir)?;

    cargo!("rustc-env=CEF_VERSION={cef_version}");

    Ok(())
}

#[cfg(all(target_os = "windows", feature = "cef"))]
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

    let archive_url = format!("https://cache-redirector.jetbrains.com/cef-builds.spotifycdn.com/{cef_distribution}.tar.bz2");
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

#[cfg(all(target_os = "windows", feature = "cef"))]
fn download_to_file(client: &Client, src: &str, dest: &Path) -> Result<()> {
    fs_remove(dest)?;

    trace!("Downloading {src} to {dest:?}");
    let mut response = client.get(src).send()?.error_for_status()?;

    let code = response.status();
    trace!("Got response from {src}, code {code}");

    let mut file = File::create(dest)
        .context(format!("Failed to create file at {dest:?}"))?;

    trace!("Writing response from {src} to {dest:?}");
    std::io::copy(&mut response, &mut file)
        .context(format!("Failed to copy response from {src} to {dest:?}"))?;
    trace!("Written response from {src} to {dest:?}");

    Ok(())
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn verify_sha1_checksum(file: &Path, expected: &str) -> Result<()> {
    trace!("Verifying checksum of {file:?}");

    let mut file = File::open(file)?;
    let mut buffer = Vec::new();
    file.read_to_end(&mut buffer)?;
    trace!("Written {file:?} to buffer to calculate digest");

    let digest = Sha1::digest(&buffer);
    let actual = format!("{digest:x}");

    if actual != expected {
        bail!("Checksum mismatch. Expected: '{expected}', actual: '{actual}'");
    }

    trace!("{file:?} has the expected checksum {expected}");

    Ok(())
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn extract_tar_bz2(archive: &Path, dest: &Path, extract_marker: &Path) -> Result<()> {
    trace!("Will extract {archive:?} to {dest:?}");

    assert!(!extract_marker.exists());
    assert!(!dest.exists());

    let dest_file_name = get_file_name(dest)?;

    let dest_parent = dest.parent()
        .context(format!("No parent for {dest:?}"))?;

    let tmp_dest = &dest_parent.join(format!("{dest_file_name}.tmp"));

    fs_remove(tmp_dest)?;
    std::fs::create_dir_all(tmp_dest)?;

    trace!("Extracting to temp destination {tmp_dest:?}");

    let status = match is_7z_available_in_path() {
        true => {
            trace!("Using 7-zip");

            let tar_dest_string = get_non_unc_string(&dest_parent.join(format!("{dest_file_name}.tar")))?;
            Command::new("7z")
                .arg("x")
                .arg(get_non_unc_string(archive)?)
                .arg(format!("-o{tar_dest_string}"))
                .status()?;

            let tmp_dest_string = get_non_unc_string(tmp_dest)?;
            Command::new("7z")
                .arg("x")
                .arg(tar_dest_string)
                .arg(format!("-o{tmp_dest_string}"))
                .status()?
        }

        false => {
            trace!("Using tar");
            Command::new("tar")
                .arg("-xjvf")
                .arg(get_non_unc_string(archive)?)
                .arg("-C")
                .arg(get_non_unc_string(tmp_dest)?)
                .status()?
        }
    };

    trace!("Extraction command finished");

    if !status.success() {
        bail!("Failed to extract CEF sandbox archive at {archive:?}")
    }

    // typical structure: archive.tar.bz2 contains archive dir as a root item
    let non_stripped_internal_dir = tmp_dest.join(dest_file_name);

    assert!(non_stripped_internal_dir.exists());
    assert!(non_stripped_internal_dir.is_dir());

    std::fs::rename(non_stripped_internal_dir, dest)?;
    File::create(extract_marker)?;

    trace!("Created extraction marker at {extract_marker:?}");

    Ok(())
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn is_7z_available_in_path() -> bool {
    let status = Command::new("7z")
        .arg("--help")
        .status();

    status.is_ok()
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn link_cef_sandbox(cef_dir: &Path) -> Result<()> {
    let cef_lib_search_path = &cef_dir.join("Release").canonicalize()?;
    let cef_lib_search_path_string = get_non_unc_string(cef_lib_search_path)?;
    cargo!("rustc-link-search=native={cef_lib_search_path_string}");

    let lib_name_without_extension = "cef_sandbox";
    let lib_name = format!("{lib_name_without_extension}.lib");
    let lib_file = &cef_lib_search_path.join(lib_name);
    assert_exists_and_file(lib_file)?;

    // https://doc.rust-lang.org/rustc/command-line-arguments.html#linking-modifiers-whole-archive
    // the default is -whole-archive until it becomes +whole-archive...
    // which happens in "some cases for backward compatibility, but it is not guaranteed"
    // (in our case that happens when running `cargo test` and linker blows up after that)
    cargo!("rustc-link-lib=static:-whole-archive={lib_name_without_extension}");

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
        //"WindowsApp" - do not add, it is not needed for Win32 apps and brings in ugly umbrella libs
    ];

    // Link each of the standard libraries
    for lib in cef_sandbox_dependencies.iter() {
        cargo!("rustc-link-lib={}", lib);
    }

    Ok(())
}

#[cfg(all(target_os = "windows", feature = "cef"))]
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

    let manifest_relative_path = "resources/windows/WinLauncher.manifest";
    assert_exists_and_file(&cargo_root.join(manifest_relative_path))?;
    cargo!("rerun-if-changed={manifest_relative_path}");

    let icon_relative_path = "resources/windows/WinLauncher.ico";
    assert_exists_and_file(&cargo_root.join(icon_relative_path))?;

    let mut res = WindowsResource::new();
    res.set_manifest_file(manifest_relative_path);
    res.set_icon_with_id(icon_relative_path, "2000");  // see `resources/windows/resource.h`
    res.compile().context("Failed to embed resources")
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn get_non_unc_string(path: &Path) -> Result<String> {
    let result = path
        .to_str()
        .context("Failed to get &str from &OsStr")?
        .to_string()
        .replace("\\\\?\\", "");

    Ok(result)
}

#[cfg(all(target_os = "windows", feature = "cef"))]
fn fs_remove(path: &Path) -> Result<()> {
    trace!("Will remove {path:?}");

    if path.exists() {
        trace!("Removing {path:?}");
        if path.is_dir() {
            std::fs::remove_dir_all(path)?;
        } else {
            std::fs::remove_file(path)?;
        }
    } else {
        trace!("{path:?} does not exist");
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
