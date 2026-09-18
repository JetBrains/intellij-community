package main

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/pluginclasspath"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

func pluginComponentTreeFixture(test *testing.T) (pluginComponentSpec, []pluginComponentAsset, []filemetadata.Entry) {
	test.Helper()
	spec, assets := pluginComponentFixture(test)
	spec.Version = 2
	excluded := false
	assets = append(assets,
		pluginComponentAsset{Destination: "kotlinc", Producer: "remainder", Kind: "tree", ClassPath: &excluded},
		pluginComponentAsset{Destination: "lib/resources.jar", Producer: "remainder", Kind: "tree", ClassPath: &excluded},
	)
	entries, err := filemetadata.Read(spec.Remainder.Metadata)
	if err != nil {
		test.Fatal(err)
	}
	entries = append(entries,
		filemetadata.Entry{RelativePath: "kotlinc", Type: "directory", Mode: 0o750},
		filemetadata.Entry{RelativePath: "kotlinc/lib", Type: "directory", Mode: 0o755},
		filemetadata.Entry{RelativePath: "kotlinc/lib/compiler.jar", Type: "file", Hash: 23, Size: 13, Mode: 0o751, Executable: true},
		filemetadata.Entry{RelativePath: "kotlinc/empty", Type: "directory", Mode: 0o710},
		filemetadata.Entry{RelativePath: "kotlinc/empty/nested", Type: "directory", Mode: 0o700},
		filemetadata.Entry{RelativePath: "lib/resources.jar", Type: "directory", Mode: 0o710},
	)
	for name, target := range map[string]string{"current": "./lib/compiler.jar", "empty-link": "empty/nested"} {
		link := filepath.Join(test.TempDir(), "link")
		if err := os.Symlink(target, link); err != nil {
			test.Fatal(err)
		}
		entry, err := filemetadata.Inspect(link, "kotlinc/"+name)
		if err != nil {
			test.Fatal(err)
		}
		entries = append(entries, entry)
	}
	writePluginJSON(test, "component-spec.json", spec)
	writePluginJSON(test, spec.Assets, assets)
	if err := filemetadata.Write(spec.Remainder.Metadata, entries); err != nil {
		test.Fatal(err)
	}
	return spec, assets, entries
}

func TestPluginComponentExpandsOwnedTreesWithoutPayloads(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, _, entries := pluginComponentTreeFixture(test)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	var manifest componentManifest
	data, err := os.ReadFile("component.json")
	if err != nil || json.Unmarshal(data, &manifest) != nil {
		test.Fatalf("manifest: %v", err)
	}
	if len(manifest.Entries) != len(entries)+2 {
		test.Fatalf("tree inventory was not expanded: %s", data)
	}
	index := slices.IndexFunc(manifest.Entries, func(entry componentEntry) bool { return entry.RelativePath == "plugins/demo/kotlinc/current" })
	if index < 0 {
		test.Fatalf("tree link is missing: %s", data)
	}
	if link := manifest.Entries[index]; link.Type != "symlink" || link.SymlinkTarget != "lib/compiler.jar" || link.Hash != filemetadata.SymlinkHash("lib/compiler.jar") {
		test.Fatalf("tree link is not recorded by its cleaned target: %#v", link)
	}
	if bytes.Contains(data, []byte(`"symlinkSource"`)) {
		test.Fatalf("tree links retain payload provenance: %s", data)
	}
	actual, err := os.ReadFile("component.plugin-classpath-part")
	expected, expectedError := os.ReadFile(spec.Classpath)
	if err != nil || expectedError != nil || !bytes.Equal(actual, expected) {
		test.Fatalf("tree changed classpath: %x: %v", actual, err)
	}
	if _, err := os.Lstat("payload"); !os.IsNotExist(err) {
		test.Fatalf("collector read or wrote payloads: %v", err)
	}
}

func TestPluginComponentExpandsPluginRootTreeAfterClaimedAssets(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	spec.Version = 2
	excluded := false
	assets = append(assets, pluginComponentAsset{Destination: "", Producer: "remainder", Kind: "tree", ClassPath: &excluded})
	entries, err := filemetadata.Read(spec.Remainder.Metadata)
	if err != nil {
		test.Fatal(err)
	}
	entries = append(entries,
		filemetadata.Entry{RelativePath: "languageService", Type: "directory", Mode: 0o755},
		filemetadata.Entry{RelativePath: "languageService/eslint.js", Type: "file", Hash: 23, Size: 13, Mode: 0o644},
	)
	if err := filemetadata.Write(spec.Remainder.Metadata, entries); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	writePluginJSON(test, "component-spec.json", spec)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	var manifest componentManifest
	if err := readPluginMetadata("component.json", &manifest); err != nil {
		test.Fatal(err)
	}
	if !slices.ContainsFunc(manifest.Entries, func(entry componentEntry) bool {
		return entry.RelativePath == "plugins/demo/languageService/eslint.js" && entry.Source == "payload/remainder/languageService/eslint.js"
	}) {
		test.Fatalf("plugin-root tree entry is missing: %+v", manifest.Entries)
	}
}

