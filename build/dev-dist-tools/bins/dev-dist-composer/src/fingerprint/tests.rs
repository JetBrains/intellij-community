// The expected values in this file come from the Kotlin code. A throwaway Java program read the same manifests with
// readDevBuildComponentManifest and called computeIdeFingerprintFromComponents, the private
// computeDevBuildLaunchMetadataHash, and orderCoreClasspathEntries. The Kotlin manifests had the version 9 shape. The
// version 10 shape states the same entries with a source for each file, and the fingerprint reads no source.

#![allow(clippy::unreadable_literal, reason = "the hash values are copied from the Kotlin output")]

use std::path::Path;

use component::manifest::{ComponentEntry, validate_manifest};

use super::*;
use crate::test_support::{TempDir, reference_bytes, test_manifest, write_file};

// The Kotlin run read `additionalModules` from these manifests. The composer takes the modules from the composition
// spec now, so the manifests list none, and each case passes the list that Kotlin summed. The expected values are
// unchanged.

/// The neutral manifest names a path that is not ASCII, so the hash stream holds more UTF-16 code units than the path
/// has characters. The Kotlin manifest also named `😀.jar` and `Ａ.jar` to pin the UTF-16 order, which differs from the
/// order of `str::cmp` for them. Every real path is ASCII, so this manifest leaves them out. The three expected values
/// with this manifest come from the UTF-16 order of the earlier code on this manifest.
const GOLDEN_NEUTRAL_MANIFEST: &str = concat!(
    r#"{"version":10,"kind":"plugins_json","platformPrefix":"idea","os":"","arch":"","plugin":true,"mainClass":null,"#,
    r#""coreClassPath":["plugins/json/lib/json.jar"],"entries":["#,
    r#"{"type":"component-file","relativePath":"plugins/json/lib/json.jar","hash":-5,"source":"inputs/json.jar"},"#,
    r#"{"type":"component-file","relativePath":"plugins/json/lib/ünïcode.jar","hash":1234567890123,"source":"inputs/unicode.jar"},"#,
    r#"{"type":"component-file","relativePath":"plugins/json/bin/tool","hash":9,"executable":true,"source":"inputs/tool","mode":488},"#,
    r#"{"type":"component-file","relativePath":"plugins/json/bin/run","hash":10,"executable":true,"source":"inputs/run","mode":493},"#,
    r#"{"type":"component-file","relativePath":"plugins/json/bin/plain","hash":12,"source":"inputs/plain","mode":420},"#,
    r#"{"type":"directory","relativePath":"plugins/json/resources","mode":448},"#,
    r#"{"type":"symlink","relativePath":"plugins/json/current","hash":11,"symlinkTarget":"lib"}]}"#
);

/// The platform manifest repeats a path with other hashes and executable flags, so the sort needs every key.
const GOLDEN_PLATFORM_MANIFEST: &str = concat!(
    r#"{"version":10,"kind":"platform_core","platformPrefix":"idea","os":"mac","arch":"aarch64","plugin":false,"#,
    r#""mainClass":"com.intellij.idea.Main","coreClassPath":["lib/app-backend.jar","lib/util.jar","lib/a/b.jar","lib/a-b.jar","#,
    r#""lib/platform-loader.jar","lib/product-backend.jar","lib/util-8.jar","lib/Z.jar","lib/é.jar","lib/util.jar"],"entries":["#,
    r#"{"type":"component-file","relativePath":"lib/app-backend.jar","hash":-9223372036854775808,"source":"inputs/app-backend.jar"},"#,
    r#"{"type":"component-file","relativePath":"lib/util.jar","hash":9223372036854775807,"source":"inputs/util.jar"},"#,
    r#"{"type":"component-file","relativePath":"lib/util.jar","hash":3,"source":"inputs/util.jar"},"#,
    r#"{"type":"component-file","relativePath":"lib/util.jar","hash":3,"executable":true,"source":"inputs/util.jar"},"#,
    r#"{"type":"component-file","relativePath":"bin/idea.sh","hash":42,"executable":true,"source":"inputs/idea.sh"}]}"#
);

/// Decodes a manifest and applies the checks of the reader, so that the version and mode checks apply as in the Go test.
/// The Kotlin inputs list core classpath jars that no entry names, so the core classpath check does not apply.
pub(crate) fn read_golden_manifest(content: &str) -> ComponentManifest {
    let manifest: ComponentManifest = serde_json::from_str(content).unwrap();
    validate_manifest(&ComponentManifest {
        core_class_path: Vec::new(),
        ..manifest.clone()
    })
    .unwrap();
    manifest
}

/// Writes more than two 256 KiB blocks, so that the content hash frames three of them.
fn write_golden_plugin_classpath(directory: &TempDir) -> std::path::PathBuf {
    let file = directory.path().join("plugin-classpath.txt");
    write_file(&file, reference_bytes(600_001));
    file
}

