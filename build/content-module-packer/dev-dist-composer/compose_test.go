package main

import (
	"bytes"
	"io/fs"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"
)

// The tests in this file cover composeComponents and its checks. The tests of the manifest writer are Kotlin tests,
// because the composer only reads manifests.

func pointer[T any](value T) *T {
	return &value
}

func formatInt(value int64) string {
	return strconv.FormatInt(value, 10)
}

func testManifest(kind string) *componentManifest {
	return &componentManifest{
		Version: componentManifestVersion, Kind: kind, PlatformPrefix: "idea", OS: "linux", Arch: "x64",
		AdditionalModules: []string{}, MainClass: pointer("com.intellij.idea.Main"), CoreClassPath: []string{},
	}
}

func withEntries(manifest *componentManifest, entries ...componentEntry) *componentManifest {
	manifest.Entries = entries
	return manifest
}

func fileEntry(relativePath string) componentEntry {
	return componentEntry{RelativePath: relativePath, Type: componentFileEntryType, Hash: pointer(int64(1))}
}

func sourcedEntry(relativePath, source string) componentEntry {
	entry := fileEntry(relativePath)
	entry.Source = pointer(source)
	return entry
}

func linkEntry(relativePath, target string) componentEntry {
	return componentEntry{RelativePath: relativePath, Type: "symlink", Hash: pointer(int64(1)), SymlinkTarget: pointer(target)}
}

func writeTestFile(test *testing.T, file, content string) {
	test.Helper()
	writeTestBytes(test, file, []byte(content))
}

func writeTestBytes(test *testing.T, file string, content []byte) {
	test.Helper()
	if err := os.MkdirAll(filepath.Dir(file), 0o755); err != nil {
		test.Fatal(err)
	}
	if err := os.WriteFile(file, content, 0o644); err != nil {
		test.Fatal(err)
	}
}

func readTestFile(test *testing.T, file string) string {
	test.Helper()
	data, err := os.ReadFile(file)
	if err != nil {
		test.Fatal(err)
	}
	return string(data)
}

// tempDir returns a test directory without symbolic links in its path, as `toRealPath` would.
func tempDir(test *testing.T) string {
	test.Helper()
	directory, err := filepath.EvalSymlinks(test.TempDir())
	if err != nil {
		test.Fatal(err)
	}
	return directory
}

// sourcedComponent writes one file whose content is name, and adds it to manifest as the source of relativeFile.
func sourcedComponent(test *testing.T, directory, name, relativeFile string, manifest *componentManifest) devBuildComponent {
	test.Helper()
	source := filepath.Join(directory, name, filepath.FromSlash(relativeFile))
	writeTestFile(test, source, name)
	manifest.Entries = append(manifest.Entries, sourcedEntry(relativeFile, source))
	return devBuildComponent{manifest: manifest}
}

func withPluginClasspathPart(component devBuildComponent, part string) devBuildComponent {
	component.pluginClasspathPart = part
	return component
}

func requireError(test *testing.T, err error, message string) {
	test.Helper()
	if err == nil || !strings.Contains(err.Error(), message) {
		test.Fatalf("error = %v, expected a message with %q", err, message)
	}
}

func requireAbsent(test *testing.T, file string) {
	test.Helper()
	if _, err := os.Lstat(file); err == nil {
		test.Fatalf("%s exists", file)
	}
}

func requirePermissions(test *testing.T, file string, expected fs.FileMode) {
	test.Helper()
	if windows {
		return
	}
	info, err := os.Stat(file)
	if err != nil || info.Mode().Perm() != expected {
		test.Fatalf("permissions of %s = %v, error = %v, expected %v", file, info.Mode().Perm(), err, expected)
	}
}

func requireLink(test *testing.T, file, expected string) {
	test.Helper()
	target, err := os.Readlink(file)
	if err != nil || filepath.ToSlash(target) != expected {
		test.Fatalf("link %s = %q, error = %v, expected %q", file, target, err, expected)
	}
}

func compose(components []devBuildComponent, target string) (*composedBuild, error) {
	return composeComponents(components, target, composeOptions{})
}

func withDirectoryRunfiles(directory, runfile string) composeOptions {
	runfiles := &orderedMap{values: map[string]string{}}
	runfiles.put(directory, runfile)
	return composeOptions{sourceDirectoryRunfiles: runfiles}
}

func TestComposerPreservesTheModificationTimeOfASourcedFile(test *testing.T) {
	directory := tempDir(test)
	sourceFile := filepath.Join(directory, "source/bin/tool")
	writeTestFile(test, sourceFile, "tool")
	modified := time.UnixMilli(1_234_000)
	if err := os.Chtimes(sourceFile, modified, modified); err != nil {
		test.Fatal(err)
	}
	target := filepath.Join(directory, "target")
	if _, err := compose([]devBuildComponent{{manifest: withEntries(testManifest("plugin"), sourcedEntry("bin/tool", sourceFile))}}, target); err != nil {
		test.Fatal(err)
	}
	copied := filepath.Join(target, "bin/tool")
	info, err := os.Stat(copied)
	if err != nil || readTestFile(test, copied) != "tool" || !info.ModTime().Equal(modified) {
		test.Fatalf("copied file = %v, error = %v", info, err)
	}
}

