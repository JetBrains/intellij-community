package pluginpack

import (
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

func TestOwnedTreeMetadataValidatesWithoutPayload(test *testing.T) {
	recipe, catalogue := treePlan(filepath.Join(test.TempDir(), "absent"))
	catalogue.Artifacts[0].Tree = &OwnedTree{Version: TreeVersion, Artifact: "tree", Plugin: recipe.Plugin, LayoutSignature: recipe.LayoutSignature,
		RootMode: 0o710, Entries: []filemetadata.Entry{{RelativePath: "empty", Type: "directory", Mode: 0}}}
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		test.Fatal(err)
	}
	catalogue.Artifacts[0].Tree.RootMode = 0
	catalogue.Artifacts[0].Tree.Entries[0].RelativePath = "changed"
	inputs := execution.Inputs()
	inputs[0].Tree.Entries[0].RelativePath = "changed-again"
	if actual := execution.Inputs()[0].Tree; actual.RootMode != 0o710 || actual.Entries[0].RelativePath != "empty" {
		test.Fatal("metadata aliases caller storage")
	}
}

func TestOwnedTreeMetadataRejectsInvalidContracts(test *testing.T) {
	for _, scenario := range []string{"v1", "metadata-v1", "metadata-v3", "owner", "plugin", "signature", "file-root", "root-mode", "nil-entries", "duplicate", "case", "unicode", "escape", "newline", "missing-parent", "file-parent", "file-mode", "file-size", "executable", "directory-hash", "unknown-type", "library", "ordinary-copy"} {
		test.Run(scenario, func(test *testing.T) {
			recipe, catalogue := treePlan(filepath.Join(test.TempDir(), "absent"))
			tree := &OwnedTree{Version: TreeVersion, Artifact: "tree", Plugin: recipe.Plugin, LayoutSignature: recipe.LayoutSignature,
				RootMode: 0o755, Entries: []filemetadata.Entry{{RelativePath: "file", Type: "file", Mode: 0o644, Size: 1, Hash: 1}}}
			catalogue.Artifacts[0].Tree = tree
			switch scenario {
			case "v1":
				recipe.Version = Version
			case "metadata-v1":
				tree.Version = 1
			case "metadata-v3":
				tree.Version = 3
			case "owner":
				tree.Artifact = "other"
			case "plugin":
				tree.Plugin = "other"
			case "signature":
				tree.LayoutSignature = "stale"
			case "file-root":
				catalogue.Artifacts[0].Kind = "file"
			case "root-mode":
				tree.RootMode = 0o1755
			case "nil-entries":
				tree.Entries = nil
			case "duplicate":
				tree.Entries = append(tree.Entries, tree.Entries[0])
			case "case", "unicode":
				first, second := "File", "file"
				if scenario == "unicode" {
					first, second = "Café", "Cafe\u0301"
				}
				tree.Entries = []filemetadata.Entry{{RelativePath: first, Type: "directory"}, {RelativePath: second, Type: "directory"}}
			case "escape":
				tree.Entries[0].RelativePath = "../escape"
			case "newline":
				tree.Entries[0].RelativePath = "bad\nname"
			case "missing-parent":
				tree.Entries[0].RelativePath = "absent/file"
			case "file-parent":
				tree.Entries = append(tree.Entries, filemetadata.Entry{RelativePath: "file/child", Type: "directory"})
			case "file-mode":
				tree.Entries[0].Mode = 0o1644
			case "file-size":
				tree.Entries[0].Size = -1
			case "executable":
				tree.Entries[0].Executable = true
			case "directory-hash":
				tree.Entries[0].Type = "directory"
			case "unknown-type":
				tree.Entries[0].Type = "device"
			case "library":
				catalogue.Libraries = []Library{{ID: "library", Files: []Reference{{Artifact: "tree", Path: "file"}}}}
			case "ordinary-copy":
				recipe.Assets = append(recipe.Assets, Asset{Destination: "resource", Producer: "remainder"})
				recipe.Operations = append(recipe.Operations, Operation{Kind: "copy", Destination: "resource", Input: &Reference{Artifact: "tree", Path: "file"}})
			}
			if _, err := Plan(recipe, catalogue); err == nil {
				test.Fatal("accepted invalid owned tree metadata")
			}
		})
	}
}

