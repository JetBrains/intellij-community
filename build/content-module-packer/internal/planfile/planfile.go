// Package planfile decodes the plan file of one complex plugin. It derives the execution recipe, the asset rows and
// the plugin classpath record of the chain.
//
// The decoder is strict. It accepts the compact forms of PluginPackingProjectionEncoding.kt and refuses every
// operation the Go packer does not execute. It recomputes no signature: plugin-model-tool --check owns the layout
// signature and every operation signature.
package planfile

import (
	"encoding/json"
	"fmt"
	"slices"
	"strings"

	"jetbrains.com/content-module-packer/internal/pluginpack"
)

// DefaultMode is the mode of a plan asset that states none: 0644, or 420 in the plan file.
const DefaultMode uint32 = 0o644

const (
	moduleFilterKind = "module-filter"
	layoutAssetsKind = "layout-assets"
	nativeSelectKind = "native-select"
	defaultManifest  = "single-meaningful-source"
	libraryPrefix    = "intellij.libraries."
)

// File is one decoded plan file in its full form.
type File struct {
	Version           int
	Plugin            string
	Variant           string
	LayoutSignature   string
	Assets            []Asset
	Preparations      []Preparation
	PreparationRoots  []string
	ReusableArtifacts []ReusableArtifact
	Operations        []Operation
}

// Asset is one plan asset. Kind is file, directory, or tree. Scope is plugin or distribution.
type Asset struct {
	Destination        string
	Inputs             []string
	Recipe             *JarRecipe
	Mode               uint32
	SymlinkTarget      *string
	Kind               string
	ClassPath          bool
	NormalizeTreeModes bool
	Scope              string
}

// JarRecipe is the canonical recipe of one jar. Two assets with equal recipes and modes share one reusable artifact.
type JarRecipe struct {
	Sources []JarSource
	Writer  JarWriter
}

// JarSource is one ordered jar source. A prepared source names a preparation output.
type JarSource struct {
	Input            string
	Kind             string
	Filter           string
	Entry            string
	Expansion        []string
	Options          []string
	PreparedManifest *PreparedManifest
}

// PreparedManifest states the manifest facts of a prepared source. A nil count counts module patches.
type PreparedManifest struct {
	OriginalMeaningfulSourceCount *int
	SourceManifestPolicies        []string
}

// JarWriter holds the writer options of one jar recipe.
type JarWriter struct {
	Manifest             string
	MergeEntities        bool
	DirectoryEntries     bool
	RewriteBootClassPath bool
	OutputName           string
}

// ReusableArtifact is one Bazel-packed jar the plan may reuse in place of a remainder operation.
type ReusableArtifact struct {
	Label  string
	Recipe JarRecipe
	Mode   uint32
}

// Preparation is one preparation definition. Its operation is in File.Operations under the same ID.
type Preparation struct {
	ID             string
	Inputs         []string
	Outputs        []string
	ModelSignature string
	AlwaysRun      bool
}

// Operation is one preparation operation. The Go packer executes the kinds module-filter, layout-assets, and native-select.
// A module-filter reads Input and filters it by Excludes. A layout-assets operation reads Inputs into LayoutAssets.
// A native-select reads the native archive Input with Filter, reserves its native entries in the jar that consumes
// its output, and writes the entries of the plan's platform into the tree that consumes it.
type Operation struct {
	ID           string
	Kind         string
	Input        *pluginpack.Reference
	Inputs       []pluginpack.Reference
	Output       string
	Manifest     string
	Excludes     []string
	Filter       string
	LayoutAssets *LayoutAssetPreparation
}

// LayoutAssetPreparation is the layoutAssets payload of one operation. Format is tree, entries, or file.
type LayoutAssetPreparation struct {
	Format string
	Root   string
	Assets []pluginpack.LayoutAsset
}

type rawFile struct {
	Version           *int                  `json:"version"`
	Plugin            *string               `json:"plugin"`
	Variant           *string               `json:"variant"`
	LayoutSignature   *string               `json:"layoutSignature"`
	Assets            *[]rawAsset           `json:"assets"`
	Preparations      []rawPreparation      `json:"preparations"`
	PreparationRoots  []string              `json:"preparationRoots"`
	ReusableArtifacts []rawReusableArtifact `json:"reusableArtifacts"`
	Operations        []rawOperation        `json:"operations"`
}

