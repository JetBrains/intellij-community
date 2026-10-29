// The composer tests that need only manifests and records. The tests of the copy step are in `merge/tests.rs`.

use std::path::PathBuf;

use component::manifest::ComponentEntry;
use testkit::{TempDir, require_absent, require_error, write_file};

use super::*;
use crate::test_support::{
    directory_entry, file_entry, file_with_mode, link_entry, no_merge, runfiles, skip_merge, sourced_entry, test_manifest, with_entries,
};

const NO_MODULES: &[&str] = &[];

fn sourced_component(directory: &TempDir, name: &str, relative_file: &str, mut manifest: ComponentManifest) -> DevBuildComponent {
    let source = directory.join(&format!("{name}/{relative_file}"));
    write_file(&source, name);
    manifest.entries.push(sourced_entry(relative_file, &source));
    DevBuildComponent::new(manifest)
}

fn compose(components: &[DevBuildComponent], target: &Path) -> Result<ComposedBuild> {
    compose_with_merge(components, target, &ComposeOptions::default(), skip_merge)
}

#[test]
fn the_merge_step_runs_only_for_a_full_distribution() {
    let directory = TempDir::new();
    let components = [DevBuildComponent::new(test_manifest("platform"))];
    let options = ComposeOptions::default();
    let mut merged = None;
    compose_with_merge(&components, &directory.path().join("dist"), &options, |components, target| {
        merged = Some((components.len(), target.to_path_buf()));
        Ok(())
    })
    .unwrap();
    assert_eq!(merged, Some((1, directory.path().join("dist"))));
    let local = ComposeOptions {
        source_runfiles: Some(runfiles(&[])),
        ..options
    };
    compose_with_merge(&components, &directory.path().join("metadata"), &local, no_merge).unwrap();
    assert!(directory.path().join("metadata/local-layout.json").is_file());
}

#[test]
fn composer_accepts_ordered_platform_layers_and_plugins() {
    let directory = TempDir::new();
    let mut platform_lib = test_manifest("platform_lib");
    platform_lib.core_class_path = vec!["lib/platform.jar".into()];
    let mut plugins = test_manifest("plugins");
    plugins.core_class_path = vec!["plugins/sample/lib/sample.jar".into()];
    let mut extra = test_manifest("plugins_extra");
    extra.core_class_path = vec!["plugins/extra/lib/extra.jar".into()];
    let components = [
        sourced_component(&directory, "platform-lib", "lib/platform.jar", platform_lib),
        sourced_component(
            &directory,
            "platform-resources",
            "bin/idea.properties",
            test_manifest("platform_resources"),
        ),
        sourced_component(&directory, "plugins", "plugins/sample/lib/sample.jar", plugins),
        sourced_component(&directory, "extra-plugins", "plugins/extra/lib/extra.jar", extra),
    ];
    let options = ComposeOptions {
        additional_modules: vec![
            "intellij.sample".into(),
            "intellij.shared".into(),
            "intellij.extra".into(),
            "intellij.shared".into(),
        ],
        ..ComposeOptions::default()
    };
    let result = compose_with_merge(&components, &directory.path().join("target"), &options, skip_merge).unwrap();
    // Ordered here rather than left in component order, because each component sorted only the share it packed.
    assert_eq!(
        result.core_class_path,
        ["lib/platform.jar", "plugins/extra/lib/extra.jar", "plugins/sample/lib/sample.jar"]
    );
    assert_eq!(result.additional_modules, ["intellij.sample", "intellij.shared", "intellij.extra"]);
    let manifests: Vec<&ComponentManifest> = components.iter().map(|component| &component.manifest).collect();
    let expected = fingerprint::compute_ide_fingerprint_from_components(&manifests, None, &result.additional_modules).unwrap();
    assert_eq!(result.fingerprint, expected);
}