func TestPluginComponentAssignsNestedTreesBySpecificity(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	spec.Version = 2
	excluded := false
	assets = append(assets,
		pluginComponentAsset{Destination: "resources", Producer: "remainder", Kind: "tree", ClassPath: &excluded},
		pluginComponentAsset{Destination: "resources/nested", Producer: "remainder", Kind: "tree", ClassPath: &excluded},
	)
	entries, err := filemetadata.Read(spec.Remainder.Metadata)
	if err != nil {
		test.Fatal(err)
	}
	entries = append(entries,
		filemetadata.Entry{RelativePath: "resources", Type: "directory", Mode: 0o755},
		filemetadata.Entry{RelativePath: "resources/outer.txt", Type: "file", Hash: 23, Size: 13, Mode: 0o644},
		filemetadata.Entry{RelativePath: "resources/nested", Type: "directory", Mode: 0o755},
		filemetadata.Entry{RelativePath: "resources/nested/inner.txt", Type: "file", Hash: 24, Size: 14, Mode: 0o644},
	)
	if err := filemetadata.Write(spec.Remainder.Metadata, entries); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	writePluginJSON(test, "component-spec.json", spec)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	var manifest componentManifest
	if err := readPluginMetadata("component.json", &manifest); err != nil {
		test.Fatal(err)
	}
	for _, name := range []string{"plugins/demo/resources/outer.txt", "plugins/demo/resources/nested/inner.txt"} {
		if !slices.ContainsFunc(manifest.Entries, func(entry componentEntry) bool { return entry.RelativePath == name }) {
			test.Fatalf("nested tree entry is missing: %s", name)
		}
	}
}

func TestPluginComponentAcceptsEmptyOwnedTree(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	spec.Version = 2
	excluded := false
	assets = append(assets, pluginComponentAsset{Destination: "optional", Producer: "remainder", Kind: "tree", ClassPath: &excluded})
	writePluginJSON(test, spec.Assets, assets)
	writePluginJSON(test, "component-spec.json", spec)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
}

func TestPluginComponentRejectsIndependentFileInsideTreeWithIdenticalMetadata(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets, _ := pluginComponentTreeFixture(test)
	assets[0].Destination = "kotlinc/lib/compiler.jar"
	if err := filemetadata.Write(spec.Independent[0].Metadata, []filemetadata.Entry{{
		RelativePath: spec.Independent[0].RelativePath,
		Type:         "file",
		Hash:         23,
		Size:         13,
		Mode:         0o751,
		Executable:   true,
	}}); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code == 0 || !strings.Contains(errors.String(), "conflicting plugin destinations") {
		test.Fatalf("accepted identical metadata for two owners: exit %d: %s", code, &errors)
	}
}

func TestPluginComponentAllowsRemainderFilesBelowTree(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets, entries := pluginComponentTreeFixture(test)
	assets = append(assets, pluginComponentAsset{Destination: "kotlinc/generated.jar", Producer: "remainder"})
	entries = append(entries, filemetadata.Entry{RelativePath: "kotlinc/generated.jar", Type: "file", Hash: 25, Size: 15, Mode: 0o644})
	if err := filemetadata.Write(spec.Remainder.Metadata, entries); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	var manifest componentManifest
	if err := readPluginMetadata("component.json", &manifest); err != nil {
		test.Fatal(err)
	}
	if !slices.ContainsFunc(manifest.Entries, func(entry componentEntry) bool {
		return entry.RelativePath == "plugins/demo/kotlinc/generated.jar"
	}) {
		test.Fatal("the remainder file is missing")
	}
}

func TestPluginComponentRejectsStaleTreeInventory(test *testing.T) {
	for _, scenario := range []struct {
		name   string
		mutate func(*pluginComponentSpec, *[]pluginComponentAsset, *[]filemetadata.Entry)
	}{
		{"version 1", func(spec *pluginComponentSpec, _ *[]pluginComponentAsset, _ *[]filemetadata.Entry) { spec.Version = 1 }},
		{"classpath", func(_ *pluginComponentSpec, assets *[]pluginComponentAsset, _ *[]filemetadata.Entry) {
			(*assets)[5].ClassPath = nil
		}},
		{"independent tree", func(_ *pluginComponentSpec, assets *[]pluginComponentAsset, _ *[]filemetadata.Entry) {
			(*assets)[5].Producer = "independent"
			(*assets)[5].Artifact = "shared"
		}},
		{"nested asset", func(_ *pluginComponentSpec, assets *[]pluginComponentAsset, _ *[]filemetadata.Entry) {
			*assets = append(*assets, pluginComponentAsset{Destination: "kotlinc/empty", Producer: "remainder", Kind: "directory"})
		}},
		{"independent overlap", func(_ *pluginComponentSpec, assets *[]pluginComponentAsset, _ *[]filemetadata.Entry) {
			(*assets)[0].Destination = "kotlinc/lib/compiler.jar"
		}},
		{"root alias", func(_ *pluginComponentSpec, assets *[]pluginComponentAsset, _ *[]filemetadata.Entry) {
			(*assets)[5].Destination = "KOTLINC"
		}},
		{"missing root", func(_ *pluginComponentSpec, _ *[]pluginComponentAsset, entries *[]filemetadata.Entry) {
			*entries = slices.DeleteFunc(*entries, func(entry filemetadata.Entry) bool { return entry.RelativePath == "kotlinc" })
		}},
		{"missing directory", func(_ *pluginComponentSpec, _ *[]pluginComponentAsset, entries *[]filemetadata.Entry) {
			*entries = slices.DeleteFunc(*entries, func(entry filemetadata.Entry) bool { return entry.RelativePath == "kotlinc/lib" })
		}},
		{"missing target", func(_ *pluginComponentSpec, _ *[]pluginComponentAsset, entries *[]filemetadata.Entry) {
			*entries = slices.DeleteFunc(*entries, func(entry filemetadata.Entry) bool { return entry.RelativePath == "kotlinc/lib/compiler.jar" })
		}},
		{"unclaimed prefix", func(_ *pluginComponentSpec, _ *[]pluginComponentAsset, entries *[]filemetadata.Entry) {
			*entries = append(*entries, filemetadata.Entry{RelativePath: "kotlinc-other/file", Type: "file", Mode: 0o644})
		}},
		{"independent inventory", func(_ *pluginComponentSpec, _ *[]pluginComponentAsset, entries *[]filemetadata.Entry) {
			*entries = append(*entries, filemetadata.Entry{RelativePath: "lib/member.jar", Type: "file", Mode: 0o644})
		}},
		{"duplicate inventory", func(_ *pluginComponentSpec, _ *[]pluginComponentAsset, entries *[]filemetadata.Entry) {
			*entries = append(*entries, (*entries)[3])
		}},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			test.Chdir(test.TempDir())
			spec, assets, entries := pluginComponentTreeFixture(test)
			scenario.mutate(&spec, &assets, &entries)
			writePluginJSON(test, "component-spec.json", spec)
			writePluginJSON(test, spec.Assets, assets)
			writePluginJSON(test, spec.Remainder.Metadata, map[string]any{"version": 1, "entries": entries})
			var output, errors bytes.Buffer
			if code := run(pluginComponentArgs(), &output, &errors); code == 0 {
				test.Fatal("accepted stale tree ownership")
			}
			for _, file := range []string{"component.json", "component.plugin-classpath-part"} {
				if _, err := os.Lstat(file); !os.IsNotExist(err) {
					test.Fatalf("invalid ownership wrote %s: %v", file, err)
				}
			}
		})
	}
}