func TestOwnedTreeMetadataRejectsUnsafeLogicalLinks(test *testing.T) {
	for _, target := range []string{"missing", "link", ".", "file/child", "./file/..", "../outside", "/absolute", "bad\nname"} {
		test.Run(target, func(test *testing.T) {
			recipe, catalogue := treePlan(filepath.Join(test.TempDir(), "absent"))
			link := filepath.Join(test.TempDir(), "link")
			if err := os.Symlink(target, link); err != nil {
				test.Fatal(err)
			}
			entry, _ := filemetadata.Inspect(link, "link")
			catalogue.Artifacts[0].Tree = &OwnedTree{Version: TreeVersion, Artifact: "tree", Plugin: recipe.Plugin, LayoutSignature: recipe.LayoutSignature,
				RootMode: 0o755, Entries: []filemetadata.Entry{{RelativePath: "file", Type: "file", Mode: 0o644}, entry}}
			if _, err := Plan(recipe, catalogue); err == nil {
				test.Fatal("accepted unsafe logical link")
			}
		})
	}
}

func treePlan(root string) (Recipe, Catalogue) {
	excluded := false
	assets := []Asset{
		{Destination: "kotlinc", Producer: "remainder", Kind: "tree", ClassPath: &excluded},
		{Destination: "lib/independent.jar", Producer: "independent", Artifact: "independent"},
	}
	recipe := Recipe{Version: TreeVersion, Plugin: "tree", LayoutSignature: "tree-v2", Assets: assets,
		Operations: []Operation{{Kind: "copy-tree", Destination: "kotlinc", Input: &Reference{Artifact: "tree"}}}}
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{{ID: "tree", Kind: "directory", Root: root}}}
	return recipe, catalogue
}

func TestTreePlanningIsVersionedAndDoesNotReadDirectories(test *testing.T) {
	recipe, catalogue := treePlan(filepath.Join(test.TempDir(), "missing"))
	execution, err := Plan(recipe, catalogue)
	if err != nil || !reflect.DeepEqual(execution.Inputs(), catalogue.Artifacts) {
		test.Fatalf("tree planning opened the source or changed its ownership: %v", err)
	}
	for _, version := range []int{Version, 3} {
		recipe.Version = version
		if _, err := Plan(recipe, catalogue); err == nil {
			test.Fatalf("accepted tree version %d", version)
		}
	}
	legacy, legacyCatalogue := samplePlan(test.TempDir())
	legacy.Version = TreeVersion
	if _, err := Plan(legacy, legacyCatalogue); err != nil {
		test.Fatalf("version 2 rejected non-tree operations: %v", err)
	}
}

func TestTreePlanAllowsDescendantAssetsWithoutReadingTree(test *testing.T) {
	recipe, catalogue := treePlan(filepath.Join(test.TempDir(), "missing"))
	recipe.Assets[1].Destination = "kotlinc/lib/compiler.jar"
	if _, err := Plan(recipe, catalogue); err != nil {
		test.Fatal(err)
	}
}

