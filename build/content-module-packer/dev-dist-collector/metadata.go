package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path"
	"path/filepath"
	"slices"
	"strings"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

// metadataRecord names the inventory entry of one source. A file record's relativePath is the entry's key. A tree
// record's relativePath is the key of the tree's root directory, under which the inventory lists the files the packer
// wrote; the collector places those and reads nothing from the tree itself.
type metadataRecord struct {
	Source       string `json:"source"`
	Metadata     string `json:"metadata"`
	RelativePath string `json:"relativePath"`
	Tree         bool   `json:"tree"`
}

// treeMetadata is the inventory of one native tree: the entries under its root, keyed by their path below the root,
// and the inventory file they came from.
type treeMetadata struct {
	metadata string
	entries  []filemetadata.Entry
}

// attachMetadata pairs every collected file with its inventory entry, and replaces every tree record with the files
// its inventory names. The result is what the manifest lists, so a tree with no file under it contributes nothing.
func attachMetadata(files []sourcedFile, catalogue string) ([]sourcedFile, error) {
	data, err := os.ReadFile(catalogue)
	if err != nil {
		return nil, err
	}
	var records []metadataRecord
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&records); err != nil {
		return nil, fmt.Errorf("%s: %w", catalogue, err)
	}
	if records == nil {
		return nil, fmt.Errorf("%s: expected an array of metadata records", catalogue)
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		return nil, fmt.Errorf("%s: unexpected data after the metadata records", catalogue)
	}
	bySource := make(map[string]filemetadata.Entry)
	trees := make(map[string]treeMetadata)
	cache := make(map[string]map[string]filemetadata.Entry)
	for _, record := range records {
		if isBlank(record.Source) || isBlank(record.Metadata) || filemetadata.ValidatePath(record.RelativePath) != nil {
			return nil, fmt.Errorf("%s: metadata records require source, metadata and a safe relativePath", catalogue)
		}
		metadataPath, err := filepath.Abs(record.Metadata)
		if err != nil {
			return nil, err
		}
		entries, exists := cache[metadataPath]
		if !exists {
			inventory, err := filemetadata.Read(metadataPath)
			if err != nil {
				return nil, err
			}
			entries = make(map[string]filemetadata.Entry, len(inventory))
			for _, entry := range inventory {
				entries[entry.RelativePath] = entry
			}
			cache[metadataPath] = entries
		}
		source, err := filepath.Abs(record.Source)
		if err != nil {
			return nil, err
		}
		if _, isFile := bySource[source]; isFile && record.Tree {
			return nil, fmt.Errorf("conflicting metadata for source %s", record.Source)
		}
		if _, isTree := trees[source]; isTree && !record.Tree {
			return nil, fmt.Errorf("conflicting metadata for source %s", record.Source)
		}
		if record.Tree {
			tree, err := treeEntries(entries, record, metadataPath)
			if err != nil {
				return nil, err
			}
			if previous, exists := trees[source]; exists && previous.metadata != tree.metadata {
				return nil, fmt.Errorf("conflicting metadata for source %s", record.Source)
			}
			trees[source] = tree
			continue
		}
		entry, exists := entries[record.RelativePath]
		if !exists {
			return nil, fmt.Errorf("metadata %s has no entry for %s", record.Metadata, record.RelativePath)
		}
		if previous, exists := bySource[source]; exists && previous != entry {
			return nil, fmt.Errorf("conflicting metadata for source %s", record.Source)
		}
		bySource[source] = entry
	}
	used := make(map[string]bool)
	attached := make([]sourcedFile, 0, len(files))
	for _, file := range files {
		source, err := filepath.Abs(file.Source)
		if err != nil {
			return nil, err
		}
		if file.tree {
			tree, exists := trees[source]
			if !exists {
				if _, isFile := bySource[source]; isFile {
					return nil, fmt.Errorf("tree record %s has file metadata", file.Source)
				}
				return nil, fmt.Errorf("missing metadata for tree %s", file.Source)
			}
			used[source] = true
			attached = append(attached, tree.files(file)...)
			continue
		}
		entry, exists := bySource[source]
		if !exists {
			if _, isTree := trees[source]; isTree {
				return nil, fmt.Errorf("file record %s has tree metadata", file.Source)
			}
			return nil, fmt.Errorf("missing metadata for source %s", file.Source)
		}
		used[source] = true
		file.metadata = &entry
		attached = append(attached, file)
	}
	for source := range bySource {
		if !used[source] {
			return nil, fmt.Errorf("stale metadata ownership for source %s", source)
		}
	}
	for source := range trees {
		if !used[source] {
			return nil, fmt.Errorf("stale metadata ownership for tree %s", source)
		}
	}
	return attached, nil
}

// treeEntries reads a tree's inventory: the root must be a directory entry, and every entry under it is a file or a
// directory. The packer writes a native tree from archive entries, so a link there is a producer that changed.
func treeEntries(entries map[string]filemetadata.Entry, record metadataRecord, metadataPath string) (treeMetadata, error) {
	root, exists := entries[record.RelativePath]
	if !exists || root.Type != "directory" {
		return treeMetadata{}, fmt.Errorf("metadata %s has no directory entry for tree %s", record.Metadata, record.RelativePath)
	}
	tree := treeMetadata{metadata: metadataPath}
	prefix := record.RelativePath + "/"
	for _, entry := range entries {
		if !strings.HasPrefix(entry.RelativePath, prefix) {
			continue
		}
		if entry.Type == "symlink" {
			return treeMetadata{}, fmt.Errorf("metadata %s lists a symbolic link in tree %s: %s", record.Metadata, record.RelativePath, entry.RelativePath)
		}
		entry.RelativePath = entry.RelativePath[len(prefix):]
		tree.entries = append(tree.entries, entry)
	}
	slices.SortFunc(tree.entries, func(first, second filemetadata.Entry) int {
		return strings.Compare(first.RelativePath, second.RelativePath)
	})
	return tree, nil
}

// files places the tree's regular files under the record's destination, one sourcedFile each. The directories are not
// placed: the manifest the Kotlin fragment writes for the same tree lists none, and the composer creates parents.
func (tree treeMetadata) files(record sourcedFile) []sourcedFile {
	files := make([]sourcedFile, 0, len(tree.entries))
	for _, entry := range tree.entries {
		if entry.Type != "file" {
			continue
		}
		file := sourcedFile{
			Source:       path.Join(record.Source, entry.RelativePath),
			RelativePath: record.RelativePath + "/" + entry.RelativePath,
			Executable:   entry.Executable,
			mode:         &entry.Mode,
		}
		entry.RelativePath = file.RelativePath
		file.metadata = &entry
		files = append(files, file)
	}
	return files
}
