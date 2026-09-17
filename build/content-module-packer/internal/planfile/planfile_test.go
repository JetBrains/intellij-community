package planfile

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/pluginclasspath"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

func readPlan(t *testing.T, text string) (*File, error) {
	t.Helper()
	file := filepath.Join(t.TempDir(), "plan.json")
	if err := os.WriteFile(file, []byte(text), 0o644); err != nil {
		t.Fatal(err)
	}
	return Read(file)
}

func mustReadPlan(t *testing.T, text string) *File {
	t.Helper()
	file, err := readPlan(t, text)
	if err != nil {
		t.Fatal(err)
	}
	return file
}

// plan wraps assets and the optional sections into one plan file text.
func plan(version int, assets string, sections ...string) string {
	text := `{"version": ` + itoa(version) + `, "plugin": "demo", "variant": "", "layoutSignature": "signature", "assets": [` + assets + `]`
	for _, section := range sections {
		text += ", " + section
	}
	return text + "}"
}

func itoa(value int) string {
	return string(rune('0' + value))
}

func fileArtifact(id string) pluginpack.Artifact {
	return pluginpack.Artifact{ID: id, Kind: "file", Root: "inputs/" + strings.ReplaceAll(id, "/", "_")}
}

func directoryArtifact(id string) pluginpack.Artifact {
	return pluginpack.Artifact{ID: id, Kind: "directory", Root: "inputs/" + id}
}

func catalogue(artifacts ...pluginpack.Artifact) pluginpack.Catalogue {
	return pluginpack.Catalogue{Version: pluginpack.Version, Artifacts: artifacts}
}

const moduleFilterSection = `"preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"}],
	"operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "drop", "excludes": ["drop/**"]}]`

const filteredJar = `{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "drop"}}}`

func TestReadExpandsTheCompactForms(t *testing.T) {
	file := mustReadPlan(t, plan(1, `{"module": "demo.content"}, {"module": "demo.other", "mode": 420, "classPath": true},
		{"destination": "lib/demo.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}},
		{"destination": "bin/tool", "inputs": ["native"], "mode": 493, "classPath": false, "scope": "distribution"}`,
		`"reusableArtifacts": [{"label": "//demo:content.jar", "module": "demo.content"}, {"label": "//demo:other.jar", "module": "demo.other"},
		{"label": "//demo:demo.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}, "mode": 493}]`,
		`"operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "keep"}]`))
	content := moduleJarAsset("demo.content")
	if !reflect.DeepEqual(file.Assets[0], content) || !reflect.DeepEqual(file.Assets[1], moduleJarAsset("demo.other")) {
		t.Fatalf("compact module assets: %+v, %+v", file.Assets[0], file.Assets[1])
	}
	if content.Destination != "lib/modules/demo.content.jar" || !equalStrings(content.Inputs, "demo.content") || content.Mode != DefaultMode ||
		content.Kind != "file" || !content.ClassPath || content.Scope != pluginpack.PluginScope || content.Recipe.Writer.Manifest != defaultManifest || !content.Recipe.Writer.MergeEntities {
		t.Fatalf("module jar asset: %+v", content)
	}
	demo := file.Assets[2]
	if !equalStrings(demo.Inputs, "demo.main") || demo.Mode != DefaultMode || demo.Kind != "file" || !demo.ClassPath || demo.Scope != pluginpack.PluginScope {
		t.Fatalf("the defaults of a recipe asset: %+v", demo)
	}
	tool := file.Assets[3]
	if !equalStrings(tool.Inputs, "native") || tool.Mode != 0o755 || tool.ClassPath || tool.Scope != pluginpack.DistributionScope || tool.Recipe != nil {
		t.Fatalf("an explicit asset: %+v", tool)
	}
	if file.ReusableArtifacts[0].Mode != DefaultMode || !reflect.DeepEqual(file.ReusableArtifacts[0].Recipe, moduleJarRecipe("demo.content")) ||
		file.ReusableArtifacts[2].Mode != 0o755 || file.ReusableArtifacts[2].Recipe.Sources[0].Input != "demo.main" {
		t.Fatalf("reusable artifacts: %+v", file.ReusableArtifacts)
	}
	if operation := file.Operations[0]; operation.Kind != moduleFilterKind || operation.Input.Artifact != "raw" || operation.Manifest != "keep" || len(operation.Excludes) != 0 {
		t.Fatalf("the default operation kind: %+v", operation)
	}
}