type rawAsset struct {
	Module             *string       `json:"module"`
	Destination        *string       `json:"destination"`
	Inputs             *[]string     `json:"inputs"`
	Recipe             *rawJarRecipe `json:"recipe"`
	Mode               *uint32       `json:"mode"`
	SymlinkTarget      *string       `json:"symlinkTarget"`
	Kind               *string       `json:"kind"`
	ClassPath          *bool         `json:"classPath"`
	NormalizeTreeModes *bool         `json:"normalizeTreeModes"`
	Scope              *string       `json:"scope"`
}

type rawJarRecipe struct {
	Sources *[]rawJarSource `json:"sources"`
	Writer  *rawJarWriter   `json:"writer"`
}

type rawJarSource struct {
	Input            *string              `json:"input"`
	Kind             *string              `json:"kind"`
	Filter           *string              `json:"filter"`
	Entry            *string              `json:"entry"`
	Expansion        []string             `json:"expansion"`
	Options          []string             `json:"options"`
	PreparedManifest *rawPreparedManifest `json:"preparedManifest"`
}

type rawPreparedManifest struct {
	Version                       *int      `json:"version"`
	OriginalMeaningfulSourceCount *int      `json:"originalMeaningfulSourceCount"`
	SourceManifestPolicies        *[]string `json:"sourceManifestPolicies"`
}

type rawJarWriter struct {
	Manifest             *string `json:"manifest"`
	MergeEntities        *bool   `json:"mergeEntities"`
	DirectoryEntries     *bool   `json:"directoryEntries"`
	RewriteBootClassPath *bool   `json:"rewriteBootClassPath"`
	OutputName           *string `json:"outputName"`
}

type rawReusableArtifact struct {
	Label  *string       `json:"label"`
	Module *string       `json:"module"`
	Recipe *rawJarRecipe `json:"recipe"`
	Mode   *uint32       `json:"mode"`
}

type rawPreparation struct {
	ID             *string   `json:"id"`
	Inputs         *[]string `json:"inputs"`
	Outputs        *[]string `json:"outputs"`
	ModelSignature *string   `json:"modelSignature"`
	AlwaysRun      *bool     `json:"alwaysRun"`
}

// rawOperation accepts every field of DevPluginPreparationOperation. The decoder then refuses a Kotlin-executed
// operation by its kind, with a clear message, and not by an unknown field.
type rawOperation struct {
	ID            *string                `json:"id"`
	Kind          *string                `json:"kind"`
	Input         *pluginpack.Reference  `json:"input"`
	Inputs        []pluginpack.Reference `json:"inputs"`
	Output        *string                `json:"output"`
	Manifest      *string                `json:"manifest"`
	Excludes      []string               `json:"excludes"`
	Entry         json.RawMessage        `json:"entry"`
	Mode          json.RawMessage        `json:"mode"`
	Filter        *string                `json:"filter"`
	Overrides     json.RawMessage        `json:"overrides"`
	Library       json.RawMessage        `json:"library"`
	Resource      json.RawMessage        `json:"resource"`
	Binary        json.RawMessage        `json:"binary"`
	LibraryLayout json.RawMessage        `json:"libraryLayout"`
	LayoutAssets  *rawLayoutAssets       `json:"layoutAssets"`
	ArchiveSha256 json.RawMessage        `json:"archiveSha256"`
}

type rawLayoutAssets struct {
	Format *string                   `json:"format"`
	Root   *string                   `json:"root"`
	Assets *[]pluginpack.LayoutAsset `json:"assets"`
}

// Read decodes one plan file with the strictness of pluginpack.ReadJSON and expands its compact forms.
func Read(file string) (*File, error) {
	var raw rawFile
	if err := pluginpack.ReadJSON(file, &raw); err != nil {
		return nil, err
	}
	decoded, err := raw.decode()
	if err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	return decoded, nil
}

