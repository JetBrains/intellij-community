package main

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
)

// The tests in this file cover the local layout that writeLocalLayout writes.

func layoutComponent(kind string, entries ...componentEntry) devBuildComponent {
	manifest := withEntries(testManifest(kind), entries...)
	manifest.CoreClassPath = []string{"lib/app.jar"}
	return devBuildComponent{manifest: manifest}
}

func runfiles(pairs ...string) *orderedMap {
	result := &orderedMap{values: map[string]string{}}
	for index := 0; index < len(pairs); index += 2 {
		result.put(pairs[index], pairs[index+1])
	}
	return result
}

func composeLocal(components []devBuildComponent, target string, sourceRunfiles *orderedMap) (*composedBuild, error) {
	return composeComponents(components, target, composeOptions{sourceRunfiles: sourceRunfiles})
}

func requireLayout(test *testing.T, target string, fragments ...string) string {
	test.Helper()
	layout := readTestFile(test, filepath.Join(target, "local-layout.json"))
	for _, fragment := range fragments {
		if !strings.Contains(layout, fragment) {
			test.Fatalf("the local layout has no %s: %s", fragment, layout)
		}
	}
	return layout
}

func TestDirectoryComponentsPreserveModesInLocalAndFullLayoutsWithoutPayloadTrees(test *testing.T) {
	directory := tempDir(test)
	entries := []componentEntry{
		{RelativePath: "resources/empty", Type: "directory", Mode: pointer(int64(0o710))},
		{RelativePath: "resources", Type: "directory", Mode: pointer(int64(0o700))},
	}
	plugin := layoutComponent("plugin", entries...)
	metadata := filepath.Join(directory, "metadata")
	if _, err := composeLocal([]devBuildComponent{plugin}, metadata, runfiles()); err != nil {
		test.Fatal(err)
	}
	requireLayout(test, metadata, `"kind":"directory"`, `"mode":456`, `"mode":448`)
	requireAbsent(test, filepath.Join(metadata, "resources"))
	target := filepath.Join(directory, "home")
	if _, err := compose([]devBuildComponent{plugin}, target); err != nil {
		test.Fatal(err)
	}
	for _, entry := range entries {
		requirePermissions(test, filepath.Join(target, entry.RelativePath), os.FileMode(*entry.Mode))
	}
	conventional := withEntries(testManifest("plugin"), entries...)
	conventional.CoreClassPath = plugin.manifest.CoreClassPath
	conventional.Entries = []componentEntry{entries[0], entries[1]}
	for index := range conventional.Entries {
		conventional.Entries[index].Mode = pointer(int64(0o755))
	}
	if mustFingerprint(test, plugin.manifest) == mustFingerprint(test, conventional) {
		test.Fatal("the fingerprint ignores directory modes")
	}
	for _, change := range []func(*componentEntry){
		func(entry *componentEntry) { entry.Hash = pointer(int64(0)) },
		func(entry *componentEntry) { entry.Source = pointer("tree") },
		func(entry *componentEntry) { entry.Executable = true },
		func(entry *componentEntry) { entry.SymlinkTarget = pointer("other") },
	} {
		invalid := entries[0]
		change(&invalid)
		_, err := compose([]devBuildComponent{layoutComponent("invalid", invalid)}, filepath.Join(directory, "invalid"))
		requireError(test, err, "Invalid directory")
	}
}

func TestAManifestOnlyLinkReachesTheLocalLayoutWithoutAPayload(test *testing.T) {
	target := filepath.Join(tempDir(test), "metadata")
	if _, err := composeLocal([]devBuildComponent{layoutComponent("plugin", linkEntry("plugins/demo/current", "lib/payload"))}, target, runfiles()); err != nil {
		test.Fatal(err)
	}
	requireLayout(test, target, `"symlinkTarget":"lib/payload"`)
}

func TestExactModesRemainInLaunchMetadataWithoutReadingPayloads(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "absent-tool")
	file := sourcedEntry("plugins/demo/bin/tool", source)
	file.Executable, file.Mode = true, pointer(int64(0o750))
	target := filepath.Join(directory, "metadata")
	if _, err := composeLocal([]devBuildComponent{layoutComponent("plugin", file)}, target, runfiles(source, "_main/absent-tool")); err != nil {
		test.Fatal(err)
	}
	requireLayout(test, target, `"mode":488`)
	requireAbsent(test, source)
}

