// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Command plugin-descriptor-writer writes the `META-INF/plugin.xml` a plugin's main jar receives.
//
// The --embedded-product mode resolves includes and embeds content modules without plugin stamps.
// The --application-info mode produces the application info of the embedded JetBrains Client.
//
// It is the executor of the `dev_dist_plugin_descriptor` rule
// (`community/platform/build-scripts/bazel-rules/dev_dist_plugin_descriptor.bzl`), and the Go counterpart of
// `applyPluginDescriptorPatch`
// (`applyPluginDescriptorPatch` of
// `community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt`).
// `build/decisions/0006-content-module-in-jar-out-composer-places-it.md` puts the executors in Go, and a descriptor
// feeds every plugin main jar, so a JVM action for it sits on the build's critical path.
//
// ### The stages
//
// The patch has seven stages, and this binary runs four of them:
//
//	source → rawTextPatcher → reserialized → stamps → includes → contentModules → textPatcher
//
// `rawTextPatcher` and `textPatcher` are per-layout Kotlin lambdas, so they are code and not data: a plugin whose
// layout states one is held out of this rule's population by the generated plan. `reserialized` is the round trip of
// `internal/descriptorxml`, `stamps` is `internal/stamps`, and the two structural stages are `internal/structural`.
//
// ### The guards
//
// This binary is the one producer of the text. The curated cases of `internal/descriptorxml`, `internal/stamps` and
// `internal/structural` are the committed gate, and every expectation in them is a text the platform produced.
// `//build:idea_dev_descriptor_leaf_build_test` builds a sample group of leaves. `./build/dev-dist.cmd snapshot diff`
// compares every plugin main jar of a composed distribution against a recorded baseline.
package main

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"jetbrains.com/plugin-descriptor-writer/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-writer/internal/markers"
	"jetbrains.com/plugin-descriptor-writer/internal/stamps"
	"jetbrains.com/plugin-descriptor-writer/internal/structural"
)

func main() {
	if code := run(os.Args[1:]); code != 0 {
		os.Exit(code)
	}
}

// request is one plugin's request, as the rule states it.
//
// Every field is a string, a boolean, a path or a list of those. Nothing here can reach a layout or a build context.
// `dev_dist_plugin_descriptor` states the request, and this parser is its one reader.
type request struct {
	output          string
	mainModule      string
	directoryName   string
	mainJarName     string
	source          string
	sourceEntry     string
	buildNumberFile string
	releaseDate     string
	releaseVersion  string
	isEap           bool
	exactVersion    bool
	retainProduct   bool
	embedsContent   bool
	// reserializeBeforeContentEmbedding finishes ordinary XML normalization before embedded descriptors add CDATA.
	reserializeBeforeContentEmbedding bool
	// refusedContentModules are the content modules the product's filter refuses. Normally empty.
	refusedContentModules []string
	separateJar           map[string]bool
	// pluginDescriptors and platformDescriptors are the descriptors the patch can reach, keyed by load path.
	pluginDescriptors   map[string]string
	platformDescriptors map[string]string
	// pluginDescriptorsInJar are the descriptors no production source root holds, keyed by load path and valued by the
	// jars of the library container that answers it. The load path is also the zip entry: `toLoadPath` strips the
	// leading `/`. The rule declares a container rather than a jar, so the jar that holds the entry is found here.
	pluginDescriptorsInJar map[string][]string
	pluginModules          []string
	platformModules        []string
	// markers is the layout's raw text patch as marker-table rows, in the order it applies them.
	markers []string
	// versionSuffix is what the layout appends to the IDE build version, empty for a layout that stamps it unchanged.
	versionSuffix string
	// compatibleBuildRange overrides the since/until constraint generation.
	compatibleBuildRange string
	// reserializedOutput, when set, receives the final descriptor after one more `descriptorxml` round trip. That is
	// the form the plugin classpath record embeds (`generatePluginClassPathFromOrderedAssets` of `orderedAssets.kt`).
	reserializedOutput string
}

