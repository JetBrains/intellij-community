// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Command plugin-descriptor-patcher writes the `META-INF/plugin.xml` a plugin's main jar receives.
//
// It is the executor of the `dev_dist_plugin_descriptor` rule
// (`community/platform/build-scripts/bazel-rules/dev_dist_plugin_descriptor.bzl`), and the Go counterpart of
// `applyPluginDescriptorPatch`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt:89`).
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
// ### The reference producer
//
// `@community//platform/build-scripts/bazel-rules/dev-dist-plugin-descriptor` is the JVM tool this binary replaced. It
// stays, and it takes the same request: `./build/dev-dist.cmd descriptors --two-producer` runs both over the same
// declared inputs and compares their bytes per plugin. That is the gate that makes a second implementation safe.
package main

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-patcher/internal/markers"
	"jetbrains.com/plugin-descriptor-patcher/internal/stamps"
	"jetbrains.com/plugin-descriptor-patcher/internal/structural"
)

func main() {
	if code := run(os.Args[1:]); code != 0 {
		os.Exit(code)
	}
}

// request is one plugin's request, as the rule states it.
//
// It is `DevDistPluginDescriptorRequest` (`DevDistPluginDescriptorMain.kt:56-82`), field for field and option for
// option. The two binaries take one request spelling, so the rule's executable is the only thing the swap changed.
type request struct {
	output          string
	mainModule      string
	directoryName   string
	mainJarName     string
	source          string
	buildNumberFile string
	buildDateSecond int64
	releaseDate     string
	releaseVersion  string
	isEap           bool
	exactVersion    bool
	retainProduct   bool
	embedsContent   bool
	contentModules  []string
	separateJar     map[string]bool
	// pluginDescriptors and platformDescriptors are the descriptors the patch can reach, keyed by load path.
	pluginDescriptors   map[string]string
	platformDescriptors map[string]string
	// pluginDescriptorsInJar are the descriptors no production source root holds, keyed by load path and valued by the
	// jar that holds them. The load path is also the zip entry: `toLoadPath` strips the leading `/`.
	pluginDescriptorsInJar map[string]string
	pluginModules          []string
	platformModules        []string
	// markers is the layout's raw text patch as marker-table rows, in the order it applies them.
	markers []string
	// versionSuffix is what the layout appends to the IDE build version, empty for a layout that stamps it unchanged.
	versionSuffix string
}

func run(arguments []string) int {
	lines, err := readArgumentLines(arguments)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
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
	if err := os.MkdirAll(filepath.Dir(parsed.output), 0o755); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 1
	}
	if err := os.WriteFile(parsed.output, []byte(content), 0o644); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 1
	}
	return 0
}