#[test]
fn composer_puts_the_leading_core_classpath_jars_first() {
    let directory = TempDir::new();
    let mut manifest = test_manifest("platform_core");
    manifest.core_class_path = ["lib/app-backend.jar", "lib/util.jar", "lib/platform-loader.jar", "lib/util-8.jar"]
        .map(String::from)
        .to_vec();
    manifest.entries = ["lib/app-backend.jar", "lib/platform-loader.jar", "lib/util-8.jar"]
        .map(file_entry)
        .to_vec();
    let result = compose(
        &[sourced_component(&directory, "platform", "lib/util.jar", manifest)],
        &directory.path().join("target"),
    )
    .unwrap();
    assert_eq!(
        result.core_class_path,
        ["lib/platform-loader.jar", "lib/util-8.jar", "lib/util.jar", "lib/app-backend.jar"]
    );
}

fn with_part(mut component: DevBuildComponent, part: &str) -> DevBuildComponent {
    component.plugin_classpath_part = Some(PathBuf::from(part));
    component
}

#[test]
fn composer_builds_plugin_classpath_from_the_prefix_and_every_components_records() {
    let directory = TempDir::new();
    let prefix = directory.join("prefix.bin");
    write_file(&prefix, [3, 0, 0, 0, 0]);
    let target = directory.path().join("target");
    let plugin = |name: &str, part: &[u8]| {
        let mut manifest = test_manifest(&format!("plugins_{name}"));
        manifest.plugin = true;
        let file = directory.join(&format!("{name}.part"));
        write_file(&file, part);
        let jar = format!("plugins/{name}/lib/{name}.jar");
        with_part(sourced_component(&directory, name, &jar, manifest), &file)
    };
    let components = [
        plugin("air", &[10]),
        DevBuildComponent::new(test_manifest("platform_lib")),
        plugin("git", &[20, 21]),
        plugin("json", &[30]),
    ];
    let options = ComposeOptions {
        plugin_classpath_prefix: Some(PathBuf::from(&prefix)),
        ..ComposeOptions::default()
    };
    compose_with_merge(&components, &target, &options, skip_merge).unwrap();
    // The prefix, then the summed plugin count as a big-endian short, then the records in component order.
    assert_eq!(
        fs::read(target.join(PLUGIN_CLASSPATH)).unwrap(),
        [3, 0, 0, 0, 0, 0, 3, 10, 20, 21, 30]
    );
}

#[test]
fn composer_rejects_plugin_records_without_a_prefix() {
    let directory = TempDir::new();
    let mut air = test_manifest("plugins_air");
    air.plugin = true;
    let part = directory.join("air.part");
    write_file(&part, [10]);
    let components = [with_part(
        sourced_component(&directory, "air", "plugins/air-plugin/lib/air.jar", air),
        &part,
    )];
    require_error(
        compose(&components, &directory.path().join("target")),
        "plugin-classpath prefix is required",
    );
}

#[test]
fn composer_rejects_a_positive_plugin_count_without_records_before_writing_output() {
    let directory = TempDir::new();
    let mut air = test_manifest("plugins_air");
    air.plugin = true;
    let target = directory.path().join("target");
    let components = [sourced_component(&directory, "air", "plugins/air-plugin/lib/air.jar", air)];
    require_error(
        compose_with_merge(&components, &target, &ComposeOptions::default(), no_merge),
        "plugins_air (1)",
    );
    require_absent(&target);
}

#[test]
fn composer_takes_the_main_class_from_a_component_that_declares_one() {
    let directory = TempDir::new();
    let mut jars = test_manifest("platform_jars");
    jars.main_class = None;
    let composed = compose(
        &[
            sourced_component(&directory, "jars", "lib/packed.jar", jars),
            sourced_component(&directory, "core", "lib/platform.jar", test_manifest("platform_core")),
        ],
        &directory.path().join("target"),
    )
    .unwrap();
    assert_eq!(composed.main_class, "com.intellij.idea.Main");
}

