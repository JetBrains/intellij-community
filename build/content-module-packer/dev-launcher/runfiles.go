package main

import (
	"bufio"
	"fmt"
	"os"
	"path"
	"path/filepath"
	"strings"
)

// runfiles resolves a runfiles path through the runfiles directory or, where there is none, the runfiles manifest.
type runfiles struct {
	directory    string
	manifestFile string
	manifest     map[string]string
}

// findRunfiles finds the runfiles of the launcher [self]: RUNFILES_DIR, then `<self>.runfiles`, then
// RUNFILES_MANIFEST_FILE or `<self>.runfiles_manifest`. `bazel run` sets none of the variables.
func findRunfiles(self string, getenv func(string) string) (runfiles, error) {
	for _, directory := range []string{getenv("RUNFILES_DIR"), self + ".runfiles"} {
		if info, err := os.Stat(directory); directory != "" && err == nil && info.IsDir() {
			return runfiles{directory: directory}, nil
		}
	}
	for _, file := range []string{getenv("RUNFILES_MANIFEST_FILE"), self + ".runfiles_manifest"} {
		if file == "" {
			continue
		}
		if _, err := os.Stat(file); err != nil {
			continue
		}
		manifest, err := readRunfilesManifest(file)
		if err != nil {
			return runfiles{}, err
		}
		return runfiles{manifestFile: file, manifest: manifest}, nil
	}
	return runfiles{}, fmt.Errorf("no runfiles for %s", self)
}

func readRunfilesManifest(file string) (map[string]string, error) {
	input, err := os.Open(file)
	if err != nil {
		return nil, err
	}
	defer input.Close()
	result := make(map[string]string)
	scanner := bufio.NewScanner(input)
	for scanner.Scan() {
		line := scanner.Text()
		escaped := strings.HasPrefix(line, " ")
		if escaped {
			line = line[1:]
		}
		name, source, _ := strings.Cut(line, " ")
		if escaped {
			decoder := strings.NewReplacer(`\s`, " ", `\n`, "\n", `\b`, `\`)
			name, source = decoder.Replace(name), decoder.Replace(source)
		}
		result[name] = source
	}
	return result, scanner.Err()
}

func (files runfiles) rlocation(name string) (string, error) {
	// A local Java runtime states its executable as an absolute path.
	if filepath.IsAbs(name) {
		return name, nil
	}
	if files.directory != "" {
		candidate := filepath.Join(files.directory, filepath.FromSlash(name))
		if _, err := os.Stat(candidate); err != nil {
			return "", fmt.Errorf("missing runfile %s: %w", name, err)
		}
		return candidate, nil
	}
	for prefix := name; prefix != "."; prefix = path.Dir(prefix) {
		if source := files.manifest[prefix]; source != "" {
			return filepath.Join(source, filepath.FromSlash(strings.TrimPrefix(strings.TrimPrefix(name, prefix), "/"))), nil
		}
	}
	return "", fmt.Errorf("missing runfile %s", name)
}

// environment is what a child needs to find the same runfiles: the local home tool, the before-run step, and the IDE,
// which the java stub gave JAVA_RUNFILES.
func (files runfiles) environment() []string {
	if files.directory != "" {
		return []string{"RUNFILES_DIR=" + files.directory, "JAVA_RUNFILES=" + files.directory}
	}
	return []string{"RUNFILES_MANIFEST_FILE=" + files.manifestFile}
}