func TestComposerRecreatesAManifestDeclaredJcefFrameworkSymlink(test *testing.T) {
	directory := tempDir(test)
	framework := filepath.Join(directory, "staged/Chromium Embedded Framework")
	writeTestFile(test, framework, "framework")
	versions := "plugins/jcef/jcef/Frameworks/Chromium Embedded Framework.framework/Versions"
	relativeLink := versions + "/Current"
	manifest := withEntries(testManifest("plugins_jcef"), sourcedEntry(versions+"/A/Chromium Embedded Framework", framework), linkEntry(relativeLink, "A"))
	target := filepath.Join(directory, "target")
	if _, err := compose([]devBuildComponent{{manifest: manifest}}, target); err != nil {
		test.Fatal(err)
	}
	requireLink(test, filepath.Join(target, relativeLink), "A")
	if readTestFile(test, filepath.Join(target, relativeLink, "Chromium Embedded Framework")) != "framework" {
		test.Fatal("the framework link does not reach the framework")
	}
}

func TestComposerPlacesRuntimeModuleRepositoryFilesAtTheDistributionRoot(test *testing.T) {
	directory := tempDir(test)
	repositoryRoot := filepath.Join(directory, "runtime-module-repository")
	writeTestFile(test, filepath.Join(repositoryRoot, "modules/module-descriptors.dat"), "repository-dat-changed")
	writeTestFile(test, filepath.Join(repositoryRoot, "modules/module-descriptors.jar"), "repository-jar")
	repository := withEntries(testManifest("platform_runtime_module_repository"),
		sourcedEntry("modules/module-descriptors.dat", filepath.Join(repositoryRoot, "modules/module-descriptors.dat")),
		sourcedEntry("modules/module-descriptors.jar", filepath.Join(repositoryRoot, "modules/module-descriptors.jar")))
	platformLib := testManifest("platform_lib")
	platformLib.CoreClassPath = []string{"lib/platform.jar"}
	target := filepath.Join(directory, "target")
	result, err := compose([]devBuildComponent{
		sourcedComponent(test, directory, "platform-lib", "lib/platform.jar", platformLib),
		{manifest: repository},
	}, target)
	if err != nil {
		test.Fatal(err)
	}
	if readTestFile(test, filepath.Join(target, "modules/module-descriptors.dat")) != "repository-dat-changed" ||
		readTestFile(test, filepath.Join(target, "modules/module-descriptors.jar")) != "repository-jar" {
		test.Fatal("the repository files are not at the distribution root")
	}
	if !slices.Equal(result.coreClassPath, []string{"lib/platform.jar"}) {
		test.Fatalf("core classpath = %q", result.coreClassPath)
	}
}

func TestComposerAcceptsOrderedPlatformLayersAndPlugins(test *testing.T) {
	directory := tempDir(test)
	platformLib := testManifest("platform_lib")
	platformLib.CoreClassPath = []string{"lib/platform.jar"}
	plugins := testManifest("plugins")
	plugins.CoreClassPath = []string{"plugins/sample/lib/sample.jar"}
	plugins.AdditionalModules = []string{"intellij.sample", "intellij.shared"}
	extra := testManifest("plugins_extra")
	extra.CoreClassPath = []string{"plugins/extra/lib/extra.jar"}
	extra.AdditionalModules = []string{"intellij.shared", "intellij.extra"}
	components := []devBuildComponent{
		sourcedComponent(test, directory, "platform-lib", "lib/platform.jar", platformLib),
		sourcedComponent(test, directory, "platform-resources", "bin/idea.properties", testManifest("platform_resources")),
		sourcedComponent(test, directory, "plugins", "plugins/sample/lib/sample.jar", plugins),
		sourcedComponent(test, directory, "extra-plugins", "plugins/extra/lib/extra.jar", extra),
	}
	result, err := composeComponents(components, filepath.Join(directory, "target"), composeOptions{
		additionalModules: []string{"intellij.sample", "intellij.shared", "intellij.extra"},
	})
	if err != nil {
		test.Fatal(err)
	}
	// Ordered here rather than left in component order, because each component sorted only the share it packed.
	if !slices.Equal(result.coreClassPath, []string{"lib/platform.jar", "plugins/extra/lib/extra.jar", "plugins/sample/lib/sample.jar"}) {
		test.Fatalf("core classpath = %q", result.coreClassPath)
	}
	if !slices.Equal(result.additionalModules, []string{"intellij.sample", "intellij.shared", "intellij.extra"}) {
		test.Fatalf("additional modules = %q", result.additionalModules)
	}
	if _, err := os.Stat(filepath.Join(directory, "target/bin/idea.properties")); err != nil {
		test.Fatal(err)
	}
	manifests := []*componentManifest{platformLib, components[1].manifest, plugins, extra}
	if expected, _ := computeIdeFingerprintFromComponents(manifests, "", nil); result.fingerprint != expected {
		test.Fatalf("fingerprint = %s, expected %s", result.fingerprint, expected)
	}
}

func TestComposerPutsTheLeadingCoreClasspathJarsFirst(test *testing.T) {
	directory := tempDir(test)
	manifest := testManifest("platform_core")
	manifest.CoreClassPath = []string{"lib/app-backend.jar", "lib/util.jar", "lib/platform-loader.jar", "lib/util-8.jar"}
	result, err := compose([]devBuildComponent{sourcedComponent(test, directory, "platform", "lib/util.jar", manifest)},
		filepath.Join(directory, "target"))
	if err != nil {
		test.Fatal(err)
	}
	if !slices.Equal(result.coreClassPath, []string{"lib/platform-loader.jar", "lib/util-8.jar", "lib/util.jar", "lib/app-backend.jar"}) {
		test.Fatalf("core classpath = %q", result.coreClassPath)
	}
}