const NO_MODULES: &[&str] = &[];

fn must_fingerprint(components: &[&ComponentManifest]) -> String {
    compute_ide_fingerprint_from_components(components, None, NO_MODULES).unwrap()
}

#[test]
fn kotlin_fingerprint_golden() {
    let neutral = read_golden_manifest(GOLDEN_NEUTRAL_MANIFEST);
    let platform = read_golden_manifest(GOLDEN_PLATFORM_MANIFEST);
    let directory = TempDir::new();
    let plugin_classpath = write_golden_plugin_classpath(&directory);
    type Case<'a> = (&'a str, Vec<&'a ComponentManifest>, Option<&'a Path>, &'a [&'a str], &'a str);
    let cases: [Case<'_>; 4] = [
        (
            "declared modules and plugin records",
            vec![&neutral, &platform],
            Some(&plugin_classpath),
            &["intellij.shared", "intellij.json", "intellij.extra"],
            "v5:1mi8fetozs9w2",
        ),
        (
            "summed modules",
            vec![&neutral, &platform],
            None,
            &["intellij.json", "intellij.shared", "intellij.extra"],
            "v5:1dlyt2g7ft0t3",
        ),
        ("no modules", vec![&platform], None, NO_MODULES, "v5:1r4g1pf8wm6az"),
        (
            "platform first",
            vec![&platform, &neutral],
            Some(&plugin_classpath),
            &["intellij.extra", "intellij.json", "intellij.shared"],
            "v5:18zwy2x3g1pbj",
        ),
    ];
    for (name, components, plugin_classpath, declared, expected) in cases {
        let actual = compute_ide_fingerprint_from_components(&components, plugin_classpath, declared).unwrap();
        assert_eq!(actual, expected, "{name}");
    }
}

#[test]
fn kotlin_launch_metadata_hash_golden() {
    type Case<'a> = (&'a str, &'a str, &'a str, &'a str, &'a [&'a str], i64);
    let cases: [Case<'_>; 3] = [
        ("idea", "linux", "x64", "com.intellij.idea.Main", &[], -802626408373264443),
        ("Идея", "mac", "aarch64", "Main\u{1F600}", &["a", "b", "ü"], 4151155953984664682),
        ("", "", "", "", &[""], 2331720420689199891),
    ];
    for (prefix, os, arch, main_class, modules, expected) in cases {
        assert_eq!(
            launch_metadata_hash(prefix, os, arch, main_class, modules),
            expected,
            "{prefix} {os} {arch}"
        );
    }
}

#[test]
fn kotlin_core_classpath_order_golden() {
    let platform = read_golden_manifest(GOLDEN_PLATFORM_MANIFEST);
    let expected = [
        "lib/platform-loader.jar",
        "lib/util-8.jar",
        "lib/util.jar",
        "lib/product-backend.jar",
        "lib/Z.jar",
        "lib/a-b.jar",
        "lib/a/b.jar",
        "lib/app-backend.jar",
        "lib/util.jar",
        "lib/é.jar",
    ];
    assert_eq!(classpath::order_core_classpath_entries(&platform.core_class_path), expected);
}

// The Kotlin test "Go manifests preserve version 9 hashes and tree fingerprints" pins these content hashes. The
// fingerprint reuses `xxh3::hash_file` as `computeDevBuildContentHash`, and the vectors prove that the two agree.
#[test]
fn sourced_manifest_hashes_and_source_independence() {
    let vectors: [(usize, i64); 10] = [
        (0, 3244421341483603138),
        (1, -2399747073602280719),
        (3, -737883702129266468),
        (240, 2788469911834355041),
        (241, -4155630063455057979),
        (262143, 9078738661776034622),
        (262144, -1692254647099917537),
        (262145, -2541306581069977202),
        (524288, 3157545227256347297),
        (524301, 8144707773225287728),
    ];
    let launch = test_manifest("launch");
    for (size, hash) in vectors {
        let directory = TempDir::new();
        let content = directory.path().join("content.jar");
        write_file(&content, reference_bytes(size));
        assert_eq!(xxh3::hash_file(&content).unwrap(), hash, "hash of {size} bytes");
        let sourced = read_golden_manifest(&format!(
            r#"{{"version": 10, "kind": "files", "platformPrefix": "idea", "os": "linux", "arch": "x64", "plugin": false,
            "mainClass": null, "coreClassPath": [],
            "entries": [{{"type": "component-file", "relativePath": "lib/content.jar", "hash": {hash}, "source": "inputs/content.jar"}}]}}"#
        ));
        assert!(!sourced.entries[0].executable());
        let mut relocated = sourced.clone();
        relocated.entries = vec![ComponentEntry::ComponentFile {
            relative_path: "lib/content.jar".to_owned(),
            hash,
            executable: false,
            source: "other/content.jar".to_owned(),
            mode: None,
        }];
        assert_eq!(
            must_fingerprint(&[&launch, &relocated]),
            must_fingerprint(&[&launch, &sourced]),
            "the source changed the fingerprint"
        );
    }
}