func TestTreePlanRejectsUnsafeOwnershipAndOperationOptions(test *testing.T) {
	for _, scenario := range []struct {
		name   string
		mutate func(*Recipe, *Catalogue)
	}{
		{"root collision", func(recipe *Recipe, _ *Catalogue) { recipe.Assets[1].Destination = "kotlinc" }},
		{"case alias", func(recipe *Recipe, _ *Catalogue) { recipe.Assets[1].Destination = "KOTLINC/lib/compiler.jar" }},
		{"unicode alias", func(recipe *Recipe, _ *Catalogue) {
			recipe.Assets[0].Destination = "é"
			recipe.Operations[0].Destination = "é"
			recipe.Assets[1].Destination = "e\u0301/nested"
		}},
		{"file ancestor", func(recipe *Recipe, _ *Catalogue) {
			recipe.Assets[0].Destination = "lib/kotlinc"
			recipe.Operations[0].Destination = "lib/kotlinc"
			recipe.Assets[1].Destination = "lib"
		}},
		{"independent tree", func(recipe *Recipe, _ *Catalogue) {
			recipe.Assets[0].Producer = "independent"
			recipe.Assets[0].Artifact = "independent-tree"
		}},
		{"classpath", func(recipe *Recipe, _ *Catalogue) { recipe.Assets[0].ClassPath = nil }},
		{"path", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Input.Path = "child" }},
		{"mode", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Mode = 0o755 }},
		{"sources", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Sources = []Source{{Kind: "entries"}} }},
		{"target", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Target = "other" }},
		{"options", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Options = &JarOptions{Directories: "none"} }},
		{"no input", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Input = nil }},
		{"file input", func(_ *Recipe, catalogue *Catalogue) { catalogue.Artifacts[0].Kind = "file" }},
		{"ordinary copy", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Kind = "copy" }},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			recipe, catalogue := treePlan(filepath.Join(test.TempDir(), "missing"))
			scenario.mutate(&recipe, &catalogue)
			if _, err := Plan(recipe, catalogue); err == nil {
				test.Fatal("accepted an unsafe tree contract")
			}
		})
	}
	recipe, catalogue := treePlan(filepath.Join(test.TempDir(), "missing"))
	catalogue.Artifacts = append(catalogue.Artifacts, Artifact{ID: "independent", Kind: "file", Root: "absent.jar"})
	if _, err := Plan(recipe, catalogue); err == nil {
		test.Fatal("accepted an independent input")
	}
}

// nativeSelectPlan is the recipe the planfile package compiles for a native-select operation: the jar reserves the
// natives of the archive, and the distribution tree selects the natives of one platform. Plan opens no file.
func nativeSelectPlan() (Recipe, Catalogue) {
	excluded := false
	native := &Reference{Artifact: "native"}
	recipe := Recipe{Version: ScopedVersion, Plugin: "vcs", LayoutSignature: "vcs-v3",
		Assets: []Asset{
			{Destination: "lib/sqlite.jar", Producer: "remainder"},
			{Destination: "lib/native", Producer: "remainder", Kind: "tree", ClassPath: &excluded, Scope: DistributionScope}},
		Operations: []Operation{
			{Kind: "jar", Destination: "lib/sqlite.jar", Options: &JarOptions{Directories: "none"},
				Sources: []Source{{Kind: "archive", Input: native, Filter: "library", Manifest: "keep", ReserveNatives: true}}},
			{Kind: "native-tree", Destination: "lib/native", Scope: DistributionScope, Input: native, Native: &NativeTarget{OS: "linux", Arch: "x64"}}}}
	return recipe, Catalogue{Version: Version, Artifacts: []Artifact{{ID: "native", Kind: "file", Root: "absent-native.jar"}}}
}

func TestNativeTreePlanValidatesTheTargetAndTheReservation(test *testing.T) {
	recipe, catalogue := nativeSelectPlan()
	if _, err := Plan(recipe, catalogue); err != nil {
		test.Fatal(err)
	}
	for _, scenario := range []struct {
		name   string
		mutate func(*Recipe, *Catalogue)
		want   string
	}{
		{"version 1", func(recipe *Recipe, _ *Catalogue) {
			recipe.Version = Version
			recipe.Assets[1].Scope, recipe.Operations[1].Scope = "", ""
		}, "requires version 2 or 3"},
		{"no target", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[1].Native = nil }, "native-tree requires"},
		{"unknown os", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[1].Native.OS = "mac" }, "unknown native target"},
		{"unknown arch", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[1].Native.Arch = "arm64" }, "unknown native target"},
		{"universal arch", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[1].Native.Arch = "" }, "unknown native target"},
		{"no input", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[1].Input = nil }, "native-tree requires"},
		{"path input", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[1].Input = &Reference{Artifact: "native", Path: "child"}
		}, "native-tree requires"},
		{"mode", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[1].Mode = 0o755 }, "native-tree requires"},
		{"sources", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[1].Sources = []Source{{Kind: "entries"}} }, "native-tree requires"},
		{"directory input", func(recipe *Recipe, catalogue *Catalogue) {
			recipe.Assets, recipe.Operations = recipe.Assets[1:], recipe.Operations[1:]
			catalogue.Artifacts[0].Kind = "directory"
		}, "declared archive file"},
		{"target on a copy-tree", func(recipe *Recipe, catalogue *Catalogue) {
			recipe.Assets, recipe.Operations = recipe.Assets[1:], recipe.Operations[1:]
			recipe.Operations[0].Kind = "copy-tree"
			catalogue.Artifacts[0].Kind = "directory"
		}, "only a native-tree operation carries a native target"},
		{"reservation on a library source", func(recipe *Recipe, catalogue *Catalogue) {
			recipe.Operations[0].Sources[0] = Source{Kind: "library", Library: "natives", Filter: "library", Manifest: "keep", ReserveNatives: true}
			catalogue.Libraries = []Library{{ID: "natives", Files: []Reference{{Artifact: "native"}}}}
		}, "native reservation requires"},
		{"reservation with overrides", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Overrides = []EntryOverride{{Kind: "reserve", Name: "sqlite/x.so"}}
		}, "native reservation requires"},
		{"reservation on a layout source", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0] = Source{Kind: "layout", Manifest: "keep", ReserveNatives: true, Layout: &LayoutAssets{}}
		}, "layout source requires"},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			recipe, catalogue := nativeSelectPlan()
			scenario.mutate(&recipe, &catalogue)
			if _, err := Plan(recipe, catalogue); err == nil || !strings.Contains(err.Error(), scenario.want) {
				test.Fatalf("expected %q, got %v", scenario.want, err)
			}
		})
	}
}