func (raw *rawFile) decode() (*File, error) {
	if raw.Plugin == nil || raw.Variant == nil || raw.LayoutSignature == nil || raw.Assets == nil {
		return nil, fmt.Errorf("a plan file requires plugin, variant, layoutSignature, and assets")
	}
	file := &File{Version: 1, Plugin: *raw.Plugin, Variant: *raw.Variant, LayoutSignature: *raw.LayoutSignature, PreparationRoots: raw.PreparationRoots}
	if raw.Version != nil {
		file.Version = *raw.Version
	}
	for index, asset := range *raw.Assets {
		decoded, err := asset.decode()
		if err != nil {
			return nil, fmt.Errorf("asset %d: %w", index, err)
		}
		file.Assets = append(file.Assets, decoded)
	}
	for index, preparation := range raw.Preparations {
		if preparation.ID == nil || preparation.Inputs == nil || preparation.Outputs == nil || preparation.ModelSignature == nil {
			return nil, fmt.Errorf("preparation %d requires id, inputs, outputs, and modelSignature", index)
		}
		file.Preparations = append(file.Preparations, Preparation{ID: *preparation.ID, Inputs: *preparation.Inputs, Outputs: *preparation.Outputs,
			ModelSignature: *preparation.ModelSignature, AlwaysRun: preparation.AlwaysRun != nil && *preparation.AlwaysRun})
	}
	for index, artifact := range raw.ReusableArtifacts {
		decoded, err := artifact.decode()
		if err != nil {
			return nil, fmt.Errorf("reusable artifact %d: %w", index, err)
		}
		file.ReusableArtifacts = append(file.ReusableArtifacts, decoded)
	}
	for index, operation := range raw.Operations {
		decoded, err := operation.decode()
		if err != nil {
			return nil, fmt.Errorf("operation %d: %w", index, err)
		}
		file.Operations = append(file.Operations, decoded)
	}
	return file, nil
}

// moduleJarRecipe is the recipe of a module's own jar, the commonest asset, which the compact form states as its module.
func moduleJarRecipe(module string) JarRecipe {
	return JarRecipe{Sources: []JarSource{{Input: module, Kind: "module", Filter: "module-v1"}}, Writer: JarWriter{Manifest: defaultManifest, MergeEntities: true}}
}

// moduleJarAsset is the asset of a module's own jar at its default destination.
func moduleJarAsset(module string) Asset {
	recipe := moduleJarRecipe(module)
	return Asset{Destination: "lib/modules/" + module + ".jar", Inputs: []string{module}, Recipe: &recipe, Mode: DefaultMode, Kind: "file", ClassPath: true, Scope: pluginpack.PluginScope}
}

func (raw *rawAsset) decode() (Asset, error) {
	asset := Asset{Mode: DefaultMode, Kind: "file", ClassPath: true, Scope: pluginpack.PluginScope}
	if raw.Mode != nil {
		asset.Mode = *raw.Mode
	}
	if raw.Kind != nil {
		asset.Kind = *raw.Kind
	}
	if raw.ClassPath != nil {
		asset.ClassPath = *raw.ClassPath
	}
	if raw.NormalizeTreeModes != nil {
		asset.NormalizeTreeModes = *raw.NormalizeTreeModes
	}
	if raw.Scope != nil {
		asset.Scope = *raw.Scope
	}
	if raw.Module != nil {
		plain := moduleJarAsset(*raw.Module)
		if raw.Destination != nil || raw.Inputs != nil || raw.Recipe != nil || raw.SymlinkTarget != nil ||
			asset.Mode != plain.Mode || asset.Kind != plain.Kind || asset.ClassPath != plain.ClassPath || asset.NormalizeTreeModes || asset.Scope != plain.Scope {
			return Asset{}, fmt.Errorf("a module jar asset states only its module: %s", *raw.Module)
		}
		return plain, nil
	}
	if raw.Destination == nil {
		return Asset{}, fmt.Errorf("a plan asset requires a destination or a module")
	}
	asset.Destination = *raw.Destination
	asset.SymlinkTarget = raw.SymlinkTarget
	if raw.Recipe != nil {
		recipe, err := raw.Recipe.decode()
		if err != nil {
			return Asset{}, fmt.Errorf("%s: %w", asset.Destination, err)
		}
		asset.Recipe = &recipe
	}
	switch {
	case raw.Inputs != nil:
		asset.Inputs = *raw.Inputs
	case asset.Recipe != nil:
		for _, source := range asset.Recipe.Sources {
			asset.Inputs = append(asset.Inputs, source.Input)
		}
	default:
		return Asset{}, fmt.Errorf("a plan asset without inputs requires a recipe: %s", asset.Destination)
	}
	if asset.Inputs == nil {
		asset.Inputs = []string{}
	}
	return asset, nil
}

