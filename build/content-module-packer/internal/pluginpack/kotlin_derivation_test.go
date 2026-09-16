package pluginpack_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/planfile"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

// derivationFixture is one plan file with its raw inputs on disk. inputs is the Starlark-shaped input catalogue,
// libraries included. present lists the output paths the fixture exists for.
type derivationFixture struct {
	plan    pluginpack.KotlinPlanFile
	inputs  pluginpack.Catalogue
	present []string
}

// moduleJar writes a module output jar with one class under the package of the module.
func moduleJar(t *testing.T, inputs, module string) pluginpack.Artifact {
	t.Helper()
	jar := filepath.Join(inputs, module+".jar")
	pluginpack.ArchiveTestFile(t, jar, [2]string{strings.ReplaceAll(module, ".", "/") + "/Main.class", "class of " + module},
		[2]string{"META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nModule: " + module + "\r\n\r\n"})
	return pluginpack.FileArtifact(module, jar)
}

// libraryJar writes a library jar and returns its artifact under the library member ID.
func libraryJar(t *testing.T, inputs, id, name string) pluginpack.Artifact {
	t.Helper()
	jar := filepath.Join(inputs, name)
	pluginpack.ArchiveTestFile(t, jar, [2]string{"org/" + strings.TrimSuffix(name, ".jar") + "/Library.class", "library " + name})
	return pluginpack.FileArtifact(id, jar)
}

func moduleSource(module string) pluginpack.KotlinJarSource {
	return pluginpack.KotlinJarSource{Input: module, Kind: "module", Filter: "module-v1"}
}

// signedPlan sets every preparation signature from its operation by position and signs the layout.
func signedPlan(t *testing.T, plan pluginpack.KotlinPlanFile, operations ...string) pluginpack.KotlinPlanFile {
	t.Helper()
	if len(operations) != len(plan.Preparations) {
		t.Fatalf("%d operations for %d preparations", len(operations), len(plan.Preparations))
	}
	for index, operation := range operations {
		plan.Preparations[index].ModelSignature = pluginpack.KotlinModelSignature(operation)
		plan.Operations = append(plan.Operations, json.RawMessage(operation))
	}
	plan.LayoutSignature = pluginpack.KotlinLayoutSignature(plan)
	return plan
}

// moduleFilterFixture is one module-filter operation whose prepared source enters lib/main.jar beside a module source.
// The writer manifest and the prepared manifest select the manifest policy of every source.
func moduleFilterFixture(t *testing.T, inputs, manifest string, preparedManifest *pluginpack.KotlinPreparedManifest) derivationFixture {
	t.Helper()
	raw := filepath.Join(inputs, "raw.jar")
	pluginpack.ArchiveTestFile(t, raw, [2]string{"keep/Service.class", "retained"}, [2]string{"drop/Ignore.class", "excluded"},
		[2]string{"META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"}, [2]string{"META-INF/listOfEntities.txt", "keep.Service\n"})
	extra := "demo.extra"
	if preparedManifest != nil {
		extra = "intellij.libraries.bar"
	}
	operationManifest := manifest
	if operationManifest == "" {
		operationManifest = "keep"
	}
	plan := pluginpack.KotlinPlanFile{Version: pluginpack.Version, Plugin: "filtered", Assets: []pluginpack.KotlinPlanAsset{
		{Destination: "lib/main.jar", Recipe: &pluginpack.KotlinJarRecipe{
			Sources: []pluginpack.KotlinJarSource{{Input: "filtered:output", Kind: "prepared", Filter: "prepared", PreparedManifest: preparedManifest}, moduleSource(extra)},
			Writer:  pluginpack.KotlinJarWriter{Manifest: manifest, MergeEntities: true}}}},
		Preparations: []pluginpack.KotlinPreparation{{ID: "filter", Inputs: []string{"raw"}, Outputs: []string{"filtered:output"}}}}
	plan = signedPlan(t, plan, pluginpack.KotlinModuleFilterOperation(t, "filter", "raw", "filtered:output", operationManifest, []string{"drop/**"}))
	return derivationFixture{plan: plan, present: []string{"lib/main.jar"},
		inputs: pluginpack.Catalogue{Version: pluginpack.Version, Artifacts: []pluginpack.Artifact{pluginpack.FileArtifact("raw", raw), moduleJar(t, inputs, extra)}}}
}

