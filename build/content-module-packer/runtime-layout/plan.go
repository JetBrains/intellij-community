package main

import (
	"fmt"
	"strings"

	"jetbrains.com/content-module-packer/internal/planfile"
	"jetbrains.com/content-module-packer/internal/pluginpack"
)

// partFromPlan derives the part of a complex plugin from its resolved plan file and its input catalogue.
//
// Each jar asset of the plugin scope is one jar of the part, in plan order. A `module` source is a member, and so is a
// `prepared` source whose `module-filter` operation reads a module. A `library` source is a member with the files that
// the catalogue lists for it. A `file` source, such as the patched descriptor, and a `layout-assets` output merge no
// module. The plan file keeps the ID of a library, which is the label of its container.
//
// A reused content module jar is not in the catalogue, because its own target packs it. independentLibraries states
// the libraries of such jars, by label.
func partFromPlan(plan *planfile.File, catalogue *pluginpack.Catalogue, independentLibraries []member, descriptorModule, pluginDirectory, descriptor string) (*part, error) {
	roots := make(map[string]string, len(catalogue.Artifacts))
	for _, artifact := range catalogue.Artifacts {
		roots[artifact.ID] = artifact.Root
	}
	libraryFiles := make(map[string][]string, len(catalogue.Libraries))
	for _, library := range catalogue.Libraries {
		var files []string
		for _, file := range library.Files {
			root, found := roots[file.Artifact]
			if !found || file.Path != "" {
				return nil, fmt.Errorf("the catalogue library %s names the member %q, which is not a catalogue file", library.ID, file.Artifact)
			}
			files = append(files, root)
		}
		libraryFiles[library.ID] = files
	}
	independentFiles := make(map[string][]string, len(independentLibraries))
	for _, library := range independentLibraries {
		independentFiles[normalizeLabel(library.Library)] = library.Jars
	}
	filteredModules := make(map[string]string)
	for _, operation := range plan.Operations {
		if operation.Kind == "module-filter" && operation.Input != nil {
			filteredModules[operation.Output] = operation.Input.Artifact
		}
	}

	result := &part{Version: partVersion, DescriptorModule: descriptorModule, Directory: pluginDirectory, Order: pluginOrder, Descriptor: descriptor, Jars: []partJar{}}
	for _, planAsset := range plan.Assets {
		if planAsset.Recipe == nil || planAsset.Kind != "file" || !strings.HasSuffix(planAsset.Destination, ".jar") {
			continue
		}
		var members []member
		for _, source := range planAsset.Recipe.Sources {
			switch source.Kind {
			case "module":
				members = append(members, member{Module: source.Input})
			case "prepared":
				if module, found := filteredModules[source.Input]; found {
					members = append(members, member{Module: module})
				}
			case "library":
				files, found := libraryFiles[source.Input]
				if !found {
					files, found = independentFiles[normalizeLabel(source.Input)]
				}
				if !found {
					return nil, fmt.Errorf("%s merges the library %s, which neither the catalogue nor a reused jar lists", planAsset.Destination, source.Input)
				}
				members = append(members, member{Library: source.Input, Jars: files})
			}
		}
		if len(members) == 0 {
			continue
		}
		if planAsset.Scope == pluginpack.DistributionScope {
			// No plan places a jar with a module outside the plugin today. The entry path of such a jar is not settled.
			return nil, fmt.Errorf("%s is a jar of the distribution scope that merges a module or a library", planAsset.Destination)
		}
		if !strings.HasPrefix(planAsset.Destination, "lib/") {
			return nil, fmt.Errorf("%s merges a module or a library, but it is not under lib/", planAsset.Destination)
		}
		result.Jars = append(result.Jars, partJar{Destination: planAsset.Destination, Members: members})
	}
	if err := result.validate(); err != nil {
		return nil, err
	}
	return result, nil
}
