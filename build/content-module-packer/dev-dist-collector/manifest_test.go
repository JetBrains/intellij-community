package main

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/span"
)

var referenceSizes = []int{0, 1, 3, 240, 241, 262143, 262144, 262145, 524288, 524301}

func referenceBytes(size int) []byte {
	data := make([]byte, size)
	for index := range data {
		data[index] = byte(index*31 + 7)
	}
	return data
}

func TestPlatformManifestKotlinParity(t *testing.T) {
	golden, err := os.ReadFile("testdata/platform.json")
	if err != nil {
		t.Fatal(err)
	}
	t.Chdir(t.TempDir())
	var jars []string
	for _, size := range referenceSizes {
		name := fmt.Sprintf("inputs/vector-%d.jar", size)
		jars = append(jars, writeTestFile(t, name, referenceBytes(size)))
	}
	writeJarRecords(t, "jars.json", jars...)
	args := baseArgs("--jars-file=jars.json")
	args[1] = "--kind=platform"
	var output, errors bytes.Buffer
	catalogue := writeMetadataCatalogue(t, jars)
	if code := run(append(args, "--trace-file=trace.json", "--metadata-catalogue="+catalogue), &output, &errors); code != 0 {
		t.Fatalf("exit = %d: %s", code, &errors)
	}
	actual, err := os.ReadFile("component.json")
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(actual, bytes.TrimSuffix(golden, []byte{'\n'})) {
		t.Fatalf("manifest differs from Kotlin v9:\ngot: %s\nwant: %s", actual, golden)
	}
	trace := readTrace(t, "trace.json")
	activities := trace.Data[0].Spans
	if len(activities) != 3 || activities[0].OperationName != "collect packed jars" || activities[0].tag("kind") != "platform" {
		t.Fatalf("trace = %#v", trace)
	}
	for _, activity := range activities[1:] {
		if len(activity.References) != 1 || activity.References[0].SpanID != activities[0].SpanID {
			t.Fatalf("span is not under the action root: %#v", activity)
		}
	}
	if activities[1].OperationName != "collect platform jars" || activities[1].tag("jarCount") != "10" || activities[1].tag("byteCount") != "0" ||
		activities[2].OperationName != "inventory dev build component" || activities[2].tag("fileCount") != "10" ||
		activities[2].tag("hashedFileCount") != "0" || activities[2].tag("byteCount") != "0" {
		t.Fatalf("span counters differ from Kotlin: %#v", activities)
	}
}

func TestInventorySourceIdentityAndMode(t *testing.T) {
	t.Chdir(t.TempDir())
	writeTestFile(t, "inputs/shared.jar", referenceBytes(3))
	if err := os.Chmod("inputs/shared.jar", 0o755); err != nil {
		t.Fatal(err)
	}
	files := []sourcedFile{
		{Source: "inputs/shared.jar", RelativePath: "lib/z.jar"},
		{Source: "inputs/./shared.jar", RelativePath: "bin/ijent", Executable: true},
	}
	tracer := span.NewTracer("collect files")
	entries, err := inventory(files, tracer, nil)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 2 || entries[0].RelativePath != "bin/ijent" || entries[0].Source != "inputs/./shared.jar" ||
		!entries[0].Executable || entries[1].Executable || entries[0].Hash != -737883702129266468 || entries[0].Hash != entries[1].Hash {
		t.Fatalf("entries = %#v", entries)
	}
	if err := tracer.WriteFile("trace.json"); err != nil {
		t.Fatal(err)
	}
	activity := readTrace(t, "trace.json").Data[0].Spans[0]
	if activity.tag("fileCount") != "2" || activity.tag("hashedFileCount") != "1" || activity.tag("byteCount") != "3" {
		t.Fatalf("inventory counters = %#v", activity)
	}
}

func TestInventoryEmitsLogicalComponentModes(t *testing.T) {
	metadata := []filemetadata.Entry{
		{RelativePath: "source-data", Type: "file", Hash: 1, Size: 1, Mode: 0o444},
		{RelativePath: "source-tool", Type: "file", Hash: 2, Size: 1, Mode: 0o555, Executable: true},
		{RelativePath: "source-special", Type: "file", Hash: 3, Size: 1, Mode: 0o550, Executable: true},
		{RelativePath: "source-directory", Type: "directory", Mode: 0o555},
	}
	files := make([]sourcedFile, 0, len(metadata))
	for index := range metadata {
		entry := &metadata[index]
		files = append(files, sourcedFile{
			Source:       entry.RelativePath,
			RelativePath: "plugins/demo/" + entry.RelativePath,
			metadata:     entry,
			mode:         &entry.Mode,
		})
	}
	entries, err := inventory(files, nil, nil)
	if err != nil {
		t.Fatal(err)
	}
	byPath := make(map[string]componentEntry, len(entries))
	for _, entry := range entries {
		byPath[entry.RelativePath] = entry
	}
	for _, name := range []string{"source-data", "source-tool"} {
		entry := byPath["plugins/demo/"+name]
		if entry.Mode != nil {
			t.Fatalf("%s mode = %04o, want the conventional mode omitted", name, *entry.Mode)
		}
	}
	special := byPath["plugins/demo/source-special"]
	if special.Mode == nil || *special.Mode != 0o550 {
		t.Fatalf("special mode = %v, want 0550", special.Mode)
	}
	directory := byPath["plugins/demo/source-directory"]
	if directory.Mode == nil || *directory.Mode != 0o755 {
		t.Fatalf("directory mode = %v, want 0755", directory.Mode)
	}
}

func TestInventoryFollowsStagingLinks(t *testing.T) {
	t.Chdir(t.TempDir())
	writeTestFile(t, "source.jar", referenceBytes(3))
	if err := os.Symlink("source.jar", "staged.jar"); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	entries, err := inventory([]sourcedFile{{Source: "staged.jar", RelativePath: "lib/staged.jar"}}, nil, nil)
	if err != nil || entries[0].Hash != -737883702129266468 || entries[0].Source != "staged.jar" {
		t.Fatalf("entries = %#v, error = %v", entries, err)
	}
}

func TestInventoryRejectsNonFiles(t *testing.T) {
	root := t.TempDir()
	for _, source := range []string{root, filepath.Join(root, "missing")} {
		_, err := inventory([]sourcedFile{{Source: source, RelativePath: "lib/source.jar"}}, nil, nil)
		requireError(t, err, "not a regular file")
	}
}

func TestManifestOrderingAndEscaping(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "inputs/a&b.jar", "bytes")
	opts := options{manifest: "component.json", kind: "files", platformPrefix: "idea<test>", os: "linux", arch: "x64"}
	files := []sourcedFile{
		{Source: "inputs/a&b.jar", RelativePath: "lib/\ue000.jar"},
		{Source: "inputs/a&b.jar", RelativePath: "lib/\U0001f600.jar"},
	}
	if err := writeManifest(opts, files, nil, nil); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(opts.manifest)
	if err != nil {
		t.Fatal(err)
	}
	text := string(data)
	if !strings.Contains(text, "inputs/a&b.jar") || !strings.Contains(text, "idea<test>") || strings.Index(text, "\U0001f600") >= strings.Index(text, "\ue000") {
		t.Fatalf("wrong escaping or order: %s", text)
	}
}
