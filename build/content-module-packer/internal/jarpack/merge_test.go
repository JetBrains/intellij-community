// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"strings"
	"testing"
)

// The digests below are the bytes this packer produced when it was proved byte-identical to the Kotlin
// `@rules_jvm//content-module-packer` over 192 real jars, 26 real recipes and 4 constructed ones. They are the gate,
// and the reason they are here rather than only in a distribution build: the distribution consumes these jars, so a
// packer that drifts surfaces at class-load time in the IDE and nowhere earlier.
//
// A digest that changes is not a test to update. It is either a deliberate format change - in which case the Kotlin
// `JarPackager` has to make the same one, and `./build/dev-dist.cmd jars` is what says so - or a regression.

// moduleSource is what `jvm_library` hands the packer: a Bazel-built module output jar, which carries directory records,
// the build-time inputs the filter drops, and whatever a previous packing left behind.
func moduleSource(t *testing.T, name string) string {
	return writeZipJar(t, name,
		sourceEntry{name: "com/", data: ""},
		sourceEntry{name: "com/example/", data: ""},
		sourceEntry{name: "com/example/Service.class", data: "class bytes"},
		sourceEntry{name: "com/example/nested/Inner.class", data: "inner bytes"},
		sourceEntry{name: "messages/Bundle.properties", data: "key=value"},
		sourceEntry{name: "icon-robots.txt", data: "dropped: a build-time input"},
		sourceEntry{name: "com/example/icon-robots.txt", data: "dropped: same, nested"},
		sourceEntry{name: ".unmodified", data: "dropped: compilation cache leftover"},
		sourceEntry{name: "classpath.index", data: "dropped: compilation cache leftover"},
		sourceEntry{name: "module-info.class", data: "dropped"},
		sourceEntry{name: IndexFileName, data: "dropped: a stale index is never inherited"},
		sourceEntry{name: ManifestEntryName, data: "Manifest-Version: 1.0\r\n\r\n"},
	)
}

func TestPackModuleOutputDropsWhatADistributionNeverInherits(t *testing.T) {
	data, duplicates := pack(t, MergeSpec{
		Output:  "intellij.example.jar",
		Sources: []Source{{Path: moduleSource(t, "module.jar"), Filter: ModuleOutputNameFilter}},
	})
	if len(duplicates) != 0 {
		t.Errorf("one source cannot produce duplicates, got %v", duplicates)
	}
	if got, want := strings.Join(entryNames(t, data), ","),
		"com/example/Service.class,com/example/nested/Inner.class,messages/Bundle.properties,__index__"; got != want {
		t.Errorf("entries are %q, want %q", got, want)
	}
	if got, want := digest(data), goldenModuleOnly; got != want {
		t.Errorf("packed bytes hash to %s, want %s", got, want)
	}
}

func TestPackKeepsTheManifestOfASingleMeaningfulSource(t *testing.T) {
	data, _ := pack(t, MergeSpec{
		Output:       "intellij.example.jar",
		Sources:      []Source{{Path: moduleSource(t, "module.jar"), Filter: ModuleOutputNameFilter}},
		KeepManifest: true,
	})
	names := strings.Join(entryNames(t, data), ",")
	if !strings.Contains(names, ManifestEntryName) {
		t.Errorf("entries are %q, want the manifest kept", names)
	}
	if got, want := digest(data), goldenKeepManifest; got != want {
		t.Errorf("packed bytes hash to %s, want %s", got, want)
	}
}

// librarySource is a third-party jar: DEFLATED, and carrying every class of name the library filter exists to drop.
func librarySource(t *testing.T, name string) string {
	return writeZipJar(t, name,
		sourceEntry{name: "org/thirdparty/Api.class", data: "third party bytes"},
		sourceEntry{name: "org/thirdparty/Api.kotlin_metadata", data: "dropped"},
		sourceEntry{name: "META-INF/versions/9/module-info.class", data: "dropped: multi-release module-info"},
		sourceEntry{name: "META-INF/versions/11/Multi.class", data: "kept: not a module-info"},
		sourceEntry{name: "LICENSE", data: "dropped"},
		sourceEntry{name: "META-INF/NOTICE.txt", data: "dropped"},
		sourceEntry{name: "licenses/apache.txt", data: "dropped"},
		sourceEntry{name: "META-INF/SIGNER.SF", data: "dropped"},
		sourceEntry{name: "META-INF/SIGNER.RSA", data: "dropped"},
		sourceEntry{name: "META-INF/services/org.thirdparty.Spi", data: "impl"},
		sourceEntry{name: ManifestEntryName, data: "Manifest-Version: 1.0\r\nBundle-Name: third party\r\n\r\n"},
	)
}

