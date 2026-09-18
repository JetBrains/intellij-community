package pluginpack

import (
	"archive/zip"
	"fmt"
	"path/filepath"

	"jetbrains.com/content-module-packer/internal/jarpack"
	"jetbrains.com/content-module-packer/internal/nativelib"
)

// nativeEntries lists the native entries of one archive in central-directory order, each name once. An entry is
// native when the filter includes it and nativelib.IsNativeEntry accepts it. An archive without one is an error,
// because a native-select operation has no use for it.
func nativeEntries(archive *zip.ReadCloser, file string, filter func(string) bool) ([]string, error) {
	var names []string
	seen := make(map[string]bool)
	for _, entry := range archive.File {
		name := entry.Name
		if seen[name] || !filter(name) || !nativelib.IsNativeEntry(name) {
			continue
		}
		seen[name] = true
		names = append(names, name)
	}
	if len(names) == 0 {
		return nil, fmt.Errorf("native archive %s has no native entries", file)
	}
	return names, nil
}

// reserveNativeEntries adds a reservation for every native entry of the archive, so the jar keeps the other entries
// and the native-tree operation owns the natives.
func reserveNativeEntries(file string, filter func(string) bool, overrides map[string]jarpack.EntryOverride) error {
	archive, err := zip.OpenReader(file)
	if err != nil {
		return err
	}
	defer archive.Close()
	names, err := nativeEntries(archive, file, filter)
	if err != nil {
		return err
	}
	for _, name := range names {
		overrides[name] = jarpack.EntryOverride{Reserve: true}
	}
	return nil
}

// nativeTree writes the native entries of the operation's platform into a scratch directory and resolves it like a
// copy-tree. The library name comes from the catalogue root of the archive, the way getLibNameBySourceFile read it.
// An archive without an entry for the platform yields no operation, so the tree root is not written.
func (executor *layoutExecutor) nativeTree(operation Operation) ([]resolvedOperation, error) {
	artifact := executor.execution.artifacts[operation.Input.Artifact]
	file, err := executor.resolveFile(operation.Input)
	if err != nil {
		return nil, err
	}
	archive, err := zip.OpenReader(file)
	if err != nil {
		return nil, err
	}
	defer archive.Close()
	names, err := nativeEntries(archive, file, jarpack.LibraryNameFilter)
	if err != nil {
		return nil, err
	}
	matches, err := nativelib.Select(names, nativelib.Family(operation.Native.OS), nativelib.Arch(operation.Native.Arch))
	if err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	if len(matches) == 0 {
		return nil, nil
	}
	entries := make(map[string]*zip.File, len(archive.File))
	for _, entry := range archive.File {
		if _, exists := entries[entry.Name]; !exists {
			entries[entry.Name] = entry
		}
	}
	root, err := executor.scratch.directory("native")
	if err != nil {
		return nil, err
	}
	writer := newLayoutTreeWriter(root)
	libName := nativelib.LibNameFromFile(filepath.Base(artifact.Root))
	for _, match := range matches {
		relativePath, err := nativelib.RelativePath(libName, match.Arch, match.FileName(), match.Path)
		if err != nil {
			return nil, err
		}
		if _, claimed := writer.claimed[relativePath]; claimed {
			return nil, fmt.Errorf("%s: two native entries select %q", file, relativePath)
		}
		content, err := readZipEntry(entries[match.PathWithPrefix])
		if err != nil {
			return nil, fmt.Errorf("%s: %s: %w", file, match.PathWithPrefix, err)
		}
		mode := uint32(0o644)
		if nativelib.IsExecutable(match.Family, match.FileName()) {
			mode = 0o755
		}
		if err := writer.file(relativePath, content, mode); err != nil {
			return nil, err
		}
	}
	return resolveDirectoryTree(operation, root)
}
