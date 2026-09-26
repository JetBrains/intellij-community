// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"bytes"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

func TestParseSourceManifestPolicy(t *testing.T) {
	for _, mode := range []ManifestMode{ManifestKeep, ManifestDrop, ManifestCoverageAgent, ManifestRewriteBootClassPath} {
		t.Run(string(mode), func(t *testing.T) {
			specs, err := parseRecipe(t, "output=out/a.jar\nlibrary=first.jar\nlibrary=agent.jar\nsource-manifest="+string(mode)+
				"\nmodule=owner.jar\noutput=out/b.jar\nmodule=other.jar\n")
			if err != nil {
				t.Fatal(err)
			}
			if specs[0].Sources[0].Manifest != "" || specs[0].Sources[1].Manifest != mode ||
				specs[0].Sources[2].Manifest != "" || specs[1].Sources[0].Manifest != "" {
				t.Fatalf("policy did not stay on the selected source: %+v", specs)
			}
		})
	}
	for _, recipe := range []string{
		"source-manifest=keep\noutput=out.jar\nmodule=in.jar\n",
		"output=out.jar\nsource-manifest=keep\nmodule=in.jar\n",
		"output=out.jar\nlibrary=in.jar\nsource-manifest=unknown\n",
		"output=out.jar\nlibrary=in.jar\nsource-manifest=\n",
		"output=out.jar\nlibrary=in.jar\nsource-manifest=keep\nsource-manifest=drop\n",
		"output=out.jar\nfile=META-INF/MANIFEST.MF=in.txt\nsource-manifest=keep\n",
		"output=out.jar\npatch=META-INF/MANIFEST.MF=in.txt\nsource-manifest=keep\n",
		"output=first.jar\nlibrary=in.jar\noutput=second.jar\nsource-manifest=keep\nmodule=other.jar\n",
	} {
		if _, err := parseRecipe(t, recipe); err == nil {
			t.Errorf("accepted invalid source policy: %s", recipe)
		}
	}
}

func TestIndependentProductionEntityRecipe(t *testing.T) {
	library := writeZipJar(t, "library.jar",
		sourceEntry{name: "META-INF/listOfEntities.txt", data: "  Library\n"},
		sourceEntry{name: "duplicate.txt", data: "library"},
	)
	before := writeZipJar(t, "before.jar", sourceEntry{name: "META-INF/listOfEntities.txt", data: "\nBefore  "})
	owner := writeZipJar(t, "owner.jar",
		sourceEntry{name: "META-INF/listOfEntities.txt", data: "\tOwner\r\n"},
		sourceEntry{name: "duplicate.txt", data: "module"},
	)
	after := writeZipJar(t, "after.jar", sourceEntry{name: "META-INF/listOfEntities.txt", data: " After "})
	sourceLines := "library=" + library + "\nmodule=" + before + "\nmodule=" + owner + "\nmodule=" + after + "\n"
	specs, err := parseRecipe(t, "output=legacy.jar\n"+sourceLines+"output=owner_content_module_jar.production.jar\nmerge-entities=true\n"+sourceLines)
	if err != nil {
		t.Fatal(err)
	}
	legacy, _ := pack(t, specs[0])
	production, _ := pack(t, specs[1])
	unchanged, _ := pack(t, MergeSpec{Output: "legacy.jar", Sources: []Source{
		{Path: library, Filter: LibraryNameFilter},
		{Path: before, Filter: ModuleOutputNameFilter},
		{Path: owner, Filter: ModuleOutputNameFilter},
		{Path: after, Filter: ModuleOutputNameFilter},
	}})
	if !bytes.Equal(legacy, unchanged) || packedEntry(t, legacy, "META-INF/listOfEntities.txt") != "  Library\n" {
		t.Fatal("the legacy recipe changed")
	}
	if got := packedEntry(t, production, "META-INF/listOfEntities.txt"); got != "Library\nBefore\nOwner\nAfter" {
		t.Fatalf("entity source order changed: %q", got)
	}
	if packedEntry(t, production, "duplicate.txt") != "library" {
		t.Fatal("the production recipe changed first-wins precedence")
	}
	names := entryNames(t, production)
	if slices.Index(names, "META-INF/listOfEntities.txt") < slices.Index(names, "duplicate.txt") {
		t.Fatalf("entities did not follow the source entries: %v", names)
	}
}