func run(arguments []string) int {
	lines, err := readArgumentLines(arguments)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
	}
	mode, err := selectOperation(lines)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
	}
	switch mode {
	case embeddedProductMode:
		return runEmbeddedProduct(lines)
	case applicationInfoMode:
		return runApplicationInfo(lines)
	}
	parsed, err := parseRequest(lines)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
	}
	content, err := patch(parsed)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: could not patch the descriptor (module=%s): %v\n", parsed.mainModule, err)
		return 1
	}
	outputs := map[string]string{parsed.output: content}
	if parsed.reserializedOutput != "" {
		reserialized, err := reserialize(content)
		if err != nil {
			fmt.Fprintf(os.Stderr, "ERROR: could not reserialize the descriptor (module=%s): %v\n", parsed.mainModule, err)
			return 1
		}
		outputs[parsed.reserializedOutput] = reserialized
	}
	for file, text := range outputs {
		if err := writeOutput(file, text); err != nil {
			fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
			return 1
		}
	}
	return 0
}

const (
	embeddedProductMode = "--embedded-product"
	applicationInfoMode = "--application-info"
)

// selectOperation leaves requests without a mode on the plugin patching path.
func selectOperation(lines []string) (string, error) {
	mode := ""
	for _, line := range lines {
		option, _, hasValue := strings.Cut(line, "=")
		if option != embeddedProductMode && option != applicationInfoMode {
			continue
		}
		if hasValue {
			return "", fmt.Errorf("%s is a flag and takes no value", option)
		}
		if mode != "" {
			return "", fmt.Errorf("only one mode flag is allowed, got %s and %s", mode, option)
		}
		mode = option
	}
	return mode, nil
}

// reserialize is the `JDOMUtil.load` and `JDOMUtil.write` pair the classpath writer applies to a descriptor.
func reserialize(content string) (string, error) {
	element, err := descriptorxml.Read(content)
	if err != nil {
		return "", err
	}
	return descriptorxml.Write(element), nil
}

func writeOutput(file string, content string) error {
	if err := os.MkdirAll(filepath.Dir(file), 0o755); err != nil {
		return err
	}
	return os.WriteFile(file, []byte(content), 0o644)
}

// patch is the port of `applyPluginDescriptorPatch` (`PluginXmlPatcher.kt`), the body the assembly runs, driven by
// the plan instead of by the product layout.
func patch(parsed request) (string, error) {
	buildNumberContent, err := os.ReadFile(parsed.buildNumberFile)
	if err != nil {
		return "", err
	}
	buildNumber := strings.TrimSpace(string(buildNumberContent))
	pluginVersion, err := stamps.PluginBuildNumber(buildNumber)
	if err != nil {
		return "", err
	}
	pluginVersion += parsed.versionSuffix
	compatibleBuildRange := stamps.RangeNewerWithSameBaseline
	switch {
	case parsed.compatibleBuildRange == "EXACT":
		compatibleBuildRange = stamps.RangeExact
	case parsed.compatibleBuildRange == "RESTRICTED_TO_SAME_RELEASE":
		compatibleBuildRange = stamps.RangeRestrictedToSameRelease
	case parsed.compatibleBuildRange == "NEWER_WITH_SAME_BASELINE":
		compatibleBuildRange = stamps.RangeNewerWithSameBaseline
	case parsed.compatibleBuildRange != "":
		return "", fmt.Errorf("unknown compatible build range: %s", parsed.compatibleBuildRange)
	case parsed.exactVersion:
		compatibleBuildRange = stamps.RangeExact
	case parsed.isEap:
		compatibleBuildRange = stamps.RangeRestrictedToSameRelease
	}
	sinceBuild, untilBuild := stamps.CompatiblePlatformVersionRange(compatibleBuildRange, buildNumber)

	var source []byte
	if parsed.sourceEntry == "" {
		source, err = os.ReadFile(parsed.source)
	} else {
		source, err = readFirstZipEntry([]string{parsed.source}, parsed.sourceEntry)
	}
	if err != nil {
		return "", err
	}
	patched, err := markers.Apply(string(source), parsed.markers)
	if err != nil {
		return "", err
	}
	pluginCache, err := readSeed(parsed.pluginDescriptors)
	if err != nil {
		return "", err
	}
	if err := seedFromJars(pluginCache, parsed.pluginDescriptorsInJar); err != nil {
		return "", err
	}
	platformCache, err := readSeed(parsed.platformDescriptors)
	if err != nil {
		return "", err
	}
	resolver := structural.NewResolver([]structural.Scope{
		{Modules: parsed.pluginModules, Cache: pluginCache},
		{Modules: parsed.platformModules, Cache: platformCache},
	})

	element, err := descriptorxml.Read(patched)
	if err != nil {
		return "", err
	}
	stamps.Apply(element, stamps.Request{
		Version:        pluginVersion,
		SinceBuild:     sinceBuild,
		UntilBuild:     untilBuild,
		ReleaseDate:    parsed.releaseDate,
		ReleaseVersion: parsed.releaseVersion,
		// A dev distribution publishes no plugin: `PluginBuilder` passes an empty set on this path.
		ToPublish:                               false,
		RetainProductDescriptorForBundledPlugin: parsed.retainProduct,
		IsEap:                                   parsed.isEap,
	})
	if err := structural.ResolveIncludes(element, resolver); err != nil {
		return "", err
	}
	if parsed.reserializeBeforeContentEmbedding {
		element, err = descriptorxml.Read(descriptorxml.Write(element))
		if err != nil {
			return "", err
		}
	}
	err = structural.EmbedContentModules(element, structural.ContentRequest{
		MainModule:  parsed.mainModule,
		Refused:     parsed.refusedContentModules,
		SeparateJar: parsed.separateJar,
		Embeds:      parsed.embedsContent,
	}, pluginCache, resolver)
	if err != nil {
		return "", err
	}
	// `patchText` is the identity here, for `rawTextPatcher`'s reason.
	return descriptorxml.Write(element), nil
}