func TestPluginComponentDirectoriesNeedNoHashesOrPayload(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	entries, err := filemetadata.Read(spec.Remainder.Metadata)
	if err != nil {
		test.Fatal(err)
	}
	for _, directory := range []string{"lib", "empty", "empty/nested", "lib/not-a-file.jar"} {
		entries = append(entries, filemetadata.Entry{RelativePath: directory, Type: "directory", Mode: 0o710})
		assets = append(assets, pluginComponentAsset{Destination: directory, Producer: "remainder", Kind: "directory"})
	}
	if err := filemetadata.Write(spec.Remainder.Metadata, entries); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	var manifest struct {
		Entries []map[string]json.RawMessage `json:"entries"`
	}
	data, err := os.ReadFile("component.json")
	if err != nil || json.Unmarshal(data, &manifest) != nil {
		test.Fatalf("manifest: %s: %v", data, err)
	}
	count := 0
	for _, entry := range manifest.Entries {
		if string(entry["type"]) != `"directory"` {
			continue
		}
		count++
		if entry["hash"] != nil || entry["source"] != nil || string(entry["mode"]) != "456" {
			test.Fatalf("invalid directory component: %+v", entry)
		}
	}
	if count != 4 {
		test.Fatalf("missing directories: %s", data)
	}
	if _, err := os.Lstat(spec.Remainder.Directory); !os.IsNotExist(err) {
		test.Fatalf("collector materialized the remainder: %v", err)
	}
	expectedClasspath, err := os.ReadFile(spec.Classpath)
	if err != nil {
		test.Fatal(err)
	}
	actualClasspath, err := os.ReadFile("component.plugin-classpath-part")
	if err != nil || !bytes.Equal(actualClasspath, expectedClasspath) {
		test.Fatalf("directory with a jar suffix changed the prepared classpath: %x: %v", actualClasspath, err)
	}
}

func writePluginJSON(test *testing.T, destination string, value any) {
	test.Helper()
	data, err := json.Marshal(value)
	if err != nil {
		test.Fatal(err)
	}
	writeTestFile(test, destination, data)
}

func pluginClassPathFixture(plugin string, names ...string) []byte {
	data := binary.BigEndian.AppendUint16(nil, uint16(len(names)))
	appendName := func(value string) {
		encoded := pluginclasspath.ModifiedUTF8(value)
		data = binary.BigEndian.AppendUint16(data, uint16(len(encoded)))
		data = append(data, encoded...)
	}
	appendName(plugin)
	descriptor := []byte("<idea-plugin/>\n")
	data = binary.BigEndian.AppendUint32(data, uint32(len(descriptor)))
	data = append(data, descriptor...)
	for _, name := range names {
		appendName(name)
	}
	return data
}

