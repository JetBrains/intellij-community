// runtime-layout writes the layout file that the runtime module repository generator reads, the
// `RuntimeModuleRepositoryLayout` of `RuntimeModuleRepositoryMain`: the files of each plugin of a distribution, in the
// order in which `JarPackager` reports them.
//
// Each producer of plugin jars states what its jars merge in a part file. The assembly joins the parts, maps each
// library container to its library in the project model, and derives the order of the entries:
//
//	runtime-layout --part=<file>... [--frontend-only-part=<file>...] --bazel-targets=<file> --output=<file>
//
// A complex plugin states no jar at analysis time. Its part is derived from the resolved plan file and the input
// catalogue of its chain:
//
//	runtime-layout plan-part --plan=<file> --catalogue=<file> [--independent-libraries=<file>] \
//	  --descriptor-module=<module> --plugin-directory=plugins/<directory> --descriptor=<file> --output=<file>
//
// The independent libraries file is `{"version": 1, "libraries": [{"library": <label>, "jars": [<file>...]}...]}`: the
// libraries that the reused content module jars of the plugin merge.
package main

import (
	"fmt"
	"io"
	"os"
	"strings"

	"jetbrains.com/content-module-packer/internal/planfile"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(args []string, output, errors io.Writer) int {
	var err error
	var message string
	if len(args) > 0 && args[0] == "plan-part" {
		message, err = runPlanPart(args[1:])
	} else {
		message, err = runAssemble(args)
	}
	if err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	fmt.Fprintln(output, message)
	return 0
}

func runAssemble(args []string) (string, error) {
	values, err := parseOptions(args, map[string]bool{"--part": true, "--frontend-only-part": true}, "--bazel-targets", "--output")
	if err != nil {
		return "", err
	}
	if len(values["--part"]) == 0 {
		return "", fmt.Errorf("--part is required")
	}
	libraries, err := readLibraryIndex(values["--bazel-targets"][0])
	if err != nil {
		return "", err
	}
	var parts []assembledPart
	for _, option := range []string{"--part", "--frontend-only-part"} {
		for _, file := range values[option] {
			p, err := readPart(file)
			if err != nil {
				return "", err
			}
			assembled := assembledPart{part: p, frontendOnly: option == "--frontend-only-part"}
			if p.Order == pluginOrder {
				if assembled.content, err = readContentOrder(p.Descriptor); err != nil {
					return "", err
				}
			}
			parts = append(parts, assembled)
		}
	}
	result, err := assemble(parts, libraries)
	if err != nil {
		return "", err
	}
	if err := writeJSON(values["--output"][0], result, true); err != nil {
		return "", err
	}
	return fmt.Sprintf("Wrote the runtime module repository layout of %d plugins to %s", len(result.Plugins), values["--output"][0]), nil
}

func runPlanPart(args []string) (string, error) {
	values, err := parseOptions(args, map[string]bool{"--independent-libraries": true}, "--plan", "--catalogue", "--descriptor-module", "--plugin-directory", "--descriptor", "--output")
	if err != nil {
		return "", err
	}
	var independentLibraries []member
	for _, file := range values["--independent-libraries"] {
		var libraries struct {
			Version   int      `json:"version"`
			Libraries []member `json:"libraries"`
		}
		if err := pluginpack.ReadJSON(file, &libraries); err != nil {
			return "", err
		}
		if libraries.Version != partVersion {
			return "", fmt.Errorf("%s has version %d, but %d is expected", file, libraries.Version, partVersion)
		}
		independentLibraries = append(independentLibraries, libraries.Libraries...)
	}
	plan, err := planfile.Read(values["--plan"][0])
	if err != nil {
		return "", err
	}
	var catalogue pluginpack.Catalogue
	if err := pluginpack.ReadJSON(values["--catalogue"][0], &catalogue); err != nil {
		return "", err
	}
	result, err := partFromPlan(plan, &catalogue, independentLibraries, values["--descriptor-module"][0], values["--plugin-directory"][0], values["--descriptor"][0])
	if err != nil {
		return "", fmt.Errorf("%s: %w", values["--plan"][0], err)
	}
	if err := writeJSON(values["--output"][0], result, false); err != nil {
		return "", err
	}
	return fmt.Sprintf("Wrote the layout part of %s with %d jars to %s", result.DescriptorModule, len(result.Jars), values["--output"][0]), nil
}

// parseOptions reads `--key=value` options. A key of repeated may occur any number of times. Every key of required
// occurs exactly once.
func parseOptions(args []string, repeated map[string]bool, required ...string) (map[string][]string, error) {
	known := make(map[string]bool, len(required))
	for _, name := range required {
		known[name] = true
	}
	values := make(map[string][]string)
	for _, arg := range args {
		name, value, hasValue := strings.Cut(arg, "=")
		if !strings.HasPrefix(name, "--") || !hasValue || value == "" {
			return nil, fmt.Errorf("expected an option in the '--key=value' form, but got %q", arg)
		}
		if !known[name] && !repeated[name] {
			return nil, fmt.Errorf("unknown option %q", name)
		}
		if known[name] && len(values[name]) != 0 {
			return nil, fmt.Errorf("%s must be specified at most once", name)
		}
		values[name] = append(values[name], value)
	}
	for _, name := range required {
		if len(values[name]) == 0 {
			return nil, fmt.Errorf("%s is required", name)
		}
	}
	return values, nil
}
