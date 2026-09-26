package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

func writeMetadataCatalogue(t *testing.T, sources []string) string {
	t.Helper()
	records := []metadataRecord{}
	for _, source := range sources {
		metadata := source + ".metadata.json"
		entry, err := filemetadata.Inspect(source, filepath.Base(source))
		if err != nil {
			t.Fatal(err)
		}
		if err := filemetadata.Write(metadata, []filemetadata.Entry{entry}); err != nil {
			t.Fatal(err)
		}
		records = append(records, metadataRecord{Source: source, Metadata: metadata, RelativePath: entry.RelativePath})
	}
	writeCatalogue(t, "metadata-catalogue.json", records)
	return "metadata-catalogue.json"
}

func writeCatalogue(t *testing.T, destination string, records []metadataRecord) {
	t.Helper()
	data, err := json.Marshal(records)
	if err != nil {
		t.Fatal(err)
	}
	writeTestFile(t, destination, data)
}

func TestPackedCollectorDoesNotReadOrStatPayload(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "payload/shared.jar", "packed bytes")
	catalogue := writeMetadataCatalogue(t, []string{"payload/shared.jar"})
	if err := os.Remove("payload/shared.jar"); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("shared.jar", "payload/shared.jar"); err != nil {
		t.Fatal(err)
	}
	writeJarRecords(t, "jars.json", "payload/shared.jar")
	var output, errors bytes.Buffer
	args := baseArgs("--jars-file=jars.json")
	if code := run(append(args, "--metadata-catalogue="+catalogue, "--trace-file=trace.json"), &output, &errors); code != 0 {
		t.Fatalf("collector accessed an unreadable payload: exit %d, %s", code, &errors)
	}
	trace := readTrace(t, "trace.json")
	for _, activity := range trace.Data[0].Spans[1:] {
		if activity.tag("byteCount") != "0" {
			t.Fatalf("payload bytes were read: %#v", activity)
		}
	}
}

func TestMetadataCatalogueRejectsConflictsAndStaleOwnership(t *testing.T) {
	t.Chdir(t.TempDir())
	entry := filemetadata.Entry{RelativePath: "shared.jar", Type: "file", Hash: 42, Size: 11, Mode: 0644}
	if err := filemetadata.Write("one.json", []filemetadata.Entry{entry}); err != nil {
		t.Fatal(err)
	}
	other := entry
	other.Hash++
	if err := filemetadata.Write("two.json", []filemetadata.Entry{other}); err != nil {
		t.Fatal(err)
	}
	first := metadataRecord{Source: "missing/shared.jar", Metadata: "one.json", RelativePath: "shared.jar"}
	second := first
	second.Source, second.Metadata = "missing/./shared.jar", "two.json"
	files := []sourcedFile{{Source: first.Source, RelativePath: "lib/shared.jar"}}
	writeCatalogue(t, "catalogue.json", []metadataRecord{first, second})
	requireError(t, attachError(files, "catalogue.json"), "conflicting metadata")
	writeCatalogue(t, "catalogue.json", []metadataRecord{})
	requireError(t, attachError(files, "catalogue.json"), "missing metadata")
	writeCatalogue(t, "catalogue.json", []metadataRecord{first})
	requireError(t, attachError(nil, "catalogue.json"), "stale metadata ownership")
	first.RelativePath = "another.jar"
	writeCatalogue(t, "catalogue.json", []metadataRecord{first})
	requireError(t, attachError(files, "catalogue.json"), "has no entry")
	first.RelativePath = "../shared.jar"
	writeCatalogue(t, "catalogue.json", []metadataRecord{first})
	requireError(t, attachError(files, "catalogue.json"), "safe relativePath")
}

func attachError(files []sourcedFile, catalogue string) error {
	_, err := attachMetadata(files, catalogue)
	return err
}

