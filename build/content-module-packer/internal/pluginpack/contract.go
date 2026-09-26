// Package pluginpack executes the internal, versioned contract for one plugin remainder.
package pluginpack

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path"
	"unicode/utf8"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

const Version = 1
const TreeVersion = 2
const ScopedVersion = 3

const PluginScope = "plugin"
const DistributionScope = "distribution"

const distributionTransportRoot = ".distribution-root"

// TransportDestination keeps distribution files separate inside the remainder directory.
func TransportDestination(version int, scope, destination string) string {
	if scope == "" {
		scope = PluginScope
	}
	if version == ScopedVersion && scope == DistributionScope {
		return path.Join(distributionTransportRoot, destination)
	}
	return destination
}

// Recipe preserves the complete asset order and states each asset's producer.
// It contains operations only for the remainder. The catalogue defines the remainder input set.
// plugin-remainder-packer --projection derives it in memory from the plan file through the planfile package.
// The Starlark input catalogue is the catalogue.
type Recipe struct {
	Version         int         `json:"version"`
	Plugin          string      `json:"plugin"`
	LayoutSignature string      `json:"layoutSignature"`
	Assets          []Asset     `json:"assets"`
	Operations      []Operation `json:"operations"`
}

// Asset identifies a destination relative to its selected root.
// Independent assets name an artifact that must be absent from the input catalogue.
type Asset struct {
	Destination        string `json:"destination"`
	Producer           string `json:"producer"`
	Artifact           string `json:"artifact,omitempty"`
	Kind               string `json:"kind,omitempty"`
	ClassPath          *bool  `json:"classPath,omitempty"`
	NormalizeTreeModes bool   `json:"normalizeTreeModes,omitempty"`
	Scope              string `json:"scope,omitempty"`
}

// Catalogue is supplied by the action. Only this document contains filesystem roots.
// Its artifacts are the complete remainder input set in execution order.
type Catalogue struct {
	Version   int        `json:"version"`
	Artifacts []Artifact `json:"artifacts"`
	Libraries []Library  `json:"libraries,omitempty"`
}

type Artifact struct {
	ID   string     `json:"id"`
	Kind string     `json:"kind"`
	Root string     `json:"root"`
	Tree *OwnedTree `json:"tree,omitempty"`
}

type OwnedTree struct {
	Version         int                  `json:"version"`
	Artifact        string               `json:"artifact"`
	Plugin          string               `json:"plugin"`
	LayoutSignature string               `json:"layoutSignature"`
	RootMode        uint32               `json:"rootMode"`
	Entries         []filemetadata.Entry `json:"entries"`
	OmitRoot        bool                 `json:"omitRoot,omitempty"`
}

func (tree *OwnedTree) UnmarshalJSON(data []byte) error {
	type encodedTree OwnedTree
	var decoded encodedTree
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&decoded); err != nil {
		return err
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}
	for _, key := range []string{"version", "artifact", "plugin", "layoutSignature", "rootMode", "entries"} {
		if value, exists := fields[key]; !exists || bytes.Equal(value, []byte("null")) {
			return fmt.Errorf("missing prepared tree metadata field %q", key)
		}
	}
	if decoded.Version != TreeVersion {
		return fmt.Errorf("unsupported prepared tree metadata version %d", decoded.Version)
	}
	var entries []map[string]json.RawMessage
	if err := json.Unmarshal(fields["entries"], &entries); err != nil {
		return err
	}
	for index, entry := range entries {
		keys := []string{"relativePath", "type", "size", "mode", "executable"}
		if decoded.Entries[index].Type != "directory" {
			keys = append(keys, "hash")
		}
		for _, key := range keys {
			if value, exists := entry[key]; !exists || bytes.Equal(value, []byte("null")) {
				return fmt.Errorf("missing prepared tree entry field %q", key)
			}
		}
	}
	*tree = OwnedTree(decoded)
	return nil
}

// Library lists archive files in their original expansion order.
type Library struct {
	ID    string      `json:"id"`
	Files []Reference `json:"files"`
}

// Reference identifies a declared file, or one relative file inside a declared directory.
type Reference struct {
	Artifact string `json:"artifact"`
	Path     string `json:"path,omitempty"`
}