func readSeed(files map[string]string) (*structural.Cache, error) {
	seed := make(map[string][]byte, len(files))
	for loadPath, file := range files {
		data, err := os.ReadFile(file)
		if err != nil {
			return nil, err
		}
		seed[loadPath] = data
	}
	return structural.NewCache(seed), nil
}

// seedFromJars puts a descriptor that lives inside a declared library container into the cache.
//
// The assembly uses findFileInModuleLibraryDependencies in moduleContentUtil.kt to check each library jar for the load path.
// The rule declares the container. The writer checks its jars in order and uses the first matching entry.
// If no jar has the entry, the action fails and names every jar it checked.
func seedFromJars(cache *structural.Cache, candidates map[string][]string) error {
	for loadPath, jars := range candidates {
		data, err := readFirstZipEntry(jars, loadPath)
		if err != nil {
			return err
		}
		cache.PutIfAbsent(loadPath, data)
	}
	return nil
}

func readFirstZipEntry(jars []string, name string) ([]byte, error) {
	for _, jar := range jars {
		reader, err := zip.OpenReader(jar)
		if err != nil {
			return nil, err
		}
		data, found, err := readZipEntry(reader, name)
		closeErr := reader.Close()
		if err != nil {
			return nil, err
		}
		if closeErr != nil {
			return nil, closeErr
		}
		if found {
			return data, nil
		}
	}
	return nil, fmt.Errorf("no declared jar has the entry '%s': %s", name, strings.Join(jars, ", "))
}

func readZipEntry(reader *zip.ReadCloser, name string) ([]byte, bool, error) {
	for _, entry := range reader.File {
		if entry.Name != name {
			continue
		}
		opened, err := entry.Open()
		if err != nil {
			return nil, false, err
		}
		defer opened.Close()
		data, err := io.ReadAll(opened)
		return data, err == nil, err
	}
	return nil, false, nil
}

// readArgumentLines reads a multiline --flagfile or returns the direct arguments.
func readArgumentLines(arguments []string) ([]string, error) {
	if len(arguments) == 1 && strings.HasPrefix(arguments[0], "--flagfile=") {
		content, err := os.ReadFile(strings.TrimPrefix(arguments[0], "--flagfile="))
		if err != nil {
			return nil, err
		}
		return strings.Split(strings.ReplaceAll(string(content), "\r\n", "\n"), "\n"), nil
	}
	return arguments, nil
}

