package pluginpack

import (
	"encoding/json"
	"fmt"
	"maps"
	"path"
	"path/filepath"
	"slices"
	"strings"
	"unicode/utf8"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/jarpack"
	"jetbrains.com/content-module-packer/internal/javaglob"
	"jetbrains.com/content-module-packer/internal/nativelib"
)

// Execution contains a validated plan. Planning does not access the filesystem.
type Execution struct {
	recipe    Recipe
	artifacts map[string]Artifact
	libraries map[string]Library
	inputs    []Artifact
}

// Inputs returns the complete declared input set. Independent outputs are never included.
func (execution *Execution) Inputs() []Artifact {
	inputs := slices.Clone(execution.inputs)
	for index := range inputs {
		inputs[index] = cloneTreeArtifact(inputs[index])
	}
	return inputs
}

func cloneTreeArtifact(artifact Artifact) Artifact {
	if artifact.Tree != nil {
		tree := *artifact.Tree
		tree.Entries = slices.Clone(tree.Entries)
		artifact.Tree = &tree
	}
	return artifact
}

func (execution *Execution) validateTreeMetadata(artifact Artifact) error {
	tree := artifact.Tree
	if artifact.Kind != "directory" || tree.Version != TreeVersion || tree.Artifact != artifact.ID ||
		tree.Plugin != execution.recipe.Plugin || tree.LayoutSignature != execution.recipe.LayoutSignature || tree.RootMode > 0o777 || tree.Entries == nil ||
		tree.OmitRoot && len(tree.Entries) != 0 {
		return fmt.Errorf("invalid prepared tree version, ownership, or root mode for %q", artifact.ID)
	}
	entries := make(map[string]filemetadata.Entry, len(tree.Entries))
	assets := make([]Asset, 0, len(tree.Entries))
	var links []Operation
	for _, entry := range tree.Entries {
		if err := validateRelativePath(entry.RelativePath); err != nil {
			return err
		}
		if _, exists := entries[entry.RelativePath]; exists {
			return fmt.Errorf("duplicate prepared tree entry %q", entry.RelativePath)
		}
		entries[entry.RelativePath] = entry
		kind := "file"
		if entry.Type == "directory" {
			kind = "directory"
		}
		assets = append(assets, Asset{Destination: entry.RelativePath, Kind: kind})
		if entry.Type == "symlink" {
			if strings.ContainsAny(entry.SymlinkTarget, "\r\n") {
				return fmt.Errorf("unsafe prepared tree link %q", entry.RelativePath)
			}
			links = append(links, Operation{Kind: "symlink", Destination: entry.RelativePath, Target: entry.SymlinkTarget})
		}
	}
	if _, err := filemetadata.Merge(tree.Entries); err != nil {
		return err
	}
	for name := range entries {
		for parent := path.Dir(name); parent != "."; parent = path.Dir(parent) {
			if entries[parent].Type != "directory" {
				return fmt.Errorf("missing prepared tree directory %q", parent)
			}
		}
	}
	return validateSymlinks(assets, links)
}

func assetScope(asset Asset) string {
	if asset.Scope == "" {
		return PluginScope
	}
	return asset.Scope
}

func operationScope(operation Operation) string {
	if operation.Scope == "" {
		return PluginScope
	}
	return operation.Scope
}

func scopedDestination(scope, destination string) string {
	return scope + "\x00" + destination
}