// writeNativeTree writes a native tree the way the packer does, inventories it under the key prefix `native` beside a
// jar entry, and returns the inventory. The tree is removed afterwards when keep is false, because the collector must
// place its files from the inventory alone.
func writeNativeTree(t *testing.T, tree string, keep bool, files map[string]uint32) []filemetadata.Entry {
	t.Helper()
	if err := os.MkdirAll(tree, 0o755); err != nil {
		t.Fatal(err)
	}
	for name, mode := range files {
		target := filepath.Join(tree, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(target, []byte("native "+name), os.FileMode(mode)); err != nil {
			t.Fatal(err)
		}
		if err := os.Chmod(target, os.FileMode(mode)); err != nil {
			t.Fatal(err)
		}
	}
	root, err := filemetadata.Inspect(tree, "native")
	if err != nil {
		t.Fatal(err)
	}
	entries, err := filemetadata.Inventory(tree)
	if err != nil {
		t.Fatal(err)
	}
	inventory := []filemetadata.Entry{root}
	for _, entry := range entries {
		entry.RelativePath = "native/" + entry.RelativePath
		inventory = append(inventory, entry)
	}
	if !keep {
		if err := os.RemoveAll(tree); err != nil {
			t.Fatal(err)
		}
	}
	return inventory
}

func TestTreeRecordsExpandToTheInventoryFiles(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "payload/intellij.libraries.pty4j.jar", "packed bytes")
	jar, err := filemetadata.Inspect("payload/intellij.libraries.pty4j.jar", "intellij.libraries.pty4j.jar")
	if err != nil {
		t.Fatal(err)
	}
	tree := writeNativeTree(t, "payload/native", false, map[string]uint32{
		"darwin/libpty.dylib":            0o644,
		"darwin/pty4j-unix-spawn-helper": 0o755,
	})
	if err := filemetadata.Write("pty4j.metadata.json", append([]filemetadata.Entry{jar}, tree...)); err != nil {
		t.Fatal(err)
	}
	if err := os.Remove("payload/intellij.libraries.pty4j.jar"); err != nil {
		t.Fatal(err)
	}
	writeCatalogue(t, "catalogue.json", []metadataRecord{
		{Source: "payload/intellij.libraries.pty4j.jar", Metadata: "pty4j.metadata.json", RelativePath: "intellij.libraries.pty4j.jar"},
		{Source: "payload/native", Metadata: "pty4j.metadata.json", RelativePath: "native", Tree: true},
	})
	writeText(t, "jars.json", `[
		{"source":"payload/intellij.libraries.pty4j.jar","relativePath":"intellij.libraries.pty4j.jar"},
		{"source":"payload/native","relativePath":"pty4j","tree":true}
	]`)
	var output, errors bytes.Buffer
	args := append(baseArgs("--jars-file=jars.json"), "--metadata-catalogue=catalogue.json", "--trace-file=trace.json")
	if code := run(args, &output, &errors); code != 0 {
		t.Fatalf("exit = %d: %s", code, &errors)
	}
	data, err := os.ReadFile("component.json")
	if err != nil {
		t.Fatal(err)
	}
	// The shape the Kotlin fragment writes for the same files: a component file per native, the executable bit where
	// the tree has it, no directory, and no mode when it is the conventional one.
	var manifest struct {
		Entries []map[string]json.RawMessage `json:"entries"`
	}
	if err := json.Unmarshal(data, &manifest); err != nil {
		t.Fatal(err)
	}
	byPath := make(map[string]map[string]json.RawMessage)
	for _, entry := range manifest.Entries {
		byPath[strings.Trim(string(entry["relativePath"]), `"`)] = entry
	}
	if len(byPath) != 3 {
		t.Fatalf("manifest = %s", data)
	}
	library := byPath["lib/pty4j/darwin/libpty.dylib"]
	helper := byPath["lib/pty4j/darwin/pty4j-unix-spawn-helper"]
	if library == nil || helper == nil || byPath["lib/intellij.libraries.pty4j.jar"] == nil {
		t.Fatalf("manifest = %s", data)
	}
	for name, entry := range map[string]map[string]json.RawMessage{"library": library, "helper": helper} {
		if string(entry["type"]) != `"component-file"` || entry["hash"] == nil || entry["mode"] != nil {
			t.Errorf("%s = %s", name, entry)
		}
	}
	if string(library["source"]) != `"payload/native/darwin/libpty.dylib"` || library["executable"] != nil {
		t.Errorf("library = %s", library)
	}
	if string(helper["source"]) != `"payload/native/darwin/pty4j-unix-spawn-helper"` || string(helper["executable"]) != "true" {
		t.Errorf("helper = %s", helper)
	}
	var hashes struct {
		Entries []componentEntry `json:"entries"`
	}
	if err := json.Unmarshal(data, &hashes); err != nil {
		t.Fatal(err)
	}
	for _, entry := range hashes.Entries {
		for _, expected := range tree {
			if "lib/pty4j/"+strings.TrimPrefix(expected.RelativePath, "native/") == entry.RelativePath && expected.Hash != entry.Hash {
				t.Errorf("%s hashes to %d, the inventory says %d", entry.RelativePath, entry.Hash, expected.Hash)
			}
		}
	}
	// And nothing of the payload was read: the tree is gone, and the counters say so.
	trace := readTrace(t, "trace.json")
	for _, activity := range trace.Data[0].Spans[1:] {
		if activity.tag("byteCount") != "0" {
			t.Fatalf("payload bytes were read: %#v", activity)
		}
	}
	if !strings.Contains(output.String(), "named 3 packed jars") {
		t.Errorf("stdout = %s", &output)
	}
}

