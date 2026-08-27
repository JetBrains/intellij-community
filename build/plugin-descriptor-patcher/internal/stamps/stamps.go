// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The stamps stage of the plugin descriptor patch.
//
// This is `doPatchPluginXml`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt:250-294`), the second of
// the seven stages `DevDistDescriptorStage` names. It runs over the element tree the round trip parsed, and it is the
// one stage that every plugin's descriptor reaches: a dev assembly's own report shows it changing all 162 texts of one
// product.
//
// It does four things, and each one moves bytes:
//
//  1. stamps `since-build` and `until-build` onto `idea-version`, creating the element when the descriptor has none;
//  2. stamps the plugin's version as the text of `version`, again creating the element;
//  3. detaches `product-descriptor` for a bundled plugin, or stamps `eap`, `release-date` and `release-version` on it;
//  4. wraps the text of `description` and `change-notes` back into a CDATA section, which is what shrinks a descriptor
//     rather than growing it: the round trip escaped that prose, and the CDATA frame un-escapes it.
//
// The **position** of a created element is data, not a detail. `getOrCreateTopElement` puts a new element straight
// after the first of `id` or `name`, and at position 0 when the descriptor states neither
// (`PluginXmlPatcher.kt:296-315`). Nothing else in the stage decides where a line lands.
package stamps

import (
	"strings"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
)

// Request is every fact the stamps stage reads, and nothing else.
//
// It is the subset of `PluginDescriptorPatchRequest` (`PluginXmlPatcher.kt:57-77`) that `doPatchPluginXml` takes. The
// fields that request holds for the other stages, or for the report, are absent on purpose: a field this stage cannot
// read must not be reachable from it.
type Request struct {
	// Version is the text of `<version>`. An empty string is what a null `pluginVersion` produces, and it writes
	// `<version />`.
	Version string
	// SinceBuild and UntilBuild are the pair `getCompatiblePlatformVersionRange` returns.
	SinceBuild string
	UntilBuild string
	// ReleaseDate and ReleaseVersion reach `product-descriptor` only.
	ReleaseDate    string
	ReleaseVersion string
	// ToPublish and RetainProductDescriptorForBundledPlugin decide whether `product-descriptor` survives.
	ToPublish                               bool
	RetainProductDescriptorForBundledPlugin bool
	// IsEap sets or clears the `eap` attribute of `product-descriptor`.
	IsEap bool
}

// cdataElements are the two elements whose prose returns to a CDATA section, in the order the platform visits them
// (`PluginXmlPatcher.kt:286`).
var cdataElements = []string{"description", "change-notes"}

// anchors are the two children a created element goes after, in priority order (`PluginXmlPatcher.kt:261`, `:264`).
var anchors = []string{"id", "name"}

// Apply runs the stamps stage over the root element, in place.
func Apply(root *descriptorxml.Element, request Request) {
	ideaVersion := GetOrCreateTopElement(root, "idea-version", anchors)
	ideaVersion.SetAttribute("since-build", request.SinceBuild)
	ideaVersion.SetAttribute("until-build", request.UntilBuild)

	version := GetOrCreateTopElement(root, "version", anchors)
	version.SetText(request.Version)

	if productDescriptor := root.Child("product-descriptor"); productDescriptor != nil {
		if !request.ToPublish && !request.RetainProductDescriptorForBundledPlugin {
			root.RemoveChild(productDescriptor)
		} else {
			if request.IsEap {
				productDescriptor.SetAttribute("eap", "true")
			} else {
				productDescriptor.RemoveAttribute("eap")
			}
			// A release date the descriptor already states wins, unless it starts with `__`. That prefix is how a
			// descriptor states a placeholder the build must replace (`PluginXmlPatcher.kt:276-280`).
			stated, present := productDescriptor.Attribute("release-date")
			if !present || strings.HasPrefix(stated, "__") {
				productDescriptor.SetAttribute("release-date", request.ReleaseDate)
			}
			productDescriptor.SetAttribute("release-version", request.ReleaseVersion)
		}
	}

	// The round trip escaped this prose as element text, so the frame has to go back on. An empty element keeps its
	// text content, because `CDATA("")` is not what the platform writes there (`PluginXmlPatcher.kt:286-293`).
	for _, name := range cdataElements {
		element := root.Child(name)
		if element == nil {
			continue
		}
		text := element.Text()
		if text != "" {
			element.SetCDATA(text)
		}
	}
}

// GetOrCreateTopElement is `getOrCreateTopElement` (`PluginXmlPatcher.kt:296-315`).
//
// It returns the existing child when the root has one. Otherwise it creates one, straight after the first anchor the
// root states, or at position 0 when the root states none of them.
func GetOrCreateTopElement(root *descriptorxml.Element, name string, anchors []string) *descriptorxml.Element {
	if existing := root.Child(name); existing != nil {
		return existing
	}
	created := &descriptorxml.Element{Name: name}
	for _, anchorName := range anchors {
		anchor := root.Child(anchorName)
		if anchor == nil {
			continue
		}
		root.InsertChild(root.IndexOfChild(anchor)+1, created)
		return created
	}
	root.InsertChild(0, created)
	return created
}