// ValidateAssets applies the shared asset rules to one asset table. It checks the scope, the version, the reserved
// transport path, the plugin root target, and the relative path. It also checks the kind, the tree rules, the
// distribution rules, and the destination collisions. The identity folds a destination before the collision check.
// The directory-spellings check runs only when checkDirectorySpellings is true. The result maps each folded scoped
// destination to its asset. The packer calls it in Plan before it writes. The collector calls it again on the
// produced table in a second process, because the collector does not trust the producer.
func ValidateAssets(version int, assets []Asset, identity func(string) string, checkDirectorySpellings bool) (map[string]Asset, error) {
	validated := make(map[string]Asset, len(assets))
	spellings := make(map[string]string)
	hasDistributionAssets := false
	for _, asset := range assets {
		kind := assetKind(asset)
		scope := assetScope(asset)
		if scope != PluginScope && scope != DistributionScope {
			return nil, fmt.Errorf("unknown asset scope %q", asset.Scope)
		}
		if scope == DistributionScope && version != ScopedVersion {
			return nil, fmt.Errorf("distribution asset %q requires version 3", asset.Destination)
		}
		if version == ScopedVersion && scope == PluginScope {
			destination := identity(asset.Destination)
			transportRoot := identity(distributionTransportRoot)
			if destination == transportRoot || strings.HasPrefix(destination, transportRoot+"/") {
				return nil, fmt.Errorf("plugin asset %q uses the reserved distribution transport path", asset.Destination)
			}
		}
		hasDistributionAssets = hasDistributionAssets || scope == DistributionScope
		if asset.Destination == "" && (kind != "tree" || scope != PluginScope) {
			return nil, fmt.Errorf("only a declared tree can target the plugin root")
		}
		if asset.Destination != "" {
			if err := validateRelativePath(asset.Destination); err != nil {
				return nil, err
			}
		}
		key := scopedDestination(scope, identity(asset.Destination))
		if _, exists := validated[key]; exists {
			return nil, fmt.Errorf("destination collision at %q", asset.Destination)
		}
		validated[key] = asset
		if kind != "file" && kind != "directory" && kind != "tree" {
			return nil, fmt.Errorf("unknown asset kind %q", asset.Kind)
		}
		if asset.NormalizeTreeModes && kind != "tree" {
			return nil, fmt.Errorf("non-tree asset %q requests tree mode normalization", asset.Destination)
		}
		if kind == "tree" && (version < TreeVersion || asset.Producer != "remainder" || asset.ClassPath == nil || *asset.ClassPath) {
			return nil, fmt.Errorf("tree %q requires version 2 or 3, remainder ownership, and classPath false", asset.Destination)
		}
		if scope == DistributionScope && (asset.ClassPath == nil || *asset.ClassPath) {
			return nil, fmt.Errorf("distribution asset %q requires classPath false", asset.Destination)
		}
		for prefix := asset.Destination; checkDirectorySpellings && prefix != "" && prefix != "."; prefix = path.Dir(prefix) {
			spelling := scopedDestination(scope, filemetadata.PathIdentity(prefix))
			if previous, exists := spellings[spelling]; exists && previous != prefix {
				return nil, fmt.Errorf("conflicting directory spellings %q and %q", previous, prefix)
			}
			spellings[spelling] = prefix
		}
	}
	if version == ScopedVersion && !hasDistributionAssets {
		return nil, fmt.Errorf("version 3 requires a distribution asset")
	}
	return validated, nil
}