func TestComposerBuildsPluginClasspathFromThePrefixAndEveryComponentsRecords(test *testing.T) {
	directory := tempDir(test)
	prefix := filepath.Join(directory, "prefix.bin")
	writeTestBytes(test, prefix, []byte{3, 0, 0, 0, 0})
	air := testManifest("plugins_air")
	air.PluginCount = 1
	remaining := testManifest("plugins_remaining")
	remaining.PluginCount = 2
	airPart, remainingPart := filepath.Join(directory, "air.part"), filepath.Join(directory, "remaining.part")
	writeTestBytes(test, airPart, []byte{10})
	writeTestBytes(test, remainingPart, []byte{20, 21})
	target := filepath.Join(directory, "target")
	_, err := composeComponents([]devBuildComponent{
		withPluginClasspathPart(sourcedComponent(test, directory, "air", "plugins/air-plugin/lib/air.jar", air), airPart),
		withPluginClasspathPart(sourcedComponent(test, directory, "remaining", "plugins/git/lib/git.jar", remaining), remainingPart),
	}, target, composeOptions{pluginClasspathPrefix: prefix})
	if err != nil {
		test.Fatal(err)
	}
	// The prefix, then the summed plugin count as a big-endian short, then the records in component order.
	if data := readTestFile(test, filepath.Join(target, pluginClassPath)); !bytes.Equal([]byte(data), []byte{3, 0, 0, 0, 0, 0, 3, 10, 20, 21}) {
		test.Fatalf("plugin classpath = %v", []byte(data))
	}
}

func TestComposerRejectsPluginRecordsWithoutAPrefix(test *testing.T) {
	directory := tempDir(test)
	air := testManifest("plugins_air")
	air.PluginCount = 1
	part := filepath.Join(directory, "air.part")
	writeTestBytes(test, part, []byte{10})
	_, err := compose([]devBuildComponent{
		withPluginClasspathPart(sourcedComponent(test, directory, "air", "plugins/air-plugin/lib/air.jar", air), part),
	}, filepath.Join(directory, "target"))
	requireError(test, err, "plugin-classpath prefix is required")
}

func TestComposerRejectsAPositivePluginCountWithoutRecordsBeforeWritingOutput(test *testing.T) {
	directory := tempDir(test)
	air := testManifest("plugins_air")
	air.PluginCount = 1
	target := filepath.Join(directory, "target")
	_, err := compose([]devBuildComponent{sourcedComponent(test, directory, "air", "plugins/air-plugin/lib/air.jar", air)}, target)
	requireError(test, err, "plugins_air (1)")
	requireAbsent(test, target)
}

func TestComposerTakesTheMainClassFromAComponentThatDeclaresOne(test *testing.T) {
	directory := tempDir(test)
	jars := testManifest("platform_jars")
	jars.MainClass = nil
	composed, err := compose([]devBuildComponent{
		sourcedComponent(test, directory, "jars", "lib/packed.jar", jars),
		sourcedComponent(test, directory, "core", "lib/platform.jar", testManifest("platform_core")),
	}, filepath.Join(directory, "target"))
	if err != nil || composed.mainClass != "com.intellij.idea.Main" {
		test.Fatalf("composed = %+v, error = %v", composed, err)
	}
}

func TestComposerRejectsInconsistentCompositionsBeforeWritingOutput(test *testing.T) {
	directory := tempDir(test)
	jars := testManifest("platform_jars")
	jars.MainClass = nil
	rider := testManifest("platform_resources")
	rider.PlatformPrefix = "Rider"
	otherMain := testManifest("platform_resources")
	otherMain.MainClass = pointer("com.intellij.idea.OtherMain")
	neutral := testManifest("plugins_json")
	neutral.OS, neutral.Arch, neutral.MainClass = "", "", nil
	mac := testManifest("platform_resources")
	mac.OS, mac.Arch = "mac", "aarch64"
	negative := testManifest("plugins_negative")
	negative.PluginCount = -1
	for _, invalid := range []struct {
		manifests         []*componentManifest
		expectedFragments []string
		message           string
	}{
		{[]*componentManifest{jars}, nil, "No dev-build component declares an IDE main class: platform_jars"},
		{[]*componentManifest{testManifest("platform_core")}, []string{"platform_core", "platform_resources"},
			"Dev-build fragments do not match the expected composition; missing: platform_resources; present: platform_core"},
		{[]*componentManifest{testManifest("platform_core"), testManifest("plugins_stale")}, []string{"platform_core"},
			"; unexpected: plugins_stale; present: platform_core, plugins_stale"},
		{[]*componentManifest{testManifest("platform_core")}, []string{"platform_core", "platform_core"},
			"Expected dev-build fragment kinds must be unique, but these occur more than once: platform_core"},
		{[]*componentManifest{testManifest("platform_core"), testManifest("platform_core")}, nil,
			"Dev-build fragment kinds must be unique, but these occur more than once: platform_core"},
		{[]*componentManifest{testManifest("platform_lib"), rider}, nil, "Dev-build components have different products: 'idea' and 'Rider'"},
		{[]*componentManifest{testManifest("platform_lib"), otherMain}, nil,
			"different IDE main classes: 'com.intellij.idea.Main' and 'com.intellij.idea.OtherMain'"},
		{[]*componentManifest{neutral, testManifest("platform_core"), mac}, nil, "different target platforms: 'linux/x64' and 'mac/aarch64'"},
		{[]*componentManifest{testManifest("platform_core"), negative}, nil, "report a negative plugin count: plugins_negative (-1)"},
	} {
		var components []devBuildComponent
		for _, manifest := range invalid.manifests {
			components = append(components, devBuildComponent{manifest: manifest})
		}
		target := filepath.Join(directory, "target")
		_, err := composeComponents(components, target, composeOptions{expectedFragments: invalid.expectedFragments})
		requireError(test, err, invalid.message)
		requireAbsent(test, target)
	}
}