func TestIndependentProductionCoverageRecipe(t *testing.T) {
	unrelated := writeZipJar(t, "unrelated.jar", sourceEntry{name: ManifestEntryName, data: "Boot-Class-Path: unrelated.jar\r\nUnrelated: true\r\n"})
	owner := writeZipJar(t, "owner.jar", sourceEntry{name: ManifestEntryName, data: "Boot-Class-Path: owner.jar\r\n"})
	for _, agentManifest := range []string{
		"Boot-Class-Path: intellij-coverage-agent-1.2.3.jar\r\nAgent: true\r\n",
		"Boot-Class-Path: custom-agent.jar\r\nAgent: true\r\n",
	} {
		agent := writeZipJar(t, "intellij-coverage-agent-1.2.3.jar", sourceEntry{name: ManifestEntryName, data: agentManifest})
		prefix := "library=" + unrelated + "\nlibrary=" + agent + "\n"
		suffix := "module=" + owner + "\n"
		specs, err := parseRecipe(t, "output=agent_content_module_jar.production.jar\nmerge-entities=true\n"+prefix+"source-manifest=coverage-agent\n"+suffix)
		if err != nil {
			t.Fatal(err)
		}
		production, _ := pack(t, specs[0])
		want := strings.ReplaceAll(agentManifest, "intellij-coverage-agent-1.2.3.jar", "intellij.platform.coverage.agent.jar")
		if got := packedEntry(t, production, ManifestEntryName); got != want {
			t.Fatalf("the production manifest is %q, want %q", got, want)
		}
	}
}

func parseRecipe(t *testing.T, lines string) ([]MergeSpec, error) {
	t.Helper()
	path := filepath.Join(t.TempDir(), "recipe.params")
	if err := os.WriteFile(path, []byte(lines), 0o644); err != nil {
		t.Fatal(err)
	}
	return ParseFlagFile(path, "/exec/root")
}

func TestParseFlagFileGroupsByOutputAndKeepsSourceOrder(t *testing.T) {
	specs, err := parseRecipe(t, "output=out/a.jar\nkeep-manifest=true\nlibrary=lib/one.jar\nmodule=mod/a.jar\n"+
		"output=out/b.jar\nmerge-entities=true\nmodule=mod/b.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if len(specs) != 2 {
		t.Fatalf("%d groups, want 2", len(specs))
	}
	if got, want := specs[0].Output, filepath.Join("/exec/root", "out/a.jar"); got != want {
		t.Errorf("output is %q, want %q resolved against the base directory", got, want)
	}
	if !specs[0].KeepManifest || specs[1].KeepManifest {
		t.Error("keep-manifest applied to the wrong group")
	}
	if specs[0].MergeEntities || !specs[1].MergeEntities {
		t.Error("merge-entities applied to the wrong group")
	}
	// The order is the precedence the merge uses, so it is part of the grammar rather than an accident of parsing.
	if got, want := specs[0].Sources[0].Path, filepath.Join("/exec/root", "lib/one.jar"); got != want {
		t.Errorf("first source is %q, want the library %q", got, want)
	}
}

func TestParseFlagFileRejectsWhatWouldChangeBytesSilently(t *testing.T) {
	for name, lines := range map[string]string{
		"an option before any output":      "module=mod/a.jar\n",
		"a line that is not an assignment": "output=out/a.jar\nmodule\n",
		"an unknown option":                "output=out/a.jar\nmodul=mod/a.jar\n",
		"a mis-spelled boolean":            "output=out/a.jar\nkeep-manifest=TRUE\nmodule=mod/a.jar\n",
		"a group with no source":           "output=out/a.jar\n",
		"the same output twice":            "output=out/a.jar\nmodule=mod/a.jar\noutput=out/a.jar\nmodule=mod/b.jar\n",
		"two trace destinations": "output=out/a.jar\ntrace-file=out/a.jar.spans.json\nmodule=mod/a.jar\n" +
			"output=out/b.jar\ntrace-file=out/b.jar.spans.json\nmodule=mod/b.jar\n",
	} {
		if _, err := parseRecipe(t, lines); err == nil {
			t.Errorf("%s was accepted", name)
		}
	}
}