// Plan validates the recipe against the catalogue. The catalogue artifacts, in their order, become the execution inputs.
func Plan(recipe Recipe, catalogue Catalogue) (*Execution, error) {
	if recipe.Version < Version || recipe.Version > ScopedVersion || catalogue.Version != Version {
		return nil, fmt.Errorf("unsupported contract version; the recipe must use version 1, 2, or 3, the catalogue version 1")
	}
	if !validID(recipe.Plugin) || !validID(recipe.LayoutSignature) {
		return nil, fmt.Errorf("invalid plugin identity or layout signature")
	}
	hasTrees := slices.ContainsFunc(recipe.Assets, func(asset Asset) bool { return assetKind(asset) == "tree" })
	hasDirectories := hasTrees || slices.ContainsFunc(recipe.Assets, func(asset Asset) bool { return assetKind(asset) == "directory" })
	pathIdentity := strings.ToLower
	if hasDirectories {
		pathIdentity = filemetadata.PathIdentity
	}
	assets, err := ValidateAssets(recipe.Version, recipe.Assets, pathIdentity, hasDirectories)
	if err != nil {
		return nil, err
	}
	// The artifact of an independent asset is the module name of its reused jar. The module output can be a catalogue
	// input of the same chain under that name, so the two namespaces are not compared.
	for _, asset := range recipe.Assets {
		switch asset.Producer {
		case "independent":
			if !validID(asset.Artifact) || assetKind(asset) != "file" {
				return nil, fmt.Errorf("independent asset %q requires an artifact ID", asset.Destination)
			}
		case "remainder":
			if asset.Artifact != "" {
				return nil, fmt.Errorf("remainder asset %q must not name an independent artifact", asset.Destination)
			}
		default:
			return nil, fmt.Errorf("unknown producer %q", asset.Producer)
		}
	}
	for destination := range assets {
		scope, name, _ := strings.Cut(destination, "\x00")
		for parent := path.Dir(name); parent != "."; parent = path.Dir(parent) {
			if ancestor, exists := assets[scopedDestination(scope, parent)]; exists && assetKind(ancestor) == "file" {
				return nil, fmt.Errorf("destination collision between %q and %q", parent, name)
			}
		}
	}
	encoded, err := json.Marshal(recipe)
	if err != nil {
		return nil, err
	}
	execution := &Execution{artifacts: make(map[string]Artifact), libraries: make(map[string]Library)}
	if err := json.Unmarshal(encoded, &execution.recipe); err != nil {
		return nil, err
	}
	roots := make(map[string]bool)
	for _, artifact := range catalogue.Artifacts {
		artifact = cloneTreeArtifact(artifact)
		if _, exists := execution.artifacts[artifact.ID]; exists || !validID(artifact.ID) {
			return nil, fmt.Errorf("invalid or duplicate catalogue input %q", artifact.ID)
		}
		if artifact.Kind != "file" && artifact.Kind != "directory" {
			return nil, fmt.Errorf("unknown root kind %q", artifact.Kind)
		}
		if artifact.Tree != nil {
			if err := execution.validateTreeMetadata(artifact); err != nil {
				return nil, err
			}
		}
		if artifact.Root == "" || artifact.Root == "." || path.Clean(filepath.ToSlash(artifact.Root)) != filepath.ToSlash(artifact.Root) || !utf8.ValidString(artifact.Root) || strings.ContainsAny(artifact.Root, "\x00\r\n") {
			return nil, fmt.Errorf("invalid root for %q", artifact.ID)
		}
		absolute, err := filepath.Abs(artifact.Root)
		if err != nil || roots[absolute] {
			return nil, fmt.Errorf("invalid or duplicate root for %q", artifact.ID)
		}
		roots[absolute] = true
		execution.artifacts[artifact.ID] = artifact
		execution.inputs = append(execution.inputs, artifact)
	}
	for _, library := range catalogue.Libraries {
		if _, exists := execution.libraries[library.ID]; exists || !validID(library.ID) || len(library.Files) == 0 {
			return nil, fmt.Errorf("invalid or duplicate library %q", library.ID)
		}
		seen := make(map[Reference]bool)
		for _, reference := range library.Files {
			if err := execution.validateReference(&reference, nil); err != nil {
				return nil, err
			}
			if seen[reference] {
				return nil, fmt.Errorf("duplicate file in library %q", library.ID)
			}
			seen[reference] = true
		}
		library.Files = slices.Clone(library.Files)
		execution.libraries[library.ID] = library
	}
	used := make(map[string]bool)
	usedLibraries := make(map[string]bool)
	operations := make(map[string]Operation)
	for _, operation := range recipe.Operations {
		scope := operationScope(operation)
		key := scopedDestination(scope, pathIdentity(operation.Destination))
		asset, exists := assets[key]
		if _, duplicate := operations[key]; duplicate || !exists || asset.Destination != operation.Destination || assetScope(asset) != scope || asset.Producer != "remainder" {
			return nil, fmt.Errorf("conflicting or unowned remainder destination %q", operation.Destination)
		}
		if err := execution.validateOperation(operation, used, usedLibraries); err != nil {
			return nil, fmt.Errorf("%s: %w", operation.Destination, err)
		}
		writesTree := operation.Kind == "copy-tree" || operation.Kind == "layout-tree" || operation.Kind == "native-tree"
		if (assetKind(asset) == "directory") != (operation.Kind == "directory") || (assetKind(asset) == "tree") != writesTree {
			return nil, fmt.Errorf("stale asset kind at %q", operation.Destination)
		}
		if asset.NormalizeTreeModes != (writesTree && operation.Mode == 0o644) {
			return nil, fmt.Errorf("stale tree mode policy at %q", operation.Destination)
		}
		operations[key] = operation
	}
	for _, asset := range recipe.Assets {
		key := scopedDestination(assetScope(asset), pathIdentity(asset.Destination))
		if _, exists := operations[key]; asset.Producer == "remainder" && !exists {
			return nil, fmt.Errorf("missing remainder operation for %q", asset.Destination)
		}
	}
	if len(used) != len(execution.artifacts) || len(usedLibraries) != len(execution.libraries) {
		return nil, fmt.Errorf("stale ownership: catalogue contains unused inputs or libraries")
	}
	if !hasTrees {
		if err := validateSymlinks(recipe.Assets, recipe.Operations); err != nil {
			return nil, err
		}
	}
	return execution, nil
}

