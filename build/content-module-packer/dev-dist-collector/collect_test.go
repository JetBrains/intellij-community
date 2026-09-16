package main

import (
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

func requireError(t *testing.T, err error, message string) {
	t.Helper()
	if err == nil || !strings.Contains(err.Error(), message) {
		t.Fatalf("error = %v, want %q", err, message)
	}
}

func TestPlatformJars(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "jars.list", "\r\n \t\rinputs/z.jar\r\ninputs/a.jar\n")
	actual, err := collectPlatformJars("jars.list")
	if err != nil {
		t.Fatal(err)
	}
	expected := []sourcedFile{
		{Source: "inputs/z.jar", RelativePath: "lib/z.jar"},
		{Source: "inputs/a.jar", RelativePath: "lib/a.jar"},
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("files = %#v, want %#v", actual, expected)
	}
	writeText(t, "jars.list", "inputs/shared.jar\nother/shared.jar")
	_, err = collectPlatformJars("jars.list")
	requireError(t, err, "two packed jars are named 'shared.jar'")
	writeText(t, "jars.list", " \t\n")
	_, err = collectPlatformJars("jars.list")
	requireError(t, err, "names no jar")
}

func TestPluginRelations(t *testing.T) {
	t.Chdir(t.TempDir())
	writeText(t, "jars.tsv", "plugin.two\tmodules/shared.jar\tinputs/shared.jar\n"+
		"plugin.one\tcustom.jar\tinputs/custom.jar\nplugin.one\tmodules/shared.jar\tinputs/shared.jar\n")
	writeText(t, "one.tsv", "plugin.one\tmodules/shared.jar\tplugins/one/lib/modules/shared.jar\n")
	writeText(t, "two.tsv", "plugin.two\tmodules/shared.jar\tplugins/two/lib/modules/shared.jar\n"+
		"plugin.one\tcustom.jar\tplugins/one/lib/custom.jar\n")
	actual, err := collectPluginJars("jars.tsv", []string{"one.tsv", "two.tsv"})
	if err != nil {
		t.Fatal(err)
	}
	expected := []sourcedFile{
		{Source: "inputs/custom.jar", RelativePath: "plugins/one/lib/custom.jar"},
		{Source: "inputs/shared.jar", RelativePath: "plugins/one/lib/modules/shared.jar"},
		{Source: "inputs/shared.jar", RelativePath: "plugins/two/lib/modules/shared.jar"},
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("files = %#v, want %#v", actual, expected)
	}
	writeText(t, "empty.tsv", "")
	empty, err := collectPluginJars("empty.tsv", nil)
	if err != nil || len(empty) != 0 {
		t.Fatalf("empty plugin records: files = %#v, error = %v", empty, err)
	}
}

func TestInvalidPluginRelations(t *testing.T) {
	const jar = "plugin.one\tmodules/content.jar\tinput.jar\n"
	const placement = "plugin.one\tmodules/content.jar\tplugins/one/lib/modules/content.jar\n"
	cases := []struct {
		name       string
		jars       string
		placements []string
		message    string
	}{
		{"missing placement", jar, nil, "missing placements [plugin.one/modules/content.jar]"},
		{"unknown placement", "", []string{placement}, "unknown placements [plugin.one/modules/content.jar]"},
		{"duplicate jar", jar + jar, nil, "duplicate plugin jar relation"},
		{"duplicate placement", jar, []string{placement, placement}, "duplicate placement"},
		{"few fields", "plugin.one\tcontent.jar", nil, "expected 3 tab-separated fields, got 2"},
		{"extra fields", jar + "plugin.two\tcontent.jar\tinput.jar\tmore", nil, "expected 3 tab-separated fields, got 4"},
		{"blank field", "plugin.one\t \tinput.jar", nil, "fields must not be blank"},
		{"trailing field", "plugin.one\tcontent.jar\t", nil, "fields must not be blank"},
		{"output escape", "plugin.one\t../content.jar\tinput.jar", nil, "escapes plugin lib"},
		{"absolute output", "plugin.one\t/content.jar\tinput.jar", nil, "escapes plugin lib"},
		{"unnormalized output", "plugin.one\tmodules/./content.jar\tinput.jar", nil, "escapes plugin lib"},
		{"placement escape", jar, []string{"plugin.one\tmodules/content.jar\t../outside.jar"}, "escapes the distribution"},
		{"absolute placement", jar, []string{"plugin.one\tmodules/content.jar\t/plugins/one/lib/modules/content.jar"}, "escapes the distribution"},
		{"wrong suffix", jar, []string{"plugin.one\tmodules/content.jar\tplugins/one/lib/modules/other.jar"}, "expected plugins/<directory>/lib/modules/content.jar"},
		{"partial suffix", jar, []string{"plugin.one\tmodules/content.jar\tplugins/one/notlib/modules/content.jar"}, "expected plugins/<directory>"},
		{"partial prefix", jar, []string{"plugin.one\tmodules/content.jar\tplugins-other/one/lib/modules/content.jar"}, "expected plugins/<directory>"},
		{"collision", jar + "plugin.two\tmodules/content.jar\tother.jar", []string{
			placement + "plugin.two\tmodules/content.jar\tplugins/one/lib/modules/content.jar",
		}, "both claim plugins/one/lib/modules/content.jar"},
		{"invalid UTF-8", jar + "\xff", nil, "not valid UTF-8"},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			t.Chdir(t.TempDir())
			writeText(t, "jars.tsv", test.jars)
			var placements []string
			for index, text := range test.placements {
				name := strings.Repeat("part-", index+1) + ".tsv"
				placements = append(placements, writeText(t, name, text))
			}
			_, err := collectPluginJars("jars.tsv", placements)
			requireError(t, err, test.message)
		})
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
