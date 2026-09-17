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
	requireError(t, attachMetadata(files, "catalogue.json"), "conflicting metadata")
	writeCatalogue(t, "catalogue.json", []metadataRecord{})
	requireError(t, attachMetadata(files, "catalogue.json"), "missing metadata")
	writeCatalogue(t, "catalogue.json", []metadataRecord{first})
	requireError(t, attachMetadata(nil, "catalogue.json"), "stale metadata ownership")
	first.RelativePath = "another.jar"
	writeCatalogue(t, "catalogue.json", []metadataRecord{first})
	requireError(t, attachMetadata(files, "catalogue.json"), "has no entry")
	first.RelativePath = "../shared.jar"
	writeCatalogue(t, "catalogue.json", []metadataRecord{first})
	requireError(t, attachMetadata(files, "catalogue.json"), "safe relativePath")
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
