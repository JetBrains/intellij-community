package pluginpack

import (
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"slices"
	"strings"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/jarpack"
)

type resolvedOperation struct {
	operation   Operation
	spec        jarpack.MergeSpec
	input       string
	sourceInfo  os.FileInfo
	expected    *filemetadata.Entry
	backingRoot string
}

// Write creates one plugin directory and a separate inventory. It does not read independent assets.
// The directory must be absent or empty. Failed writes leave no partial payload or inventory.
// Exclusive reservations reject filesystem aliases before any operation writes content.
func (execution *Execution) Write(outputDirectory, inventoryFile string) error {
	output, inventory, err := execution.outputPaths(outputDirectory, inventoryFile)
	if err != nil {
		return err
	}
	prepared, backingRoots, err := execution.resolvePreparedFiles()
	if err != nil {
		return err
	}
	scratch, err := newLayoutScratch(execution.recipe, output)
	if err != nil {
		return err
	}
	defer scratch.remove()
	operations, err := execution.resolveOperations(prepared, scratch)
	if err != nil {
		return err
	}
	for _, resolved := range operations {
		if resolved.backingRoot != "" {
			backingRoots = append(backingRoots, resolved.backingRoot)
		}
	}
	for _, backingRoot := range backingRoots {
		for _, destination := range []string{output, inventory} {
			overlap, err := overlappingPaths(destination, backingRoot)
			if err != nil {
				return err
			}
			if overlap {
				return fmt.Errorf("output overlaps prepared tree backing root %q", backingRoot)
			}
		}
	}
	if err := checkOutputNamespace(output, inventory); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(output), 0o755); err != nil {
		return err
	}
	if err := execution.checkAssetNamespace(filepath.Dir(output), operations); err != nil {
		return err
	}
	stage, err := os.MkdirTemp(filepath.Dir(output), ".plugin-remainder-*")
	if err != nil {
		return err
	}
	cleanup := func(root string) {
		directories := slices.Clone(operations)
		slices.SortFunc(directories, func(first, second resolvedOperation) int {
			return strings.Compare(first.operation.Destination, second.operation.Destination)
		})
		for _, resolved := range directories {
			if resolved.operation.Kind == "directory" {
				destination := TransportDestination(execution.recipe.Version, resolved.operation.Scope, resolved.operation.Destination)
				os.Chmod(filepath.Join(root, filepath.FromSlash(destination)), 0o755)
			}
		}
		os.RemoveAll(root)
	}
	defer cleanup(stage)
	if err := os.Chmod(stage, 0o755); err != nil {
		return err
	}
	destinations := make([]Asset, 0, len(operations))
	for _, resolved := range operations {
		kind := "file"
		if resolved.operation.Kind == "directory" {
			kind = "directory"
		}
		destinations = append(destinations, Asset{
			Destination: TransportDestination(execution.recipe.Version, resolved.operation.Scope, resolved.operation.Destination), Kind: kind,
		})
	}
	if err := reserveOutputs(stage, destinations); err != nil {
		return err
	}
	files := make([]filemetadata.Entry, 0, len(operations))
	record := func(resolved resolvedOperation, file filemetadata.Entry) error {
		if resolved.expected != nil {
			expected := *resolved.expected
			expected.RelativePath = file.RelativePath
			if file != expected {
				return fmt.Errorf("prepared tree entry changed while writing %q", resolved.operation.Destination)
			}
		}
		files = append(files, file)
		return nil
	}
	var links []layoutLink
	linkOperations := make(map[string]resolvedOperation)
	for _, resolved := range operations {
		operation := resolved.operation
		if operation.Kind == "directory" {
			continue
		}
		transportDestination := TransportDestination(execution.recipe.Version, operation.Scope, operation.Destination)
		destination := filepath.Join(stage, filepath.FromSlash(transportDestination))
		switch operation.Kind {
		case "jar":
			resolved.spec.Output = destination
			if _, err := resolved.spec.Merge(); err != nil {
				return fmt.Errorf("%s: %w", operation.Destination, err)
			}
		case "copy":
			if err := copyFile(resolved.input, destination); err != nil {
				return err
			}
		case "symlink":
			links = append(links, layoutLink{name: transportDestination, target: operation.Target})
			linkOperations[transportDestination] = resolved
			continue
		default:
			return fmt.Errorf("unknown operation %q", operation.Kind)
		}
		// The inspection runs before the chmod, because a declared mode can deny the read.
		file, err := filemetadata.Inspect(destination, transportDestination)
		if err != nil {
			return err
		}
		mode := operation.Mode
		if mode == 0 {
			if resolved.expected != nil {
				mode = resolved.expected.Mode
			} else if resolved.sourceInfo != nil {
				mode = filemetadata.Permissions(resolved.sourceInfo)
			} else {
				mode = 0o644
			}
		}
		if err := os.Chmod(destination, os.FileMode(mode)); err != nil {
			return err
		}
		// The inventory records the mode the packer set. POSIX reads the same bits back, and NTFS stores none.
		file.Mode, file.Executable = mode, mode&0o111 != 0
		if err := record(resolved, file); err != nil {
			return err
		}
	}
	// The links come after every file and in dependency order, so each link finds the kind of its target.
	orderedLinks, err := orderLinks(links)
	if err != nil {
		return err
	}
	for _, link := range orderedLinks {
		destination := filepath.Join(stage, filepath.FromSlash(link.name))
		if err := os.Remove(destination); err != nil {
			return err
		}
		if err := os.Symlink(link.target, destination); err != nil {
			return err
		}
		file, err := filemetadata.Inspect(destination, link.name)
		if err != nil {
			return err
		}
		if err := record(linkOperations[link.name], file); err != nil {
			return err
		}
	}
	directoryOperations := slices.Clone(operations)
	slices.SortFunc(directoryOperations, func(first, second resolvedOperation) int {
		return strings.Compare(second.operation.Destination, first.operation.Destination)
	})
	for _, resolved := range directoryOperations {
		operation := resolved.operation
		if operation.Kind != "directory" {
			continue
		}
		transportDestination := TransportDestination(execution.recipe.Version, operation.Scope, operation.Destination)
		destination := filepath.Join(stage, filepath.FromSlash(transportDestination))
		mode := operation.Mode
		if mode == 0 {
			if resolved.expected != nil {
				mode = resolved.expected.Mode
			} else if resolved.sourceInfo != nil {
				mode = filemetadata.Permissions(resolved.sourceInfo)
			} else {
				mode = 0o755
			}
		}
		if err := os.Chmod(destination, os.FileMode(mode)); err != nil {
			return err
		}
		entry, err := filemetadata.Inspect(destination, transportDestination)
		if err != nil {
			return err
		}
		entry.Mode = mode
		if resolved.expected != nil {
			expected := *resolved.expected
			expected.RelativePath = transportDestination
			if entry != expected {
				return fmt.Errorf("prepared tree directory changed while writing %q", operation.Destination)
			}
		}
		files = append(files, entry)
	}
	if err := os.MkdirAll(filepath.Dir(inventory), 0o755); err != nil {
		return err
	}
	metadata, err := os.CreateTemp(filepath.Dir(inventory), ".plugin-inventory-*")
	if err != nil {
		return err
	}
	defer os.Remove(metadata.Name())
	if err := metadata.Close(); err != nil {
		return err
	}
	if err := filemetadata.Write(metadata.Name(), files); err != nil {
		return err
	}
	if err := os.Chmod(metadata.Name(), 0o644); err != nil {
		return err
	}
	if err := scratch.remove(); err != nil {
		return err
	}
	if err := checkEmptyDirectory(output); err != nil {
		return err
	}
	if err := os.Remove(output); err != nil && !os.IsNotExist(err) {
		return err
	}
	if err := os.Rename(stage, output); err != nil {
		return err
	}
	if err := os.Rename(metadata.Name(), inventory); err != nil {
		cleanup(output)
		return err
	}
	return nil
}

