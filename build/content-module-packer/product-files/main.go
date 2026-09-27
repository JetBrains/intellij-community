// product-files renders the launch files of one product for one platform: `build.txt`, `bin/idea.properties`, the
// vmoptions file and `bin/product-info.json`.
//
// The input is the launch model that the dev distribution plan generator writes for the product
// (`ProductLaunchModel` in community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productInfo). The
// files must be the files that the production writers of the build scripts write, byte for byte.
// `DevDistProductLaunchModelTest` checks that for every split product and every host platform.
package main

import (
	"fmt"
	"io"
	"os"
	"strings"
)

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

type options struct {
	model             string
	platform          string
	openedPackages    string
	ideaProperties    string
	buildTxtOut       string
	ideaPropertiesOut string
	vmOptionsOut      string
	productInfoOut    string
}

func run(args []string, output, errors io.Writer) int {
	opts, err := parseOptions(args)
	if err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 2
	}
	if err := renderToFiles(opts); err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	fmt.Fprintf(output, "Rendered the launch files of %s for %s\n", opts.model, opts.platform)
	return 0
}

func parseOptions(args []string) (options, error) {
	var opts options
	singles := map[string]*string{
		"--model":               &opts.model,
		"--platform":            &opts.platform,
		"--opened-packages":     &opts.openedPackages,
		"--idea-properties":     &opts.ideaProperties,
		"--build-txt-out":       &opts.buildTxtOut,
		"--idea-properties-out": &opts.ideaPropertiesOut,
		"--vmoptions-out":       &opts.vmOptionsOut,
		"--product-info-out":    &opts.productInfoOut,
	}
	seen := make(map[string]bool)
	for _, arg := range args {
		name, value, hasValue := strings.Cut(arg, "=")
		destination, known := singles[name]
		if !known || !hasValue {
			return opts, fmt.Errorf("expected one of the options in the '--key=value' form, but got %q", arg)
		}
		if seen[name] {
			return opts, fmt.Errorf("%s must be specified at most once", name)
		}
		seen[name] = true
		*destination = value
	}
	for name, destination := range singles {
		if *destination == "" {
			return opts, fmt.Errorf("%s is required", name)
		}
	}
	return opts, nil
}

func renderToFiles(opts options) error {
	modelText, err := os.ReadFile(opts.model)
	if err != nil {
		return err
	}
	model, err := parseLaunchModel(modelText)
	if err != nil {
		return fmt.Errorf("%s: %w", opts.model, err)
	}
	target, err := parsePlatform(opts.platform)
	if err != nil {
		return err
	}
	openedPackages, err := os.ReadFile(opts.openedPackages)
	if err != nil {
		return err
	}
	// The base file the model names by `languageServerBase`. The caller passes it, so an action reads only its own.
	ideaProperties, err := os.ReadFile(opts.ideaProperties)
	if err != nil {
		return err
	}
	files, err := renderLaunchFiles(model, target, string(openedPackages), string(ideaProperties))
	if err != nil {
		return fmt.Errorf("%s: %w", opts.model, err)
	}
	for _, file := range []struct {
		path    string
		content string
	}{
		{opts.buildTxtOut, files.buildTxt},
		{opts.ideaPropertiesOut, files.ideaProperties},
		{opts.vmOptionsOut, files.vmOptions},
		{opts.productInfoOut, files.productInfo},
	} {
		if err := os.WriteFile(file.path, []byte(file.content), 0o644); err != nil {
			return err
		}
	}
	return nil
}
