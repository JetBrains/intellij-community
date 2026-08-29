// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The two structural stages of the plugin descriptor patch: `includes` and `contentModules`.
//
// `internal/stamps` runs over the plugin's own descriptor and reads nothing else. These two stages read **other**
// descriptors: an `xi:include` names a file, and a `<module/>` of `<content>` receives that module's own descriptor as
// a CDATA body. So this package needs a descriptor cache, and it needs the cache to answer from declared files alone.
//
// The platform sites this mirrors, each rule carrying its own `file:line`:
//
//   - `community/platform/build-scripts/src/org/jetbrains/intellij/build/classPath/contentModuleEmbedding.kt` -
//     `resolveIncludes`, `resolveXIncludeElement`, `extractNeededChildrenFor`,
//     `resolveAndEmbedContentModuleDescriptor` and `XIncludeElementResolverImpl`;
//   - `community/platform/build-scripts/src/org/jetbrains/intellij/build/dev/DevDistPluginDescriptorMain.kt` -
//     the plan-driven content stage, its order assertion and the three `separate-jar` gates.
//
// ### The search collapses to the cache, and that is the whole point of the action
//
// `XIncludeElementResolverImpl.resolveElement` (`contentModuleEmbedding.kt:405-488`) searches a cache, then a module
// output, then module dependencies, then every module of the project. Every step but the first needs a JPS project
// model. `DevDistPluginDescriptorMain` hands the resolver a `RefusingModuleOutputProvider` whose every method throws
// (`RefusingDescriptorResolveContext` of `DevDistPluginDescriptorMain.kt`), so a run that reaches one of those
// steps fails rather than loading a
// model.
//
// This port therefore searches the cache and nothing else, and it fails with the same message shape where the JVM tool
// would throw. The two-pass loop of `DescriptorSearchPass` collapses with it: a cache lookup does not depend on the
// pass, so the second pass can only repeat the first.
package structural

import (
	"fmt"
	"strings"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
)

// XIncludeNamespace is `JDOMUtil.XINCLUDE_NAMESPACE`
// (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:76`).
const XIncludeNamespace = "http://www.w3.org/2001/XInclude"

// xmlNamespace is `Namespace.XML_NAMESPACE`, which `resolveXIncludeElement` reads `base` from
// (`contentModuleEmbedding.kt:520`).
const xmlNamespace = "http://www.w3.org/XML/1998/namespace"

// Cache is a descriptor cache seeded from declared files, keyed by the load path a resolver asks for.
//
// It is `SeededDescriptorContainer` (`DevDistPluginDescriptorMain.kt`), which is the seam that lets the patch
// run with no project model. `PutIfAbsent` exists because the platform's resolver writes what it found back into the
// cache; here every hit is already seeded, so the write is the identity. It is kept so that a reader can put this file
// beside `resolveElement` and find every line of it.
type Cache struct {
	content map[string][]byte
}

// NewCache returns a cache holding these load paths.
func NewCache(seed map[string][]byte) *Cache {
	content := make(map[string][]byte, len(seed))
	for loadPath, data := range seed {
		content[loadPath] = data
	}
	return &Cache{content: content}
}

// Get is `ScopedCachedDescriptorContainer.getCachedFileData`.
func (c *Cache) Get(loadPath string) ([]byte, bool) {
	data, held := c.content[loadPath]
	return data, held
}

// PutIfAbsent is `ScopedCachedDescriptorContainer.putIfAbsent`.
func (c *Cache) PutIfAbsent(loadPath string, data []byte) {
	if _, held := c.content[loadPath]; !held {
		c.content[loadPath] = data
	}
}

// LoadPaths returns every load path the cache holds. The refusal message names them, because the fix for an
// unresolvable include is always a missing declaration in the plan.
func (c *Cache) LoadPaths() []string {
	result := make([]string, 0, len(c.content))
	for loadPath := range c.content {
		result = append(result, loadPath)
	}
	return result
}

// Scope is `DescriptorSearchScope` (`contentModuleEmbedding.kt:61-71`) with the two fields a cache-only search reads.
//
// `searchInDependencies` is absent: it decides whether the search walks module dependencies, and no step of this port
// walks a module at all.
type Scope struct {
	// Modules is the scope's JPS module names. It decides nothing here but the answer of
	// [Resolver.CopyWithExtraSearchPath], which is where the platform reads it too.
	Modules []string
	Cache   *Cache
}

