package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"slices"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/span"
)

type componentEntry struct {
	RelativePath  string  `json:"relativePath"`
	Type          string  `json:"type"`
	Hash          int64   `json:"hash"`
	Executable    bool    `json:"executable,omitempty"`
	Source        string  `json:"source,omitempty"`
	SymlinkTarget string  `json:"symlinkTarget,omitempty"`
	Mode          *uint32 `json:"mode,omitempty"`
}

type componentManifest struct {
	Version           int              `json:"version,omitempty"`
	Kind              string           `json:"kind"`
	PlatformPrefix    string           `json:"platformPrefix"`
	OS                string           `json:"os"`
	Arch              string           `json:"arch"`
	AdditionalModules []string         `json:"additionalModules"`
	MainClass         *string          `json:"mainClass"`
	CoreClassPath     []string         `json:"coreClassPath"`
	Entries           []componentEntry `json:"entries"`
	PluginCount       int              `json:"pluginCount,omitempty"`
}

func (entry componentEntry) MarshalJSON() ([]byte, error) {
	type encodedEntry componentEntry
	var value any = encodedEntry(entry)
	if entry.Type == "directory" {
		value = struct {
			encodedEntry
			Hash *int64 `json:"hash,omitempty"`
		}{encodedEntry: encodedEntry(entry)}
	}
	var output bytes.Buffer
	encoder := json.NewEncoder(&output)
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(value); err != nil {
		return nil, err
	}
	return bytes.TrimSuffix(output.Bytes(), []byte{'\n'}), nil
}

func writeManifest(opts options, files []sourcedFile, tracer *span.Tracer, parent *span.Span) error {
	entries, err := inventory(files, tracer, parent)
	if err != nil {
		return err
	}
	manifest := componentManifest{
		Kind: opts.kind, PlatformPrefix: opts.platformPrefix, OS: opts.os, Arch: opts.arch,
		AdditionalModules: []string{}, CoreClassPath: []string{}, Entries: entries,
	}
	if opts.pluginComponent != "" {
		manifest.Version = 9
		manifest.PluginCount = 1
	}
	var output bytes.Buffer
	encoder := json.NewEncoder(&output)
	encoder.SetEscapeHTML(false)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(manifest); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(opts.manifest), 0o755); err != nil {
		return err
	}
	return os.WriteFile(opts.manifest, bytes.TrimSuffix(output.Bytes(), []byte{'\n'}), 0o644)
}

func inventory(files []sourcedFile, tracer *span.Tracer, parent *span.Span) (entries []componentEntry, err error) {
	activity := tracer.Start("inventory dev build component", parent)
	defer func() {
		if err != nil {
			activity.Fail(err)
		}
		activity.End()
	}()
	hashes := make(map[string]int64)
	links := make(map[string]string)
	entries = make([]componentEntry, 0, len(files))
	var byteCount int64
	for _, file := range files {
		if file.metadata != nil {
			metadata := *file.metadata
			metadata.RelativePath = file.RelativePath
			if _, err := filemetadata.Merge([]filemetadata.Entry{metadata}); err != nil {
				return nil, err
			}
			entry := componentEntry{RelativePath: file.RelativePath, Type: "component-file", Hash: metadata.Hash}
			if metadata.Type == "directory" {
				entry.Type = "directory"
				mode := logicalComponentMode(metadata.Mode)
				entry.Mode = &mode
			} else if metadata.Type == "symlink" {
				if file.Executable {
					return nil, fmt.Errorf("symbolic link has an executable override: %s", file.RelativePath)
				}
				entry.Type = "symlink"
				entry.SymlinkTarget = filemetadata.CleanLinkTarget(metadata.SymlinkTarget)
				entry.Hash = filemetadata.SymlinkHash(entry.SymlinkTarget)
				links[entry.RelativePath] = entry.SymlinkTarget
			} else {
				entry.Executable = file.Executable || metadata.Executable
				entry.Source = file.Source
				if file.mode != nil {
					mode := logicalComponentMode(*file.mode)
					conventionalMode := uint32(0o644)
					if entry.Executable {
						conventionalMode = 0o755
					}
					if mode != conventionalMode {
						entry.Mode = &mode
					}
				}
			}
			entries = append(entries, entry)
			continue
		}
		info, err := os.Stat(file.Source)
		if err != nil || !info.Mode().IsRegular() {
			return nil, fmt.Errorf("source of '%s' is not a regular file: %s", file.RelativePath, file.Source)
		}
		absolute, err := filepath.Abs(file.Source)
		if err != nil {
			return nil, err
		}
		hash, exists := hashes[absolute]
		if !exists {
			hash, err = filemetadata.HashFile(absolute)
			if err != nil {
				return nil, err
			}
			hashes[absolute] = hash
			byteCount += info.Size()
		}
		entries = append(entries, componentEntry{
			RelativePath: file.RelativePath, Type: "component-file", Hash: hash, Executable: file.Executable, Source: file.Source,
		})
	}
	if err := filemetadata.ValidateLinks(links); err != nil {
		return nil, err
	}
	slices.SortFunc(entries, func(first, second componentEntry) int { return compareStrings(first.RelativePath, second.RelativePath) })
	activity.SetInt("fileCount", int64(len(entries)))
	activity.SetInt("hashedFileCount", int64(len(hashes)))
	activity.SetInt("byteCount", byteCount)
	return entries, nil
}

func logicalComponentMode(mode uint32) uint32 {
	switch mode {
	case 0o444:
		return 0o644
	case 0o555:
		return 0o755
	default:
		return mode
	}
}
