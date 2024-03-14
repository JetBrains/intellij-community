use std::path::{PathBuf};

use winresource::WindowsResource;

// technically we shouldn't use #cfg here due to cross-compilation,
// but the only we do is windows x64 -> arm64, so it's fine for our purposes

fn main() {
    println!("cargo:rerun-if-changed=build.rs");
    main_os_specific()
}

#[cfg(target_os = "windows")]
fn main_os_specific() {
    println!("cargo:rustc-link-lib=legacy_stdio_definitions");

    enable_symlinks_if_can_create_them();

    let needs_metadata = std::env::var("XPLAT_LAUNCHER_EMBED_RESOURCES_AND_MANIFEST")
        .unwrap_or("0".to_string());

    if needs_metadata == "1" {
        embed_metadata()
    }
}

fn enable_symlinks_if_can_create_them() {
    let out_dir = PathBuf::from(std::env::var("OUT_DIR").unwrap());
    let target = out_dir.join("target");
    if target.exists() {
        std::fs::remove_file(&target)
            .expect("Failed to delete target file")
    }

    let contents = "test";
    std::fs::write(&target, contents)
        .expect("Unable to write to target file");

    let link = out_dir.join("link");
    if link.exists() {
        // DeleteFile removes the symlink, and that's what is used
        std::fs::remove_file(&link)
            .expect("Failed to delete the link")
    }

    match std::os::windows::fs::symlink_file(&target, &link) {
        Ok(_) => allow_symlink_creation(),
        Err(e) if e.raw_os_error() == Some(1314) => {
            println!(
                "Cannot use CreateSymbolicLink API, will use junction instead. File link creation will result in runtime errors."
            );
        },
        Err(e) => panic!("Failed to create symbolic link, but not due to the privileges: {:?}", e),
    }
}

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

fn allow_symlink_creation() {
    println!("cargo:rustc-cfg=feature=\"symlink_creation\"");
}

#[cfg(not(target_os = "windows"))]
fn main_os_specific() {
    allow_symlink_creation()
}