func equalStrings(actual []string, expected ...string) bool {
	return reflect.DeepEqual(actual, expected)
}

func TestReadRefusesMalformedForms(t *testing.T) {
	for name, text := range map[string]string{
		"a module asset with another field":  plan(1, `{"module": "m", "destination": "lib/x.jar"}`),
		"a module asset at another mode":     plan(1, `{"module": "m", "mode": 493}`),
		"an asset without destination":       plan(1, `{"inputs": ["x"]}`),
		"an asset without inputs and recipe": plan(1, `{"destination": "bin/tool"}`),
		"an unknown asset field":             plan(1, `{"destination": "bin/tool", "inputs": ["x"], "producer": "remainder"}`),
		"a reusable artifact with both":      plan(1, `{"module": "m"}`, `"reusableArtifacts": [{"label": "l", "module": "m", "recipe": {"sources": [{"input": "m", "kind": "module", "filter": "module-v1"}]}}]`),
		"a recipe without sources":           plan(1, `{"destination": "lib/x.jar", "recipe": {"sources": []}}`),
		"a prepared manifest off a prepared source": plan(1, `{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "m", "kind": "module", "filter": "module-v1",
			"preparedManifest": {"sourceManifestPolicies": ["keep"]}}]}}`),
		"a Kotlin operation kind":             plan(1, `{"module": "m"}`, `"operations": [{"id": "n", "kind": "native-presigned", "input": {"artifact": "a"}, "output": "o", "manifest": "keep", "filter": "library"}]`),
		"a callback operation kind":           plan(1, `{"module": "m"}`, `"operations": [{"id": "n", "kind": "library-layout-patches", "output": "o", "manifest": "keep", "libraryLayout": {"any": 1}}]`),
		"a module-filter with a native field": plan(1, `{"module": "m"}`, `"operations": [{"id": "n", "input": {"artifact": "a"}, "output": "o", "manifest": "keep", "filter": "library"}]`),
		"a module-filter without input":       plan(1, `{"module": "m"}`, `"operations": [{"id": "n", "output": "o", "manifest": "keep"}]`),
		"a layout-assets with a primary input": plan(1, `{"module": "m"}`, `"operations": [{"id": "n", "kind": "layout-assets", "input": {"artifact": "a"}, "output": "o", "manifest": "keep",
			"layoutAssets": {"format": "tree", "assets": []}}]`),
		"a layout-assets with the drop manifest": plan(1, `{"module": "m"}`, `"operations": [{"id": "n", "kind": "layout-assets", "output": "o", "manifest": "drop",
			"layoutAssets": {"format": "tree", "assets": []}}]`),
		"a preparation without signature": plan(1, `{"module": "m"}`, `"preparations": [{"id": "p", "inputs": [], "outputs": ["o"]}]`),
		"a duplicate key":                 `{"version": 1, "version": 1, "plugin": "demo", "variant": "", "layoutSignature": "s", "assets": []}`,
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := readPlan(t, text); err == nil {
				t.Fatalf("accepted %s", text)
			}
		})
	}
	if _, err := readPlan(t, plan(1, `{"module": "m"}`, `"operations": [{"id": "n", "kind": "native-presigned", "input": {"artifact": "a"}, "output": "o", "manifest": "keep", "filter": "library"}]`)); err == nil || !strings.Contains(err.Error(), "does not execute") {
		t.Fatalf("a Kotlin kind needs a clear refusal: %v", err)
	}
}

func derive(t *testing.T, text string, inputs pluginpack.Catalogue, version int) (*Derivation, error) {
	t.Helper()
	return Derive(mustReadPlan(t, text), inputs, "plugins/demo", []byte("<idea-plugin/>"), version)
}

