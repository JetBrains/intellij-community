package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

func writeTestFile(t *testing.T, name string, data []byte) string {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(name), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(name, data, 0o644); err != nil {
		t.Fatal(err)
	}
	return name
}

func writeText(t *testing.T, name, text string) string {
	t.Helper()
	return writeTestFile(t, name, []byte(text))
}

// The jar records the packing rule writes for a jar that keeps its own name, which is every jar these tests pack.
func writeJarRecords(t *testing.T, name string, sources ...string) string {
	t.Helper()
	records := make([]map[string]string, 0, len(sources))
	for _, source := range sources {
		records = append(records, map[string]string{"source": source, "relativePath": filepath.Base(source)})
	}
	data, err := json.Marshal(records)
	if err != nil {
		t.Fatal(err)
	}
	return writeTestFile(t, name, data)
}

func requireError(t *testing.T, err error, message string) {
	t.Helper()
	if err == nil || !strings.Contains(err.Error(), message) {
		t.Fatalf("error = %v, want %q", err, message)
	}
}

func TestPlatformJars(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "jars.json", `[
  {"source":"inputs/z.jar", "relativePath":"z.jar"},
  {"source":"inputs/a.jar", "relativePath":"ext/a.jar"}
]`)
	actual, err := collectPlatformJars("jars.json")
	if err != nil {
		t.Fatal(err)
	}
	expected := []sourcedFile{
		{Source: "inputs/z.jar", RelativePath: "lib/z.jar"},
		{Source: "inputs/a.jar", RelativePath: "lib/ext/a.jar"},
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("files = %#v, want %#v", actual, expected)
	}
	// The same jar name under two destinations is what the nested destinations are for, so it must stay legal.
	writeText(t, "jars.json", `[
  {"source":"inputs/shared.jar", "relativePath":"shared.jar"},
  {"source":"other/shared.jar", "relativePath":"ext/shared.jar"}
]`)
	if _, err := collectPlatformJars("jars.json"); err != nil {
		t.Fatal(err)
	}
	writeText(t, "jars.json", "[]")
	_, err = collectPlatformJars("jars.json")
	requireError(t, err, "names no jar")
	// A tree record is the library's directory under `lib/`, kept apart from the jars until the metadata expands it.
	writeText(t, "jars.json", `[
  {"source":"inputs/intellij.libraries.jna.jar", "relativePath":"intellij.libraries.jna.jar"},
  {"source":"inputs/native", "relativePath":"jna", "tree":true},
  {"source":"inputs/other.jar", "relativePath":"other.jar", "tree":false}
]`)
	actual, err = collectPlatformJars("jars.json")
	if err != nil {
		t.Fatal(err)
	}
	expected = []sourcedFile{
		{Source: "inputs/intellij.libraries.jna.jar", RelativePath: "lib/intellij.libraries.jna.jar"},
		{Source: "inputs/native", RelativePath: "lib/jna", tree: true},
		{Source: "inputs/other.jar", RelativePath: "lib/other.jar"},
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("files = %#v, want %#v", actual, expected)
	}
}

func TestInvalidPlatformJars(t *testing.T) {
	cases := []struct{ text, message string }{
		{`null`, "expected an array"},
		{`[{"relativePath":"a.jar"}]`, "requires source and relativePath"},
		{`[{"source":"in","relativePath":" "}]`, "requires source and relativePath"},
		{`[{"source":"in","relativePath":"a.jar","executable":true}]`, "states executable"},
		{`[{"source":"in","relativePath":"jna","tree":true,"executable":false}]`, "states executable"},
		{`[{"source":"in","relativePath":"../jna","tree":true}]`, "escapes the distribution"},
		{`[{"source":"in","relativePath":"jna","tree":"true"}]`, "cannot unmarshal"},
		{`[{"source":"in","relativePath":"../a.jar"}]`, "escapes the distribution"},
		{`[{"source":"in","relativePath":"/a.jar"}]`, "escapes the distribution"},
		{`[{"source":"in","relativePath":"a.jar","extra":1}]`, "unknown field"},
	}
	for _, test := range cases {
		t.Run(test.text, func(t *testing.T) {
			file := writeText(t, filepath.Join(t.TempDir(), "jars.json"), test.text)
			_, err := collectPlatformJars(file)
			requireError(t, err, test.message)
		})
	}
}