func TestComposerTakesThePlatformFromTheFirstComponentThatNamesOne(test *testing.T) {
	directory := tempDir(test)
	neutral := testManifest("plugins_json")
	neutral.OS, neutral.Arch, neutral.MainClass = "", "", nil
	linux := testManifest("platform_core")
	target := filepath.Join(directory, "target")
	if _, err := compose([]devBuildComponent{
		sourcedComponent(test, directory, "json", "plugins/json/lib/json.jar", neutral),
		sourcedComponent(test, directory, "platform", "lib/platform.jar", linux),
	}, target); err != nil {
		test.Fatal(err)
	}
	mac := *linux
	mac.OS, mac.Arch = "mac", "aarch64"
	if _, err := os.Stat(filepath.Join(target, "plugins/json/lib/json.jar")); err != nil {
		test.Fatal(err)
	}
	// The launch metadata hashes the platform of the distribution, not the empty one of the neutral component.
	if mustFingerprint(test, neutral, linux) == mustFingerprint(test, neutral, &mac) {
		test.Fatal("the fingerprint ignores the platform")
	}
}

func TestComposerAcceptsACompositionOfNeutralComponentsOnly(test *testing.T) {
	directory := tempDir(test)
	neutral := testManifest("plugins_json")
	neutral.OS, neutral.Arch = "", ""
	composed, err := compose([]devBuildComponent{sourcedComponent(test, directory, "json", "plugins/json/lib/json.jar", neutral)},
		filepath.Join(directory, "target"))
	if err != nil || composed.platformPrefix != "idea" {
		test.Fatalf("composed = %+v, error = %v", composed, err)
	}
}

// The regression that turned every AIR UI lane red. A fragment that several distributions share packs a bundled
// plugin, so no component manifest names it.
func TestComposerDeclaresABundledModuleThatNoComponentAssembled(test *testing.T) {
	directory := tempDir(test)
	additional := testManifest("plugins_additional")
	additional.AdditionalModules = []string{"intellij.bridge.plugin"}
	manifests := []*componentManifest{testManifest("plugins_air"), additional}
	result, err := composeComponents([]devBuildComponent{
		sourcedComponent(test, directory, "plugins-air", "plugins/air/lib/air.jar", manifests[0]),
		sourcedComponent(test, directory, "plugins-additional", "plugins/bridge/lib/bridge.jar", manifests[1]),
	}, filepath.Join(directory, "target"), composeOptions{additionalModules: []string{"intellij.air.plugin", "intellij.bridge.plugin"}})
	if err != nil {
		test.Fatal(err)
	}
	if !slices.Equal(result.additionalModules, []string{"intellij.air.plugin", "intellij.bridge.plugin"}) {
		test.Fatalf("additional modules = %q", result.additionalModules)
	}
	// The declaration is part of the launch metadata, so a distribution that only declared more is not reused.
	if result.fingerprint == mustFingerprint(test, manifests...) {
		test.Fatal("the fingerprint ignores the declared modules")
	}
}

func TestComposerRejectsAComponentThatAssembledAnUndeclaredModule(test *testing.T) {
	directory := tempDir(test)
	additional := testManifest("plugins_additional")
	additional.AdditionalModules = []string{"intellij.devkit"}
	_, err := composeComponents([]devBuildComponent{
		sourcedComponent(test, directory, "plugins-additional", "plugins/devkit/lib/devkit.jar", additional),
	}, filepath.Join(directory, "target"), composeOptions{additionalModules: []string{"intellij.air.plugin"}})
	requireError(test, err, "does not declare: [intellij.devkit]\n  declared: [intellij.air.plugin]\n  assembled: [intellij.devkit]")
}

// The collector used to own this mode assertion, because it changed the mode of its own copy of the jar. The copy is
// gone, so the composer sets the mode, and `executable = false` keeps its meaning.
func TestComposerCopiesTreeLessSourcedFilesAsNonExecutableDistributionFiles(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "staged/shared.jar")
	writeTestFile(test, source, "packed bytes")
	if err := os.Chmod(source, 0o700); err != nil {
		test.Fatal(err)
	}
	manifest := withEntries(testManifest("plugins_packed_content_modules"),
		sourcedEntry("plugins/one/lib/modules/shared.jar", source), sourcedEntry("plugins/two/lib/modules/shared.jar", source))
	target := filepath.Join(directory, "target")
	if _, err := compose([]devBuildComponent{{manifest: manifest}}, target); err != nil {
		test.Fatal(err)
	}
	for _, relativePath := range []string{"plugins/one/lib/modules/shared.jar", "plugins/two/lib/modules/shared.jar"} {
		copied := filepath.Join(target, relativePath)
		if info, err := os.Lstat(copied); err != nil || info.Mode()&os.ModeSymlink != 0 || readTestFile(test, copied) != "packed bytes" {
			test.Fatalf("copied file = %v, error = %v", info, err)
		}
		requirePermissions(test, copied, 0o644)
	}
}

func TestComposerHonorsTheDeclaredExecutableFlagWithoutChangingTheSource(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "ijent")
	writeTestFile(test, source, "binary bytes")
	if err := os.Chmod(source, 0o400); err != nil {
		test.Fatal(err)
	}
	entry := sourcedEntry("bin/ijent", source)
	entry.Executable = true
	manifest := withEntries(testManifest("ijent"), entry)
	target := filepath.Join(directory, "target")
	composed, err := compose([]devBuildComponent{{manifest: manifest}}, target)
	if err != nil {
		test.Fatal(err)
	}
	copied := filepath.Join(target, "bin/ijent")
	requirePermissions(test, copied, 0o755)
	requirePermissions(test, source, 0o400)
	nonExecutable := *manifest
	nonExecutable.Entries = []componentEntry{entry}
	nonExecutable.Entries[0].Executable = false
	if composed.fingerprint != mustFingerprint(test, manifest) || composed.fingerprint == mustFingerprint(test, &nonExecutable) {
		test.Fatal("the fingerprint ignores the declared executable flag")
	}
	writeTestFile(test, copied, "changed bytes")
	if readTestFile(test, source) != "binary bytes" {
		test.Fatal("the copy shares the bytes of its source")
	}
}