func TestPluginComponentClassPathUsesOriginalParticipation(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	entries, err := filemetadata.Read(spec.Remainder.Metadata)
	if err != nil {
		test.Fatal(err)
	}
	excluded := false
	entries = append(entries,
		filemetadata.Entry{RelativePath: "lib/foo.jar", Type: "file", Mode: 0o644, Hash: 21},
		filemetadata.Entry{RelativePath: "lib/empty.jar", Type: "directory", Mode: 0o755},
		filemetadata.Entry{RelativePath: "lib/custom.jar", Type: "file", Mode: 0o644, Hash: 22},
	)
	assets = append(assets,
		pluginComponentAsset{Destination: "lib/foo.jar", Producer: "remainder", ClassPath: &excluded},
		pluginComponentAsset{Destination: "lib/empty.jar", Producer: "remainder", Kind: "directory"},
		pluginComponentAsset{Destination: "lib/custom.jar", Producer: "remainder"},
	)
	if err := filemetadata.Write(spec.Remainder.Metadata, entries); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	valid := pluginClassPathFixture("demo", "lib/demo.jar", "lib/custom.jar", "lib/member.jar")
	for _, invalid := range [][]byte{
		pluginClassPathFixture("demo", "lib/demo.jar", "lib/member.jar", "lib/foo.jar"),
		pluginClassPathFixture("demo", "lib/demo.jar", "lib/member.jar", "lib/empty.jar"),
		pluginClassPathFixture("demo", "lib/demo.jar", "lib/member.jar", "lib/member.jar"),
		pluginClassPathFixture("demo", "lib/demo.jar", "lib/member.jar"),
		pluginClassPathFixture("other", "lib/demo.jar", "lib/custom.jar", "lib/member.jar"),
		append(append([]byte{}, valid...), 0),
		valid[:len(valid)-1],
		{0, 3, 0, 4, 'd', 'e', 'm', 'o', 0xff, 0xff, 0xff, 0xff},
	} {
		writeTestFile(test, spec.Classpath, invalid)
		var output, errors bytes.Buffer
		if code := run(pluginComponentArgs(), &output, &errors); code == 0 || !strings.Contains(errors.String(), "classpath") {
			test.Fatalf("accepted invalid classpath %x: exit %d: %s", invalid, code, &errors)
		}
		for _, output := range []string{"component.json", "component.plugin-classpath-part"} {
			if _, err := os.Lstat(output); !os.IsNotExist(err) {
				test.Fatalf("invalid classpath produced %s: %v", output, err)
			}
		}
	}
	writeTestFile(test, spec.Classpath, valid)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("valid classpath failed: exit %d: %s", code, &errors)
	}
	actual, err := os.ReadFile("component.plugin-classpath-part")
	if err != nil || !bytes.Equal(actual, valid) {
		test.Fatalf("the prepared classpath changed: %x: %v", actual, err)
	}
}

func TestPluginComponentClassPathPreservesJavaStringEncoding(test *testing.T) {
	if !bytes.Equal(pluginclasspath.ModifiedUTF8("\x00é😀"), []byte{0xc0, 0x80, 0xc3, 0xa9, 0xed, 0xa0, 0xbd, 0xed, 0xb8, 0x80}) {
		test.Fatal("classpath names do not use Java string encoding")
	}
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	spec.PluginDirectory = "plugins/démo😀"
	assets[0].Destination = "lib/😀.jar"
	writePluginJSON(test, spec.Assets, assets)
	writePluginJSON(test, "component-spec.json", spec)
	writeTestFile(test, spec.Classpath, pluginClassPathFixture("démo😀", "lib/demo.jar", "lib/😀.jar"))
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("Unicode classpath failed: exit %d: %s", code, &errors)
	}
}

func pluginComponentFixture(test *testing.T) (pluginComponentSpec, []pluginComponentAsset) {
	test.Helper()
	spec := pluginComponentSpec{
		Version: 1, PluginDirectory: "plugins/demo",
		Remainder: pluginComponentRemainder{Directory: "payload/remainder", Metadata: "metadata/remainder.json"},
		Assets:    "metadata/assets.json", Classpath: "metadata/plugin-classpath.txt",
		Independent: []pluginComponentIndependent{{Artifact: "shared", Source: "payload/shared.jar", Metadata: "metadata/shared.json", RelativePath: "shared.jar"}},
	}
	assets := []pluginComponentAsset{
		{Destination: "lib/member.jar", Producer: "independent", Artifact: "shared"},
		{Destination: "lib/demo.jar", Producer: "remainder"},
		{Destination: "bin/tool", Producer: "remainder"},
		{Destination: "bin/current", Producer: "remainder"},
		{Destination: "lib/nested/shared.jar", Producer: "independent", Artifact: "shared"},
	}
	link := filepath.Join(test.TempDir(), "current")
	if err := os.Symlink("./tool", link); err != nil {
		test.Fatal(err)
	}
	linkEntry, err := filemetadata.Inspect(link, "bin/current")
	if err != nil {
		test.Fatal(err)
	}
	if err := filemetadata.Write(spec.Remainder.Metadata, []filemetadata.Entry{
		{RelativePath: "lib/demo.jar", Type: "file", Hash: 10, Size: 12, Mode: 0644},
		{RelativePath: "bin/tool", Type: "file", Hash: 11, Size: 13, Mode: 0750, Executable: true},
		linkEntry,
	}); err != nil {
		test.Fatal(err)
	}
	if err := filemetadata.Write(spec.Independent[0].Metadata, []filemetadata.Entry{
		{RelativePath: "shared.jar", Type: "file", Hash: 12, Size: 14, Mode: 0600},
	}); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	writePluginJSON(test, "component-spec.json", spec)
	writeTestFile(test, spec.Classpath, pluginClassPathFixture("demo", "lib/demo.jar", "lib/member.jar"))
	return spec, assets
}

func pluginComponentArgs() []string {
	return append(baseArgs("--plugin-component=component-spec.json"), "--plugin-classpath-part=component.plugin-classpath-part")
}

