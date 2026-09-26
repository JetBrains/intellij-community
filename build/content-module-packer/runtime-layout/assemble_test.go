package main

import (
	"bytes"
	"os"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"
)

const testTargets = `{
  "modules": {
    "p.main": {"productionTargets": ["//p:main"], "moduleLibraries": {"b": {"target": "@lib//:p-main-b", "jars": ["external/lib+/b.jar"], "jarTargets": ["@lib//:b/b-1.0.jar"]}}},
    "p.other": {"moduleLibraries": {"shared": {"target": "@lib//:p-other-shared", "jars": ["external/lib+/shared.jar"], "jarTargets": ["@lib//:shared.jar"]}}}
  },
  "projectLibraries": {
    "a": {"target": "@lib//:a", "jars": ["external/lib+/a1.jar", "external/lib+/a2.jar"]},
    "alpha": {"target": "@lib//:alpha", "jars": ["external/lib+/alpha.jar"]},
    "zeta": {"target": "@lib//libs/zeta", "jars": ["external/lib+/zeta.jar"]},
    "shared1": {"target": "@lib//:shared1", "jars": ["external/lib+/shared.jar"], "jarTargets": ["@lib//:shared.jar"]},
    "shared2": {"target": "@lib//:shared2", "jars": ["external/lib+/shared.jar"], "jarTargets": ["@lib//:shared.jar"]}
  },
  "pluginDistributionTargets": {}
}`

