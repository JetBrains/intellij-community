use std::fs;

use component::manifest::{ComponentEntry, ComponentManifest};
use filemeta::macho::MacBinary;

use super::*;

fn file(relative_path: &str, mac_binary: Option<MacBinary>) -> ComponentEntry {
    ComponentEntry::ComponentFile {
        relative_path: relative_path.to_owned(),
        hash: 1,
        executable: mac_binary == Some(MacBinary::Executable),
        source: format!("inputs/{relative_path}"),
        mode: None,
        mac_binary,
    }
}

fn component(os: &str, entries: Vec<ComponentEntry>) -> ComponentManifest {
    ComponentManifest {
        kind: format!("component_{os}"),
        os: os.to_owned(),
        arch: if os.is_empty() { String::new() } else { "aarch64".to_owned() },
        entries,
        ..ComponentManifest::default()
    }
}

#[test]
fn a_mac_composition_lists_the_mach_o_files_of_every_component_in_destination_order() {
    let platform = component(
        "mac",
        vec![file("bin/idea", Some(MacBinary::Executable)), file("lib/app.jar", None)],
    );
    let plugin = component(
        "",
        vec![
            file("plugins/gdb/lib/libpython.dylib", Some(MacBinary::Dylib)),
            file("plugins/gdb/lib/_ctypes.so", Some(MacBinary::Bundle)),
        ],
    );
    let files = mac_binaries(&[&plugin, &platform]);
    assert_eq!(
        files,
        [
            ("bin/idea", MacBinary::Executable),
            ("plugins/gdb/lib/_ctypes.so", MacBinary::Bundle),
            ("plugins/gdb/lib/libpython.dylib", MacBinary::Dylib),
        ]
    );
}

#[test]
fn another_platform_or_a_neutral_composition_lists_no_file() {
    let entries = vec![file("bin/tool", Some(MacBinary::Executable))];
    assert_eq!(mac_binaries(&[&component("linux", entries.clone())]), []);
    assert_eq!(mac_binaries(&[&component("", entries)]), []);
}

#[test]
fn the_file_has_the_version_and_the_indent_of_a_manifest() {
    let directory = testkit::TempDir::new();
    let path = directory.path().join("dist.mac-binaries.json");
    let platform = component("mac", vec![file("bin/idea", Some(MacBinary::Executable))]);
    write_mac_binaries(&path, &[&platform]).unwrap();
    assert_eq!(
        fs::read_to_string(&path).unwrap(),
        "{\n  \"version\": 1,\n  \"files\": [\n    {\n      \"relativePath\": \"bin/idea\",\n      \"kind\": \"executable\"\n    }\n  ]\n}"
    );
    write_mac_binaries(&path, &[&component("windows", Vec::new())]).unwrap();
    assert_eq!(fs::read_to_string(&path).unwrap(), "{\n  \"version\": 1,\n  \"files\": []\n}");
}
