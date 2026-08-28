// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package structural

import (
	"fmt"
	"strings"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
)

// ContentRequest is the plan's statement about one plugin's content modules.
//
// It is the subset of `DevDistPluginDescriptorRequest` the content stage reads
// (`DevDistPluginDescriptorMain.kt:56-82`). The plan states the survivors in order, because the assembly's own filter
// reads the JPS project model and no action may.
type ContentRequest struct {
	// MainModule names the plugin in every failure.
	MainModule string
	// Modules are the content modules that survive the filter, in the order the descriptor must keep them.
	Modules []string
	// SeparateJar is which content module's embedded descriptor takes `separate-jar="true"`. A deviation, so normally
	// empty.
	SeparateJar map[string]bool
	// Embeds is false for a layout that embeds no content-module descriptor. Such a descriptor keeps its `<module/>`
	// elements empty, and the filter still runs.
	Embeds bool
}

// EmbedContentModules is `embedContentModulesFromPlan` (`DevDistPluginDescriptorMain.kt:146-198`).
//
// Two things happen, in this order. Every `<module/>` the plan does not name is removed, wherever it stands. Then each
// survivor receives its own module's descriptor as a CDATA body, unless the layout embeds none.
//
// The order assertion between the two is not a sanity check. The plan states the content modules as a list, the
// descriptor states them as elements, and the two are joined by position: a descriptor that lists them in another order
// would take the wrong `separate-jar` verdicts in silence. So the action refuses, and the refusal names both orders.
func EmbedContentModules(
	rootElement *descriptorxml.Element,
	request ContentRequest,
	cache *Cache,
	resolver *Resolver,
) error {
	survivors := make(map[string]bool, len(request.Modules))
	for _, name := range request.Modules {
		survivors[name] = true
	}

	type keptModule struct {
		element *descriptorxml.Element
		name    string
	}
	var kept []keptModule
	var keptNames []string
	for _, content := range rootElement.ChildElementsNamed("content") {
		for _, moduleElement := range content.ChildElementsNamed("module") {
			moduleName, stated := moduleElement.Attribute("name")
			if !stated {
				return fmt.Errorf("a <module/> of %s states no name", request.MainModule)
			}
			if survivors[moduleName] {
				kept = append(kept, keptModule{element: moduleElement, name: moduleName})
				keptNames = append(keptNames, moduleName)
				continue
			}
			content.RemoveChild(moduleElement)
		}
	}

	if !sameOrder(keptNames, request.Modules) {
		return fmt.Errorf(
			"the plan of %s states the content modules [%s], and its descriptor states [%s]",
			request.MainModule, strings.Join(request.Modules, ", "), strings.Join(keptNames, ", "))
	}

	if !request.Embeds {
		return nil
	}

	for _, module := range kept {
		if err := resolveAndEmbedContentModuleDescriptor(module.element, module.name, request, cache, resolver); err != nil {
			return err
		}
	}
	return nil
}

// sameOrder is Kotlin's `List<String> == List<String>`, which is element-wise and ordered.
func sameOrder(left []string, right []string) bool {
	if len(left) != len(right) {
		return false
	}
	for i := range left {
		if left[i] != right[i] {
			return false
		}
	}
	return true
}

// resolveAndEmbedContentModuleDescriptor is `resolveAndEmbedContentModuleDescriptor`
// (`contentModuleEmbedding.kt:332-353`) with the descriptor modifier of the plan-driven stage
// (`DevDistPluginDescriptorMain.kt:184-194`).
func resolveAndEmbedContentModuleDescriptor(
	moduleElement *descriptorxml.Element,
	moduleName string,
	request ContentRequest,
	cache *Cache,
	resolver *Resolver,
) error {
	// A `<module/>` that already holds content keeps it. The whole stage is the one that puts a body there, so this is
	// what makes the stage idempotent (`contentModuleEmbedding.kt:339-341`).
	if len(moduleElement.Children) != 0 {
		return nil
	}

	descriptor, err := resolveContentModuleDescriptor(
		moduleName,
		cache,
		resolver.CopyWithExtraSearchPath(moduleName, cache),
	)
	if err != nil {
		return err
	}

	applySeparateJar(descriptor, moduleName, request)
	moduleElement.SetCDATA(descriptorxml.Write(descriptor))
	return nil
}

// applySeparateJar is the descriptor modifier of the plan-driven content stage
// (`DevDistPluginDescriptorMain.kt:184-194`), which is `embedContentModule`'s modifier
// (`contentModuleEmbedding.kt:284-299`) with the plan answering the third gate.
//
// Three gates, in the platform's order:
//
//  1. the embedded descriptor states a `package` attribute. Without one, `separate-jar` decides nothing at runtime;
//  2. the content-module name holds no `/`. Kotlin's `substringBeforeLast` answers the whole string when the delimiter
//     is absent, so `jpsModuleName == moduleName` is exactly that test. A name that holds a `/` points at a descriptor
//     of another module, and the assembly asks the verdict of the module before the `/` only when the two are the same
//     string;
//  3. the plan names the module in `separate_jar`.
func applySeparateJar(descriptor *descriptorxml.Element, moduleName string, request ContentRequest) {
	if _, stated := descriptor.Attribute("package"); !stated {
		return
	}
	if strings.Contains(moduleName, "/") {
		return
	}
	if !request.SeparateJar[moduleName] {
		return
	}
	descriptor.SetAttribute("separate-jar", "true")
}

// resolveContentModuleDescriptor is `resolveContentModuleDescriptor` (`contentModuleEmbedding.kt:303-330`).
//
// The cache branch is the only one ported. A miss reaches `findUnprocessedDescriptorContent` over the output provider,
// which this action refuses, so a miss is the plan's incompleteness and says so.
func resolveContentModuleDescriptor(
	moduleName string,
	cache *Cache,
	resolver *Resolver,
) (*descriptorxml.Element, error) {
	descriptorFilename := ContentModuleDescriptorFileName(moduleName)
	data, held := cache.Get(descriptorFilename)
	if !held {
		return nil, fmt.Errorf(
			"cannot find file %s of the content module %s: no declared descriptor answers it. The plan of this "+
				"plugin is incomplete: add the descriptor the patch asked for",
			descriptorFilename, moduleName)
	}
	element, err := descriptorxml.Read(string(data))
	if err != nil {
		return nil, fmt.Errorf("%s: %w", descriptorFilename, err)
	}
	if err := ResolveIncludes(element, resolver); err != nil {
		return nil, fmt.Errorf("%s: %w", descriptorFilename, err)
	}
	return element, nil
}
