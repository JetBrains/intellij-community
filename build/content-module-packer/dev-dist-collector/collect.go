package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"unicode"
	"unicode/utf16"
	"unicode/utf8"

	"jetbrains.com/content-module-packer/internal/span"
)

type sourcedFile struct {
	Source       string `json:"source"`
	RelativePath string `json:"relativePath"`
	Executable   bool   `json:"executable"`
}

type relation struct {
	plugin string
	output string
}

func collect(opts options, tracer *span.Tracer, parent *span.Span) (files []sourcedFile, err error) {
	name := "collect platform jars"
	if opts.pluginJarsFile != "" {
		name = "collect prepacked plugin content jars"
	} else if opts.filesFile != "" {
		name = "collect explicit files"
	}
	activity := tracer.Start(name, parent)
	defer activity.End()
	switch {
	case opts.jarsFile != "":
		files, err = collectPlatformJars(opts.jarsFile)
	case opts.pluginJarsFile != "":
		files, err = collectPluginJars(opts.pluginJarsFile, opts.placements)
	default:
		files, err = collectFiles(opts.filesFile)
	}
	var byteCount int64
	if err == nil {
		for _, file := range files {
			var info os.FileInfo
			info, err = os.Stat(file.Source)
			if err != nil {
				break
			}
			byteCount += info.Size()
		}
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
	activity.SetInt("byteCount", byteCount)
	return files, nil
}

func collectPlatformJars(file string) ([]sourcedFile, error) {
	lines, err := readLines(file)
	if err != nil {
		return nil, err
	}
	files := make([]sourcedFile, 0, len(lines))
	byName := make(map[string]string)
	for _, source := range lines {
		if isBlank(source) {
			continue
		}
		name := filepath.Base(source)
		if previous, exists := byName[name]; exists {
			return nil, fmt.Errorf("two packed jars are named '%s': %s and %s", name, previous, source)
		}
		byName[name] = source
		files = append(files, sourcedFile{Source: source, RelativePath: "lib/" + name})
	}
	if len(files) == 0 {
		return nil, fmt.Errorf("%s names no jar, so this component would contribute nothing", file)
	}
	return files, nil
}

func collectPluginJars(file string, placementFiles []string) ([]sourcedFile, error) {
	jars := make(map[relation]string)
	if err := readRelations(file, jars, "plugin jar relation"); err != nil {
		return nil, err
	}
	placements := make(map[relation]string)
	for _, placementFile := range placementFiles {
		if err := readRelations(placementFile, placements, "placement"); err != nil {
			return nil, err
		}
	}
	missing := difference(jars, placements)
	unknown := difference(placements, jars)
	if len(missing) != 0 || len(unknown) != 0 {
		return nil, fmt.Errorf("packed plugin jar records and validated placements differ: missing placements %s, unknown placements %s",
			formatRelations(missing), formatRelations(unknown))
	}
	keys := make([]relation, 0, len(placements))
	for key := range placements {
		keys = append(keys, key)
	}
	sortRelations(keys)
	destinations := make(map[string]relation)
	files := make([]sourcedFile, 0, len(keys))
	for _, key := range keys {
		destination := placements[key]
		relativePath, err := normalizedRelativePath(destination)
		if err != nil {
			return nil, fmt.Errorf("placement for %s/%s escapes the distribution: %s", key.plugin, key.output, destination)
		}
		output, err := normalizedRelativePath(key.output)
		if err != nil {
			return nil, err
		}
		expectedSuffix := "lib/" + output
		if !strings.HasPrefix(relativePath, "plugins/") || !strings.HasSuffix(relativePath, "/"+expectedSuffix) {
			return nil, fmt.Errorf("placement for %s/%s is '%s', expected plugins/<directory>/%s",
				key.plugin, key.output, destination, expectedSuffix)
		}
		if previous, exists := destinations[relativePath]; exists {
			return nil, fmt.Errorf("plugin jars %s/%s and %s/%s both claim %s",
				previous.plugin, previous.output, key.plugin, key.output, destination)
		}
		destinations[relativePath] = key
		files = append(files, sourcedFile{Source: jars[key], RelativePath: relativePath})
	}
	return files, nil
}

func readRelations(file string, result map[relation]string, kind string) error {
	lines, err := readLines(file)
	if err != nil {
		return err
	}
	for index, line := range lines {
		if isBlank(line) {
			continue
		}
		fields := strings.Split(line, "\t")
		if len(fields) != 3 {
			return fmt.Errorf("%s:%d: expected 3 tab-separated fields, got %d", file, index+1, len(fields))
		}
		for _, field := range fields {
			if isBlank(field) {
				return fmt.Errorf("%s:%d: fields must not be blank", file, index+1)
			}
		}
		if _, err := normalizedRelativePath(fields[1]); err != nil {
			return fmt.Errorf("%s:%d: relative output file of %s escapes plugin lib: '%s'", file, index+1, fields[0], fields[1])
		}
		key := relation{plugin: fields[0], output: fields[1]}
		if previous, exists := result[key]; exists {
			return fmt.Errorf("%s:%d: duplicate %s for %s/%s (already %s)", file, index+1, kind, key.plugin, key.output, previous)
		}
		result[key] = fields[2]
	}
	return nil
}

func collectFiles(file string) ([]sourcedFile, error) {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(data) {
		return nil, fmt.Errorf("%s is not valid UTF-8", file)
	}
	var records []struct {
		Source       string `json:"source"`
		RelativePath string `json:"relativePath"`
		Executable   *bool  `json:"executable"`
	}
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
	files := make([]sourcedFile, 0, len(records))
	destinations := make(map[string]bool)
	for index, record := range records {
		if isBlank(record.Source) || isBlank(record.RelativePath) || record.Executable == nil {
			return nil, fmt.Errorf("%s: record %d requires source, relativePath and executable", file, index+1)
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
	if strings.HasPrefix(filepath.ToSlash(value), "/") || filepath.VolumeName(value) != "" || strings.ContainsRune(value, '\x00') {
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

func readLines(file string) ([]string, error) {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(data) {
		return nil, fmt.Errorf("%s is not valid UTF-8", file)
	}
	text := strings.ReplaceAll(string(data), "\r\n", "\n")
	return strings.Split(strings.ReplaceAll(text, "\r", "\n"), "\n"), nil
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

func sortRelations(keys []relation) {
	slices.SortFunc(keys, func(first, second relation) int {
		if order := compareStrings(first.plugin, second.plugin); order != 0 {
			return order
		}
		return compareStrings(first.output, second.output)
	})
}

func difference(first, second map[relation]string) []relation {
	var result []relation
	for key := range first {
		if _, exists := second[key]; !exists {
			result = append(result, key)
		}
	}
	return result
}

func formatRelations(keys []relation) string {
	sortRelations(keys)
	names := make([]string, len(keys))
	for index, key := range keys {
		names[index] = key.plugin + "/" + key.output
	}
	return "[" + strings.Join(names, ", ") + "]"
}