func TestComposerFollowsAStagingSymlinkOfATreeLessComponent(test *testing.T) {
	directory := tempDir(test)
	bytesFile := filepath.Join(directory, "bazel-out/packed.jar")
	writeTestFile(test, bytesFile, "jar bytes")
	staged := filepath.Join(directory, "sandbox/packed.jar")
	if err := os.MkdirAll(filepath.Dir(staged), 0o755); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink(bytesFile, staged); err != nil {
		test.Fatal(err)
	}
	target := filepath.Join(directory, "target")
	if _, err := compose([]devBuildComponent{{manifest: withEntries(testManifest("platform_packed_content_modules"), sourcedEntry("lib/packed.jar", staged))}}, target); err != nil {
		test.Fatal(err)
	}
	if err := os.Remove(bytesFile); err != nil {
		test.Fatal(err)
	}
	if readTestFile(test, filepath.Join(target, "lib/packed.jar")) != "jar bytes" {
		test.Fatal("the composer copied the staging link")
	}
}

func TestComposerPreservesExactModesWithoutModifyingSharedSources(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "shared-tool")
	writeTestFile(test, source, "tool")
	if err := os.Chmod(source, 0o400); err != nil {
		test.Fatal(err)
	}
	file := sourcedEntry("plugins/demo/bin/tool", source)
	file.Executable, file.Mode = true, pointer(int64(0o750))
	target := filepath.Join(directory, "target")
	if _, err := compose([]devBuildComponent{{manifest: withEntries(testManifest("plugin"), file)}}, target); err != nil {
		test.Fatal(err)
	}
	requirePermissions(test, filepath.Join(target, file.RelativePath), 0o750)
	requirePermissions(test, source, 0o400)
}

func TestComposerCreatesAManifestOnlyLinkWhenTheStagedSourceIsARealDirectory(test *testing.T) {
	directory := tempDir(test)
	sourceRoot := filepath.Join(directory, "plugin")
	// Bazel turned the link into a copied directory when it fetched the tree from the cache.
	stagedCopy := filepath.Join(sourceRoot, "current")
	writeTestFile(test, filepath.Join(stagedCopy, "payload"), "copied bytes")
	link := linkEntry("plugins/demo/current", "lib/payload")
	target := filepath.Join(directory, "target")
	if _, err := composeComponents([]devBuildComponent{{manifest: withEntries(testManifest("plugin"), link)}}, target,
		withDirectoryRunfiles(sourceRoot, "_main/plugin")); err != nil {
		test.Fatal(err)
	}
	requireLink(test, filepath.Join(target, link.RelativePath), "lib/payload")
	if info, err := os.Lstat(stagedCopy); err != nil || !info.IsDir() {
		test.Fatalf("staged copy = %v, error = %v", info, err)
	}
}

func TestComposerCreatesAManifestOnlyLinkFromTheManifestAndNotFromTheStagedLink(test *testing.T) {
	directory := tempDir(test)
	sourceRoot := filepath.Join(directory, "plugin")
	if err := os.MkdirAll(sourceRoot, 0o755); err != nil {
		test.Fatal(err)
	}
	// The staged link keeps the spelling of the packer, and the manifest holds the cleaned target.
	if err := os.Symlink("./lib/payload", filepath.Join(sourceRoot, "current")); err != nil {
		test.Fatal(err)
	}
	link := linkEntry("plugins/demo/current", "lib/payload")
	target := filepath.Join(directory, "target")
	if _, err := composeComponents([]devBuildComponent{{manifest: withEntries(testManifest("plugin"), link)}}, target,
		withDirectoryRunfiles(sourceRoot, "_main/plugin")); err != nil {
		test.Fatal(err)
	}
	requireLink(test, filepath.Join(target, link.RelativePath), "lib/payload")
}

func TestComposerRejectsRegularFilesThroughEscapingDirectoryAliases(test *testing.T) {
	directory := tempDir(test)
	sourceRoot := filepath.Join(directory, "plugin")
	outside := filepath.Join(directory, "outside")
	writeTestFile(test, filepath.Join(outside, "payload"), "outside bytes")
	if err := os.MkdirAll(sourceRoot, 0o755); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink(outside, filepath.Join(sourceRoot, "alias")); err != nil {
		test.Fatal(err)
	}
	file := sourcedEntry("plugins/demo/payload", filepath.Join(sourceRoot, "alias/payload"))
	target := filepath.Join(directory, "target")
	_, err := composeComponents([]devBuildComponent{{manifest: withEntries(testManifest("plugin"), file)}}, target,
		withDirectoryRunfiles(sourceRoot, "_main/plugin"))
	requireError(test, err, "escapes its declared source directory")
	requireAbsent(test, filepath.Join(target, file.RelativePath))
}