// parseRequest reads the parameter file `dev_dist_plugin_descriptor` writes, one option per line.
//
// An option the parser does not know fails the run. That is the platform's rule too, and it keeps the rule and this
// binary on one spelling: a rule that grows an option reaches this parser or fails here.
func parseRequest(lines []string) (request, error) {
	parsed := request{
		embedsContent:          true,
		separateJar:            map[string]bool{},
		pluginDescriptors:      map[string]string{},
		platformDescriptors:    map[string]string{},
		pluginDescriptorsInJar: map[string][]string{},
	}
	for _, line := range lines {
		if line == "" {
			continue
		}
		option, value, _ := strings.Cut(line, "=")
		var err error
		switch option {
		case "--out":
			parsed.output = value
		case "--main-module":
			parsed.mainModule = value
		case "--directory-name":
			// This binary reads the value and writes it nowhere.
			parsed.directoryName = value
		case "--main-jar-name":
			parsed.mainJarName = value
		case "--source":
			if parsed.source != "" {
				err = fmt.Errorf("the descriptor source is declared more than once")
				break
			}
			parsed.source = value
		case "--source-in-jar":
			if parsed.source != "" {
				err = fmt.Errorf("the descriptor source is declared more than once")
				break
			}
			parsed.sourceEntry, parsed.source, err = parseDescriptorJar(value)
		case "--build-number-file":
			parsed.buildNumberFile = value
		case "--release-date":
			parsed.releaseDate = value
		case "--release-version":
			parsed.releaseVersion = value
		case "--eap":
			parsed.isEap, err = parseBooleanStrict(value)
		case "--exact-version":
			parsed.exactVersion, err = parseBooleanStrict(value)
		case "--retain-product-descriptor":
			parsed.retainProduct, err = parseBooleanStrict(value)
		case "--embed-content-modules":
			parsed.embedsContent, err = parseBooleanStrict(value)
		case "--reserialize-before-content-embedding":
			parsed.reserializeBeforeContentEmbedding, err = parseBooleanStrict(value)
		case "--refused-content-module":
			parsed.refusedContentModules = append(parsed.refusedContentModules, value)
		case "--separate-jar":
			parsed.separateJar[value] = true
		case "--plugin-descriptor":
			err = putDescriptor(parsed.pluginDescriptors, value)
		case "--plugin-descriptor-in-jar":
			err = appendDescriptorJar(parsed.pluginDescriptorsInJar, value)
		case "--marker":
			parsed.markers = append(parsed.markers, value)
		case "--version-suffix":
			parsed.versionSuffix = value
		case "--compatible-build-range":
			parsed.compatibleBuildRange = value
		case "--reserialized-output":
			parsed.reserializedOutput = value
		case "--platform-descriptor":
			err = putDescriptor(parsed.platformDescriptors, value)
		case "--plugin-module":
			parsed.pluginModules = append(parsed.pluginModules, value)
		case "--platform-module":
			parsed.platformModules = append(parsed.platformModules, value)
		default:
			err = fmt.Errorf("unknown option '%s'", option)
		}
		if err != nil {
			return parsed, err
		}
	}
	for _, required := range []struct {
		option string
		value  string
	}{
		{"--out", parsed.output},
		{"--main-module", parsed.mainModule},
		{"--source", parsed.source},
		{"--build-number-file", parsed.buildNumberFile},
	} {
		if required.value == "" {
			return parsed, fmt.Errorf("%s is required", required.option)
		}
	}
	// `--release-date` and `--release-version` are mandatory on the rule, so an empty one is a request the rule cannot
	// state. They reach `<product-descriptor>` alone, and 1 of the 163 plugins states one.
	return parsed, nil
}

func parseDescriptorJar(value string) (string, string, error) {
	entry, jar, found := strings.Cut(value, "=")
	if !found || entry == "" || jar == "" {
		return "", "", fmt.Errorf("a jar descriptor is '<entry>=<jar>', and '%s' is not", value)
	}
	return entry, jar, nil
}

// parseBooleanStrict is Kotlin's `String.toBooleanStrict`, which accepts exactly `true` and `false`.
func parseBooleanStrict(value string) (bool, error) {
	switch value {
	case "true":
		return true, nil
	case "false":
		return false, nil
	}
	return false, fmt.Errorf("'%s' is neither 'true' nor 'false'", value)
}

// putDescriptor records a declared file by its load path.
func putDescriptor(into map[string]string, value string) error {
	loadPath, file, found := strings.Cut(value, "=")
	if !found || loadPath == "" {
		return fmt.Errorf("a descriptor is '<load path>=<file>', and '%s' is not", value)
	}
	into[loadPath] = file
	return nil
}

// appendDescriptorJar records a jar candidate for a load path.
//
// One option per (load path, jar), because the rule declares a library container and states every jar of it. The order
// is the container's own, and the first jar that has the entry answers.
func appendDescriptorJar(into map[string][]string, value string) error {
	loadPath, jar, found := strings.Cut(value, "=")
	if !found || loadPath == "" {
		return fmt.Errorf("a descriptor is '<load path>=<file>', and '%s' is not", value)
	}
	into[loadPath] = append(into[loadPath], jar)
	return nil
}