func TestTreeRecordsPlaceUnconventionalModes(t *testing.T) {
	t.Chdir(t.TempDir())
	tree := writeNativeTree(t, "payload/native", false, map[string]uint32{"amd64/libasyncProfiler.so": 0o600})
	if err := filemetadata.Write("metadata.json", tree); err != nil {
		t.Fatal(err)
	}
	writeCatalogue(t, "catalogue.json", []metadataRecord{{Source: "payload/native", Metadata: "metadata.json", RelativePath: "native", Tree: true}})
	files, err := attachMetadata([]sourcedFile{{Source: "payload/native", RelativePath: "lib/async-profiler", tree: true}}, "catalogue.json")
	if err != nil {
		t.Fatal(err)
	}
	if len(files) != 1 || files[0].RelativePath != "lib/async-profiler/amd64/libasyncProfiler.so" || files[0].mode == nil || *files[0].mode != 0o600 ||
		files[0].metadata == nil || files[0].metadata.RelativePath != files[0].RelativePath || files[0].tree {
		t.Fatalf("files = %#v", files)
	}
	entries, err := inventory(files, nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || entries[0].Mode == nil || *entries[0].Mode != 0o600 {
		t.Fatalf("entries = %#v", entries)
	}
}

func TestAnEmptyTreeContributesNothing(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "payload/intellij.libraries.jna.jar", "packed bytes")
	jar, err := filemetadata.Inspect("payload/intellij.libraries.jna.jar", "intellij.libraries.jna.jar")
	if err != nil {
		t.Fatal(err)
	}
	tree := writeNativeTree(t, "payload/native", false, nil)
	if err := filemetadata.Write("jna.metadata.json", append([]filemetadata.Entry{jar}, tree...)); err != nil {
		t.Fatal(err)
	}
	writeCatalogue(t, "catalogue.json", []metadataRecord{
		{Source: "payload/intellij.libraries.jna.jar", Metadata: "jna.metadata.json", RelativePath: "intellij.libraries.jna.jar"},
		{Source: "payload/native", Metadata: "jna.metadata.json", RelativePath: "native", Tree: true},
	})
	files := []sourcedFile{
		{Source: "payload/intellij.libraries.jna.jar", RelativePath: "lib/intellij.libraries.jna.jar"},
		{Source: "payload/native", RelativePath: "lib/jna", tree: true},
	}
	attached, err := attachMetadata(files, "catalogue.json")
	if err != nil || len(attached) != 1 || attached[0].RelativePath != "lib/intellij.libraries.jna.jar" || attached[0].metadata == nil {
		t.Fatalf("files = %#v, error = %v", attached, err)
	}
}

