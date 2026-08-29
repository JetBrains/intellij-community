// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Package markers applies the raw descriptor text patch a plan entry states as data.
//
// It is `applyDescriptorMarkers` and `parseDescriptorMarkerRow`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/dev/DevDistPluginDescriptorMain.kt`),
// rule for rule. `PluginLayout.rawPluginXmlPatcher` is a Kotlin lambda, and a plan cannot state one; a layout that
// states its patch as a `DescriptorMarkerPatcher` states replacements instead, and this package is what a producer
// does with them.
package markers

import (
	"fmt"
	"strings"
)

// osIds is `OsFamily.osId` (`OsFamily.kt:25-27`), which is also `BuildOptions.OS_WINDOWS` and its two siblings.
var osIds = map[string]bool{"windows": true, "mac": true, "linux": true}

// marketplaceArchitectures is `JvmArchitecture.marketplaceName` (`JvmArchitecture.kt:15-16`).
var marketplaceArchitectures = map[string]bool{"x86_64": true, "arm64": true}

// osArchPlaceholder is `OS_SPECIFIC_DEPENDENCIES_PLUGIN_XML_PLACEHOLDER` (`PluginLayout.kt:766`).
const osArchPlaceholder = "<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->"

// Marker is one replacement: the text that must be there, and what takes its place.
//
// It is `DescriptorMarker` (`PluginLayout.kt`).
type Marker struct {
	Literal     string
	Replacement string
}

// Apply replaces the first occurrence of each row's literal, in the table's order.
//
// ### Why a plain replacement and not a regular expression
//
// `checkedReplace` (`BuildUtils.kt:21`) compiles the literal as a regular expression and reads `$` and `\` in the
// replacement. Go's `regexp` is RE2 and Java's `Pattern` is not, so a row that reached either engine would be a row the
// two producers could read differently. The generator refuses a row whose literal states a regular-expression
// metacharacter and one whose replacement states `$` or `\`, so a plain replacement is what `checkedReplace` does for
// every row that gets here.
//
// A literal the descriptor does not state fails the run. `checkedReplace` tolerates that case outside TeamCity, for an
// `Update IDE from Sources` run that re-patches a text it already patched; this action reads a declared source file and
// can never be in that state.
func Apply(text string, rows []string) (string, error) {
	result := text
	for _, row := range rows {
		marker, err := Parse(row)
		if err != nil {
			return "", err
		}
		at := strings.Index(result, marker.Literal)
		if at < 0 {
			return "", fmt.Errorf("the descriptor does not state '%s', which the marker table replaces", marker.Literal)
		}
		result = result[:at] + marker.Replacement + result[at+len(marker.Literal):]
	}
	return result, nil
}

// Parse reads one marker-table row.
//
// `os-arch:<osId>:<marketplaceName>` names the operating system and the architecture, and [OsArchMarker] builds the
// replacement - the text holds a newline the request's parameter file could not carry on one line, and keeping it in one
// function is what makes the two producers agree about it. `marker:<literal>:<replacement>` states a plain replacement,
// and the literal ends at the first `:`.
//
// An unknown shape is an error and never a row this producer skips, because a skipped row emits an unpatched text.
func Parse(row string) (Marker, error) {
	shape, rest, found := strings.Cut(row, ":")
	if !found || shape == "" {
		return Marker{}, fmt.Errorf("a marker row is '<shape>:...', and '%s' is not", row)
	}
	switch shape {
	case "os-arch":
		osID, architecture, split := strings.Cut(rest, ":")
		if !split || !osIds[osID] {
			return Marker{}, fmt.Errorf("'%s' does not name an OsFamily.osId and a JvmArchitecture.marketplaceName", row)
		}
		if !marketplaceArchitectures[architecture] {
			return Marker{}, fmt.Errorf("'%s' is no JvmArchitecture.marketplaceName", architecture)
		}
		return OsArchMarker(osID, architecture), nil
	case "marker":
		literal, replacement, split := strings.Cut(rest, ":")
		if !split || literal == "" {
			return Marker{}, fmt.Errorf("a marker row is 'marker:<literal>:<replacement>', and '%s' is not", row)
		}
		return Marker{Literal: literal, Replacement: replacement}, nil
	}
	return Marker{}, fmt.Errorf("'%s' states a marker shape this tool does not know, so the descriptor would be emitted unpatched", row)
}

// OsArchMarker is `osArchDescriptorMarker` (`PluginLayout.kt`), text for text.
//
// The two `<plugin id=.../>` lines and the newline between them are the whole replacement, and `trimMargin` leaves no
// indentation on either line.
func OsArchMarker(osID string, marketplaceArchitecture string) Marker {
	return Marker{
		Literal: osArchPlaceholder,
		Replacement: "<plugin id=\"com.intellij.modules.os." + osID + "\"/>\n" +
			"<plugin id=\"com.intellij.modules.arch." + marketplaceArchitecture + "\"/>",
	}
}
