package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path"
	"path/filepath"
	"strings"
)

type localLayoutFile struct {
	Path          string `json:"path"`
	Runfile       string `json:"runfile"`
	SymlinkTarget string `json:"symlinkTarget"`
	Executable    bool   `json:"executable"`
}

type localLayout struct {
	Version  int               `json:"version"`
	Files    []localLayoutFile `json:"files"`
	Metadata []string          `json:"metadata"`
}

func runLocalHome(args []string, output, errors io.Writer) int {
	values := make(map[string]string)
	for _, argument := range args {
		name, value, present := strings.Cut(argument, "=")
		if !present || value == "" || (name != "--layout" && name != "--output-dir") || values[name] != "" {
			fmt.Fprintf(errors, "ERROR: invalid local-home option: %s\n", argument)
			return 2
		}
		values[name] = value
	}
	if values["--layout"] == "" || values["--output-dir"] == "" {
		fmt.Fprintln(errors, "ERROR: local-home requires --layout and --output-dir")
		return 2
	}
	lookup, err := localRunfilesLookup()
	if err == nil {
		err = materializeLocalHome(values["--layout"], values["--output-dir"], lookup)
	}
	if err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	fmt.Fprintln(output, "Prepared the local dev home")
	return 0
}

func materializeLocalHome(layoutFile, outputDir string, lookup func(string) (string, error)) error {
	content, err := os.Open(layoutFile)
	if err != nil {
		return err
	}
	defer content.Close()
	decoder := json.NewDecoder(content)
	decoder.DisallowUnknownFields()
	var layout localLayout
	if err := decoder.Decode(&layout); err != nil {
		return fmt.Errorf("read the local layout: %w", err)
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		return fmt.Errorf("unexpected content after the local layout")
	}
	if layout.Version != 1 {
		return fmt.Errorf("unsupported local layout version: %d", layout.Version)
	}
	paths := map[string]bool{"local-layout.json": true}
	for _, name := range layout.Metadata {
		if name != "core-classpath.txt" && name != "fingerprint.txt" && name != "plugins/plugin-classpath.txt" {
			return fmt.Errorf("unknown local metadata file: %s", name)
		}
		if paths[name] {
			return fmt.Errorf("duplicate local path: %s", name)
		}
		paths[name] = true
	}
	for _, file := range layout.Files {
		if err := validateLocalPath(file.Path); err != nil {
			return err
		}
		if paths[file.Path] {
			return fmt.Errorf("duplicate local path: %s", file.Path)
		}
		paths[file.Path] = true
		if (file.Runfile == "") == (file.SymlinkTarget == "") {
			return fmt.Errorf("%s requires exactly one runfile or symbolic link target", file.Path)
		}
		if file.Runfile != "" {
			if err := validateLocalPath(file.Runfile); err != nil {
				return err
			}
		} else {
			target := file.SymlinkTarget
			resolved := path.Join(path.Dir(file.Path), target)
			if path.IsAbs(target) || strings.ContainsAny(target, "\\:\x00") || resolved == ".." || strings.HasPrefix(resolved, "../") {
				return fmt.Errorf("symbolic link %s escapes the local home: %s", file.Path, target)
			}
		}
	}
	for name := range paths {
		for parent := path.Dir(name); parent != "."; parent = path.Dir(parent) {
			if paths[parent] {
				return fmt.Errorf("local path %s is below another entry: %s", name, parent)
			}
		}
	}
	if info, err := os.Lstat(outputDir); err == nil && !info.IsDir() {
		return fmt.Errorf("the local home must be a directory: %s", outputDir)
	}
	if entries, err := os.ReadDir(outputDir); err == nil && len(entries) != 0 {
		return fmt.Errorf("the local home must be empty: %s", outputDir)
	} else if err != nil && !os.IsNotExist(err) {
		return err
	}
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return err
	}
	for _, file := range layout.Files {
		destination := filepath.Join(outputDir, filepath.FromSlash(file.Path))
		if err := os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
			return err
		}
		if file.SymlinkTarget != "" {
			if err := os.Symlink(filepath.FromSlash(file.SymlinkTarget), destination); err != nil {
				return err
			}
			continue
		}
		source, err := lookup(file.Runfile)
		if err != nil {
			return err
		}
		source, err = filepath.Abs(source)
		if err != nil {
			return err
		}
		info, err := os.Stat(source)
		if err != nil {
			return err
		}
		if !info.Mode().IsRegular() {
			return fmt.Errorf("the runfile for %s is not a regular file: %s", file.Path, source)
		}
		if file.Executable && info.Mode().Perm()&0111 == 0 {
			err = copyLocalFile(source, destination, file.Executable)
		} else {
			err = os.Symlink(source, destination)
		}
		if err != nil {
			return err
		}
	}
	for _, name := range layout.Metadata {
		destination := filepath.Join(outputDir, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
			return err
		}
		if err := copyLocalFile(filepath.Join(filepath.Dir(layoutFile), filepath.FromSlash(name)), destination, false); err != nil {
			return err
		}
	}
	return nil
}

func validateLocalPath(name string) error {
	if name == "" || strings.ContainsAny(name, "\\:\x00") {
		return fmt.Errorf("invalid local path: %q", name)
	}
	for _, part := range strings.Split(name, "/") {
		if part == "" || part == "." || part == ".." {
			return fmt.Errorf("invalid local path: %q", name)
		}
	}
	return nil
}

func copyLocalFile(source, destination string, executable bool) error {
	input, err := os.Open(source)
	if err != nil {
		return err
	}
	defer input.Close()
	mode := os.FileMode(0644)
	if executable {
		mode = 0755
	}
	output, err := os.OpenFile(destination, os.O_CREATE|os.O_EXCL|os.O_WRONLY, mode)
	if err != nil {
		return err
	}
	_, copyError := io.Copy(output, input)
	closeError := output.Close()
	if copyError != nil {
		return copyError
	}
	return closeError
}

func localRunfilesLookup() (func(string) (string, error), error) {
	roots := []string{os.Getenv("JAVA_RUNFILES"), os.Getenv("RUNFILES_DIR")}
	manifest := make(map[string]string)
	if manifestFile := os.Getenv("RUNFILES_MANIFEST_FILE"); manifestFile != "" {
		input, err := os.Open(manifestFile)
		if err != nil {
			return nil, err
		}
		defer input.Close()
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
			manifest[name] = source
		}
		if err := scanner.Err(); err != nil {
			return nil, err
		}
	}
	return func(name string) (string, error) {
		for _, root := range roots {
			if root != "" {
				candidate := filepath.Join(root, filepath.FromSlash(name))
				if _, err := os.Stat(candidate); err == nil {
					return candidate, nil
				}
			}
		}
		for prefix := name; prefix != "."; prefix = path.Dir(prefix) {
			if source := manifest[prefix]; source != "" {
				return filepath.Join(source, filepath.FromSlash(strings.TrimPrefix(strings.TrimPrefix(name, prefix), "/"))), nil
			}
		}
		return "", fmt.Errorf("missing local dev runfile: %s", name)
	}, nil
}