func TestCanonicalModesRetainTheExistingLocalLinkingPolicy(test *testing.T) {
	directory := tempDir(test)
	jar, executable := filepath.Join(directory, "absent.jar"), filepath.Join(directory, "absent-tool")
	jarEntry := sourcedEntry("plugins/demo/lib/main.jar", jar)
	jarEntry.Mode = pointer(int64(0o644))
	toolEntry := sourcedEntry("plugins/demo/bin/tool", executable)
	toolEntry.Mode, toolEntry.Executable = pointer(int64(0o755)), true
	target := filepath.Join(directory, "metadata")
	if _, err := composeLocal([]devBuildComponent{layoutComponent("plugin", jarEntry, toolEntry)}, target,
		runfiles(jar, "_main/absent.jar", executable, "_main/absent-tool")); err != nil {
		test.Fatal(err)
	}
	layout := requireLayout(test, target, `"executable":false`, `"executable":true`)
	if strings.Contains(layout, `"mode":420`) || strings.Contains(layout, `"mode":493`) {
		test.Fatalf("the local layout names a canonical mode: %s", layout)
	}
}

func TestInvalidOrConflictingModesFailBeforeCreatingLaunchMetadata(test *testing.T) {
	directory := tempDir(test)
	link := linkEntry("bin/link", "tool")
	link.Mode = pointer(int64(0))
	for index, mode := range []struct {
		mode       int64
		executable bool
	}{{-1, false}, {512, false}, {0o755, false}, {0o644, true}} {
		file := fileEntry("bin/tool")
		file.Mode, file.Executable = pointer(mode.mode), mode.executable
		target := filepath.Join(directory, "metadata-"+strconv.Itoa(index))
		_, err := composeLocal([]devBuildComponent{layoutComponent("plugin", file)}, target, runfiles())
		requireError(test, err, "file mode")
		requireAbsent(test, target)
	}
	target := filepath.Join(directory, "metadata-link")
	_, err := composeLocal([]devBuildComponent{layoutComponent("plugin", link)}, target, runfiles())
	requireError(test, err, "file mode")
	requireAbsent(test, target)
}

func TestLocalCompositionReadsMetadataWithoutStagingPayload(test *testing.T) {
	directory := tempDir(test)
	root, packed := filepath.Join(directory, "absent-tree"), filepath.Join(directory, "absent.jar")
	app := filepath.Join(root, "lib/app.jar")
	components := []devBuildComponent{
		layoutComponent("platform", sourcedEntry("lib/app.jar", app)),
		layoutComponent("packed", sourcedEntry("plugins/demo/lib/demo.jar", packed)),
	}
	target := filepath.Join(directory, "metadata")
	if _, err := composeComponents(components, target, composeOptions{
		expectedFragments: []string{"platform", "packed"},
		sourceRunfiles:    runfiles(app, "_main/tree/lib/app.jar", packed, "community+/packed.jar"),
	}); err != nil {
		test.Fatal(err)
	}
	requireLayout(test, target, `"runfile":"_main/tree/lib/app.jar"`, `"runfile":"community+/packed.jar"`)
	for _, absent := range []string{filepath.Join(target, "lib"), filepath.Join(target, "plugins"), root, packed} {
		requireAbsent(test, absent)
	}
}

func TestLocalAndExportedCompositionsHaveTheSameMetadata(test *testing.T) {
	directory := tempDir(test)
	app := filepath.Join(directory, "tree/lib/app.jar")
	writeTestFile(test, app, "bytes")
	prefix, part := filepath.Join(directory, "prefix"), filepath.Join(directory, "part")
	writeTestBytes(test, prefix, []byte{1, 2, 3})
	writeTestBytes(test, part, []byte{4, 5, 6})
	platform := layoutComponent("platform", sourcedEntry("lib/app.jar", app))
	platform.manifest.PluginCount = 1
	platform.pluginClasspathPart = part
	exported, err := composeComponents([]devBuildComponent{platform}, filepath.Join(directory, "dist"), composeOptions{pluginClasspathPrefix: prefix})
	if err != nil {
		test.Fatal(err)
	}
	if err := os.Remove(app); err != nil {
		test.Fatal(err)
	}
	local, err := composeComponents([]devBuildComponent{platform}, filepath.Join(directory, "metadata"), composeOptions{
		pluginClasspathPrefix: prefix, sourceRunfiles: runfiles(app, "_main/tree/lib/app.jar"),
	})
	if err != nil {
		test.Fatal(err)
	}
	if local.fingerprint != exported.fingerprint || strings.Join(local.coreClassPath, "\n") != strings.Join(exported.coreClassPath, "\n") ||
		local.mainClass != exported.mainClass || local.platformPrefix != exported.platformPrefix {
		test.Fatalf("local = %+v, exported = %+v", local, exported)
	}
	if readTestFile(test, filepath.Join(directory, "metadata", pluginClassPath)) != readTestFile(test, filepath.Join(directory, "dist", pluginClassPath)) {
		test.Fatal("the plugin classpath differs")
	}
}