var manifestPolicies = map[string]bool{"keep": true, "drop": true, "coverage-agent": true, "rewrite-boot-class-path": true, defaultManifest: true}

func (raw *rawJarRecipe) decode() (JarRecipe, error) {
	if raw.Sources == nil || len(*raw.Sources) == 0 {
		return JarRecipe{}, fmt.Errorf("a jar recipe requires ordered sources")
	}
	recipe := JarRecipe{Writer: JarWriter{Manifest: defaultManifest}}
	if writer := raw.Writer; writer != nil {
		if writer.Manifest != nil {
			recipe.Writer.Manifest = *writer.Manifest
		}
		recipe.Writer.MergeEntities = writer.MergeEntities != nil && *writer.MergeEntities
		recipe.Writer.DirectoryEntries = writer.DirectoryEntries != nil && *writer.DirectoryEntries
		recipe.Writer.RewriteBootClassPath = writer.RewriteBootClassPath != nil && *writer.RewriteBootClassPath
		if writer.OutputName != nil {
			recipe.Writer.OutputName = *writer.OutputName
		}
	}
	for _, raw := range *raw.Sources {
		if raw.Input == nil || raw.Kind == nil || raw.Filter == nil || *raw.Input == "" || *raw.Kind == "" || *raw.Filter == "" {
			return JarRecipe{}, fmt.Errorf("a jar source requires an input, a root kind, and a filter")
		}
		source := JarSource{Input: *raw.Input, Kind: *raw.Kind, Filter: *raw.Filter, Expansion: raw.Expansion, Options: raw.Options}
		if raw.Entry != nil {
			source.Entry = *raw.Entry
		}
		if manifest := raw.PreparedManifest; manifest != nil {
			if manifest.Version != nil && *manifest.Version != 1 {
				return JarRecipe{}, fmt.Errorf("unsupported prepared manifest version %d", *manifest.Version)
			}
			if manifest.SourceManifestPolicies == nil {
				return JarRecipe{}, fmt.Errorf("a prepared manifest requires sourceManifestPolicies")
			}
			decoded := &PreparedManifest{OriginalMeaningfulSourceCount: manifest.OriginalMeaningfulSourceCount, SourceManifestPolicies: *manifest.SourceManifestPolicies}
			if decoded.OriginalMeaningfulSourceCount != nil && *decoded.OriginalMeaningfulSourceCount < 0 {
				return JarRecipe{}, fmt.Errorf("invalid prepared source count")
			}
			if slices.ContainsFunc(decoded.SourceManifestPolicies, func(policy string) bool { return !manifestPolicies[policy] }) {
				return JarRecipe{}, fmt.Errorf("unsupported prepared manifest policies: %v", decoded.SourceManifestPolicies)
			}
			if decoded.OriginalMeaningfulSourceCount == nil && !slices.Equal(decoded.SourceManifestPolicies, []string{"keep"}) {
				return JarRecipe{}, fmt.Errorf("module patches require the keep policy")
			}
			if source.Kind != "prepared" || source.Filter != "prepared" || source.Entry != "" || len(source.Expansion) != 0 || len(source.Options) != 0 {
				return JarRecipe{}, fmt.Errorf("only a symbolic prepared source can declare a prepared manifest recipe")
			}
			source.PreparedManifest = decoded
		}
		recipe.Sources = append(recipe.Sources, source)
	}
	if recipe.Writer.RewriteBootClassPath && recipe.Writer.OutputName == "" {
		return JarRecipe{}, fmt.Errorf("a manifest rewrite requires the output name")
	}
	for _, source := range recipe.Sources {
		if slices.Contains(source.Options, "manifest=rewrite-boot-class-path") && recipe.Writer.OutputName == "" {
			return JarRecipe{}, fmt.Errorf("an explicit manifest rewrite requires the output name")
		}
	}
	return recipe, nil
}

