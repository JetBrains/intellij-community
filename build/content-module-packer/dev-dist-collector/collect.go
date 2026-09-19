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
	"unicode"
	"unicode/utf16"
	"unicode/utf8"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/span"
)

type sourcedFile struct {
	Source       string `json:"source"`
	RelativePath string `json:"relativePath"`
	Executable   bool   `json:"executable"`
	metadata     *filemetadata.Entry
	mode         *uint32
	classPath    bool
	// tree marks a directory record, which attachMetadata replaces with one file per inventory entry under it. It
	// never reaches the manifest: the composer creates the directories a file needs.
	tree bool
}

func collect(opts options, tracer *span.Tracer, parent *span.Span) (files []sourcedFile, err error) {
	name := "collect platform jars"
	if opts.filesFile != "" {
		name = "collect explicit files"
	}
	activity := tracer.Start(name, parent)
	defer activity.End()
	switch {
	case opts.jarsFile != "":
		files, err = collectPlatformJars(opts.jarsFile)
	default:
		files, err = collectFiles(opts.filesFile)
	}
	if err == nil && opts.metadataCatalogue != "" {
		files, err = attachMetadata(files, opts.metadataCatalogue)
	} else if err == nil && opts.filesFile == "" {
		err = fmt.Errorf("packed jars require --metadata-catalogue; payload inventories belong to the packing action")
	}
	// After the metadata, because a tree record is a directory until its inventory names the files under it, and
	// the destinations to check are theirs.
	if err == nil {
		err = validateDestinations(files)
	}
	if err != nil {
		activity.Fail(err)
		return nil, err
	}
	countName := "jarCount"
	if opts.filesFile != "" {
		countName = "fileCount"
	}
	activity.SetInt(countName, int64(len(files)))
	activity.SetInt("byteCount", 0)
	return files, nil
}

func collectPlatformJars(file string) ([]sourcedFile, error) {
	records, err := decodeRecords(file)
	if err != nil {
		return nil, err
	}
	files := make([]sourcedFile, 0, len(records))
	for index, record := range records {
		if isBlank(record.Source) || isBlank(record.RelativePath) {
			return nil, fmt.Errorf("%s: record %d requires source and relativePath", file, index+1)
		}
		// A packed jar is a jar, never a program. The packing action does not state the bit, and a file that states it
		// is a file record in the wrong mode.
		if record.Executable != nil {
			return nil, fmt.Errorf("%s: record %d states executable, which a packed jar never is", file, index+1)
		}
		// The destination the jar declares, not the name of the file that holds it: a platform jar can name a
		// subdirectory of the plugin's `lib/`, and the two agree only when the destination is flat. A tree record
		// names the library's directory under `lib/`, the way the Kotlin packer places `lib/jna/`.
		relativePath, err := normalizedRelativePath(record.RelativePath)
		if err != nil {
			return nil, fmt.Errorf("%s: record %d escapes the distribution: %s", file, index+1, record.RelativePath)
		}
		files = append(files, sourcedFile{Source: record.Source, RelativePath: "lib/" + relativePath, tree: record.Tree})
	}
	if len(files) == 0 {
		return nil, fmt.Errorf("%s names no jar, so this component would contribute nothing", file)
	}
	return files, nil
}

// One record shape for both collection modes. A jar record leaves `executable` unstated, and a file record states it.
// A jar record may state `tree`, and then names a native tree directory rather than a jar; see sourcedFile.
type collectedRecord struct {
	Source       string `json:"source"`
	RelativePath string `json:"relativePath"`
	Executable   *bool  `json:"executable"`
	Tree         bool   `json:"tree"`
}

// decodeRecords reads the records of one collection mode, and refuses a file that is not exactly an array of them.
func decodeRecords(file string) ([]collectedRecord, error) {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(data) {
		return nil, fmt.Errorf("%s is not valid UTF-8", file)
	}
	var records []collectedRecord
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&records); err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	if records == nil {
		return nil, fmt.Errorf("%s: expected an array of file records", file)
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		return nil, fmt.Errorf("%s: unexpected data after the file records", file)
	}
	return records, nil
}

func collectFiles(file string) ([]sourcedFile, error) {
	records, err := decodeRecords(file)
	if err != nil {
		return nil, err
	}
	files := make([]sourcedFile, 0, len(records))
	destinations := make(map[string]bool)
	for index, record := range records {
		if isBlank(record.Source) || isBlank(record.RelativePath) || record.Executable == nil {
			return nil, fmt.Errorf("%s: record %d requires source, relativePath and executable", file, index+1)
		}
		if record.Tree {
			return nil, fmt.Errorf("%s: record %d states tree, which only a packed jar record can", file, index+1)
		}
		relativePath, err := normalizedRelativePath(record.RelativePath)
		if err != nil {
			return nil, fmt.Errorf("%s: record %d escapes the distribution: %s", file, index+1, record.RelativePath)
		}
		if destinations[relativePath] {
			return nil, fmt.Errorf("%s: duplicate destination: %s", file, relativePath)
		}
		destinations[relativePath] = true
		files = append(files, sourcedFile{Source: record.Source, RelativePath: relativePath, Executable: *record.Executable})
	}
	return files, nil
}

func normalizedRelativePath(value string) (string, error) {
	if strings.HasPrefix(filepath.ToSlash(value), "/") || filepath.VolumeName(value) != "" || strings.ContainsAny(value, "\\:\x00") {
		return "", fmt.Errorf("not a relative path: %q", value)
	}
	parts := strings.FieldsFunc(filepath.ToSlash(value), func(character rune) bool { return character == '/' })
	if len(parts) == 0 {
		return "", fmt.Errorf("empty path: %q", value)
	}
	for _, part := range parts {
		if part == "." || part == ".." {
			return "", fmt.Errorf("not a normalized path: %q", value)
		}
	}
	return strings.Join(parts, "/"), nil
}

func validateDestinations(files []sourcedFile) error {
	byPath := make(map[string]bool)
	for _, file := range files {
		if err := filemetadata.ValidatePath(file.RelativePath); err != nil {
			return err
		}
		if byPath[file.RelativePath] {
			return fmt.Errorf("conflicting destination: %s", file.RelativePath)
		}
		byPath[file.RelativePath] = true
	}
	for name := range byPath {
		for parent := path.Dir(name); parent != "."; parent = path.Dir(parent) {
			if byPath[parent] {
				return fmt.Errorf("conflicting destinations: %s contains %s", parent, name)
			}
		}
	}
	return nil
}

func isBlank(value string) bool {
	for _, character := range value {
		if !(unicode.IsSpace(character) && character != '\u0085') && !(character >= '\u001c' && character <= '\u001f') {
			return false
		}
	}
	return true
}

func compareStrings(first, second string) int {
	return slices.Compare(utf16.Encode([]rune(first)), utf16.Encode([]rune(second)))
}
