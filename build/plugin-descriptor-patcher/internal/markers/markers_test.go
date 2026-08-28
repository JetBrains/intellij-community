// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package markers_test

import (
	"strings"
	"testing"

	"jetbrains.com/plugin-descriptor-patcher/internal/markers"
)

const placeholder = "<!-- OS/ARCH-DEPENDENCY-PLACEHOLDER -->"

// TestOsArchRowsCoverEveryPlatform states the replacement of all six (os, arch) pairs.
//
// Six and not one, because the plan emits one row per layout variant and every one of them reaches an action. The
// expectations are transcribed from `osArchDescriptorMarker` (`PluginLayout.kt`), whose `trimMargin` leaves no
// indentation on either line.
func TestOsArchRowsCoverEveryPlatform(t *testing.T) {
	cases := []struct {
		row      string
		expected string
	}{
		{"os-arch:mac:arm64", "<plugin id=\"com.intellij.modules.os.mac\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>"},
		{"os-arch:mac:x86_64", "<plugin id=\"com.intellij.modules.os.mac\"/>\n<plugin id=\"com.intellij.modules.arch.x86_64\"/>"},
		{"os-arch:linux:arm64", "<plugin id=\"com.intellij.modules.os.linux\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>"},
		{"os-arch:linux:x86_64", "<plugin id=\"com.intellij.modules.os.linux\"/>\n<plugin id=\"com.intellij.modules.arch.x86_64\"/>"},
		{"os-arch:windows:arm64", "<plugin id=\"com.intellij.modules.os.windows\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>"},
		{"os-arch:windows:x86_64", "<plugin id=\"com.intellij.modules.os.windows\"/>\n<plugin id=\"com.intellij.modules.arch.x86_64\"/>"},
	}
	for _, one := range cases {
		marker, err := markers.Parse(one.row)
		if err != nil {
			t.Fatalf("%s: %v", one.row, err)
		}
		if marker.Literal != placeholder {
			t.Errorf("%s: literal %q, want %q", one.row, marker.Literal, placeholder)
		}
		if marker.Replacement != one.expected {
			t.Errorf("%s: replacement %q, want %q", one.row, marker.Replacement, one.expected)
		}
	}
}

func TestOsArchRowReplacesAtItsOwnPosition(t *testing.T) {
	source := "<idea-plugin>\n  <depends>\n" + placeholder + "\n  </depends>\n</idea-plugin>"
	patched, err := markers.Apply(source, []string{"os-arch:mac:arm64"})
	if err != nil {
		t.Fatal(err)
	}
	expected := "<idea-plugin>\n  <depends>\n" +
		"<plugin id=\"com.intellij.modules.os.mac\"/>\n<plugin id=\"com.intellij.modules.arch.arm64\"/>" +
		"\n  </depends>\n</idea-plugin>"
	if patched != expected {
		t.Errorf("patched %q, want %q", patched, expected)
	}
}

// TestPlainRowReplacesTheFirstOccurrenceOnly is `checkedReplace`'s `replaceFirst`, which is not `replace`.
func TestPlainRowReplacesTheFirstOccurrenceOnly(t *testing.T) {
	row := "marker:<!-- X -->:<incompatible-with>com.intellij.modules.androidstudio</incompatible-with>"
	patched, err := markers.Apply("a<!-- X -->b<!-- X -->c", []string{row})
	if err != nil {
		t.Fatal(err)
	}
	expected := "a<incompatible-with>com.intellij.modules.androidstudio</incompatible-with>b<!-- X -->c"
	if patched != expected {
		t.Errorf("patched %q, want %q", patched, expected)
	}
}

// TestRowsApplyInOrder states that the table is a sequence and not a set: a later row sees an earlier row's output.
func TestRowsApplyInOrder(t *testing.T) {
	patched, err := markers.Apply("<A>", []string{"marker:<A>:<B>", "marker:<B>:<C>"})
	if err != nil {
		t.Fatal(err)
	}
	if patched != "<C>" {
		t.Errorf("patched %q, want %q", patched, "<C>")
	}
}

func TestEmptyTableChangesNothing(t *testing.T) {
	patched, err := markers.Apply("<idea-plugin/>", nil)
	if err != nil {
		t.Fatal(err)
	}
	if patched != "<idea-plugin/>" {
		t.Errorf("patched %q", patched)
	}
}

// TestRefusals is the negative control per branch. Every one of them must fail, because a row this producer cannot read
// would otherwise emit an unpatched descriptor.
func TestRefusals(t *testing.T) {
	cases := []struct {
		name string
		text string
		rows []string
		says string
	}{
		{"an absent literal", "<idea-plugin/>", []string{"os-arch:mac:arm64"}, "does not state"},
		{"an unknown shape", placeholder, []string{"regex:a:b"}, "marker shape this tool does not know"},
		{"no shape separator", placeholder, []string{"os-arch"}, "is not"},
		{"a wrong os id", placeholder, []string{"os-arch:macos:arm64"}, "does not name an OsFamily.osId"},
		{"a wrong architecture", placeholder, []string{"os-arch:mac:aarch64"}, "no JvmArchitecture.marketplaceName"},
		{"an os-arch row with no architecture", placeholder, []string{"os-arch:mac"}, "does not name an OsFamily.osId"},
		{"a plain row with no replacement separator", placeholder, []string{"marker:<A>"}, "is not"},
		{"a plain row with an empty literal", placeholder, []string{"marker::<B>"}, "is not"},
	}
	for _, one := range cases {
		_, err := markers.Apply(one.text, one.rows)
		if err == nil {
			t.Errorf("%s: no error", one.name)
			continue
		}
		if !strings.Contains(err.Error(), one.says) {
			t.Errorf("%s: %q does not say %q", one.name, err.Error(), one.says)
		}
	}
}