// A repeated destination and a destination that holds another are refused by `validateDestinations`, which both
// collection modes share, so the jar mode keeps no check of its own.
func TestConflictingPlatformJarDestinations(t *testing.T) {
	cases := []struct{ files []sourcedFile; message string }{
		{[]sourcedFile{{Source: "one", RelativePath: "lib/a.jar"}, {Source: "two", RelativePath: "lib/a.jar"}}, "conflicting destination: lib/a.jar"},
		{[]sourcedFile{{Source: "one", RelativePath: "lib/ext.jar"}, {Source: "two", RelativePath: "lib/ext.jar/a.jar"}}, "contains"},
	}
	for _, test := range cases {
		requireError(t, validateDestinations(test.files), test.message)
	}
}

func TestFiles(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "files.json", `[
  {"source":"inputs/ijent", "relativePath":"bin/ijent", "executable":true},
  {"source":"inputs/ijent", "relativePath":"other/ijent", "executable":false}
]`)
	actual, err := collectFiles("files.json")
	if err != nil {
		t.Fatal(err)
	}
	expected := []sourcedFile{
		{Source: "inputs/ijent", RelativePath: "bin/ijent", Executable: true},
		{Source: "inputs/ijent", RelativePath: "other/ijent"},
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("files = %#v, want %#v", actual, expected)
	}
	writeText(t, "files.json", "[]")
	actual, err = collectFiles("files.json")
	if err != nil || len(actual) != 0 {
		t.Fatalf("empty records: files = %#v, error = %v", actual, err)
	}
}

func TestInvalidFiles(t *testing.T) {
	cases := []struct{ text, message string }{
		{`null`, "expected an array"},
		{`[null]`, "requires source, relativePath and executable"},
		{`[{"source":"in","relativePath":"bin/out"}]`, "requires source, relativePath and executable"},
		{`[{"source":"in","relativePath":"bin/out","executable":null}]`, "requires source, relativePath and executable"},
		{`[{"source":" ","relativePath":"bin/out","executable":true}]`, "requires source, relativePath and executable"},
		{`[{"source":"in","relativePath":"../out","executable":true}]`, "escapes the distribution"},
		{`[{"source":"in","relativePath":"/out","executable":true}]`, "escapes the distribution"},
		{`[{"source":"in","relativePath":"bin/../out","executable":true}]`, "escapes the distribution"},
		{`[{"source":"in","relativePath":"out","executable":true,"extra":1}]`, "unknown field"},
		{`[{"source":"in","relativePath":"out","executable":true,"tree":true}]`, "states tree"},
		{`[{"source":"in","relativePath":"out","executable":"true"}]`, "cannot unmarshal"},
		{`[] []`, "unexpected data"},
		{`[{"source":"in","relativePath":"out","executable":true},{"source":"other","relativePath":"out","executable":false}]`, "duplicate destination"},
		{"[\xff]", "not valid UTF-8"},
	}
	for _, test := range cases {
		t.Run(test.text, func(t *testing.T) {
			file := writeText(t, filepath.Join(t.TempDir(), "files.json"), test.text)
			_, err := collectFiles(file)
			requireError(t, err, test.message)
		})
	}
}

func TestJavaPathAndStringSemantics(t *testing.T) {
	actual, err := normalizedRelativePath("plugins//one/lib/modules/content.jar/")
	if err != nil || actual != "plugins/one/lib/modules/content.jar" {
		t.Fatalf("path = %q, error = %v", actual, err)
	}
	if compareStrings("\U0001f600", "\ue000") >= 0 {
		t.Fatal("manifest order must use UTF-16 code units")
	}
	if !isBlank("\u001c\u00a0\u2003") || isBlank("\u0085") {
		t.Fatal("blank lines must follow Kotlin whitespace rules")
	}
}
