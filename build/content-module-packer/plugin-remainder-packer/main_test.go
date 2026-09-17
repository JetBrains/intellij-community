package main

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/pluginclasspath"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

func TestArguments(t *testing.T) {
	for _, arguments := range [][]string{
		nil, {"--unknown=value"}, {"--recipe"}, {"--recipe="}, {"--recipe=one", "--recipe=two"},
		{"--projection=plan.json", "--recipe=recipe.json"},
		{"--projection=plan.json"},
		{"--recipe=recipe.json", "--catalogue=catalogue.json", "--output-dir=out", "--inventory=inventory.json", "--assets=assets.json"},
		{"--projection=plan.json", "--input-catalogue=catalogue.json", "--classpath-descriptor=descriptor.xml", "--plugin-directory=plugins/x",
			"--execution-version=1", "--output-dir=out", "--inventory=inventory.json", "--assets=assets.json", "--classpath=classpath.txt", "--catalogue=c.json"},
		{"--projection=plan.json", "--input-catalogue=catalogue.json", "--classpath-descriptor=descriptor.xml", "--plugin-directory=plugins/x",
			"--execution-version=4", "--output-dir=out", "--inventory=inventory.json", "--assets=assets.json", "--classpath=classpath.txt"},
	} {
		var output, errors bytes.Buffer
		if code := run(arguments, &output, &errors); code != 2 || errors.Len() == 0 || output.Len() != 0 {
			t.Fatalf("arguments %v: code=%d, output=%q, errors=%q", arguments, code, &output, &errors)
		}
	}
}

func TestRun(t *testing.T) {
	for _, stale := range []bool{false, true} {
		root := t.TempDir()
		assets := []pluginpack.Asset{{Destination: "lib/empty.jar", Producer: "remainder"}}
		recipe := pluginpack.Recipe{Version: 1, Plugin: "example", LayoutSignature: "signature", Assets: assets,
			Operations: []pluginpack.Operation{{Kind: "jar", Destination: "lib/empty.jar", Options: &pluginpack.JarOptions{Directories: "none"}}}}
		catalogue := pluginpack.Catalogue{Version: 1}
		if stale {
			catalogue.Artifacts = []pluginpack.Artifact{{ID: "unused", Kind: "file", Root: filepath.Join(root, "unused.jar")}}
		}
		var arguments []string
		for name, document := range map[string]any{"recipe": recipe, "catalogue": catalogue} {
			file := filepath.Join(root, name+".json")
			data, err := json.Marshal(document)
			if err != nil {
				t.Fatal(err)
			}
			if err := os.WriteFile(file, data, 0o644); err != nil {
				t.Fatal(err)
			}
			arguments = append(arguments, "--"+name+"="+file)
		}
		arguments = append(arguments, "--output-dir="+filepath.Join(root, "payload"), "--inventory="+filepath.Join(root, "inventory.json"))
		var output, errors bytes.Buffer
		code := run(arguments, &output, &errors)
		if stale {
			if code != 1 || errors.Len() == 0 || output.Len() != 0 {
				t.Fatalf("unused catalogue input: code=%d, output=%q, errors=%q", code, &output, &errors)
			}
		} else {
			if code != 0 || output.Len() == 0 || errors.Len() != 0 {
				t.Fatalf("run: code=%d, output=%q, errors=%q", code, &output, &errors)
			}
			if _, err := os.Stat(filepath.Join(root, "payload/lib/empty.jar")); err != nil {
				t.Fatal(err)
			}
		}
	}
}

// projectionPlan is a plan file with one remainder jar, one independent module jar, and one raw file copy.
const projectionPlan = `{
  "version": 1, "plugin": "example", "variant": "", "layoutSignature": "signature",
  "assets": [
    {"destination": "lib/example.jar", "recipe": {"sources": [{"input": "example.main", "kind": "module", "filter": "module-v1"}], "writer": {"mergeEntities": true}}},
    {"module": "example.content"},
    {"destination": "bin/tool", "inputs": ["tool"], "mode": 493, "classPath": false}
  ],
  "reusableArtifacts": [{"label": "//example:content.jar", "module": "example.content"}]
}`