// derivationFixtures cover the plan-file shapes the Go derivation compiles: module-v1 and library sources, a patch
// descriptor, a reusable artifact and a same-recipe asset at another mode, a directory and a link asset, the
// module-filter manifests, a layout-assets tree and entries operation, a raw copy-tree and a version-3 asset.
var derivationFixtures = []struct {
	name  string
	build func(t *testing.T, inputs string) derivationFixture
}{
	{"jars, ownership rows, and the classpath order", func(t *testing.T, inputs string) derivationFixture {
		excluded := false
		current := "./tool"
		pluginpack.WriteTestFile(t, filepath.Join(inputs, "tool"), []byte("tool"))
		pluginpack.WriteTestFile(t, filepath.Join(inputs, "patch.xml"), []byte("<idea-plugin>patched</idea-plugin>"))
		rt := &pluginpack.KotlinJarRecipe{Sources: []pluginpack.KotlinJarSource{moduleSource("demo.rt")}, Writer: pluginpack.KotlinJarWriter{MergeEntities: true}}
		plan := pluginpack.KotlinPlanFile{Version: pluginpack.Version, Plugin: "demo", Assets: []pluginpack.KotlinPlanAsset{
			{Module: "demo.content"},
			{Destination: "lib/demo.jar", Recipe: &pluginpack.KotlinJarRecipe{Sources: []pluginpack.KotlinJarSource{
				{Input: "@lib//:two", Kind: "library", Filter: "library-v1", Expansion: []string{"@lib//:two/a.jar", "@lib//:two/b.jar"}},
				moduleSource("demo.main"),
				{Input: "descriptor", Kind: "file", Filter: "none", Entry: "META-INF/plugin.xml", Options: []string{"patch"}}},
				Writer: pluginpack.KotlinJarWriter{MergeEntities: true}}},
			{Destination: "lib/rt.jar", Recipe: rt},
			{Destination: "lib/rt-exec.jar", Recipe: rt, Mode: 0o755},
			{Destination: "lib/intellij.libraries.foo.jar", Recipe: &pluginpack.KotlinJarRecipe{Sources: []pluginpack.KotlinJarSource{
				{Input: "@lib//:one", Kind: "library", Filter: "library-v1", Expansion: []string{"@lib//:one/a.jar"}},
				moduleSource("intellij.libraries.foo")}, Writer: pluginpack.KotlinJarWriter{MergeEntities: true}}},
			{Destination: "lib/side.jar", Recipe: &pluginpack.KotlinJarRecipe{Sources: []pluginpack.KotlinJarSource{moduleSource("demo.side")}}, ClassPath: &excluded},
			{Destination: "lib/nested/inner.jar", Recipe: &pluginpack.KotlinJarRecipe{Sources: []pluginpack.KotlinJarSource{moduleSource("demo.main")}}},
			{Destination: "bin/tool", Inputs: []string{"native"}, Mode: 0o755},
			{Destination: "bin/current", Inputs: []string{}, SymlinkTarget: &current},
			{Destination: "lib/empty", Inputs: []string{}, Kind: "directory"},
		}, ReusableArtifacts: []pluginpack.KotlinReusableArtifact{{Label: "//demo:content.jar", Module: "demo.content"}, {Label: "//demo:rt.jar", Recipe: rt}}}
		plan = signedPlan(t, plan)
		return derivationFixture{plan: plan,
			inputs: pluginpack.Catalogue{Version: pluginpack.Version, Artifacts: []pluginpack.Artifact{
				moduleJar(t, inputs, "demo.main"), moduleJar(t, inputs, "demo.rt"), moduleJar(t, inputs, "intellij.libraries.foo"), moduleJar(t, inputs, "demo.side"),
				libraryJar(t, inputs, "@lib//:two/a.jar", "two-a.jar"), libraryJar(t, inputs, "@lib//:two/b.jar", "two-b.jar"), libraryJar(t, inputs, "@lib//:one/a.jar", "one-a.jar"),
				pluginpack.FileArtifact("descriptor", filepath.Join(inputs, "patch.xml")), pluginpack.FileArtifact("native", filepath.Join(inputs, "tool"))},
				Libraries: []pluginpack.Library{
					{ID: "@lib//:two", Files: []pluginpack.Reference{{Artifact: "@lib//:two/a.jar"}, {Artifact: "@lib//:two/b.jar"}}},
					{ID: "@lib//:one", Files: []pluginpack.Reference{{Artifact: "@lib//:one/a.jar"}}}}},
			present: []string{"lib/demo.jar", "lib/rt-exec.jar", "lib/intellij.libraries.foo.jar", "lib/side.jar", "lib/nested/inner.jar", "bin/tool", "bin/current", "lib/empty"}}
	}},
	{"module-filter with manifest keep", func(t *testing.T, inputs string) derivationFixture {
		return moduleFilterFixture(t, inputs, "keep", nil)
	}},
	{"module-filter with manifest drop", func(t *testing.T, inputs string) derivationFixture {
		return moduleFilterFixture(t, inputs, "drop", nil)
	}},
	{"module-filter with a prepared manifest under single-meaningful-source", func(t *testing.T, inputs string) derivationFixture {
		return moduleFilterFixture(t, inputs, "", &pluginpack.KotlinPreparedManifest{SourceManifestPolicies: []string{"keep"}})
	}},
	{"layout-assets tree and entries beside a raw copy-tree", func(t *testing.T, inputs string) derivationFixture {
		excluded := false
		archive := filepath.Join(inputs, "assets.tar.gz")
		pluginpack.TarGzTestFile(t, archive, pluginpack.TarEntry{Name: "top/", Mode: 0o755}, pluginpack.TarEntry{Name: "top/bin/", Mode: 0o755},
			pluginpack.TarEntry{Name: "top/bin/tool", Content: "tool", Mode: 0o751}, pluginpack.TarEntry{Name: "top/data.txt", Content: "data", Mode: 0o664},
			pluginpack.TarEntry{Name: "top/docs/", Mode: 0o750}, pluginpack.TarEntry{Name: "top/docs/readme.md", Content: "readme", Mode: 0o644})
		properties, dsls := filepath.Join(inputs, "properties"), filepath.Join(inputs, "dsls")
		pluginpack.WriteTestFile(t, filepath.Join(properties, "alpha.properties"), []byte("alpha"))
		pluginpack.WriteTestFile(t, filepath.Join(properties, "notes.txt"), []byte("notes"))
		pluginpack.WriteTestFile(t, filepath.Join(dsls, "a.gdsl"), []byte("dsl"))
		pluginpack.ChmodTestTree(t, properties)
		pluginpack.ChmodTestTree(t, dsls)
		tree := pluginpack.LayoutAssets{Inputs: []pluginpack.Reference{{Artifact: "archive"}},
			Assets: []pluginpack.LayoutAsset{{Sources: []int{0}, Transform: pluginpack.ArchiveTree(1)}}}
		entries := pluginpack.LayoutAssets{Inputs: []pluginpack.Reference{{Artifact: "properties"}}, Assets: []pluginpack.LayoutAsset{{Sources: []int{0},
			Transform: pluginpack.TreeMap(pluginpack.LayoutMapping{Pattern: "*.properties", Destination: "messages"}, pluginpack.LayoutMapping{})}}}
		plan := pluginpack.KotlinPlanFile{Version: pluginpack.TreeVersion, Plugin: "layout", Assets: []pluginpack.KotlinPlanAsset{
			{Destination: "payload", Inputs: []string{"layout-tree:output"}, Kind: "tree", ClassPath: &excluded, NormalizeTreeModes: true},
			{Destination: "lib/localization.jar", Recipe: &pluginpack.KotlinJarRecipe{
				Sources: []pluginpack.KotlinJarSource{{Input: "layout-entries:output", Kind: "prepared", Filter: "prepared"}, moduleSource("demo.l10n")},
				Writer:  pluginpack.KotlinJarWriter{Manifest: "drop", MergeEntities: true}}},
			{Destination: "lib/standardDsls", Inputs: []string{"dsls"}, Kind: "tree", ClassPath: &excluded},
		}, Preparations: []pluginpack.KotlinPreparation{
			{ID: "layout-tree", Inputs: []string{"archive"}, Outputs: []string{"layout-tree:output"}},
			{ID: "layout-entries", Inputs: []string{"properties"}, Outputs: []string{"layout-entries:output"}}}}
		plan = signedPlan(t, plan,
			pluginpack.KotlinLayoutAssetsOperation(t, "layout-tree", "layout-tree:output", "tree", "payload", tree),
			pluginpack.KotlinLayoutAssetsOperation(t, "layout-entries", "layout-entries:output", "entries", "", entries))
		return derivationFixture{plan: plan,
			inputs: pluginpack.Catalogue{Version: pluginpack.Version, Artifacts: []pluginpack.Artifact{pluginpack.FileArtifact("archive", archive),
				pluginpack.DirectoryArtifact("properties", properties), moduleJar(t, inputs, "demo.l10n"), pluginpack.DirectoryArtifact("dsls", dsls)}},
			present: []string{"payload/bin/tool", "payload/data.txt", "payload/docs/readme.md", "lib/localization.jar", "lib/standardDsls/a.gdsl"}}
	}},
	{"version 3 with a distribution-scope asset", func(t *testing.T, inputs string) derivationFixture {
		excluded := false
		pluginpack.WriteTestFile(t, filepath.Join(inputs, "launcher.sh"), []byte("#!/bin/sh\n"))
		plan := pluginpack.KotlinPlanFile{Version: pluginpack.ScopedVersion, Plugin: "scoped", Assets: []pluginpack.KotlinPlanAsset{
			{Destination: "lib/scoped.jar", Recipe: &pluginpack.KotlinJarRecipe{Sources: []pluginpack.KotlinJarSource{moduleSource("demo.main")}, Writer: pluginpack.KotlinJarWriter{MergeEntities: true}}},
			{Destination: "bin/launcher.sh", Inputs: []string{"launcher"}, Mode: 0o755, ClassPath: &excluded, Scope: pluginpack.DistributionScope}}}
		plan = signedPlan(t, plan)
		return derivationFixture{plan: plan,
			inputs: pluginpack.Catalogue{Version: pluginpack.Version, Artifacts: []pluginpack.Artifact{moduleJar(t, inputs, "demo.main"),
				pluginpack.FileArtifact("launcher", filepath.Join(inputs, "launcher.sh"))}},
			present: []string{"lib/scoped.jar", pluginpack.TransportDestination(pluginpack.ScopedVersion, pluginpack.DistributionScope, "bin/launcher.sh")}}
	}},
}