func mustDerive(t *testing.T, text string, inputs pluginpack.Catalogue, version int) *Derivation {
	t.Helper()
	derivation, err := derive(t, text, inputs, version)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := pluginpack.Plan(derivation.Recipe, derivation.Catalogue); err != nil {
		t.Fatalf("the derived recipe does not plan: %v", err)
	}
	return derivation
}

const rtRecipe = `{"sources": [{"input": "demo.rt", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}`

func TestDeriveMatchesOwnershipByRecipeAndMode(t *testing.T) {
	derivation := mustDerive(t, plan(1, `{"module": "demo.content"}, {"destination": "lib/rt.jar", "recipe": `+rtRecipe+`},
		{"destination": "lib/rt-exec.jar", "recipe": `+rtRecipe+`, "mode": 493}, {"destination": "lib/rt-explicit.jar", "inputs": ["demo.rt"], "recipe": `+rtRecipe+`}`,
		`"reusableArtifacts": [{"label": "//demo:content.jar", "module": "demo.content"}, {"label": "//demo:rt.jar", "recipe": `+rtRecipe+`}]`),
		catalogue(fileArtifact("demo.rt")), 1)
	want := []pluginpack.Asset{
		{Destination: "lib/modules/demo.content.jar", Producer: "independent", Artifact: "//demo:content.jar"},
		{Destination: "lib/rt.jar", Producer: "independent", Artifact: "//demo:rt.jar"},
		{Destination: "lib/rt-exec.jar", Producer: "remainder"},
		{Destination: "lib/rt-explicit.jar", Producer: "independent", Artifact: "//demo:rt.jar"},
	}
	if !reflect.DeepEqual(derivation.Assets, want) || !reflect.DeepEqual(derivation.Recipe.Assets, want) {
		t.Fatalf("asset rows: %+v", derivation.Assets)
	}
	if len(derivation.Recipe.Operations) != 1 || derivation.Recipe.Operations[0].Destination != "lib/rt-exec.jar" || derivation.Recipe.Operations[0].Mode != 0o755 {
		t.Fatalf("operations: %+v", derivation.Recipe.Operations)
	}
	if _, err := derive(t, plan(1, `{"destination": "lib/rt.jar", "recipe": `+rtRecipe+`, "mode": 493}`,
		`"reusableArtifacts": [{"label": "//demo:rt.jar", "recipe": `+rtRecipe+`}]`), catalogue(fileArtifact("demo.rt")), 1); err == nil || !strings.Contains(err.Error(), "unused reusable artifacts") {
		t.Fatalf("an artifact at another mode must stay unused: %v", err)
	}
}

func TestDeriveRequiresTheExecutionVersionOfTheAssets(t *testing.T) {
	excludedTree := `{"destination": "lib/tree", "inputs": ["tree"], "kind": "tree", "classPath": false}`
	distribution := `{"destination": "bin/run", "inputs": ["run"], "mode": 493, "classPath": false, "scope": "distribution"}`
	for name, scenario := range map[string]struct {
		text    string
		inputs  pluginpack.Catalogue
		version int
	}{
		"files":        {plan(1, `{"destination": "bin/tool", "inputs": ["tool"]}`), catalogue(fileArtifact("tool")), 1},
		"a tree":       {plan(2, excludedTree), catalogue(directoryArtifact("tree")), 2},
		"distribution": {plan(3, distribution), catalogue(fileArtifact("run")), 3},
	} {
		t.Run(name, func(t *testing.T) {
			if derivation := mustDerive(t, scenario.text, scenario.inputs, scenario.version); derivation.Recipe.Version != scenario.version {
				t.Fatalf("version %d", derivation.Recipe.Version)
			}
			for _, wrong := range []int{1, 2, 3} {
				if wrong == scenario.version {
					continue
				}
				if _, err := derive(t, scenario.text, scenario.inputs, wrong); err == nil || !strings.Contains(err.Error(), "stale execution version") {
					t.Fatalf("declared version %d: %v", wrong, err)
				}
			}
		})
	}
	if _, err := derive(t, plan(2, `{"destination": "bin/tool", "inputs": ["tool"]}`), catalogue(fileArtifact("tool")), 2); err == nil || !strings.Contains(err.Error(), "stale execution version") {
		t.Fatalf("a file version above the assets: %v", err)
	}
}