#[test]
fn composer_rejects_inconsistent_compositions_before_writing_output() {
    let directory = TempDir::new();
    let mut jars = test_manifest("platform_jars");
    jars.main_class = None;
    let mut rider = test_manifest("platform_resources");
    rider.platform_prefix = "Rider".into();
    let mut other_main = test_manifest("platform_resources");
    other_main.main_class = Some("com.intellij.idea.OtherMain".into());
    let mut neutral = test_manifest("plugins_json");
    neutral.os.clear();
    neutral.arch.clear();
    neutral.main_class = None;
    let mut mac = test_manifest("platform_resources");
    mac.os = "mac".into();
    mac.arch = "aarch64".into();
    let core = || test_manifest("platform_core");
    let cases: Vec<(Vec<ComponentManifest>, Vec<&str>, &str)> = vec![
        (
            vec![jars],
            vec![],
            "No dev-build component declares an IDE main class: platform_jars",
        ),
        (
            vec![core()],
            vec!["platform_core", "platform_resources"],
            "Dev-build fragments do not match the expected composition; missing: platform_resources; present: platform_core",
        ),
        (
            vec![core(), test_manifest("plugins_stale")],
            vec!["platform_core"],
            "; unexpected: plugins_stale; present: platform_core, plugins_stale",
        ),
        (
            vec![core()],
            vec!["platform_core", "platform_core"],
            "Expected dev-build fragment kinds must be unique, but these occur more than once: platform_core",
        ),
        (
            vec![core(), core()],
            vec![],
            "Dev-build fragment kinds must be unique, but these occur more than once: platform_core",
        ),
        (
            vec![test_manifest("platform_lib"), rider],
            vec![],
            "Dev-build components have different products: 'idea' and 'Rider'",
        ),
        (
            vec![test_manifest("platform_lib"), other_main],
            vec![],
            "different IDE main classes: 'com.intellij.idea.Main' and 'com.intellij.idea.OtherMain'",
        ),
        (
            vec![neutral, core(), mac],
            vec![],
            "different target platforms: 'linux/x64' and 'mac/aarch64'",
        ),
        (vec![], vec![], "At least one dev-build component is required"),
    ];
    for (manifests, expected_fragments, message) in cases {
        let components: Vec<DevBuildComponent> = manifests.into_iter().map(DevBuildComponent::new).collect();
        let target = directory.path().join("target");
        let options = ComposeOptions {
            expected_fragments: expected_fragments.into_iter().map(String::from).collect(),
            ..ComposeOptions::default()
        };
        require_error(compose_with_merge(&components, &target, &options, no_merge), message);
        require_absent(&target);
    }
}

#[test]
fn composer_accepts_a_composition_of_neutral_components_only() {
    let directory = TempDir::new();
    let mut neutral = test_manifest("plugins_json");
    neutral.os.clear();
    neutral.arch.clear();
    let composed = compose(
        &[sourced_component(&directory, "json", "plugins/json/lib/json.jar", neutral)],
        &directory.path().join("target"),
    )
    .unwrap();
    assert_eq!(composed.platform_prefix, "idea");
}

// The regression that turned every AIR UI lane red. A fragment that several distributions share packs a bundled
// plugin, so only the composition spec declares its module.
#[test]
fn composer_declares_the_modules_of_the_spec() {
    let directory = TempDir::new();
    let components = [
        sourced_component(&directory, "plugins-air", "plugins/air/lib/air.jar", test_manifest("plugins_air")),
        sourced_component(
            &directory,
            "plugins-additional",
            "plugins/bridge/lib/bridge.jar",
            test_manifest("plugins_additional"),
        ),
    ];
    let options = ComposeOptions {
        additional_modules: vec!["intellij.air.plugin".into(), "intellij.bridge.plugin".into()],
        ..ComposeOptions::default()
    };
    let result = compose_with_merge(&components, &directory.path().join("target"), &options, skip_merge).unwrap();
    assert_eq!(result.additional_modules, ["intellij.air.plugin", "intellij.bridge.plugin"]);
    // The declaration is part of the launch metadata, so a distribution that only declared more is not reused.
    let manifests: Vec<&ComponentManifest> = components.iter().map(|component| &component.manifest).collect();
    assert_ne!(
        result.fingerprint,
        fingerprint::compute_ide_fingerprint_from_components(&manifests, None, NO_MODULES).unwrap()
    );
}

