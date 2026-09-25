// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use anyhow::{Result, bail};

/// `SnapshotBuildNumber.SNAPSHOT_SUFFIX`
/// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/SnapshotBuildNumber.kt:21`).
const SNAPSHOT_SUFFIX: &str = ".SNAPSHOT";

/// `SNAPSHOT_VERSION_SEGMENT` of the same file: what a `.SNAPSHOT` suffix becomes. A fixed number and not the build
/// date, so that two builds of one commit state one version.
const SNAPSHOT_VERSION_SEGMENT: &str = "99999999";

/// `CompatibleBuildRange` (`community/platform/build-scripts/src/org/jetbrains/intellij/build/CompatibleBuildRange.kt`).
///
/// `ANY_WITH_SAME_BASELINE` is deprecated in the platform, and no plugin of this population states it. So it is absent
/// here and not ported as an unreachable value.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum CompatibleBuildRange {
    /// The plugin is compatible with this build number alone.
    Exact,
    /// The plugin is compatible with the builds that differ only in the last component.
    RestrictedToSameRelease,
    /// The plugin is compatible with the newer builds of the same baseline.
    NewerWithSameBaseline,
}

/// `computePluginBuildNumber` (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/SnapshotBuildNumber.kt`).
///
/// It replaces the `.SNAPSHOT` suffix with a fixed number, and it appends `.0` when the result has one dot or none. The
/// semantic-version check of the platform stays. A build number that the platform refuses must not reach a jar through
/// this binary instead.
pub(crate) fn plugin_build_number(build_number: &str) -> Result<String> {
    let mut value = if build_number.ends_with(SNAPSHOT_SUFFIX) {
        build_number.replace(SNAPSHOT_SUFFIX, &format!(".{SNAPSHOT_VERSION_SEGMENT}"))
    } else {
        build_number.to_owned()
    };
    if value.matches('.').count() <= 1 {
        value.push_str(".0");
    }
    if !is_semantic_version(&value) {
        bail!("the plugin build number {value} is expected to match the Semantic Versioning, see https://semver.org");
    }
    Ok(value)
}

/// `SemVer.parseFromText(text) != null` (`community/platform/util/base/src/com/intellij/util/text/SemVer.java:189-217`).
///
/// It is a port of that reader and not of the specification. The platform accepts a leading zero and a numeric segment
/// of any width, and it reads the patch up to the first `-` or `+`.
fn is_semantic_version(text: &str) -> bool {
    let Some((major, rest)) = text.split_once('.') else {
        return false;
    };
    let Some((minor, rest)) = rest.split_once('.') else {
        return false;
    };
    let patch = rest.find(['-', '+']).map_or(rest, |end| &rest[..end]);
    is_non_negative_integer(major) && is_non_negative_integer(minor) && is_non_negative_integer(patch)
}

/// `StringUtilRt.parseInt(text, -1) >= 0`, which is what the three segment checks come down to.
///
/// A segment is an optional sign, then decimal digits, within 64 bits. `Integer.parseInt` accepts only 32 bits, and no
/// build number has a longer segment.
fn is_non_negative_integer(text: &str) -> bool {
    text.parse::<i64>().is_ok_and(|value| value >= 0)
}

/// `buildNumberRegex` (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt:24`),
/// which is `(\d+\.)+\d+`. It is a scan and not a regular expression, so the two shapes below read together.
fn build_number_shape(build_number: &str) -> bool {
    let segments: Vec<&str> = build_number.split('.').collect();
    segments.len() >= 2
        && segments
            .iter()
            .all(|segment| !segment.is_empty() && segment.bytes().all(|byte| byte.is_ascii_digit()))
}

/// `digitDotDigitRegex` (`PluginXmlPatcher.kt:25`), which is `\d+\.\d+`.
fn digit_dot_digit_shape(build_number: &str) -> bool {
    build_number_shape(build_number) && build_number.matches('.').count() == 1
}

/// `getCompatiblePlatformVersionRange`
/// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt:27-49`).
///
/// It returns the `since-build` and the `until-build` that the stamps stage writes.
pub(crate) fn compatible_platform_version_range(compatible_build_range: CompatibleBuildRange, build_number: &str) -> (String, String) {
    if compatible_build_range == CompatibleBuildRange::Exact || !build_number_shape(build_number) {
        return (build_number.to_owned(), build_number.to_owned());
    }

    // A build number of this shape has at least one dot.
    let last_dot = build_number.rfind('.').unwrap_or(build_number.len());
    let since_build = if digit_dot_digit_shape(build_number) {
        build_number
    } else {
        &build_number[..last_dot]
    };
    let end = if compatible_build_range == CompatibleBuildRange::RestrictedToSameRelease {
        if digit_dot_digit_shape(build_number) {
            build_number.len()
        } else {
            last_dot
        }
    } else {
        build_number.find('.').unwrap_or(build_number.len())
    };
    (since_build.to_owned(), format!("{}.*", &build_number[..end]))
}