func TestPluginComponentUsesOnlyMetadata(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	if err := os.MkdirAll("payload", 0755); err != nil {
		test.Fatal(err)
	}
	for _, source := range []string{spec.Remainder.Directory, spec.Independent[0].Source} {
		if err := os.Symlink(filepath.Base(source), source); err != nil {
			test.Fatal(err)
		}
	}
	var output, errors bytes.Buffer
	if code := run(append(pluginComponentArgs(), "--trace-file=component.spans.json"), &output, &errors); code != 0 {
		test.Fatalf("collector accessed an unreadable payload: exit %d: %s", code, &errors)
	}
	var manifest componentManifest
	if err := readPluginMetadata("component.json", &manifest); err != nil {
		test.Fatal(err)
	}
	if manifest.Version != 9 || manifest.PluginCount != 1 || len(manifest.Entries) != len(assets) {
		test.Fatalf("incomplete component manifest: %#v", manifest)
	}
	entries := make(map[string]componentEntry)
	for _, entry := range manifest.Entries {
		entries[entry.RelativePath] = entry
	}
	for _, expectation := range []struct {
		destination string
		source      string
		mode        uint32
		hash        int64
	}{
		{"lib/member.jar", "payload/shared.jar", 0600, 12},
		{"lib/demo.jar", "payload/remainder/lib/demo.jar", 0644, 10},
		{"bin/tool", "payload/remainder/bin/tool", 0750, 11},
		{"lib/nested/shared.jar", "payload/shared.jar", 0600, 12},
	} {
		entry := entries["plugins/demo/"+expectation.destination]
		modeMatches := entry.Mode != nil && *entry.Mode == expectation.mode
		if expectation.mode == 0644 {
			modeMatches = entry.Mode == nil
		}
		if entry.Type != "component-file" || entry.Source != expectation.source || entry.Hash != expectation.hash || !modeMatches || entry.Executable != (expectation.mode&0111 != 0) {
			test.Fatalf("incorrect file metadata: %#v", entry)
		}
	}
	link := entries["plugins/demo/bin/current"]
	if link.Type != "symlink" || link.Source != "" || link.SymlinkTarget != "tool" || link.Hash != filemetadata.SymlinkHash("tool") || link.Mode != nil || link.Executable {
		test.Fatalf("link is not recorded by its cleaned target: %#v", link)
	}
	if data, err := os.ReadFile("component.json"); err != nil || bytes.Contains(data, []byte(`"symlinkSource"`)) {
		test.Fatalf("link retains payload provenance: %s, %v", data, err)
	}
	wantClasspath, err := os.ReadFile(spec.Classpath)
	if err != nil {
		test.Fatal(err)
	}
	actualClasspath, err := os.ReadFile("component.plugin-classpath-part")
	if err != nil || !bytes.Equal(actualClasspath, wantClasspath) {
		test.Fatalf("classpath changed: %x, %v", actualClasspath, err)
	}
	actualAssets := []pluginComponentAsset{}
	if err := readPluginMetadata(spec.Assets, &actualAssets); err != nil || !reflect.DeepEqual(actualAssets, assets) {
		test.Fatalf("asset order changed: %#v, %v", actualAssets, err)
	}
	trace := readTrace(test, "component.spans.json")
	for _, activity := range trace.Data[0].Spans {
		if activity.tag("byteCount") != "0" {
			test.Fatalf("payload reads were reported: %#v", activity)
		}
	}
}

func TestPluginComponentMapsDistributionScopedAssetsToTheDistributionRoot(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	spec.Version = pluginpack.ScopedVersion
	excluded := false
	distributionAsset := pluginComponentAsset{
		Destination: "lib/native/tool", Producer: "remainder", ClassPath: &excluded, Scope: pluginpack.DistributionScope,
	}
	assets = append(assets, distributionAsset)
	entries, err := filemetadata.Read(spec.Remainder.Metadata)
	if err != nil {
		test.Fatal(err)
	}
	transportDestination := pluginpack.TransportDestination(spec.Version, distributionAsset.Scope, distributionAsset.Destination)
	entries = append(entries, filemetadata.Entry{
		RelativePath: transportDestination, Type: "file", Hash: 31, Size: 17, Mode: 0o755, Executable: true,
	})
	if err := filemetadata.Write(spec.Remainder.Metadata, entries); err != nil {
		test.Fatal(err)
	}
	writePluginJSON(test, spec.Assets, assets)
	writePluginJSON(test, "component-spec.json", spec)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	var manifest componentManifest
	if err := readPluginMetadata("component.json", &manifest); err != nil {
		test.Fatal(err)
	}
	rootEntry := slices.IndexFunc(manifest.Entries, func(entry componentEntry) bool { return entry.RelativePath == "lib/native/tool" })
	if rootEntry < 0 {
		test.Fatalf("distribution asset is absent: %+v", manifest.Entries)
	}
	entry := manifest.Entries[rootEntry]
	if entry.Source != spec.Remainder.Directory+"/"+transportDestination || !entry.Executable {
		test.Fatalf("distribution asset has incorrect provenance: %+v", entry)
	}
	if slices.ContainsFunc(manifest.Entries, func(entry componentEntry) bool {
		return entry.RelativePath == spec.PluginDirectory+"/"+distributionAsset.Destination
	}) {
		test.Fatal("distribution asset was also mapped below the plugin root")
	}
}

func TestPluginComponentReservesTheDistributionTransportPrefixForVersionThreePluginAssets(test *testing.T) {
	excluded := false
	transportRoot := pluginpack.TransportDestination(pluginpack.ScopedVersion, pluginpack.DistributionScope, "")
	for _, destination := range []string{transportRoot, transportRoot + "/child"} {
		test.Run("reject "+destination, func(test *testing.T) {
			assets := []pluginComponentAsset{
				{Destination: destination, Producer: "independent", Artifact: "plugin"},
				{Destination: "lib/native.bin", Producer: "independent", Artifact: "distribution", ClassPath: &excluded, Scope: pluginpack.DistributionScope},
			}
			if err := validatePluginComponentAssets(pluginpack.ScopedVersion, assets); err == nil || !strings.Contains(err.Error(), "reserved distribution transport path") {
				test.Fatalf("accepted reserved plugin destination %q: %v", destination, err)
			}
		})
		test.Run("version 2 "+destination, func(test *testing.T) {
			assets := []pluginComponentAsset{{Destination: destination, Producer: "independent", Artifact: "plugin"}}
			if err := validatePluginComponentAssets(pluginpack.TreeVersion, assets); err != nil {
				test.Fatal(err)
			}
		})
		test.Run("distribution "+destination, func(test *testing.T) {
			assets := []pluginComponentAsset{{
				Destination: destination, Producer: "independent", Artifact: "distribution", ClassPath: &excluded, Scope: pluginpack.DistributionScope,
			}}
			if err := validatePluginComponentAssets(pluginpack.ScopedVersion, assets); err != nil {
				test.Fatal(err)
			}
		})
	}
}