func TestPlanReservesTheDistributionTransportPrefixForVersionThreePluginAssets(test *testing.T) {
	excluded := false
	for _, destination := range []string{distributionTransportRoot, distributionTransportRoot + "/child"} {
		test.Run("reject "+destination, func(test *testing.T) {
			assets := []Asset{
				{Destination: destination, Producer: "independent", Artifact: "plugin"},
				{Destination: "lib/native.bin", Producer: "independent", Artifact: "distribution", ClassPath: &excluded, Scope: DistributionScope},
			}
			recipe := Recipe{Version: ScopedVersion, Plugin: "scoped", LayoutSignature: "scoped-v3", Assets: assets}
			if _, err := Plan(recipe, Catalogue{Version: Version}); err == nil || !strings.Contains(err.Error(), "reserved distribution transport path") {
				test.Fatalf("accepted reserved plugin destination %q: %v", destination, err)
			}
		})
		test.Run("version 2 "+destination, func(test *testing.T) {
			assets := []Asset{{Destination: destination, Producer: "independent", Artifact: "plugin"}}
			recipe := Recipe{Version: TreeVersion, Plugin: "legacy", LayoutSignature: "legacy-v2", Assets: assets}
			if _, err := Plan(recipe, Catalogue{Version: Version}); err != nil {
				test.Fatal(err)
			}
		})
		test.Run("distribution "+destination, func(test *testing.T) {
			assets := []Asset{{
				Destination: destination, Producer: "independent", Artifact: "distribution", ClassPath: &excluded, Scope: DistributionScope,
			}}
			recipe := Recipe{Version: ScopedVersion, Plugin: "scoped", LayoutSignature: "scoped-v3", Assets: assets}
			if _, err := Plan(recipe, Catalogue{Version: Version}); err != nil {
				test.Fatal(err)
			}
		})
	}
}