func jarManifests(t *testing.T, derivation *Derivation, destination string) []string {
	t.Helper()
	for _, operation := range derivation.Recipe.Operations {
		if operation.Destination != destination {
			continue
		}
		var manifests []string
		for _, source := range operation.Sources {
			manifests = append(manifests, source.Manifest)
		}
		return manifests
	}
	t.Fatalf("no operation at %s", destination)
	return nil
}

func TestDeriveCountsMeaningfulSourcesForTheManifest(t *testing.T) {
	inputs := catalogue(fileArtifact("demo.main"), fileArtifact("intellij.libraries.foo"), fileArtifact("@lib//:two/a.jar"), fileArtifact("@lib//:two/b.jar"), fileArtifact("raw"))
	inputs.Libraries = []pluginpack.Library{{ID: "@lib//:two", Files: []pluginpack.Reference{{Artifact: "@lib//:two/a.jar"}, {Artifact: "@lib//:two/b.jar"}}}}
	two := `{"input": "@lib//:two", "kind": "library", "filter": "library-v1", "expansion": ["@lib//:two/a.jar", "@lib//:two/b.jar"]}`
	derivation := mustDerive(t, plan(1,
		`{"destination": "lib/one.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
		{"destination": "lib/library.jar", "recipe": {"sources": [`+two+`]}},
		{"destination": "lib/lib-module.jar", "recipe": {"sources": [{"input": "intellij.libraries.foo", "kind": "module", "filter": "module-v1"}, {"input": "demo.main", "kind": "module", "filter": "module-v1", "options": ["manifest=drop"]}]}},
		{"destination": "lib/option.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1", "options": ["lib-module"]}, {"input": "intellij.libraries.foo", "kind": "module", "filter": "module-v1"}]}},
		{"destination": "lib/counted.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared", "preparedManifest": {"originalMeaningfulSourceCount": 2, "sourceManifestPolicies": ["keep"]}}]}},
		{"destination": "lib/uncounted.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared", "preparedManifest": {"sourceManifestPolicies": ["keep"]}}]}}`,
		`"preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"}],
		"operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "keep", "excludes": []}]`), inputs, 1)
	for destination, want := range map[string][]string{
		"lib/one.jar":        {"keep"},
		"lib/library.jar":    {"drop", "drop"},
		"lib/lib-module.jar": {"keep", "drop"},
		"lib/option.jar":     {"drop", "drop"},
		"lib/counted.jar":    {"keep"},
		"lib/uncounted.jar":  {"keep"},
	} {
		if got := jarManifests(t, derivation, destination); !reflect.DeepEqual(got, want) {
			t.Errorf("%s: manifests %v, want %v", destination, got, want)
		}
	}
	library := derivation.Recipe.Operations[1].Sources
	if len(library) != 2 || library[0].Kind != "archive" || library[0].Input.Artifact != "@lib//:two/a.jar" || library[1].Input.Artifact != "@lib//:two/b.jar" || library[0].Filter != "library" {
		t.Fatalf("library expansion: %+v", library)
	}
	if len(derivation.Catalogue.Libraries) != 0 || len(derivation.Catalogue.Artifacts) != len(inputs.Artifacts) {
		t.Fatalf("the remainder catalogue keeps the artifacts and drops the libraries: %+v", derivation.Catalogue)
	}
}