func TestPluginComponentRejectsStaleOwnership(test *testing.T) {
	for _, scenario := range []struct {
		name   string
		change func(*pluginComponentSpec, []pluginComponentAsset) []pluginComponentAsset
		want   string
	}{
		{"version", func(spec *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			spec.Version = 3
			return assets
		}, "version"},
		{"plugin escape", func(spec *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			spec.PluginDirectory = "plugins/../outside"
			return assets
		}, "pluginDirectory"},
		{"unknown producer", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[0].Producer = "kotlin"
			return assets
		}, "unknown asset producer"},
		{"invalid mode normalization", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[0].NormalizeTreeModes = true
			return assets
		}, "non-tree asset"},
		{"missing independent", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[0].Artifact = "missing"
			return assets
		}, "missing independent artifact"},
		{"unused independent", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset { return assets[1:4] }, "unused independent"},
		{"unclaimed remainder", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			return append(assets[:1], assets[2:]...)
		}, "unclaimed remainder"},
		{"missing remainder", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[1].Destination = "lib/missing.jar"
			return assets
		}, "stale remainder ownership"},
		{"unsafe reuse", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[1].Artifact = "shared"
			return assets
		}, "stale remainder ownership"},
		{"duplicate destination", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			return append(assets, assets[0])
		}, "destination collision"},
		{"case collision", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[4].Destination = "lib/MEMBER.jar"
			return assets
		}, "destination collision"},
		{"Unicode collision", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[0].Destination = "lib/\u03c3.jar"
			assets[4].Destination = "lib/\u03c2.jar"
			return assets
		}, "destination collision"},
		{"directory spelling", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[4].Destination = "LIB/another.jar"
			return assets
		}, "conflicting directory spellings"},
		{"parent collision", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[4].Destination = "lib/demo.jar/child"
			return assets
		}, "conflicting destinations"},
		{"escaping destination", func(_ *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			assets[0].Destination = "../outside"
			return assets
		}, "unsafe relative path"},
		{"metadata in payload", func(spec *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			spec.Classpath = "payload/remainder/classpath"
			return assets
		}, "overlaps payload"},
		{"escaping source root", func(spec *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			spec.Remainder.Directory = "../outside"
			return assets
		}, "invalid declared artifact path"},
		{"source root backslash", func(spec *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			spec.Remainder.Directory = `payload\remainder`
			return assets
		}, "invalid declared artifact path"},
		{"independent in remainder", func(spec *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			spec.Independent[0].Source = "payload/remainder/shared.jar"
			return assets
		}, "overlaps the remainder"},
		{"duplicate ID", func(spec *pluginComponentSpec, assets []pluginComponentAsset) []pluginComponentAsset {
			spec.Independent = append(spec.Independent, spec.Independent[0])
			return assets
		}, "duplicate independent artifact"},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			test.Chdir(test.TempDir())
			spec, assets := pluginComponentFixture(test)
			assets = scenario.change(&spec, assets)
			writePluginJSON(test, spec.Assets, assets)
			writePluginJSON(test, "component-spec.json", spec)
			var output, errors bytes.Buffer
			if code := run(pluginComponentArgs(), &output, &errors); code == 0 || !strings.Contains(errors.String(), scenario.want) {
				test.Fatalf("exit %d, error %q; expected %s", code, errors.String(), scenario.want)
			}
			for _, file := range []string{"component.json", "component.plugin-classpath-part"} {
				if _, err := os.Lstat(file); !os.IsNotExist(err) {
					test.Fatalf("invalid metadata produced %s: %v", file, err)
				}
			}
		})
	}
}

func TestPluginComponentRejectsConflictingMetadata(test *testing.T) {
	test.Chdir(test.TempDir())
	spec, assets := pluginComponentFixture(test)
	conflict := spec.Independent[0]
	conflict.Artifact = "conflict"
	conflict.Metadata = "metadata/conflict.json"
	if err := filemetadata.Write(conflict.Metadata, []filemetadata.Entry{{RelativePath: "shared.jar", Type: "file", Hash: 99, Size: 14, Mode: 0600}}); err != nil {
		test.Fatal(err)
	}
	spec.Independent = append(spec.Independent, conflict)
	assets[4].Artifact = conflict.Artifact
	writePluginJSON(test, "component-spec.json", spec)
	writePluginJSON(test, spec.Assets, assets)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code == 0 || !strings.Contains(errors.String(), "conflicting metadata") {
		test.Fatalf("exit %d: %s", code, &errors)
	}
}