func writeFile(t *testing.T, name, content string) string {
	t.Helper()
	file := filepath.Join(t.TempDir(), name)
	if err := os.WriteFile(file, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	return file
}

func testIndex(t *testing.T) *libraryIndex {
	t.Helper()
	index, err := readLibraryIndex(writeFile(t, "bazel-targets.json", testTargets))
	if err != nil {
		t.Fatal(err)
	}
	return index
}

func modules(names ...string) []member {
	members := make([]member, 0, len(names))
	for _, name := range names {
		members = append(members, member{Module: name})
	}
	return members
}

func entryModules(plugin pluginLayout) []string {
	var names []string
	for _, e := range plugin.Entries {
		if e.Kind == "module" {
			names = append(names, e.Name)
		}
	}
	return names
}

// The jars of `intellij.dev` as `dev_plugin` states them: its own jars in plan order, then the reused content module
// jars in label order. The expected order is the header that the Kotlin fragment writes for the plugin.
func TestReusedJarsFollowContentOrder(t *testing.T) {
	content := []string{
		"intellij.platform.statistics.devkit", "intellij.platform.statistics.devkit.backend", "intellij.platform.statistics.devkit.frontend",
		"intellij.platform.ide.ui.inspector", "intellij.dev.psiViewer", "intellij.dev.codeInsight", "intellij.dev.leakDetection",
		"intellij.dev.pluginLoading", "intellij.java.dev", "intellij.groovy.dev", "intellij.kotlin.dev", "intellij.php.dev", "intellij.dev.core",
	}
	reused := func(module string) partJar {
		return partJar{Destination: "lib/modules/" + module + ".jar", Members: modules(module), Reused: true}
	}
	p := &part{Version: partVersion, DescriptorModule: "intellij.dev", Directory: "plugins/dev", Order: pluginOrder, Descriptor: "plugin.xml", Jars: []partJar{
		{Destination: "lib/dev.jar", Members: modules("intellij.dev", "intellij.dev.psiViewer", "intellij.dev.codeInsight", "intellij.java.dev", "intellij.kotlin.dev")},
		{Destination: "lib/modules/intellij.php.dev.jar", Members: modules("intellij.php.dev")},
		reused("intellij.platform.ide.ui.inspector"),
		reused("intellij.platform.statistics.devkit.backend"),
		reused("intellij.platform.statistics.devkit.frontend"),
		reused("intellij.platform.statistics.devkit"),
		reused("intellij.dev.core"),
		reused("intellij.dev.leakDetection"),
		reused("intellij.dev.pluginLoading"),
		reused("intellij.groovy.dev"),
	}}
	var contentModules []contentModule
	for _, name := range content {
		contentModules = append(contentModules, contentModule{name: name})
	}
	result, err := assemble([]assembledPart{{part: p, content: contentModules}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	expected := []string{
		"intellij.platform.statistics.devkit", "intellij.platform.statistics.devkit.backend", "intellij.platform.statistics.devkit.frontend",
		"intellij.platform.ide.ui.inspector", "intellij.dev.psiViewer", "intellij.dev.codeInsight", "intellij.java.dev", "intellij.kotlin.dev",
		"intellij.dev", "intellij.dev.leakDetection", "intellij.dev.pluginLoading", "intellij.groovy.dev", "intellij.php.dev", "intellij.dev.core",
	}
	if actual := entryModules(result.Plugins[0]); !reflect.DeepEqual(actual, expected) {
		t.Fatalf("module order:\n  actual   %q\n  expected %q", actual, expected)
	}
	first := result.Plugins[0].Entries[0]
	if *first.Path != "plugins/dev/lib/modules/intellij.platform.statistics.devkit.jar" || *first.RelativeOutputFile != "modules/intellij.platform.statistics.devkit.jar" {
		t.Fatalf("first entry = %+v", first)
	}
}

// A plugin part keeps the plan order of its own jars, library jars included. A reused jar goes among the jars of the
// content pass, before the first jar of the layout pass. A library reports one entry for each of its files.
func TestPluginJarsKeepPlanOrder(t *testing.T) {
	p := &part{Version: partVersion, DescriptorModule: "p.main", Directory: "plugins/p", Order: pluginOrder, Descriptor: "plugin.xml", Jars: []partJar{
		{Destination: "lib/modules/p.content.jar", Members: modules("p.content")},
		{Destination: "lib/p.jar", Members: append(modules("p.main", "p.other"), member{Library: "@lib//:a", Jars: []string{"external/lib+/a1.jar", "external/lib+/a2.jar"}})},
		{Destination: "lib/b.jar", Members: []member{{Library: "@lib//:p-main-b", Jars: []string{"external/lib+/b.jar"}}}},
		{Destination: "lib/zeta.jar", Members: []member{{Library: "@lib//libs/zeta:zeta"}}},
		{Destination: "lib/alpha.jar", Members: []member{{Library: "@@lib+//:alpha", Jars: []string{"external/lib+/alpha.jar"}}}},
		{Destination: "lib/modules/p.reused.jar", Members: modules("p.reused"), Reused: true},
	}}
	result, err := assemble([]assembledPart{{part: p, content: []contentModule{{name: "p.content"}, {name: "p.reused"}}}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	var actual []string
	for _, e := range result.Plugins[0].Entries {
		actual = append(actual, e.Kind+":"+e.Name+"@"+*e.RelativeOutputFile)
	}
	expected := []string{
		"module:p.content@modules/p.content.jar",
		"module:p.reused@modules/p.reused.jar",
		"module:p.main@p.jar", "module:p.other@p.jar", "projectLibrary:a@p.jar", "projectLibrary:a@p.jar",
		"moduleLibrary:p.main@b.jar",
		"projectLibrary:zeta@zeta.jar",
		"projectLibrary:alpha@alpha.jar",
	}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("entries:\n  actual   %q\n  expected %q", actual, expected)
	}
}

// The platform states its jars in the order of its layout. A frontend-only plugin is not in the distribution, so its
// entries state no path.
func TestLayoutOrderAndFrontendOnlyPlugins(t *testing.T) {
	platform := &part{Version: partVersion, DescriptorModule: "intellij.idea.customization", Order: layoutOrder, Jars: []partJar{
		{Destination: "lib/util.jar", Members: modules("intellij.platform.util", "intellij.platform.util.base")},
		{Destination: "lib/app.jar", Members: modules("intellij.platform.ide.impl", "intellij.idea.customization")},
	}}
	frontend := &part{Version: partVersion, DescriptorModule: "intellij.frontend.split.customization", Order: layoutOrder, Jars: []partJar{
		{Destination: "lib/frontend.jar", Members: modules("intellij.frontend.split.customization")},
	}}
	result, err := assemble([]assembledPart{{part: platform}, {part: frontend, frontendOnly: true}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	if actual := entryModules(result.Plugins[0]); !reflect.DeepEqual(actual, []string{"intellij.platform.util", "intellij.platform.util.base", "intellij.platform.ide.impl", "intellij.idea.customization"}) {
		t.Fatalf("platform order = %q", actual)
	}
	if path := result.Plugins[0].Entries[2].Path; path == nil || *path != "lib/app.jar" {
		t.Fatalf("platform path = %v", path)
	}
	frontendPlugin := result.Plugins[1]
	if !frontendPlugin.AdditionalFrontendOnlyPlugin || frontendPlugin.Entries[0].Path != nil || *frontendPlugin.Entries[0].RelativeOutputFile != "frontend.jar" {
		t.Fatalf("frontend-only plugin = %+v", frontendPlugin)
	}
	if _, err := assemble([]assembledPart{{part: platform}, {part: platform}}, testIndex(t)); err == nil || !strings.Contains(err.Error(), "two parts") {
		t.Fatalf("a duplicate plugin was accepted: %v", err)
	}
}

func TestLibraryResolution(t *testing.T) {
	index := testIndex(t)
	for _, test := range []struct {
		m        member
		modules  []string
		expected jpsLibrary
	}{
		{member{Library: "@lib//:a"}, nil, jpsLibrary{projectLibraryKind, "a"}},
		{member{Library: "@@lib+//:a"}, nil, jpsLibrary{projectLibraryKind, "a"}},
		{member{Library: "@lib//libs/zeta"}, nil, jpsLibrary{projectLibraryKind, "zeta"}},
		{member{Library: "@lib//:p-main-b"}, nil, jpsLibrary{moduleLibraryKind, "p.main"}},
		{member{Library: "@@lib+//:b/b-1.0.jar", Jars: []string{"bazel-out/bin/external/lib+/b/b-1.0.jar"}}, nil, jpsLibrary{moduleLibraryKind, "p.main"}},
		{member{Library: "//unknown:label", Jars: []string{"external/lib+/a1.jar", "external/lib+/a2.jar"}}, nil, jpsLibrary{projectLibraryKind, "a"}},
		{member{Library: "@lib//:shared.jar"}, []string{"p.main", "p.other"}, jpsLibrary{moduleLibraryKind, "p.other"}},
		{member{Library: "//unknown:label", Jars: []string{"external/lib+/shared.jar"}}, []string{"p.other"}, jpsLibrary{moduleLibraryKind, "p.other"}},
	} {
		library, err := index.resolve(test.m, test.modules)
		if err != nil || library != test.expected {
			t.Errorf("resolve(%+v) = %+v, %v", test.m, library, err)
		}
	}
	for _, test := range []struct {
		m       member
		message string
	}{
		{member{Library: "//unknown:label"}, "states no jar"},
		{member{Library: "//unknown:label", Jars: []string{"external/lib+/other.jar"}}, "neither is its jar"},
		{member{Library: "//unknown:label", Jars: []string{"external/lib+/shared.jar"}}, "belongs to 3 libraries"},
		{member{Library: "@lib//:shared.jar"}, "and 0 of them are module libraries"},
		{member{Library: "//unknown:label", Jars: []string{"external/lib+/a1.jar", "external/lib+/alpha.jar"}}, "different libraries"},
	} {
		if _, err := index.resolve(test.m, []string{"p.main"}); err == nil || !strings.Contains(err.Error(), test.message) {
			t.Errorf("resolve(%+v) error = %v, expected %q", test.m, err, test.message)
		}
	}
}

func TestNormalizeLabel(t *testing.T) {
	for input, expected := range map[string]string{
		"@@lib+//:x":                 "@lib//:x",
		"@@community+//platform/x:y": "@community//platform/x:y",
		"@@//build:x":                "//build:x",
		"@lib//:x":                   "@lib//:x",
		"//plugins/p":                "//plugins/p:p",
		"@lib//libs/zeta":            "@lib//libs/zeta:zeta",
	} {
		if actual := normalizeLabel(input); actual != expected {
			t.Errorf("normalizeLabel(%q) = %q, expected %q", input, actual, expected)
		}
	}
}

func TestContentOrderOfDescriptor(t *testing.T) {
	descriptor := writeFile(t, "plugin.xml", `<idea-plugin>
  <id>p</id>
  <content namespace="jetbrains">
    <module name="p.first" loading="required"><![CDATA[<idea-plugin><content><module name="nested"/></content></idea-plugin>]]></module>
    <module name="p.main/descriptor.xml"/>
  </content>
  <extensions><content><module name="not.content"/></content></extensions>
  <content>
    <module name="p.second"/>
  </content>
</idea-plugin>`)
	content, err := readContentOrder(descriptor)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(content, []contentModule{{name: "p.first", loading: "required"}, {name: "p.second"}}) {
		t.Fatalf("content = %+v", content)
	}
}

// The layout file keeps the field order and the formatting of the Kotlin serialization, with a final newline.
func TestAssembleCommand(t *testing.T) {
	directory := t.TempDir()
	descriptor := filepath.Join(directory, "plugin.xml")
	if err := os.WriteFile(descriptor, []byte(`<idea-plugin><content><module name="p.content"/></content></idea-plugin>`), 0o644); err != nil {
		t.Fatal(err)
	}
	partFile := writeFile(t, "part.json", `{"version":1,"descriptorModule":"p.main","directory":"plugins/p","order":"plugin","descriptor":"`+descriptor+`",`+
		`"jars":[{"destination":"lib/p.jar","members":[{"module":"p.main"}]},{"destination":"lib/modules/p.content.jar","members":[{"module":"p.content"}],"reused":true}]}`)
	targets := writeFile(t, "bazel-targets.json", testTargets)
	output := filepath.Join(directory, "layout.json")
	var stdout, stderr bytes.Buffer
	if code := run([]string{"--part=" + partFile, "--bazel-targets=" + targets, "--output=" + output}, &stdout, &stderr); code != 0 {
		t.Fatalf("exit = %d: %s", code, &stderr)
	}
	actual, err := os.ReadFile(output)
	if err != nil {
		t.Fatal(err)
	}
	expected := `{
  "version": 1,
  "plugins": [
    {
      "descriptorModule": "p.main",
      "additionalFrontendOnlyPlugin": false,
      "entries": [
        {
          "kind": "module",
          "name": "p.content",
          "path": "plugins/p/lib/modules/p.content.jar",
          "relativeOutputFile": "modules/p.content.jar"
        },
        {
          "kind": "module",
          "name": "p.main",
          "path": "plugins/p/lib/p.jar",
          "relativeOutputFile": "p.jar"
        }
      ]
    }
  ]
}
`
	if string(actual) != expected {
		t.Fatalf("layout:\n%s", actual)
	}
}

func TestInvalidInput(t *testing.T) {
	targets := writeFile(t, "bazel-targets.json", testTargets)
	for _, test := range []struct {
		part    string
		message string
	}{
		{`{"version":2,"descriptorModule":"p","order":"layout","jars":[]}`, "version 2"},
		{`{"version":1,"descriptorModule":"p","order":"plugin","jars":[]}`, "names no descriptor"},
		{`{"version":1,"descriptorModule":"p","order":"sorted","jars":[]}`, "has the order"},
		{`{"version":1,"descriptorModule":"p","order":"layout","jars":[{"destination":"p.jar","members":[{"module":"p"}]}]}`, "not a jar under lib/"},
		{`{"version":1,"descriptorModule":"p","order":"layout","jars":[{"destination":"lib/p.jar","members":[]}]}`, "merges nothing"},
		{`{"version":1,"descriptorModule":"p","order":"layout","jars":[{"destination":"lib/p.jar","members":[{"module":"p","library":"@lib//:a"}]}]}`, "one module or one library"},
		{`{"version":1,"descriptorModule":"p","order":"layout","extra":true,"jars":[]}`, "unknown field"},
	} {
		var stdout, stderr bytes.Buffer
		arguments := []string{"--part=" + writeFile(t, "part.json", test.part), "--bazel-targets=" + targets, "--output=" + filepath.Join(t.TempDir(), "layout.json")}
		if code := run(arguments, &stdout, &stderr); code == 0 || !strings.Contains(stderr.String(), test.message) {
			t.Errorf("part %s: exit %d, stderr %q, expected %q", test.part, code, &stderr, test.message)
		}
	}
	for _, test := range []struct {
		arguments []string
		message   string
	}{
		{[]string{"--bazel-targets=" + targets, "--output=out.json"}, "--part is required"},
		{[]string{"--part=a.json", "--output=out.json"}, "--bazel-targets is required"},
		{[]string{"--part=a.json", "--bazel-targets=" + targets, "--bazel-targets=" + targets, "--output=out.json"}, "at most once"},
		{[]string{"--unknown=x"}, "unknown option"},
		{[]string{"part.json"}, "--key=value"},
	} {
		var stdout, stderr bytes.Buffer
		if code := run(test.arguments, &stdout, &stderr); code == 0 || !strings.Contains(stderr.String(), test.message) {
			t.Errorf("%q: exit %d, stderr %q, expected %q", test.arguments, code, &stderr, test.message)
		}
	}
}

// A content module at a custom path is not placed by the content pass, so its jar starts the layout pass, and a reused
// jar goes before it. An embedded content module is placed at `lib/<module>.jar`, and a frontend member at
// `lib/<main>-frontend.jar`.
func TestCustomPathContentModules(t *testing.T) {
	p := &part{Version: partVersion, DescriptorModule: "p.main", Directory: "plugins/p", Order: pluginOrder, Descriptor: "plugin.xml", Jars: []partJar{
		{Destination: "lib/modules/p.late.jar", Members: modules("p.late"), Reused: true},
		{Destination: "lib/p.embedded.jar", Members: modules("p.embedded")},
		{Destination: "lib/p-frontend.jar", Members: modules("p.frontend")},
		{Destination: "lib/p.jar", Members: modules("p.main", "p.shared")},
		{Destination: "lib/custom.jar", Members: modules("p.custom")},
	}}
	content := []contentModule{{name: "p.custom"}, {name: "p.embedded", loading: "embedded"}, {name: "p.frontend"}, {name: "p.shared"}, {name: "p.late"}}
	result, err := assemble([]assembledPart{{part: p, content: content}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	expected := []string{"p.embedded", "p.frontend", "p.shared", "p.main", "p.late", "p.custom"}
	if actual := entryModules(result.Plugins[0]); !reflect.DeepEqual(actual, expected) {
		t.Fatalf("module order:\n  actual   %q\n  expected %q", actual, expected)
	}
	// A reused jar of a module that the descriptor refused is left out.
	result, err = assemble([]assembledPart{{part: p, content: content[:4]}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	if actual := entryModules(result.Plugins[0]); slices.Contains(actual, "p.late") {
		t.Fatalf("a refused reused jar was kept: %q", actual)
	}
}

// A jar of project libraries starts the layout pass, so a reused jar goes before it. The generator makes each project
// library a module of the plugin header, so this order is in the bytes of the repository. A jar of module libraries
// does not end the content pass: the embedded content module after it keeps its place before the reused jar.
func TestProjectLibraryJarStartsTheLayoutPass(t *testing.T) {
	p := &part{Version: partVersion, DescriptorModule: "p.main", Directory: "plugins/p", Order: pluginOrder, Descriptor: "plugin.xml", Jars: []partJar{
		{Destination: "lib/modules/p.content.jar", Members: modules("p.content")},
		{Destination: "lib/b.jar", Members: []member{{Library: "@lib//:p-main-b"}}},
		{Destination: "lib/p.embedded.jar", Members: modules("p.embedded")},
		{Destination: "lib/alpha.jar", Members: []member{{Library: "@lib//:alpha"}}},
		{Destination: "lib/modules/p.reused.jar", Members: modules("p.reused"), Reused: true},
	}}
	content := []contentModule{{name: "p.content"}, {name: "p.embedded", loading: "embedded"}, {name: "p.reused"}}
	result, err := assemble([]assembledPart{{part: p, content: content}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	var actual []string
	for _, e := range result.Plugins[0].Entries {
		actual = append(actual, e.Kind+":"+e.Name)
	}
	expected := []string{"module:p.content", "moduleLibrary:p.main", "module:p.embedded", "module:p.reused", "projectLibrary:alpha"}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("entries:\n  actual   %q\n  expected %q", actual, expected)
	}
}

// A platform part orders its jars by its jar order file. A jar that the part lacks fails the assembly, and a jar that
// the file lacks is left out, because it is not in the platform layout.
func TestPlatformJarOrder(t *testing.T) {
	order := writeFile(t, "idea.platform-jars.txt", "nio-fs.jar\nutil.jar\next/platform-main.jar\n")
	p := &part{Version: partVersion, DescriptorModule: "intellij.idea.customization", Order: layoutOrder, JarOrder: order, Jars: []partJar{
		{Destination: "lib/ext/platform-main.jar", Members: modules("intellij.platform.main")},
		{Destination: "lib/nio-fs.jar", Members: modules("intellij.platform.core.nio.fs")},
		{Destination: "lib/util.jar", Members: modules("intellij.platform.util", "intellij.platform.util.base")},
	}}
	result, err := assemble([]assembledPart{{part: p}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	expected := []string{"intellij.platform.core.nio.fs", "intellij.platform.util", "intellij.platform.util.base", "intellij.platform.main"}
	if actual := entryModules(result.Plugins[0]); !reflect.DeepEqual(actual, expected) {
		t.Fatalf("module order = %q", actual)
	}
	if path := result.Plugins[0].Entries[3].Path; path == nil || *path != "lib/ext/platform-main.jar" {
		t.Fatalf("path = %v", path)
	}
	p.JarOrder = writeFile(t, "short.platform-jars.txt", "nio-fs.jar\nutil.jar\nextra.jar\n")
	if _, err := assemble([]assembledPart{{part: p}}, testIndex(t)); err == nil ||
		!strings.Contains(err.Error(), "does not pack: extra.jar") {
		t.Fatalf("an unknown jar was accepted: %v", err)
	}
	p.JarOrder = writeFile(t, "partial.platform-jars.txt", "util.jar\nnio-fs.jar\n")
	result, err = assemble([]assembledPart{{part: p}}, testIndex(t))
	if err != nil {
		t.Fatal(err)
	}
	if actual := entryModules(result.Plugins[0]); !reflect.DeepEqual(actual, []string{"intellij.platform.util", "intellij.platform.util.base", "intellij.platform.core.nio.fs"}) {
		t.Fatalf("a jar outside the order was kept: %q", actual)
	}
}
