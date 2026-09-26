package main

import (
	"fmt"
	"os"
	"path"
	"path/filepath"
	"strings"
	"unicode"
)

const compositionSpecVersion = 1

// compositionComponent is the Kotlin DevBuildCompositionComponent. Its manifest names each file where it already is.
type compositionComponent struct {
	Manifest            string
	PluginClasspathPart *string
}

// compositionSpec is the Kotlin DevBuildCompositionSpec. A nil SourceRunfiles requests a full distribution.
type compositionSpec struct {
	Version                 int64
	ExpectedFragments       []string
	AdditionalModules       []string
	Components              []compositionComponent
	PluginClasspathPrefix   *string
	SourceRunfiles          *orderedMap
	SourceDirectoryRunfiles *orderedMap
	SourceBindings          *string
}

func readCompositionSpec(file string) (*compositionSpec, error) {
	data, err := readJSONFile(file)
	if err != nil {
		return nil, err
	}
	spec, err := decodeCompositionSpec(data)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	if spec.Version != compositionSpecVersion {
		return nil, fmt.Errorf("Unsupported dev-build composition spec version %d in %s", spec.Version, file)
	}
	if len(spec.Components) == 0 {
		return nil, fmt.Errorf("Dev-build composition spec in %s has no components", file)
	}
	return spec, nil
}

func decodeCompositionSpec(data []byte) (*compositionSpec, error) {
	object, err := decodeJSONObject(data, "org.jetbrains.intellij.build.dev.DevBuildCompositionSpec",
		"version", "expectedFragments", "additionalModules", "components", "pluginClasspathPrefix", "sourceRunfiles",
		"sourceDirectoryRunfiles", "sourceBindings")
	if err != nil {
		return nil, err
	}
	spec := &compositionSpec{Version: compositionSpecVersion}
	if version, err := object.integer("version", 32, false); err != nil {
		return nil, err
	} else if version != nil {
		spec.Version = *version
	}
	if spec.ExpectedFragments, err = object.stringList("expectedFragments", true); err != nil {
		return nil, err
	}
	if spec.AdditionalModules, err = object.stringList("additionalModules", false); err != nil {
		return nil, err
	}
	items, err := object.objectList("components")
	if err != nil {
		return nil, err
	}
	for _, item := range items {
		component, err := decodeJSONObject(item, "org.jetbrains.intellij.build.dev.DevBuildCompositionComponent",
			"manifest", "pluginClasspathPart")
		if err != nil {
			return nil, err
		}
		var value compositionComponent
		if value.Manifest, err = component.string("manifest"); err != nil {
			return nil, err
		}
		if value.PluginClasspathPart, err = component.optionalString("pluginClasspathPart", false); err != nil {
			return nil, err
		}
		spec.Components = append(spec.Components, value)
	}
	if spec.PluginClasspathPrefix, err = object.optionalString("pluginClasspathPrefix", false); err != nil {
		return nil, err
	}
	if spec.SourceRunfiles, err = object.stringMap("sourceRunfiles"); err != nil {
		return nil, err
	}
	if _, err := object.value("sourceDirectoryRunfiles", false, false); err != nil {
		return nil, err
	}
	if spec.SourceDirectoryRunfiles, err = object.stringMap("sourceDirectoryRunfiles"); err != nil {
		return nil, err
	}
	if spec.SourceDirectoryRunfiles == nil {
		spec.SourceDirectoryRunfiles = &orderedMap{values: map[string]string{}}
	}
	if spec.SourceBindings, err = object.optionalString("sourceBindings", false); err != nil {
		return nil, err
	}
	return spec, nil
}

// boundSource is the Kotlin DevBuildBoundSource. An empty directory means a file artifact.
type boundSource struct {
	path      string
	directory string
	kind      string
}

// componentSources is the Kotlin DevBuildComponentSources. It maps the absolute path of each staged source to the
// artifact that Bazel declared for it.
type componentSources struct {
	sources map[string]boundSource
}