func (execution *Execution) validateOperation(operation Operation, used, usedLibraries map[string]bool) error {
	if operation.Mode > 0o777 {
		return fmt.Errorf("invalid file mode %o", operation.Mode)
	}
	if operation.Layout != nil && operation.Kind != "layout-tree" && operation.Kind != "layout-file" {
		return fmt.Errorf("only a layout-tree or a layout-file operation carries layout assets")
	}
	if operation.Native != nil && operation.Kind != "native-tree" {
		return fmt.Errorf("only a native-tree operation carries a native target")
	}
	switch operation.Kind {
	case "native-tree":
		if execution.recipe.Version < TreeVersion || len(operation.Sources) != 0 || operation.Options != nil || operation.Target != "" ||
			operation.Mode != 0 && operation.Mode != 0o644 || operation.Input == nil || operation.Input.Path != "" || operation.Native == nil {
			return fmt.Errorf("native-tree requires version 2 or 3, one archive input, and a native target without file or jar options")
		}
		if !nativelib.ValidFamily(nativelib.Family(operation.Native.OS)) || !nativelib.ValidArch(nativelib.Arch(operation.Native.Arch)) {
			return fmt.Errorf("unknown native target %s_%s", operation.Native.OS, operation.Native.Arch)
		}
		artifact, exists := execution.artifacts[operation.Input.Artifact]
		if !exists || artifact.Kind != "file" {
			return fmt.Errorf("native-tree requires a declared archive file")
		}
		used[artifact.ID] = true
	case "layout-tree":
		if execution.recipe.Version < TreeVersion || len(operation.Sources) != 0 || operation.Options != nil || operation.Target != "" ||
			operation.Mode != 0 && operation.Mode != 0o644 || operation.Input != nil || operation.Layout == nil {
			return fmt.Errorf("layout-tree requires version 2 or 3 and layout assets without an input, file, or jar options")
		}
		return execution.validateLayout(operation.Layout, layoutTreeFormat, used)
	case "layout-file":
		if len(operation.Sources) != 0 || operation.Options != nil || operation.Target != "" || operation.Input != nil || operation.Layout == nil {
			return fmt.Errorf("layout-file requires layout assets without an input, a link target, or jar options")
		}
		if len(operation.Layout.Assets) != 1 || operation.Layout.Assets[0].Destination != operation.Destination {
			return fmt.Errorf("layout-file requires one layout asset at its destination")
		}
		return execution.validateLayout(operation.Layout, layoutFileFormat, used)
	case "copy-tree":
		if execution.recipe.Version < TreeVersion || len(operation.Sources) != 0 || operation.Options != nil || operation.Target != "" ||
			operation.Mode != 0 && operation.Mode != 0o644 || operation.Input == nil || operation.Input.Path != "" {
			return fmt.Errorf("copy-tree requires version 2 or 3 and one directory root without file or jar options")
		}
		artifact, exists := execution.artifacts[operation.Input.Artifact]
		if !exists || artifact.Kind != "directory" {
			return fmt.Errorf("copy-tree requires a declared directory artifact")
		}
		used[artifact.ID] = true
	case "directory":
		if operation.Input != nil || len(operation.Sources) != 0 || operation.Options != nil || operation.Target != "" {
			return fmt.Errorf("directory operation must not contain file inputs, jar options, or a link target")
		}
	case "jar":
		if operation.Input != nil || operation.Target != "" || operation.Options == nil {
			return fmt.Errorf("jar operation requires options, not an input or link target")
		}
		switch jarpack.DirectoryMode(operation.Options.Directories) {
		case jarpack.DirectoriesNone, jarpack.DirectoriesResources, jarpack.DirectoriesAll:
		default:
			return fmt.Errorf("unknown directory mode %q", operation.Options.Directories)
		}
		for _, source := range operation.Sources {
			if err := execution.validateSource(source, used, usedLibraries); err != nil {
				return err
			}
		}
	case "copy":
		if len(operation.Sources) != 0 || operation.Options != nil || operation.Target != "" {
			return fmt.Errorf("copy operation contains jar options or a link target")
		}
		return execution.validateReference(operation.Input, used)
	case "symlink":
		if len(operation.Sources) != 0 || operation.Options != nil || operation.Input != nil || operation.Mode != 0 {
			return fmt.Errorf("symlink operation contains file or jar options")
		}
		if operation.Target == "" || !utf8.ValidString(operation.Target) || path.IsAbs(operation.Target) || strings.ContainsAny(operation.Target, "\\:\x00\r\n") {
			return fmt.Errorf("unsafe symlink target %q", operation.Target)
		}
		target := path.Join(path.Dir(operation.Destination), operation.Target)
		if target == ".." || strings.HasPrefix(target, "../") {
			return fmt.Errorf("symlink target %q escapes its asset scope", operation.Target)
		}
	default:
		return fmt.Errorf("unknown operation %q", operation.Kind)
	}
	return nil
}