func TestLocalCompositionPreservesGenuineRelativeLinks(test *testing.T) {
	directory := tempDir(test)
	target := filepath.Join(directory, "metadata")
	if _, err := composeLocal([]devBuildComponent{layoutComponent("platform", linkEntry("lib/current", "versions/A"))}, target, runfiles()); err != nil {
		test.Fatal(err)
	}
	requireLayout(test, target, `"symlinkTarget":"versions/A"`, `"runfile":null`)
}

func TestLocalCompositionRejectsUndeclaredInputs(test *testing.T) {
	directory := tempDir(test)
	_, err := composeLocal([]devBuildComponent{layoutComponent("platform", sourcedEntry("lib/app.jar", filepath.Join(directory, "tree/lib/app.jar")))},
		filepath.Join(directory, "metadata"), runfiles())
	requireError(test, err, "undeclared source")
}

func TestLocalCompositionResolvesFilesInsideDeclaredDirectoriesWithoutReadingThem(test *testing.T) {
	directory := tempDir(test)
	pluginDirectory := filepath.Join(directory, "absent-plugin")
	packed := filepath.Join(pluginDirectory, "lib/nested/plugin.jar")
	target := filepath.Join(directory, "metadata")
	_, err := composeComponents([]devBuildComponent{layoutComponent("plugin", sourcedEntry("plugins/demo/lib/plugin.jar", packed))}, target,
		composeOptions{sourceRunfiles: runfiles(), sourceDirectoryRunfiles: runfiles(pluginDirectory, "_main/plugin")})
	if err != nil {
		test.Fatal(err)
	}
	requireLayout(test, target, `"runfile":"_main/plugin/lib/nested/plugin.jar"`)
	requireAbsent(test, pluginDirectory)
	requireAbsent(test, filepath.Join(target, "plugins"))
}

func TestLocalCompositionDoesNotTreatAFileDeclarationAsADirectory(test *testing.T) {
	directory := tempDir(test)
	file := filepath.Join(directory, "file")
	_, err := composeLocal([]devBuildComponent{layoutComponent("plugin", sourcedEntry("lib/plugin.jar", filepath.Join(file, "nested.jar")))},
		filepath.Join(directory, "metadata"), runfiles(file, "_main/file"))
	requireError(test, err, "undeclared source")
}

func TestDirectoryReferencesRejectSiblingPrefixesAndEscapingPaths(test *testing.T) {
	directory := tempDir(test)
	pluginDirectory := filepath.Join(directory, "plugin")
	for _, source := range []string{
		filepath.Join(directory, "plugin-other/file.jar"), pluginDirectory + "/../other.jar", pluginDirectory + "/lib/../plugin.jar",
	} {
		_, err := composeComponents([]devBuildComponent{layoutComponent("plugin", sourcedEntry("lib/plugin.jar", source))},
			filepath.Join(directory, "metadata"), composeOptions{sourceRunfiles: runfiles(), sourceDirectoryRunfiles: runfiles(pluginDirectory, "_main/plugin")})
		if err == nil {
			test.Fatalf("accepted the source %s", source)
		}
	}
}

func TestManifestOnlyLinksPreserveTheirSpellingWithoutAPayloadTree(test *testing.T) {
	directory := tempDir(test)
	for index, target := range []string{"lib/../lib/plugin.jar", "lib//payload/"} {
		metadata := filepath.Join(directory, "metadata-"+strconv.Itoa(index))
		if _, err := composeLocal([]devBuildComponent{layoutComponent("plugin", linkEntry("plugins/demo/current", target))}, metadata, runfiles()); err != nil {
			test.Fatal(err)
		}
		requireLayout(test, metadata, `"symlinkTarget":"`+target+`"`)
	}
}

func TestManifestOnlyLinksRejectFileSourcesAndEscapes(test *testing.T) {
	directory := tempDir(test)
	withSource := linkEntry("plugins/demo/current", "lib/plugin.jar")
	withSource.Source = pointer("ambiguous")
	for _, entry := range []componentEntry{withSource, linkEntry("plugins/demo/current", "../../../outside")} {
		if _, err := composeLocal([]devBuildComponent{layoutComponent("plugin", entry)}, filepath.Join(directory, "metadata"), runfiles()); err == nil {
			test.Fatalf("accepted %+v", entry)
		}
	}
}