func (sources *componentSources) directory(source string) (string, error) {
	absolute, err := absolutePath(source)
	if err != nil {
		return "", err
	}
	return sources.sources[absolute].directory, nil
}

// resolve returns the physical file of a staged source. It fails when the source is not the declared artifact.
func (sources *componentSources) resolve(source string) (string, error) {
	absolute, err := absolutePath(source)
	if err != nil {
		return "", err
	}
	bound, exists := sources.sources[absolute]
	if !exists {
		return "", fmt.Errorf("Missing declared artifact binding for %s", source)
	}
	if bound.directory != "" {
		info, err := os.Lstat(bound.directory)
		if err != nil || !info.IsDir() {
			return "", fmt.Errorf("Declared source directory escapes its artifact binding: %s", bound.directory)
		}
		if real, err := evalSymlinks(bound.directory); err != nil {
			return "", err
		} else if real != bound.directory {
			return "", fmt.Errorf("Declared source directory escapes its artifact binding: %s", bound.directory)
		}
		if bound.path == bound.directory {
			return "", fmt.Errorf("Declared source member has an escaping directory alias: %s", source)
		}
		parent := filepath.Dir(bound.path)
		if real, err := evalSymlinks(parent); err != nil {
			return "", err
		} else if real != parent || !pathStartsWith(parent, bound.directory) {
			return "", fmt.Errorf("Declared source member has an escaping directory alias: %s", source)
		}
	}
	info, err := os.Stat(bound.path)
	regular := err == nil && info.Mode().IsRegular()
	if regular && bound.directory != "" {
		if linkInfo, err := os.Lstat(bound.path); err == nil && linkInfo.Mode()&os.ModeSymlink != 0 {
			regular = false
		}
	}
	if bound.kind != "file" || !regular {
		return "", fmt.Errorf("Declared source member is not a regular file: %s", source)
	}
	stagedReal, err := realPath(source)
	if err != nil {
		return "", err
	}
	boundReal, err := evalSymlinks(bound.path)
	if err != nil {
		return "", err
	}
	if stagedReal != boundReal {
		return "", fmt.Errorf("Staged source differs from its declared artifact binding: %s", source)
	}
	return boundReal, nil
}

