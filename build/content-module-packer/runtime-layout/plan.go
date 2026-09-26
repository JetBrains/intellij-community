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
// the catalogue lists for it. An `archive` source is a member with its one catalogue file: the plan names a single jar
// of a library by the label of that jar. A `file` source, such as the patched descriptor, and a `layout-assets` output
// merge no module. The plan file keeps the ID of a library, which is the label of its container. Any other source
// fails, so that a new kind of source cannot silently leave a jar out of the repository.
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
	operationKinds := make(map[string]string, len(plan.Operations))
	filteredModules := make(map[string]string)
	for _, operation := range plan.Operations {
		operationKinds[operation.Output] = operation.Kind
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
				switch operationKinds[source.Input] {
				case "module-filter":
					module, found := filteredModules[source.Input]
					if !found {
						return nil, fmt.Errorf("%s merges the module filter output %s, which reads no module", planAsset.Destination, source.Input)
					}
					members = append(members, member{Module: module})
				case "layout-assets":
				default:
					return nil, fmt.Errorf("%s merges the prepared source %s, which no module-filter or layout-assets operation writes", planAsset.Destination, source.Input)
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
			case "archive":
				root, found := roots[source.Input]
				if !found {
					return nil, fmt.Errorf("%s merges the archive %s, which the catalogue does not list", planAsset.Destination, source.Input)
				}
				members = append(members, member{Library: source.Input, Jars: []string{root}})
			case "file":
			default:
				return nil, fmt.Errorf("%s merges the %s source %s, which the runtime layout does not read", planAsset.Destination, source.Kind, source.Input)
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