func TestDirectoryAssetsRejectConflictsAndFilePayloads(test *testing.T) {
	for _, mutate := range []struct {
		name  string
		apply func(*Recipe)
	}{
		{"file parent", func(recipe *Recipe) { recipe.Assets[0].Kind = "file" }},
		{"independent directory", func(recipe *Recipe) { recipe.Assets[0].Producer = "independent"; recipe.Assets[0].Artifact = "jar" }},
		{"directory input", func(recipe *Recipe) { recipe.Operations[0].Input = &Reference{Artifact: "raw"} }},
		{"directory target", func(recipe *Recipe) { recipe.Operations[0].Target = "empty" }},
		{"directory options", func(recipe *Recipe) { recipe.Operations[0].Options = &JarOptions{Directories: "none"} }},
		{"directory mode", func(recipe *Recipe) { recipe.Operations[0].Mode = 0o1777 }},
		{"wrong operation", func(recipe *Recipe) { recipe.Operations[0].Kind = "symlink"; recipe.Operations[0].Target = "dir/empty" }},
		{"case alias", func(recipe *Recipe) {
			recipe.Assets[1].Destination = "DIR/empty"
			recipe.Operations[1].Destination = "DIR/empty"
		}},
		{"parent traversal", func(recipe *Recipe) {
			recipe.Assets[1].Destination = "dir/../empty"
			recipe.Operations[1].Destination = "dir/../empty"
		}},
		{"link parent", func(recipe *Recipe) {
			recipe.Assets[0].Kind = "file"
			recipe.Operations[0].Kind = "symlink"
			recipe.Operations[0].Target = "elsewhere"
		}},
		{"duplicate", func(recipe *Recipe) { recipe.Assets = append(recipe.Assets, recipe.Assets[0]) }},
	} {
		test.Run(mutate.name, func(test *testing.T) {
			recipe := Recipe{Version: Version, Plugin: "dirs", LayoutSignature: "dirs-v1", Assets: []Asset{
				{Destination: "dir", Producer: "remainder", Kind: "directory"},
				{Destination: "dir/empty", Producer: "remainder", Kind: "directory"},
			}, Operations: []Operation{{Kind: "directory", Destination: "dir"}, {Kind: "directory", Destination: "dir/empty"}}}
			mutate.apply(&recipe)
			if _, err := Plan(recipe, Catalogue{Version: Version}); err == nil {
				test.Fatal("accepted invalid directory plan")
			}
		})
	}
}

func samplePlan(root string) (Recipe, Catalogue) {
	assets := []Asset{
		{Destination: "lib/modules/separate.jar", Producer: "independent", Artifact: "packed-separate"},
		{Destination: "lib/plugin.jar", Producer: "remainder"},
	}
	recipe := Recipe{
		Version: Version, Plugin: "example", LayoutSignature: "ordered-layout-v1", Assets: assets,
		Operations: []Operation{{Kind: "jar", Destination: "lib/plugin.jar", Options: &JarOptions{Directories: "none", MergeEntities: true},
			Sources: []Source{{Kind: "archive", Input: &Reference{Artifact: "module"}, Filter: "module", Manifest: "drop"}}}},
	}
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{{ID: "module", Kind: "file", Root: filepath.Join(root, "module.jar")}}}
	return recipe, catalogue
}

func TestPlanDoesNotReadPayloads(t *testing.T) {
	recipe, catalogue := samplePlan(filepath.Join(t.TempDir(), "does-not-exist"))
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(execution.Inputs(), catalogue.Artifacts) {
		t.Fatalf("unexpected action inputs: %#v", execution.Inputs())
	}
	execution.Inputs()[0].ID = "mutated"
	recipe.Operations[0].Sources[0].Input.Artifact = "mutated"
	recipe.Operations[0].Options.Directories = "mutated"
	recipe.Assets[1].Destination = "mutated"
	if execution.inputs[0].ID != "module" || execution.recipe.Operations[0].Sources[0].Input.Artifact != "module" ||
		execution.recipe.Operations[0].Options.Directories != "none" || execution.recipe.Assets[1].Destination != "lib/plugin.jar" {
		t.Fatal("a caller changed the validated plan")
	}
}

func TestPlanOwnsEmptyPreparedSources(test *testing.T) {
	recipe, catalogue := samplePlan(test.TempDir())
	catalogue.Artifacts[0].Kind = "directory"
	recipe.Operations[0].Sources = []Source{{Kind: "entries", Prepared: "module", Manifest: "drop"}}
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		test.Fatal(err)
	}
	if !reflect.DeepEqual(execution.Inputs(), catalogue.Artifacts) {
		test.Fatalf("empty prepared source lost its owner: %+v", execution.Inputs())
	}
	for _, identifier := range []string{"missing", "packed-separate", " bad "} {
		recipe.Operations[0].Sources[0].Prepared = identifier
		if _, err := Plan(recipe, catalogue); err == nil || !strings.Contains(err.Error(), "prepared source") {
			test.Fatalf("accepted invalid prepared owner %q: %v", identifier, err)
		}
	}
	recipe.Operations[0].Sources[0].Prepared = "module"
	catalogue.Artifacts[0].Kind = "file"
	if _, err := Plan(recipe, catalogue); err == nil || !strings.Contains(err.Error(), "declared directory") {
		test.Fatalf("accepted a file as a prepared directory: %v", err)
	}
}

