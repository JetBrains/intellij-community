package main

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"

	"github.com/zeebo/xxh3"
	"jetbrains.com/content-module-packer/internal/span"
)

const contentHashBlockSize = 256 * 1024

type componentEntry struct {
	RelativePath string `json:"relativePath"`
	Type         string `json:"type"`
	Hash         int64  `json:"hash"`
	Executable   bool   `json:"executable,omitempty"`
	Source       string `json:"source"`
}

type componentManifest struct {
	Kind              string           `json:"kind"`
	PlatformPrefix    string           `json:"platformPrefix"`
	OS                string           `json:"os"`
	Arch              string           `json:"arch"`
	AdditionalModules []string         `json:"additionalModules"`
	MainClass         *string          `json:"mainClass"`
	CoreClassPath     []string         `json:"coreClassPath"`
	Entries           []componentEntry `json:"entries"`
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
	buffer := make([]byte, contentHashBlockSize+4)
	entries = make([]componentEntry, 0, len(files))
	var byteCount int64
	for _, file := range files {
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
			hash, err = hashFile(absolute, buffer)
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
	slices.SortFunc(entries, func(first, second componentEntry) int { return compareStrings(first.RelativePath, second.RelativePath) })
	activity.SetInt("fileCount", int64(len(entries)))
	activity.SetInt("hashedFileCount", int64(len(hashes)))
	activity.SetInt("byteCount", byteCount)
	return entries, nil
}

func hashFile(path string, buffer []byte) (int64, error) {
	file, err := os.Open(path)
	if err != nil {
		return 0, err
	}
	defer file.Close()
	hasher := xxh3.New()
	for {
		count, err := io.ReadFull(file, buffer[:contentHashBlockSize])
		if err != nil && err != io.EOF && err != io.ErrUnexpectedEOF {
			return 0, err
		}
		if count != 0 {
			binary.LittleEndian.PutUint32(buffer[count:count+4], uint32(count))
			hasher.Write(buffer[:count+4])
		}
		if err != nil {
			return int64(hasher.Sum64()), nil
		}
	}
}