func (raw *rawReusableArtifact) decode() (ReusableArtifact, error) {
	if raw.Label == nil {
		return ReusableArtifact{}, fmt.Errorf("a reusable artifact requires a label")
	}
	if (raw.Module == nil) == (raw.Recipe == nil) {
		return ReusableArtifact{}, fmt.Errorf("a reusable artifact states a module or a recipe: %s", *raw.Label)
	}
	artifact := ReusableArtifact{Label: *raw.Label, Mode: DefaultMode}
	if raw.Mode != nil {
		artifact.Mode = *raw.Mode
	}
	if raw.Module != nil {
		artifact.Recipe = moduleJarRecipe(*raw.Module)
		return artifact, nil
	}
	recipe, err := raw.Recipe.decode()
	if err != nil {
		return ReusableArtifact{}, fmt.Errorf("%s: %w", artifact.Label, err)
	}
	artifact.Recipe = recipe
	return artifact, nil
}

func (raw *rawOperation) decode() (Operation, error) {
	if raw.ID == nil || raw.Output == nil || raw.Manifest == nil {
		return Operation{}, fmt.Errorf("a preparation operation requires id, output, and manifest")
	}
	operation := Operation{ID: *raw.ID, Kind: moduleFilterKind, Input: raw.Input, Inputs: raw.Inputs, Output: *raw.Output, Manifest: *raw.Manifest, Excludes: raw.Excludes}
	if raw.Kind != nil {
		operation.Kind = *raw.Kind
	}
	if operation.Kind != moduleFilterKind && operation.Kind != layoutAssetsKind && operation.Kind != nativeSelectKind {
		return Operation{}, fmt.Errorf("operation %q has kind %q, which the Go packer does not execute; the plan needs a Kotlin preparation", operation.ID, operation.Kind)
	}
	var stated []string
	for name, value := range map[string]json.RawMessage{"entry": raw.Entry, "mode": raw.Mode, "overrides": raw.Overrides, "library": raw.Library,
		"resource": raw.Resource, "binary": raw.Binary, "libraryLayout": raw.LibraryLayout, "archiveSha256": raw.ArchiveSha256} {
		if value != nil {
			stated = append(stated, name)
		}
	}
	if raw.Filter != nil && operation.Kind != nativeSelectKind {
		stated = append(stated, "filter")
	}
	slices.Sort(stated)
	if len(stated) != 0 {
		return Operation{}, fmt.Errorf("operation %q of kind %s states the fields %s of another kind", operation.ID, operation.Kind, strings.Join(stated, ", "))
	}
	if operation.Kind == moduleFilterKind {
		if operation.Input == nil || len(operation.Inputs) != 0 || raw.LayoutAssets != nil {
			return Operation{}, fmt.Errorf("module-filter operation %q requires one input and no layout assets", operation.ID)
		}
		return operation, nil
	}
	if operation.Kind == nativeSelectKind {
		if operation.Input == nil || len(operation.Inputs) != 0 || raw.LayoutAssets != nil || len(operation.Excludes) != 0 ||
			operation.Manifest != "keep" || raw.Filter == nil || *raw.Filter != "library" {
			return Operation{}, fmt.Errorf("native-select operation %q requires one archive input, the keep manifest, the library filter, and no excludes or layout assets", operation.ID)
		}
		operation.Filter = *raw.Filter
		return operation, nil
	}
	if operation.Input != nil || raw.LayoutAssets == nil || raw.LayoutAssets.Format == nil || raw.LayoutAssets.Assets == nil {
		return Operation{}, fmt.Errorf("layout-assets operation %q requires layoutAssets with a format and assets, and no primary input", operation.ID)
	}
	if operation.Manifest != "keep" || len(operation.Excludes) != 0 {
		return Operation{}, fmt.Errorf("layout-assets operation %q requires the keep manifest and no excludes", operation.ID)
	}
	operation.LayoutAssets = &LayoutAssetPreparation{Format: *raw.LayoutAssets.Format, Assets: *raw.LayoutAssets.Assets}
	if raw.LayoutAssets.Root != nil {
		operation.LayoutAssets.Root = *raw.LayoutAssets.Root
	}
	return operation, nil
}
