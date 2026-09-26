// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"encoding/binary"
	"fmt"
	"os"
	"strings"

	"jetbrains.com/plugin-descriptor-writer/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-writer/internal/structural"
)

// productDescriptorRequest contains the declared inputs of the product descriptor, the `META-INF` descriptor of the
// application-info module.
//
// The source is the Product DSL content with the module sets and the deprecated includes inlined. It is the text that
// `processAndGetProductPluginContentModules` (`productModuleLayout.kt`) loads. The generator writes it, and the plan
// states the refusals of the product's content filter and the scrambled content modules. So the action loads no project
// model.
type productDescriptorRequest struct {
	embeddedProductRequest
	mainModule string
	refused    []string
	scrambled  map[string]bool
	// pluginClassPathPrefix, when set, receives the prefix of `plugins/plugin-classpath.txt`.
	pluginClassPathPrefix string
	// classpathDescriptor, when set, receives the descriptor of that prefix alone, with no header.
	classpathDescriptor string
}

func runProductDescriptor(lines []string) int {
	parsed, err := parseProductDescriptorRequest(lines)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		return 2
	}
	content, err := resolveProductDescriptor(parsed)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: could not resolve the product descriptor (module=%s): %v\n", parsed.mainModule, err)
		return 1
	}
	outputs := map[string]string{parsed.output: content.text}
	if parsed.pluginClassPathPrefix != "" || parsed.classpathDescriptor != "" {
		descriptor, err := classpathDescriptor(content, parsed.mainModule)
		if err != nil {
			fmt.Fprintf(os.Stderr, "ERROR: could not write the plugin classpath prefix (module=%s): %v\n", parsed.mainModule, err)
			return 1
		}
		if parsed.pluginClassPathPrefix != "" {
			outputs[parsed.pluginClassPathPrefix] = pluginClassPathPrefix(descriptor)
		}
		if parsed.classpathDescriptor != "" {
			outputs[parsed.classpathDescriptor] = descriptor
		}
	}
	for file, text := range outputs {
		if err := writeOutput(file, text); err != nil {
			fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
			return 1
		}
	}
	return 0
}

// pluginClassPathFormatVersion is `PLUGIN_CLASSPATH_FORMAT_VERSION` of `classpath.kt`, the first byte of
// `plugins/plugin-classpath.txt`.
const pluginClassPathFormatVersion = 3

// pluginClassPathPrefix is `writePluginClassPathPrefix` (`classpath.kt`): the format version, the size of the product
// descriptor as a big-endian 32-bit integer, and the product descriptor.
func pluginClassPathPrefix(descriptor string) string {
	header := make([]byte, 5)
	header[0] = pluginClassPathFormatVersion
	binary.BigEndian.PutUint32(header[1:], uint32(len(descriptor)))
	return string(header) + descriptor
}

// classpathDescriptor is `createCachedProductDescriptor`. It loads the product descriptor, so every embedded body
// becomes text, and it embeds a descriptor into every `<module/>` that is still empty. So a scrambled module gets its
// descriptor here, and no filter runs. The runtime module repository reads the same descriptor as the core plugin.
func classpathDescriptor(content productContent, mainModule string) (string, error) {
	element, err := descriptorxml.Read(content.text)
	if err != nil {
		return "", err
	}
	request := structural.ContentRequest{MainModule: mainModule, Embeds: true}
	if err := structural.EmbedContentModules(element, request, content.cache, content.resolver); err != nil {
		return "", err
	}
	return descriptorxml.Write(element), nil
}

// resolveProductDescriptor is the part of `processAndGetProductPluginContentModules` (`productModuleLayout.kt`) that
// follows the load of the source.
//
// The includes are resolved first. Then the content filter removes the refused modules, and every other content module
// receives its descriptor, except a scrambled one. The product descriptor takes no `separate-jar` attribute, because
// `processProductModule` embeds with no descriptor modifier.
func resolveProductDescriptor(parsed productDescriptorRequest) (productContent, error) {
	return resolveProductContent(parsed.embeddedProductRequest, structural.ContentRequest{
		MainModule: parsed.mainModule,
		Refused:    parsed.refused,
		Scrambled:  parsed.scrambled,
		Embeds:     true,
	})
}

func parseProductDescriptorRequest(lines []string) (productDescriptorRequest, error) {
	parsed := productDescriptorRequest{embeddedProductRequest: newProductContentRequest(), scrambled: map[string]bool{}}
	if err := requireMode(lines, productDescriptorMode); err != nil {
		return parsed, err
	}
	for _, line := range lines {
		if line == "" || line == productDescriptorMode {
			continue
		}
		option, value, _ := strings.Cut(line, "=")
		handled, err := parseProductContentOption(&parsed.embeddedProductRequest, option, value)
		if !handled {
			switch option {
			case "--main-module":
				parsed.mainModule = value
			case "--refused-content-module":
				parsed.refused = append(parsed.refused, value)
			case "--scrambled-content-module":
				parsed.scrambled[value] = true
			case "--plugin-classpath-prefix":
				parsed.pluginClassPathPrefix = value
			case "--classpath-descriptor":
				parsed.classpathDescriptor = value
			default:
				err = fmt.Errorf("unknown product descriptor option '%s'", option)
			}
		}
		if err != nil {
			return parsed, err
		}
	}
	if err := checkProductContentRequest(parsed.embeddedProductRequest); err != nil {
		return parsed, err
	}
	if parsed.mainModule == "" {
		return parsed, fmt.Errorf("--main-module is required")
	}
	return parsed, nil
}
