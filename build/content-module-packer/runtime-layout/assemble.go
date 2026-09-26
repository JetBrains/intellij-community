package main

import (
	"fmt"
	"os"
	"slices"
	"strings"
)

const layoutVersion = 1

// layout is the `RuntimeModuleRepositoryLayout` that `RuntimeModuleRepositoryMain` reads. The field order and the
// omitted absent fields follow its Kotlin serialization.
type layout struct {
	Version int            `json:"version"`
	Plugins []pluginLayout `json:"plugins"`
}

type pluginLayout struct {
	DescriptorModule             string  `json:"descriptorModule"`
	AdditionalFrontendOnlyPlugin bool    `json:"additionalFrontendOnlyPlugin"`
	Entries                      []entry `json:"entries"`
}

// entry is one `PluginDistributionEntry`.
type entry struct {
	Kind               string  `json:"kind"`
	Name               string  `json:"name"`
	Path               *string `json:"path,omitempty"`
	RelativeOutputFile *string `json:"relativeOutputFile,omitempty"`
}

// assembledPart is one part and how the layout takes it.
type assembledPart struct {
	part *part
	// frontendOnly marks a plugin that only the frontend process started from the IDE loads. Its files are not in the
	// distribution, so its entries state no path.
	frontendOnly bool
	// content is the `<content>` order of the plugin descriptor, for a content-ordered part.
	content []contentModule
}

// asset is one jar in the order `JarPackager` creates it, with its modules in the order it reports them.
type asset struct {
	destination string
	modules     []string
	libraries   []resolvedLibrary
}

type resolvedLibrary struct {
	library jpsLibrary
	// files is the number of files of the library. `JarPackager` reports one entry for each file.
	files int
}

func assemble(parts []assembledPart, libraries *libraryIndex) (*layout, error) {
	result := &layout{Version: layoutVersion, Plugins: []pluginLayout{}}
	seen := make(map[string]bool)
	for _, assembled := range parts {
		p := assembled.part
		if seen[p.DescriptorModule] {
			return nil, fmt.Errorf("two parts have the descriptor module '%s'", p.DescriptorModule)
		}
		seen[p.DescriptorModule] = true
		assets, err := orderAssets(p, assembled.content, libraries)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", p.DescriptorModule, err)
		}
		plugin := pluginLayout{DescriptorModule: p.DescriptorModule, AdditionalFrontendOnlyPlugin: assembled.frontendOnly, Entries: []entry{}}
		for _, a := range assets {
			var path *string
			if !assembled.frontendOnly {
				value := a.destination
				if p.Directory != "" {
					value = p.Directory + "/" + a.destination
				}
				path = &value
			}
			relativeOutputFile := strings.TrimPrefix(a.destination, "lib/")
			for _, module := range a.modules {
				plugin.Entries = append(plugin.Entries, entry{Kind: "module", Name: module, Path: path, RelativeOutputFile: &relativeOutputFile})
			}
			for _, library := range a.libraries {
				for range max(library.files, 1) {
					plugin.Entries = append(plugin.Entries, entry{Kind: library.library.kind, Name: library.library.name, Path: path, RelativeOutputFile: &relativeOutputFile})
				}
			}
		}
		result.Plugins = append(result.Plugins, plugin)
	}
	return result, nil
}

// orderAssets returns the jars of p in the order `JarPackager` creates their assets.
//
// A layout-ordered part keeps its order and the merge order inside each jar. A plugin part states its jars in asset
// order, the order of its plan file, except for the reused content module jars. The content pass of
// `computeModuleSourcesByContent` creates each reused jar, so it goes among the other jars of that pass by `<content>`
// order. Inside a plugin jar, the content modules come first in `<content>` order, and the other modules and the
// libraries follow in merge order, as `computeDistributionFileEntries` reports them.
func orderAssets(p *part, content []contentModule, libraries *libraryIndex) ([]asset, error) {
	resolved := make([][]resolvedLibrary, len(p.Jars))
	for index, jar := range p.Jars {
		var jarModules []string
		for _, m := range jar.Members {
			if m.Module != "" {
				jarModules = append(jarModules, m.Module)
			}
		}
		for _, m := range jar.Members {
			if m.Library == "" {
				continue
			}
			library, err := libraries.resolve(m, jarModules)
			if err != nil {
				return nil, fmt.Errorf("%s: %w", jar.Destination, err)
			}
			resolved[index] = append(resolved[index], resolvedLibrary{library: library, files: len(m.Jars)})
		}
	}
	contentIndex := make(map[string]int, len(content))
	for position, module := range content {
		contentIndex[module.name] = position
	}
	order := make([]int, 0, len(p.Jars))
	if p.Order == layoutOrder {
		for index := range p.Jars {
			order = append(order, index)
		}
		if p.JarOrder != "" {
			var err error
			if order, err = orderByJarOrder(p); err != nil {
				return nil, err
			}
		}
	} else {
		var err error
		if order, err = pluginAssetOrder(p, content, contentIndex, resolved); err != nil {
			return nil, err
		}
	}

	assets := make([]asset, 0, len(order))
	for _, index := range order {
		var modules, other []string
		for _, m := range p.Jars[index].Members {
			if m.Module == "" {
				continue
			}
			if _, isContent := contentIndex[m.Module]; isContent && p.Order == pluginOrder {
				modules = append(modules, m.Module)
			} else {
				other = append(other, m.Module)
			}
		}
		slices.SortStableFunc(modules, func(a, b string) int { return contentIndex[a] - contentIndex[b] })
		assets = append(assets, asset{destination: p.Jars[index].Destination, modules: append(modules, other...), libraries: resolved[index]})
	}
	return assets, nil
}