func (execution *Execution) checkAssetNamespace(parent string, operations []resolvedOperation) error {
	hasIndependent := false
	destinations := resolvedAssets(operations)
	for _, asset := range execution.recipe.Assets {
		hasIndependent = hasIndependent || asset.Producer == "independent"
		if asset.Producer == "independent" {
			destinations = append(destinations, asset)
		}
	}
	for index := range destinations {
		destinations[index].Destination = TransportDestination(execution.recipe.Version, destinations[index].Scope, destinations[index].Destination)
	}
	if !hasIndependent || len(execution.recipe.Operations) == 0 {
		return nil
	}
	probe, err := os.MkdirTemp(parent, ".plugin-namespace-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(probe)
	return reserveOutputs(probe, destinations)
}

func reserveOutputs(root string, destinations []Asset) error {
	directories := map[string]bool{".": true}
	for _, asset := range destinations {
		destination := asset.Destination
		parentPath := path.Dir(destination)
		if assetKind(asset) == "directory" {
			parentPath = destination
		}
		parent := "."
		for _, component := range strings.Split(parentPath, "/") {
			parent = path.Join(parent, component)
			if directories[parent] {
				continue
			}
			if err := os.Mkdir(filepath.Join(root, filepath.FromSlash(parent)), 0o755); err != nil {
				return fmt.Errorf("conflicting output directory %q: %w", parent, err)
			}
			directories[parent] = true
		}
		if assetKind(asset) == "directory" {
			continue
		}
		file, err := os.OpenFile(filepath.Join(root, filepath.FromSlash(destination)), os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
		if err != nil {
			return fmt.Errorf("conflicting output destination %q: %w", destination, err)
		}
		if err := file.Close(); err != nil {
			return err
		}
	}
	return nil
}

func (execution *Execution) resolvePreparedFiles() (map[Reference]string, []string, error) {
	cache := make(map[Reference]string)
	var backingRoots []string
	copiedTrees := make(map[string]bool)
	for _, operation := range execution.recipe.Operations {
		if operation.Kind == "copy-tree" {
			copiedTrees[operation.Input.Artifact] = true
		}
	}
	for _, artifact := range execution.inputs {
		if artifact.Tree == nil || copiedTrees[artifact.ID] {
			continue
		}
		entries, err := resolveOwnedTree(Operation{}, artifact)
		if err != nil {
			return nil, nil, fmt.Errorf("input %s: %w", artifact.ID, err)
		}
		for _, entry := range entries {
			if entry.operation.Kind == "copy" {
				cache[Reference{Artifact: artifact.ID, Path: entry.expected.RelativePath}] = entry.input
			}
			if entry.backingRoot != "" {
				backingRoots = append(backingRoots, entry.backingRoot)
			}
		}
	}
	return cache, backingRoots, nil
}

func (execution *Execution) resolveOperations(cache map[Reference]string, scratch *layoutScratch) ([]resolvedOperation, error) {
	resolve := func(reference *Reference) (string, error) {
		if reference == nil {
			return "", nil
		}
		if file, exists := cache[*reference]; exists {
			return file, nil
		}
		file, err := execution.resolve(*reference)
		if err != nil {
			return "", err
		}
		cache[*reference] = file
		return file, nil
	}
	layouts := &layoutExecutor{execution: execution, resolveFile: resolve, scratch: scratch, transportRoots: make(map[string]string)}
	operations := make([]resolvedOperation, 0, len(execution.recipe.Operations))
	for _, operation := range execution.recipe.Operations {
		if operation.Kind == "copy-tree" || operation.Kind == "layout-tree" {
			var tree []resolvedOperation
			var err error
			if operation.Kind == "copy-tree" {
				tree, err = execution.resolveTree(operation)
			} else {
				tree, err = layouts.tree(operation)
			}
			if err != nil {
				return nil, fmt.Errorf("%s: %w", operation.Destination, err)
			}
			operations = append(operations, tree...)
			continue
		}
		resolved := resolvedOperation{operation: operation}
		var err error
		resolved.input, err = resolve(operation.Input)
		if err != nil {
			return nil, err
		}
		if operation.Kind == "copy" {
			resolved.sourceInfo, err = os.Stat(resolved.input)
			if err != nil {
				return nil, err
			}
		}
		if operation.Kind == "jar" {
			resolved.spec = jarpack.MergeSpec{
				MergeEntities: operation.Options.MergeEntities, DirectoryMode: jarpack.DirectoryMode(operation.Options.Directories),
				VerifyCRC: operation.Options.VerifyCRC, ValidateEntryNames: true,
			}
			for _, source := range operation.Sources {
				manifest := jarpack.ManifestMode(source.Manifest)
				if source.Kind == "layout" {
					entries, err := layouts.entries(source)
					if err != nil {
						return nil, fmt.Errorf("%s: %w", operation.Destination, err)
					}
					resolved.spec.Sources = append(resolved.spec.Sources, entries...)
					continue
				}
				if source.Kind == "entries" {
					for _, entry := range source.Entries {
						file, err := resolve(entry.Input)
						if err != nil {
							return nil, err
						}
						resolved.spec.Sources = append(resolved.spec.Sources, jarpack.Source{
							Path: file, Name: entry.Name, Patch: entry.Kind == "patch", Reserve: entry.Kind == "reserve", Manifest: manifest,
						})
					}
					continue
				}
				filter, err := sourceFilter(source)
				if err != nil {
					return nil, err
				}
				var references []Reference
				if source.Kind == "library" {
					references = execution.libraries[source.Library].Files
				} else {
					references = []Reference{*source.Input}
				}
				for _, reference := range references {
					file, err := resolve(&reference)
					if err != nil {
						return nil, err
					}
					archive := jarpack.Source{Path: file, Filter: filter, Manifest: manifest, EntryOverrides: make(map[string]jarpack.EntryOverride)}
					for _, override := range source.Overrides {
						file, err := resolve(override.Input)
						if err != nil {
							return nil, err
						}
						archive.EntryOverrides[override.Name] = jarpack.EntryOverride{Path: file, Reserve: override.Kind == "reserve"}
					}
					resolved.spec.Sources = append(resolved.spec.Sources, archive)
				}
			}
		}
		operations = append(operations, resolved)
	}
	assets := resolvedAssets(operations)
	for _, asset := range execution.recipe.Assets {
		if asset.Producer == "independent" {
			assets = append(assets, asset)
		}
	}
	links := make([]Operation, 0, len(operations))
	for _, resolved := range operations {
		links = append(links, resolved.operation)
	}
	if err := validateSymlinks(assets, links); err != nil {
		return nil, err
	}
	return operations, nil
}

func resolvedAssets(operations []resolvedOperation) []Asset {
	assets := make([]Asset, 0, len(operations))
	for _, resolved := range operations {
		kind := "file"
		if resolved.operation.Kind == "directory" {
			kind = "directory"
		}
		assets = append(assets, Asset{Destination: resolved.operation.Destination, Kind: kind, Scope: resolved.operation.Scope})
	}
	return assets
}

func (execution *Execution) resolveTree(operation Operation) ([]resolvedOperation, error) {
	artifact := execution.artifacts[operation.Input.Artifact]
	if artifact.Tree != nil {
		return resolveOwnedTree(operation, artifact)
	}
	return resolveDirectoryTree(operation, artifact.Root)
}

// resolveDirectoryTree walks one raw directory and turns every entry into a copy, directory, or symlink operation.
// It rejects setuid, setgid, and sticky bits, aliased entries, conflicting spellings, and unsafe links.
func resolveDirectoryTree(operation Operation, treeRoot string) ([]resolvedOperation, error) {
	info, err := os.Lstat(treeRoot)
	if err != nil || !info.IsDir() {
		return nil, fmt.Errorf("tree root is not a directory: %s", treeRoot)
	}
	root, err := evalSymlinks(treeRoot)
	if err != nil {
		return nil, err
	}
	root, err = filepath.Abs(root)
	if err != nil {
		return nil, err
	}
	var operations []resolvedOperation
	var localAssets []Asset
	var localLinks []Operation
	spellings := make(map[string]string)
	identities := make(map[int64][]os.FileInfo)
	backingRoot := ""
	err = filepath.WalkDir(root, func(source string, entry fs.DirEntry, walkError error) error {
		if walkError != nil {
			return walkError
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		relative, err := filepath.Rel(root, source)
		if err != nil {
			return err
		}
		name := filepath.ToSlash(relative)
		if name != "." {
			if err := validateRelativePath(name); err != nil {
				return err
			}
		}
		kind := "copy"
		assetKind := "file"
		target := ""
		switch {
		case info.IsDir():
			kind, assetKind = "directory", "directory"
		case info.Mode().IsRegular():
		case info.Mode()&os.ModeSymlink != 0:
			target, err = filemetadata.ReadLinkTarget(source)
			if err != nil {
				return err
			}
			if filepath.IsAbs(target) {
				source, info, backingRoot, err = resolveTransportFile(target, name, backingRoot)
				if err != nil {
					return err
				}
				target = ""
				break
			}
			kind = "symlink"
			if strings.ContainsAny(target, "\r\n") {
				return fmt.Errorf("unsafe tree link: %s", source)
			}
			localLinks = append(localLinks, Operation{Kind: "symlink", Destination: name, Target: target})
		default:
			return fmt.Errorf("unsupported tree entry: %s", source)
		}
		if info.Mode()&(os.ModeSetuid|os.ModeSetgid|os.ModeSticky) != 0 {
			return fmt.Errorf("unsupported tree mode: %s", source)
		}
		if kind != "symlink" {
			for _, previous := range identities[info.Size()] {
				if os.SameFile(info, previous) {
					return fmt.Errorf("aliased tree entry: %s", source)
				}
			}
			identities[info.Size()] = append(identities[info.Size()], info)
		}
		for prefix := name; prefix != "."; prefix = path.Dir(prefix) {
			identity := filemetadata.PathIdentity(prefix)
			if previous, exists := spellings[identity]; exists && previous != prefix {
				return fmt.Errorf("conflicting tree entries %q and %q", previous, prefix)
			}
			spellings[identity] = prefix
		}
		if name != "." {
			localAssets = append(localAssets, Asset{Destination: name, Kind: assetKind})
		}
		if name == "." && operation.Destination == "" {
			return nil
		}
		operations = append(operations, resolvedOperation{
			operation: Operation{
				Kind: kind, Destination: path.Join(operation.Destination, name), Scope: operation.Scope, Target: target,
				Mode: normalizedTreeEntryMode(operation.Mode, filemetadata.Permissions(info), kind),
			},
			input: source, sourceInfo: info,
		})
		return nil
	})
	if err != nil {
		return nil, err
	}
	if len(operations) != 0 {
		operations[0].backingRoot = backingRoot
	}
	if err := validateSymlinks(localAssets, localLinks); err != nil {
		return nil, err
	}
	return operations, nil
}

func resolveTransportEntry(target, relativePath, previousRoot string) (string, os.FileInfo, string, error) {
	root := filepath.Clean(target)
	for _, part := range slices.Backward(strings.Split(relativePath, "/")) {
		if filepath.Base(root) != part {
			return "", nil, "", fmt.Errorf("transport link path conflicts with tree entry %q", relativePath)
		}
		root = filepath.Dir(root)
	}
	return resolveTransportRootEntry(root, relativePath, previousRoot)
}

func normalizedTreeEntryMode(policyMode, sourceMode uint32, kind string) uint32 {
	if policyMode == 0 || kind == "symlink" {
		return 0
	}
	return sourceMode & 0o755
}

func resolveTransportRootEntry(root, relativePath, previousRoot string) (string, os.FileInfo, string, error) {
	info, err := os.Lstat(root)
	if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
		return "", nil, "", fmt.Errorf("transport backing root is not a real directory: %s", root)
	}
	root, err = evalSymlinks(root)
	if err != nil {
		return "", nil, "", err
	}
	if previousRoot != "" {
		previous, err := os.Stat(previousRoot)
		if err != nil {
			return "", nil, "", err
		}
		current, err := os.Stat(root)
		if err != nil {
			return "", nil, "", err
		}
		if !os.SameFile(previous, current) {
			return "", nil, "", fmt.Errorf("tree files have conflicting transport roots")
		}
	}
	source := filepath.Join(root, filepath.FromSlash(relativePath))
	for parent := filepath.Dir(source); within(root, parent); parent = filepath.Dir(parent) {
		info, err := os.Lstat(parent)
		if err != nil || !info.IsDir() || info.Mode()&os.ModeSymlink != 0 {
			return "", nil, "", fmt.Errorf("transport member parent is not a real directory: %s", parent)
		}
		if parent == root {
			break
		}
	}
	info, err = os.Lstat(source)
	if err != nil {
		return "", nil, "", fmt.Errorf("cannot inspect transport member %s: %w", source, err)
	}
	return source, info, root, nil
}

func resolveTransportFile(target, relativePath, previousRoot string) (string, os.FileInfo, string, error) {
	source, info, root, err := resolveTransportEntry(target, relativePath, previousRoot)
	if err != nil {
		return "", nil, "", err
	}
	if !info.Mode().IsRegular() || info.Mode()&(os.ModeSetuid|os.ModeSetgid|os.ModeSticky) != 0 {
		return "", nil, "", fmt.Errorf("transport member is not a regular file: %s", source)
	}
	return source, info, root, nil
}

func resolveOwnedTree(operation Operation, artifact Artifact) ([]resolvedOperation, error) {
	root, err := filepath.Abs(artifact.Root)
	if err != nil {
		return nil, err
	}
	checkDirectory := func(directory string) error {
		info, err := os.Lstat(directory)
		if os.IsNotExist(err) {
			return nil
		}
		if err != nil {
			return err
		}
		if !info.IsDir() {
			return fmt.Errorf("prepared tree directory is not a real directory: %s", directory)
		}
		return nil
	}
	if err := checkDirectory(root); err != nil {
		return nil, err
	}
	entries := slices.Clone(artifact.Tree.Entries)
	slices.SortFunc(entries, func(first, second filemetadata.Entry) int {
		return strings.Compare(first.RelativePath, second.RelativePath)
	})
	for _, entry := range entries {
		if entry.Type == "directory" {
			if err := checkDirectory(filepath.Join(root, filepath.FromSlash(entry.RelativePath))); err != nil {
				return nil, err
			}
		}
	}
	rootMode := artifact.Tree.RootMode
	if operation.Mode != 0 {
		rootMode &= 0o755
	}
	rootEntry := filemetadata.Entry{RelativePath: operation.Destination, Type: "directory", Mode: rootMode}
	var operations []resolvedOperation
	if !artifact.Tree.OmitRoot && operation.Destination != "" {
		operations = append(operations, resolvedOperation{
			operation: Operation{
				Kind: "directory", Destination: operation.Destination, Scope: operation.Scope,
				Mode: normalizedTreeEntryMode(operation.Mode, artifact.Tree.RootMode, "directory"),
			},
			expected: &rootEntry,
		})
	}
	identities := make(map[int64][]os.FileInfo)
	backingRoot := ""
	preparedFiles := make(map[string]string)
	for _, entry := range entries {
		if entry.Type != "file" {
			continue
		}
		source := filepath.Join(root, filepath.FromSlash(entry.RelativePath))
		info, err := os.Lstat(source)
		if err != nil {
			return nil, err
		}
		if info.Mode()&os.ModeSymlink != 0 {
			target, err := filemetadata.ReadLinkTarget(source)
			if err != nil {
				return nil, err
			}
			if !filepath.IsAbs(target) {
				return nil, fmt.Errorf("prepared file transport link must be absolute: %s", source)
			}
			source, info, backingRoot, err = resolveTransportFile(target, entry.RelativePath, backingRoot)
			if err != nil {
				return nil, err
			}
		}
		if !info.Mode().IsRegular() || info.Mode()&(os.ModeSetuid|os.ModeSetgid|os.ModeSticky) != 0 || info.Size() != entry.Size {
			return nil, fmt.Errorf("prepared file type, size, or mode conflicts with metadata: %s", source)
		}
		for _, previous := range identities[info.Size()] {
			if os.SameFile(info, previous) {
				return nil, fmt.Errorf("aliased prepared tree file: %s", source)
			}
		}
		identities[info.Size()] = append(identities[info.Size()], info)
		hash, err := filemetadata.HashFile(source)
		if err != nil {
			return nil, err
		}
		if hash != entry.Hash {
			return nil, fmt.Errorf("prepared file hash conflicts with metadata: %s", source)
		}
		preparedFiles[entry.RelativePath] = source
	}
	for _, entry := range entries {
		source := filepath.Join(root, filepath.FromSlash(entry.RelativePath))
		kind := "directory"
		switch entry.Type {
		case "file":
			kind = "copy"
			source = preparedFiles[entry.RelativePath]
		case "symlink":
			kind = "symlink"
			info, err := os.Lstat(source)
			dereferencedDirectory := false
			if err == nil && info.Mode()&os.ModeSymlink != 0 {
				target, err := filemetadata.ReadLinkTarget(source)
				if err != nil {
					return nil, err
				}
				if filepath.IsAbs(target) {
					source, info, backingRoot, err = resolveTransportEntry(target, entry.RelativePath, backingRoot)
					if err != nil {
						return nil, err
					}
				}
			} else {
				if err != nil && !os.IsNotExist(err) {
					return nil, err
				}
				if err == nil {
					if !info.IsDir() {
						return nil, fmt.Errorf("prepared link type conflicts with metadata: %s", source)
					}
					dereferencedDirectory = true
				}
				if backingRoot == "" {
					return nil, fmt.Errorf("prepared link type conflicts with metadata: %s", source)
				}
				source, info, _, err = resolveTransportRootEntry(backingRoot, entry.RelativePath, backingRoot)
				if err != nil {
					return nil, err
				}
			}
			if info.Mode()&os.ModeSymlink == 0 {
				return nil, fmt.Errorf("prepared link type conflicts with metadata: %s", source)
			}
			target, err := filemetadata.ReadLinkTarget(source)
			if err != nil {
				return nil, err
			}
			if filemetadata.CleanLinkTarget(target) != filemetadata.CleanLinkTarget(entry.SymlinkTarget) {
				return nil, fmt.Errorf("prepared link target conflicts with metadata: %s", source)
			}
			if dereferencedDirectory {
				targetInfo, err := os.Stat(source)
				if err != nil || !targetInfo.IsDir() {
					return nil, fmt.Errorf("prepared link target is not a directory: %s", source)
				}
			}
		}
		entry.RelativePath = path.Join(operation.Destination, entry.RelativePath)
		if mode := normalizedTreeEntryMode(operation.Mode, entry.Mode, kind); operation.Mode != 0 && kind != "symlink" {
			entry.Mode = mode
			entry.Executable = entry.Type != "directory" && mode&0o111 != 0
		}
		operations = append(operations, resolvedOperation{
			operation: Operation{
				Kind: kind, Destination: entry.RelativePath, Scope: operation.Scope, Target: entry.SymlinkTarget,
				Mode: normalizedTreeEntryMode(operation.Mode, entry.Mode, kind),
			},
			input: source, expected: &entry,
		})
	}
	if len(operations) != 0 {
		operations[0].backingRoot = backingRoot
	}
	return operations, nil
}

func (execution *Execution) resolve(reference Reference) (string, error) {
	artifact := execution.artifacts[reference.Artifact]
	root, err := filepath.Abs(artifact.Root)
	if err != nil {
		return "", fmt.Errorf("input %s: %w", artifact.ID, err)
	}
	root, err = evalSymlinks(root)
	if err != nil {
		return "", err
	}
	file := root
	if artifact.Kind == "directory" {
		info, err := os.Stat(root)
		if err != nil || !info.IsDir() {
			return "", fmt.Errorf("input %s is not a directory", artifact.ID)
		}
		file, err = evalSymlinks(filepath.Join(root, filepath.FromSlash(reference.Path)))
		if err != nil {
			return "", fmt.Errorf("input %s/%s: %w", artifact.ID, reference.Path, err)
		}
		if !within(root, file) {
			return "", fmt.Errorf("input %s/%s escapes its declared directory", artifact.ID, reference.Path)
		}
	}
	info, err := os.Stat(file)
	if err != nil || !info.Mode().IsRegular() {
		return "", fmt.Errorf("input %s/%s is not a regular file", artifact.ID, reference.Path)
	}
	return file, nil
}

func (execution *Execution) outputPaths(outputDirectory, inventoryFile string) (string, string, error) {
	if outputDirectory == "" || inventoryFile == "" {
		return "", "", fmt.Errorf("output directory and inventory file are required")
	}
	if err := checkEmptyDirectory(outputDirectory); err != nil {
		return "", "", err
	}
	if _, err := os.Lstat(inventoryFile); !os.IsNotExist(err) {
		return "", "", fmt.Errorf("inventory must not exist: %s", inventoryFile)
	}
	output, err := physicalPath(outputDirectory)
	if err != nil {
		return "", "", err
	}
	inventory, err := physicalPath(inventoryFile)
	if err != nil {
		return "", "", err
	}
	overlap, err := overlappingPaths(output, inventory)
	if err != nil {
		return "", "", err
	}
	if overlap {
		return "", "", fmt.Errorf("inventory must be outside the payload directory")
	}
	for _, artifact := range execution.inputs {
		root, err := physicalPath(artifact.Root)
		if err != nil {
			return "", "", err
		}
		for _, destination := range []string{output, inventory} {
			overlap, err := overlappingPaths(destination, root)
			if err != nil {
				return "", "", err
			}
			if overlap {
				return "", "", fmt.Errorf("output overlaps input %q", artifact.ID)
			}
		}
	}
	return output, inventory, nil
}

func physicalPath(file string) (string, error) {
	absolute, err := filepath.Abs(file)
	if err != nil {
		return "", err
	}
	resolved, err := evalSymlinks(absolute)
	if err == nil {
		return resolved, nil
	}
	if !os.IsNotExist(err) || filepath.Dir(absolute) == absolute {
		return "", err
	}
	parent, err := physicalPath(filepath.Dir(absolute))
	if err != nil {
		return "", err
	}
	return filepath.Join(parent, filepath.Base(absolute)), nil
}

func within(root, file string) bool {
	relative, err := filepath.Rel(root, file)
	return err == nil && relative != ".." && !strings.HasPrefix(relative, ".."+string(filepath.Separator))
}

func overlappingPaths(first, second string) (bool, error) {
	if within(first, second) || within(second, first) {
		return true, nil
	}
	firstAncestor, firstSuffix, firstInfo, err := existingPath(first)
	if err != nil {
		return false, err
	}
	secondAncestor, secondSuffix, secondInfo, err := existingPath(second)
	if err != nil {
		return false, err
	}
	if os.SameFile(firstInfo, secondInfo) {
		return within(firstSuffix, secondSuffix) || within(secondSuffix, firstSuffix), nil
	}
	for _, boundary := range []struct {
		suffix string
		info   os.FileInfo
		other  string
	}{{firstSuffix, firstInfo, secondAncestor}, {secondSuffix, secondInfo, firstAncestor}} {
		if boundary.suffix != "." {
			continue
		}
		for current := boundary.other; ; current = filepath.Dir(current) {
			info, err := os.Stat(current)
			if err != nil {
				return false, err
			}
			if os.SameFile(boundary.info, info) {
				return true, nil
			}
			if filepath.Dir(current) == current {
				break
			}
		}
	}
	return false, nil
}

func existingPath(file string) (string, string, os.FileInfo, error) {
	suffix := "."
	for {
		info, err := os.Stat(file)
		if err == nil {
			return file, suffix, info, nil
		}
		if !os.IsNotExist(err) || filepath.Dir(file) == file {
			return "", "", nil, err
		}
		suffix = filepath.Join(filepath.Base(file), suffix)
		file = filepath.Dir(file)
	}
}

func checkOutputNamespace(output, inventory string) error {
	ancestor, outputSuffix, outputInfo, err := existingPath(output)
	if err != nil {
		return err
	}
	_, inventorySuffix, inventoryInfo, err := existingPath(inventory)
	if err != nil {
		return err
	}
	if !os.SameFile(outputInfo, inventoryInfo) || outputSuffix == "." || inventorySuffix == "." {
		return nil
	}
	probe, err := os.MkdirTemp(ancestor, ".plugin-output-boundary-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(probe)
	outputPath, inventoryPath := filepath.Join(probe, outputSuffix), filepath.Join(probe, inventorySuffix)
	if err := os.MkdirAll(outputPath, 0o755); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(inventoryPath), 0o755); err != nil {
		return err
	}
	file, err := os.OpenFile(inventoryPath, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return fmt.Errorf("inventory must be outside the payload directory: %w", err)
	}
	if err := file.Close(); err != nil {
		return err
	}
	overlap, err := overlappingPaths(outputPath, inventoryPath)
	if err != nil {
		return err
	}
	if overlap {
		return fmt.Errorf("inventory must be outside the payload directory")
	}
	return nil
}

func checkEmptyDirectory(directory string) error {
	directory = filepath.Clean(directory)
	info, err := os.Lstat(directory)
	if os.IsNotExist(err) {
		return nil
	}
	if err != nil {
		return err
	}
	if !info.IsDir() {
		return fmt.Errorf("output is not a real directory: %s", directory)
	}
	entries, err := os.ReadDir(directory)
	if err != nil {
		return err
	}
	if len(entries) != 0 {
		return fmt.Errorf("output directory is not empty: %s", directory)
	}
	return nil
}

func copyFile(source, destination string) error {
	input, err := os.Open(source)
	if err != nil {
		return err
	}
	defer input.Close()
	output, err := os.OpenFile(destination, os.O_TRUNC|os.O_WRONLY, 0)
	if err != nil {
		return err
	}
	defer output.Close()
	if _, err := io.Copy(output, input); err != nil {
		return err
	}
	return output.Close()
}