// A test that requests a bundled plugin reads the bundled modules, so each one must be a plugin of the distribution.
#[test]
fn composer_states_the_bundled_plugin_modules() {
    let directory = TempDir::new();
    let prefix = directory.join("prefix.bin");
    write_file(&prefix, [3, 0, 0, 0, 0]);
    let plugin = |kind: &str| {
        let mut manifest = test_manifest(kind);
        manifest.plugin = true;
        let part = directory.join(&format!("{kind}.part"));
        write_file(&part, [1]);
        with_part(DevBuildComponent::new(manifest), &part)
    };
    let components = [
        DevBuildComponent::new(test_manifest("platform")),
        plugin("intellij.lombok.plugin"),
        plugin("intellij.java.plugin"),
    ];
    let modules = |modules: &[&str]| ComposeOptions {
        plugin_classpath_prefix: Some(PathBuf::from(&prefix)),
        bundled_modules: modules.iter().map(|module| (*module).to_owned()).collect(),
        ..ComposeOptions::default()
    };
    let options = modules(&["intellij.lombok.plugin", "intellij.java.plugin", "intellij.lombok.plugin"]);
    let result = compose_with_merge(&components, &directory.path().join("target"), &options, skip_merge).unwrap();
    assert_eq!(result.bundled_modules, ["intellij.java.plugin", "intellij.lombok.plugin"]);

    for (module, target) in [("intellij.absent.plugin", "absent"), ("platform", "not-a-plugin")] {
        let options = modules(&["intellij.java.plugin", module]);
        require_error(
            compose_with_merge(&components, &directory.path().join(target), &options, skip_merge),
            &format!("Bundled plugin modules name no plugin component of the distribution: {module}"),
        );
        require_absent(directory.path().join(target));
    }
}

#[test]
fn composer_checks_every_destination_before_the_merge_step() {
    let directory = TempDir::new();
    let component = |kind: &str, entries: Vec<ComponentEntry>| DevBuildComponent::new(with_entries(test_manifest(kind), entries));
    let cases = [
        (
            vec![
                component("platform", vec![file_entry("lib/a.jar")]),
                component("plugins", vec![file_entry("lib/a.jar")]),
            ],
            "Dev-build components both provide 'lib/a.jar'",
        ),
        (
            vec![component("platform", vec![file_entry("fingerprint.txt")])],
            "Dev-build components both provide 'fingerprint.txt'",
        ),
        (
            vec![
                component("platform", vec![file_entry("lib/a.jar")]),
                component("plugins", vec![file_entry("Lib/b.jar")]),
            ],
            "conflicting destinations: lib and Lib",
        ),
        (
            vec![component("platform", vec![file_entry("lib"), file_entry("lib/a.jar")])],
            "conflicting destinations: lib contains lib/a.jar",
        ),
        (
            vec![component("platform", vec![link_entry("lib/current", "../../outside")])],
            "symbolic link escapes the directory: lib/current",
        ),
        (
            vec![component(
                "platform",
                vec![link_entry("lib/current", "versions"), link_entry("lib/latest", "current")],
            )],
            "unsupported symbolic link chain",
        ),
        (
            vec![component("platform", vec![file_entry("lib/../a.jar")])],
            "invalid relative path",
        ),
        (
            vec![component("platform", vec![directory_entry("lib", 0o1000)])],
            "invalid size or mode for lib",
        ),
    ];
    for (components, message) in cases {
        let target = directory.path().join("target");
        require_error(
            compose_with_merge(&components, &target, &ComposeOptions::default(), no_merge),
            message,
        );
        require_absent(&target);
    }
    let shared_directory = [
        component("platform", vec![directory_entry("lib", 0o755), file_entry("lib/a.jar")]),
        component("plugins", vec![file_entry("lib/b.jar")]),
    ];
    compose_with_merge(
        &shared_directory,
        &directory.path().join("shared"),
        &ComposeOptions::default(),
        skip_merge,
    )
    .unwrap();
}