func TestTreeRecordsMustAgreeWithTheCatalogue(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "payload/a.jar", "packed bytes")
	jar, err := filemetadata.Inspect("payload/a.jar", "a.jar")
	if err != nil {
		t.Fatal(err)
	}
	tree := writeNativeTree(t, "payload/native", false, map[string]uint32{"aarch64/libjnidispatch.jnilib": 0o644})
	if err := filemetadata.Write("metadata.json", append([]filemetadata.Entry{jar}, tree...)); err != nil {
		t.Fatal(err)
	}
	linked := append([]filemetadata.Entry{}, tree...)
	linked = append(linked, filemetadata.Entry{RelativePath: "native/aarch64/link", Type: "symlink", SymlinkTarget: "libjnidispatch.jnilib", Hash: filemetadata.SymlinkHash("libjnidispatch.jnilib")})
	if err := filemetadata.Write("linked.json", linked); err != nil {
		t.Fatal(err)
	}
	jarRecord := metadataRecord{Source: "payload/a.jar", Metadata: "metadata.json", RelativePath: "a.jar"}
	treeRecord := metadataRecord{Source: "payload/native", Metadata: "metadata.json", RelativePath: "native", Tree: true}
	jarFile := sourcedFile{Source: "payload/a.jar", RelativePath: "lib/a.jar"}
	treeFile := sourcedFile{Source: "payload/native", RelativePath: "lib/jna", tree: true}
	for name, test := range map[string]struct {
		records []metadataRecord
		files   []sourcedFile
		want    string
	}{
		"a tree record with file metadata": {
			records: []metadataRecord{jarRecord, {Source: "payload/native", Metadata: "metadata.json", RelativePath: "native"}},
			files:   []sourcedFile{jarFile, treeFile},
			want:    "tree record payload/native has file metadata",
		},
		"a file record with tree metadata": {
			records: []metadataRecord{jarRecord, treeRecord},
			files:   []sourcedFile{jarFile, {Source: "payload/native", RelativePath: "lib/native"}},
			want:    "file record payload/native has tree metadata",
		},
		"a tree record without metadata": {
			records: []metadataRecord{jarRecord},
			files:   []sourcedFile{jarFile, treeFile},
			want:    "missing metadata for tree payload/native",
		},
		"a tree catalogue record nothing collects": {
			records: []metadataRecord{jarRecord, treeRecord},
			files:   []sourcedFile{jarFile},
			want:    "stale metadata ownership for tree",
		},
		"a tree key that is a file": {
			records: []metadataRecord{jarRecord, {Source: "payload/native", Metadata: "metadata.json", RelativePath: "a.jar", Tree: true}},
			files:   []sourcedFile{jarFile, treeFile},
			want:    "no directory entry for tree a.jar",
		},
		"a tree key the inventory lacks": {
			records: []metadataRecord{jarRecord, {Source: "payload/native", Metadata: "metadata.json", RelativePath: "other", Tree: true}},
			files:   []sourcedFile{jarFile, treeFile},
			want:    "no directory entry for tree other",
		},
		"a source recorded as both": {
			records: []metadataRecord{jarRecord, treeRecord, {Source: "payload/native", Metadata: "metadata.json", RelativePath: "a.jar"}},
			files:   []sourcedFile{jarFile, treeFile},
			want:    "conflicting metadata for source payload/native",
		},
		"a link in the tree": {
			records: []metadataRecord{jarRecord, {Source: "payload/native", Metadata: "linked.json", RelativePath: "native", Tree: true}},
			files:   []sourcedFile{jarFile, treeFile},
			want:    "symbolic link in tree native",
		},
	} {
		t.Run(name, func(t *testing.T) {
			writeCatalogue(t, "catalogue.json", test.records)
			requireError(t, attachError(test.files, "catalogue.json"), test.want)
		})
	}
}

// A jar that lands where the tree puts a file is caught after the expansion, which is why collect validates the
// destinations last. Before the expansion the tree is one directory, and `lib/jna` beside `lib/jna/x` is legal.
func TestTreeDestinationsAreValidatedAfterExpansion(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "payload/dispatch.jar", "packed bytes")
	jar, err := filemetadata.Inspect("payload/dispatch.jar", "dispatch.jar")
	if err != nil {
		t.Fatal(err)
	}
	tree := writeNativeTree(t, "payload/native", false, map[string]uint32{"aarch64/libjnidispatch.jnilib": 0o644})
	if err := filemetadata.Write("metadata.json", append([]filemetadata.Entry{jar}, tree...)); err != nil {
		t.Fatal(err)
	}
	writeCatalogue(t, "catalogue.json", []metadataRecord{
		{Source: "payload/dispatch.jar", Metadata: "metadata.json", RelativePath: "dispatch.jar"},
		{Source: "payload/native", Metadata: "metadata.json", RelativePath: "native", Tree: true},
	})
	writeText(t, "jars.json", `[
		{"source":"payload/dispatch.jar","relativePath":"jna/aarch64/libjnidispatch.jnilib"},
		{"source":"payload/native","relativePath":"jna","tree":true}
	]`)
	var output, errors bytes.Buffer
	code := run(append(baseArgs("--jars-file=jars.json"), "--metadata-catalogue=catalogue.json"), &output, &errors)
	if code == 0 || !strings.Contains(errors.String(), "conflicting destination: lib/jna/aarch64/libjnidispatch.jnilib") {
		t.Fatalf("exit = %d, stderr = %s", code, &errors)
	}
	// The jar under the tree's directory is a conflict as well, one the tree's directory record alone would not show.
	writeText(t, "jars.json", `[
		{"source":"payload/dispatch.jar","relativePath":"jna/aarch64/libjnidispatch.jnilib/inner.jar"},
		{"source":"payload/native","relativePath":"jna","tree":true}
	]`)
	output.Reset()
	errors.Reset()
	code = run(append(baseArgs("--jars-file=jars.json"), "--metadata-catalogue=catalogue.json"), &output, &errors)
	if code == 0 || !strings.Contains(errors.String(), "contains") {
		t.Fatalf("exit = %d, stderr = %s", code, &errors)
	}
}

