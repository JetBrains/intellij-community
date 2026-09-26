// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"archive/zip"
	"bytes"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

func TestPatchPrecedenceAndCollision(t *testing.T) {
	patch := filepath.Join(t.TempDir(), "plugin.xml")
	if err := os.WriteFile(patch, []byte("patched"), 0o644); err != nil {
		t.Fatal(err)
	}
	module := writeZipJar(t, "main.jar", sourceEntry{name: "META-INF/plugin.xml", data: "original"})
	patched := Source{Path: patch, Name: "META-INF/plugin.xml", Patch: true}
	archive := Source{Path: module, Filter: ModuleOutputNameFilter}
	data, _ := pack(t, MergeSpec{Output: "plugin.jar", Sources: []Source{patched, archive}})
	if got := packedEntry(t, data, patched.Name); got != "patched" {
		t.Fatalf("descriptor is %q", got)
	}
	_, err := (MergeSpec{Output: filepath.Join(t.TempDir(), "bad.jar"), Sources: []Source{archive, patched}}).Pack()
	if err == nil || !strings.Contains(err.Error(), "must precede") {
		t.Fatalf("expected a patch collision, got %v", err)
	}
}

func TestEntityMergeKeepsSourceOrder(t *testing.T) {
	first := writeZipJar(t, "first.jar", sourceEntry{name: "META-INF/listOfEntities.txt", data: "  First\n"})
	second := writeZipJar(t, "second.jar", sourceEntry{name: "META-INF/listOfEntities.txt", data: "\nSecond  "})
	sources := []Source{{Path: first, Filter: LibraryNameFilter}, {Path: second, Filter: ModuleOutputNameFilter}}
	data, duplicates := pack(t, MergeSpec{Output: "entities.jar", Sources: sources, MergeEntities: true, VerifyCRC: true})
	if len(duplicates) != 0 || packedEntry(t, data, "META-INF/listOfEntities.txt") != "First\nSecond" {
		t.Fatalf("entity merge did not preserve the source order: %v", duplicates)
	}
	legacy, _ := pack(t, MergeSpec{Output: "legacy.jar", Sources: sources})
	if packedEntry(t, legacy, "META-INF/listOfEntities.txt") != "  First\n" {
		t.Fatal("changed the existing recipe behavior")
	}
}

func packedEntry(t *testing.T, data []byte, name string) string {
	t.Helper()
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range reader.File {
		if entry.Name != name {
			continue
		}
		stream, err := entry.Open()
		if err != nil {
			t.Fatal(err)
		}
		content, err := io.ReadAll(stream)
		stream.Close()
		if err != nil {
			t.Fatal(err)
		}
		return string(content)
	}
	t.Fatalf("missing entry %s", name)
	return ""
}

func TestManifestPolicyBelongsToTheSource(t *testing.T) {
	first := writeZipJar(t, "unrelated.jar", sourceEntry{name: ManifestEntryName, data: "Boot-Class-Path: unrelated.jar\r\n"})
	agent := writeZipJar(t, "agent.jar", sourceEntry{name: ManifestEntryName, data: "Boot-Class-Path: intellij-coverage-agent-1.2.3.jar\r\nOther: unchanged\r\n"})
	for _, test := range []struct {
		mode ManifestMode
		want string
	}{
		{ManifestKeep, "Boot-Class-Path: intellij-coverage-agent-1.2.3.jar\r\nOther: unchanged\r\n"},
		{ManifestRewriteBootClassPath, "Boot-Class-Path: renamed.jar\r\nOther: unchanged\r\n"},
		{ManifestCoverageAgent, "Boot-Class-Path: intellij.platform.coverage.agent.jar\r\nOther: unchanged\r\n"},
	} {
		t.Run(string(test.mode), func(t *testing.T) {
			filter := ModuleOutputNameFilter
			if test.mode == ManifestCoverageAgent {
				filter = func(string) bool { return false }
			}
			data, _ := pack(t, MergeSpec{Output: "renamed.jar", KeepManifest: true, Sources: []Source{
				{Path: first, Filter: ModuleOutputNameFilter, Manifest: ManifestDrop},
				{Path: agent, Filter: filter, Manifest: test.mode},
			}})
			if got := packedEntry(t, data, ManifestEntryName); got != test.want {
				t.Fatalf("manifest is %q, want %q", got, test.want)
			}
		})
	}
}

