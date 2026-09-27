// The dev-dist-composer command assembles the components of a dev distribution into one tree, or into launch metadata
// only. Then it writes the files that start the IDE.
package main

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"jetbrains.com/content-module-packer/internal/span"
)

const jobName = "compose dev distribution"

func main() {
	os.Exit(run(os.Args[1:], os.Stderr))
}

// run returns 1 for every failure, as the JVM does for an uncaught exception.
func run(args []string, errors io.Writer) (exitCode int) {
	options, err := parseOptions(args)
	var traceFile string
	if err == nil {
		traceFile, err = options.optionalPath("--trace-file")
	}
	if err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	var tracer *span.Tracer
	if traceFile != "" {
		tracer = span.NewTracer(jobName)
	}
	root := tracer.Start(jobName, nil)
	defer func() {
		root.End()
		if err := tracer.WriteFile(traceFile); err != nil {
			fmt.Fprintf(errors, "ERROR: writing the span file: %v\n", err)
			exitCode = 1
		}
	}()
	if err := composeDevDistribution(options, tracer, root); err != nil {
		root.Fail(err)
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	return 0
}

// commandLineOptions is the Kotlin CommandLineOptions. It keeps every value of an option in the order of the
// command line.
type commandLineOptions struct {
	names  []string
	values map[string][]string
	used   map[string]bool
}

func parseOptions(args []string) (*commandLineOptions, error) {
	options := &commandLineOptions{values: make(map[string][]string), used: make(map[string]bool)}
	for _, arg := range args {
		if !strings.HasPrefix(arg, "--") {
			return nil, fmt.Errorf("Expected an option in the '--key=value' form, but got '%s'", arg)
		}
		name, value, hasValue := strings.Cut(arg, "=")
		if !hasValue {
			value = "true"
		}
		if _, exists := options.values[name]; !exists {
			options.names = append(options.names, name)
		}
		options.values[name] = append(options.values[name], value)
	}
	return options, nil
}

// optionalPath returns the absolute path of an option, or an empty string when the option is absent or empty.
func (options *commandLineOptions) optionalPath(name string) (string, error) {
	options.used[name] = true
	values, exists := options.values[name]
	if !exists {
		return "", nil
	}
	if len(values) != 1 {
		return "", fmt.Errorf("%s must be specified at most once, but got %d values: %s", name, len(values), kotlinList(values))
	}
	if values[0] == "" {
		return "", nil
	}
	return absolutePath(values[0])
}

func (options *commandLineOptions) requiredPath(name string) (string, error) {
	value, err := options.optionalPath(name)
	if err == nil && value == "" {
		err = fmt.Errorf("%s is required (no value and no fallback available)", name)
	}
	return value, err
}

func (options *commandLineOptions) checkNoUnknownOptions() error {
	var unknown []string
	for _, name := range options.names {
		if !options.used[name] {
			unknown = append(unknown, name)
		}
	}
	if len(unknown) != 0 {
		return fmt.Errorf("Unknown options: %s", strings.Join(sortedStrings(unknown), ", "))
	}
	return nil
}

// composeDevDistribution checks the composition spec first, then the output options, the unknown options, the source
// bindings and each component manifest. It removes the output directory only after all of these checks pass.
func composeDevDistribution(options *commandLineOptions, tracer *span.Tracer, root *span.Span) error {
	specFile, err := options.requiredPath("--composition-spec")
	if err != nil {
		return err
	}
	spec, err := readCompositionSpec(specFile)
	if err != nil {
		return err
	}
	var outputDir, ideConfig, fingerprintFile string
	for _, option := range []struct {
		name        string
		destination *string
	}{{"--output-dir", &outputDir}, {"--ide-config", &ideConfig}, {"--fingerprint", &fingerprintFile}} {
		if *option.destination, err = options.requiredPath(option.name); err != nil {
			return err
		}
	}
	if err := options.checkNoUnknownOptions(); err != nil {
		return err
	}
	root.SetInt("componentCount", int64(len(spec.Components)))

	if spec.SourceRunfiles != nil && spec.SourceBindings != nil {
		return fmt.Errorf("Local launch metadata must not expand source bindings")
	}
	var bindings map[string]*componentSources
	if spec.SourceBindings != nil {
		if bindings, err = readSourceBindings(*spec.SourceBindings, spec.Components); err != nil {
			return err
		}
	}
	components := make([]devBuildComponent, 0, len(spec.Components))
	for _, component := range spec.Components {
		manifestFile, err := absolutePath(component.Manifest)
		if err != nil {
			return err
		}
		manifest, err := readComponentManifest(manifestFile)
		if err != nil {
			return err
		}
		// A component resolves the paths of its entries against the working directory. That is the execution root
		// where its action staged them, so nothing becomes absolute here for it.
		value := devBuildComponent{manifest: manifest}
		if component.PluginClasspathPart != nil {
			if value.pluginClasspathPart, err = absolutePath(*component.PluginClasspathPart); err != nil {
				return err
			}
		}
		if bindings != nil {
			value.sourceBindings = bindings[component.Manifest]
		}
		components = append(components, value)
	}

	if _, err := os.Stat(outputDir); err == nil {
		if err := os.RemoveAll(outputDir); err != nil {
			return err
		}
	}
	composeOptions := composeOptions{
		expectedFragments: spec.ExpectedFragments,
		additionalModules: spec.AdditionalModules,
		tracer:            tracer,
		parent:            root,
	}
	if spec.PluginClasspathPrefix != nil {
		if composeOptions.pluginClasspathPrefix, err = absolutePath(*spec.PluginClasspathPrefix); err != nil {
			return err
		}
	}
	if spec.SourceRunfiles != nil {
		if composeOptions.sourceRunfiles, err = absoluteKeys(spec.SourceRunfiles); err != nil {
			return err
		}
	}
	if composeOptions.sourceDirectoryRunfiles, err = absoluteKeys(spec.SourceDirectoryRunfiles); err != nil {
		return err
	}
	result, err := composeComponents(components, outputDir, composeOptions)
	if err != nil {
		return err
	}
	for _, file := range []struct{ path, content string }{
		{filepath.Join(outputDir, "core-classpath.txt"), strings.Join(result.coreClassPath, "\n")},
		{filepath.Join(outputDir, "fingerprint.txt"), result.fingerprint},
		{fingerprintFile, result.fingerprint},
	} {
		if err := os.WriteFile(file.path, []byte(file.content), 0o666); err != nil {
			return err
		}
	}
	return writeDevIdeConfig(ideConfig, outputDir, result.mainClass, result.platformPrefix, result.additionalModules)
}

// absoluteKeys is Kotlin `mapKeys { Path.of(it.key).toAbsolutePath().normalize() }`. Two keys with one absolute path
// keep the position of the first and the value of the last.
func absoluteKeys(source *orderedMap) (*orderedMap, error) {
	result := &orderedMap{values: make(map[string]string, len(source.keys))}
	for _, key := range source.keys {
		absolute, err := absolutePath(key)
		if err != nil {
			return nil, err
		}
		result.put(absolute, source.values[key])
	}
	return result, nil
}

// writeDevIdeConfig writes the file that the Java DevIdeConfig reads. It names the home relative to the config file when
// the config file is above it, so that the pair can move as a unit. Both paths are absolute and normalized.
func writeDevIdeConfig(configFile, home, mainClass, platformPrefix string, additionalModules []string) error {
	configDir := filepath.Dir(configFile)
	hasParent := configDir != configFile
	homePath := home
	if hasParent && pathStartsWith(home, configDir) {
		if home == configDir {
			homePath = ""
		} else {
			homePath = strings.TrimPrefix(strings.TrimPrefix(home, configDir), string(filepath.Separator))
		}
	}
	if hasParent {
		if err := os.MkdirAll(configDir, 0o777); err != nil {
			return err
		}
	}
	content := "home.path=" + filepath.ToSlash(homePath) + "\n" +
		"main.class.name=" + mainClass + "\n" +
		"platform.prefix=" + platformPrefix + "\n" +
		"additional.modules=" + strings.Join(additionalModules, ",") + "\n"
	return os.WriteFile(configFile, []byte(content), 0o666)
}