func TestComposerRejectsInvalidTreeLessEntries(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "packed.jar")
	writeTestFile(test, source, "packed bytes")
	ambiguousLink := sourcedEntry("lib/packed.jar", source)
	ambiguousLink.SymlinkTarget = pointer("other.jar")
	for _, invalid := range []struct {
		entries []componentEntry
		message string
	}{
		{[]componentEntry{fileEntry("lib/packed.jar")}, "declares no tree, so 'lib/packed.jar' must name where its bytes are"},
		{[]componentEntry{ambiguousLink}, "must declare the symbolic link 'lib/packed.jar' without a file source"},
		{[]componentEntry{sourcedEntry("../outside.jar", source)}, "escapes the distribution: ../outside.jar"},
		{[]componentEntry{sourcedEntry("lib/packed.jar", filepath.Join(directory, "absent.jar"))}, "but nothing is staged there"},
		{[]componentEntry{sourcedEntry("lib/packed.jar", directory+"/./packed.jar")}, "has an unsafe source"},
		{[]componentEntry{sourcedEntry("lib/packed.jar", directory)}, "does not name a regular file"},
	} {
		_, err := compose([]devBuildComponent{{manifest: withEntries(testManifest("platform_packed_content_modules"), invalid.entries...)}},
			filepath.Join(test.TempDir(), "target"))
		requireError(test, err, invalid.message)
	}
}

func TestComposerCreatesExplicitLinksForManifestOnlyComponents(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "packed.jar")
	writeTestFile(test, source, "packed bytes")
	manifest := withEntries(testManifest("plugin"),
		sourcedEntry("plugins/demo/lib/packed.jar", source), linkEntry("plugins/demo/current", "lib/packed.jar"))
	target := filepath.Join(directory, "target")
	if _, err := compose([]devBuildComponent{{manifest: manifest}}, target); err != nil {
		test.Fatal(err)
	}
	requireLink(test, filepath.Join(target, "plugins/demo/current"), "lib/packed.jar")
	if readTestFile(test, filepath.Join(target, "plugins/demo/current")) != "packed bytes" {
		test.Fatal("the link does not reach the file")
	}
}

func TestComposerOrdersALinkAfterTheLinksItsTargetTraverses(test *testing.T) {
	names := []string{
		"plugins/jcef/jcef.framework/Frameworks", "plugins/jcef/jcef.framework/Resources",
		"plugins/jcef/jcef.framework/Versions/Current", "plugins/jcef/jcef.framework/lib", "plugins/jcef/shared/lib",
	}
	targets := map[string]string{
		names[0]: "Versions/Current/Frameworks", names[1]: "Versions/Current/Resources", names[2]: "A", names[3]: "../shared/lib",
		names[4]: "lib-1",
	}
	ordered, err := orderDevBuildLinks(names, targets)
	expected := []string{names[2], names[4], names[0], names[1], names[3]}
	if err != nil || !slices.Equal(ordered, expected) {
		test.Fatalf("ordered = %q, error = %v", ordered, err)
	}
	_, err = orderDevBuildLinks([]string{"a", "b"}, map[string]string{"a": "b", "b": "a"})
	requireError(test, err, "Dev-build component symbolic link cycle at 'a'")
}

func TestComposerRejectsAFileInsideAnotherDeclaredEntry(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "packed.jar")
	writeTestFile(test, source, "bytes")
	manifest := withEntries(testManifest("plugin"), sourcedEntry("plugins/demo", source), sourcedEntry("plugins/demo/lib/plugin.jar", source))
	_, err := compose([]devBuildComponent{{manifest: manifest}}, filepath.Join(directory, "target"))
	requireError(test, err, "is below another entry")
	requireAbsent(test, filepath.Join(directory, "target/plugins"))
}

func TestComposerReservesGeneratedMetadataDestinations(test *testing.T) {
	directory := tempDir(test)
	for _, name := range []string{"fingerprint.txt", "Fingerprint.txt", "core-classpath.txt", pluginClassPath, "plugins", "Plugins"} {
		manifest := withEntries(testManifest("plugin"), linkEntry(name, "lib/app.jar"))
		if _, err := compose([]devBuildComponent{{manifest: manifest}}, filepath.Join(directory, "target")); err == nil {
			test.Fatalf("accepted a link at %s", name)
		}
		requireAbsent(test, filepath.Join(directory, "target", name))
	}
}

func TestComposerRejectsEscapingLinkChainsBeforeCreatingLinks(test *testing.T) {
	directory := tempDir(test)
	manifest := withEntries(testManifest("plugin"), linkEntry("plugins/demo/current", "../.."), linkEntry("plugins/demo/escape", "current/../outside"))
	_, err := compose([]devBuildComponent{{manifest: manifest}}, filepath.Join(directory, "target"))
	requireError(test, err, "link chain escapes")
	requireAbsent(test, filepath.Join(directory, "target/plugins"))
}

func TestComposerFailsInsteadOfChangingAManifestOnlyLinkTarget(test *testing.T) {
	directory := tempDir(test)
	for _, target := range []string{"payload/", "lib//payload", "lib//payload/"} {
		manifest := withEntries(testManifest("plugin"), linkEntry("plugins/demo/current", target))
		_, err := compose([]devBuildComponent{{manifest: manifest}}, filepath.Join(directory, "target"))
		requireError(test, err, "cannot preserve symbolic link")
		requireAbsent(test, filepath.Join(directory, "target/plugins"))
	}
}

func TestComposerRejectsAPathTwoComponentsBothProvide(test *testing.T) {
	directory := tempDir(test)
	source := filepath.Join(directory, "packed.jar")
	writeTestFile(test, source, "packed bytes")
	_, err := compose([]devBuildComponent{
		sourcedComponent(test, directory, "platform", "lib/packed.jar", testManifest("platform_lib")),
		{manifest: withEntries(testManifest("platform_packed_content_modules"), sourcedEntry("lib/packed.jar", source))},
	}, filepath.Join(directory, "target"))
	requireError(test, err, "both provide 'lib/packed.jar'")
}

