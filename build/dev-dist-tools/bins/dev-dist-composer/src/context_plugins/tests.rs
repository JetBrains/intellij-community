use std::fs;

use component::manifest::{ComponentEntry, ComponentManifest, MANIFEST_VERSION};

use super::*;

fn file(relative_path: &str, executable: bool) -> ComponentEntry {
    ComponentEntry::ComponentFile {
        relative_path: relative_path.to_owned(),
        hash: 1,
        executable,
        source: format!("inputs/{relative_path}"),
        mode: None,
        mac_binary: None,
    }
}

fn plugin(kind: &str, entries: Vec<ComponentEntry>) -> ComponentManifest {
    ComponentManifest {
        version: MANIFEST_VERSION,
        kind: kind.to_owned(),
        plugin: true,
        entries,
        ..ComponentManifest::default()
    }
}

#[test]
fn the_list_names_each_executable_file_of_every_plugin_below_a_tree_too_in_destination_order() {
    let debugger = plugin(
        "intellij.nativeDebug.plugin",
        vec![
            ComponentEntry::Directory {
                relative_path: "plugins/nativeDebug/bin".to_owned(),
                mode: 0o755,
            },
            file("plugins/nativeDebug/bin/lldb/mac/aarch64/LLDBFrontend", true),
            file("plugins/nativeDebug/bin/gdb/mac/aarch64/bin/gdb", true),
            file("plugins/nativeDebug/bin/gdb/mac/aarch64/share/gdb.txt", false),
            file("plugins/nativeDebug/lib/nativeDebug.jar", false),
            ComponentEntry::symlink(
                "plugins/nativeDebug/bin/lldb/mac/aarch64/current".to_owned(),
                "LLDBFrontend".to_owned(),
            ),
        ],
    );
    let chrome = plugin(
        "intellij.chromeConnect",
        vec![file("plugins/chromeConnect/bin/chrome-devtools", true)],
    );
    assert_eq!(
        executables(&[&debugger, &chrome]).unwrap(),
        [
            "chromeConnect/bin/chrome-devtools",
            "nativeDebug/bin/gdb/mac/aarch64/bin/gdb",
            "nativeDebug/bin/lldb/mac/aarch64/LLDBFrontend",
        ]
    );
}

#[test]
fn an_executable_file_outside_the_plugins_directory_fails() {
    let platform = plugin("platform_core", vec![file("bin/idea", true)]);
    let error = executables(&[&platform]).unwrap_err();
    assert_eq!(
        format!("{error:#}"),
        "The component 'platform_core' marks 'bin/idea' executable, and a context plugin places no file outside `plugins/`"
    );
}

#[test]
fn the_file_has_one_line_per_executable_and_is_empty_without_one() {
    let directory = testkit::TempDir::new();
    let tool = directory.path().join("tool.component.json");
    let jar = directory.path().join("jar.component.json");
    manifest::write_component_manifest(
        &tool,
        &plugin("tool", vec![file("plugins/tool/bin/b", true), file("plugins/tool/bin/a", true)]),
    )
    .unwrap();
    manifest::write_component_manifest(&jar, &plugin("jar", vec![file("plugins/jar/lib/jar.jar", false)])).unwrap();
    let output = directory.path().join("pool.context-plugin-executables.txt");

    write_executables(&output, &[jar.clone(), tool]).unwrap();
    assert_eq!(fs::read_to_string(&output).unwrap(), "tool/bin/a\ntool/bin/b\n");

    write_executables(&output, &[jar]).unwrap();
    assert_eq!(fs::read_to_string(&output).unwrap(), "");

    let error = write_executables(&output, &[]).unwrap_err();
    assert_eq!(
        format!("{error:#}"),
        "A context plugin pool needs at least one --component-manifest"
    );
}