func TestPreparedFileReferencesRequireExactMetadata(test *testing.T) {
	for _, version := range []int{Version, TreeVersion} {
		for _, name := range []string{"", "missing", "bin", "current", "bin/../bin/tool", "../outside", "bin/TOOL"} {
			test.Run(fmt.Sprintf("v%d/%s", version, name), func(test *testing.T) {
				recipe, catalogue, _ := ownedTreeFixture(test)
				recipe = preparedFileRecipe(recipe, version, "entries")
				recipe.Operations[0].Sources[0].Entries[0].Input.Path = name
				if _, err := Plan(recipe, catalogue); err == nil {
					test.Fatal("accepted a reference outside the regular metadata entries")
				}
			})
		}
	}
}

func TestPreparedFilePlanningDoesNotReadPayload(test *testing.T) {
	for _, version := range []int{Version, TreeVersion} {
		recipe, catalogue, _ := ownedTreeFixture(test)
		recipe = preparedFileRecipe(recipe, version, "entries")
		catalogue.Artifacts[0].Root = filepath.Join(test.TempDir(), "absent")
		if _, err := Plan(recipe, catalogue); err != nil {
			test.Fatal(err)
		}
	}
}

func TestPlanRejectsInvalidContracts(t *testing.T) {
	tests := []struct {
		name   string
		change func(*Recipe, *Catalogue)
		want   string
	}{
		{"recipe version", func(recipe *Recipe, _ *Catalogue) { recipe.Version = 0 }, "version"},
		{"catalogue version", func(_ *Recipe, catalogue *Catalogue) { catalogue.Version = 2 }, "version"},
		{"missing operation", func(recipe *Recipe, _ *Catalogue) { recipe.Operations = nil }, "missing remainder"},
		{"extra operation", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations = append(recipe.Operations, recipe.Operations[0])
		}, "conflicting"},
		{"independent destination", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Destination = recipe.Assets[0].Destination
		}, "unowned"},
		{"unknown operation", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Kind = "kotlin" }, "unknown operation"},
		{"unknown source", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Sources[0].Kind = "lazy" }, "unknown source"},
		{"missing reference", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Sources[0].Input = nil }, "missing input"},
		{"unknown reference", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Input.Artifact = "other"
		}, "unresolved input"},
		{"missing catalogue input", func(_ *Recipe, catalogue *Catalogue) { catalogue.Artifacts = nil }, "unresolved input"},
		{"undeclared catalogue input", func(_ *Recipe, catalogue *Catalogue) { catalogue.Artifacts[0].ID = "other" }, "unresolved input"},
		{"unused input", func(_ *Recipe, catalogue *Catalogue) {
			catalogue.Artifacts = append(catalogue.Artifacts, Artifact{ID: "unused", Kind: "file", Root: "/unused.jar"})
		}, "unused inputs"},
		{"invalid catalogue input", func(_ *Recipe, catalogue *Catalogue) { catalogue.Artifacts[0].ID = " module " }, "invalid"},
		{"unknown root kind", func(_ *Recipe, catalogue *Catalogue) { catalogue.Artifacts[0].Kind = "tree-scan" }, "root kind"},
		{"unclean root", func(_ *Recipe, catalogue *Catalogue) {
			catalogue.Artifacts[0].Root = "./" + catalogue.Artifacts[0].Root
		}, "invalid root"},
		{"file child", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Input.Path = "child"
		}, "relative path"},
		{"unknown filter", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Filter = "glob:**"
		}, "prepared entries"},
		{"unknown manifest", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Sources[0].Manifest = "auto" }, "manifest policy"},
		{"excludes off the module filter", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Filter = "all"
			recipe.Operations[0].Sources[0].Excludes = []string{"drop/**"}
		}, "excludes require"},
		{"excludes on prepared entries", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0] = Source{Kind: "entries", Manifest: "drop", Excludes: []string{"drop/**"}}
		}, "prepared entries cannot"},
		{"invalid exclude", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Excludes = []string{"drop/**", "{unclosed"}
		}, "invalid exclude"},
		{"unknown directory mode", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Options.Directories = "auto" }, "directory mode"},
		{"unknown library", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0] = Source{Kind: "library", Library: "unknown", Filter: "library", Manifest: "drop"}
		}, "library source"},
		{"setuid", func(recipe *Recipe, _ *Catalogue) { recipe.Operations[0].Mode = 0o4755 }, "file mode"},
		{"mixed operation", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Input = &Reference{Artifact: "module"}
		}, "jar operation"},
		{"unknown override", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Overrides = []EntryOverride{{Kind: "sign", Name: "native.so"}}
		}, "unknown override"},
		{"override manifest", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Overrides = []EntryOverride{{Kind: "reserve", Name: "META-INF/MANIFEST.MF"}}
		}, "unsupported override"},
		{"duplicate override", func(recipe *Recipe, _ *Catalogue) {
			recipe.Operations[0].Sources[0].Overrides = []EntryOverride{{Kind: "reserve", Name: "native.so"}, {Kind: "reserve", Name: "native.so"}}
		}, "conflicting"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			recipe, catalogue := samplePlan(t.TempDir())
			test.change(&recipe, &catalogue)
			if _, err := Plan(recipe, catalogue); err == nil || !strings.Contains(err.Error(), test.want) {
				t.Fatalf("expected %q, got %v", test.want, err)
			}
		})
	}
}