// patch is `patchPluginDescriptorFromPlan` and the body it calls
// (`DevDistPluginDescriptorMain.kt:85-143`, `PluginXmlPatcher.kt:89-140`).
func patch(parsed request) (string, error) {
	buildNumberContent, err := os.ReadFile(parsed.buildNumberFile)
	if err != nil {
		return "", err
	}
	buildNumber := strings.TrimSpace(string(buildNumberContent))
	pluginVersion, err := stamps.PluginBuildNumber(buildNumber, parsed.buildDateSecond)
	if err != nil {
		return "", err
	}
	pluginVersion += parsed.versionSuffix
	compatibleBuildRange := stamps.RangeNewerWithSameBaseline
	switch {
	case parsed.exactVersion:
		compatibleBuildRange = stamps.RangeExact
	case parsed.isEap:
		compatibleBuildRange = stamps.RangeRestrictedToSameRelease
	}
	sinceBuild, untilBuild := stamps.CompatiblePlatformVersionRange(compatibleBuildRange, buildNumber)

	source, err := os.ReadFile(parsed.source)
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
		// A dev distribution publishes no plugin: `PluginBuilder` passes an empty set on this path
		// (`DevDistPluginDescriptorMain.kt:120`).
		ToPublish:                               false,
		RetainProductDescriptorForBundledPlugin: parsed.retainProduct,
		IsEap:                                   parsed.isEap,
	})
	if err := structural.ResolveIncludes(element, resolver); err != nil {
		return "", err
	}
	err = structural.EmbedContentModules(element, structural.ContentRequest{
		MainModule:  parsed.mainModule,
		Modules:     parsed.contentModules,
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

// seedFromJars puts a descriptor that lives inside a declared jar into the cache.
//
// It is `readSeedFromJars` (`DevDistPluginDescriptorMain.kt:203`). The assembly reaches such a file through
// `findFileInModuleLibraryDependencies` (`moduleContentUtil.kt:148`), which asks each declared library jar for the load
// path; the plan names the one jar that answers, so the entry is read directly.
func seedFromJars(cache *structural.Cache, jars map[string]string) error {
	for loadPath, jar := range jars {
		reader, err := zip.OpenReader(jar)
		if err != nil {
			return err
		}
		data, err := readZipEntry(reader, jar, loadPath)
		closeErr := reader.Close()
		if err != nil {
			return err
		}
		if closeErr != nil {
			return closeErr
		}
		cache.PutIfAbsent(loadPath, data)
	}
	return nil
}

func readZipEntry(reader *zip.ReadCloser, jar string, name string) ([]byte, error) {
	for _, entry := range reader.File {
		if entry.Name != name {
			continue
		}
		opened, err := entry.Open()
		if err != nil {
			return nil, err
		}
		defer opened.Close()
		return io.ReadAll(opened)
	}
	return nil, fmt.Errorf("'%s' has no entry '%s'", jar, name)
}

// readArgumentLines is `readArgumentLines` (`DevDistPluginDescriptorMain.kt:283-288`).
//
// The rule passes one `--flagfile=<path>` of a multiline parameter file, the way `content_module_jar` and `ij_plugin`
// do. Plain arguments are accepted too, so the binary is runnable by hand.
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

// parseRequest is `parseDevDistPluginDescriptorRequest` (`DevDistPluginDescriptorMain.kt:290-378`).
//
// An option the parser does not know fails the run. That is the platform's rule too, and it is what keeps the two
// producers on one spelling: a rule that grows an option reaches both binaries or neither.
func parseRequest(lines []string) (request, error) {
	parsed := request{
		embedsContent:          true,
		separateJar:            map[string]bool{},
		pluginDescriptors:      map[string]string{},
		platformDescriptors:    map[string]string{},
		pluginDescriptorsInJar: map[string]string{},
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
			// Reported by `DevDistPatchedDescriptors` only, so this binary reads it and writes it nowhere.
			parsed.directoryName = value
		case "--main-jar-name":
			parsed.mainJarName = value
		case "--source":
			parsed.source = value
		case "--build-number-file":
			parsed.buildNumberFile = value
		case "--build-date-seconds":
			parsed.buildDateSecond, err = strconv.ParseInt(value, 10, 64)
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
		case "--content-module":
			parsed.contentModules = append(parsed.contentModules, value)
		case "--separate-jar":
			parsed.separateJar[value] = true
		case "--plugin-descriptor":
			err = putDescriptor(parsed.pluginDescriptors, value)
		case "--plugin-descriptor-in-jar":
			err = putDescriptor(parsed.pluginDescriptorsInJar, value)
		case "--marker":
			parsed.markers = append(parsed.markers, value)
		case "--version-suffix":
			parsed.versionSuffix = value
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

// putDescriptor is `putDescriptor` (`DevDistPluginDescriptorMain.kt:380-384`).
func putDescriptor(into map[string]string, value string) error {
	loadPath, file, found := strings.Cut(value, "=")
	if !found || loadPath == "" {
		return fmt.Errorf("a descriptor is '<load path>=<file>', and '%s' is not", value)
	}
	into[loadPath] = file
	return nil
}
