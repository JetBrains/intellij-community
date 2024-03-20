// technically we shouldn't use #cfg in build.rs due to cross-compilation,
// but the only we do is windows x64 -> arm64, so it's fine for our purposes

use std::path::{PathBuf};

#[cfg(target_os = "windows")]
use winresource::WindowsResource;

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

    let manifest_file = cargo_root.join("../WinLauncher/WinLauncher.manifest");
    assert!(manifest_file.is_file());

    let rc_file = PathBuf::from("../WinLauncher/WinLauncher.rc");
    assert!(rc_file.is_file());

    let mut res = WindowsResource::new();
    res.set_resource_file(rc_file.to_str().unwrap());
    res.set_manifest_file(manifest_file.to_str().unwrap());
    res.compile().expect("Failed to embed resource table and/or application manifest");
}

#[cfg(not(target_os = "windows"))]
fn main_os_specific() { }