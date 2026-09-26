package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

const testPlan = `{
  "version": 2,
  "plugin": "p.main",
  "variant": "",
  "layoutSignature": "signature",
  "assets": [
    {"module": "p.content"},
    {"destination": "lib/modules/p.natives.jar", "recipe": {"sources": [
      {"input": "p.natives", "kind": "module", "filter": "module-v1"},
      {"input": "@lib//:natives", "kind": "library", "filter": "library-v1"}
    ], "writer": {"mergeEntities": true, "nativeLib": "natives"}}},
    {"destination": "lib/p.jar", "recipe": {"sources": [
      {"input": "descriptor:p.main", "kind": "file", "filter": "none", "entry": "META-INF/plugin.xml", "options": ["patch"]},
      {"input": "module-filter:p.main:output", "kind": "prepared", "filter": "prepared"},
      {"input": "p.other", "kind": "module", "filter": "module-v1"},
      {"input": "@lib//:a", "kind": "library", "filter": "library-v1"}
    ], "writer": {"manifest": "drop", "mergeEntities": true}}},
    {"destination": "lib/resources.jar", "recipe": {"sources": [
      {"input": "layout-assets:0:output", "kind": "prepared", "filter": "prepared"}
    ], "writer": {"manifest": "drop", "mergeEntities": true}}},
    {"destination": "js", "inputs": ["module-resource:0:source"], "kind": "tree", "classPath": false}
  ],
  "preparations": [
    {"id": "module-filter:p.main", "inputs": ["p.main"], "outputs": ["module-filter:p.main:output"], "modelSignature": "m"}
  ],
  "operations": [
    {"id": "module-filter:p.main", "input": {"artifact": "p.main"}, "output": "module-filter:p.main:output", "manifest": "keep", "excludes": ["js/**"]}
  ]
}`

const testCatalogue = `{
  "version": 1,
  "artifacts": [
    {"id": "p.main", "kind": "file", "root": "bazel-out/bin/p/main.jar"},
    {"id": "@lib//:a/a1.jar", "kind": "file", "root": "external/lib+/a1.jar"},
    {"id": "@lib//:a/a2.jar", "kind": "file", "root": "external/lib+/a2.jar"}
  ],
  "libraries": [
    {"id": "@lib//:a", "files": [{"artifact": "@lib//:a/a1.jar"}, {"artifact": "@lib//:a/a2.jar"}]}
  ]
}`

func TestPlanPart(t *testing.T) {
	output := filepath.Join(t.TempDir(), "part.json")
	var stdout, stderr bytes.Buffer
	arguments := []string{
		"plan-part", "--plan=" + writeFile(t, "plan.json", testPlan), "--catalogue=" + writeFile(t, "catalogue.json", testCatalogue),
		"--independent-libraries=" + writeFile(t, "independent.json", `{"version": 1, "libraries": [{"library": "@@lib+//:natives", "jars": ["external/lib+/natives.jar"]}]}`),
		"--descriptor-module=p.main", "--plugin-directory=plugins/p", "--descriptor=bazel-out/bin/p/plugin.classpath.xml", "--output=" + output,
	}
	if code := run(arguments, &stdout, &stderr); code != 0 {
		t.Fatalf("exit = %d: %s", code, &stderr)
	}
	data, err := os.ReadFile(output)
	if err != nil {
		t.Fatal(err)
	}
	var actual part
	if err := json.Unmarshal(data, &actual); err != nil {
		t.Fatal(err)
	}
	expected := part{Version: partVersion, DescriptorModule: "p.main", Directory: "plugins/p", Order: pluginOrder, Descriptor: "bazel-out/bin/p/plugin.classpath.xml", Jars: []partJar{
		{Destination: "lib/modules/p.content.jar", Members: modules("p.content")},
		{Destination: "lib/modules/p.natives.jar", Members: append(modules("p.natives"), member{Library: "@lib//:natives", Jars: []string{"external/lib+/natives.jar"}})},
		{Destination: "lib/p.jar", Members: append(modules("p.main", "p.other"), member{Library: "@lib//:a", Jars: []string{"external/lib+/a1.jar", "external/lib+/a2.jar"}})},
	}}
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("part:\n  actual   %+v\n  expected %+v", actual, expected)
	}
	if !strings.HasSuffix(string(data), "}\n") {
		t.Fatalf("the part does not end with a newline: %q", data)
	}
}

func TestPlanPartRefusals(t *testing.T) {
	for _, test := range []struct {
		plan    string
		message string
	}{
		{strings.Replace(testPlan, `"@lib//:a", "kind": "library"`, `"@lib//:unknown", "kind": "library"`, 1), "neither the catalogue nor a reused jar"},
		{strings.Replace(testPlan, `{"destination": "lib/p.jar",`, `{"scope": "distribution", "destination": "lib/p.jar",`, 1), "distribution scope"},
		{strings.Replace(testPlan, `{"destination": "lib/p.jar",`, `{"destination": "p.jar",`, 1), "not under lib/"},
	} {
		var stdout, stderr bytes.Buffer
		arguments := []string{
			"plan-part", "--plan=" + writeFile(t, "plan.json", test.plan), "--catalogue=" + writeFile(t, "catalogue.json", testCatalogue),
			"--independent-libraries=" + writeFile(t, "independent.json", `{"version": 1, "libraries": [{"library": "@lib//:natives", "jars": ["external/lib+/natives.jar"]}]}`),
			"--descriptor-module=p.main", "--plugin-directory=plugins/p", "--descriptor=plugin.xml", "--output=" + filepath.Join(t.TempDir(), "part.json"),
		}
		if code := run(arguments, &stdout, &stderr); code == 0 || !strings.Contains(stderr.String(), test.message) {
			t.Errorf("exit %d, stderr %q, expected %q", code, &stderr, test.message)
		}
	}
	var stdout, stderr bytes.Buffer
	if code := run([]string{"plan-part", "--plan=plan.json"}, &stdout, &stderr); code == 0 || !strings.Contains(stderr.String(), "is required") {
		t.Errorf("exit %d, stderr %q", code, &stderr)
	}
}
