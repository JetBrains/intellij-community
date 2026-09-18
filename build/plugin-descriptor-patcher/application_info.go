// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"fmt"
	"os"
	"strings"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
)

const applicationInfoNamespace = "http://jetbrains.org/intellij/schema/application-info"

// applicationInfoRequest contains the declared inputs of dev_dist_frontend_application_info.
type applicationInfoRequest struct {
	output                 string
	clientApplicationInfo  string
	productApplicationInfo string
	buildNumber            string
	eapOverride            string
	versionSuffixOverride  string
	nightly                bool
	branchName             string
}

func runApplicationInfo(lines []string) int {
	parsed, err := parseApplicationInfoRequest(lines)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
	}
	content, err := resolveApplicationInfo(parsed)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: could not produce the frontend application info: %v\n", err)
		return 1
	}
	if err := writeOutput(parsed.output, content); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 1
	}
	return 0
}

// resolveApplicationInfo applies the client build markers, then follows applyApplicationInfoOverrides in ApplicationInfoPropertiesImpl.kt.
func resolveApplicationInfo(parsed applicationInfoRequest) (string, error) {
	buildNumberContent, err := os.ReadFile(parsed.buildNumber)
	if err != nil {
		return "", err
	}
	buildNumber := strings.TrimSpace(string(buildNumberContent))
	if buildNumber == "" {
		return "", fmt.Errorf("the frontend build number is empty: %s", parsed.buildNumber)
	}
	clientContent, err := os.ReadFile(parsed.clientApplicationInfo)
	if err != nil {
		return "", err
	}
	replaced := strings.ReplaceAll(string(clientContent), "__BUILD_NUMBER__", "JBC-"+buildNumber)
	replaced = strings.ReplaceAll(replaced, "__BUILD__", buildNumber)
	replaced = strings.ReplaceAll(replaced, "__BUILTIN_PLUGINS_URL__", "")
	productContent, err := os.ReadFile(parsed.productApplicationInfo)
	if err != nil {
		return "", err
	}
	product, err := parseApplicationInfo(string(productContent), parsed.productApplicationInfo)
	if err != nil {
		return "", err
	}
	productName, present := product.names.Attribute("fullname")
	if !present {
		productName, present = product.names.Attribute("product")
	}
	if !present {
		return "", fmt.Errorf("the product application info has no product name: %s", parsed.productApplicationInfo)
	}
	client, err := parseApplicationInfo(replaced, parsed.clientApplicationInfo)
	if err != nil {
		return "", err
	}
	client.names.SetAttribute("fullname", productName)
	client.names.RemoveAttribute("edition")
	copyApplicationInfoAttribute(client.names, product.names, "motto")
	for _, name := range []string{"eap", "major", "minor", "micro", "patch", "full", "suffix"} {
		copyApplicationInfoAttribute(client.version, product.version, name)
	}
	copyApplicationInfoAttribute(client.build, product.build, "majorReleaseDate")
	if parsed.eapOverride != "" || parsed.versionSuffixOverride != "" {
		replaceApplicationInfoAttribute(client.version, "eap", parsed.eapOverride, parsed.eapOverride != "")
		replaceApplicationInfoAttribute(client.version, "suffix", parsed.versionSuffixOverride, parsed.versionSuffixOverride != "")
	}
	if parsed.branchName != "" && (parsed.nightly || strings.Count(buildNumber, ".") <= 1) {
		client.build.SetAttribute("branchName", parsed.branchName)
	}
	return descriptorxml.Write(client.root), nil
}

type applicationInfoElements struct {
	root    *descriptorxml.Element
	names   *descriptorxml.Element
	version *descriptorxml.Element
	build   *descriptorxml.Element
}

func parseApplicationInfo(content string, file string) (applicationInfoElements, error) {
	var elements applicationInfoElements
	root, err := descriptorxml.Read(content)
	if err != nil {
		return elements, fmt.Errorf("%s: %w", file, err)
	}
	elements.root = root
	for _, child := range []struct {
		name   string
		target **descriptorxml.Element
	}{
		{"names", &elements.names},
		{"version", &elements.version},
		{"build", &elements.build},
	} {
		count := 0
		for _, element := range root.ChildElements() {
			if element.Name == child.name && element.URI == applicationInfoNamespace {
				*child.target = element
				count++
			}
		}
		if count != 1 {
			return elements, fmt.Errorf("the application info has no unique %s element: %s", child.name, file)
		}
	}
	return elements, nil
}

func copyApplicationInfoAttribute(target *descriptorxml.Element, source *descriptorxml.Element, name string) {
	value, present := source.Attribute(name)
	replaceApplicationInfoAttribute(target, name, value, present)
}

func replaceApplicationInfoAttribute(element *descriptorxml.Element, name string, value string, present bool) {
	if present {
		element.SetAttribute(name, value)
	} else {
		element.RemoveAttribute(name)
	}
}

func parseApplicationInfoRequest(lines []string) (applicationInfoRequest, error) {
	var parsed applicationInfoRequest
	mode, err := selectOperation(lines)
	if err != nil {
		return parsed, err
	}
	if mode != applicationInfoMode {
		return parsed, fmt.Errorf("%s is required", applicationInfoMode)
	}
	for _, line := range lines {
		if line == "" || line == applicationInfoMode {
			continue
		}
		option, value, _ := strings.Cut(line, "=")
		switch option {
		case "--out":
			parsed.output = value
		case "--client-application-info":
			parsed.clientApplicationInfo = value
		case "--product-application-info":
			parsed.productApplicationInfo = value
		case "--build-number":
			parsed.buildNumber = value
		case "--eap-override":
			parsed.eapOverride = value
		case "--version-suffix-override":
			parsed.versionSuffixOverride = value
		case "--nightly":
			if value != "" {
				return parsed, fmt.Errorf("--nightly is a flag")
			}
			parsed.nightly = true
		case "--branch-name":
			parsed.branchName = value
		default:
			return parsed, fmt.Errorf("unknown frontend application info option '%s'", option)
		}
	}
	for _, required := range []struct{ option, value string }{
		{"--out", parsed.output},
		{"--client-application-info", parsed.clientApplicationInfo},
		{"--product-application-info", parsed.productApplicationInfo},
		{"--build-number", parsed.buildNumber},
	} {
		if required.value == "" {
			return parsed, fmt.Errorf("%s is required", required.option)
		}
	}
	return parsed, nil
}
