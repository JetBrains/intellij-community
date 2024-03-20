// technically we shouldn't use #cfg in build.rs due to cross-compilation,
// but the only we do is windows x64 -> arm64, so it's fine for our purposes

use std::fs;
use std::fs::File;
use std::io::{BufRead, BufReader, BufWriter, Write};
use std::path::{Path, PathBuf};

#[cfg(target_os = "windows")]
use {
    windows::Win32::System::SystemInformation::GetLocalTime,
    winresource::WindowsResource,
};

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    main_os_specific()
}

#[cfg(target_os = "windows")]
fn main_os_specific() {
    println!("cargo:rustc-link-lib=legacy_stdio_definitions");

    let needs_metadata = std::env::var("XPLAT_LAUNCHER_EMBED_RESOURCES_AND_MANIFEST")
        .unwrap_or("0".to_string());

    if needs_metadata == "1" {
        embed_metadata()
    }
}

#[cfg(target_os = "windows")]
fn embed_metadata() {
    let cargo_root_env_var = std::env::var("CARGO_MANIFEST_DIR").unwrap();

    let cargo_root = PathBuf::from(cargo_root_env_var);

    let manifest_file = cargo_root.join("./resources/windows/WinLauncher.manifest");
    assert!(manifest_file.is_file());

    let rc_file_template = PathBuf::from("./resources/windows/WinLauncher.rc");
    assert!(rc_file_template.is_file());

    let rc_file = process_rc_template(&rc_file_template);

    let mut res = WindowsResource::new();
    res.set_resource_file(rc_file.to_str().unwrap());
    res.set_manifest_file(manifest_file.to_str().unwrap());
    res.compile().expect("Failed to embed resource table and/or application manifest");
}

#[cfg(target_os = "windows")]
fn process_rc_template(template: &Path) -> PathBuf {
    let file = File::open(template)
        .expect("Failed to open .rc template");

    let current_year = get_current_year();
    let package_name = std::env::var("CARGO_PKG_NAME")
        .expect("CARGO_PKG_NAME must be set in build context");


    let mut processed_lines = Vec::with_capacity(60);
    for line in BufReader::new(file).lines() {
        let line = line.expect("Failed to read from .rc template");
        let processed_line = line
            .replace("@YEAR@", &current_year)
            .replace("@FILE_NAME@", &package_name);
        processed_lines.push(processed_line)
    }

    let out_dir = std::env::var("OUT_DIR")
        .expect("OUT_DIR must be set in build context");
    let out_dir = PathBuf::from(out_dir);

    let out_file_path = out_dir.join("xplat-launcher.rc");
    let out_file = File::create(&out_file_path)
        .expect("Failed to create output .rc file");

    let mut buf_writer = BufWriter::new(out_file);

    for line in processed_lines {
        writeln!(buf_writer, "{}", line)
            .expect("Failed to write to output .rc file");
    }

    buf_writer.flush()
        .expect("Failed to flush write buffer to .rc file");

    let rc_dependencies = vec!["resource.h", "WinLauncher.ico"];
    for dep in rc_dependencies {
        let src = PathBuf::from(format!("./resources/windows/{dep}"));
        let dest = out_dir.join(dep);
        fs::copy(&src, &dest)
            .expect("Failed to copy .rc dependency");
    }

    out_file_path
}

#[cfg(target_os = "windows")]
fn get_current_year() -> String {
    let system_time = unsafe {
        GetLocalTime()
    };

    let current_year = system_time.wYear as u32;
    current_year.to_string()
}

#[cfg(not(target_os = "windows"))]
fn main_os_specific() { }