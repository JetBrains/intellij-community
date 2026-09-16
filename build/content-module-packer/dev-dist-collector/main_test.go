package main

import (
	"bytes"
	"encoding/json"
	"os"
	"reflect"
	"strings"
	"testing"
)

func baseArgs(mode string) []string {
	return []string{"--component-manifest=component.json", "--kind=files", "--platform-prefix=idea", "--os=linux", "--arch=x64", mode}
}

func TestOptions(t *testing.T) {
	for _, osAlias := range []struct{ input, expected string }{
		{"WINDOWS", "windows"}, {"win", "windows"}, {"macos", "mac"}, {"MAC", "mac"}, {"Linux", "linux"},
	} {
		for _, archAlias := range []struct{ input, expected string }{
			{"x64", "x64"}, {"X86_64", "x64"}, {"amd64", "x64"}, {"AArch64", "aarch64"}, {"arm64", "aarch64"},
		} {
			args := baseArgs("--plugin-jars-file=jars.tsv")
			args[3] = "--os=" + osAlias.input
			args[4] = "--arch=" + archAlias.input
			opts, err := parseOptions(append(args, "--plugin-placement=one.tsv", "--plugin-placement=two.tsv", "--trace-file="))
			if err != nil || opts.os != osAlias.expected || opts.arch != archAlias.expected ||
				!reflect.DeepEqual(opts.placements, []string{"one.tsv", "two.tsv"}) {
				t.Fatalf("options = %#v, error = %v", opts, err)
			}
		}
	}
	opts, err := parseOptions([]string{"--component-manifest=component.json", "--kind=files", "--platform-prefix=idea", "--files-file=files.json"})
	if err != nil || opts.os == "" || opts.arch == "" {
		t.Fatalf("host options = %#v, error = %v", opts, err)
	}
}

func TestInvalidOptions(t *testing.T) {
	cases := []struct {
		extra   []string
		message string
	}{
		{[]string{"--files-file=files.json"}, "exactly one"},
		{[]string{"--plugin-jars-file=jars.tsv"}, "exactly one"},
		{[]string{"--jars-file=other"}, "at most once"},
		{[]string{"--kind="}, "at most once"},
		{[]string{"--plugin-placement=placements.tsv"}, "only valid with --plugin-jars-file"},
		{[]string{"--unknown="}, "unknown option"},
		{[]string{"file"}, "--key=value"},
	}
	for _, test := range cases {
		_, err := parseOptions(append(baseArgs("--jars-file=jars.list"), test.extra...))
		requireError(t, err, test.message)
	}
	for _, index := range []int{0, 1, 2, 3, 4, 5} {
		args := baseArgs("--jars-file=jars.list")
		if index < 3 || index == 5 {
			args[index] = strings.Split(args[index], "=")[0] + "="
		} else {
			args[index] = strings.Split(args[index], "=")[0] + "=unknown"
		}
		if _, err := parseOptions(args); err == nil {
			t.Fatalf("accepted %v", args)
		}
	}
}

func TestFileModeCLI(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "inputs/ijent", "binary bytes")
	writeText(t, "files.json", `[{"source":"inputs/ijent","relativePath":"bin/ijent","executable":true}]`)
	var output, errors bytes.Buffer
	if code := run(baseArgs("--files-file=files.json"), &output, &errors); code != 0 {
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
	if len(manifest.Entries) != 1 || !manifest.Entries[0].Executable || manifest.Entries[0].Source != "inputs/ijent" {
		t.Fatalf("manifest = %#v", manifest)
	}
	if !strings.Contains(output.String(), "named 1 files") || errors.Len() != 0 {
		t.Fatalf("stdout = %s, stderr = %s", &output, &errors)
	}
	entries, err := os.ReadDir(".")
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 3 {
		t.Fatalf("unexpected outputs: %v", entries)
	}
}

func TestFailedRunWritesTraceWithoutManifest(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "files.json", `[{"source":"missing","relativePath":"bin/ijent","executable":true}]`)
	var output, errors bytes.Buffer
	args := append(baseArgs("--files-file=files.json"), "--trace-file=trace.json")
	if code := run(args, &output, &errors); code == 0 {
		t.Fatal("missing source succeeded")
	}
	if _, err := os.Stat("component.json"); !os.IsNotExist(err) {
		t.Fatalf("failed action wrote a manifest: %v", err)
	}
	trace := readTrace(t, "trace.json")
	if trace.Data[0].Spans[0].OperationName != "collect files" || trace.Data[0].Spans[0].tag("error") != "true" {
		t.Fatalf("failure trace = %#v", trace)
	}
	if output.Len() != 0 || !strings.Contains(errors.String(), "missing") {
		t.Fatalf("stdout = %s, stderr = %s", &output, &errors)
	}
}

func TestUnwritableTraceFailsRun(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "files.json", "[]")
	writeText(t, "not-a-directory", "bytes")
	var output, errors bytes.Buffer
	code := run(append(baseArgs("--files-file=files.json"), "--trace-file=not-a-directory/trace.json"), &output, &errors)
	if code == 0 || !strings.Contains(errors.String(), "writing the span file") {
		t.Fatalf("exit = %d, stderr = %s", code, &errors)
	}
}

type traceSpan struct {
	OperationName string `json:"operationName"`
	SpanID        string `json:"spanID"`
	References    []struct {
		SpanID string `json:"spanID"`
	} `json:"references"`
	Tags []struct {
		Key   string `json:"key"`
		Type  string `json:"type"`
		Value string `json:"value"`
	} `json:"tags"`
}

func (activity traceSpan) tag(name string) string {
	for _, tag := range activity.Tags {
		if tag.Key == name {
			return tag.Value
		}
	}
	return ""
}

type traceDocument struct {
	Data []struct {
		Spans []traceSpan `json:"spans"`
	} `json:"data"`
}

func readTrace(t *testing.T, file string) traceDocument {
	t.Helper()
	data, err := os.ReadFile(file)
	if err != nil {
		t.Fatal(err)
	}
	var trace traceDocument
	if err := json.Unmarshal(data, &trace); err != nil {
		t.Fatal(err)
	}
	return trace
}
