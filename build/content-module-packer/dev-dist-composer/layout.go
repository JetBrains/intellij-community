package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
)

// writeLocalLayout writes `local-layout.json`, which names the runfile of each distribution file instead of a copy. The
// `local-home` subcommand of dev-dist-collector reads it.
func writeLocalLayout(components []devBuildComponent, target string, sourceRunfiles *orderedMap, hasPluginClasspath bool,
	sourceDirectoryRunfiles *orderedMap) error {
	var links []distributionLink
	var relativePaths []string
	directories := make(map[string]bool)
	for _, component := range components {
		for _, entry := range component.manifest.Entries {
			if entry.SymlinkTarget != nil {
				links = append(links, distributionLink{entry.RelativePath, *entry.SymlinkTarget})
			}
			relativePaths = append(relativePaths, entry.RelativePath)
			if entry.Type == "directory" {
				directories[devBuildPathIdentity(entry.RelativePath)] = true
			}
		}
	}
	if err := validateDevBuildLinks(links); err != nil {
		return err
	}
	metadata := []string{"core-classpath.txt", "fingerprint.txt"}
	if hasPluginClasspath {
		metadata = append(metadata, pluginClassPath)
	}
	paths := newOrderedSet("local-layout.json")
	for _, name := range metadata {
		paths.add(name)
	}
	if err := validateDevBuildDirectorySpellings(append(relativePaths, metadata...)); err != nil {
		return err
	}
	output := []byte(`{"version":1,"files":[`)
	count := 0
	for _, component := range components {
		for _, entry := range component.manifest.Entries {
			if err := validateDevBuildEntryMode(entry); err != nil {
				return err
			}
			name := entry.RelativePath
			if err := validateDevBuildLocalPath(name); err != nil {
				return err
			}
			if !paths.add(devBuildPathIdentity(name)) {
				return fmt.Errorf("Dev-build components both provide '%s'", name)
			}
			var runfile *string
			if entry.Type != "directory" && entry.SymlinkTarget != nil {
				if entry.Source != nil || entry.Type != "symlink" {
					return fmt.Errorf("Dev-build component must declare the symbolic link '%s' without a file source", name)
				}
				if err := checkDevBuildDistributionLink(name, *entry.SymlinkTarget); err != nil {
					return err
				}
			} else if entry.Type != "directory" {
				if entry.Source == nil {
					return fmt.Errorf("Dev-build component entry '%s' has no source", name)
				}
				source, err := javaPath(*entry.Source)
				if err != nil {
					return err
				}
				resolved, err := resolveSourceRunfile(source, sourceRunfiles, sourceDirectoryRunfiles, name)
				if err != nil {
					return err
				}
				runfile = &resolved
			}
			mode := entry.Mode
			if entry.Type != "directory" && mode != nil && *mode == conventionalMode(entry.Executable) {
				mode = nil
			}
			if count != 0 {
				output = append(output, ',')
			}
			count++
			output = appendLocalLayoutEntry(output, name, runfile, entry.SymlinkTarget, entry.Executable, mode, entry.Type == "directory")
		}
	}
	if err := checkNoEntryBelowAnother(paths.values, directories); err != nil {
		return err
	}
	output = append(output, `],"metadata":[`...)
	for index, name := range metadata {
		if index != 0 {
			output = append(output, ',')
		}
		output = appendJSONString(output, name)
	}
	output = append(output, "]}"...)
	return os.WriteFile(filepath.Join(target, "local-layout.json"), output, 0o666)
}

// appendLocalLayoutEntry writes one entry of the local layout. It writes every property, null included, and the kind
// property only for a directory.
func appendLocalLayoutEntry(output []byte, name string, runfile, symlinkTarget *string, executable bool, mode *int64, directory bool) []byte {
	output = append(output, `{"path":`...)
	output = appendJSONString(output, name)
	output = append(output, `,"runfile":`...)
	output = appendNullableJSONString(output, runfile)
	output = append(output, `,"symlinkTarget":`...)
	output = appendNullableJSONString(output, symlinkTarget)
	output = append(output, `,"executable":`...)
	output = strconv.AppendBool(output, executable)
	output = append(output, `,"mode":`...)
	if mode == nil {
		output = append(output, "null"...)
	} else {
		output = strconv.AppendInt(output, *mode, 10)
	}
	if directory {
		output = append(output, `,"kind":"directory"`...)
	}
	return append(output, '}')
}

func appendNullableJSONString(output []byte, value *string) []byte {
	if value == nil {
		return append(output, "null"...)
	}
	return appendJSONString(output, *value)
}

// resolveSourceRunfile finds the runfile of a source: an exact file declaration, or a file inside the deepest
// declared directory.
func resolveSourceRunfile(source string, files, directories *orderedMap, name string) (string, error) {
	if hasDotName(source) {
		return "", fmt.Errorf("Dev-build component entry '%s' has an unsafe source: %s", name, source)
	}
	absolute, err := absolutePath(source)
	if err != nil {
		return "", err
	}
	if exact, exists := files.values[absolute]; exists {
		return exact, validateDevBuildLocalPath(exact)
	}
	directory := ""
	if directories != nil {
		for _, candidate := range directories.keys {
			if absolute != candidate && pathStartsWith(absolute, candidate) && len(candidate) > len(directory) {
				directory = candidate
			}
		}
	}
	if directory == "" {
		return "", fmt.Errorf("Dev-build component entry '%s' names an undeclared source: %s", name, source)
	}
	runfile := directories.values[directory]
	if err := validateDevBuildLocalPath(runfile); err != nil {
		return "", err
	}
	child, err := filepath.Rel(directory, absolute)
	if err != nil {
		return "", err
	}
	child = filepath.ToSlash(child)
	if err := validateDevBuildLocalPath(child); err != nil {
		return "", err
	}
	return runfile + "/" + child, nil
}
