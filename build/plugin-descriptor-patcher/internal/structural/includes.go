// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package structural

import (
	"fmt"
	"regexp"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
)

// xPointerPattern is `JDOMUtil.XPOINTER_PATTERN`
// (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:78`). Java's `Matcher.matches` asks the whole
// text to match, which the anchors state here.
var xPointerPattern = regexp.MustCompile(`^xpointer\((.*)\)$`)

// childrenPattern is `JDOMUtil.CHILDREN_PATTERN`
// (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:81`).
var childrenPattern = regexp.MustCompile(`^/([^/]*)(/[^/]*)?/\*$`)

// IsIncludeElement is `isIncludeElementFor` (`contentModuleEmbedding.kt:504-506`).
//
// The namespace is matched by URI, so a descriptor that writes the prefix without declaring it does not state an
// include here, exactly as JDOM reads it.
func IsIncludeElement(element *descriptorxml.Element) bool {
	return element.Name == "include" && element.URI == XIncludeNamespace
}

// ResolveIncludes is `resolveIncludes` (`contentModuleEmbedding.kt:508-511`).
//
// It replaces each `xi:include` by what the include names, **at the include's own position**. That position is data:
// `intellij.database.plugin` states four includes after its own three `<content>` blocks, and the order of the
// resulting content modules is what a later stage asserts.
func ResolveIncludes(element *descriptorxml.Element, resolver *Resolver) error {
	if IsIncludeElement(element) {
		return fmt.Errorf("the root element is an <%s:include>, which cannot be resolved in place", element.Prefix)
	}
	return resolveNonXIncludeElementFromCache(element, resolver)
}

// resolveNonXIncludeElementFromCache is `doResolveNonXIncludeElementFromCache`
// (`contentModuleEmbedding.kt:569-586`).
//
// It walks the content list **backwards**, because replacing a child with a list of children moves every later index.
// A resolution that answers nothing leaves the include element in the tree, which is what an optional include does.
func resolveNonXIncludeElementFromCache(original *descriptorxml.Element, resolver *Resolver) error {
	for i := len(original.Children) - 1; i >= 0; i-- {
		content := original.Children[i]
		if content.Kind != descriptorxml.KindElement {
			continue
		}
		if IsIncludeElement(content.Element) {
			result, resolved, err := resolveXIncludeElement(content.Element, resolver)
			if err != nil {
				return err
			}
			if resolved {
				original.ReplaceChildAt(i, result)
			}
			continue
		}
		if err := resolveNonXIncludeElementFromCache(content.Element, resolver); err != nil {
			return err
		}
	}
	return nil
}

// resolveXIncludeElement is `resolveXIncludeElement` (`contentModuleEmbedding.kt:514-567`).
//
// The second return value is the platform's `null`: false means the include resolved to nothing and must stay in the
// tree. An empty slice with true means the include resolved to no element and must be deleted, which is a different
// answer and a different byte.
func resolveXIncludeElement(
	element *descriptorxml.Element,
	resolver *Resolver,
) ([]*descriptorxml.Element, bool, error) {
	href, stated := element.Attribute("href")
	if !stated {
		return nil, false, fmt.Errorf("missing href attribute")
	}

	if _, stated := element.PrefixedAttribute("xml", "base"); stated {
		return nil, false, fmt.Errorf("`base` attribute is not supported")
	}

	// The fallback is looked up in the include's **own** namespace, so `xi:fallback` under `xi:include`. Its presence
	// is what makes the include optional (`contentModuleEmbedding.kt:525`, `:529`).
	fallbackElement := element.ChildInNamespace("fallback", element.URI)
	_, hasIncludeUnless := element.Attribute("includeUnless")
	_, hasIncludeIf := element.Attribute("includeIf")
	isDynamic := hasIncludeUnless || hasIncludeIf

	remoteElement, err := resolver.ResolveElement(href, fallbackElement != nil, isDynamic)
	if err != nil {
		return nil, false, err
	}
	if remoteElement == nil {
		return nil, false, nil
	}

	remoteParsed, err := extractNeededChildrenFor(element, remoteElement)
	if err != nil {
		return nil, false, err
	}

	// Every child, recursively, so a nested include resolves too.
	i := 0
	for i < len(remoteParsed) {
		child := remoteParsed[i]
		if IsIncludeElement(child) {
			elements, resolved, err := resolveXIncludeElement(child, resolver)
			if err != nil {
				return nil, false, err
			}
			if resolved {
				if len(elements) == 0 {
					// Remove the include that resolves to nothing.
					remoteParsed = append(remoteParsed[:i], remoteParsed[i+1:]...)
					i--
				} else {
					// Replace the include by what it resolved to, and skip over the inserted elements.
					tail := append([]*descriptorxml.Element{}, remoteParsed[i+1:]...)
					remoteParsed = append(append(remoteParsed[:i], elements...), tail...)
					i += len(elements) - 1
				}
			}
		} else if err := resolveNonXIncludeElementFromCache(child, resolver); err != nil {
			return nil, false, err
		}
		i++
	}

	// `elementToDetach.detach()` has no counterpart here: every remote element comes from a tree this resolver parsed
	// for this one include, so no element is reachable from two parents (`contentModuleEmbedding.kt:563-565`).
	return remoteParsed, true, nil
}

// extractNeededChildrenFor is `extractNeededChildrenFor` (`contentModuleEmbedding.kt:589-616`).
//
// The default pointer takes every child of `<idea-plugin>`. A remote root with another name contributes nothing, which
// is a silent empty answer in the platform and stays one here.
func extractNeededChildrenFor(
	element *descriptorxml.Element,
	remoteElement *descriptorxml.Element,
) ([]*descriptorxml.Element, error) {
	xpointer, stated := element.Attribute("xpointer")
	if !stated {
		xpointer = "xpointer(/idea-plugin/*)"
	}

	pointerMatch := xPointerPattern.FindStringSubmatch(xpointer)
	if pointerMatch == nil {
		return nil, fmt.Errorf("unsupported XPointer: %s", xpointer)
	}
	pointer := pointerMatch[1]
	childrenMatch := childrenPattern.FindStringSubmatch(pointer)
	if childrenMatch == nil {
		return nil, fmt.Errorf("unsupported pointer: %s", pointer)
	}

	rootTagName := childrenMatch[1]
	current := remoteElement
	if current.Name != rootTagName {
		return nil, nil
	}

	subTagName := childrenMatch[2]
	if subTagName != "" {
		// cut off the slash
		child := current.Child(subTagName[1:])
		if child == nil {
			return nil, fmt.Errorf("child element not found: %s", subTagName[1:])
		}
		current = child
	}
	return current.ChildElements(), nil
}