func TestDeriveCompilesEveryOperationKind(t *testing.T) {
	excluded := false
	inputs := catalogue(fileArtifact("raw"), fileArtifact("descriptor"), fileArtifact("native"), directoryArtifact("dsls"), fileArtifact("archive"), directoryArtifact("properties"))
	tree := `{"id": "tree", "kind": "layout-assets", "inputs": [{"artifact": "archive"}], "output": "tree:output", "manifest": "keep",
		"layoutAssets": {"format": "tree", "root": "payload", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "archive-tree", "stripComponents": 1}}]}}`
	entries := `{"id": "entries", "kind": "layout-assets", "inputs": [{"artifact": "properties"}], "output": "entries:output", "manifest": "keep",
		"layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "tree-map", "mappings": [{"pattern": "*.properties", "destination": "messages"}, {}],
			"excludes": ["*.pyc", "**/*.pyc"], "directoryExcludes": ["tests", "**/tests"]}}]}}`
	derivation := mustDerive(t, plan(2,
		`{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"},
			{"input": "descriptor", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]}], "writer": {"manifest": "drop", "directoryEntries": true}}},
		{"destination": "lib/l10n.jar", "recipe": {"sources": [{"input": "entries:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "keep"}}},
		{"destination": "payload", "inputs": ["tree:output"], "kind": "tree", "classPath": false, "normalizeTreeModes": true},
		{"destination": "lib/standardDsls", "inputs": ["dsls"], "kind": "tree", "classPath": false},
		{"destination": "bin/tool", "inputs": ["native"], "mode": 493},
		{"destination": "bin/current", "inputs": [], "symlinkTarget": "./tool"},
		{"destination": "lib/empty", "inputs": [], "kind": "directory", "mode": 493}`,
		`"preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"},
			{"id": "tree", "inputs": ["archive"], "outputs": ["tree:output"], "modelSignature": "y"},
			{"id": "entries", "inputs": ["properties"], "outputs": ["entries:output"], "modelSignature": "z"}]`,
		`"operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "keep", "excludes": ["drop/**"]}, `+tree+`, `+entries+`]`), inputs, 2)
	want := []pluginpack.Operation{
		{Kind: "jar", Destination: "lib/main.jar", Mode: DefaultMode, Options: &pluginpack.JarOptions{Directories: "all"}, Sources: []pluginpack.Source{
			{Kind: "archive", Input: &pluginpack.Reference{Artifact: "raw"}, Filter: "module", Excludes: []string{"drop/**"}, Manifest: "keep"},
			{Kind: "entries", Manifest: "drop", Entries: []pluginpack.PreparedEntry{{Kind: "patch", Name: "META-INF/plugin.xml", Input: &pluginpack.Reference{Artifact: "descriptor"}}}}}},
		{Kind: "jar", Destination: "lib/l10n.jar", Mode: DefaultMode, Options: &pluginpack.JarOptions{Directories: "none"}, Sources: []pluginpack.Source{
			{Kind: "layout", Manifest: "keep", Layout: &pluginpack.LayoutAssets{Inputs: []pluginpack.Reference{{Artifact: "properties"}}, Assets: []pluginpack.LayoutAsset{{Sources: []int{0},
				Transform: &pluginpack.LayoutTransform{Kind: "tree-map", Mappings: []pluginpack.LayoutMapping{{Pattern: "*.properties", Destination: "messages"}, {}},
					Excludes: []string{"*.pyc", "**/*.pyc"}, DirectoryExcludes: []string{"tests", "**/tests"}}}}}}}},
		{Kind: "layout-tree", Destination: "payload", Mode: DefaultMode, Layout: &pluginpack.LayoutAssets{Inputs: []pluginpack.Reference{{Artifact: "archive"}},
			Assets: []pluginpack.LayoutAsset{{Sources: []int{0}, Transform: &pluginpack.LayoutTransform{Kind: "archive-tree", StripComponents: 1}}}}},
		{Kind: "copy-tree", Destination: "lib/standardDsls", Input: &pluginpack.Reference{Artifact: "dsls"}},
		{Kind: "copy", Destination: "bin/tool", Input: &pluginpack.Reference{Artifact: "native"}, Mode: 0o755},
		{Kind: "symlink", Destination: "bin/current", Target: "./tool"},
		{Kind: "directory", Destination: "lib/empty", Mode: 0o755},
	}
	if got, expected := mustJSON(t, derivation.Recipe.Operations), mustJSON(t, want); got != expected {
		t.Fatalf("operations differ:\n%s\n%s", got, expected)
	}
	rows := []pluginpack.Asset{
		{Destination: "lib/main.jar", Producer: "remainder"}, {Destination: "lib/l10n.jar", Producer: "remainder"},
		{Destination: "payload", Producer: "remainder", Kind: "tree", ClassPath: &excluded, NormalizeTreeModes: true},
		{Destination: "lib/standardDsls", Producer: "remainder", Kind: "tree", ClassPath: &excluded},
		{Destination: "bin/tool", Producer: "remainder"}, {Destination: "bin/current", Producer: "remainder"},
		{Destination: "lib/empty", Producer: "remainder", Kind: "directory"},
	}
	if got, expected := mustJSON(t, derivation.Assets), mustJSON(t, rows); got != expected {
		t.Fatalf("asset rows differ:\n%s\n%s", got, expected)
	}
}

