package main

import (
	"fmt"
	"io"
	"os"
	"runtime"
	"strings"

	"jetbrains.com/content-module-packer/internal/span"
)

type options struct {
	manifest          string
	kind              string
	platformPrefix    string
	os                string
	arch              string
	jarsFile          string
	filesFile         string
	traceFile         string
	metadataCatalogue string
	pluginComponent   string
	pluginClasspath   string
	// platformNeutral leaves the manifest's os and arch empty: the component fits every target platform.
	platformNeutral bool
}

func main() {
	os.Exit(run(os.Args[1:], os.Stdout, os.Stderr))
}

func run(args []string, output, errors io.Writer) (exitCode int) {
	if len(args) > 0 && args[0] == "local-home" {
		return runLocalHome(args[1:], output, errors)
	}
	opts, err := parseOptions(args)
	if err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 2
	}
	if opts.pluginComponent != "" {
		return runPluginComponent(opts, output, errors)
	}
	jobName := "collect packed jars"
	if opts.filesFile != "" {
		jobName = "collect files"
	}
	var tracer *span.Tracer
	if opts.traceFile != "" {
		tracer = span.NewTracer(jobName)
	}
	root := tracer.Start(jobName, nil)
	root.SetString("kind", opts.kind)
	defer func() {
		root.End()
		if err := tracer.WriteFile(opts.traceFile); err != nil {
			fmt.Fprintf(errors, "ERROR: writing the span file: %v\n", err)
			exitCode = 1
		}
	}()
	files, err := collect(opts, tracer, root)
	if err == nil {
		err = writeManifest(opts, files, tracer, root)
	}
	if err != nil {
		root.Fail(err)
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	content := "packed jars"
	if opts.filesFile != "" {
		content = "files"
	}
	fmt.Fprintf(output, "Dev distribution component '%s' named %d %s in %s\n", opts.kind, len(files), content, opts.manifest)
	return 0
}

func parseOptions(args []string) (opts options, err error) {
	var platformNeutral string
	singles := map[string]*string{
		"--platform-neutral":      &platformNeutral,
		"--component-manifest":    &opts.manifest,
		"--kind":                  &opts.kind,
		"--platform-prefix":       &opts.platformPrefix,
		"--os":                    &opts.os,
		"--arch":                  &opts.arch,
		"--jars-file":             &opts.jarsFile,
		"--files-file":            &opts.filesFile,
		"--trace-file":            &opts.traceFile,
		"--metadata-catalogue":    &opts.metadataCatalogue,
		"--plugin-component":      &opts.pluginComponent,
		"--plugin-classpath-part": &opts.pluginClasspath,
	}
	seen := make(map[string]bool)
	for _, arg := range args {
		if !strings.HasPrefix(arg, "--") {
			return opts, fmt.Errorf("expected an option in the '--key=value' form, but got %q", arg)
		}
		name, value, hasValue := strings.Cut(arg, "=")
		if !hasValue {
			value = "true"
		}
		destination, known := singles[name]
		if !known {
			return opts, fmt.Errorf("unknown option: %s", name)
		}
		if seen[name] {
			return opts, fmt.Errorf("%s must be specified at most once", name)
		}
		seen[name] = true
		*destination = value
	}
	for _, name := range []string{"--component-manifest", "--kind", "--platform-prefix"} {
		if *singles[name] == "" {
			return opts, fmt.Errorf("%s is required", name)
		}
	}
	modes := 0
	for _, file := range []string{opts.jarsFile, opts.filesFile, opts.pluginComponent} {
		if file != "" {
			modes++
		}
	}
	if modes != 1 {
		return opts, fmt.Errorf("exactly one of --jars-file, --files-file and --plugin-component is required")
	}
	if (opts.pluginComponent != "") != (opts.pluginClasspath != "") {
		return opts, fmt.Errorf("--plugin-component and --plugin-classpath-part are required together")
	}
	if opts.pluginComponent != "" && opts.metadataCatalogue != "" {
		return opts, fmt.Errorf("--plugin-component cannot use --metadata-catalogue")
	}
	switch platformNeutral {
	case "", "false":
	case "true":
		opts.platformNeutral = true
	default:
		return opts, fmt.Errorf("--platform-neutral accepts only true or false, but got %q", platformNeutral)
	}
	if opts.platformNeutral {
		if opts.os != "" || opts.arch != "" {
			return opts, fmt.Errorf("--platform-neutral cannot be combined with --os or --arch")
		}
		return opts, nil
	}
	if opts.os == "" {
		opts.os = runtime.GOOS
		if opts.os == "darwin" {
			opts.os = "mac"
		}
	}
	switch strings.ToLower(opts.os) {
	case "windows", "win":
		opts.os = "windows"
	case "macos", "mac":
		opts.os = "mac"
	case "linux":
		opts.os = "linux"
	default:
		return opts, fmt.Errorf("unknown --os value %q, expected one of windows, mac, linux", opts.os)
	}
	if opts.arch == "" {
		opts.arch = runtime.GOARCH
	}
	switch strings.ToLower(opts.arch) {
	case "x64", "x86_64", "amd64":
		opts.arch = "x64"
	case "aarch64", "arm64":
		opts.arch = "aarch64"
	default:
		return opts, fmt.Errorf("unknown --arch value %q, expected one of x64, aarch64", opts.arch)
	}
	return opts, nil
}