#[test]
fn composer_requires_an_absent_or_empty_target() {
    let directory = TempDir::new();
    let components = [DevBuildComponent::new(test_manifest("platform"))];
    let empty = directory.path().join("empty");
    fs::create_dir(&empty).unwrap();
    compose_with_merge(&components, &empty, &ComposeOptions::default(), skip_merge).unwrap();
    let used = directory.path().join("used");
    write_file(used.join("stale.txt"), "stale");
    require_error(
        compose_with_merge(&components, &used, &ComposeOptions::default(), no_merge),
        "The dev-build composition target must be empty",
    );
}

#[test]
fn the_fingerprint_follows_the_declared_executable_flag() {
    let directory = TempDir::new();
    let source = directory.join("ijent");
    let manifest = with_entries(test_manifest("ijent"), vec![file_with_mode("bin/ijent", &source, true, None)]);
    let composed = compose(&[DevBuildComponent::new(manifest.clone())], &directory.path().join("target")).unwrap();
    let non_executable = with_entries(test_manifest("ijent"), vec![sourced_entry("bin/ijent", &source)]);
    let fingerprint =
        |manifest: &ComponentManifest| fingerprint::compute_ide_fingerprint_from_components(&[manifest], None, NO_MODULES).unwrap();
    assert_eq!(composed.fingerprint, fingerprint(&manifest));
    assert_ne!(composed.fingerprint, fingerprint(&non_executable));
}

#[test]
fn absolute_keys_join_the_working_directory_and_refuse_a_collision() {
    let spec_map = |pairs: &[(&str, &str)]| -> BTreeMap<String, String> {
        pairs.iter().map(|(key, value)| ((*key).to_owned(), (*value).to_owned())).collect()
    };
    let directory = std::env::current_dir().unwrap();
    let absolute_a = directory.join("a").to_str().unwrap().to_owned();
    let absolute = absolute_keys(&spec_map(&[("a/b.jar", "_main/a/b.jar")])).unwrap();
    let expected = directory.join(paths::from_slash("a/b.jar").as_ref());
    assert_eq!(absolute.get(&expected).map(String::as_str), Some("_main/a/b.jar"));
    require_error(
        absolute_keys(&spec_map(&[("a", "first"), (absolute_a.as_str(), "second")])),
        "Two dev-build runfile keys name one path",
    );
    require_error(absolute_keys(&spec_map(&[("a/../b", "b")])), "Unsupported host path");
}

#[test]
fn distinct_keeps_the_first_occurrence() {
    assert_eq!(distinct(["b", "a", "b"]), ["b", "a"]);
}

/// A full distribution with a packed `lib/` component and a component that places `product-info.json` at `place`. The
/// copy step writes `product_info` there, as the real copy writes the rendered file.
fn compose_with_product_info(place: &str, product_info: &serde_json::Value, options: &ComposeOptions) -> Result<ComposedBuild> {
    let directory = TempDir::new();
    let mut jars = test_manifest("platform_jars");
    jars.main_class = None;
    jars.core_class_path = ["lib/app.jar", "lib/nio-fs.jar", "lib/platform-loader.jar"]
        .map(String::from)
        .to_vec();
    jars.entries = ["lib/app.jar", "lib/nio-fs.jar", "lib/platform-loader.jar"]
        .map(file_entry)
        .to_vec();
    let resources = with_entries(test_manifest("platform_resources"), vec![file_entry(place)]);
    let components = [DevBuildComponent::new(jars), DevBuildComponent::new(resources)];
    let text = product_info.to_string();
    let target = directory.path().join("target");
    let place = place.to_owned();
    compose_with_merge(&components, &target, options, move |_, target| {
        write_file(target.join(&place), &text);
        Ok(())
    })
}

fn product_info(main_class: &str, boot_class_path: &[&str], arguments: &[&str]) -> serde_json::Value {
    serde_json::json!({
        "name": "IntelliJ IDEA",
        "launch": [{
            "os": "macOS",
            "mainClass": main_class,
            "bootClassPathJarNames": boot_class_path,
            "additionalJvmArguments": arguments,
        }],
    })
}

const NIO_FS_BOOT: &str = "-Xbootclasspath/a:$APP_PACKAGE/Contents/lib/nio-fs.jar";

