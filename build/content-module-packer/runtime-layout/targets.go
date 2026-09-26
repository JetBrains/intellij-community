package main

import (
	"encoding/json"
	"fmt"
	"os"
	"slices"
	"strings"
)

// The kinds of a library entry, as `PluginDistributionEntry.Kind` spells them.
const (
	projectLibraryKind = "projectLibrary"
	moduleLibraryKind  = "moduleLibrary"
)

// jpsLibrary is a library of the project model. Name is the library name of a project library, and the name of the
// owning module of a module library, as `PluginDistributionEntry.name` states it.
type jpsLibrary struct {
	kind string
	name string
}

// libraryIndex maps a library container of Bazel to its library in the project model.
type libraryIndex struct {
	byLabel map[string]jpsLibrary
	// byJarTarget and byJar list every library that has a jar, as two libraries can share one.
	byJarTarget map[string][]jpsLibrary
	byJar       map[string][]jpsLibrary
}

type targetsFile struct {
	Modules map[string]struct {
		ModuleLibraries map[string]targetLibrary `json:"moduleLibraries"`
	} `json:"modules"`
	ProjectLibraries map[string]targetLibrary `json:"projectLibraries"`
}

type targetLibrary struct {
	Target string   `json:"target"`
	Jars   []string `json:"jars"`
	// JarTargets are the labels of the single jars of the library. A plugin names a jar by such a label when two libraries
	// share it.
	JarTargets []string `json:"jarTargets"`
}

// readLibraryIndex reads the bazel-targets.json that the JPS-to-Bazel converter writes. Unknown fields are allowed,
// because another tool owns the format and this one reads two parts of it.
func readLibraryIndex(file string) (*libraryIndex, error) {
	data, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	var targets targetsFile
	if err := json.Unmarshal(data, &targets); err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	index := &libraryIndex{byLabel: make(map[string]jpsLibrary), byJarTarget: make(map[string][]jpsLibrary), byJar: make(map[string][]jpsLibrary)}
	add := func(library jpsLibrary, target targetLibrary) {
		if target.Target != "" {
			index.byLabel[normalizeLabel(target.Target)] = library
		}
		for _, jarTarget := range target.JarTargets {
			key := normalizeLabel(jarTarget)
			index.byJarTarget[key] = append(index.byJarTarget[key], library)
		}
		for _, jar := range target.Jars {
			index.byJar[jar] = append(index.byJar[jar], library)
		}
	}
	// Sorted, so that two libraries with one label resolve the same way on every run.
	for _, name := range sortedKeys(targets.ProjectLibraries) {
		add(jpsLibrary{kind: projectLibraryKind, name: name}, targets.ProjectLibraries[name])
	}
	for _, moduleName := range sortedKeys(targets.Modules) {
		libraries := targets.Modules[moduleName].ModuleLibraries
		for _, name := range sortedKeys(libraries) {
			add(jpsLibrary{kind: moduleLibraryKind, name: moduleName}, libraries[name])
		}
	}
	return index, nil
}

// resolve returns the library of the project model that m merges: by the label of its container or of its single jar,
// or else by its jars. A jar that several libraries share resolves to the module library of one of modules, the
// modules of the jar that merges it. `JarPackager` packs a module library with its module.
func (index *libraryIndex) resolve(m member, modules []string) (jpsLibrary, error) {
	label := normalizeLabel(m.Library)
	if library, found := index.byLabel[label]; found {
		return library, nil
	}
	if candidates := index.byJarTarget[label]; len(candidates) != 0 {
		library, err := pick(candidates, modules)
		if err != nil {
			return jpsLibrary{}, fmt.Errorf("the jar %s %w", m.Library, err)
		}
		return library, nil
	}
	var resolved *jpsLibrary
	for _, jar := range m.Jars {
		candidates := index.byJar[jar]
		if len(candidates) == 0 {
			return jpsLibrary{}, fmt.Errorf("the library %s is not in bazel-targets.json, and neither is its jar %s", m.Library, jar)
		}
		library, err := pick(candidates, modules)
		if err != nil {
			return jpsLibrary{}, fmt.Errorf("the jar %s of the library %s %w", jar, m.Library, err)
		}
		if resolved != nil && *resolved != library {
			return jpsLibrary{}, fmt.Errorf("the jars of the library %s belong to different libraries of bazel-targets.json", m.Library)
		}
		resolved = &library
	}
	if resolved == nil {
		return jpsLibrary{}, fmt.Errorf("the library %s is not in bazel-targets.json, and it states no jar", m.Library)
	}
	return *resolved, nil
}

// pick returns the only candidate, or the one module library of a module in modules.
func pick(candidates []jpsLibrary, modules []string) (jpsLibrary, error) {
	if len(candidates) == 1 {
		return candidates[0], nil
	}
	var owned []jpsLibrary
	for _, candidate := range candidates {
		if candidate.kind == moduleLibraryKind && slices.Contains(modules, candidate.name) && !slices.Contains(owned, candidate) {
			owned = append(owned, candidate)
		}
	}
	if len(owned) != 1 {
		return jpsLibrary{}, fmt.Errorf("belongs to %d libraries of bazel-targets.json, and %d of them are module libraries of the modules of its jar", len(candidates), len(owned))
	}
	return owned[0], nil
}

// normalizeLabel spells a label the way bazel-targets.json does. `str(Label)` in Starlark gives the canonical form,
// `@@lib+//:x` for the `lib` module, and the converter writes the apparent form, `@lib//:x`. A label without a target
// name names the target after its package.
func normalizeLabel(label string) string {
	if rest, canonical := strings.CutPrefix(label, "@@"); canonical {
		repository, target, found := strings.Cut(rest, "//")
		if !found {
			return label
		}
		repository = strings.TrimSuffix(repository, "+")
		if repository == "" {
			label = "//" + target
		} else {
			label = "@" + repository + "//" + target
		}
	}
	separator := strings.Index(label, "//")
	if separator < 0 {
		return label
	}
	packagePath := label[separator+2:]
	if packagePath != "" && !strings.Contains(packagePath, ":") {
		label += ":" + packagePath[strings.LastIndex(packagePath, "/")+1:]
	}
	return label
}

func sortedKeys[V any](values map[string]V) []string {
	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	slices.Sort(keys)
	return keys
}
