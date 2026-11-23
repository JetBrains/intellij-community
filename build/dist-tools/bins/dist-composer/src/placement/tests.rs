// The tests of the home placement check. They need manifests only.

use std::collections::BTreeMap;

use component::manifest::ComponentManifest;

use crate::placement::{Placement, check_placement};
use crate::test_support::*;

fn placement(files: &[(&str, &str)], trees: &[(&str, &str)], executables: &[&str]) -> Placement {
    let map = |pairs: &[(&str, &str)]| -> BTreeMap<String, String> {
        pairs.iter().map(|(key, value)| ((*key).to_owned(), (*value).to_owned())).collect()
    };
    Placement {
        files: map(files),
        trees: map(trees),
        executables: executables.iter().map(|path| (*path).to_owned()).collect(),
    }
}

fn platform() -> ComponentManifest {
    with_entries(
        test_manifest("platform"),
        vec![
            directory_entry("lib", 0o755),
            sourced_entry("lib/util.jar", "out/util.jar"),
            file_with_mode("bin/restarter", "external/restarter", true, None),
            sourced_entry(
                "lib/jna/darwin/libjnidispatch.jnilib",
                "out/jna/native/darwin/libjnidispatch.jnilib",
            ),
        ],
    )
}

fn plugin() -> ComponentManifest {
    with_entries(
        test_manifest("intellij.kotlin.plugin"),
        vec![
            directory_entry("plugins/Kotlin", 0o755),
            sourced_entry("plugins/Kotlin/lib/kotlin-plugin.jar", "out/remainder/lib/kotlin-plugin.jar"),
            sourced_entry("plugins/Kotlin/lib/modules/intellij.kotlin.base.jar", "out/base.jar"),
            link_entry("plugins/Kotlin/kotlinc/current", "lib"),
        ],
    )
}

/// A plugin places each remainder file and each reused jar as a file, and each tree asset as a tree.
fn complete() -> Placement {
    placement(
        &[
            ("lib/util.jar", "out/util.jar"),
            ("bin/restarter", "external/restarter"),
            ("plugins/Kotlin/lib/kotlin-plugin.jar", "out/remainder/lib/kotlin-plugin.jar"),
            ("plugins/Kotlin/lib/modules/intellij.kotlin.base.jar", "out/base.jar"),
        ],
        &[("lib/jna", "out/jna/native"), ("plugins/Kotlin/kotlinc", "out/remainder/kotlinc")],
        &["bin/restarter"],
    )
}

fn error(placement: &Placement) -> String {
    let (platform, plugin) = (platform(), plugin());
    format!("{:#}", check_placement(&[&platform, &plugin], placement).unwrap_err())
}

#[test]
fn a_complete_placement_passes() {
    let (platform, plugin) = (platform(), plugin());
    check_placement(&[&platform, &plugin], &complete()).unwrap();
}

#[test]
fn an_unplaced_file_fails() {
    let mut placement = complete();
    placement.files.remove("lib/util.jar");
    let message = error(&placement);
    assert!(
        message.contains("'lib/util.jar' of the component 'platform' is not placed"),
        "{message}"
    );
}

#[test]
fn a_file_with_another_source_fails() {
    let mut placement = complete();
    placement.files.insert("lib/util.jar".to_owned(), "out/other.jar".to_owned());
    let message = error(&placement);
    assert!(
        message.contains("'lib/util.jar' comes from out/util.jar, and the placement states out/other.jar"),
        "{message}"
    );
}

#[test]
fn a_placed_file_without_an_entry_fails() {
    let mut placement = complete();
    placement.files.insert("lib/extra.jar".to_owned(), "out/extra.jar".to_owned());
    let message = error(&placement);
    assert!(
        message.contains("the placement states 'lib/extra.jar', which no component manifest names"),
        "{message}"
    );
}

#[test]
fn a_different_executable_flag_fails() {
    let mut placement = complete();
    placement.executables.clear();
    let message = error(&placement);
    assert!(
        message.contains("'bin/restarter' is executable in the manifest and not executable in the placement"),
        "{message}"
    );
}

#[test]
fn a_tree_member_at_another_relative_path_fails() {
    let mut placement = complete();
    placement.trees.insert("lib/jna".to_owned(), "out/jna".to_owned());
    let message = error(&placement);
    assert!(
        message.contains("and the tree at 'lib/jna' holds out/jna/darwin/libjnidispatch.jnilib"),
        "{message}"
    );
}

#[test]
fn a_reused_jar_from_the_remainder_fails() {
    let mut placement = complete();
    placement.files.insert(
        "plugins/Kotlin/lib/modules/intellij.kotlin.base.jar".to_owned(),
        "out/remainder/lib/modules/intellij.kotlin.base.jar".to_owned(),
    );
    let message = error(&placement);
    assert!(
        message.contains(
            "'plugins/Kotlin/lib/modules/intellij.kotlin.base.jar' comes from out/base.jar, and the placement states out/remainder/lib/modules/intellij.kotlin.base.jar"
        ),
        "{message}"
    );
}

#[test]
fn a_link_outside_a_root_fails() {
    let mut placement = complete();
    placement.trees.remove("plugins/Kotlin/kotlinc");
    let message = error(&placement);
    assert!(
        message.contains("'plugins/Kotlin/kotlinc/current' is a link outside a placed directory"),
        "{message}"
    );
}

#[test]
fn an_empty_directory_fails() {
    let mut empty = platform();
    empty.entries.push(directory_entry("lib/empty", 0o755));
    let plugin = plugin();
    let message = format!("{:#}", check_placement(&[&empty, &plugin], &complete()).unwrap_err());
    assert!(message.contains("'lib/empty' is an empty directory"), "{message}");
}

#[test]
fn an_unused_root_fails() {
    let mut placement = complete();
    placement.trees.insert("lib/skiko".to_owned(), "out/skiko/native".to_owned());
    let message = error(&placement);
    assert!(
        message.contains("the placement links 'lib/skiko', and no component manifest names an entry there"),
        "{message}"
    );
}
