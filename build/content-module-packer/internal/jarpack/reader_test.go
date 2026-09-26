// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"encoding/binary"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func openedNames(t *testing.T, path string) []string {
	t.Helper()
	jar, err := OpenJar(path)
	if err != nil {
		t.Fatal(err)
	}
	defer jar.Close()
	names := make([]string, 0, len(jar.Entries))
	for _, entry := range jar.Entries {
		// Cloned because these outlive the Close above: an entry's name is a string over the source's mapping, which is
		// the contract Jar documents, and reading one afterwards is a segfault rather than a stale value.
		names = append(names, strings.Clone(entry.Name))
	}
	return names
}

func TestOpenJarKeepsCentralDirectoryOrderAndDropsWhatIsNeverInherited(t *testing.T) {
	path := writeZipJar(t, "source.jar",
		sourceEntry{name: "z/", data: ""},
		sourceEntry{name: "z/Last.class", data: "z"},
		sourceEntry{name: IndexFileName, data: "a stale index"},
		sourceEntry{name: "a/First.class", data: "a"},
	)
	if got, want := strings.Join(openedNames(t, path), ","), "z/Last.class,a/First.class"; got != want {
		t.Errorf("entries are %q, want %q", got, want)
	}
}

func TestOpenJarCarriesTheCentralDirectoryCRCOfADeflatedEntry(t *testing.T) {
	// The point of the carried CRC: a zip CRC is defined over the uncompressed data, so a DEFLATED source's central
	// directory already holds the value the STORED output needs. `archive/zip` writes a data descriptor, so the *local*
	// header's CRC is zero here - reading it from there would produce a jar every JVM rejects.
	content := "the uncompressed bytes, which are what the CRC covers"
	path := writeZipJar(t, "deflated.jar", sourceEntry{name: "org/Deflated.class", data: content})
	jar, err := OpenJar(path)
	if err != nil {
		t.Fatal(err)
	}
	defer jar.Close()
	if len(jar.Entries) != 1 {
		t.Fatalf("%d entries, want 1", len(jar.Entries))
	}
	if got, want := jar.Entries[0].CRC, crc32Of([]byte(content)); got != want {
		t.Errorf("carried CRC is %08x, want %08x", got, want)
	}
	data, err := jar.Data(jar.Entries[0])
	if err != nil {
		t.Fatal(err)
	}
	if got := string(data); got != content {
		t.Errorf("inflated data is %q, want %q", got, content)
	}
}

func TestOpenJarTakesTheDataOffsetFromTheLocalHeader(t *testing.T) {
	path := writeRawJar(t, "asymmetric.jar", rawEntry{
		name:         "org/Wide.class",
		data:         "the real bytes",
		localExtra:   []byte{0x55, 0x54, 5, 0, 1, 1, 2, 3, 4},
		centralExtra: []byte{0x55, 0x54, 1, 0, 1},
	})
	jar, err := OpenJar(path)
	if err != nil {
		t.Fatal(err)
	}
	defer jar.Close()
	data, err := jar.Data(jar.Entries[0])
	if err != nil {
		t.Fatal(err)
	}
	if got, want := string(data), "the real bytes"; got != want {
		t.Errorf("data is %q, want %q - the central extra length was used", got, want)
	}
}

func TestOpenJarRefusesANameThatIsNotUTF8(t *testing.T) {
	// Go keeps the raw bytes and the Kotlin reader would decode them to U+FFFD, so the two would hash the same entry
	// differently. Refusing is the only answer that cannot diverge silently.
	path := writeRawJar(t, "latin1.jar", rawEntry{name: "org/caf\xe9.properties", data: "value"})
	if _, err := OpenJar(path); err == nil {
		t.Error("a name that is not valid UTF-8 was accepted")
	} else if !strings.Contains(err.Error(), "not valid UTF-8") {
		t.Errorf("error is %q, want it to name the encoding", err)
	}
}

func TestOpenJarRefusesWhatIsTooSmallToBeAZip(t *testing.T) {
	path := filepath.Join(t.TempDir(), "truncated.jar")
	if err := os.WriteFile(path, []byte("not a zip"), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := OpenJar(path); err == nil {
		t.Error("a 9-byte file was accepted as a zip")
	}
}

func TestOpenJarRefusesAFileWithNoEndRecord(t *testing.T) {
	path := filepath.Join(t.TempDir(), "garbage.jar")
	if err := os.WriteFile(path, make([]byte, 512), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := OpenJar(path); err == nil {
		t.Error("512 zero bytes were accepted as a zip")
	}
}

func TestOpenJarRefusesACentralDirectoryThatRunsPastItsEnd(t *testing.T) {
	// A record whose name length reaches beyond the directory is a corrupt jar. It has to be reported as one, naming the
	// file, rather than crash the packer with a slice-bounds panic that names nothing.
	path := writeRawJar(t, "corrupt.jar", rawEntry{name: "org/Example.class", data: "bytes"})
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	eocd := len(data) - eocdMinSize
	centralOffset := binary.LittleEndian.Uint32(data[eocd+16:])
	// Offset 28 of a central directory record is its file name length.
	binary.LittleEndian.PutUint16(data[int(centralOffset)+28:], 0xff00)
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := OpenJar(path); err == nil {
		t.Error("a central directory record reaching past its end was accepted")
	}
}
