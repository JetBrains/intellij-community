package planfile

import (
	"fmt"
	"path"
	"path/filepath"
	"slices"
	"strings"

	"jetbrains.com/content-module-packer/internal/nativelib"
	"jetbrains.com/content-module-packer/internal/pluginclasspath"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

// goLayoutFormats are the layoutAssets formats the Go packer executes: a tree under one root and the entries of one jar.
var goLayoutFormats = map[string]bool{"tree": true, "entries": true}

// goLayoutTransforms are the transform kinds the Go packer executes. A nil transform is a plain copy.
var goLayoutTransforms = map[string]bool{"archive-tree": true, "gzip-xml-archive": true, "tree-map": true}

// Derivation is the execution contract of one chain, derived from the plan file.
// Catalogue is the input catalogue without its libraries. The derivation expands every library into its files, so
// the remainder input set holds files and directories only. pluginpack.Plan validates Recipe against it.
type Derivation struct {
	Recipe    pluginpack.Recipe
	Assets    []pluginpack.Asset
	ClassPath []byte
	Catalogue pluginpack.Catalogue
}

type plannedAsset struct {
	asset    Asset
	artifact string
}

// compiler holds the plan file, the ownership rows and the catalogue index of one derivation.
type compiler struct {
	file               *File
	independentModules []string
	assets             []plannedAsset
	required           []Preparation
	producers          map[string]Preparation
	requiredRaw        []string
	artifacts          map[string]pluginpack.Artifact
	libraries          map[string]pluginpack.Library
	goExecuted         map[string]*Operation
}

// Derive compiles the plan file for the Go packer. catalogue is the Starlark input catalogue of the chain.
// pluginDirectory is `plugins/<name>`; descriptor is the classpath descriptor in its final byte form.
// executionVersion is the version the chain declares; it must equal the version of the file and of its assets.
// independentModules names the modules whose jar the chain reuses from a content_module_jar target. An asset of the
// module's jar shape (see reusableModuleJar) is independent, and its artifact is the module name. The generator has
// matched the whole recipe against the target before it names the module.
func Derive(file *File, catalogue pluginpack.Catalogue, pluginDirectory string, descriptor []byte, executionVersion int, independentModules []string) (*Derivation, error) {
	pluginDirName := path.Base(filepath.ToSlash(filepath.Clean(pluginDirectory)))
	if pluginDirName == "" || pluginDirName == "." || pluginDirName == ".." || pluginDirName == "/" {
		return nil, fmt.Errorf("invalid plugin directory %q", pluginDirectory)
	}
	version := executionVersionOf(file.Assets)
	if file.Version != version || executionVersion != version {
		return nil, fmt.Errorf("plugin %q has a stale execution version: file=%d declared=%d required=%d; regenerate the dev distribution declarations",
			file.Plugin, file.Version, executionVersion, version)
	}
	c := &compiler{file: file, independentModules: independentModules, goExecuted: make(map[string]*Operation)}
	if err := c.plan(); err != nil {
		return nil, fmt.Errorf("plugin %q: %w", file.Plugin, err)
	}
	if err := c.bindOperations(); err != nil {
		return nil, fmt.Errorf("plugin %q: %w", file.Plugin, err)
	}
	if err := c.indexCatalogue(catalogue); err != nil {
		return nil, fmt.Errorf("plugin %q: %w", file.Plugin, err)
	}
	if err := c.resolveOperationInputs(); err != nil {
		return nil, fmt.Errorf("plugin %q: %w", file.Plugin, err)
	}
	assets := c.assetRows()
	operations, err := c.operations()
	if err != nil {
		return nil, fmt.Errorf("plugin %q: %w", file.Plugin, err)
	}
	classPath, err := pluginclasspath.Record(pluginDirName, descriptor, c.classPathJars())
	if err != nil {
		return nil, err
	}
	return &Derivation{
		Recipe:    pluginpack.Recipe{Version: version, Plugin: file.Plugin, LayoutSignature: file.LayoutSignature, Assets: assets, Operations: operations},
		Assets:    assets,
		ClassPath: classPath,
		Catalogue: pluginpack.Catalogue{Version: catalogue.Version, Artifacts: catalogue.Artifacts},
	}, nil
}

// executionVersionOf is pluginPackingExecutionVersion: 3 with a distribution asset, 2 with a tree, else 1.
func executionVersionOf(assets []Asset) int {
	switch {
	case slices.ContainsFunc(assets, func(asset Asset) bool { return asset.Scope == pluginpack.DistributionScope }):
		return pluginpack.ScopedVersion
	case slices.ContainsFunc(assets, func(asset Asset) bool { return asset.Kind == "tree" }):
		return pluginpack.TreeVersion
	default:
		return pluginpack.Version
	}
}

// reusableModuleJar is the module whose jar the recipe packs in the shape a content_module_jar target packs: the module
// output first, then only library containers, with the default writer and mode. The generator states the rest of the
// equality, so the libraries are not compared here.
func reusableModuleJar(recipe JarRecipe, mode uint32) (string, bool) {
	if mode != DefaultMode || recipe.Writer != moduleJarRecipe("").Writer || len(recipe.Sources) == 0 {
		return "", false
	}
	owner := recipe.Sources[0]
	if owner.Kind != "module" || owner.Filter != "module-v1" || !plainSource(owner) {
		return "", false
	}
	for _, source := range recipe.Sources[1:] {
		if source.Kind != "library" || source.Filter != "library-v1" || !plainSource(source) {
			return "", false
		}
	}
	return owner.Input, true
}

// plainSource reports whether the source states nothing beyond its input, kind and filter.
func plainSource(source JarSource) bool {
	return source.Entry == "" && len(source.Options) == 0 && source.PreparedManifest == nil
}

// plan is planPluginPacking: the asset rules, the ownership match and the required preparations.
func (c *compiler) plan() error {
	file := c.file
	if file.Plugin == "" {
		return fmt.Errorf("a plugin plan requires a plugin")
	}
	for _, asset := range file.Assets {
		if err := validateAsset(asset); err != nil {
			return err
		}
	}
	independent := make(map[string]bool, len(c.independentModules))
	for _, module := range c.independentModules {
		if module == "" {
			return fmt.Errorf("an independent module requires a name")
		}
		if independent[module] {
			return fmt.Errorf("independent module %q is named twice", module)
		}
		independent[module] = true
	}
	used := make(map[string]bool)
	for _, asset := range file.Assets {
		planned := plannedAsset{asset: asset}
		if recipe := asset.Recipe; recipe != nil && !slices.ContainsFunc(recipe.Sources, func(source JarSource) bool { return source.Kind == "prepared" }) {
			inputs := make(map[string]bool, len(asset.Inputs))
			for _, input := range asset.Inputs {
				inputs[input] = true
			}
			sources := make(map[string]bool, len(recipe.Sources))
			for _, source := range recipe.Sources {
				sources[source.Input] = true
			}
			if len(inputs) == len(sources) && !slices.ContainsFunc(recipe.Sources, func(source JarSource) bool { return !inputs[source.Input] }) {
				if module, ok := reusableModuleJar(*recipe, asset.Mode); ok && independent[module] {
					planned.artifact = module
				}
			}
		}
		if planned.artifact != "" {
			used[planned.artifact] = true
		}
		c.assets = append(c.assets, planned)
	}
	if len(used) != len(c.independentModules) {
		for _, module := range c.independentModules {
			if !used[module] {
				return fmt.Errorf("independent module %q matches no module jar asset; regenerate the dev distribution declarations", module)
			}
		}
	}
	c.producers = make(map[string]Preparation)
	seen := make(map[string]bool, len(file.Preparations))
	for _, preparation := range file.Preparations {
		if seen[preparation.ID] || preparation.ID == "" || preparation.ModelSignature == "" {
			return fmt.Errorf("invalid or repeated preparation %q", preparation.ID)
		}
		seen[preparation.ID] = true
		for _, output := range preparation.Outputs {
			if _, exists := c.producers[output]; exists || output == "" {
				return fmt.Errorf("conflicting preparation output %q", output)
			}
			c.producers[output] = preparation
		}
	}
	for _, asset := range file.Assets {
		if asset.Recipe == nil {
			continue
		}
		for _, source := range asset.Recipe.Sources {
			if _, produced := c.producers[source.Input]; source.Kind == "prepared" && !produced {
				return fmt.Errorf("prepared source %q of %q has no producer in the plan file", source.Input, asset.Destination)
			}
		}
	}
	requiredIDs := make(map[string]bool)
	visiting := make(map[string]bool)
	var requireInput func(string) error
	requireInput = func(input string) error {
		if input == "" {
			return fmt.Errorf("an empty preparation input")
		}
		preparation, produced := c.producers[input]
		if !produced {
			if !slices.Contains(c.requiredRaw, input) {
				c.requiredRaw = append(c.requiredRaw, input)
			}
			return nil
		}
		if requiredIDs[preparation.ID] {
			return nil
		}
		if visiting[preparation.ID] {
			return fmt.Errorf("a preparation cycle at %q", preparation.ID)
		}
		visiting[preparation.ID] = true
		for _, dependency := range preparation.Inputs {
			if err := requireInput(dependency); err != nil {
				return err
			}
		}
		delete(visiting, preparation.ID)
		requiredIDs[preparation.ID] = true
		c.required = append(c.required, preparation)
		return nil
	}
	for _, planned := range c.assets {
		if planned.artifact != "" {
			continue
		}
		for _, input := range planned.asset.Inputs {
			if err := requireInput(input); err != nil {
				return err
			}
		}
	}
	for _, root := range file.PreparationRoots {
		if err := requireInput(root); err != nil {
			return err
		}
	}
	for _, preparation := range file.Preparations {
		if preparation.AlwaysRun {
			return fmt.Errorf("preparation %q always runs, which needs a Kotlin preparation", preparation.ID)
		}
	}
	return nil
}

func validateAsset(asset Asset) error {
	destination := asset.Destination
	if asset.Scope != pluginpack.PluginScope && asset.Scope != pluginpack.DistributionScope {
		return fmt.Errorf("unknown plugin asset scope %q", asset.Scope)
	}
	if destination == "" && !(asset.Kind == "tree" && asset.Scope == pluginpack.PluginScope) {
		return fmt.Errorf("only a declared tree can target the plugin root")
	}
	if slices.Contains(asset.Inputs, "") {
		return fmt.Errorf("asset %q has an empty input", destination)
	}
	if asset.Kind != "file" && asset.Kind != "directory" && asset.Kind != "tree" {
		return fmt.Errorf("unsupported plugin asset kind %q", asset.Kind)
	}
	if asset.Mode > 0o777 || asset.Mode == 0 && !(asset.Kind == "file" && asset.Recipe == nil && asset.SymlinkTarget == nil) {
		return fmt.Errorf("asset %q has an unsupported mode", destination)
	}
	if asset.NormalizeTreeModes && asset.Kind != "tree" {
		return fmt.Errorf("only a plugin tree can normalize copied modes: %q", destination)
	}
	if asset.Kind == "tree" && (len(asset.Inputs) != 1 || asset.Recipe != nil || asset.SymlinkTarget != nil || asset.ClassPath || asset.Mode != DefaultMode) {
		return fmt.Errorf("plugin tree %q requires one directory input, no jar recipe, no link target, no classpath, and no mode override", destination)
	}
	if asset.Kind == "directory" && (len(asset.Inputs) != 0 || asset.Recipe != nil || asset.SymlinkTarget != nil) {
		return fmt.Errorf("plugin directory %q must not declare file inputs or a link target", destination)
	}
	if asset.Scope == pluginpack.DistributionScope && asset.ClassPath {
		return fmt.Errorf("distribution asset %q must not contribute to the plugin classpath", destination)
	}
	if target := asset.SymlinkTarget; target != nil {
		if asset.Recipe != nil || len(asset.Inputs) != 0 {
			return fmt.Errorf("plugin link %q must not declare file inputs", destination)
		}
		resolved := path.Join(path.Dir(destination), *target)
		if *target == "" || path.IsAbs(*target) || strings.ContainsAny(*target, "\\:\x00") || resolved == ".." || strings.HasPrefix(resolved, "../") {
			return fmt.Errorf("plugin link %q escapes the plugin: %s", destination, *target)
		}
	}
	if recipe := asset.Recipe; recipe != nil {
		for _, source := range recipe.Sources {
			if !slices.Contains(asset.Inputs, source.Input) {
				return fmt.Errorf("asset %q does not declare every source of its recipe", destination)
			}
		}
	}
	return nil
}

// bindOperations pairs every required preparation with its operation and checks the operation against its
// definition and its consumers. Every operation must be one the Go packer executes.
func (c *compiler) bindOperations() error {
	requiredIDs := make(map[string]Preparation, len(c.required))
	for _, preparation := range c.required {
		requiredIDs[preparation.ID] = preparation
	}
	bound := make(map[string]bool)
	for index := range c.file.Operations {
		operation := &c.file.Operations[index]
		if bound[operation.ID] {
			return fmt.Errorf("duplicate preparation operation %q", operation.ID)
		}
		bound[operation.ID] = true
		definition, required := requiredIDs[operation.ID]
		if !required {
			return fmt.Errorf("unexpected preparation operation %q", operation.ID)
		}
		if !manifestPolicies[operation.Manifest] || operation.Manifest == defaultManifest {
			return fmt.Errorf("operation %q has an unknown manifest policy %q", operation.ID, operation.Manifest)
		}
		var expectedInputs []string
		if operation.Kind == moduleFilterKind || operation.Kind == nativeSelectKind {
			expectedInputs = []string{operation.Input.Artifact}
		} else {
			for _, reference := range operation.Inputs {
				if !slices.Contains(expectedInputs, reference.Artifact) {
					expectedInputs = append(expectedInputs, reference.Artifact)
				}
			}
		}
		if !slices.Equal(definition.Inputs, expectedInputs) {
			return fmt.Errorf("preparation %q must declare exactly inputs %v", operation.ID, expectedInputs)
		}
		if !slices.Equal(definition.Outputs, []string{operation.Output}) {
			return fmt.Errorf("preparation %q must declare exactly output %q", operation.ID, operation.Output)
		}
		if err := c.validateConsumers(operation); err != nil {
			return err
		}
		c.goExecuted[operation.Output] = operation
	}
	for _, preparation := range c.required {
		if !bound[preparation.ID] {
			return fmt.Errorf("missing preparation operation %q", preparation.ID)
		}
	}
	return nil
}

// validateConsumers applies the consumer rules of the Kotlin generator: a tree output has one tree asset at its
// root, and a module-filter or entries output is a prepared jar source.
// A native-select output has one distribution tree and one prepared jar source.
func (c *compiler) validateConsumers(operation *Operation) error {
	var consumers []Asset
	for _, planned := range c.assets {
		if slices.Contains(planned.asset.Inputs, operation.Output) {
			if planned.artifact != "" {
				return fmt.Errorf("output %q of operation %q is consumed by an independent asset", operation.Output, operation.ID)
			}
			consumers = append(consumers, planned.asset)
		}
	}
	if operation.Kind == nativeSelectKind {
		trees, jarSources := 0, 0
		for _, consumer := range consumers {
			switch {
			case consumer.Kind == "tree" && consumer.Scope == pluginpack.DistributionScope && !consumer.ClassPath && consumer.Recipe == nil &&
				slices.Equal(consumer.Inputs, []string{operation.Output}):
				trees++
			case consumer.Recipe != nil && slices.ContainsFunc(consumer.Recipe.Sources, func(source JarSource) bool {
				return source.Kind == "prepared" && source.Filter == "prepared" && source.Input == operation.Output
			}):
				jarSources++
			}
		}
		if len(consumers) != 2 || trees != 1 || jarSources != 1 {
			return fmt.Errorf("native selection %q requires one distribution tree consumer and one prepared jar source", operation.ID)
		}
		return nil
	}
	if layout := operation.LayoutAssets; layout != nil {
		if !goLayoutFormats[layout.Format] {
			return fmt.Errorf("operation %q has the layout format %q, which the Go packer does not execute; the plan needs a Kotlin preparation", operation.ID, layout.Format)
		}
		if layout.Format == "entries" && layout.Root != "" {
			return fmt.Errorf("operation %q must not declare a tree root for its entries", operation.ID)
		}
		for _, asset := range layout.Assets {
			if asset.Transform != nil && !goLayoutTransforms[asset.Transform.Kind] {
				return fmt.Errorf("operation %q has the layout transform %q, which the Go packer does not execute; the plan needs a Kotlin preparation", operation.ID, asset.Transform.Kind)
			}
		}
		if layout.Format == "tree" {
			if len(consumers) != 1 || consumers[0].Kind != "tree" || consumers[0].Destination != layout.Root || !slices.Equal(consumers[0].Inputs, []string{operation.Output}) || consumers[0].ClassPath {
				return fmt.Errorf("layout asset preparation %q requires one tree asset at %q", operation.ID, layout.Root)
			}
			return nil
		}
	}
	for _, consumer := range consumers {
		if consumer.Recipe == nil || !slices.ContainsFunc(consumer.Recipe.Sources, func(source JarSource) bool { return source.Kind == "prepared" && source.Input == operation.Output }) {
			return fmt.Errorf("output %q of operation %q requires a prepared jar source at %q", operation.Output, operation.ID, consumer.Destination)
		}
	}
	return nil
}

// indexCatalogue checks the input catalogue against the required raw inputs.
func (c *compiler) indexCatalogue(catalogue pluginpack.Catalogue) error {
	if catalogue.Version != pluginpack.Version {
		return fmt.Errorf("unsupported artifact catalogue version %d", catalogue.Version)
	}
	c.artifacts = make(map[string]pluginpack.Artifact, len(catalogue.Artifacts))
	c.libraries = make(map[string]pluginpack.Library, len(catalogue.Libraries))
	raw := make(map[string]bool)
	for _, artifact := range catalogue.Artifacts {
		if _, exists := c.artifacts[artifact.ID]; exists || !validID(artifact.ID) {
			return fmt.Errorf("invalid or duplicate artifact ID %q", artifact.ID)
		}
		if artifact.Kind != "file" && artifact.Kind != "directory" {
			return fmt.Errorf("unknown artifact root kind %q", artifact.Kind)
		}
		if artifact.Tree != nil {
			return fmt.Errorf("catalogue artifact %q carries prepared tree metadata, which a plan without a Kotlin preparation cannot have", artifact.ID)
		}
		c.artifacts[artifact.ID] = artifact
		raw[artifact.ID] = true
	}
	for _, library := range catalogue.Libraries {
		if _, exists := c.libraries[library.ID]; exists || !validID(library.ID) || raw[library.ID] || len(library.Files) == 0 {
			return fmt.Errorf("invalid or duplicate library %q", library.ID)
		}
		seen := make(map[pluginpack.Reference]bool, len(library.Files))
		for _, reference := range library.Files {
			artifact, exists := c.artifacts[reference.Artifact]
			if !exists || artifact.Kind != "file" || reference.Path != "" || seen[reference] {
				return fmt.Errorf("library %q requires distinct file artifacts", library.ID)
			}
			seen[reference] = true
			raw[reference.Artifact] = true
		}
		c.libraries[library.ID] = library
		raw[library.ID] = true
	}
	for _, preparation := range c.file.Preparations {
		for _, output := range preparation.Outputs {
			if raw[output] {
				return fmt.Errorf("preparation %q output %q aliases a raw catalogue ID", preparation.ID, output)
			}
		}
	}
	expected := make(map[string]bool)
	for _, input := range c.requiredRaw {
		if library, isLibrary := c.libraries[input]; isLibrary {
			for _, reference := range library.Files {
				expected[reference.Artifact] = true
			}
		} else {
			expected[input] = true
		}
	}
	for id := range expected {
		if _, exists := c.artifacts[id]; !exists {
			return fmt.Errorf("stale preparation inputs: the catalogue lacks %q", id)
		}
	}
	for id := range c.artifacts {
		if !expected[id] {
			return fmt.Errorf("stale preparation inputs: the catalogue artifact %q is not an input of the plan", id)
		}
	}
	for id := range c.libraries {
		if !slices.Contains(c.requiredRaw, id) {
			return fmt.Errorf("stale preparation inputs: the catalogue library %q is not an input of the plan", id)
		}
	}
	return nil
}

func validID(value string) bool {
	return value != "" && strings.TrimSpace(value) == value && !strings.ContainsAny(value, "\x00\r\n")
}

// resolveOperationInputs replaces a library ID in the inputs of every Go-executed operation with the references of
// its member files. The plan names the version-free library; the catalogue names its files. The primary input of a
// module-filter or native-select names one file, so a library there has one member. A layout-assets input stands for
// every member in catalogue order, and the sources of each layout asset follow the expanded positions. The resolved
// copies replace the plan operations in goExecuted, so the plan file keeps its text.
func (c *compiler) resolveOperationInputs() error {
	for output, operation := range c.goExecuted {
		resolved := *operation
		if operation.Input != nil {
			members, err := c.resolveReference(*operation.Input)
			if err != nil {
				return fmt.Errorf("operation %q: %w", operation.ID, err)
			}
			if len(members) != 1 {
				return fmt.Errorf("operation %q: library %q has %d members; the primary input names one file", operation.ID, operation.Input.Artifact, len(members))
			}
			resolved.Input = &members[0]
		}
		if len(operation.Inputs) != 0 {
			positions := make([][]int, len(operation.Inputs))
			resolved.Inputs = nil
			for index, reference := range operation.Inputs {
				members, err := c.resolveReference(reference)
				if err != nil {
					return fmt.Errorf("operation %q: %w", operation.ID, err)
				}
				for _, member := range members {
					positions[index] = append(positions[index], len(resolved.Inputs))
					resolved.Inputs = append(resolved.Inputs, member)
				}
			}
			if layout := operation.LayoutAssets; layout != nil {
				expanded := *layout
				expanded.Assets = make([]pluginpack.LayoutAsset, len(layout.Assets))
				for index, asset := range layout.Assets {
					if len(asset.Sources) != 0 {
						sources := make([]int, 0, len(asset.Sources))
						for _, source := range asset.Sources {
							if source < 0 || source >= len(positions) {
								return fmt.Errorf("operation %q: layout asset %q names the input %d, which the operation lacks", operation.ID, asset.Destination, source)
							}
							sources = append(sources, positions[source]...)
						}
						asset.Sources = sources
					}
					expanded.Assets[index] = asset
				}
				resolved.LayoutAssets = &expanded
			}
		}
		c.goExecuted[output] = &resolved
	}
	return nil
}

// resolveReference is the references of the member files of a library in catalogue order, or the reference itself
// when it names no library.
func (c *compiler) resolveReference(reference pluginpack.Reference) ([]pluginpack.Reference, error) {
	library, isLibrary := c.libraries[reference.Artifact]
	if !isLibrary {
		return []pluginpack.Reference{reference}, nil
	}
	if reference.Path != "" {
		return nil, fmt.Errorf("library %q is not a directory: the input names the path %q in it", reference.Artifact, reference.Path)
	}
	return library.Files, nil
}

// assetRows is deriveDevPluginExecutionAssets: the producer of every asset in plan order. A default is left empty,
// the way the Kotlin encoder omits it.
func (c *compiler) assetRows() []pluginpack.Asset {
	rows := make([]pluginpack.Asset, 0, len(c.assets))
	for _, planned := range c.assets {
		asset := planned.asset
		row := pluginpack.Asset{Destination: asset.Destination, Producer: "remainder", Artifact: planned.artifact,
			Kind: kindField(asset.Kind), ClassPath: classPathField(asset.ClassPath), NormalizeTreeModes: asset.NormalizeTreeModes, Scope: scopeField(asset.Scope)}
		if planned.artifact != "" {
			row.Producer = "independent"
		}
		rows = append(rows, row)
	}
	return rows
}

func kindField(kind string) string {
	if kind == "file" {
		return ""
	}
	return kind
}

func scopeField(scope string) string {
	if scope == pluginpack.PluginScope {
		return ""
	}
	return scope
}

func classPathField(classPath bool) *bool {
	if classPath {
		return nil
	}
	return &classPath
}

// classPathJars selects the plan-scope classpath jars directly under lib/, in plan order.
func (c *compiler) classPathJars() []string {
	var jars []string
	for _, planned := range c.assets {
		asset := planned.asset
		destination := asset.Destination
		if asset.Scope == pluginpack.PluginScope && asset.ClassPath && asset.Kind == "file" && strings.HasPrefix(destination, "lib/") &&
			strings.Count(destination, "/") == 1 && strings.HasSuffix(destination, ".jar") {
			jars = append(jars, destination)
		}
	}
	return jars
}

// operations compiles one remainder operation per remainder asset.
func (c *compiler) operations() ([]pluginpack.Operation, error) {
	var operations []pluginpack.Operation
	for _, planned := range c.assets {
		if planned.artifact != "" {
			continue
		}
		asset := planned.asset
		operation := pluginpack.Operation{Destination: asset.Destination, Scope: scopeField(asset.Scope)}
		switch {
		case asset.Kind == "tree":
			input := asset.Inputs[0]
			if asset.NormalizeTreeModes {
				operation.Mode = DefaultMode
			}
			if goOperation, executed := c.goExecuted[input]; executed && goOperation.Kind == nativeSelectKind {
				target, err := c.nativeTarget()
				if err != nil {
					return nil, err
				}
				operation.Kind = "native-tree"
				operation.Input = goOperation.Input
				operation.Native = target
			} else if executed {
				layout := goOperation.LayoutAssets
				if layout == nil || layout.Format != "tree" || layout.Root != asset.Destination {
					return nil, fmt.Errorf("tree %q requires a layout-assets tree operation at its destination", asset.Destination)
				}
				operation.Kind = "layout-tree"
				operation.Layout = &pluginpack.LayoutAssets{Inputs: goOperation.Inputs, Assets: layout.Assets}
			} else {
				if _, produced := c.producers[input]; produced {
					return nil, fmt.Errorf("tree %q reads the prepared output %q, which needs a Kotlin preparation", asset.Destination, input)
				}
				if artifact, exists := c.artifacts[input]; !exists || artifact.Kind != "directory" {
					return nil, fmt.Errorf("tree %q requires a directory artifact", asset.Destination)
				}
				operation.Kind = "copy-tree"
				operation.Input = &pluginpack.Reference{Artifact: input}
			}
		case asset.Kind == "directory":
			operation.Kind = "directory"
			operation.Mode = asset.Mode
		case asset.SymlinkTarget != nil:
			operation.Kind = "symlink"
			operation.Target = *asset.SymlinkTarget
		case asset.Recipe == nil:
			if len(asset.Inputs) != 1 {
				return nil, fmt.Errorf("completed asset %q requires one declared file", asset.Destination)
			}
			input := asset.Inputs[0]
			operation.Mode = asset.Mode
			if _, produced := c.producers[input]; produced {
				return nil, fmt.Errorf("completed asset %q requires a prepared file, not a Go-executed output", asset.Destination)
			}
			operation.Kind = "copy"
			operation.Input = &pluginpack.Reference{Artifact: input}
		default:
			sources, err := c.compileSources(asset.Recipe, asset.Destination)
			if err != nil {
				return nil, fmt.Errorf("%s: %w", asset.Destination, err)
			}
			directories := "none"
			if asset.Recipe.Writer.DirectoryEntries {
				directories = "all"
			}
			operation.Kind = "jar"
			operation.Mode = asset.Mode
			operation.Sources = sources
			operation.Options = &pluginpack.JarOptions{MergeEntities: asset.Recipe.Writer.MergeEntities, Directories: directories}
		}
		operations = append(operations, operation)
	}
	return operations, nil
}

var sourceOptions = map[string]bool{"patch": true, "lib-module": true, "manifest=keep": true, "manifest=drop": true,
	"manifest=coverage-agent": true, "manifest=rewrite-boot-class-path": true}

// compileSources is the Kotlin compileSources: it turns the recipe sources into the sources the Go packer reads.
// A prepared source of a Go-executed operation becomes the archive source with its excludes, or the layout source.
func (c *compiler) compileSources(recipe *JarRecipe, destination string) ([]pluginpack.Source, error) {
	name := path.Base(destination)
	if recipe.Writer.OutputName != "" && recipe.Writer.OutputName != name {
		return nil, fmt.Errorf("the writer output name does not match %q", destination)
	}
	if recipe.Writer.RewriteBootClassPath && !strings.Contains(name, "intellij.platform.coverage.agent") {
		return nil, fmt.Errorf("coverage manifest rewriting requires the coverage agent destination")
	}
	var meaningful int
	for _, source := range recipe.Sources {
		switch {
		case source.PreparedManifest != nil:
			operation, executed := c.goExecuted[source.Input]
			if !executed {
				return nil, fmt.Errorf("prepared source %q has no Go-executed producer in the plan file", source.Input)
			}
			policies := source.PreparedManifest.SourceManifestPolicies
			if len(policies) == 0 {
				policies = []string{"keep"}
			}
			if !slices.Equal(policies, []string{operation.Manifest}) {
				return nil, fmt.Errorf("prepared source %q has stale manifest policies", source.Input)
			}
			if count := source.PreparedManifest.OriginalMeaningfulSourceCount; count != nil {
				meaningful += *count
			} else {
				meaningful++
			}
		case source.Kind == "prepared":
			if recipe.Writer.Manifest == defaultManifest {
				return nil, fmt.Errorf("prepared sources require an explicit manifest policy")
			}
			meaningful++
		case slices.Contains(source.Options, "lib-module") || source.Kind == "module" && strings.HasPrefix(source.Input, libraryPrefix):
		case source.Kind == "library":
			library, exists := c.libraries[source.Input]
			if !exists {
				return nil, fmt.Errorf("unknown library %q", source.Input)
			}
			meaningful += len(library.Files)
		default:
			meaningful++
		}
	}
	manifest := recipe.Writer.Manifest
	switch manifest {
	case defaultManifest:
		manifest = "drop"
		if meaningful == 1 {
			manifest = "keep"
		}
	case "keep", "drop":
	default:
		return nil, fmt.Errorf("unknown manifest policy %q", recipe.Writer.Manifest)
	}
	var sources []pluginpack.Source
	for _, source := range recipe.Sources {
		if len(source.Options) != len(slices.Compact(slices.Sorted(slices.Values(source.Options)))) ||
			slices.ContainsFunc(source.Options, func(option string) bool { return !sourceOptions[option] }) {
			return nil, fmt.Errorf("source %q requires an unsupported preparation option: %v", source.Input, source.Options)
		}
		sourceManifest := manifest
		manifestOptions := 0
		for _, option := range source.Options {
			if policy, isManifest := strings.CutPrefix(option, "manifest="); isManifest {
				manifestOptions++
				sourceManifest = policy
			}
		}
		if manifestOptions > 1 {
			return nil, fmt.Errorf("source %q has conflicting manifest policies", source.Input)
		}
		if source.Kind == "prepared" {
			if len(source.Options) != 0 || source.Entry != "" || source.Filter != "prepared" {
				return nil, fmt.Errorf("prepared source %q must materialize its options", source.Input)
			}
			operation, executed := c.goExecuted[source.Input]
			if !executed {
				return nil, fmt.Errorf("prepared source %q has no Go-executed producer in the plan file", source.Input)
			}
			compiled, err := goExecutedSource(operation)
			if err != nil {
				return nil, err
			}
			sources = append(sources, compiled)
			continue
		}
		var filter string
		switch source.Filter {
		case "module", "library", "all":
			filter = source.Filter
		case "module-v1":
			filter = "module"
		case "library-v1":
			filter = "library"
		case "none":
			filter = "all"
		default:
			return nil, fmt.Errorf("custom filter %q requires declared preparation inputs", source.Filter)
		}
		patch := slices.Contains(source.Options, "patch")
		switch source.Kind {
		case "zip", "archive", "module":
			if source.Entry != "" || patch {
				return nil, fmt.Errorf("archive source %q contains entry options", source.Input)
			}
			if _, isLibrary := c.libraries[source.Input]; isLibrary {
				return nil, fmt.Errorf("archive source %q names a library; a library source merges its members", source.Input)
			}
			compiled, err := c.archiveSource(recipe, pluginpack.Reference{Artifact: source.Input}, filter, sourceManifest)
			if err != nil {
				return nil, err
			}
			sources = append(sources, compiled)
		case "library":
			if source.Entry != "" || patch {
				return nil, fmt.Errorf("library %q contains entry options", source.Input)
			}
			library, exists := c.libraries[source.Input]
			if !exists {
				return nil, fmt.Errorf("unknown library %q", source.Input)
			}
			for _, reference := range library.Files {
				compiled, err := c.archiveSource(recipe, reference, filter, sourceManifest)
				if err != nil {
					return nil, err
				}
				sources = append(sources, compiled)
			}
		case "file":
			if filter != "all" {
				return nil, fmt.Errorf("file source %q contains filter options", source.Input)
			}
			kind := "file"
			if patch {
				kind = "patch"
			}
			sources = append(sources, pluginpack.Source{Kind: "entries", Manifest: sourceManifest,
				Entries: []pluginpack.PreparedEntry{{Kind: kind, Name: source.Entry, Input: &pluginpack.Reference{Artifact: source.Input}}}})
		default:
			return nil, fmt.Errorf("source kind %q requires declared preparation inputs", source.Kind)
		}
	}
	return sources, nil
}

// archiveSource is one archive source. The coverage agent keeps its manifest when the writer rewrites the boot class path.
func (c *compiler) archiveSource(recipe *JarRecipe, reference pluginpack.Reference, filter, manifest string) (pluginpack.Source, error) {
	artifact, exists := c.artifacts[reference.Artifact]
	if !exists {
		return pluginpack.Source{}, fmt.Errorf("unresolved input %q", reference.Artifact)
	}
	sourceName := reference.Path
	if sourceName == "" {
		sourceName = artifact.Root
	}
	if recipe.Writer.RewriteBootClassPath && strings.HasPrefix(path.Base(filepath.ToSlash(sourceName)), "intellij-coverage-agent") {
		manifest = "coverage-agent"
	}
	input := reference
	return pluginpack.Source{Kind: "archive", Input: &input, Filter: filter, Manifest: manifest}, nil
}

// nativeTarget reads the platform of the plan file from its variant. A plan with a native-select operation is never
// neutral: the generator keeps one record per platform, so the variant is a real platform id.
func (c *compiler) nativeTarget() (*pluginpack.NativeTarget, error) {
	family, arch, err := nativelib.ParseVariant(c.file.Variant)
	if err != nil {
		return nil, fmt.Errorf("a native-select operation reads the platform of the plan file: %w", err)
	}
	return &pluginpack.NativeTarget{OS: string(family), Arch: string(arch)}, nil
}

// goExecutedSource is the jar source the Go packer executes in place of a Go-executed operation's prepared output.
// A native-select becomes the native archive itself, with every native entry reserved for the tree.
func goExecutedSource(operation *Operation) (pluginpack.Source, error) {
	if operation.Kind == moduleFilterKind {
		return pluginpack.Source{Kind: "archive", Input: operation.Input, Filter: "module", Excludes: operation.Excludes, Manifest: operation.Manifest}, nil
	}
	if operation.Kind == nativeSelectKind {
		return pluginpack.Source{Kind: "archive", Input: operation.Input, Filter: operation.Filter, Manifest: operation.Manifest, ReserveNatives: true}, nil
	}
	layout := operation.LayoutAssets
	if layout == nil || layout.Format != "entries" {
		return pluginpack.Source{}, fmt.Errorf("prepared source %q requires a module-filter or a layout-assets entries operation", operation.Output)
	}
	return pluginpack.Source{Kind: "layout", Manifest: "keep", Layout: &pluginpack.LayoutAssets{Inputs: operation.Inputs, Assets: layout.Assets}}, nil
}
