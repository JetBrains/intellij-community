// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Command plugin-descriptor-patcher writes the `META-INF/plugin.xml` a plugin's main jar receives.
//
// It is the Go counterpart of `applyPluginDescriptorPatch`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt:89`).
// `build/decisions/0006-content-module-in-jar-out-composer-places-it.md` puts the executors in Go and leaves the JVM in
// the generator, and a descriptor feeds every plugin main jar, so a JVM action for it sits on the build's critical path.
//
// ### What this covers, and what it refuses
//
// The patch has seven stages. This binary runs the first three:
//
//	source → rawTextPatcher → reserialized → stamps
//
// `rawTextPatcher` is a per-layout lambda, so it is not data and never reaches this binary: a plugin whose layout
// states one is not a candidate. `reserialized` is the round trip of `internal/descriptorxml`, and `stamps` is
// `internal/stamps`.
//
// The last three stages are **not ported**. `includes` resolves `xi:include`, `contentModules` embeds a content
// module's own descriptor, and `textPatcher` is the second per-layout lambda. Both structural stages need the
// descriptor closure of the plugin, which another step owns. So this binary **refuses** a descriptor that would reach
// one of them, rather than writing a text that silently lost an include or an embedded body. The refusal is the point:
// a wrong descriptor fails at class-load time inside the IDE, where nothing here can see it.
//
// No Bazel rule runs this binary yet. `dev_dist_plugin_descriptor` still runs the JVM tool, which stays the reference
// (`community/platform/build-scripts/bazel-rules/dev_dist_plugin_descriptor.bzl`).
package main

import (
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-patcher/internal/stamps"
)

// xIncludeNamespace is the XInclude namespace URI. A descriptor that states the prefix without declaring it reaches the
// reader with the prefix and no URI, which is why the refusal below tests both.
const xIncludeNamespace = "http://www.w3.org/2001/XInclude"

func main() {
	if code := run(os.Args[1:]); code != 0 {
		os.Exit(code)
	}
}

type options struct {
	flagFile string
	source   string
	output   string
	request  stamps.Request
}

func run(arguments []string) int {
	opts, err := parseOptions(arguments)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
	}

	source, err := os.ReadFile(opts.source)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 1
	}
	element, err := descriptorxml.Read(string(source))
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %s: %v\n", opts.source, err)
		return 1
	}
	if reason := unportedStage(element); reason != "" {
		fmt.Fprintf(os.Stderr, "ERROR: %s needs a stage this binary does not run: %s\n", opts.source, reason)
		return 1
	}

	stamps.Apply(element, opts.request)
	if err := os.MkdirAll(filepath.Dir(opts.output), 0o755); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 1
	}
	if err := os.WriteFile(opts.output, []byte(descriptorxml.Write(element)), 0o644); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 1
	}
	return 0
}

// unportedStage names the stage a descriptor would reach that this binary does not run, or the empty string.
//
// It walks the tree. A text search would read a `<module` inside a CDATA section as a declared content module, which
// `build/internal/content/descriptor.go` measured at 152 false survivors over one product.
func unportedStage(root *descriptorxml.Element) string {
	if name := findXInclude(root); name != "" {
		return "the includes stage, because it states " + name
	}
	if content := root.Child("content"); content != nil {
		for _, child := range content.Children {
			if child.Kind == descriptorxml.KindElement && child.Element.Name == "module" {
				name, _ := child.Element.Attribute("name")
				return "the contentModules stage, because <content> declares the module " + name
			}
		}
	}
	return ""
}

func findXInclude(element *descriptorxml.Element) string {
	for _, child := range element.Children {
		if child.Kind != descriptorxml.KindElement {
			continue
		}
		if child.Element.Name == "include" && (child.Element.URI == xIncludeNamespace || child.Element.Prefix == "xi") {
			return "an <" + child.Element.Prefix + ":include> element"
		}
		if found := findXInclude(child.Element); found != "" {
			return found
		}
	}
	return ""
}

func parseOptions(arguments []string) (options, error) {
	var opts options
	flags := flag.NewFlagSet(filepath.Base(os.Args[0]), flag.ContinueOnError)
	flags.StringVar(&opts.flagFile, "flagfile", "",
		"read the options from this file instead, one `--option=value` a line")
	flags.StringVar(&opts.source, "source", "", "the plugin's own `META-INF/plugin.xml`")
	flags.StringVar(&opts.output, "output", "", "where to write the patched descriptor")
	flags.StringVar(&opts.request.Version, "version", "", "the text of `<version>`")
	flags.StringVar(&opts.request.SinceBuild, "since-build", "", "the `since-build` of `<idea-version>`")
	flags.StringVar(&opts.request.UntilBuild, "until-build", "", "the `until-build` of `<idea-version>`")
	flags.StringVar(&opts.request.ReleaseDate, "release-date", "", "the `release-date` of `<product-descriptor>`")
	flags.StringVar(&opts.request.ReleaseVersion, "release-version", "", "the `release-version` of `<product-descriptor>`")
	flags.BoolVar(&opts.request.ToPublish, "to-publish", false, "the plugin is published, so `<product-descriptor>` survives")
	flags.BoolVar(&opts.request.RetainProductDescriptorForBundledPlugin, "retain-product-descriptor", false,
		"a bundled plugin keeps its `<product-descriptor>`")
	flags.BoolVar(&opts.request.IsEap, "eap", false, "the product is an EAP, so `<product-descriptor>` gains `eap=\"true\"`")
	flags.Usage = func() {
		fmt.Fprintf(os.Stderr, "usage: %s --source=<path> --output=<path> [stamps]\n", flags.Name())
		fmt.Fprintf(os.Stderr, "   or: %s --flagfile=<path>\n", flags.Name())
		flags.PrintDefaults()
	}
	if err := flags.Parse(arguments); err != nil {
		return opts, err
	}
	if opts.flagFile != "" {
		lines, err := readFlagFile(opts.flagFile)
		if err != nil {
			return opts, err
		}
		if err := flags.Parse(lines); err != nil {
			return opts, err
		}
	}
	if opts.source == "" || opts.output == "" {
		return opts, fmt.Errorf("both --source and --output are required")
	}
	return opts, nil
}

// readFlagFile reads one `--option=value` a line, the shape a Bazel `--flagfile=%s` parameter file takes. A blank line
// and a `#` line are skipped, so a generated file can carry a header.
func readFlagFile(path string) ([]string, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var lines []string
	for number, line := range strings.Split(string(content), "\n") {
		line = strings.TrimSpace(strings.TrimRight(line, "\r"))
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if !strings.HasPrefix(line, "--") {
			return nil, fmt.Errorf("%s line %s: expected `--option=value`, got %q", path, strconv.Itoa(number+1), line)
		}
		lines = append(lines, line)
	}
	return lines, nil
}