func TestPluginComponentProtectsInputsAndPayloadFromOutputs(test *testing.T) {
	for _, scenario := range []struct{ name, value string }{
		{"--trace-file", "metadata/shared.json"},
		{"--trace-file", "payload/remainder/trace.json"},
		{"--trace-file", "payload/shared.jar"},
		{"--trace-file", "payload"},
		{"--trace-file", "component.plugin-classpath-part"},
		{"--plugin-classpath-part", "metadata/plugin-classpath.txt"},
		{"--plugin-classpath-part", "metadata/./plugin-classpath.txt"},
		{"--component-manifest", "metadata/remainder.json"},
	} {
		test.Run(scenario.name+"="+scenario.value, func(test *testing.T) {
			test.Chdir(test.TempDir())
			spec, _ := pluginComponentFixture(test)
			before := make(map[string][]byte)
			for _, source := range []string{spec.Remainder.Metadata, spec.Independent[0].Metadata, spec.Classpath} {
				before[source], _ = os.ReadFile(source)
			}
			args := pluginComponentArgs()
			if scenario.name == "--trace-file" {
				args = append(args, scenario.name+"="+scenario.value)
			} else {
				for index, arg := range args {
					if strings.HasPrefix(arg, scenario.name+"=") {
						args[index] = scenario.name + "=" + scenario.value
					}
				}
			}
			var output, errors bytes.Buffer
			if code := run(args, &output, &errors); code == 0 {
				test.Fatal("accepted an output that aliases an input")
			}
			for source, expected := range before {
				actual, err := os.ReadFile(source)
				if err != nil || !bytes.Equal(actual, expected) {
					test.Fatalf("input %s changed: %v", source, err)
				}
			}
		})
	}
}

func TestPluginComponentOptions(test *testing.T) {
	if _, err := parseOptions(pluginComponentArgs()); err != nil {
		test.Fatal(err)
	}
	for _, args := range [][]string{
		baseArgs("--plugin-component=spec.json"),
		append(baseArgs("--files-file=files.json"), "--plugin-classpath-part=part"),
		append(pluginComponentArgs(), "--metadata-catalogue=metadata.json"),
		append(pluginComponentArgs(), "--files-file=files.json"),
	} {
		if _, err := parseOptions(args); err == nil {
			test.Fatalf("accepted invalid options: %v", args)
		}
	}
}

func packedPluginComponentFixture(test *testing.T) pluginComponentSpec {
	test.Helper()
	spec := pluginComponentSpec{
		Version: 1, PluginDirectory: "plugins/json", Descriptor: "metadata/plugin.xml",
		Jars: []pluginComponentJar{
			{Destination: "lib/json-rpc-1.0.jar", Source: "payload/json-rpc-1.0.jar", Metadata: "metadata/rpc.json"},
			{Destination: "lib/json.jar", Source: "payload/intellij.json.jar", Metadata: "metadata/main.json"},
			{Destination: "lib/modules/intellij.json.split.jar", Source: "payload/intellij.json.split.jar", Metadata: "metadata/split.json"},
		},
	}
	for index, entry := range []filemetadata.Entry{
		{RelativePath: "json-rpc-1.0.jar", Type: "file", Hash: 30, Size: 15, Mode: 0644},
		{RelativePath: "intellij.json.jar", Type: "file", Hash: 10, Size: 12, Mode: 0600},
		{RelativePath: "intellij.json.split.jar", Type: "file", Hash: 20, Size: 13, Mode: 0644},
	} {
		if err := filemetadata.Write(spec.Jars[index].Metadata, []filemetadata.Entry{entry}); err != nil {
			test.Fatal(err)
		}
	}
	writeText(test, spec.Descriptor, "<idea-plugin/>\n")
	writePluginJSON(test, "component-spec.json", spec)
	return spec
}

