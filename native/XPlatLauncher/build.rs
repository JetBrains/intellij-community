use std::path::PathBuf;

use winresource::WindowsResource;

fn main() {
    if std::env::var("CARGO_CFG_TARGET_OS").unwrap() == "windows" {
        println!("cargo:rustc-link-lib=legacy_stdio_definitions");

        let cargo_root_env_var = std::env::var("CARGO_MANIFEST_DIR")
            .expect("CARGO_MANIFEST_DIR is not set");

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

    println!("cargo:rerun-if-changed=build.rs");
}