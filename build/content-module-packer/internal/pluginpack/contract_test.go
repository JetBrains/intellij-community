package pluginpack

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestReadJSONRejectsAmbiguousDocuments(t *testing.T) {
	for _, content := range []string{
		`null`, `[]`, `{"version":1,"version":2}`, `{"version":1} {"version":2}`,
		`{"version":1,"unknown":true}`, `{"version":1,"operations":[{"kind":"jar","kind":"copy"}]}`,
		`{"version":1,"operations":[{"kind":"jar","options":{"directories":"none","unknown":1}}]}`,
		`{"version":1,`,
		"{\"plugin\":\"\xff\"}",
	} {
		t.Run(content, func(t *testing.T) {
			file := filepath.Join(t.TempDir(), "recipe.json")
			if err := os.WriteFile(file, []byte(content), 0o644); err != nil {
				t.Fatal(err)
			}
			var recipe Recipe
			if err := ReadJSON(file, &recipe); err == nil {
				t.Fatal("accepted an ambiguous contract")
			}
		})
	}
}

func TestOwnedTreeMetadataEncodingAndStrictVersion(test *testing.T) {
	legacy := Catalogue{Version: Version, Artifacts: []Artifact{{ID: "raw", Kind: "file", Root: "raw"}}}
	data, err := json.Marshal(legacy)
	if err != nil || string(data) != `{"version":1,"artifacts":[{"id":"raw","kind":"file","root":"raw"}]}` {
		test.Fatalf("changed v1 catalogue bytes: %s: %v", data, err)
	}
	valid := `{"version":2,"artifact":"tree","plugin":"tree","layoutSignature":"tree-v2","rootMode":0,"entries":[]}`
	for _, metadata := range []string{
		strings.Replace(valid, `"version":2`, `"version":1`, 1),
		strings.Replace(valid, `"version":2`, `"version":3`, 1),
		strings.Replace(valid, `"version":2,`, ``, 1),
		strings.Replace(valid, `"rootMode":0,`, ``, 1),
		strings.Replace(valid, `"entries":[]`, `"entries":null`, 1),
		strings.Replace(valid, `"entries":[]`, `"unknown":false,"entries":[]`, 1),
		strings.Replace(valid, `"entries":[]`, `"entries":[{"relativePath":"file","type":"file","size":0,"mode":0,"executable":false}]`, 1),
		strings.Replace(valid, `"entries":[]`, `"entries":[{"relativePath":"empty","type":"directory","hash":0,"size":0,"mode":0,"executable":false,"unknown":0}]`, 1),
	} {
		file := filepath.Join(test.TempDir(), "catalogue.json")
		writeTestFile(test, file, []byte(`{"version":1,"artifacts":[{"id":"tree","kind":"directory","root":"absent","tree":`+metadata+`} ]}`))
		var catalogue Catalogue
		if err := ReadJSON(file, &catalogue); err == nil {
			test.Fatalf("accepted invalid metadata: %s", metadata)
		}
	}
}

func TestLayoutTransformExcludesEncoding(t *testing.T) {
	for _, test := range []struct {
		excludes          []string
		directoryExcludes []string
		want              string
	}{
		{want: `{"kind":"tree-map"}`},
		{excludes: []string{}, directoryExcludes: []string{}, want: `{"kind":"tree-map"}`},
		{excludes: []string{"*.pyc"}, want: `{"kind":"tree-map","excludes":["*.pyc"]}`},
		{directoryExcludes: []string{"tests"}, want: `{"kind":"tree-map","directoryExcludes":["tests"]}`},
		{excludes: []string{"*.pyc"}, directoryExcludes: []string{"tests", "**/tests"},
			want: `{"kind":"tree-map","excludes":["*.pyc"],"directoryExcludes":["tests","**/tests"]}`},
	} {
		transform := &LayoutTransform{Kind: "tree-map", Excludes: test.excludes, DirectoryExcludes: test.directoryExcludes}
		data, err := json.Marshal(transform)
		if err != nil || string(data) != test.want {
			t.Fatalf("transform encoding = %s: %v, want %s", data, err, test.want)
		}
		file := filepath.Join(t.TempDir(), "transform.json")
		writeTestFile(t, file, data)
		var decoded LayoutTransform
		if err := ReadJSON(file, &decoded); err != nil {
			t.Fatal(err)
		}
		if got := kotlinJSON(t, decoded); got != test.want {
			t.Fatalf("decoded transform = %s, want %s", got, test.want)
		}
		operation := kotlinLayoutAssetsOperation(t, "layout", "output", "tree", "payload", LayoutAssets{Assets: []LayoutAsset{{Transform: transform}}})
		var encoded struct {
			LayoutAssets struct {
				Assets []struct {
					Transform json.RawMessage `json:"transform"`
				} `json:"assets"`
			} `json:"layoutAssets"`
		}
		if err := json.Unmarshal([]byte(operation), &encoded); err != nil {
			t.Fatal(err)
		}
		if len(encoded.LayoutAssets.Assets) != 1 || string(encoded.LayoutAssets.Assets[0].Transform) != test.want {
			t.Fatalf("Kotlin operation encoding = %s, want transform %s", operation, test.want)
		}
	}
}

func TestKotlinDefaultFieldEncoding(t *testing.T) {
	root := t.TempDir()
	recipeFile := filepath.Join(root, "recipe.json")
	writeTestFile(t, recipeFile, []byte(`{
  "version":1,"plugin":"example","layoutSignature":"signature",
  "assets":[{"destination":"lib/plugin.jar","producer":"remainder","artifact":""}],
  "operations":[{
    "kind":"jar","destination":"lib/plugin.jar","target":"","mode":0,
    "options":{"mergeEntities":false,"directories":"none","verifyCrc":false},
    "sources":[{"kind":"entries","library":"","filter":"","excludes":[],"manifest":"drop","entries":[],"overrides":[]}]
  }]
}`))
	var recipe Recipe
	if err := ReadJSON(recipeFile, &recipe); err != nil {
		t.Fatal(err)
	}
	execution, err := Plan(recipe, Catalogue{Version: Version})
	if err != nil {
		t.Fatal(err)
	}
	if err := execution.Write(filepath.Join(root, "output"), filepath.Join(root, "inventory.json")); err != nil {
		t.Fatal(err)
	}
}