func TestPackedPluginComponentWritesManifestAndClassPath(test *testing.T) {
	test.Chdir(test.TempDir())
	packedPluginComponentFixture(test)
	var output, errors bytes.Buffer
	if code := run(append(pluginComponentArgs(), "--trace-file=component.spans.json"), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	var manifest componentManifest
	if err := readPluginMetadata("component.json", &manifest); err != nil {
		test.Fatal(err)
	}
	if manifest.Version != 9 || manifest.PluginCount != 1 || manifest.OS != "linux" || manifest.Arch != "x64" || len(manifest.Entries) != 3 {
		test.Fatalf("incomplete component manifest: %#v", manifest)
	}
	entries := make(map[string]componentEntry)
	for _, entry := range manifest.Entries {
		entries[entry.RelativePath] = entry
	}
	mode := uint32(0600)
	for _, expected := range []componentEntry{
		{RelativePath: "plugins/json/lib/json-rpc-1.0.jar", Type: "component-file", Hash: 30, Source: "payload/json-rpc-1.0.jar"},
		{RelativePath: "plugins/json/lib/json.jar", Type: "component-file", Hash: 10, Source: "payload/intellij.json.jar", Mode: &mode},
		{RelativePath: "plugins/json/lib/modules/intellij.json.split.jar", Type: "component-file", Hash: 20, Source: "payload/intellij.json.split.jar"},
	} {
		if actual := entries[expected.RelativePath]; !reflect.DeepEqual(actual, expected) {
			test.Fatalf("entry = %#v, want %#v", actual, expected)
		}
	}
	// the main jar first by the plugin name, the versioned library last, the `lib/modules` jar absent
	actual, err := os.ReadFile("component.plugin-classpath-part")
	if err != nil {
		test.Fatal(err)
	}
	if expected := pluginClassPathFixture("json", "lib/json.jar", "lib/json-rpc-1.0.jar"); !bytes.Equal(actual, expected) {
		test.Fatalf("classpath = %x, want %x", actual, expected)
	}
	if _, err := os.Lstat("payload"); !os.IsNotExist(err) {
		test.Fatalf("collector read or wrote payloads: %v", err)
	}
	for _, activity := range readTrace(test, "component.spans.json").Data[0].Spans {
		if activity.tag("byteCount") != "0" {
			test.Fatalf("payload reads were reported: %#v", activity)
		}
	}
	if !strings.Contains(output.String(), "named 3 plugin files") {
		test.Fatalf("stdout = %s", &output)
	}
}

func TestPackedPluginComponentAcceptsTheDeclaredSpecShape(test *testing.T) {
	test.Chdir(test.TempDir())
	packedPluginComponentFixture(test)
	writeText(test, "component-spec.json", `{"version": 1, "pluginDirectory": "plugins/json", "descriptor": "metadata/plugin.xml",
 "jars": [{"destination": "lib/json.jar", "source": "payload/intellij.json.jar", "metadata": "metadata/main.json"}]}`)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	actual, err := os.ReadFile("component.plugin-classpath-part")
	if err != nil || !bytes.Equal(actual, pluginClassPathFixture("json", "lib/json.jar")) {
		test.Fatalf("classpath = %x: %v", actual, err)
	}
}

func TestPackedPluginComponentKeepsTheDescriptorBytes(test *testing.T) {
	test.Chdir(test.TempDir())
	spec := packedPluginComponentFixture(test)
	descriptor := "<idea-plugin>\n  <id>json</id>\n  <description><![CDATA[<b>x</b>]]></description>\n</idea-plugin>"
	writeText(test, spec.Descriptor, descriptor)
	var output, errors bytes.Buffer
	if code := run(pluginComponentArgs(), &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	actual, err := os.ReadFile("component.plugin-classpath-part")
	if err != nil {
		test.Fatal(err)
	}
	name := pluginclasspath.ModifiedUTF8("json")
	prefix := append([]byte{0, 2, 0, byte(len(name))}, name...)
	prefix = append(prefix, 0, 0, 0, byte(len(descriptor)))
	prefix = append(prefix, descriptor...)
	if !bytes.HasPrefix(actual, prefix) {
		test.Fatalf("classpath = %x, want prefix %x", actual, prefix)
	}
}

func TestPackedPluginComponentIsPlatformNeutral(test *testing.T) {
	test.Chdir(test.TempDir())
	packedPluginComponentFixture(test)
	args := []string{
		"--component-manifest=component.json", "--kind=plugins_json", "--platform-prefix=idea", "--platform-neutral",
		"--plugin-component=component-spec.json", "--plugin-classpath-part=component.plugin-classpath-part",
	}
	var output, errors bytes.Buffer
	if code := run(args, &output, &errors); code != 0 {
		test.Fatalf("exit %d: %s", code, &errors)
	}
	data, err := os.ReadFile("component.json")
	if err != nil {
		test.Fatal(err)
	}
	var manifest map[string]json.RawMessage
	if err := json.Unmarshal(data, &manifest); err != nil {
		test.Fatal(err)
	}
	if string(manifest["os"]) != `""` || string(manifest["arch"]) != `""` || string(manifest["kind"]) != `"plugins_json"` {
		test.Fatalf("manifest = %s", data)
	}
}

func TestPackedPluginComponentRejectsStaleInputs(test *testing.T) {
	for _, scenario := range []struct {
		name   string
		change func(*pluginComponentSpec)
		want   string
	}{
		{"version", func(spec *pluginComponentSpec) { spec.Version = 2 }, "unsupported packed plugin component version"},
		{"mixed shape", func(spec *pluginComponentSpec) { spec.Classpath = "metadata/plugin-classpath.txt" }, "names only its descriptor and jars"},
		{"mixed remainder", func(spec *pluginComponentSpec) { spec.Remainder.Directory = "payload/remainder" }, "names only its descriptor and jars"},
		{"no jars", func(spec *pluginComponentSpec) { spec.Jars = []pluginComponentJar{} }, "at least one jar"},
		{"plugin escape", func(spec *pluginComponentSpec) { spec.PluginDirectory = "plugins/../outside" }, "pluginDirectory"},
		{"escaping destination", func(spec *pluginComponentSpec) { spec.Jars[0].Destination = "../outside.jar" }, "invalid relative path"},
		{"case collision", func(spec *pluginComponentSpec) { spec.Jars[0].Destination = "lib/JSON.jar" }, "conflicting plugin destinations"},
		{"metadata for another jar", func(spec *pluginComponentSpec) { spec.Jars[0].Metadata = spec.Jars[1].Metadata }, "exactly one regular file"},
		{"missing metadata", func(spec *pluginComponentSpec) { spec.Jars[0].Metadata = "metadata/missing.json" }, "missing.json"},
		{"missing descriptor", func(spec *pluginComponentSpec) { spec.Descriptor = "metadata/missing.xml" }, "missing.xml"},
		{"descriptor in payload", func(spec *pluginComponentSpec) { spec.Descriptor = "payload/intellij.json.jar" }, "overlaps payload"},
		{"empty descriptor", func(spec *pluginComponentSpec) { spec.Descriptor = "" }, "invalid declared artifact path"},
		{"empty source", func(spec *pluginComponentSpec) { spec.Jars[0].Source = "" }, "invalid declared artifact path"},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			test.Chdir(test.TempDir())
			spec := packedPluginComponentFixture(test)
			scenario.change(&spec)
			writePluginJSON(test, "component-spec.json", spec)
			var output, errors bytes.Buffer
			if code := run(pluginComponentArgs(), &output, &errors); code == 0 || !strings.Contains(errors.String(), scenario.want) {
				test.Fatalf("exit %d, error %q; expected %s", code, errors.String(), scenario.want)
			}
			for _, file := range []string{"component.json", "component.plugin-classpath-part"} {
				if _, err := os.Lstat(file); !os.IsNotExist(err) {
					test.Fatalf("invalid metadata produced %s: %v", file, err)
				}
			}
		})
	}
}