func TestComposerAppliesDirectoryModesDeepestFirst(test *testing.T) {
	directory := tempDir(test)
	manifest := withEntries(testManifest("plugin"),
		componentEntry{RelativePath: "resources", Type: "directory", Mode: pointer(int64(0o500))},
		componentEntry{RelativePath: "resources/empty", Type: "directory", Mode: pointer(int64(0o710))})
	target := filepath.Join(directory, "target")
	test.Cleanup(func() { os.Chmod(filepath.Join(target, "resources"), 0o755) })
	if _, err := compose([]devBuildComponent{{manifest: manifest}}, target); err != nil {
		test.Fatal(err)
	}
	requirePermissions(test, filepath.Join(target, "resources"), 0o500)
	requirePermissions(test, filepath.Join(target, "resources/empty"), 0o710)
}

type boundTree struct {
	physical string
	staged   string
	bindings *componentSources
	file     string
}

// createBoundTree stages one tree as Bazel does in a sandbox: the staged members link to the physical outputs, and
// the bindings file describes the tree.
func createBoundTree(test *testing.T, directory string, members []string) (*boundTree, error) {
	test.Helper()
	physical := filepath.Join(directory, "physical/trees/plugin")
	staged := filepath.Join(directory, "sandbox/trees/plugin")
	for _, tree := range []string{physical, staged} {
		if err := os.MkdirAll(filepath.Join(tree, "lib"), 0o755); err != nil {
			test.Fatal(err)
		}
	}
	writeTestFile(test, filepath.Join(physical, "lib/native.jar"), "native bytes")
	if err := os.Symlink(filepath.Join(physical, "lib/native.jar"), filepath.Join(staged, "lib/native.jar")); err != nil {
		test.Fatal(err)
	}
	physicalMetadata := filepath.Join(directory, "physical/metadata/bindings.jsonl")
	stagedMetadata := filepath.Join(directory, "sandbox/metadata/bindings.jsonl")
	line := []byte(`{"component":"plugin","source":`)
	line = appendJSONString(line, staged)
	line = append(line, `,"anchorRelativePath":"../trees/plugin","type":"directory","members":[`...)
	for index, member := range members {
		if index != 0 {
			line = append(line, ',')
		}
		line = appendJSONString(line, member)
	}
	writeTestBytes(test, physicalMetadata, append(line, "]}"...))
	if err := os.MkdirAll(filepath.Dir(stagedMetadata), 0o755); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink(physicalMetadata, stagedMetadata); err != nil {
		test.Fatal(err)
	}
	bindings, err := readSourceBindings(stagedMetadata, []compositionComponent{{Manifest: "plugin"}})
	if err != nil {
		return nil, err
	}
	return &boundTree{physical: physical, staged: staged, bindings: bindings["plugin"], file: stagedMetadata}, nil
}

func mustBoundTree(test *testing.T, directory string, members ...string) *boundTree {
	test.Helper()
	if members == nil {
		members = []string{"lib/native.jar"}
	}
	tree, err := createBoundTree(test, directory, members)
	if err != nil {
		test.Fatal(err)
	}
	return tree
}

func TestComposerConsumesBoundSandboxMembersAndGenuineLinks(test *testing.T) {
	directory := tempDir(test)
	fixture := mustBoundTree(test, directory)
	file := sourcedEntry("plugins/demo/lib/native.jar", filepath.Join(fixture.staged, "lib/native.jar"))
	file.Mode, file.Executable = pointer(int64(0o751)), true
	target := filepath.Join(directory, "target")
	options := withDirectoryRunfiles(fixture.staged, "_main/tree")
	manifest := withEntries(testManifest("plugin"), file, linkEntry("plugins/demo/current", "lib/native.jar"))
	if _, err := composeComponents([]devBuildComponent{{manifest: manifest, sourceBindings: fixture.bindings}}, target, options); err != nil {
		test.Fatal(err)
	}
	copied := filepath.Join(target, file.RelativePath)
	if info, err := os.Lstat(copied); err != nil || info.Mode()&os.ModeSymlink != 0 || readTestFile(test, copied) != "native bytes" {
		test.Fatalf("copied file = %v, error = %v", info, err)
	}
	requireLink(test, filepath.Join(target, "plugins/demo/current"), "lib/native.jar")
	requirePermissions(test, copied, 0o751)
}

func TestComposerRejectsSandboxMembersWithoutBindings(test *testing.T) {
	directory := tempDir(test)
	fixture := mustBoundTree(test, directory)
	manifest := withEntries(testManifest("plugin"), sourcedEntry("lib/native.jar", filepath.Join(fixture.staged, "lib/native.jar")))
	_, err := composeComponents([]devBuildComponent{{manifest: manifest}}, filepath.Join(directory, "target"),
		withDirectoryRunfiles(fixture.staged, "_main/tree"))
	requireError(test, err, "escapes its declared source directory")
}

func TestSourceBindingsRejectOutsideSourcesAndMemberTampering(test *testing.T) {
	directory := tempDir(test)
	fixture := mustBoundTree(test, directory)
	outside := filepath.Join(directory, "outside")
	writeTestFile(test, outside, "native bytes")
	for _, source := range []string{outside, filepath.Join(fixture.staged, "lib/Native.jar"), fixture.staged} {
		_, err := fixture.bindings.resolve(source)
		requireError(test, err, "Missing declared artifact binding")
	}
	other, err := readSourceBindings(fixture.file, []compositionComponent{{Manifest: "plugin"}, {Manifest: "other"}})
	if err != nil {
		test.Fatal(err)
	}
	_, err = other["other"].resolve(filepath.Join(fixture.staged, "lib/native.jar"))
	requireError(test, err, "Missing declared artifact binding")
	staged := filepath.Join(fixture.staged, "lib/native.jar")
	if err := os.Remove(staged); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink(outside, staged); err != nil {
		test.Fatal(err)
	}
	_, err = fixture.bindings.resolve(staged)
	requireError(test, err, "Staged source differs")
}