func TestDeriveWritesThePlanScopeClassPath(t *testing.T) {
	inputs := catalogue(fileArtifact("demo.main"), fileArtifact("run"))
	derivation := mustDerive(t, plan(3,
		`{"destination": "lib/util.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
		{"destination": "lib/demo.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
		{"destination": "lib/modules/demo.main.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}},
		{"destination": "lib/hidden.jar", "recipe": {"sources": [{"input": "demo.main", "kind": "module", "filter": "module-v1"}]}, "classPath": false},
		{"destination": "lib/data.txt", "inputs": ["run"]},
		{"destination": "bin/run", "inputs": ["run"], "mode": 493, "classPath": false, "scope": "distribution"},
		{"module": "demo.content"}`,
		`"reusableArtifacts": [{"label": "//demo:content.jar", "module": "demo.content"}]`), inputs, 3)
	want, err := pluginclasspath.Record("demo", []byte("<idea-plugin/>"), []string{"lib/util.jar", "lib/demo.jar"})
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(derivation.ClassPath, want) {
		t.Fatalf("classpath record = %x, want %x", derivation.ClassPath, want)
	}
	if !bytes.Contains(want, []byte("lib/demo.jar\x00\x0clib/util.jar")) {
		t.Fatalf("the plugin jar goes first: %x", want)
	}
}