func validateSymlinks(assets []Asset, operations []Operation) error {
	scopes := make(map[string]bool)
	for _, asset := range assets {
		scopes[assetScope(asset)] = true
	}
	for _, operation := range operations {
		scopes[operationScope(operation)] = true
	}
	for scope := range scopes {
		var scopedAssets []Asset
		for _, asset := range assets {
			if assetScope(asset) == scope {
				scopedAssets = append(scopedAssets, asset)
			}
		}
		var scopedOperations []Operation
		for _, operation := range operations {
			if operationScope(operation) == scope {
				scopedOperations = append(scopedOperations, operation)
			}
		}
		if err := validateScopedSymlinks(scopedAssets, scopedOperations); err != nil {
			return err
		}
	}
	return nil
}

func validateScopedSymlinks(assets []Asset, operations []Operation) error {
	links := make(map[string]string)
	for _, operation := range operations {
		if operation.Kind == "symlink" {
			links[operation.Destination] = operation.Target
		}
	}
	if len(links) == 0 {
		return nil
	}
	if err := filemetadata.ValidateLinks(links); err != nil {
		return err
	}
	directories := map[string]bool{".": true}
	for _, asset := range assets {
		directories[asset.Destination] = assetKind(asset) == "directory"
		for parent := path.Dir(asset.Destination); parent != "."; parent = path.Dir(parent) {
			directories[parent] = true
		}
	}
	return ValidateLinkGraph(directories, links)
}

// ValidateLinkGraph checks the link graph of one directory tree. The directories map names every node and marks each
// directory true. The links map names each symlink with its target. It refuses a link cycle, a link through a
// non-directory, a link that escapes the root, and a target absent from the tree. It also refuses two names that
// differ only in case, a parent that is not a directory, and a directory cycle through links.
// The packer calls it on the planned symlinks. The collector calls it again on the produced inventory in a second
// process, because the collector does not trust the producer.
func ValidateLinkGraph(directories map[string]bool, links map[string]string) error {
	casing := make(map[string]string)
	for name := range directories {
		key := strings.ToLower(name)
		if previous, exists := casing[key]; exists && previous != name {
			return fmt.Errorf("ambiguous path casing in link graph: %q and %q", previous, name)
		}
		casing[key] = name
	}
	resolved := make(map[string]string)
	resolving := make(map[string]bool)
	var resolveLink func(string) (string, error)
	resolveLink = func(link string) (string, error) {
		if resolving[link] {
			return "", fmt.Errorf("symlink cycle at %q", link)
		}
		if target, exists := resolved[link]; exists {
			return target, nil
		}
		resolving[link] = true
		defer delete(resolving, link)
		current := path.Dir(link)
		for _, component := range strings.Split(links[link], "/") {
			if !directories[current] {
				return "", fmt.Errorf("symlink %q traverses a non-directory %q", link, current)
			}
			switch component {
			case "", ".":
				continue
			case "..":
				if current == "." {
					return "", fmt.Errorf("symlink %q escapes the plugin through %q", link, links[link])
				}
				current = path.Dir(current)
				continue
			}
			current = path.Join(current, component)
			if _, exists := directories[current]; !exists {
				return "", fmt.Errorf("unresolved symlink target %q at %q", links[link], current)
			}
			if _, isLink := links[current]; isLink {
				var err error
				current, err = resolveLink(current)
				if err != nil {
					return "", err
				}
			}
		}
		resolved[link] = current
		return current, nil
	}
	edges := make(map[string][]string)
	for name, directory := range directories {
		if name == "." {
			continue
		}
		parent := path.Dir(name)
		if !directories[parent] {
			return fmt.Errorf("missing directory %q in link graph", parent)
		}
		if directory {
			edges[parent] = append(edges[parent], name)
		}
	}
	for _, link := range slices.Sorted(maps.Keys(links)) {
		target, err := resolveLink(link)
		if err != nil {
			return err
		}
		if directories[target] {
			parent := path.Dir(link)
			edges[parent] = append(edges[parent], target)
		}
	}
	states := make(map[string]uint8)
	var visit func(string) error
	visit = func(directory string) error {
		switch states[directory] {
		case 1:
			return fmt.Errorf("symlink directory cycle at %q", directory)
		case 2:
			return nil
		}
		states[directory] = 1
		for _, child := range edges[directory] {
			if err := visit(child); err != nil {
				return err
			}
		}
		states[directory] = 2
		return nil
	}
	return visit(".")
}

