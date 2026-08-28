// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package stamps

import (
	"fmt"
	"strconv"
	"strings"
	"time"
)

// snapshotSuffix is `SnapshotBuildNumber.SNAPSHOT_SUFFIX`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/SnapshotBuildNumber.kt:21`).
const snapshotSuffix = ".SNAPSHOT"

// CompatibleBuildRange is `CompatibleBuildRange`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/CompatibleBuildRange.kt`).
//
// `ANY_WITH_SAME_BASELINE` is deprecated in the platform and no plugin of this population states it, so it is absent
// here rather than ported and unreachable.
type CompatibleBuildRange int

const (
	// RangeExact makes the plugin compatible with this build number alone.
	RangeExact CompatibleBuildRange = iota
	// RangeRestrictedToSameRelease makes it compatible with builds differing only in the last component.
	RangeRestrictedToSameRelease
	// RangeNewerWithSameBaseline makes it compatible with newer builds of the same baseline.
	RangeNewerWithSameBaseline
)

// PluginBuildNumber is `computePluginBuildNumber`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/SnapshotBuildNumber.kt:50-63`).
//
// It replaces the `.SNAPSHOT` suffix by the build date and appends `.0` when the result has one dot or none. The
// semantic-version check is the platform's, and it is kept: a build number the platform would refuse must not reach a
// jar through this binary instead.
func PluginBuildNumber(buildNumber string, buildDateInSeconds int64) (string, error) {
	value := buildNumber
	if strings.HasSuffix(value, snapshotSuffix) {
		buildDate := time.Unix(buildDateInSeconds, 0).UTC()
		value = strings.ReplaceAll(value, snapshotSuffix, "."+buildDate.Format("20060102"))
	}
	if strings.Count(value, ".") <= 1 {
		value += ".0"
	}
	if !isSemanticVersion(value) {
		return "", fmt.Errorf(
			"the plugin build number %s is expected to match the Semantic Versioning, see https://semver.org", value)
	}
	return value, nil
}

// isSemanticVersion is `SemVer.parseFromText(text) != null`
// (`community/platform/util/base/src/com/intellij/util/text/SemVer.java:189-217`).
//
// It is a port of that reader and not of the specification: the platform accepts a leading zero and a numeric segment
// of any width, and it reads the patch up to the first `-` or `+`.
func isSemanticVersion(text string) bool {
	majorEnd := strings.IndexByte(text, '.')
	if majorEnd < 0 {
		return false
	}
	minorEnd := strings.IndexByte(text[majorEnd+1:], '.')
	if minorEnd < 0 {
		return false
	}
	minorEnd += majorEnd + 1

	preRelease := strings.IndexByte(text[minorEnd+1:], '-')
	buildMeta := strings.IndexByte(text[minorEnd+1:], '+')
	if preRelease >= 0 {
		preRelease += minorEnd + 1
	}
	if buildMeta >= 0 {
		buildMeta += minorEnd + 1
	}
	patchEnd := len(text)
	switch {
	case preRelease >= 0 && buildMeta >= 0:
		patchEnd = min(preRelease, buildMeta)
	case preRelease >= 0:
		patchEnd = preRelease
	case buildMeta >= 0:
		patchEnd = buildMeta
	}

	return isNonNegativeInteger(text[:majorEnd]) &&
		isNonNegativeInteger(text[majorEnd+1:minorEnd]) &&
		isNonNegativeInteger(text[minorEnd+1:patchEnd])
}

// isNonNegativeInteger is `StringUtilRt.parseInt(text, -1) >= 0`, which is what the three segment checks come down to.
func isNonNegativeInteger(text string) bool {
	value, err := strconv.Atoi(text)
	return err == nil && value >= 0
}

// buildNumberShape is `buildNumberRegex`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt:24`), which is
// `(\d+\.)+\d+`. It is written as a scan rather than a regular expression, so that the two shapes below read together.
func buildNumberShape(buildNumber string) bool {
	segments := strings.Split(buildNumber, ".")
	if len(segments) < 2 {
		return false
	}
	for _, segment := range segments {
		if segment == "" {
			return false
		}
		for i := 0; i < len(segment); i++ {
			if segment[i] < '0' || segment[i] > '9' {
				return false
			}
		}
	}
	return true
}

// digitDotDigitShape is `digitDotDigitRegex` (`PluginXmlPatcher.kt:25`), which is `\d+\.\d+`.
func digitDotDigitShape(buildNumber string) bool {
	return buildNumberShape(buildNumber) && strings.Count(buildNumber, ".") == 1
}

// CompatiblePlatformVersionRange is `getCompatiblePlatformVersionRange`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt:27-49`).
//
// It returns the `since-build` and the `until-build` the stamps stage writes.
func CompatiblePlatformVersionRange(compatibleBuildRange CompatibleBuildRange, buildNumber string) (string, string) {
	if compatibleBuildRange == RangeExact || !buildNumberShape(buildNumber) {
		return buildNumber, buildNumber
	}

	sinceBuild := buildNumber
	if !digitDotDigitShape(buildNumber) {
		sinceBuild = buildNumber[:strings.LastIndexByte(buildNumber, '.')]
	}
	var end int
	if compatibleBuildRange == RangeRestrictedToSameRelease {
		if digitDotDigitShape(buildNumber) {
			end = len(buildNumber)
		} else {
			end = strings.LastIndexByte(buildNumber, '.')
		}
	} else {
		end = strings.IndexByte(buildNumber, '.')
	}
	return sinceBuild, buildNumber[:end] + ".*"
}