func TestPackMergesALibraryBeforeTheModuleOutput(t *testing.T) {
	data, duplicates := pack(t, MergeSpec{
		Output: "intellij.example.jar",
		Sources: []Source{
			{Path: librarySource(t, "library.jar"), Filter: LibraryNameFilter},
			{Path: moduleSource(t, "module.jar"), Filter: ModuleOutputNameFilter},
		},
	})
	if len(duplicates) != 0 {
		t.Errorf("these two sources share no entry name, got duplicates %v", duplicates)
	}
	// Library entries first, in the library's own order, and no manifest at all: a merged jar's surviving manifest would
	// describe one of its sources.
	if got, want := strings.Join(entryNames(t, data), ","),
		"org/thirdparty/Api.class,META-INF/versions/11/Multi.class,META-INF/services/org.thirdparty.Spi,"+
			"com/example/Service.class,com/example/nested/Inner.class,messages/Bundle.properties,__index__"; got != want {
		t.Errorf("entries are %q, want %q", got, want)
	}
	if got, want := digest(data), goldenLibraryAndModule; got != want {
		t.Errorf("packed bytes hash to %s, want %s", got, want)
	}
}

func TestPackResolvesADuplicateToTheFirstSource(t *testing.T) {
	first := writeZipJar(t, "first.jar",
		sourceEntry{name: "META-INF/services/org.Spi", data: "from the first library"},
		sourceEntry{name: "org/first/A.class", data: "a"},
	)
	second := writeZipJar(t, "second.jar",
		sourceEntry{name: "META-INF/services/org.Spi", data: "from the second library"},
		sourceEntry{name: "org/second/B.class", data: "b"},
	)
	data, duplicates := pack(t, MergeSpec{
		Output: "intellij.example.jar",
		Sources: []Source{
			{Path: first, Filter: LibraryNameFilter},
			{Path: second, Filter: LibraryNameFilter},
		},
	})
	if got, want := strings.Join(duplicates, ","), "META-INF/services/org.Spi"; got != want {
		t.Errorf("duplicates are %q, want %q", got, want)
	}
	if got, want := readEntry(t, data, "META-INF/services/org.Spi"), "from the first library"; got != want {
		t.Errorf("the surviving copy is %q, want %q", got, want)
	}
	if got, want := digest(data), goldenFirstSourceWins; got != want {
		t.Errorf("packed bytes hash to %s, want %s", got, want)
	}
}

func TestPackRewritesBootClassPathToNameTheJarItEndsUpIn(t *testing.T) {
	source := writeZipJar(t, "coverage.jar",
		sourceEntry{name: "com/coverage/Agent.class", data: "agent"},
		sourceEntry{name: ManifestEntryName, data: "Manifest-Version: 1.0\r\nBoot-Class-Path: intellij.coverage.jar\r\n\r\n"},
	)
	data, _ := pack(t, MergeSpec{
		Output:               "intellij.platform.coverage.jar",
		Sources:              []Source{{Path: source, Filter: ModuleOutputNameFilter}},
		RewriteBootClassPath: true,
	})
	manifest := readEntry(t, data, ManifestEntryName)
	if want := "Boot-Class-Path: intellij.platform.coverage.jar"; !strings.Contains(manifest, want) {
		t.Errorf("manifest is %q, want it to carry %q", manifest, want)
	}
	if got, want := digest(data), goldenRewriteBootClassPath; got != want {
		t.Errorf("packed bytes hash to %s, want %s", got, want)
	}
}

// A jar whose data begins after a *local* extra field that the central directory does not report. Every other case here
// would pass with the central length used by mistake; this one reads the wrong bytes, and --verify-crc catches it.
func TestPackReadsTheLocalExtraFieldRatherThanTheCentralOne(t *testing.T) {
	source := writeRawJar(t, "asymmetric.jar",
		rawEntry{
			name:         "org/example/Wide.class",
			data:         "the real bytes",
			localExtra:   []byte{0x55, 0x54, 5, 0, 1, 1, 2, 3, 4},
			centralExtra: []byte{0x55, 0x54, 1, 0, 1},
		},
		rawEntry{name: "org/example/Plain.class", data: "plain bytes"},
	)
	data, _ := pack(t, MergeSpec{
		Output:  "intellij.example.jar",
		Sources: []Source{{Path: source, Filter: ModuleOutputNameFilter}},
	})
	if got, want := readEntry(t, data, "org/example/Wide.class"), "the real bytes"; got != want {
		t.Errorf("entry data is %q, want %q", got, want)
	}
	if got, want := digest(data), goldenAsymmetricExtra; got != want {
		t.Errorf("packed bytes hash to %s, want %s", got, want)
	}
}

func TestPackRefusesARecipeWithNoSources(t *testing.T) {
	spec := MergeSpec{Output: "intellij.example.jar"}
	if _, err := spec.Pack(); err == nil {
		t.Error("packing a recipe with no source succeeded")
	}
}

func TestPackWritesNoIndexPointerForAnEmptyResult(t *testing.T) {
	// Every entry of this source is dropped, so there is no index to point at - and the comment is written anyway.
	source := writeZipJar(t, "empty.jar", sourceEntry{name: "icon-robots.txt", data: "dropped"})
	data, _ := pack(t, MergeSpec{
		Output:  "intellij.example.jar",
		Sources: []Source{{Path: source, Filter: ModuleOutputNameFilter}},
	})
	if got := indexPointer(t, data); got != -1 {
		t.Errorf("index pointer is %d, want -1", got)
	}
	if got := entryNames(t, data); len(got) != 0 {
		t.Errorf("entries are %v, want none", got)
	}
}