func (execution *Execution) validateSource(source Source, used, usedLibraries map[string]bool) error {
	if source.Prepared != "" {
		artifact, exists := execution.artifacts[source.Prepared]
		if !exists || !validID(source.Prepared) || artifact.Kind != "directory" || (source.Kind != "entries" && source.Kind != "archive") {
			return fmt.Errorf("prepared source %q requires a declared directory and entries or archive kind", source.Prepared)
		}
		used[source.Prepared] = true
	}
	switch jarpack.ManifestMode(source.Manifest) {
	case jarpack.ManifestDrop, jarpack.ManifestKeep, jarpack.ManifestRewriteBootClassPath, jarpack.ManifestCoverageAgent:
	default:
		return fmt.Errorf("unknown manifest policy %q", source.Manifest)
	}
	if source.Layout != nil && source.Kind != "layout" {
		return fmt.Errorf("only a layout source carries layout assets")
	}
	switch source.Kind {
	case "layout":
		if source.Input != nil || source.Library != "" || source.Filter != "" || len(source.Excludes) != 0 || len(source.Entries) != 0 ||
			len(source.Overrides) != 0 || source.ReserveNatives || source.Layout == nil || jarpack.ManifestMode(source.Manifest) != jarpack.ManifestKeep {
			return fmt.Errorf("layout source requires layout assets and the keep manifest policy without archive or filter options")
		}
		return execution.validateLayout(source.Layout, layoutEntriesFormat, used)
	case "archive", "library":
		if len(source.Entries) != 0 {
			return fmt.Errorf("archive or library source cannot contain prepared entries")
		}
		if source.ReserveNatives && (source.Kind != "archive" || len(source.Overrides) != 0) {
			return fmt.Errorf("native reservation requires an archive source without overrides")
		}
		if _, err := sourceFilter(source); err != nil {
			return err
		}
		if source.Kind == "archive" {
			if source.Library != "" {
				return fmt.Errorf("archive source cannot name a library")
			}
			if err := execution.validateReference(source.Input, used); err != nil {
				return err
			}
		} else {
			library, exists := execution.libraries[source.Library]
			if !exists || source.Input != nil || len(source.Overrides) != 0 {
				return fmt.Errorf("unresolved or invalid library source %q", source.Library)
			}
			usedLibraries[source.Library] = true
			for _, reference := range library.Files {
				if err := execution.validateReference(&reference, used); err != nil {
					return err
				}
			}
		}
		names := make(map[string]bool)
		for _, override := range source.Overrides {
			if names[override.Name] || override.Name == jarpack.ManifestEntryName || override.Name == "META-INF/listOfEntities.txt" {
				return fmt.Errorf("conflicting or unsupported override %q", override.Name)
			}
			names[override.Name] = true
			if err := jarpack.ValidateEntryName(override.Name); err != nil {
				return err
			}
			switch override.Kind {
			case "replace":
				if err := execution.validateReference(override.Input, used); err != nil {
					return err
				}
			case "reserve":
				if override.Input != nil {
					return fmt.Errorf("reservation cannot have an input")
				}
			default:
				return fmt.Errorf("unknown override %q", override.Kind)
			}
		}
	case "entries":
		if source.Input != nil || source.Library != "" || source.Filter != "" || len(source.Excludes) != 0 || len(source.Overrides) != 0 || source.ReserveNatives {
			return fmt.Errorf("prepared entries cannot contain archive or filter options")
		}
		for _, entry := range source.Entries {
			if err := jarpack.ValidateEntryName(entry.Name); err != nil {
				return err
			}
			switch entry.Kind {
			case "file", "patch":
				if err := execution.validateReference(entry.Input, used); err != nil {
					return err
				}
			case "reserve":
				if entry.Input != nil {
					return fmt.Errorf("reservation cannot have an input")
				}
			default:
				return fmt.Errorf("unknown prepared entry %q", entry.Kind)
			}
		}
	default:
		return fmt.Errorf("unknown source kind %q", source.Kind)
	}
	return nil
}

