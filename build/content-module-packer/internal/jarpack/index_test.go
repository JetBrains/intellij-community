// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"strings"
	"testing"
)

func TestRegisterDirsWalksAncestorsOfResourcesOnly(t *testing.T) {
	builder := newIndexBuilder()
	builder.addFile("com/example/Service.class")
	builder.addFile("messages/nested/Bundle.properties")
	if _, registered := builder.dirsToRegister["com/example"]; registered {
		// AddDirEntriesMode.NONE never registers a class directory.
		t.Error("a class directory was registered")
	}
	if got, want := strings.Join(builder.dirOrder, ","), "messages/nested,messages"; got != want {
		t.Errorf("registered directories are %q, want %q - deepest first, stopping at the first already known", got, want)
	}
}

func TestRegisterDirsSkipsPackageHtmlAndTheManifest(t *testing.T) {
	builder := newIndexBuilder()
	builder.addFile("org/example/package.html")
	builder.addFile(ManifestEntryName)
	if len(builder.dirOrder) != 0 {
		t.Errorf("registered %v, want nothing", builder.dirOrder)
	}
}

func TestFinishOrdersDirectoriesAsJavaSortsStrings(t *testing.T) {
	builder := newIndexBuilder()
	// U+10437 encodes as the surrogate pair D801 DC37, which sorts *below* U+FFFD - the opposite of what comparing runes
	// or bytes gives, and the reason compareJavaString exists.
	for _, name := range []string{"z/�/a.txt", "z/\U00010437/a.txt", "z/a/a.txt"} {
		builder.addFile(name)
	}
	if err := builder.finish(); err != nil {
		t.Fatal(err)
	}
	var dirs []string
	for i, name := range builder.names {
		if builder.entries[i].size == -1 {
			dirs = append(dirs, string(name))
		}
	}
	if got, want := strings.Join(dirs, ","), "z,z/a,z/\U00010437,z/�"; got != want {
		t.Errorf("directory order is %q, want %q", got, want)
	}
}

func TestAddRefusesAKeyCollision(t *testing.T) {
	// The Kotlin builder's set is keyed on the hash alone and fails hard on a collision, so this must too rather than
	// silently keep one of the two entries.
	builder := newIndexBuilder()
	name := []byte("org/example/A.class")
	if err := builder.add(ikvEntry{key: 42, offset: 0, size: 1}, name); err != nil {
		t.Fatal(err)
	}
	if err := builder.add(ikvEntry{key: 42, offset: 8, size: 1}, []byte("org/example/B.class")); err == nil {
		t.Error("a second entry with the same key was accepted")
	}
}

func TestPayloadSizeMatchesWhatPayloadWrites(t *testing.T) {
	builder := newIndexBuilder()
	for _, name := range []string{"com/example/Service.class", "messages/Bundle.properties", "org/example/package.html"} {
		builder.addFile(name)
		if err := builder.add(ikvEntry{key: hashName([]byte(name)), offset: 30, size: 4}, []byte(name)); err != nil {
			t.Fatal(err)
		}
	}
	if err := builder.finish(); err != nil {
		t.Fatal(err)
	}
	if got, want := len(builder.payload()), builder.payloadSize(); got != want {
		// payloadSize is what the caller allocates and what the index pointer is computed from, so a disagreement here
		// is a wrong pointer in every jar rather than a slow one.
		t.Errorf("payload is %d bytes, payloadSize says %d", got, want)
	}
}
