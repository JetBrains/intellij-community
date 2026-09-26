package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"strings"
)

const partVersion = 1

// The two orders of a part. A plugin states its jars in the order of its plan file, which is the order in which
// `JarPackager` creates them, and marks the reused content module jars, whose place the assembly derives from the
// `<content>` order of the plugin descriptor. The platform states its jars in the order of its layout.
const (
	pluginOrder = "plugin"
	layoutOrder = "layout"
)

// part is the layout of one plugin as its producer states it: the jars it packs, and what each jar merges.
type part struct {
	Version          int    `json:"version"`
	DescriptorModule string `json:"descriptorModule"`
	// Directory is the directory of the plugin relative to the distribution root, such as `plugins/dev`. It is empty
	// for the platform, whose jars are under the distribution root itself.
	Directory string `json:"directory"`
	Order     string `json:"order"`
	// Descriptor is the plugin descriptor whose `<content>` order a plugin part follows.
	Descriptor string    `json:"descriptor,omitempty"`
	Jars       []partJar `json:"jars"`
}

type partJar struct {
	// Destination is relative to Directory and starts with `lib/`.
	Destination string   `json:"destination"`
	Members     []member `json:"members"`
	// Reused marks the jar of a `content_module_jar` target that a plugin reuses. Its place is not in the part order.
	Reused bool `json:"reused,omitempty"`
}

// member is one module or one library that a jar merges, in merge order.
type member struct {
	Module string `json:"module,omitempty"`
	// Library is the label of a library container. The assembly maps it to a JPS library through bazel-targets.json.
	Library string `json:"library,omitempty"`
	// Jars are the files of the library in execution-root form. The assembly writes one entry for each file, as
	// `JarPackager` reports one for each, and maps a library by them when its label is unknown.
	Jars []string `json:"jars,omitempty"`
}

func readPart(file string) (*part, error) {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	var decoded part
	if err := decoder.Decode(&decoded); err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	if err := decoded.validate(); err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	return &decoded, nil
}

func (p *part) validate() error {
	if p.Version != partVersion {
		return fmt.Errorf("the part has version %d, but %d is expected", p.Version, partVersion)
	}
	if p.DescriptorModule == "" {
		return fmt.Errorf("the part names no descriptor module")
	}
	switch p.Order {
	case pluginOrder:
		if p.Descriptor == "" {
			return fmt.Errorf("the plugin part of '%s' names no descriptor", p.DescriptorModule)
		}
	case layoutOrder:
	default:
		return fmt.Errorf("the part of '%s' has the order %q, but %q or %q is expected", p.DescriptorModule, p.Order, pluginOrder, layoutOrder)
	}
	if strings.HasPrefix(p.Directory, "/") || strings.HasSuffix(p.Directory, "/") {
		return fmt.Errorf("the part of '%s' has the directory %q, which is not relative", p.DescriptorModule, p.Directory)
	}
	destinations := make(map[string]bool)
	for _, jar := range p.Jars {
		if !strings.HasPrefix(jar.Destination, "lib/") || !strings.HasSuffix(jar.Destination, ".jar") {
			return fmt.Errorf("the part of '%s' has the destination %q, which is not a jar under lib/", p.DescriptorModule, jar.Destination)
		}
		if destinations[jar.Destination] {
			return fmt.Errorf("the part of '%s' states %s twice", p.DescriptorModule, jar.Destination)
		}
		destinations[jar.Destination] = true
		if len(jar.Members) == 0 {
			return fmt.Errorf("%s of '%s' merges nothing", jar.Destination, p.DescriptorModule)
		}
		if jar.Reused && p.Order != pluginOrder {
			return fmt.Errorf("%s of '%s' is reused, but only a plugin part reuses a jar", jar.Destination, p.DescriptorModule)
		}
		for _, m := range jar.Members {
			if (m.Module == "") == (m.Library == "") {
				return fmt.Errorf("a member of %s of '%s' must name one module or one library", jar.Destination, p.DescriptorModule)
			}
			if m.Module != "" && len(m.Jars) != 0 {
				return fmt.Errorf("the module '%s' of %s of '%s' states library jars", m.Module, jar.Destination, p.DescriptorModule)
			}
		}
	}
	return nil
}

func writeJSON(file string, value any, indent bool) error {
	var output bytes.Buffer
	encoder := json.NewEncoder(&output)
	encoder.SetEscapeHTML(false)
	if indent {
		encoder.SetIndent("", "  ")
	}
	if err := encoder.Encode(value); err != nil {
		return err
	}
	return os.WriteFile(file, output.Bytes(), 0o644)
}