func TestDeriveRefusesWhatTheGoPackerDoesNotExecute(t *testing.T) {
	filterInputs := catalogue(fileArtifact("raw"))
	for name, scenario := range map[string]struct {
		text    string
		inputs  pluginpack.Catalogue
		message string
	}{
		"a layout format file": {plan(1, `{"destination": "lib/x.txt", "inputs": ["layout:output"], "classPath": false}`,
			`"preparations": [{"id": "layout", "inputs": ["source"], "outputs": ["layout:output"], "modelSignature": "x"}],
			"operations": [{"id": "layout", "kind": "layout-assets", "inputs": [{"artifact": "source"}], "output": "layout:output", "manifest": "keep",
				"layoutAssets": {"format": "file", "root": "lib/x.txt", "assets": [{"destination": "", "sources": [0]}]}}]`),
			catalogue(fileArtifact("source")), "does not execute"},
		"a gzip-xml-archive transform": {plan(1, `{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "layout:output", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "drop"}}}`,
			`"preparations": [{"id": "layout", "inputs": ["source"], "outputs": ["layout:output"], "modelSignature": "x"}],
			"operations": [{"id": "layout", "kind": "layout-assets", "inputs": [{"artifact": "source"}], "output": "layout:output", "manifest": "keep",
				"layoutAssets": {"format": "entries", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "gzip-xml-archive"}}]}}]`),
			catalogue(fileArtifact("source")), "does not execute"},
		"a prepared source without a producer": {plan(1, filteredJar), filterInputs, "has no producer in the plan file"},
		"a copy of a Go-executed output":       {plan(1, `{"destination": "lib/x.jar", "inputs": ["filtered"]}`, moduleFilterSection), filterInputs, "requires a prepared jar source"},
		"an operation no asset needs":          {plan(1, `{"destination": "bin/tool", "inputs": ["raw"]}`, moduleFilterSection), filterInputs, "unexpected preparation operation"},
		"a preparation without operation": {plan(1, filteredJar, `"preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x"}]`),
			filterInputs, "missing preparation operation"},
		"stale preparation inputs":  {plan(1, filteredJar, moduleFilterSection), catalogue(fileArtifact("raw"), fileArtifact("extra")), "stale preparation inputs"},
		"a missing catalogue input": {plan(1, filteredJar, moduleFilterSection), catalogue(), "stale preparation inputs"},
		"a definition with other inputs": {plan(1, filteredJar, `"preparations": [{"id": "filter", "inputs": ["raw", "more"], "outputs": ["filtered"], "modelSignature": "x"}],
			"operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "drop"}]`), filterInputs, "must declare exactly inputs"},
		"an always-run preparation": {plan(1, filteredJar, `"preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"], "modelSignature": "x", "alwaysRun": true}],
			"operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "drop"}]`), filterInputs, "always runs"},
		"a tree with another root": {plan(2, `{"destination": "other", "inputs": ["layout:output"], "kind": "tree", "classPath": false}`,
			`"preparations": [{"id": "layout", "inputs": ["source"], "outputs": ["layout:output"], "modelSignature": "x"}],
			"operations": [{"id": "layout", "kind": "layout-assets", "inputs": [{"artifact": "source"}], "output": "layout:output", "manifest": "keep",
				"layoutAssets": {"format": "tree", "root": "payload", "assets": [{"destination": "", "sources": [0], "transform": {"kind": "archive-tree"}}]}}]`),
			catalogue(fileArtifact("source")), "requires one tree asset"},
		"a tree of a file input": {plan(2, `{"destination": "tree", "inputs": ["source"], "kind": "tree", "classPath": false}`), catalogue(fileArtifact("source")), "requires a directory artifact"},
		"a catalogue with tree metadata": {plan(2, `{"destination": "tree", "inputs": ["source"], "kind": "tree", "classPath": false}`),
			catalogue(pluginpack.Artifact{ID: "source", Kind: "directory", Root: "inputs/source", Tree: &pluginpack.OwnedTree{Version: 2, Artifact: "source", Plugin: "demo", LayoutSignature: "signature", Entries: []filemetadata.Entry{}}}),
			"prepared tree metadata"},
		"a prepared source under the default manifest": {plan(1, `{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"}]}}`,
			moduleFilterSection), filterInputs, "explicit manifest policy"},
		"stale prepared manifest policies": {plan(1, `{"destination": "lib/main.jar", "recipe": {"sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared",
			"preparedManifest": {"sourceManifestPolicies": ["keep"]}}]}}`, moduleFilterSection), filterInputs, "stale manifest policies"},
		"a stale library expansion": {plan(1, `{"destination": "lib/x.jar", "recipe": {"sources": [{"input": "lib", "kind": "library", "filter": "library-v1", "expansion": ["b", "a"]}]}}`),
			pluginpack.Catalogue{Version: 1, Artifacts: []pluginpack.Artifact{fileArtifact("a"), fileArtifact("b")}, Libraries: []pluginpack.Library{{ID: "lib", Files: []pluginpack.Reference{{Artifact: "a"}, {Artifact: "b"}}}}},
			"stale file order"},
		"a tree at another mode": {plan(2, `{"destination": "tree", "inputs": ["source"], "kind": "tree", "classPath": false, "mode": 493}`), catalogue(directoryArtifact("source")), "no mode override"},
		"a link that escapes":    {plan(1, `{"destination": "bin/current", "inputs": [], "symlinkTarget": "../../tool"}`), catalogue(), "escapes the plugin"},
	} {
		t.Run(name, func(t *testing.T) {
			_, err := derive(t, scenario.text, scenario.inputs, mustReadPlan(t, scenario.text).Version)
			if err == nil || !strings.Contains(err.Error(), scenario.message) {
				t.Fatalf("expected %q, got %v", scenario.message, err)
			}
		})
	}
}

func mustJSON(t *testing.T, value any) string {
	t.Helper()
	data, err := json.MarshalIndent(value, "", " ")
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}
