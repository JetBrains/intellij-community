package main

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

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

func TestKotlinManifestParity(t *testing.T) {
	for _, mode := range []string{"platform", "plugin"} {
		t.Run(mode, func(t *testing.T) {
			golden, err := os.ReadFile("testdata/" + mode + ".json")
			if err != nil {
				t.Fatal(err)
			}
			t.Chdir(t.TempDir())
			var jars []string
			for _, size := range referenceSizes {
				name := fmt.Sprintf("inputs/vector-%d.jar", size)
				jars = append(jars, writeTestFile(t, name, referenceBytes(size)))
			}
			writeText(t, "jars.list", strings.Join(jars, "\n"))
			writeText(t, "plugins.tsv", "plugin.two\tmodules/shared.jar\tinputs/vector-262145.jar\n"+
				"plugin.one\tcustom.jar\tinputs/vector-3.jar\nplugin.one\tmodules/shared.jar\tinputs/vector-262145.jar\n")
			writeText(t, "placements.tsv", "plugin.one\tmodules/shared.jar\tplugins/one/lib/modules/shared.jar\n"+
				"plugin.two\tmodules/shared.jar\tplugins/two/lib/modules/shared.jar\nplugin.one\tcustom.jar\tplugins/one/lib/custom.jar\n")
			args := baseArgs("--jars-file=jars.list")
			args[1] = "--kind=" + mode
			if mode == "plugin" {
				args[5] = "--plugin-jars-file=plugins.tsv"
				args = append(args, "--plugin-placement=placements.tsv")
			}
			var output, errors bytes.Buffer
			if code := run(append(args, "--trace-file=trace.json"), &output, &errors); code != 0 {
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
			if len(activities) != 3 || activities[0].OperationName != "collect packed jars" || activities[0].tag("kind") != mode {
				t.Fatalf("trace = %#v", trace)
			}
			for _, activity := range activities[1:] {
				if len(activity.References) != 1 || activity.References[0].SpanID != activities[0].SpanID {
					t.Fatalf("span is not under the action root: %#v", activity)
				}
			}
			count, collectedBytes, hashedCount, hashedBytes := "10", "1835506", "10", "1835506"
			collectionName := "collect platform jars"
			if mode == "plugin" {
				count, collectedBytes, hashedCount, hashedBytes = "3", "524293", "2", "262148"
				collectionName = "collect prepacked plugin content jars"
			}
			if activities[1].OperationName != collectionName || activities[1].tag("jarCount") != count || activities[1].tag("byteCount") != collectedBytes ||
				activities[2].OperationName != "inventory dev build component" || activities[2].tag("fileCount") != count ||
				activities[2].tag("hashedFileCount") != hashedCount || activities[2].tag("byteCount") != hashedBytes {
				t.Fatalf("span counters differ from Kotlin: %#v", activities)
			}
		})
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
