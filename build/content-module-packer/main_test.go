// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"archive/zip"
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
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