// orderByJarOrder orders the jars of a layout part by its jar order file. The file and the part must name the same
// jars: a jar that one of them lacks has no producer or no place.
func orderByJarOrder(p *part) ([]int, error) {
	data, err := os.ReadFile(p.JarOrder)
	if err != nil {
		return nil, err
	}
	byDestination := make(map[string]int, len(p.Jars))
	for index, jar := range p.Jars {
		byDestination[strings.TrimPrefix(jar.Destination, "lib/")] = index
	}
	order := make([]int, 0, len(p.Jars))
	var unknown []string
	for _, line := range strings.Split(string(data), "\n") {
		if line == "" {
			continue
		}
		index, found := byDestination[line]
		if !found {
			unknown = append(unknown, line)
			continue
		}
		order = append(order, index)
		delete(byDestination, line)
	}
	if len(unknown) != 0 {
		return nil, fmt.Errorf("the jar order %s names jars that the part does not pack: %s", p.JarOrder, strings.Join(unknown, ", "))
	}
	if len(byDestination) != 0 {
		return nil, fmt.Errorf("the jar order %s does not name the packed jars %s", p.JarOrder, strings.Join(sortedKeys(byDestination), ", "))
	}
	return order, nil
}

// pluginAssetOrder merges the reused jars of a plugin part into the order of its other jars.
//
// The content pass creates its jars first, each at the first content module it places, so their `<content>` indices
// grow. The first jar that breaks this is the first jar of the layout pass, and every later jar belongs to that pass
// too. A jar with a project library and no module starts the layout pass, as `computeProjectLibrariesSources` creates
// it after every module. A jar with module libraries only does not end the content pass, because the module that owns
// them can be a content module. It stays behind the jar before it: the generator reads a module library entry by its
// owner and not by its place.
func pluginAssetOrder(p *part, content []contentModule, contentIndex map[string]int, resolved [][]resolvedLibrary) ([]int, error) {
	mainJar := ""
	for _, jar := range p.Jars {
		for _, m := range jar.Members {
			if m.Module == p.DescriptorModule && !jar.Reused {
				mainJar = strings.TrimPrefix(jar.Destination, "lib/")
			}
		}
	}
	// firstPlaced returns the `<content>` index of the first content module the content pass places in a jar, or -1.
	firstPlaced := func(jar partJar) int {
		first := -1
		for _, m := range jar.Members {
			position, isContent := contentIndex[m.Module]
			if m.Module == "" || !isContent || !placedByContent(content[position], strings.TrimPrefix(jar.Destination, "lib/"), mainJar) {
				continue
			}
			if first < 0 || position < first {
				first = position
			}
		}
		return first
	}

	type group struct {
		key  int
		jars []int
	}
	var contentPass []group
	var layoutPass []int
	var reused []group
	last := -1
	inLayoutPass := false
	for index, jar := range p.Jars {
		if jar.Reused {
			key := firstPlaced(jar)
			if key < 0 {
				return nil, fmt.Errorf("the reused jar %s holds no content module of the descriptor at its content module path", jar.Destination)
			}
			reused = append(reused, group{key: key, jars: []int{index}})
			continue
		}
		hasModule := slices.ContainsFunc(jar.Members, func(m member) bool { return m.Module != "" })
		moduleLibrariesOnly := !hasModule && !slices.ContainsFunc(resolved[index], func(library resolvedLibrary) bool { return library.library.kind != moduleLibraryKind })
		if !inLayoutPass && moduleLibrariesOnly && len(contentPass) != 0 {
			contentPass[len(contentPass)-1].jars = append(contentPass[len(contentPass)-1].jars, index)
			continue
		}
		if !inLayoutPass {
			if key := firstPlaced(jar); key > last {
				last = key
				contentPass = append(contentPass, group{key: key, jars: []int{index}})
				continue
			}
			inLayoutPass = true
		}
		layoutPass = append(layoutPass, index)
	}
	contentPass = append(contentPass, reused...)
	slices.SortStableFunc(contentPass, func(a, b group) int { return a.key - b.key })
	order := make([]int, 0, len(p.Jars))
	for _, g := range contentPass {
		order = append(order, g.jars...)
	}
	return append(order, layoutPass...), nil
}

// placedByContent tells whether `contentModuleJarPath` gives a content module the jar that the part places it in.
// A module elsewhere has a custom path, and the content pass leaves it to the layout. relativeOutputFile and mainJar
// are relative to the `lib` directory of the plugin.
func placedByContent(module contentModule, relativeOutputFile, mainJar string) bool {
	if module.loading == "embedded" {
		return relativeOutputFile == module.name+".jar"
	}
	return relativeOutputFile == "modules/"+module.name+".jar" || relativeOutputFile == mainJar ||
		(mainJar != "" && relativeOutputFile == strings.TrimSuffix(mainJar, ".jar")+"-frontend.jar")
}