#[test]
fn the_launch_names_the_core_classpath_without_the_boot_jar() {
    let options = ComposeOptions::default();
    // The order does not matter, and `nio-fs.jar` is on `-Xbootclasspath/a:`.
    let info = product_info("com.intellij.idea.Main", &["platform-loader.jar", "app.jar"], &[NIO_FS_BOOT]);
    compose_with_product_info("Resources/product-info.json", &info, &options).unwrap();
    let info = product_info("com.intellij.idea.Main", &["app.jar", "platform-loader.jar"], &[NIO_FS_BOOT]);
    compose_with_product_info("product-info.json", &info, &options).unwrap();
    // Without the multi-routing file system the launch names `nio-fs.jar` too. A Windows path names the jar as well.
    let info = product_info("com.intellij.idea.Main", &["platform-loader.jar", "app.jar", "nio-fs.jar"], &[]);
    compose_with_product_info("product-info.json", &info, &options).unwrap();
    let windows = r"-Xbootclasspath/a:%IDE_HOME%\lib\x.jar;%IDE_HOME%\lib\nio-fs.jar";
    let info = product_info("com.intellij.idea.Main", &["platform-loader.jar", "app.jar"], &[windows]);
    compose_with_product_info("product-info.json", &info, &options).unwrap();
}

#[test]
fn the_launch_of_a_full_distribution_must_name_the_core_classpath() {
    let options = ComposeOptions::default();
    for (boot_class_path, arguments, message) in [
        (
            &["platform-loader.jar"][..],
            &[NIO_FS_BOOT][..],
            r#"missing: ["app.jar"]; not on the core classpath: []"#,
        ),
        (
            &["platform-loader.jar", "app.jar"][..],
            &[][..],
            r#"missing: ["nio-fs.jar"]; not on the core classpath: []"#,
        ),
        (
            &["platform-loader.jar", "app.jar", "nio-fs.jar", "../plugins/x/lib/x.jar"][..],
            &[NIO_FS_BOOT][..],
            r#"missing: []; not on the core classpath: ["../plugins/x/lib/x.jar", "nio-fs.jar"]"#,
        ),
    ] {
        let info = product_info("com.intellij.idea.Main", boot_class_path, arguments);
        require_error(
            compose_with_product_info("Resources/product-info.json", &info, &options),
            &format!("Resources/product-info.json: the bootClassPathJarNames of the main launch differ from the core classpath; {message}"),
        );
    }
}

#[test]
fn a_modular_loader_launch_names_the_platform_loader_alone() {
    let options = ComposeOptions::default();
    let loader = "com.intellij.platform.runtime.loader.IntellijLoader";
    compose_with_product_info("product-info.json", &product_info(loader, &["platform-loader.jar"], &[]), &options).unwrap();
    require_error(
        compose_with_product_info(
            "product-info.json",
            &product_info(loader, &["platform-loader.jar", "app.jar"], &[]),
            &options,
        ),
        r#"product-info.json: the main launch starts the modular loader, so its bootClassPathJarNames must be [platform-loader.jar], not ["platform-loader.jar", "app.jar"]"#,
    );
}

#[test]
fn only_the_production_places_of_product_info_are_checked() {
    // A legacy `bin/product-info.json` is not a production place, and launch metadata copies no file.
    let info = product_info("com.intellij.idea.Main", &["platform-loader.jar"], &[]);
    compose_with_product_info("bin/product-info.json", &info, &ComposeOptions::default()).unwrap();
    // The rendered file is absent, so a read would fail.
    let directory = TempDir::new();
    let source = directory.join("absent-product-info.json");
    let resources = with_entries(
        test_manifest("platform_resources"),
        vec![sourced_entry("product-info.json", &source)],
    );
    let local = ComposeOptions {
        source_runfiles: Some(runfiles(&[(&source, "_main/product-info.json")])),
        ..ComposeOptions::default()
    };
    compose_with_merge(
        &[DevBuildComponent::new(resources)],
        &directory.path().join("metadata"),
        &local,
        no_merge,
    )
    .unwrap();
    require_error(
        compose_with_product_info("product-info.json", &serde_json::json!({"launch": []}), &ComposeOptions::default()),
        "product-info.json states no launch",
    );
}