func writeProjectionFixture(t *testing.T, root, plan string) []string {
	t.Helper()
	jar := filepath.Join(root, "main.jar")
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	entry, err := writer.Create("com/example/Main.class")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := entry.Write([]byte("class")); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	catalogue := pluginpack.Catalogue{Version: 1, Artifacts: []pluginpack.Artifact{
		{ID: "example.main", Kind: "file", Root: jar}, {ID: "tool", Kind: "file", Root: filepath.Join(root, "tool")}}}
	data, err := json.Marshal(catalogue)
	if err != nil {
		t.Fatal(err)
	}
	for name, content := range map[string][]byte{"main.jar": buffer.Bytes(), "tool": []byte("tool"), "plan.json": []byte(plan),
		"catalogue.json": data, "descriptor.xml": []byte("<idea-plugin/>")} {
		if err := os.WriteFile(filepath.Join(root, name), content, 0o644); err != nil {
			t.Fatal(err)
		}
	}
	return []string{"--projection=" + filepath.Join(root, "plan.json"), "--input-catalogue=" + filepath.Join(root, "catalogue.json"),
		"--classpath-descriptor=" + filepath.Join(root, "descriptor.xml"), "--plugin-directory=plugins/example", "--execution-version=1",
		"--output-dir=" + filepath.Join(root, "payload"), "--inventory=" + filepath.Join(root, "inventory.json"),
		"--assets=" + filepath.Join(root, "assets.json"), "--classpath=" + filepath.Join(root, "plugin-classpath.txt")}
}

func TestProjectionRunWritesThePluginTheAssetsAndTheClassPath(t *testing.T) {
	root := t.TempDir()
	arguments := writeProjectionFixture(t, root, projectionPlan)
	var output, errors bytes.Buffer
	if code := run(arguments, &output, &errors); code != 0 || output.Len() == 0 || errors.Len() != 0 {
		t.Fatalf("run: code=%d, output=%q, errors=%q", code, &output, &errors)
	}
	if _, err := os.Stat(filepath.Join(root, "payload/lib/example.jar")); err != nil {
		t.Fatal(err)
	}
	if info, err := os.Stat(filepath.Join(root, "payload/bin/tool")); err != nil || info.Mode().Perm() != 0o755 {
		t.Fatalf("copied tool: %v, %v", info, err)
	}
	if _, err := os.Lstat(filepath.Join(root, "payload/lib/modules/example.content.jar")); !os.IsNotExist(err) {
		t.Fatalf("the independent jar entered the remainder: %v", err)
	}
	assets, err := os.ReadFile(filepath.Join(root, "assets.json"))
	if err != nil {
		t.Fatal(err)
	}
	var rows []pluginpack.Asset
	if err := json.Unmarshal(assets, &rows); err != nil {
		t.Fatal(err)
	}
	excluded := false
	want := []pluginpack.Asset{{Destination: "lib/example.jar", Producer: "remainder"},
		{Destination: "lib/modules/example.content.jar", Producer: "independent", Artifact: "//example:content.jar"},
		{Destination: "bin/tool", Producer: "remainder", ClassPath: &excluded}}
	if got, expected := mustJSON(t, rows), mustJSON(t, want); got != expected {
		t.Fatalf("asset rows differ:\n%s\n%s", got, expected)
	}
	classpath, err := os.ReadFile(filepath.Join(root, "plugin-classpath.txt"))
	if err != nil {
		t.Fatal(err)
	}
	expected, err := pluginclasspath.Record("example", []byte("<idea-plugin/>"), []string{"lib/example.jar"})
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(classpath, expected) {
		t.Fatalf("classpath record = %x, want %x", classpath, expected)
	}
}

func TestProjectionRunRefusesAKotlinPreparationAndAStaleVersion(t *testing.T) {
	kotlinPlan := strings.Replace(projectionPlan, `"reusableArtifacts"`, `"preparations": [{"id": "native", "inputs": ["tool"], "outputs": ["native:output"], "modelSignature": "x"}],
  "operations": [{"id": "native", "kind": "native-presigned", "input": {"artifact": "tool"}, "output": "native:output", "manifest": "keep", "filter": "library"}],
  "reusableArtifacts"`, 1)
	kotlinPlan = strings.Replace(kotlinPlan, `"inputs": ["tool"], "mode": 493`, `"inputs": ["native:output"], "mode": 493`, 1)
	for name, scenario := range map[string]struct {
		plan    string
		version string
		message string
	}{
		"a Kotlin operation kind": {kotlinPlan, "1", "does not execute"},
		"a stale version":         {projectionPlan, "2", "stale execution version"},
	} {
		t.Run(name, func(t *testing.T) {
			root := t.TempDir()
			arguments := writeProjectionFixture(t, root, scenario.plan)
			arguments[4] = "--execution-version=" + scenario.version
			var output, errors bytes.Buffer
			if code := run(arguments, &output, &errors); code != 1 || !strings.Contains(errors.String(), scenario.message) || output.Len() != 0 {
				t.Fatalf("code=%d, output=%q, errors=%q", code, &output, &errors)
			}
			for _, file := range []string{"payload", "inventory.json", "assets.json", "plugin-classpath.txt"} {
				if _, err := os.Lstat(filepath.Join(root, file)); !os.IsNotExist(err) {
					t.Fatalf("%s was written by a failed run: %v", file, err)
				}
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