func TestParseFlagFileReadsTheTraceDestination(t *testing.T) {
	// The line the packing rule writes, in the position it writes it: immediately after `output=`, because that is
	// where the group starts. It is a worker's only per-request channel - a `--trace-file=` on the spawn would belong to
	// the worker process and to its WorkerKey.
	specs, err := parseRecipe(t, "output=out/a.jar\ntrace-file=out/a.jar.spans.json\nmodule=mod/a.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if got, want := specs[0].TraceFile, filepath.Join("/exec/root", "out/a.jar.spans.json"); got != want {
		t.Errorf("trace-file is %q, want %q resolved against the base directory like every other path", got, want)
	}
	// And it changes nothing about the pack: it is not a source, and the jar is the same jar.
	if len(specs[0].Sources) != 1 {
		t.Errorf("trace-file was counted as a source: %v", specs[0].Sources)
	}

	absolute, err := parseRecipe(t, "output=out/a.jar\ntrace-file=/tmp/a.spans.json\nmodule=mod/a.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if got, want := absolute[0].TraceFile, "/tmp/a.spans.json"; got != want {
		t.Errorf("an absolute trace-file is %q, want %q untouched", got, want)
	}

	// The same destination twice is what a flag file concatenated from one action's command lines looks like; only two
	// *different* ones have no answer.
	repeated, err := parseRecipe(t, "output=out/a.jar\ntrace-file=out/a.spans.json\nmodule=mod/a.jar\n"+
		"output=out/b.jar\ntrace-file=out/a.spans.json\nmodule=mod/b.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if repeated[1].TraceFile != repeated[0].TraceFile {
		t.Error("the second group lost the destination")
	}

	// A recipe without one is the normal case: no flag, no line, no trace.
	none, err := parseRecipe(t, "output=out/a.jar\nmodule=mod/a.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if none[0].TraceFile != "" {
		t.Errorf("a recipe with no trace-file produced %q", none[0].TraceFile)
	}
}

func TestParseFlagFileMetadataDestination(t *testing.T) {
	specs, err := parseRecipe(t, "output=out/a.jar\nmetadata-file=out/a.metadata.json\nmodule=mod/a.jar\n")
	if err != nil || len(specs) != 1 || specs[0].MetadataFile != "/exec/root/out/a.metadata.json" || len(specs[0].Sources) != 1 {
		t.Fatalf("specs = %#v, error = %v", specs, err)
	}
	for _, recipe := range []string{
		"metadata-file=a.json\noutput=a.jar\nmodule=in.jar\n",
		"output=a.jar\nmetadata-file=\nmodule=in.jar\n",
		"output=a.jar\nmetadata-file=one.json\nmetadata-file=two.json\nmodule=in.jar\n",
		"output=a.jar\nmetadata-file=a.jar\nmodule=in.jar\n",
		"output=a.jar\nmetadata-file=in.jar\nmodule=in.jar\n",
		"output=a.jar\nmetadata-file=trace.json\ntrace-file=trace.json\nmodule=in.jar\n",
		"output=a.jar\nmetadata-file=all.json\nmodule=in.jar\noutput=b.jar\nmetadata-file=all.json\nmodule=in.jar\n",
		"output=a.jar\nmetadata-file=b.jar\nmodule=in.jar\noutput=b.jar\nmodule=in.jar\n",
	} {
		if _, err := parseRecipe(t, recipe); err == nil {
			t.Errorf("accepted unsafe metadata destination: %s", recipe)
		}
	}
}

func TestParseFlagFileCleansAbsolutePaths(t *testing.T) {
	specs, err := parseRecipe(t, "output=/tmp/./a.jar\nmetadata-file=/tmp/unused/../a.metadata.json\n"+
		"trace-file=/tmp/./a.spans.json\nmodule=/tmp/./module.jar\nlibrary=/tmp/unused/../library.jar\n"+
		"file=resource.txt=/tmp/./resource.txt\npatch=META-INF/plugin.xml=/tmp/unused/../plugin.xml\n")
	if err != nil {
		t.Fatal(err)
	}
	spec := specs[0]
	paths := []string{spec.Output, spec.MetadataFile, spec.TraceFile}
	for _, source := range spec.Sources {
		paths = append(paths, source.Path)
	}
	for _, path := range paths {
		if path != filepath.Clean(path) {
			t.Errorf("path is not clean: %q", path)
		}
	}
}