// TestPlanReadsTheModuleOfAReusedJarAsAPlainInput pins the two namespaces: the artifact of an independent asset is the
// module name of its reused jar, and the same module output can be a catalogue input of the remainder.
func TestPlanReadsTheModuleOfAReusedJarAsAPlainInput(t *testing.T) {
	recipe, catalogue := samplePlan(t.TempDir())
	recipe.Assets[0].Artifact = "module"
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(execution.Inputs(), catalogue.Artifacts) {
		t.Fatalf("unexpected action inputs: %#v", execution.Inputs())
	}
}

func TestPlanRejectsUnsafeDestinationsAndCollisions(t *testing.T) {
	for _, destination := range []string{"", "/absolute", "../escape", "lib/../../escape", "lib//double", "lib/./dot", "lib/trailing/", `lib\windows`, "C:drive", "lib/zero\x00", "lib/new\nline", "lib/modules/separate.jar", "LIB/MODULES/SEPARATE.JAR", "lib/modules/separate.jar/child", "lib/modules", "lib/trailing.", "lib/trailing ", "lib/CON.jar", "lib/NUL", "lib/LPT1.txt", "lib/<file>"} {
		t.Run(destination, func(t *testing.T) {
			recipe, catalogue := samplePlan(t.TempDir())
			recipe.Assets[1].Destination = destination
			recipe.Operations[0].Destination = destination
			if _, err := Plan(recipe, catalogue); err == nil {
				t.Fatal("accepted an unsafe destination")
			}
		})
	}
}

func TestPlanRejectsUnsafeDirectoryReferences(t *testing.T) {
	for _, relative := range []string{"", "../escape", "/absolute", "nested/../../escape", `nested\escape`, "./file"} {
		t.Run(relative, func(t *testing.T) {
			recipe, catalogue := samplePlan(t.TempDir())
			catalogue.Artifacts[0].Kind = "directory"
			recipe.Operations[0].Sources[0].Input.Path = relative
			if _, err := Plan(recipe, catalogue); err == nil {
				t.Fatal("accepted an unsafe directory reference")
			}
		})
	}
}

func TestPlanValidatesLinksWithoutOpeningTheirTargets(t *testing.T) {
	for _, target := range []string{"modules/separate.jar", "./modules/../modules/separate.jar", "../../escape", "/absolute", "absent.jar", "plugin.jar"} {
		t.Run(target, func(t *testing.T) {
			recipe, catalogue := samplePlan(t.TempDir())
			catalogue.Artifacts = nil
			recipe.Operations[0] = Operation{Kind: "symlink", Destination: "lib/plugin.jar", Target: target}
			_, err := Plan(recipe, catalogue)
			valid := strings.Contains(target, "separate.jar")
			if (err == nil) != valid {
				t.Fatalf("valid=%v, got %v", valid, err)
			}
		})
	}
}

