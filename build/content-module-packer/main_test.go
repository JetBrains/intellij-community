// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"archive/zip"
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"runtime"
	"strconv"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

// These cover the argument surface and the one output that is not a jar. The jars themselves are gated in
// internal/jarpack, which is where the bytes are.

func TestTheOptionSurfaceIsExactlyWhatTheActionPasses(t *testing.T) {
	// Every case here is an invariant of the rule that spawns this binary: it passes a --flagfile, may pass a
	// --trace-file, and passes nothing else. A typo in either must fail the action rather than be ignored - a run that
	// quietly skipped the trace would look like a build that simply produced no spans.
	for _, testCase := range []struct {
		name      string
		arguments []string
		wantError string
	}{
		{name: "a recipe alone", arguments: []string{"--flagfile=recipe.txt"}},
		{name: "a recipe and a trace", arguments: []string{"--flagfile=recipe.txt", "--trace-file=out/a.jar.spans.json"}},
		{name: "no recipe", arguments: []string{"--trace-file=out/a.jar.spans.json"}, wantError: "--flagfile="},
		{name: "a misspelled trace flag", arguments: []string{"--flagfile=recipe.txt", "--tracefile=x"}, wantError: "not defined"},
		{name: "a positional argument", arguments: []string{"--flagfile=recipe.txt", "x"}, wantError: "unexpected argument"},
	} {
		t.Run(testCase.name, func(t *testing.T) {
			var out strings.Builder
			opts, err := parseOptions(testCase.arguments, &out)
			if testCase.wantError != "" {
				if err == nil {
					t.Fatalf("expected an error naming %q, got %+v", testCase.wantError, opts)
				}
				if !strings.Contains(err.Error(), testCase.wantError) && !strings.Contains(out.String(), testCase.wantError) {
					t.Fatalf("the failure should name %q: %v / %s", testCase.wantError, err, out.String())
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
		})
	}
}

// packOneJar writes a source jar and a recipe under a fresh directory, and returns that directory - which is what a
// Bazel action's cwd is, and therefore what every relative path in the arguments and in the recipe resolves against.
// extra is inserted where the packing rule puts its own extra lines: straight after `output=`.
func packOneJar(t *testing.T, extra string) string {
	t.Helper()

	baseDir := t.TempDir()
	handle, err := os.Create(filepath.Join(baseDir, "module.jar"))
	if err != nil {
		t.Fatal(err)
	}
	writer := zip.NewWriter(handle)
	entry, err := writer.Create("com/example/Packed.class")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := entry.Write([]byte("\xca\xfe\xba\xbenot really a class")); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := handle.Close(); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(baseDir, "recipe.txt"), []byte("output=out/example.jar\n"+extra+"module=module.jar\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	return baseDir
}

func TestARunWithoutATraceFileWritesOnlyItsJar(t *testing.T) {
	baseDir := packOneJar(t, "")
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}

	entries, err := os.ReadDir(filepath.Join(baseDir, "out"))
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || entries[0].Name() != "example.jar" {
		t.Errorf("the output directory holds %v, want the jar alone", entries)
	}
}

func TestPackingProducesMetadataOutsideThePayload(t *testing.T) {
	baseDir := packOneJar(t, "metadata-file=example.metadata.json\n")
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}
	entries, err := filemetadata.Read(filepath.Join(baseDir, "example.metadata.json"))
	if err != nil || len(entries) != 1 || entries[0].RelativePath != "example.jar" {
		t.Fatalf("entries = %#v, error = %v", entries, err)
	}
	expected, err := filemetadata.Inspect(filepath.Join(baseDir, "out/example.jar"), "example.jar")
	if err != nil || entries[0] != expected {
		t.Fatalf("metadata = %#v, expected %#v, error = %v", entries[0], expected, err)
	}
	files, err := os.ReadDir(filepath.Join(baseDir, "out"))
	if err != nil || len(files) != 1 || files[0].Name() != "example.jar" {
		t.Fatalf("payload contains metadata: %v, error = %v", files, err)
	}
}

// packOneNativeJar writes a jna-like library and a natives-mode recipe under a fresh directory, the way the platform
// jar rule declares them: the tree is a directory named `native` beside the jar, and the variant is macOS on arm. The
// spawn helper is pty4j's, borrowed because it is the one extension-less name nativelib knows as a native.
func packOneNativeJar(t *testing.T) string {
	t.Helper()
	baseDir := t.TempDir()
	handle, err := os.Create(filepath.Join(baseDir, "jna-5.14.0.jar"))
	if err != nil {
		t.Fatal(err)
	}
	writer := zip.NewWriter(handle)
	for name, content := range map[string]string{
		"com/sun/jna/Native.class":                           "\xca\xfe\xba\xbenot really a class",
		"com/sun/jna/darwin-aarch64/libjnidispatch.jnilib":   "arm dispatch",
		"com/sun/jna/darwin-x86-64/libjnidispatch.jnilib":    "intel dispatch",
		"com/sun/jna/linux-x86-64/libjnidispatch.so":         "linux dispatch",
		"com/sun/jna/darwin-aarch64/pty4j-unix-spawn-helper": "an executable",
	} {
		entry, err := writer.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write([]byte(content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := handle.Close(); err != nil {
		t.Fatal(err)
	}
	recipe := "output=out/intellij.libraries.jna.jar\nmetadata-file=jna.metadata.json\ntrace-file=jna.spans.json\n" +
		"native-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=jna-5.14.0.jar\n"
	if err := os.WriteFile(filepath.Join(baseDir, "recipe.txt"), []byte(recipe), 0o644); err != nil {
		t.Fatal(err)
	}
	return baseDir
}

func TestNativesModeInventoriesTheJarAndTheTree(t *testing.T) {
	baseDir := packOneNativeJar(t)
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}
	entries, err := filemetadata.Read(filepath.Join(baseDir, "jna.metadata.json"))
	if err != nil {
		t.Fatal(err)
	}
	byPath := make(map[string]filemetadata.Entry, len(entries))
	var keys []string
	for _, entry := range entries {
		byPath[entry.RelativePath] = entry
		keys = append(keys, entry.RelativePath)
	}
	// The jar, the tree root by the directory's name, and every directory and file under it; nothing of the other
	// platforms, which the jar has lost all the same.
	want := []string{"intellij.libraries.jna.jar", "native", "native/aarch64", "native/aarch64/libjnidispatch.jnilib", "native/aarch64/pty4j-unix-spawn-helper"}
	if !reflect.DeepEqual(keys, want) {
		t.Fatalf("inventory keys are %v, want %v", keys, want)
	}
	if byPath["native"].Type != "directory" || byPath["native/aarch64"].Type != "directory" {
		t.Errorf("tree directories are not directories: %#v", byPath)
	}
	library := byPath["native/aarch64/libjnidispatch.jnilib"]
	expected, err := filemetadata.Inspect(filepath.Join(baseDir, "out/native/aarch64/libjnidispatch.jnilib"), library.RelativePath)
	if err != nil || library != expected || library.Type != "file" || library.Executable || library.Size != int64(len("arm dispatch")) {
		t.Errorf("library metadata = %#v, expected %#v, error = %v", library, expected, err)
	}
	if helper := byPath["native/aarch64/pty4j-unix-spawn-helper"]; runtime.GOOS != "windows" && (!helper.Executable || helper.Mode != 0o755) {
		t.Errorf("helper metadata = %#v, want an executable of mode 0755", helper)
	}
	if runtime.GOOS != "windows" && library.Mode != 0o644 {
		t.Errorf("library mode is %o, want 0644", library.Mode)
	}
	jar, err := filemetadata.Inspect(filepath.Join(baseDir, "out/intellij.libraries.jna.jar"), "intellij.libraries.jna.jar")
	if err != nil || byPath["intellij.libraries.jna.jar"] != jar {
		t.Errorf("jar metadata = %#v, expected %#v, error = %v", byPath["intellij.libraries.jna.jar"], jar, err)
	}
	// The jar itself carries the class alone.
	reader, err := zip.OpenReader(filepath.Join(baseDir, "out/intellij.libraries.jna.jar"))
	if err != nil {
		t.Fatal(err)
	}
	defer reader.Close()
	var names []string
	for _, file := range reader.File {
		names = append(names, file.Name)
	}
	if !reflect.DeepEqual(names, []string{"com/sun/jna/Native.class", "__index__"}) {
		t.Errorf("jar entries are %v, want the class and the index alone", names)
	}
	// The inventory span keeps its shape and states how many native files it counted.
	content, err := os.ReadFile(filepath.Join(baseDir, "jna.spans.json"))
	if err != nil {
		t.Fatal(err)
	}
	var document struct {
		Data []struct {
			Spans []struct {
				OperationName string `json:"operationName"`
				Tags          []struct {
					Key   string `json:"key"`
					Value string `json:"value"`
				} `json:"tags"`
			} `json:"spans"`
		} `json:"data"`
	}
	if err := json.Unmarshal(content, &document); err != nil {
		t.Fatal(err)
	}
	if len(document.Data) != 1 || len(document.Data[0].Spans) != 3 || document.Data[0].Spans[2].OperationName != "inventory packing output" {
		t.Fatalf("missing producer inventory span: %s", content)
	}
	tags := make(map[string]string)
	for _, tag := range document.Data[0].Spans[2].Tags {
		tags[tag.Key] = tag.Value
	}
	if tags["fileCount"] != "5" || tags["hashedFileCount"] != "3" || tags["nativeFileCount"] != "2" ||
		tags["byteCount"] != strconv.FormatInt(jar.Size+library.Size+byPath["native/aarch64/pty4j-unix-spawn-helper"].Size, 10) {
		t.Fatalf("producer inventory counters = %v", tags)
	}
}

func TestNativesModeInventoriesAnEmptyTree(t *testing.T) {
	// The linux jar has no macOS native: the inventory names the empty tree root and the jar, and nothing else.
	baseDir := packOneNativeJar(t)
	recipe := "output=out/intellij.libraries.jna.jar\nmetadata-file=jna.metadata.json\n" +
		"native-tree=out/native\nnative-variant=windows_aarch64\nnative-lib=jna\nlibrary=jna-5.14.0.jar\n"
	if err := os.WriteFile(filepath.Join(baseDir, "recipe.txt"), []byte(recipe), 0o644); err != nil {
		t.Fatal(err)
	}
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}
	entries, err := filemetadata.Read(filepath.Join(baseDir, "jna.metadata.json"))
	if err != nil || len(entries) != 2 || entries[0].RelativePath != "intellij.libraries.jna.jar" || entries[1].RelativePath != "native" || entries[1].Type != "directory" {
		t.Fatalf("entries = %#v, error = %v", entries, err)
	}
}

func TestNativesModeRefusesATraceDestinationInsideTheTree(t *testing.T) {
	baseDir := packOneNativeJar(t)
	before := packingFileSnapshot(t, baseDir)
	var out strings.Builder
	arguments := []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt"), "--trace-file=out/native"}
	if code := pack(context.Background(), arguments, baseDir, &out); code != 3 || !strings.Contains(out.String(), "native tree output") {
		t.Fatalf("exit %d: %s", code, out.String())
	}
	if after := packingFileSnapshot(t, baseDir); !reflect.DeepEqual(before, after) {
		t.Error("a rejected trace destination changed the packing files")
	}
}

func TestNativesModeFailsBeforeWritingWhenTheRecipeIsIncomplete(t *testing.T) {
	baseDir := packOneNativeJar(t)
	recipe := "output=out/intellij.libraries.jna.jar\nmetadata-file=jna.metadata.json\n" +
		"native-tree=out/native\nnative-lib=jna\nlibrary=jna-5.14.0.jar\n"
	if err := os.WriteFile(filepath.Join(baseDir, "recipe.txt"), []byte(recipe), 0o644); err != nil {
		t.Fatal(err)
	}
	before := packingFileSnapshot(t, baseDir)
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code != 3 || !strings.Contains(out.String(), "required together") {
		t.Fatalf("exit %d: %s", code, out.String())
	}
	if after := packingFileSnapshot(t, baseDir); !reflect.DeepEqual(before, after) {
		t.Error("an incomplete natives recipe changed the packing files")
	}
}

func TestMetadataFailureFailsPacking(t *testing.T) {
	baseDir := packOneJar(t, "metadata-file=module.jar/metadata.json\n")
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code == 0 {
		t.Fatal("packing succeeded without its declared metadata")
	}
}

func packingFileSnapshot(t *testing.T, baseDir string) map[string]string {
	t.Helper()
	files := make(map[string]string)
	err := filepath.Walk(baseDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() {
			return nil
		}
		content, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		files[path] = string(content)
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	return files
}

func TestPackingRejectsAbsoluteMetadataAliasesBeforeWriting(t *testing.T) {
	for _, alias := range []string{"./", "unused/../"} {
		for _, destination := range []string{"out/example.jar", "module.jar"} {
			t.Run(alias+destination, func(t *testing.T) {
				baseDir := packOneJar(t, "")
				if err := os.Mkdir(filepath.Join(baseDir, "unused"), 0o755); err != nil {
					t.Fatal(err)
				}
				if err := os.Mkdir(filepath.Join(baseDir, "out"), 0o755); err != nil {
					t.Fatal(err)
				}
				if err := os.WriteFile(filepath.Join(baseDir, "out/example.jar"), []byte("existing jar"), 0o644); err != nil {
					t.Fatal(err)
				}
				recipe := "output=" + filepath.Join(baseDir, "out/example.jar") + "\nmetadata-file=" + baseDir + "/" + alias + destination +
					"\ntrace-file=safe.spans.json\nmodule=" + filepath.Join(baseDir, "module.jar") + "\n"
				flagFile := filepath.Join(baseDir, "recipe.txt")
				if err := os.WriteFile(flagFile, []byte(recipe), 0o644); err != nil {
					t.Fatal(err)
				}
				before := packingFileSnapshot(t, baseDir)
				var out strings.Builder
				code := pack(context.Background(), []string{"--flagfile=" + flagFile}, baseDir, &out)
				if code != 3 || !strings.Contains(out.String(), "metadata destination") {
					t.Errorf("expected a metadata collision, got exit %d: %s", code, out.String())
				}
				if after := packingFileSnapshot(t, baseDir); !reflect.DeepEqual(before, after) {
					t.Error("rejected metadata alias changed the packing files")
				}
			})
		}
	}
}

func TestPackingRejectsEffectiveTraceCollisionsBeforeWriting(t *testing.T) {
	for _, destination := range []string{
		"out/example.jar", "example.metadata.json", "module.jar",
		"out/other.jar", "other.metadata.json", "library.jar", "resource.txt", "plugin.xml",
	} {
		for _, variant := range []string{"relative", "absolute dot", "absolute parent", "recipe"} {
			t.Run(destination+"/"+variant, func(t *testing.T) {
				baseDir := packOneJar(t, "")
				if err := os.Mkdir(filepath.Join(baseDir, "unused"), 0o755); err != nil {
					t.Fatal(err)
				}
				module, err := os.ReadFile(filepath.Join(baseDir, "module.jar"))
				if err != nil {
					t.Fatal(err)
				}
				for name, content := range map[string][]byte{
					"library.jar": module, "resource.txt": []byte("resource"), "plugin.xml": []byte("<idea-plugin/>"),
					"example.metadata.json": []byte("existing metadata"),
				} {
					if err := os.WriteFile(filepath.Join(baseDir, name), content, 0o644); err != nil {
						t.Fatal(err)
					}
				}
				traceFile := destination
				if variant == "absolute dot" {
					traceFile = baseDir + "/./" + destination
				} else if variant == "absolute parent" {
					traceFile = baseDir + "/unused/../" + destination
				}
				flagFile := filepath.Join(baseDir, "recipe.txt")
				arguments := []string{"--flagfile=" + flagFile}
				recipeTrace := "safe.spans.json"
				if variant == "recipe" {
					recipeTrace = traceFile
				} else {
					arguments = append(arguments, "--trace-file="+traceFile)
				}
				recipe := "output=out/example.jar\nmetadata-file=example.metadata.json\ntrace-file=" + recipeTrace +
					"\nmodule=module.jar\noutput=out/other.jar\nmetadata-file=other.metadata.json\nlibrary=library.jar\n" +
					"file=resource.txt=resource.txt\npatch=META-INF/plugin.xml=plugin.xml\n"
				if err := os.WriteFile(flagFile, []byte(recipe), 0o644); err != nil {
					t.Fatal(err)
				}
				before := packingFileSnapshot(t, baseDir)
				var out strings.Builder
				code := pack(context.Background(), arguments, baseDir, &out)
				if code != 3 || (!strings.Contains(out.String(), "trace destination") && !strings.Contains(out.String(), "conflicting metadata destination")) {
					t.Errorf("expected a trace collision, got exit %d: %s", code, out.String())
				}
				if after := packingFileSnapshot(t, baseDir); !reflect.DeepEqual(before, after) {
					t.Error("rejected trace destination changed the packing files")
				}
			})
		}
	}
}

func TestPackingMetadataReadMetrics(t *testing.T) {
	baseDir := packOneJar(t, "metadata-file=example.metadata.json\ntrace-file=example.spans.json\n")
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}
	content, err := os.ReadFile(filepath.Join(baseDir, "example.spans.json"))
	if err != nil {
		t.Fatal(err)
	}
	var document struct {
		Data []struct {
			Spans []struct {
				OperationName string `json:"operationName"`
				Tags          []struct {
					Key   string `json:"key"`
					Value string `json:"value"`
				} `json:"tags"`
			} `json:"spans"`
		} `json:"data"`
	}
	if err := json.Unmarshal(content, &document); err != nil {
		t.Fatal(err)
	}
	if len(document.Data) != 1 || len(document.Data[0].Spans) != 3 || document.Data[0].Spans[2].OperationName != "inventory packing output" {
		t.Fatalf("missing producer inventory span: %s", content)
	}
	tags := make(map[string]string)
	for _, tag := range document.Data[0].Spans[2].Tags {
		tags[tag.Key] = tag.Value
	}
	info, err := os.Stat(filepath.Join(baseDir, "out/example.jar"))
	if err != nil {
		t.Fatal(err)
	}
	if tags["byteCount"] != strconv.FormatInt(info.Size(), 10) || tags["hashedFileCount"] != "1" || tags["fileCount"] != "1" {
		t.Fatalf("producer inventory counters = %v", tags)
	}
}

func TestARunWithATraceFileDescribesItselfInIt(t *testing.T) {
	baseDir := packOneJar(t, "")
	var out strings.Builder
	arguments := []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt"), "--trace-file=out/example.jar.spans.json"}
	if code := pack(context.Background(), arguments, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}

	// The path was relative, so it landed beside the jar: resolved against the working directory, which is what a
	// worker's cwd is for the life of the process.
	content, err := os.ReadFile(filepath.Join(baseDir, "out", "example.jar.spans.json"))
	if err != nil {
		t.Fatal(err)
	}

	var document struct {
		Data []struct {
			TraceID   string `json:"traceID"`
			Processes map[string]struct {
				ServiceName string `json:"serviceName"`
			} `json:"processes"`
			Spans []struct {
				OperationName string `json:"operationName"`
				ProcessID     string `json:"processID"`
				Tags          []struct {
					Key   string `json:"key"`
					Type  string `json:"type"`
					Value string `json:"value"`
				} `json:"tags"`
				References []struct {
					RefType string `json:"refType"`
					SpanID  string `json:"spanID"`
				} `json:"references"`
			} `json:"spans"`
		} `json:"data"`
	}
	if err := json.Unmarshal(content, &document); err != nil {
		t.Fatalf("%v in %s", err, string(content))
	}
	if len(document.Data) != 1 {
		t.Fatalf("want one trace, got %d", len(document.Data))
	}
	trace := document.Data[0]
	if got := trace.Processes["p1"].ServiceName; got != "content-module-packer" {
		t.Errorf("the producer names itself %q", got)
	}
	if len(trace.Spans) != 2 {
		t.Fatalf("want a root and one jar, got %d spans in %s", len(trace.Spans), string(content))
	}
	if trace.Spans[0].OperationName != "pack content modules" || trace.Spans[1].OperationName != "pack jar" {
		t.Errorf("unexpected span names: %q, %q", trace.Spans[0].OperationName, trace.Spans[1].OperationName)
	}
	if len(trace.Spans[0].References) != 0 {
		t.Errorf("the root span has a parent: %v", trace.Spans[0].References)
	}
	if len(trace.Spans[1].References) != 1 || trace.Spans[1].References[0].RefType != "CHILD_OF" {
		t.Errorf("the jar span is not a child: %v", trace.Spans[1].References)
	}

	tags := make(map[string]string, len(trace.Spans[1].Tags))
	for _, tag := range trace.Spans[1].Tags {
		tags[tag.Key] = tag.Value
	}
	if tags["jar"] != "example.jar" || tags["sources"] != "1" {
		t.Errorf("the jar span describes something else: %v", tags)
	}
	// The one tag whose value cannot be written down here, because it is the size of the jar this run just packed.
	info, err := os.Stat(filepath.Join(baseDir, "out", "example.jar"))
	if err != nil {
		t.Fatal(err)
	}
	if got, want := tags["bytes"], info.Size(); got != strconv.FormatInt(want, 10) {
		t.Errorf("bytes is %q, and the jar is %d bytes", got, want)
	}
}

func TestATraceFileThatCannotBeWrittenFailsTheRequest(t *testing.T) {
	// In a worker this is a request that has to come back with a non-zero exit code: the action declared the file, and
	// the failure may not go to stdout, which is the protocol.
	baseDir := packOneJar(t, "")
	var out strings.Builder
	arguments := []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt"), "--trace-file=module.jar/spans.json"}
	code := pack(context.Background(), arguments, baseDir, &out)
	if code == 0 {
		t.Fatalf("the request should have failed: %s", out.String())
	}
	if !strings.Contains(out.String(), "span file") {
		t.Errorf("the report should say what failed: %s", out.String())
	}
	// And the jar it was asked for is there regardless, because it was written before the trace was.
	if _, err := os.Stat(filepath.Join(baseDir, "out", "example.jar")); err != nil {
		t.Errorf("the jar should still have been packed: %v", err)
	}
}

func TestTheRecipeCanNameTheTraceDestination(t *testing.T) {
	// This is the build's channel, and the only one it has: Bazel splits a worker spawn's arguments at the param file,
	// so a `--trace-file=` there would belong to the worker process and to its WorkerKey - one worker per action across
	// ~2 500 of them. The rule writes the line straight after `output=`; no flag is passed at all.
	baseDir := packOneJar(t, "trace-file=out/example.jar.spans.json\n")
	var out strings.Builder
	if code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}

	content, err := os.ReadFile(filepath.Join(baseDir, "out", "example.jar.spans.json"))
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{`"operationName":"pack content modules"`, `"operationName":"pack jar"`,
		`"key":"jar","type":"string","value":"example.jar"`} {
		if !strings.Contains(string(content), want) {
			t.Errorf("%s is missing from %s", want, string(content))
		}
	}
}

func TestTheCommandLineWinsOverTheRecipe(t *testing.T) {
	// So that a flag file captured from `bazel aquery` - which carries the action's own `trace-file=` pointing into
	// bazel-out - can be re-run with the trace sent somewhere harmless.
	baseDir := packOneJar(t, "trace-file=out/from-the-recipe.json\n")
	var out strings.Builder
	arguments := []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt"), "--trace-file=out/from-the-flag.json"}
	if code := pack(context.Background(), arguments, baseDir, &out); code != 0 {
		t.Fatalf("exit %d: %s", code, out.String())
	}

	if _, err := os.Stat(filepath.Join(baseDir, "out", "from-the-flag.json")); err != nil {
		t.Errorf("the flag's destination was not written: %v", err)
	}
	if _, err := os.Stat(filepath.Join(baseDir, "out", "from-the-recipe.json")); err == nil {
		t.Error("the recipe's destination was written as well")
	}
}

func TestARecipeThatDoesNotParseWritesNoTrace(t *testing.T) {
	// It cannot: in a build the destination is in the file that just failed to parse. The action fails, and Bazel
	// discards the outputs of a failed action anyway.
	baseDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(baseDir, "recipe.txt"), []byte("modul=mod/a.jar\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	var out strings.Builder
	code := pack(context.Background(), []string{"--flagfile=" + filepath.Join(baseDir, "recipe.txt")}, baseDir, &out)
	if code == 0 {
		t.Fatal("a recipe that does not parse must fail the request")
	}

	entries, err := os.ReadDir(baseDir)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 {
		t.Errorf("%v: nothing but the recipe should have been written", entries)
	}
}