func TestParseFlagFileRejectsAbsolutePathAliases(t *testing.T) {
	for _, alias := range []string{"./", "unused/../"} {
		for name, recipe := range map[string]string{
			"duplicate outputs":               "output=/tmp/a.jar\nmodule=in.jar\noutput=ALIASa.jar\nmodule=in.jar\n",
			"metadata aliases jar":            "output=/tmp/a.jar\nmetadata-file=ALIASa.jar\nmodule=in.jar\n",
			"jar aliases metadata":            "output=ALIASa.jar\nmetadata-file=/tmp/a.jar\nmodule=in.jar\n",
			"metadata aliases input":          "output=out.jar\nmetadata-file=ALIASin.jar\nmodule=/tmp/in.jar\n",
			"module aliases metadata":         "output=out.jar\nmetadata-file=/tmp/in.jar\nmodule=ALIASin.jar\n",
			"library aliases metadata":        "output=out.jar\nmetadata-file=/tmp/in.jar\nlibrary=ALIASin.jar\n",
			"file aliases metadata":           "output=out.jar\nmetadata-file=/tmp/in.jar\nfile=entry=ALIASin.jar\n",
			"patch aliases metadata":          "output=out.jar\nmetadata-file=/tmp/in.jar\npatch=entry=ALIASin.jar\n",
			"metadata aliases trace":          "output=out.jar\nmetadata-file=ALIAStrace.json\ntrace-file=/tmp/trace.json\nmodule=in.jar\n",
			"trace aliases metadata":          "output=out.jar\nmetadata-file=/tmp/trace.json\ntrace-file=ALIAStrace.json\nmodule=in.jar\n",
			"metadata aliases another group":  "output=a.jar\nmetadata-file=/tmp/all.json\nmodule=in.jar\noutput=b.jar\nmetadata-file=ALIASall.json\nmodule=in.jar\n",
			"metadata aliases another output": "output=a.jar\nmetadata-file=ALIASb.jar\nmodule=in.jar\noutput=/tmp/b.jar\nmodule=in.jar\n",
			"metadata aliases another input":  "output=a.jar\nmetadata-file=ALIASin.jar\nmodule=in.jar\noutput=b.jar\nmodule=/tmp/in.jar\n",
		} {
			t.Run(alias+name, func(t *testing.T) {
				recipe = strings.ReplaceAll(recipe, "ALIAS", "/tmp/"+alias)
				if _, err := parseRecipe(t, recipe); err == nil {
					t.Fatalf("accepted conflicting paths: %s", recipe)
				}
			})
		}
	}
}

func TestParsePatchAndEntityMerge(t *testing.T) {
	specs, err := parseRecipe(t, "output=out/plugin.jar\nmerge-entities=true\nreject-native-entries=true\nlibrary=lib.jar\npatch=META-INF/plugin.xml=descriptor.xml\nmodule=main.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if !specs[0].MergeEntities || !specs[0].RejectNativeEntries || len(specs[0].Sources) != 3 || !specs[0].Sources[1].Patch || specs[0].Sources[1].Name != "META-INF/plugin.xml" {
		t.Fatalf("unexpected patch recipe: %+v", specs[0])
	}
	if _, err := parseRecipe(t, "output=out/plugin.jar\nmerge-entities=yes\nmodule=main.jar\n"); err == nil {
		t.Fatal("accepted a non-boolean entity merge option")
	}
	if _, err := parseRecipe(t, "output=out/plugin.jar\nreject-native-entries=yes\nmodule=main.jar\n"); err == nil {
		t.Fatal("accepted a non-boolean native option")
	}
}

func TestParseFlagFileReadsAFileSourceAndItsEntryName(t *testing.T) {
	specs, err := parseRecipe(t, "output=out/a.jar\nfile=META-INF/plugin.xml=gen/a.plugin.xml\nmodule=mod/a.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	source := specs[0].Sources[0]
	if got, want := source.Name, "META-INF/plugin.xml"; got != want {
		t.Errorf("entry name is %q, want %q", got, want)
	}
	if got, want := source.Path, filepath.Join("/exec/root", "gen/a.plugin.xml"); got != want {
		t.Errorf("path is %q, want %q resolved against the base directory", got, want)
	}
	if source.Filter != nil {
		t.Error("a source of one entry has nothing to select, so its filter must stay nil")
	}
}

func TestParseFlagFileRejectsAFileSourceItCannotRead(t *testing.T) {
	for name, lines := range map[string]string{
		"no entry name":    "output=out/a.jar\nfile=gen/a.plugin.xml\n",
		"empty entry name": "output=out/a.jar\nfile==gen/a.plugin.xml\n",
		"empty path":       "output=out/a.jar\nfile=META-INF/plugin.xml=\n",
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := parseRecipe(t, lines); err == nil {
				t.Error("want an error rather than a source that packs the wrong bytes")
			}
		})
	}
}