func TestPackedCollectorRequiresMetadata(t *testing.T) {
	t.Chdir(t.TempDir())
	writeJarRecords(t, "jars.json", "missing.jar")
	var output, errors bytes.Buffer
	code := run(baseArgs("--jars-file=jars.json"), &output, &errors)
	if code == 0 || !strings.Contains(errors.String(), "require --metadata-catalogue") {
		t.Fatalf("exit = %d, error = %s", code, &errors)
	}
}

func TestDirectoryEntriesAndExplicitLinksNeedNoPayload(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "payload/lib/a.jar", "packed bytes")
	if err := os.Symlink("a.jar", "payload/lib/link.jar"); err != nil {
		t.Fatal(err)
	}
	entries, err := filemetadata.Inventory("payload")
	if err != nil {
		t.Fatal(err)
	}
	if err := filemetadata.Write("metadata.json", entries); err != nil {
		t.Fatal(err)
	}
	if err := os.RemoveAll("payload"); err != nil {
		t.Fatal(err)
	}
	writeCatalogue(t, "catalogue.json", []metadataRecord{
		{Source: "payload/lib/a.jar", Metadata: "metadata.json", RelativePath: "lib/a.jar"},
		{Source: "payload/lib/link.jar", Metadata: "metadata.json", RelativePath: "lib/link.jar"},
	})
	writeText(t, "files.json", `[
		{"source":"payload/lib/a.jar","relativePath":"plugins/test/lib/a.jar","executable":false},
		{"source":"payload/lib/link.jar","relativePath":"plugins/test/lib/link.jar","executable":false}
	]`)
	var output, errors bytes.Buffer
	if code := run(append(baseArgs("--files-file=files.json"), "--metadata-catalogue=catalogue.json"), &output, &errors); code != 0 {
		t.Fatalf("exit = %d: %s", code, &errors)
	}
	data, err := os.ReadFile("component.json")
	if err != nil {
		t.Fatal(err)
	}
	var manifest componentManifest
	if err := json.Unmarshal(data, &manifest); err != nil {
		t.Fatal(err)
	}
	if len(manifest.Entries) != 2 || manifest.Entries[0].Source != "payload/lib/a.jar" || manifest.Entries[0].Hash != entries[1].Hash ||
		manifest.Entries[1].Source != "" || manifest.Entries[1].SymlinkTarget != "a.jar" || manifest.Entries[1].Hash != entries[2].Hash {
		t.Fatalf("manifest = %#v", manifest)
	}
}

func TestMetadataDestinationCollisions(t *testing.T) {
	for _, files := range [][]sourcedFile{
		{{RelativePath: "lib/a.jar"}, {RelativePath: "lib/a.jar"}},
		{{RelativePath: "lib/a.jar"}, {RelativePath: "lib/a.jar/b.jar"}},
		{{RelativePath: `C:/a.jar`}},
		{{RelativePath: `lib\a.jar`}},
	} {
		if err := validateDestinations(files); err == nil {
			t.Errorf("accepted conflicting or escaping destinations: %#v", files)
		}
	}
}

func TestCollectorRejectsLinksCombinedAcrossInventories(t *testing.T) {
	t.Chdir(t.TempDir())
	if err := os.Mkdir("payload", 0755); err != nil {
		t.Fatal(err)
	}
	records := []metadataRecord{}
	for name, target := range map[string]string{"a": "b/../file", "b": "a"} {
		source := filepath.Join("payload", name)
		if err := os.Symlink(target, source); err != nil {
			t.Fatal(err)
		}
		entry, err := filemetadata.Inspect(source, name)
		if err != nil {
			t.Fatal(err)
		}
		metadata := name + ".json"
		if err := filemetadata.Write(metadata, []filemetadata.Entry{entry}); err != nil {
			t.Fatal(err)
		}
		records = append(records, metadataRecord{Source: source, Metadata: metadata, RelativePath: name})
	}
	if err := os.RemoveAll("payload"); err != nil {
		t.Fatal(err)
	}
	writeCatalogue(t, "catalogue.json", records)
	writeText(t, "files.json", `[
		{"source":"payload/a","relativePath":"a","executable":false},
		{"source":"payload/b","relativePath":"b","executable":false}
	]`)
	var output, errors bytes.Buffer
	code := run(append(baseArgs("--files-file=files.json"), "--metadata-catalogue=catalogue.json"), &output, &errors)
	if code == 0 || !strings.Contains(errors.String(), "cycle") {
		t.Fatalf("collector accepted an unsafe combined graph: exit = %d, error = %s", code, &errors)
	}
	if _, err := os.Stat("component.json"); !os.IsNotExist(err) {
		t.Fatalf("a rejected graph produced a component manifest: %v", err)
	}
}