func TestLinkChainsCannotEscapeOrCycle(test *testing.T) {
	directory := tempDir(test)
	for _, links := range []struct {
		entries []componentEntry
		message string
	}{
		{[]componentEntry{linkEntry("plugins/demo/current", "../.."), linkEntry("plugins/demo/escape", "current/../outside")}, "link chain escapes"},
		{[]componentEntry{linkEntry("plugins/demo/first", "second"), linkEntry("plugins/demo/second", "first")}, "link cycle"},
	} {
		_, err := composeLocal([]devBuildComponent{layoutComponent("plugin", links.entries...)}, filepath.Join(directory, "metadata"), runfiles())
		requireError(test, err, links.message)
	}
}

func TestCaseAndUnicodeAliasesCannotBypassLinkContainment(test *testing.T) {
	directory := tempDir(test)
	for _, alias := range [][2]string{{"current", "CURRENT"}, {"currént", "currént"}, {"σ", "ς"}, {"straẞe", "STRASSE"}, {"ı", "i"}} {
		links := []componentEntry{linkEntry("plugins/demo/"+alias[0], "../.."), linkEntry("plugins/demo/escape", alias[1]+"/../outside")}
		_, err := composeLocal([]devBuildComponent{layoutComponent("plugin", links...)}, filepath.Join(directory, "metadata"), runfiles())
		requireError(test, err, "link chain escapes")
	}
}

func TestRepeatedAcyclicLinksUseCachedResolutions(test *testing.T) {
	links := []distributionLink{{"link0", "."}}
	for index := 1; index <= 100; index++ {
		previous := "link" + strconv.Itoa(index-1)
		links = append(links, distributionLink{"link" + strconv.Itoa(index), previous + "/" + previous})
	}
	if err := validateDevBuildLinks(links); err != nil {
		test.Fatal(err)
	}
}

func TestLocalCompositionRejectsUnsafeAndConflictingPaths(test *testing.T) {
	directory := tempDir(test)
	root := filepath.Join(directory, "tree")
	file := func(relativePath string) componentEntry {
		return sourcedEntry(relativePath, filepath.Join(root, "file.jar"))
	}
	for _, entries := range [][]componentEntry{
		{file("../outside")},
		{file("/absolute")},
		{file("dir/../outside")},
		{file("lib/app.jar"), file("lib/app.jar")},
		{file("lib/café.jar"), file("lib/café.jar")},
		{file("lib/first.jar"), file("Lib/second.jar")},
		{file("café/first.jar"), file("café/second.jar")},
		{file("lib"), file("lib/app.jar")},
		{file("fingerprint.txt")},
		{file("local-layout.json")},
		{linkEntry("lib/link", "")},
		{linkEntry("lib/link", "../../outside")},
	} {
		if _, err := composeLocal([]devBuildComponent{layoutComponent("platform", entries...)}, filepath.Join(directory, "metadata"),
			runfiles(filepath.Join(root, "file.jar"), "_main/tree/file.jar")); err == nil {
			test.Fatalf("accepted %+v", entries)
		}
	}
}

// The dev-dist-collector `local-home` subcommand reads this exact shape, so the bytes are pinned.
func TestLocalLayoutBytes(test *testing.T) {
	directory := tempDir(test)
	app, packed := filepath.Join(directory, "tree/lib/app.jar"), filepath.Join(directory, "packed.jar")
	file := sourcedEntry("plugins/demo/lib/demo \"quoted\"\t.jar", packed)
	file.Mode, file.Executable = pointer(int64(0o750)), true
	target := filepath.Join(directory, "metadata")
	_, err := composeLocal([]devBuildComponent{
		layoutComponent("platform", sourcedEntry("lib/app.jar", app), linkEntry("lib/current", "app.jar"),
			componentEntry{RelativePath: "lib/empty", Type: "directory", Mode: pointer(int64(0o700))}),
		layoutComponent("packed", file),
	}, target, runfiles(app, "_main/tree/lib/app.jar", packed, "_main/packed.jar"))
	if err != nil {
		test.Fatal(err)
	}
	expected := `{"version":1,"files":[` +
		`{"path":"lib/app.jar","runfile":"_main/tree/lib/app.jar","symlinkTarget":null,"executable":false,"mode":null},` +
		`{"path":"lib/current","runfile":null,"symlinkTarget":"app.jar","executable":false,"mode":null},` +
		`{"path":"lib/empty","runfile":null,"symlinkTarget":null,"executable":false,"mode":448,"kind":"directory"},` +
		`{"path":"plugins/demo/lib/demo \"quoted\"\t.jar","runfile":"_main/packed.jar","symlinkTarget":null,"executable":true,"mode":488}` +
		`],"metadata":["core-classpath.txt","fingerprint.txt"]}`
	if actual := readTestFile(test, filepath.Join(target, "local-layout.json")); actual != expected {
		test.Fatalf("local layout =\n%s\nexpected\n%s", actual, expected)
	}
}