func (execution *Execution) validateReference(reference *Reference, used map[string]bool) error {
	if reference == nil {
		return fmt.Errorf("missing input reference")
	}
	artifact, exists := execution.artifacts[reference.Artifact]
	if !exists {
		return fmt.Errorf("unresolved input %q", reference.Artifact)
	}
	if artifact.Tree != nil {
		if slices.ContainsFunc(execution.recipe.Operations, func(operation Operation) bool {
			return operation.Kind == "copy-tree" && operation.Input != nil && operation.Input.Artifact == artifact.ID
		}) {
			return fmt.Errorf("prepared tree %q requires copy-tree ownership", artifact.ID)
		}
		if !slices.ContainsFunc(artifact.Tree.Entries, func(entry filemetadata.Entry) bool {
			return entry.RelativePath == reference.Path && entry.Type == "file"
		}) {
			return fmt.Errorf("prepared input %q must name a metadata file: %s", artifact.ID, reference.Path)
		}
	}
	if artifact.Kind == "file" {
		if reference.Path != "" {
			return fmt.Errorf("file input %q cannot have a relative path", artifact.ID)
		}
	} else if err := validateRelativePath(reference.Path); err != nil {
		return err
	}
	if used != nil {
		used[artifact.ID] = true
	}
	return nil
}

// layoutFormat is the shape a layout payload writes: a tree under one root, the file entries of one jar, or one file.
type layoutFormat string

const (
	layoutTreeFormat    layoutFormat = "tree"
	layoutEntriesFormat layoutFormat = "entries"
	layoutFileFormat    layoutFormat = "file"
)

// validateLayout applies the operation rules of the Kotlin generator to a layout payload without reading the filesystem.
// Every input must resolve. An input is a directory when it names a raw directory artifact without a path.
func (execution *Execution) validateLayout(layout *LayoutAssets, format layoutFormat, used map[string]bool) error {
	if len(layout.Assets) == 0 {
		return fmt.Errorf("layout assets require at least one asset")
	}
	kinds := make([]string, 0, len(layout.Inputs))
	for _, reference := range layout.Inputs {
		kind, err := execution.validateLayoutInput(reference, used)
		if err != nil {
			return err
		}
		kinds = append(kinds, kind)
	}
	for _, asset := range layout.Assets {
		if err := validateLayoutAsset(asset, format, kinds); err != nil {
			return fmt.Errorf("layout asset %q: %w", asset.Destination, err)
		}
	}
	return nil
}

func (execution *Execution) validateLayoutInput(reference Reference, used map[string]bool) (string, error) {
	artifact, exists := execution.artifacts[reference.Artifact]
	if !exists {
		return "", fmt.Errorf("unresolved layout input %q", reference.Artifact)
	}
	if artifact.Kind == "directory" && reference.Path == "" {
		if artifact.Tree != nil {
			return "", fmt.Errorf("layout input %q must be a raw directory", reference.Artifact)
		}
		used[artifact.ID] = true
		return "directory", nil
	}
	if err := execution.validateReference(&reference, used); err != nil {
		return "", err
	}
	return "file", nil
}

func validateLayoutAsset(asset LayoutAsset, format layoutFormat, kinds []string) error {
	if asset.Mode > 0o777 {
		return fmt.Errorf("invalid mode %o", asset.Mode)
	}
	kind := ""
	if asset.Transform != nil {
		kind = asset.Transform.Kind
	}
	for _, index := range asset.Sources {
		if index < 0 || index >= len(kinds) {
			return fmt.Errorf("invalid source index %d", index)
		}
	}
	sourcesAre := func(kind string) bool {
		return !slices.ContainsFunc(asset.Sources, func(index int) bool { return kinds[index] != kind })
	}
	transform := asset.Transform
	if asset.Destination == "" {
		// An entry asset writes its output root when every entry brings its own relative path: a mapped tree, an
		// extracted archive, a gzip archive, or a copied directory.
		expands := kind == "tree-map" || kind == "archive-tree" || kind == "gzip-xml-archive" ||
			transform == nil && len(asset.Sources) == 1 && sourcesAre("directory")
		if format != layoutTreeFormat && !expands {
			return fmt.Errorf("only a tree, a mapped entry asset, an extracted archive, a gzip archive, or a copied directory can use its output root")
		}
	} else if err := validateRelativePath(asset.Destination); err != nil {
		return err
	}
	if format == layoutFileFormat && (kind != "" && kind != "inline-text" || transform == nil && !sourcesAre("file")) {
		return fmt.Errorf("a layout file is a plain copy of one file or an inline text")
	}
	if transform == nil {
		if len(asset.Sources) != 1 {
			return fmt.Errorf("a plain copy requires one source")
		}
		return nil
	}
	if !layoutTransforms[kind] {
		return fmt.Errorf("unknown or unsupported layout transform %q", kind)
	}
	if transform.StripComponents < 0 {
		return fmt.Errorf("negative strip count")
	}
	if kind != "tree-map" && (len(transform.Excludes) != 0 || len(transform.DirectoryExcludes) != 0) {
		return fmt.Errorf("layout excludes require tree-map")
	}
	for _, patterns := range [][]string{transform.Excludes, transform.DirectoryExcludes} {
		if _, err := compileLayoutExcludes(patterns); err != nil {
			return err
		}
	}
	for _, mapping := range transform.Mappings {
		if mapping.StripComponents < 0 {
			return fmt.Errorf("negative mapping strip count")
		}
		if mapping.Destination != "" {
			if err := validateRelativePath(mapping.Destination); err != nil {
				return err
			}
		}
		if _, err := javaglob.Compile(mappingPattern(mapping)); err != nil {
			return fmt.Errorf("invalid mapping pattern: %w", err)
		}
	}
	switch kind {
	case "archive-tree":
		if len(asset.Sources) != 1 || transform.Text != "" || !sourcesAre("file") {
			return fmt.Errorf("archive-tree requires one archive file")
		}
	case "gzip-xml-archive":
		if format != layoutEntriesFormat || len(asset.Sources) == 0 || transform.StripComponents != 0 || transform.Text != "" ||
			len(transform.Mappings) != 0 || !sourcesAre("file") {
			return fmt.Errorf("gzip-xml-archive requires ordered archive files and jar entries")
		}
	case "inline-text":
		if len(asset.Sources) != 0 || transform.StripComponents != 0 || len(transform.Mappings) != 0 || strings.ContainsAny(transform.Text, "\r\n") {
			return fmt.Errorf("inline-text requires text without a newline and no inputs")
		}
	case "tree-map":
		if len(asset.Sources) == 0 || transform.StripComponents != 0 || transform.Text != "" || len(transform.Mappings) == 0 || !sourcesAre("directory") {
			return fmt.Errorf("tree-map requires ordered directories and mappings")
		}
	}
	return nil
}