// Operation writes one remainder asset. Kinds are jar, copy, directory, symlink, copy-tree, and layout-tree.
// Version 2 adds copy-tree and layout-tree. A copy-tree copies one directory root and reserves each copied entry.
// A layout-tree writes one directory root from its layout assets, then copies it like a copy-tree.
// A tree mode of 0644 clears group-write from each source mode. Mode zero preserves source modes.
// Other operations use permission bits and default to 0644 for files. Links retain their target spelling.
// A link may target a declared asset or an implicit directory containing declared assets.
// Link traversal must stay inside the plugin. The link graph must have no cycles, including cycles through directories.
type Operation struct {
	Kind        string        `json:"kind"`
	Destination string        `json:"destination"`
	Scope       string        `json:"scope,omitempty"`
	Sources     []Source      `json:"sources,omitempty"`
	Options     *JarOptions   `json:"options,omitempty"`
	Input       *Reference    `json:"input,omitempty"`
	Target      string        `json:"target,omitempty"`
	Mode        uint32        `json:"mode,omitempty"`
	Layout      *LayoutAssets `json:"layout,omitempty"`
}

// LayoutAssets is the layoutAssets payload of a plan file with its inputs turned into catalogue references.
// Assets are written in their order. The first claim of a destination wins.
type LayoutAssets struct {
	Inputs []Reference   `json:"inputs"`
	Assets []LayoutAsset `json:"assets"`
}

// LayoutAsset writes one file or one tree under Destination. Sources index LayoutAssets.Inputs.
// A nil Transform is a plain copy of one file, link, or directory.
// An empty Destination is the output root. A tree asset always accepts it. A jar entry asset accepts it
// when every entry brings its own relative path: a tree-map, an archive-tree, a gzip-xml-archive, or a copied directory.
// Mode zero keeps the source mode, or the archive entry mode without group and other write bits.
type LayoutAsset struct {
	Destination string           `json:"destination"`
	Sources     []int            `json:"sources,omitempty"`
	Transform   *LayoutTransform `json:"transform,omitempty"`
	Mode        uint32           `json:"mode,omitempty"`
}

// LayoutTransform kinds are archive-tree, gzip-xml-archive, and tree-map.
// An archive-tree extracts one .zip, .jar, .zip.zst, .tar.gz, or .tgz archive after StripComponents, into a tree or into jar entries.
// A link entry of a .tar.gz, a .tgz, or a Unix-created .zip stays a link with its target spelling. A tree writes it as
// a link. Jar entries accept no link. An entry name loses its leading `./` prefixes, and the `.` root entry is skipped.
// A gzip-xml-archive reads the .xml entries of its .zip or .jar archives in central-directory order and writes each one
// as the jar entry <name>.gzip. It accepts no other file and no link.
// A tree-map copies the entries of its directories that a mapping selects.
// Only tree-map accepts Excludes and DirectoryExcludes. Both use java.nio globs over the whole relative path before mapping.
// Excludes omits files and symlinks. DirectoryExcludes prunes matching directories and their descendants.
// Only archive-tree accepts Includes: ordered java.nio globs over the stripped entry path before mapping. A pattern
// with a leading `!` excludes. The last matching pattern decides an entry. An entry no pattern matches is written when
// every pattern excludes, and dropped otherwise. The parent directories of a written entry are implicit.
// Executables are java.nio globs over the same path for archive-tree, and over the source-relative path for tree-map.
// A regular file that matches gets the executable bits 0111 on top of its mode. Only those two kinds accept them.
type LayoutTransform struct {
	Kind              string          `json:"kind"`
	StripComponents   int             `json:"stripComponents,omitempty"`
	Mappings          []LayoutMapping `json:"mappings,omitempty"`
	Excludes          []string        `json:"excludes,omitempty"`
	DirectoryExcludes []string        `json:"directoryExcludes,omitempty"`
	Includes          []string        `json:"includes,omitempty"`
	Executables       []string        `json:"executables,omitempty"`
}

