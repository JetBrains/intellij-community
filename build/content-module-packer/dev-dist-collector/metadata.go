package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

type metadataRecord struct {
	Source       string `json:"source"`
	Metadata     string `json:"metadata"`
	RelativePath string `json:"relativePath"`
}

func attachMetadata(files []sourcedFile, catalogue string) error {
	data, err := os.ReadFile(catalogue)
	if err != nil {
		return err
	}
	var records []metadataRecord
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&records); err != nil {
		return fmt.Errorf("%s: %w", catalogue, err)
	}
	if records == nil {
		return fmt.Errorf("%s: expected an array of metadata records", catalogue)
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		return fmt.Errorf("%s: unexpected data after the metadata records", catalogue)
	}
	bySource := make(map[string]filemetadata.Entry)
	cache := make(map[string]map[string]filemetadata.Entry)
	for _, record := range records {
		if isBlank(record.Source) || isBlank(record.Metadata) || filemetadata.ValidatePath(record.RelativePath) != nil {
			return fmt.Errorf("%s: metadata records require source, metadata and a safe relativePath", catalogue)
		}
		metadataPath, err := filepath.Abs(record.Metadata)
		if err != nil {
			return err
		}
		entries, exists := cache[metadataPath]
		if !exists {
			inventory, err := filemetadata.Read(metadataPath)
			if err != nil {
				return err
			}
			entries = make(map[string]filemetadata.Entry, len(inventory))
			for _, entry := range inventory {
				entries[entry.RelativePath] = entry
			}
			cache[metadataPath] = entries
		}
		entry, exists := entries[record.RelativePath]
		if !exists {
			return fmt.Errorf("metadata %s has no entry for %s", record.Metadata, record.RelativePath)
		}
		source, err := filepath.Abs(record.Source)
		if err != nil {
			return err
		}
		if previous, exists := bySource[source]; exists && previous != entry {
			return fmt.Errorf("conflicting metadata for source %s", record.Source)
		}
		bySource[source] = entry
	}
	used := make(map[string]bool)
	for index := range files {
		file := &files[index]
		source, err := filepath.Abs(file.Source)
		if err != nil {
			return err
		}
		entry, exists := bySource[source]
		if !exists {
			return fmt.Errorf("missing metadata for source %s", file.Source)
		}
		used[source] = true
		file.metadata = &entry
	}
	for source := range bySource {
		if !used[source] {
			return fmt.Errorf("stale metadata ownership for source %s", source)
		}
	}
	return nil
}