func TestSourceBindingsRejectGenuineFileLinksAndDirectoryEscapes(test *testing.T) {
	directory := tempDir(test)
	for _, escape := range []string{"file", "directory", "root"} {
		fixture := mustBoundTree(test, filepath.Join(directory, escape))
		outside := filepath.Join(directory, "outside-"+escape, "lib")
		writeTestFile(test, filepath.Join(outside, "native.jar"), "native bytes")
		member := filepath.Join(fixture.physical, "lib/native.jar")
		if err := os.Remove(member); err != nil {
			test.Fatal(err)
		}
		var err error
		switch escape {
		case "file":
			err = os.Symlink(filepath.Join(outside, "native.jar"), member)
		case "directory":
			if err = os.Remove(filepath.Dir(member)); err == nil {
				err = os.Symlink(outside, filepath.Dir(member))
			}
		default:
			if err = os.Remove(filepath.Dir(member)); err == nil {
				if err = os.Remove(fixture.physical); err == nil {
					err = os.Symlink(filepath.Dir(outside), fixture.physical)
				}
			}
		}
		if err != nil {
			test.Fatal(err)
		}
		_, err = fixture.bindings.resolve(filepath.Join(fixture.staged, "lib/native.jar"))
		requireError(test, err, map[string]string{
			"file": "not a regular file", "directory": "escaping directory alias", "root": "escapes its artifact binding",
		}[escape])
	}
}

// composeBoundLink composes a bound file and a link to it, and checks that the link keeps its spelling.
func composeBoundLink(test *testing.T, directory, fileName, linkTarget string, linkToDirectory bool) {
	test.Helper()
	fixture := mustBoundTree(test, directory, fileName)
	if fileName != "lib/native.jar" {
		writeTestFile(test, filepath.Join(fixture.physical, fileName), "native bytes")
		if err := os.Symlink(filepath.Join(fixture.physical, fileName), filepath.Join(fixture.staged, fileName)); err != nil {
			test.Fatal(err)
		}
	}
	manifest := withEntries(testManifest("plugin"), sourcedEntry(fileName, filepath.Join(fixture.staged, fileName)), linkEntry("current", linkTarget))
	manifest.CoreClassPath = []string{fileName}
	target := filepath.Join(directory, "target")
	result, err := composeComponents([]devBuildComponent{{manifest: manifest, sourceBindings: fixture.bindings}}, target,
		withDirectoryRunfiles(fixture.staged, "_main/tree"))
	if err != nil {
		test.Fatal(err)
	}
	requireLink(test, filepath.Join(target, "current"), linkTarget)
	reached := filepath.Join(target, "current")
	if linkToDirectory {
		reached = filepath.Join(reached, "native.jar")
	}
	if readTestFile(test, reached) != "native bytes" || !slices.Equal(result.coreClassPath, []string{fileName}) {
		test.Fatalf("the link %s does not reach the file, or the core classpath is %q", linkTarget, result.coreClassPath)
	}
}

func TestComposerPreservesEquivalentRelativeLinkSpellings(test *testing.T) {
	directory := tempDir(test)
	for index, target := range []string{"./lib/native.jar", "lib/../lib/native.jar"} {
		composeBoundLink(test, filepath.Join(directory, "spelling-"+strconv.Itoa(index)), "lib/native.jar", target, false)
	}
	composeBoundLink(test, filepath.Join(directory, "directory"), "lib/native.jar", "./lib/../lib/.", true)
	composeBoundLink(test, filepath.Join(directory, "unicode"), "lib/é.jar", "lib/é.jar", false)
}

func TestSourceBindingsRejectMissingMembersAndAliases(test *testing.T) {
	directory := tempDir(test)
	fixture := mustBoundTree(test, filepath.Join(directory, "missing"), []string{}...)
	_, err := fixture.bindings.resolve(filepath.Join(fixture.staged, "lib/native.jar"))
	requireError(test, err, "Missing declared artifact binding")
	for index, members := range [][]string{
		{"lib/native.jar", "lib/native.jar"},
		{"lib/native.jar", "lib/Native.jar"},
		{"lib/native.jar", "Lib/second.jar"},
		{"lib/é.jar", "lib/é.jar"},
		{"lib/../outside"},
		{"lib//native.jar"},
		{"lib", "lib/native.jar"},
	} {
		if _, err := createBoundTree(test, filepath.Join(directory, "invalid-"+strconv.Itoa(index)), members); err == nil {
			test.Fatalf("accepted members %q", members)
		}
	}
}

func TestSourceBindingsRejectChangedOwnersAndAnchorPaths(test *testing.T) {
	directory := tempDir(test)
	for index, mutation := range [][2]string{
		{`"component":"plugin"`, `"component":"other"`},
		{"../trees/plugin", "../trees/other"},
		{`"type":"directory"`, `"type":"file"`},
	} {
		fixture := mustBoundTree(test, filepath.Join(directory, "tamper-"+strconv.Itoa(index)))
		physical, err := filepath.EvalSymlinks(fixture.file)
		if err != nil {
			test.Fatal(err)
		}
		writeTestFile(test, physical, strings.Replace(readTestFile(test, physical), mutation[0], mutation[1], 1))
		if _, err := readSourceBindings(fixture.file, []compositionComponent{{Manifest: "plugin"}}); err == nil {
			test.Fatalf("accepted the mutation %q", mutation)
		}
	}
}