// LayoutMapping selects entries by a java.nio glob over the whole relative path. An empty Pattern is "**".
// An archive uses the first mapping that matches any of its entries. A tree uses the first mapping per entry.
type LayoutMapping struct {
	Pattern         string `json:"pattern,omitempty"`
	StripComponents int    `json:"stripComponents,omitempty"`
	Destination     string `json:"destination,omitempty"`
}

type JarOptions struct {
	MergeEntities bool   `json:"mergeEntities,omitempty"`
	Directories   string `json:"directories"`
	VerifyCRC     bool   `json:"verifyCrc,omitempty"`
}

// Source kinds are archive, library, entries, and layout. Each source states its manifest policy.
// Filters are the existing module and library filters, or all. Custom filters use prepared entry lists instead.
// Excludes are java.nio glob patterns. They are valid only on an archive source with the module filter.
// An entry whose whole name matches an exclude is dropped. META-INF/listOfEntities.txt survives every exclude.
// A layout source adds the file entries its layout assets write, with the keep manifest policy.
// Lazy sources must be expanded in place by preparation. Go executes no callbacks.
// It executes the Java-glob excludes of a module source and the layout transforms the plan file states.
// The coverage-agent policy uses the production agent pattern. The rewrite-boot-class-path policy uses the output file name.
type Source struct {
	Kind      string          `json:"kind"`
	Prepared  string          `json:"prepared,omitempty"`
	Input     *Reference      `json:"input,omitempty"`
	Library   string          `json:"library,omitempty"`
	Filter    string          `json:"filter,omitempty"`
	Excludes  []string        `json:"excludes,omitempty"`
	Manifest  string          `json:"manifest"`
	Entries   []PreparedEntry `json:"entries,omitempty"`
	Overrides []EntryOverride `json:"overrides,omitempty"`
	Layout    *LayoutAssets   `json:"layout,omitempty"`
}

// PreparedEntry kinds are file, patch, and reserve. Entries retain their declared order.
// A file participates in entity aggregation. A patch rejects an earlier claim on its name.
// A patch retains a manifest even when the source policy is drop.
// A reservation claims the name without reading or writing bytes.
type PreparedEntry struct {
	Kind  string     `json:"kind"`
	Name  string     `json:"name"`
	Input *Reference `json:"input,omitempty"`
}

// EntryOverride replaces or reserves an archive entry when that entry occurs in the original source.
// An override must match an included entry. Missing entries fail instead of moving the override to another position.
type EntryOverride struct {
	Kind  string     `json:"kind"`
	Name  string     `json:"name"`
	Input *Reference `json:"input,omitempty"`
}

// ReadJSON rejects unknown fields, duplicate keys, and trailing documents.
func ReadJSON(file string, target any) error {
	data, err := os.ReadFile(file)
	if err != nil {
		return err
	}
	if !utf8.Valid(data) {
		return fmt.Errorf("%s: contract is not valid UTF-8", file)
	}
	if err := checkJSON(json.NewDecoder(bytes.NewReader(data))); err != nil {
		return fmt.Errorf("%s: %w", file, err)
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		return fmt.Errorf("%s: %w", file, err)
	}
	if _, err := decoder.Token(); err != io.EOF {
		return fmt.Errorf("%s: expected one JSON document", file)
	}
	return nil
}

func checkJSON(decoder *json.Decoder) error {
	token, err := decoder.Token()
	if err != nil {
		return err
	}
	if token != json.Delim('{') {
		return fmt.Errorf("expected a JSON object")
	}
	return checkJSONContainer(decoder, token.(json.Delim))
}

func checkJSONContainer(decoder *json.Decoder, container json.Delim) error {
	keys := make(map[string]bool)
	for decoder.More() {
		if container == '{' {
			token, err := decoder.Token()
			if err != nil {
				return err
			}
			key, ok := token.(string)
			if !ok || keys[key] {
				return fmt.Errorf("invalid or duplicate JSON key %q", token)
			}
			keys[key] = true
		}
		token, err := decoder.Token()
		if err != nil {
			return err
		}
		if nested, ok := token.(json.Delim); ok {
			if nested != '{' && nested != '[' {
				return fmt.Errorf("unexpected JSON delimiter %q", nested)
			}
			if err := checkJSONContainer(decoder, nested); err != nil {
				return err
			}
		}
	}
	_, err := decoder.Token()
	return err
}