// readSourceBindings is the Kotlin readDevBuildSourceBindings. Each line of file describes one artifact that Bazel
// staged for a component: a file, or a directory with its members.
func readSourceBindings(file string, components []compositionComponent) (map[string]*componentSources, error) {
	logicalFile, err := absolutePath(file)
	if err != nil {
		return nil, err
	}
	logicalAnchor := filepath.Dir(logicalFile)
	physicalFile, err := realPath(file)
	if err != nil {
		return nil, err
	}
	physicalAnchor := filepath.Dir(physicalFile)
	result := make(map[string]*componentSources, len(components))
	for _, component := range components {
		if _, exists := result[component.Manifest]; exists {
			return nil, fmt.Errorf("Duplicate component manifest: %s", component.Manifest)
		}
		result[component.Manifest] = &componentSources{sources: make(map[string]boundSource)}
	}
	data, err := readJSONFile(file)
	if err != nil {
		return nil, err
	}
	roots := make(map[string]map[string]bool)
	for _, line := range javaLines(string(data)) {
		artifact, err := decodeJSONObject([]byte(line), "org.jetbrains.intellij.build.dev.DevBuildSourceArtifact",
			"component", "source", "anchorRelativePath", "type", "members")
		if err != nil {
			return nil, err
		}
		var componentName, source, anchorRelativePath, kind string
		for _, field := range []struct {
			name        string
			destination *string
		}{{"component", &componentName}, {"source", &source}, {"anchorRelativePath", &anchorRelativePath}, {"type", &kind}} {
			if *field.destination, err = artifact.string(field.name); err != nil {
				return nil, err
			}
		}
		members, err := artifact.stringList("members", true)
		if err != nil {
			return nil, err
		}
		sources, exists := result[componentName]
		if !exists {
			return nil, fmt.Errorf("Unknown source binding component: %s", componentName)
		}
		sourcePath, err := javaPath(source)
		if err != nil {
			return nil, err
		}
		if spelling, _ := hasJavaPathSpelling(source); isBlank(source) || !spelling || hasDotName(sourcePath) {
			return nil, fmt.Errorf("Unsafe source artifact path: %s", source)
		}
		relative, err := javaPath(anchorRelativePath)
		if err != nil {
			return nil, err
		}
		root, err := filepath.Abs(sourcePath)
		if err != nil {
			return nil, err
		}
		if filepath.IsAbs(relative) || resolveNormalized(logicalAnchor, relative) != root {
			return nil, fmt.Errorf("Source artifact disagrees with its binding anchor: %s", source)
		}
		if roots[componentName] == nil {
			roots[componentName] = make(map[string]bool)
		}
		if identity := devBuildPathIdentity(root); roots[componentName][identity] {
			return nil, fmt.Errorf("Duplicate source artifact binding: %s", source)
		} else {
			roots[componentName][identity] = true
		}
		physical := resolveNormalized(physicalAnchor, relative)
		if kind != "directory" {
			if kind != "file" || len(members) != 0 {
				return nil, fmt.Errorf("Unsupported source artifact type: %s", kind)
			}
			if _, exists := sources.sources[root]; exists {
				return nil, fmt.Errorf("Overlapping source artifact binding: %s", sourcePath)
			}
			sources.sources[root] = boundSource{path: physical, kind: kind}
			continue
		}
		if err := validateDevBuildDirectorySpellings(members); err != nil {
			return nil, err
		}
		memberIdentities := make(map[string]bool, len(members))
		var directories []string
		seenDirectories := make(map[string]bool)
		for _, member := range members {
			if err := validateDevBuildLocalPath(member); err != nil {
				return nil, err
			}
			identity := devBuildPathIdentity(member)
			if memberIdentities[identity] {
				return nil, fmt.Errorf("Duplicate source member binding: %s", member)
			}
			memberIdentities[identity] = true
			key := resolveRelative(root, member)
			if _, exists := sources.sources[key]; exists {
				return nil, fmt.Errorf("Overlapping source member binding: %s", member)
			}
			sources.sources[key] = boundSource{path: resolveRelative(physical, member), directory: physical, kind: "file"}
			for parent := path.Dir(member); parent != "."; parent = path.Dir(parent) {
				if !seenDirectories[parent] {
					seenDirectories[parent] = true
					directories = append(directories, parent)
				}
			}
		}
		for _, directory := range directories {
			key := resolveRelative(root, directory)
			if _, exists := sources.sources[key]; exists {
				return nil, fmt.Errorf("Source directory conflicts with a member binding: %s", filepath.FromSlash(directory))
			}
			sources.sources[key] = boundSource{path: resolveRelative(physical, directory), directory: physical, kind: "directory"}
		}
	}
	return result, nil
}

// resolveNormalized is `base.resolve(relative).normalize()` for an absolute base.
func resolveNormalized(base, relative string) string {
	return filepath.Clean(filepath.Join(base, relative))
}

// javaLines splits text as Java `Files.readAllLines` does. A line ends at "\n", "\r", or "\r\n", and a final line
// terminator starts no empty line.
func javaLines(text string) []string {
	var lines []string
	for text != "" {
		end := strings.IndexAny(text, "\r\n")
		if end < 0 {
			lines = append(lines, text)
			break
		}
		lines = append(lines, text[:end])
		if text[end] == '\r' && end+1 < len(text) && text[end+1] == '\n' {
			end++
		}
		text = text[end+1:]
	}
	return lines
}

// isBlank is Kotlin `CharSequence.isBlank`, which uses Java `Character.isWhitespace` or `Character.isSpaceChar`.
func isBlank(value string) bool {
	for _, character := range value {
		switch {
		case character >= '\t' && character <= '\r':
		case character >= '\u001c' && character <= '\u001f':
		case unicode.In(character, unicode.Zs, unicode.Zl, unicode.Zp):
		default:
			return false
		}
	}
	return true
}
