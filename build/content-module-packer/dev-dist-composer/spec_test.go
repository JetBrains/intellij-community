package main

import (
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

func writeSpec(test *testing.T, content string) string {
	test.Helper()
	file := filepath.Join(test.TempDir(), "composition.json")
	writeTestFile(test, file, content)
	return file
}

func TestCompositionSpecDecodesItsVersionedContract(test *testing.T) {
	spec, err := readCompositionSpec(writeSpec(test, `{
  "version": 1,
  "expectedFragments": ["platform_core", "intellij.java.plugin"],
  "additionalModules": ["intellij.air.plugin"],
  "components": [
    {"manifest": "core.json"},
    {"manifest": "plugins.json", "pluginClasspathPart": "plugins.part"}
  ],
  "pluginClasspathPrefix": "prefix.bin"
}`))
	if err != nil {
		test.Fatal(err)
	}
	if !slices.Equal(spec.ExpectedFragments, []string{"platform_core", "intellij.java.plugin"}) ||
		!slices.Equal(spec.AdditionalModules, []string{"intellij.air.plugin"}) || *spec.PluginClasspathPrefix != "prefix.bin" ||
		spec.SourceRunfiles != nil || len(spec.SourceDirectoryRunfiles.keys) != 0 || spec.SourceBindings != nil {
		test.Fatalf("spec = %+v", spec)
	}
	if len(spec.Components) != 2 || spec.Components[0].Manifest != "core.json" ||
		spec.Components[0].PluginClasspathPart != nil || *spec.Components[1].PluginClasspathPart != "plugins.part" {
		test.Fatalf("components = %+v", spec.Components)
	}
}

// Starlark `json.encode` writes a null for an absent file and an absent map, and the spec keeps the key order.
func TestCompositionSpecDecodesTheStarlarkShape(test *testing.T) {
	spec, err := readCompositionSpec(writeSpec(test, `{"version":1,"expectedFragments":["a"],"additionalModules":[],`+
		`"components":[{"manifest":"a.json","pluginClasspathPart":null}],"pluginClasspathPrefix":null,`+
		`"sourceRunfiles":{"b":"_main/b","a":"_main/a","b":"_main/b2"},"sourceDirectoryRunfiles":{},"sourceBindings":null}`))
	if err != nil {
		test.Fatal(err)
	}
	if spec.Components[0].PluginClasspathPart != nil || spec.PluginClasspathPrefix != nil || !slices.Equal(spec.SourceRunfiles.keys, []string{"b", "a"}) ||
		spec.SourceRunfiles.values["b"] != "_main/b2" {
		test.Fatalf("spec = %+v, source runfiles = %+v", spec, spec.SourceRunfiles)
	}
}

func TestCompositionSpecRejectsInvalidContent(test *testing.T) {
	for _, invalid := range []struct {
		content string
		message string
	}{
		{`{"version": 2, "expectedFragments": ["platform_core"], "components": [{"manifest": "core.json"}]}`,
			"Unsupported dev-build composition spec version 2 in "},
		{`{"expectedFragments": [], "components": []}`, "has no components"},
		{`{"components": [{"manifest": "core.json"}]}`, "Field 'expectedFragments' is required"},
		{`{"expectedFragments": [], "components": [{"manifest": "core.json"}], "extra": 1}`, "unknown key 'extra'"},
		{`{"expectedFragments": [], "Components": [{"manifest": "core.json"}]}`, "unknown key 'Components'"},
		{`{"expectedFragments": [], "components": [{"pluginClasspathPart": "core.part"}]}`, "Field 'manifest' is required"},
		{`{"expectedFragments": [], "components": [{"root": "core", "manifest": "core.json"}]}`, "unknown key 'root'"},
		{`{"expectedFragments": null, "components": [{"manifest": "core.json"}]}`, "non-nullable property 'expectedFragments'"},
		{`{"expectedFragments": [], "additionalModules": null, "components": [{"manifest": "core.json"}]}`, "non-nullable"},
		{`{"expectedFragments": [], "sourceDirectoryRunfiles": null, "components": [{"manifest": "core.json"}]}`, "non-nullable"},
		{`{"expectedFragments": [1], "components": [{"manifest": "core.json"}]}`, "Expected a string literal"},
		{`{"expectedFragments": [], "components": [{"manifest": "core.json"}]} {}`, "Expected EOF"},
	} {
		_, err := readCompositionSpec(writeSpec(test, invalid.content))
		requireError(test, err, invalid.message)
	}
}

func TestComponentManifestRejectsInvalidContent(test *testing.T) {
	valid := `{"kind":"a","platformPrefix":"idea","os":"linux","arch":"x64","additionalModules":[],"mainClass":null,"coreClassPath":[],"entries":[]}`
	if manifest := readGoldenManifest(test, valid); manifest.Version != 9 || manifest.MainClass != nil || manifest.PluginCount != 0 {
		test.Fatalf("manifest = %+v", manifest)
	}
	quoted := readGoldenManifest(test, strings.Replace(valid, `"entries":[]`,
		`"pluginCount":"2","entries":[{"relativePath":"a","type":"component-file","hash":"-3","executable":"TRUE","mode":"493"}]`, 1))
	if quoted.PluginCount != 2 || *quoted.Entries[0].Hash != -3 || *quoted.Entries[0].Mode != 0o755 || !quoted.Entries[0].Executable {
		test.Fatalf("manifest with quoted numbers = %+v", quoted)
	}
	for _, invalid := range []struct {
		content string
		message string
	}{
		{strings.Replace(valid, `"kind":"a"`, `"version":8,"kind":"a"`, 1), "Unsupported dev-build component manifest version 8 in "},
		{strings.Replace(valid, `"mainClass":null,`, ``, 1), "Field 'mainClass' is required"},
		{strings.Replace(valid, `"entries":[]`, `"entries":[{"relativePath":"a","type":"component-file"}]`, 1), "entry 'a' requires a hash"},
		{strings.Replace(valid, `"entries":[]`, `"entries":[{"relativePath":"a","type":"component-file","hash":1,"Mode":1}]`, 1), "unknown key 'Mode'"},
		{strings.Replace(valid, `"entries":[]`, `"entries":[{"relativePath":"a","type":"component-file","hash":1,"mode":2147483648}]`, 1), "numeric property 'mode'"},
		{strings.Replace(valid, `"entries":[]`, `"entries":[{"relativePath":"a","type":"component-file","hash":1,"executable":1}]`, 1), "Expected a boolean"},
		{strings.Replace(valid, `"entries":[]`, `"entries":[{"relativePath":"a","type":"directory"}]`, 1), "Invalid directory entry 'a'"},
		{strings.Replace(valid, `"os":"linux"`, `"os":null`, 1), "non-nullable property 'os'"},
	} {
		file := filepath.Join(test.TempDir(), "manifest.json")
		writeTestFile(test, file, invalid.content)
		_, err := readComponentManifest(file)
		requireError(test, err, invalid.message)
	}
}

func TestJavaLines(test *testing.T) {
	for _, sample := range []struct {
		text  string
		lines []string
	}{
		{"", nil}, {"a", []string{"a"}}, {"a\n", []string{"a"}}, {"a\r\nb\rc\n\nd", []string{"a", "b", "c", "", "d"}},
	} {
		if actual := javaLines(sample.text); !slices.Equal(actual, sample.lines) {
			test.Errorf("lines of %q = %q", sample.text, actual)
		}
	}
}
