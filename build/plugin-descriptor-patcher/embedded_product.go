// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"fmt"
	"os"
	"strings"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-patcher/internal/structural"
)

// embeddedProductRequest contains the declared inputs of dev_dist_embedded_product_descriptor.
type embeddedProductRequest struct {
	output           string
	source           string
	descriptors      map[string]string
	descriptorsInJar map[string][]string
	modules          []string
	separateJar      map[string]bool
}

func runEmbeddedProduct(lines []string) int {
	parsed, err := parseEmbeddedProductRequest(lines)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
	}
	content, err := resolveEmbeddedProduct(parsed)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: could not resolve the embedded product descriptor (%s): %v\n", parsed.source, err)
		return 1
	}
	if err := writeOutput(parsed.output, content); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 1
	}
	return 0
}

func resolveEmbeddedProduct(parsed embeddedProductRequest) (string, error) {
	files, err := readSeed(parsed.descriptors)
	if err != nil {
		return "", err
	}
	cache := structural.NewCache(nil)
	if err := seedFromJars(cache, parsed.descriptorsInJar); err != nil {
		return "", err
	}
	// Kotlin merges the jar seed over the file seed when a direct request declares both.
	for _, loadPath := range files.LoadPaths() {
		data, _ := files.Get(loadPath)
		cache.PutIfAbsent(loadPath, data)
	}
	resolver := structural.NewResolver([]structural.Scope{{Modules: parsed.modules, Cache: cache}})
	source, err := os.ReadFile(parsed.source)
	if err != nil {
		return "", err
	}
	element, err := descriptorxml.Read(string(source))
	if err != nil {
		return "", err
	}
	if err := structural.ResolveIncludes(element, resolver); err != nil {
		return "", err
	}
	if err := structural.EmbedContentModules(element, structural.ContentRequest{
		MainModule:  parsed.source,
		SeparateJar: parsed.separateJar,
		Embeds:      true,
	}, cache, resolver); err != nil {
		return "", err
	}
	return descriptorxml.Write(element), nil
}

func parseEmbeddedProductRequest(lines []string) (embeddedProductRequest, error) {
	parsed := embeddedProductRequest{
		descriptors:      map[string]string{},
		descriptorsInJar: map[string][]string{},
		separateJar:      map[string]bool{},
	}
	mode, err := selectOperation(lines)
	if err != nil {
		return parsed, err
	}
	if mode != embeddedProductMode {
		return parsed, fmt.Errorf("%s is required", embeddedProductMode)
	}
	for _, line := range lines {
		if line == "" || line == embeddedProductMode {
			continue
		}
		option, value, _ := strings.Cut(line, "=")
		switch option {
		case "--out":
			parsed.output = value
		case "--source":
			parsed.source = value
		case "--descriptor":
			err = putDescriptor(parsed.descriptors, value)
		case "--descriptor-in-jar":
			err = appendDescriptorJar(parsed.descriptorsInJar, value)
		case "--module":
			parsed.modules = append(parsed.modules, value)
		case "--separate-jar":
			parsed.separateJar[value] = true
		default:
			err = fmt.Errorf("unknown embedded product descriptor option '%s'", option)
		}
		if err != nil {
			return parsed, err
		}
	}
	if parsed.output == "" {
		return parsed, fmt.Errorf("--out is required")
	}
	if parsed.source == "" {
		return parsed, fmt.Errorf("--source is required")
	}
	return parsed, nil
}
