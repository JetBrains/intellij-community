package main

import (
	"encoding/json"
	"fmt"
	"io"
	"maps"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"

	"jetbrains.com/content-module-packer/internal/planfile"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

// The packer has two modes. The recipe mode packs the recipe and the catalogue the Kotlin preparer wrote.
// The projection mode derives the recipe from the plan file for a chain without a Kotlin preparation. It also
// writes the asset rows and the plugin classpath record, which the preparer writes for the other chains.
var recipeOptions = []string{"--recipe", "--catalogue", "--output-dir", "--inventory"}
var projectionOptions = []string{"--projection", "--input-catalogue", "--classpath-descriptor", "--plugin-directory", "--execution-version",
	"--output-dir", "--inventory", "--assets", "--classpath"}

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(arguments []string, output, errors io.Writer) int {
	options := make(map[string]string)
	for _, argument := range arguments {
		name, value, present := strings.Cut(argument, "=")
		if !slices.Contains(recipeOptions, name) && !slices.Contains(projectionOptions, name) {
			fmt.Fprintf(errors, "ERROR: unknown option %q\n", name)
			return 2
		}
		if !present || value == "" || options[name] != "" {
			fmt.Fprintf(errors, "ERROR: expected one nonempty %s=value option\n", name)
			return 2
		}
		options[name] = value
	}
	projection := options["--projection"] != ""
	if projection && options["--recipe"] != "" {
		fmt.Fprintln(errors, "ERROR: --projection and --recipe are exclusive")
		return 2
	}
	allowed, required, mode := recipeOptions, recipeOptions, "recipe"
	if projection {
		allowed, required, mode = projectionOptions, projectionOptions, "projection"
	}
	for _, name := range slices.Sorted(maps.Keys(options)) {
		if !slices.Contains(allowed, name) {
			fmt.Fprintf(errors, "ERROR: %s is not an option of the %s mode\n", name, mode)
			return 2
		}
	}
	for _, name := range required {
		if options[name] == "" {
			fmt.Fprintf(errors, "ERROR: %s is required\n", name)
			return 2
		}
	}
	if projection {
		return runProjection(options, output, errors)
	}
	var recipe pluginpack.Recipe
	var catalogue pluginpack.Catalogue
	for _, document := range []struct {
		option string
		target any
	}{{"--recipe", &recipe}, {"--catalogue", &catalogue}} {
		if err := pluginpack.ReadJSON(options[document.option], document.target); err != nil {
			fmt.Fprintf(errors, "ERROR: %v\n", err)
			return 1
		}
	}
	execution, err := pluginpack.Plan(recipe, catalogue)
	if err == nil {
		err = execution.Write(options["--output-dir"], options["--inventory"])
	}
	if err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	fmt.Fprintf(output, "Packed the remainder for %s\n", recipe.Plugin)
	return 0
}

// runProjection derives the recipe from the plan file, packs it against the Starlark input catalogue, then writes
// the asset rows and the plugin classpath record. A refusal happens before any write. Only an I/O failure after
// Execution.Write can leave the plugin directory behind, and Bazel discards the outputs of a failed action.
func runProjection(options map[string]string, output, errors io.Writer) int {
	version, err := strconv.Atoi(options["--execution-version"])
	if err != nil || version < pluginpack.Version || version > pluginpack.ScopedVersion {
		fmt.Fprintln(errors, "ERROR: --execution-version must be 1, 2, or 3")
		return 2
	}
	file, err := planfile.Read(options["--projection"])
	var catalogue pluginpack.Catalogue
	if err == nil {
		err = pluginpack.ReadJSON(options["--input-catalogue"], &catalogue)
	}
	var descriptor []byte
	if err == nil {
		descriptor, err = os.ReadFile(options["--classpath-descriptor"])
	}
	var derivation *planfile.Derivation
	if err == nil {
		derivation, err = planfile.Derive(file, catalogue, options["--plugin-directory"], descriptor, version)
	}
	var execution *pluginpack.Execution
	if err == nil {
		execution, err = pluginpack.Plan(derivation.Recipe, derivation.Catalogue)
	}
	var assets []byte
	if err == nil {
		assets, err = json.Marshal(derivation.Assets)
	}
	if err == nil {
		err = execution.Write(options["--output-dir"], options["--inventory"])
	}
	if err == nil {
		err = writeOutput(options["--assets"], assets)
	}
	if err == nil {
		err = writeOutput(options["--classpath"], derivation.ClassPath)
	}
	if err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	fmt.Fprintf(output, "Packed the remainder for %s from its plan file\n", file.Plugin)
	return 0
}

func writeOutput(file string, data []byte) error {
	if err := os.MkdirAll(filepath.Dir(file), 0o755); err != nil {
		return err
	}
	return os.WriteFile(file, data, 0o644)
}