// Resolver is `XIncludeElementResolverImpl` (`contentModuleEmbedding.kt:380-489`) over a cache-only search.
type Resolver struct {
	searchPath []Scope
}

// NewResolver returns a resolver over these scopes, in search order.
func NewResolver(searchPath []Scope) *Resolver {
	return &Resolver{searchPath: searchPath}
}

// CopyWithExtraSearchPath is `copyWithExtraSearchPath` (`contentModuleEmbedding.kt:384-403`).
//
// A content module's own descriptor may state an `xi:include` of a file that only that module holds, so the module goes
// to the front of the search path while its descriptor is resolved. A scope that already names the module answers
// instead, and the resolver is returned unchanged.
//
// The `require` that guards a container mismatch is not ported. It runs only when the product-properties class is
// `org.jetbrains.intellij.build.IdeaUltimateProperties` (`contentModuleEmbedding.kt:388`), and this action has no
// product properties: `DevDistPluginDescriptorMain.NO_PRODUCT_PROPERTIES` is the string it answers with
// (`DevDistPluginDescriptorMain.kt`). So the branch is unreachable from here.
func (r *Resolver) CopyWithExtraSearchPath(moduleName string, cache *Cache) *Resolver {
	for _, scope := range r.searchPath {
		for _, module := range scope.Modules {
			if module == moduleName {
				return r
			}
		}
	}
	extended := make([]Scope, 0, len(r.searchPath)+1)
	extended = append(extended, Scope{Modules: []string{moduleName}, Cache: cache})
	extended = append(extended, r.searchPath...)
	return &Resolver{searchPath: extended}
}

// ResolveElement is `resolveElement` (`contentModuleEmbedding.kt:405-488`) over the cache alone.
//
// An optional or a dynamic include resolves to nothing, and the element then stays in the tree unresolved
// (`contentModuleEmbedding.kt:406-411`). A load path no cache holds is an error, because every step that could answer
// it needs a project model this action refuses to load.
func (r *Resolver) ResolveElement(relativePath string, isOptional bool, isDynamic bool) (*descriptorxml.Element, error) {
	if isOptional || isDynamic {
		// It is not safe to resolve an optional include at build time: the module it names may be excluded at runtime.
		return nil, nil
	}

	loadPath := ToLoadPath(relativePath)
	for _, scope := range r.searchPath {
		if data, held := scope.Cache.Get(loadPath); held {
			return descriptorxml.Read(string(data))
		}
	}
	declared := "none"
	if paths := r.declaredLoadPaths(); len(paths) != 0 {
		declared = strings.Join(sorted(paths), ", ")
	}
	return nil, fmt.Errorf(
		"cannot resolve '%s': no declared descriptor answers it. The plan of this plugin is incomplete: add the "+
			"descriptor the patch asked for. The declared load paths are %s",
		loadPath, declared)
}

func (r *Resolver) declaredLoadPaths() []string {
	var result []string
	seen := map[string]bool{}
	for _, scope := range r.searchPath {
		for _, loadPath := range scope.Cache.LoadPaths() {
			if !seen[loadPath] {
				seen[loadPath] = true
				result = append(result, loadPath)
			}
		}
	}
	return result
}

// ToLoadPath is `LoadPathUtil.toLoadPath`
// (`community/platform/pluginSystem/parser/impl/src/com/intellij/platform/pluginSystem/parser/impl/LoadPathUtil.kt`),
// which the plugin loader and the plan generator both call. The three prefixes are its whole rule. `kotlin.` is the
// third one, and KTIJ-29799 owns it. `TestTheLoadPathOfAnHref` pins every shape.
func ToLoadPath(relativePath string) string {
	switch {
	case strings.HasPrefix(relativePath, "/"):
		return relativePath[1:]
	case strings.HasPrefix(relativePath, "intellij."),
		strings.HasPrefix(relativePath, "fleet."),
		strings.HasPrefix(relativePath, "kotlin."):
		return relativePath
	default:
		return "META-INF/" + relativePath
	}
}

// ContentModuleDescriptorFileName is `contentModuleNameToDescriptorFileName`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productModuleLayout.kt:257`).
func ContentModuleDescriptorFileName(moduleName string) string {
	return strings.ReplaceAll(moduleName, "/", ".") + ".xml"
}

func sorted(values []string) []string {
	result := append([]string{}, values...)
	for i := 1; i < len(result); i++ {
		for j := i; j > 0 && result[j] < result[j-1]; j-- {
			result[j], result[j-1] = result[j-1], result[j]
		}
	}
	return result
}