func validID(value string) bool {
	return value != "" && utf8.ValidString(value) && strings.TrimSpace(value) == value && !strings.ContainsAny(value, "\x00\r\n")
}

func assetKind(asset Asset) string {
	if asset.Kind == "" {
		return "file"
	}
	return asset.Kind
}

func validateRelativePath(value string) error {
	if err := jarpack.ValidateEntryName(value); err != nil {
		return fmt.Errorf("unsafe relative path %q", value)
	}
	for _, component := range strings.Split(value, "/") {
		if strings.TrimRight(component, ". ") != component || strings.ContainsAny(component, "<>\"|?*") || strings.IndexFunc(component, func(value rune) bool { return value < 0x20 }) >= 0 {
			return fmt.Errorf("unsafe path component %q", component)
		}
		base, _, _ := strings.Cut(strings.ToUpper(component), ".")
		if base == "CON" || base == "PRN" || base == "AUX" || base == "NUL" ||
			len(base) == 4 && (strings.HasPrefix(base, "COM") || strings.HasPrefix(base, "LPT")) && base[3] >= '1' && base[3] <= '9' {
			return fmt.Errorf("reserved path component %q", component)
		}
	}
	return nil
}

// sourceFilter selects the entries of one archive or library source.
// With excludes, the module filter is composed with the Java globs. META-INF/listOfEntities.txt is never excluded.
// The manifest policy stays with jarpack.
func sourceFilter(source Source) (func(string) bool, error) {
	var filter func(string) bool
	switch source.Filter {
	case "module":
		filter = jarpack.ModuleOutputNameFilter
	case "library":
		filter = jarpack.LibraryNameFilter
	case "all":
		filter = func(string) bool { return true }
	default:
		return nil, fmt.Errorf("unknown filter %q; custom filters require prepared entries", source.Filter)
	}
	if len(source.Excludes) == 0 {
		return filter, nil
	}
	if source.Kind != "archive" || source.Filter != "module" {
		return nil, fmt.Errorf("excludes require an archive source with the module filter")
	}
	matchers := make([]javaglob.Matcher, 0, len(source.Excludes))
	for _, pattern := range source.Excludes {
		matcher, err := javaglob.Compile(pattern)
		if err != nil {
			return nil, fmt.Errorf("invalid exclude: %w", err)
		}
		matchers = append(matchers, matcher)
	}
	return func(name string) bool {
		if name == "META-INF/listOfEntities.txt" {
			return true
		}
		if !filter(name) {
			return false
		}
		return !slices.ContainsFunc(matchers, func(matcher javaglob.Matcher) bool { return matcher.Match(name) })
	}, nil
}
