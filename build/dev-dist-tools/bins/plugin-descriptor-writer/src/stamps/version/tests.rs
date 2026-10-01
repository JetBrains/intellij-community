// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The two scalars that this binary computes and does not receive.
//!
//! The assembly computes them with `computePluginBuildNumber` and `getCompatiblePlatformVersionRange`, and this module
//! is their port. A disagreement here moves the `<version>` and the `<idea-version>` of every plugin. The fragment that
//! reads the produced file refuses that in `checkProducedPluginDescriptor`.

use super::{CompatibleBuildRange, compatible_platform_version_range, plugin_build_number};

#[test]
fn the_plugin_build_number() {
    for (name, build_number, want) in [
        (
            "a snapshot takes the fixed number and a nightly zero",
            "263.SNAPSHOT",
            "263.99999999.0",
        ),
        ("a three-segment snapshot needs no zero", "263.100.SNAPSHOT", "263.100.99999999"),
        ("a released number is unchanged", "263.100.5", "263.100.5"),
        ("a two-segment number takes a zero", "263.100", "263.100.0"),
    ] {
        assert_eq!(plugin_build_number(build_number).unwrap(), want, "{name}");
    }
}

/// The platform checks the result against Semantic Versioning and fails when it does not match
/// (`SnapshotBuildNumber.kt`). This check prevents a build number that reaches a jar unchecked.
#[test]
fn a_build_number_that_is_not_semantic_fails() {
    for build_number in ["263.x.1", "abc", "263..1"] {
        assert!(plugin_build_number(build_number).is_err(), "{build_number} must be refused");
    }
}

#[test]
fn the_compatible_platform_version_range() {
    for (name, range, build_number, since, until) in [
        (
            "exact pins both ends",
            CompatibleBuildRange::Exact,
            "263.100.5",
            "263.100.5",
            "263.100.5",
        ),
        (
            "restricted to the same release keeps every segment but the last",
            CompatibleBuildRange::RestrictedToSameRelease,
            "263.100.5",
            "263.100",
            "263.100.*",
        ),
        (
            "restricted over two segments keeps both",
            CompatibleBuildRange::RestrictedToSameRelease,
            "263.100",
            "263.100",
            "263.100.*",
        ),
        (
            "newer with the same baseline stars the baseline",
            CompatibleBuildRange::NewerWithSameBaseline,
            "263.100.5",
            "263.100",
            "263.*",
        ),
        (
            "a build number of another shape pins both ends",
            CompatibleBuildRange::NewerWithSameBaseline,
            "263.SNAPSHOT",
            "263.SNAPSHOT",
            "263.SNAPSHOT",
        ),
    ] {
        assert_eq!(
            compatible_platform_version_range(range, build_number),
            (since.to_owned(), until.to_owned()),
            "{name}"
        );
    }
}
