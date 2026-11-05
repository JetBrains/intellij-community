// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The scalars that this binary computes from the build number and does not receive.
//!
//! The assembly computes the first two with `computePluginBuildNumber` and `getCompatiblePlatformVersionRange`, and
//! this module is their port. A disagreement here moves the `<version>` and the `<idea-version>` of every plugin. The
//! fragment that reads the produced file refuses that in `checkProducedPluginDescriptor`. The release version and the
//! EAP flag of a retained `product-descriptor` are dev distribution rules.

use super::{CompatibleBuildRange, compatible_platform_version_range, is_eap, plugin_build_number, release_version_for_licensing};

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
            "newer over two segments keeps both in since",
            CompatibleBuildRange::NewerWithSameBaseline,
            "263.100",
            "263.100",
            "263.*",
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

/// The baseline names the release: `271` is 2027.1. The rest of the build number does not change the version.
#[test]
fn the_release_version_comes_from_the_baseline() {
    for (build_number, want) in [
        ("271.SNAPSHOT", "2027100"),
        ("271.100.5", "2027100"),
        ("263.SNAPSHOT", "2026300"),
        ("263", "2026300"),
    ] {
        assert_eq!(release_version_for_licensing(build_number).unwrap(), want, "{build_number}");
    }
    for build_number in ["2027.1", "27.1", "27a.1", ""] {
        assert!(release_version_for_licensing(build_number).is_err(), "{build_number:?}");
    }
}

/// A `.SNAPSHOT` build number is an EAP, and a numeric one is not.
#[test]
fn a_snapshot_is_an_eap() {
    assert!(is_eap("271.SNAPSHOT"));
    assert!(is_eap("271.100.SNAPSHOT"));
    assert!(!is_eap("271.100.5"));
    assert!(!is_eap("271"));
}