fn single_file(hash: i64, executable: bool) -> ComponentManifest {
    let mut manifest = test_manifest("plugins_air");
    manifest.entries = vec![ComponentEntry::ComponentFile {
        relative_path: "plugins/air/lib/air.jar".to_owned(),
        hash,
        executable,
        source: "inputs/air.jar".to_owned(),
        mode: None,
    }];
    manifest
}

#[test]
fn fingerprint_covers_packaged_bytes_and_the_executable_bit() {
    let fingerprint = must_fingerprint(&[&single_file(1, false)]);
    for changed in [single_file(2, false), single_file(1, true)] {
        assert_ne!(
            must_fingerprint(&[&changed]),
            fingerprint,
            "the fingerprint ignores {:?}",
            changed.entries
        );
    }
}

#[test]
fn component_fingerprint_covers_generated_launch_and_classpath_data() {
    let mut base = test_manifest("platform_core");
    base.core_class_path = vec!["lib/platform.jar".to_owned()];
    let mut changed_core_classpath = base.clone();
    changed_core_classpath.core_class_path = vec!["lib/renamed-platform.jar".to_owned()];
    let mut changed_main_class = base.clone();
    changed_main_class.main_class = Some("com.intellij.idea.OtherMain".to_owned());
    let directory = TempDir::new();
    let plugin_classpath = directory.path().join("plugin-classpath.txt");
    write_file(&plugin_classpath, [1, 2, 3]);
    let fingerprint =
        |manifest: &ComponentManifest| compute_ide_fingerprint_from_components(&[manifest], Some(&plugin_classpath), NO_MODULES).unwrap();
    let expected = fingerprint(&base);
    assert_ne!(
        fingerprint(&changed_core_classpath),
        expected,
        "the fingerprint ignores the core classpath"
    );
    assert_ne!(fingerprint(&changed_main_class), expected, "the fingerprint ignores the main class");
    write_file(&plugin_classpath, [1, 2, 4]);
    assert_ne!(fingerprint(&base), expected, "the fingerprint ignores the plugin classpath");
}

#[test]
fn fingerprint_of_exact_modes() {
    let with_mode = |mode: Option<u32>| {
        let mut manifest = test_manifest("plugin");
        manifest.entries = vec![ComponentEntry::ComponentFile {
            relative_path: "plugins/demo/bin/tool".to_owned(),
            hash: 1,
            executable: true,
            source: "inputs/tool".to_owned(),
            mode,
        }];
        manifest
    };
    let exact = must_fingerprint(&[&with_mode(Some(0o750))]);
    let conventional = must_fingerprint(&[&with_mode(Some(0o755))]);
    assert_ne!(exact, conventional, "the fingerprint ignores an exact mode");
    assert_eq!(
        must_fingerprint(&[&with_mode(None)]),
        conventional,
        "a conventional mode changed the fingerprint"
    );
}

#[test]
fn fingerprint_requires_a_main_class() {
    let mut jars = test_manifest("platform_jars");
    jars.main_class = None;
    compute_ide_fingerprint_from_components(&[&jars], None, NO_MODULES).unwrap_err();
    compute_ide_fingerprint_from_components(&[], None, NO_MODULES).unwrap_err();
}

#[test]
fn the_platform_comes_from_the_first_component_that_names_one() {
    let mut neutral = test_manifest("plugins_json");
    neutral.os.clear();
    neutral.arch.clear();
    neutral.main_class = None;
    let linux = test_manifest("platform_core");
    let mut mac = linux.clone();
    mac.os = "mac".to_owned();
    mac.arch = "aarch64".to_owned();
    // The launch metadata hashes the platform of the distribution, not the empty one of the neutral component.
    assert_ne!(must_fingerprint(&[&neutral, &linux]), must_fingerprint(&[&neutral, &mac]));
}

#[test]
fn the_decoder_reads_the_golden_bytes() {
    let manifest: ComponentManifest = serde_json::from_str(GOLDEN_NEUTRAL_MANIFEST).unwrap();
    assert_eq!(manifest.entries.len(), 7);
    assert!(manifest.plugin);
    assert_eq!(
        manifest.entries[5],
        ComponentEntry::Directory {
            relative_path: "plugins/json/resources".to_owned(),
            mode: 0o700
        }
    );
}

#[test]
fn base36_is_go_format_uint() {
    assert_eq!(base36(0), "0");
    assert_eq!(base36(35), "z");
    assert_eq!(base36(36), "10");
    assert_eq!(base36(u64::MAX), "3w5e11264sgsf");
}