func TestCoverageRewriteUsesTheProductionPattern(t *testing.T) {
	input := []byte("Boot-Class-Path: custom-agent.jar\r\nBoot-Class-Path: intellij-coverage-agent-1.jar\r\n")
	want := "Boot-Class-Path: custom-agent.jar\r\nBoot-Class-Path: intellij.platform.coverage.agent.jar\r\n"
	if got := string(rewriteSourceManifest(input, ManifestCoverageAgent, "different.jar")); got != want {
		t.Fatalf("manifest is %q", got)
	}
}

func TestNativeChangesRetainOriginalSourcePositions(t *testing.T) {
	first := writeZipJar(t, "first.jar",
		sourceEntry{name: "before.class", data: "before"},
		sourceEntry{name: "native/lib.so", data: "unsigned"},
		sourceEntry{name: "native/extract.so", data: "extract"},
		sourceEntry{name: "after.class", data: "after"},
	)
	second := writeZipJar(t, "second.jar", sourceEntry{name: "native/extract.so", data: "must not reappear"})
	replacement := filepath.Join(t.TempDir(), "signed")
	if err := os.WriteFile(replacement, []byte("signed"), 0o644); err != nil {
		t.Fatal(err)
	}
	spec := MergeSpec{Output: "plugin.jar", ValidateEntryNames: true, Sources: []Source{
		{Path: first, Filter: ModuleOutputNameFilter, EntryOverrides: map[string]EntryOverride{
			"native/lib.so": {Path: replacement}, "native/extract.so": {Reserve: true},
		}},
		{Path: second, Filter: ModuleOutputNameFilter},
	}}
	data, duplicates := pack(t, spec)
	if !slices.Equal(entryNames(t, data), []string{"before.class", "native/lib.so", "after.class", "__index__"}) ||
		packedEntry(t, data, "native/lib.so") != "signed" || !slices.Equal(duplicates, []string{"native/extract.so"}) {
		t.Fatalf("native changes moved: %v, duplicates=%v", entryNames(t, data), duplicates)
	}
	var expected bytes.Buffer
	writer := NewWriter(&expected)
	for _, entry := range []sourceEntry{{name: "before.class", data: "before"}, {name: "native/lib.so", data: "signed"}, {name: "after.class", data: "after"}} {
		if err := writer.Add(entry.name, []byte(entry.data), crc32Of([]byte(entry.data)), true); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(data, expected.Bytes()) {
		t.Fatal("native replacements changed the expected jar bytes")
	}
}

func TestPreparedReservationsAndEntitiesFollowSourceOrder(t *testing.T) {
	root := t.TempDir()
	file := filepath.Join(root, "prepared")
	if err := os.WriteFile(file, []byte("\u001c Prepared \u001f"), 0o644); err != nil {
		t.Fatal(err)
	}
	archive := writeZipJar(t, "module.jar", sourceEntry{name: "reserved.txt", data: "must not appear"}, sourceEntry{name: "META-INF/listOfEntities.txt", data: " Archive "})
	data, _ := pack(t, MergeSpec{Output: "entities.jar", MergeEntities: true, Sources: []Source{
		{Name: "reserved.txt", Reserve: true},
		{Path: file, Name: "META-INF/listOfEntities.txt"},
		{Path: archive, Filter: ModuleOutputNameFilter},
	}})
	if packedEntry(t, data, "META-INF/listOfEntities.txt") != "Prepared\nArchive" || slices.Contains(entryNames(t, data), "reserved.txt") {
		t.Fatalf("prepared source order changed: %v", entryNames(t, data))
	}
	if got := trimEntityList([]byte("\u0085keep\u0085")); got != "\u0085keep\u0085" {
		t.Fatalf("trim differs from Kotlin whitespace rules: %q", got)
	}
}

func TestMergeRejectsUnsafeOrStaleSourceOperations(t *testing.T) {
	archive := writeZipJar(t, "module.jar", sourceEntry{name: "present.so", data: "native"}, sourceEntry{name: "icon-robots.txt", data: "excluded"})
	for _, source := range []Source{
		{Path: archive},
		{Path: archive, Filter: ModuleOutputNameFilter, Manifest: "unknown"},
		{Path: archive, Name: "entry", Reserve: true},
		{Name: "entry", Reserve: true, Patch: true},
		{Path: archive, Name: "../escape"},
		{Path: archive, Filter: ModuleOutputNameFilter, EntryOverrides: map[string]EntryOverride{"missing.so": {Reserve: true}}},
		{Path: archive, Filter: ModuleOutputNameFilter, EntryOverrides: map[string]EntryOverride{"icon-robots.txt": {Reserve: true}}},
		{Path: archive, Filter: ModuleOutputNameFilter, EntryOverrides: map[string]EntryOverride{"present.so": {}}},
		{Path: archive, Filter: ModuleOutputNameFilter, EntryOverrides: map[string]EntryOverride{"present.so": {Path: archive, Reserve: true}}},
	} {
		spec := MergeSpec{Output: filepath.Join(t.TempDir(), "invalid.jar"), ValidateEntryNames: true, Sources: []Source{source}}
		if _, err := spec.Pack(); err == nil {
			t.Fatalf("accepted invalid source: %+v", source)
		}
	}
}

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
		Output:  "intellij.platform.coverage.jar",
		Sources: []Source{{Path: source, Filter: ModuleOutputNameFilter, Manifest: ManifestRewriteBootClassPath}},
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

func TestResidualPackingRejectsNativeEntries(t *testing.T) {
	for _, name := range []string{"lib/native.so", "lib/native.dylib", "bin/native.dll", "bin/native.exe", "bin/pty4j-unix-spawn-helper", "lib/icudtl.dat"} {
		t.Run(name, func(t *testing.T) {
			source := writeZipJar(t, "native.jar", sourceEntry{name: name, data: "native"})
			spec := MergeSpec{
				Output:              filepath.Join(t.TempDir(), "result.jar"),
				Sources:             []Source{{Path: source, Filter: ModuleOutputNameFilter}},
				RejectNativeEntries: true,
			}
			if _, err := spec.Pack(); err == nil || !strings.Contains(err.Error(), "Kotlin packer") {
				t.Fatalf("expected a native holdout, got %v", err)
			}
		})
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

// fileSource is what an action hands the packer when the bytes of one entry live in a file of their own rather than in
// a jar. The dev distribution's produced plugin descriptor is the case.
func fileSource(t *testing.T, name string, data string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), name)
	if err := os.WriteFile(path, []byte(data), 0o644); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestPackTakesASingleFileAsOneEntryAtTheStatedName(t *testing.T) {
	descriptor := "<idea-plugin><id>example</id></idea-plugin>"
	data, duplicates := pack(t, MergeSpec{
		Output: "intellij.example.jar",
		Sources: []Source{
			{Path: fileSource(t, "produced.xml", descriptor), Name: "META-INF/plugin.xml"},
			{Path: moduleSource(t, "module.jar"), Filter: ModuleOutputNameFilter},
		},
	})
	if len(duplicates) != 0 {
		t.Errorf("the file's name is in no other source, got duplicates %v", duplicates)
	}
	// The file keeps the position its source has, because that order is the precedence Merge applies. The Kotlin
	// `JarPackager` adds a patched entry ahead of the module output, so a file source must be able to lead.
	if got, want := strings.Join(entryNames(t, data), ","),
		"META-INF/plugin.xml,com/example/Service.class,com/example/nested/Inner.class,messages/Bundle.properties,__index__"; got != want {
		t.Errorf("entries are %q, want %q", got, want)
	}
	if got := readEntry(t, data, "META-INF/plugin.xml"); got != descriptor {
		t.Errorf("the entry holds %q, want the file's bytes %q", got, descriptor)
	}
}

func TestPackReportsAFileSourceWhoseNameAnEarlierSourceTook(t *testing.T) {
	data, duplicates := pack(t, MergeSpec{
		Output: "intellij.example.jar",
		Sources: []Source{
			{Path: moduleSource(t, "module.jar"), Filter: ModuleOutputNameFilter},
			{Path: fileSource(t, "bundle.properties", "key=overwritten"), Name: "messages/Bundle.properties"},
		},
	})
	if got, want := strings.Join(duplicates, ","), "messages/Bundle.properties"; got != want {
		t.Errorf("duplicates are %q, want %q", got, want)
	}
	// First source wins, for a file source exactly as for a jar one.
	if got, want := readEntry(t, data, "messages/Bundle.properties"), "key=value"; got != want {
		t.Errorf("the entry holds %q, want the first source's %q", got, want)
	}
}

func TestPackDropsAFileSourceThatWouldSmuggleAManifest(t *testing.T) {
	data, _ := pack(t, MergeSpec{
		Output:  "intellij.example.jar",
		Sources: []Source{{Path: fileSource(t, "MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"), Name: ManifestEntryName}},
	})
	if names := strings.Join(entryNames(t, data), ","); strings.Contains(names, ManifestEntryName) {
		t.Errorf("entries are %q, want no manifest: keepManifest is false", names)
	}
}