func linkPlan(files []string, operations ...Operation) (Recipe, Catalogue) {
	recipe := Recipe{Version: Version, Plugin: "native", LayoutSignature: "native-layout", Operations: operations}
	for _, file := range files {
		recipe.Assets = append(recipe.Assets, Asset{Destination: file, Producer: "independent", Artifact: "independent:" + file})
	}
	for _, operation := range operations {
		recipe.Assets = append(recipe.Assets, Asset{Destination: operation.Destination, Producer: "remainder"})
	}
	return recipe, Catalogue{Version: Version}
}

func symbolicLink(destination, target string) Operation {
	return Operation{Kind: "symlink", Destination: destination, Target: target}
}

func TestLinkGraphUsesRawComponentsAndKnownDirectories(t *testing.T) {
	tests := []struct {
		name  string
		files []string
		links []Operation
		want  string
	}{
		{"framework", []string{"Framework/Versions/A/binary", "Framework/Versions/A/Headers/header.h"}, []Operation{
			symbolicLink("Framework/Versions/Current", "A"), symbolicLink("Framework/binary", "Versions/Current/binary"),
			symbolicLink("Framework/Headers", "Versions/Current/Headers"),
		}, ""},
		{"exact spelling", []string{"dir/file"}, []Operation{symbolicLink("alias", "./dir///"), symbolicLink("file", "alias//file")}, ""},
		{"dot dot after expansion", []string{"shallow/file"}, []Operation{
			symbolicLink("deep/inside/redirect", "../../shallow"), symbolicLink("file", "deep/inside/redirect/../shallow/file"),
		}, ""},
		{"directory boundary", []string{"directory/file"}, []Operation{symbolicLink("alias", "dir")}, "unresolved"},
		{"missing child", []string{"dir/file"}, []Operation{symbolicLink("alias", "dir/absent")}, "unresolved"},
		{"file traversal", []string{"file"}, []Operation{symbolicLink("alias", "file/child")}, "non-directory"},
		{"file trailing slash", []string{"file"}, []Operation{symbolicLink("alias", "file/")}, "non-directory"},
		{"file dot dot", []string{"file"}, []Operation{symbolicLink("alias", "file/../file")}, "non-directory"},
		{"missing cancelled directory", []string{"file"}, []Operation{symbolicLink("alias", "missing/../file")}, "unresolved"},
		{"direct cycle", nil, []Operation{symbolicLink("a", "b"), symbolicLink("b", "a")}, "cycle"},
		{"reviewer raw dot dot cycle", []string{"file"}, []Operation{symbolicLink("a", "b/../file"), symbolicLink("b", "a")}, "cycle"},
		{"prefix cycle", nil, []Operation{symbolicLink("a", "b/child"), symbolicLink("b", "a")}, "cycle"},
		{"ancestor cycle", []string{"dir/file"}, []Operation{symbolicLink("dir/back", "..")}, "cycle"},
		{"root cycle", []string{"file"}, []Operation{symbolicLink("back", ".")}, "cycle"},
		{"directory cross cycle", []string{"left/file", "right/file"}, []Operation{
			symbolicLink("left/to-right", "../right"), symbolicLink("right/to-left", "../left"),
		}, "cycle"},
		{"directory chain cycle", []string{"dir/file"}, []Operation{symbolicLink("alias", "dir"), symbolicLink("dir/back", "../alias")}, "cycle"},
		{"escape after expansion", []string{"shallow/file", "deep/escape"}, []Operation{
			symbolicLink("deep/inside/redirect", "../../shallow"), symbolicLink("escape", "deep/inside/redirect/../../escape"),
		}, "escapes"},
		{"ambiguous directory casing", []string{"Dir/first", "dir/second"}, []Operation{symbolicLink("alias", "Dir")}, "casing"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			recipe, catalogue := linkPlan(test.files, test.links...)
			execution, err := Plan(recipe, catalogue)
			if test.want != "" {
				if err == nil || !strings.Contains(err.Error(), test.want) {
					t.Fatalf("expected %q, got %v", test.want, err)
				}
			} else {
				if err != nil {
					t.Fatal(err)
				}
				if len(execution.Inputs()) != 0 {
					t.Fatalf("independent targets became inputs: %v", execution.Inputs())
				}
			}
		})
	}
}