// canonicalRows renders asset rows with the Go encoder, so a default the Kotlin encoder omits and a default Go
// leaves empty compare equal.
func canonicalRows(t *testing.T, assets []pluginpack.Asset) string {
	t.Helper()
	data, err := json.MarshalIndent(assets, "", " ")
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

// requireSameRecipe pins the derived recipe to the recipe the Kotlin preparer wrote: the version, the identity, the
// asset rows and the operations, each in its canonical Go encoding.
func requireSameRecipe(t *testing.T, kotlin, derived pluginpack.Recipe) {
	t.Helper()
	if kotlin.Version != derived.Version || kotlin.Plugin != derived.Plugin || kotlin.LayoutSignature != derived.LayoutSignature {
		t.Fatalf("the recipe identity differs:\n%+v\n%+v", kotlin, derived)
	}
	if actual, expected := canonicalRows(t, kotlin.Assets), canonicalRows(t, derived.Assets); actual != expected {
		t.Fatalf("the asset rows of the recipe differ:\nKotlin: %s\nGo:     %s", actual, expected)
	}
	if actual, expected := pluginpack.CanonicalOperations(t, kotlin.Operations), pluginpack.CanonicalOperations(t, derived.Operations); actual != expected {
		t.Fatalf("the operations differ:\nKotlin: %s\nGo:     %s", actual, expected)
	}
}

// projectionOutput is what plugin-remainder-packer --projection writes.
type projectionOutput struct {
	directory string
	inventory []filemetadata.Entry
	assets    []pluginpack.Asset
	classPath []byte
}

// runProjectionPacker runs the Go packer in its projection mode on the plan file.
func runProjectionPacker(t *testing.T, plan string, inputs pluginpack.Catalogue, descriptor []byte, plugin string, version int) projectionOutput {
	t.Helper()
	executable := pluginpack.PluginRemainderPackerExecutable(t)
	root := t.TempDir()
	pluginpack.WriteTestFile(t, filepath.Join(root, "catalogue.json"), []byte(pluginpack.KotlinJSON(t, inputs)))
	pluginpack.WriteTestFile(t, filepath.Join(root, "descriptor.xml"), descriptor)
	output := projectionOutput{directory: filepath.Join(root, "plugin")}
	commandContext, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	command := exec.CommandContext(commandContext, executable, "--projection="+plan, "--input-catalogue="+filepath.Join(root, "catalogue.json"),
		"--classpath-descriptor="+filepath.Join(root, "descriptor.xml"), "--plugin-directory="+filepath.Join("plugins", plugin),
		fmt.Sprintf("--execution-version=%d", version), "--output-dir="+output.directory, "--inventory="+filepath.Join(root, "inventory.json"),
		"--assets="+filepath.Join(root, "assets.json"), "--classpath="+filepath.Join(root, "plugin-classpath.txt"))
	command.Dir = root
	if combined, err := command.CombinedOutput(); err != nil {
		t.Fatalf("plugin-remainder-packer --projection failed: %v\n%s", err, combined)
	}
	inventory, err := filemetadata.Read(filepath.Join(root, "inventory.json"))
	if err != nil {
		t.Fatal(err)
	}
	output.inventory = inventory
	output.assets = pluginpack.ReadAssetRows(t, filepath.Join(root, "assets.json"))
	output.classPath = pluginpack.ReadTestFile(t, filepath.Join(root, "plugin-classpath.txt"))
	return output
}

// TestGoPlanDerivationMatchesKotlinPreparer runs the Kotlin preparer and the Go derivation on every fixture. The
// recipe, the asset rows, the plugin classpath record, the packed directory and the inventory must be equal. It is
// the live control between the two compilers of the plan file, and the leaf that keeps the Kotlin emit path alive.
func TestGoPlanDerivationMatchesKotlinPreparer(t *testing.T) {
	pluginpack.KotlinPreparerExecutable(t)
	for _, definition := range derivationFixtures {
		t.Run(definition.name, func(t *testing.T) {
			fixture := definition.build(t, t.TempDir())
			plan := fixture.plan
			descriptor := []byte("<idea-plugin><id>" + plan.Plugin + "</id><version>1</version></idea-plugin>")
			kotlin := pluginpack.RunKotlinPreparer(t, plan, fixture.inputs, descriptor)
			if actual, expected := canonicalRows(t, kotlin.Assets), canonicalRows(t, kotlin.Recipe.Assets); actual != expected {
				t.Fatalf("the Kotlin assets.json differs from its recipe rows:\n%s\n%s", actual, expected)
			}

			planPath := filepath.Join(t.TempDir(), "plan.json")
			pluginpack.WriteTestFile(t, planPath, []byte(pluginpack.KotlinJSON(t, plan)))
			file, err := planfile.Read(planPath)
			if err != nil {
				t.Fatal(err)
			}
			derivation, err := planfile.Derive(file, fixture.inputs, filepath.Join("plugins", plan.Plugin), descriptor, plan.Version)
			if err != nil {
				t.Fatal(err)
			}
			requireSameRecipe(t, kotlin.Recipe, derivation.Recipe)
			if actual, expected := canonicalRows(t, kotlin.Assets), canonicalRows(t, derivation.Assets); actual != expected {
				t.Fatalf("the asset rows differ:\nKotlin: %s\nGo:     %s", actual, expected)
			}
			if !bytes.Equal(kotlin.ClassPath, derivation.ClassPath) {
				t.Fatalf("the plugin classpath record differs:\nKotlin: %x\nGo:     %x", kotlin.ClassPath, derivation.ClassPath)
			}

			kotlinOutput, kotlinInventory := pluginpack.WriteExecution(t, kotlin.Recipe, kotlin.Catalogue)
			goOutput, inventory := pluginpack.WriteExecution(t, derivation.Recipe, derivation.Catalogue)
			kotlinRecord, record := pluginpack.MaterializationRecord(t, kotlinOutput), pluginpack.MaterializationRecord(t, goOutput)
			pluginpack.RequireEqualRecords(t, "the Go derivation against the Kotlin recipe", kotlinRecord, record)
			if !reflect.DeepEqual(kotlinInventory, inventory) {
				t.Fatalf("inventories differ:\n%+v\n%+v", kotlinInventory, inventory)
			}
			pluginpack.RequireInventoryMatchesTree(t, goOutput, inventory)
			pluginpack.RequireRecordedPaths(t, record, fixture.present)

			packed := runProjectionPacker(t, planPath, fixture.inputs, descriptor, plan.Plugin, plan.Version)
			pluginpack.RequireEqualRecords(t, "the packer's projection mode against the Kotlin recipe", kotlinRecord, pluginpack.MaterializationRecord(t, packed.directory))
			if !reflect.DeepEqual(kotlinInventory, packed.inventory) {
				t.Fatalf("the packer's inventory differs:\n%+v\n%+v", kotlinInventory, packed.inventory)
			}
			if actual, expected := canonicalRows(t, kotlin.Assets), canonicalRows(t, packed.assets); actual != expected {
				t.Fatalf("the packer's asset rows differ:\nKotlin: %s\nGo:     %s", actual, expected)
			}
			if !bytes.Equal(kotlin.ClassPath, packed.classPath) {
				t.Fatalf("the packer's plugin classpath record differs:\nKotlin: %x\nGo:     %x", kotlin.ClassPath, packed.classPath)
			}
		})
	}
}
