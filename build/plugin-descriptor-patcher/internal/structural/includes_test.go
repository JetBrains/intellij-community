// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package structural_test

import (
	"strings"
	"testing"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-patcher/internal/structural"
)

// The curated cases of the includes stage, one branch of `resolveXIncludeElement` each.
//
// The whole-population arm is elsewhere and it is two gates, both over real plugins. `./build/dev-dist.cmd descriptors`
// compares this binary's output against the text a dev assembly recorded, and its `--two-producer` mode compares this
// binary against the JVM tool over the same declared inputs. A curated case states one rule; those two state the
// population.

// xi is the namespace declaration every include case needs.
const xi = ` xmlns:xi="http://www.w3.org/2001/XInclude"`

// resolve reads a descriptor, resolves its includes against these declared files and returns the serialized result.
func resolve(t *testing.T, descriptor string, seed map[string]string) string {
	t.Helper()
	element := read(t, descriptor)
	if err := structural.ResolveIncludes(element, resolverOver(seed)); err != nil {
		t.Fatalf("resolve: %v", err)
	}
	return descriptorxml.Write(element)
}

func resolverOver(seed map[string]string) *structural.Resolver {
	content := map[string][]byte{}
	for loadPath, text := range seed {
		content[loadPath] = []byte(text)
	}
	return structural.NewResolver([]structural.Scope{
		{Modules: []string{"intellij.example"}, Cache: structural.NewCache(content)},
	})
}

func read(t *testing.T, descriptor string) *descriptorxml.Element {
	t.Helper()
	element, err := descriptorxml.Read(descriptor)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	return element
}

func equals(t *testing.T, got string, want string) {
	t.Helper()
	if got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}

// The include is replaced **where it stands**, and the position is data: `intellij.database.plugin` states four
// includes after its own three `<content>` blocks, and the resulting content-module order is what the embedding stage
// asserts (`contentModuleEmbedding.kt:577`).
func TestAnIncludeIsReplacedAtItsOwnPosition(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><content><module name="a"/></content>`+
			`<xi:include href="mid.xml"/>`+
			`<content><module name="z"/></content></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><content><module name="m"/></content></idea-plugin>`})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <content>
    <module name="a" />
  </content>
  <content>
    <module name="m" />
  </content>
  <content>
    <module name="z" />
  </content>
</idea-plugin>`)
}

// The negative control of the case above, stated as a case of its own: a resolver that appended, or that inserted at
// position 0, would produce these bytes instead. The two texts must differ, or the position rule is untested.
func TestThePositionOfAnIncludeChangesTheBytes(t *testing.T) {
	atItsPosition := resolve(t,
		`<idea-plugin`+xi+`><content><module name="a"/></content><xi:include href="mid.xml"/></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><content><module name="m"/></content></idea-plugin>`})
	atPositionZero := resolve(t,
		`<idea-plugin`+xi+`><xi:include href="mid.xml"/><content><module name="a"/></content></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><content><module name="m"/></content></idea-plugin>`})
	if atItsPosition == atPositionZero {
		t.Error("the two positions produced one text, so this pair proves nothing about placement")
	}
	if !strings.HasPrefix(atPositionZero, "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <content>\n    <module name=\"m\"") {
		t.Errorf("an include at position 0 must contribute first:\n%s", atPositionZero)
	}
}

// An include with an `xi:fallback` is optional, and an optional include is **not** resolved at build time: the module
// it names may be excluded at runtime (`contentModuleEmbedding.kt:405-411`, `:525`, `:529`). The element stays, and so
// does its fallback.
func TestAnIncludeWithAFallbackStaysUnresolved(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><xi:include href="mid.xml"><xi:fallback/></xi:include></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><extensions/></idea-plugin>`})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <xi:include href="mid.xml">
    <xi:fallback />
  </xi:include>
</idea-plugin>`)
}

// The fallback is looked up in the include's own namespace. A `<fallback/>` with no namespace is not one, so the
// include is **not** optional and does resolve. That is the negative control of the case above, and it is the
// difference `Element.getChild(String, Namespace)` makes.
func TestAFallbackWithNoNamespaceDoesNotMakeAnIncludeOptional(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><xi:include href="mid.xml"><fallback/></xi:include></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><extensions/></idea-plugin>`})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <extensions />
</idea-plugin>`)
}

// `includeIf` and `includeUnless` make an include dynamic, and a dynamic include is left alone for the same reason an
// optional one is (`contentModuleEmbedding.kt:526`).
func TestADynamicIncludeStaysUnresolved(t *testing.T) {
	for _, attribute := range []string{"includeIf", "includeUnless"} {
		t.Run(attribute, func(t *testing.T) {
			got := resolve(t,
				`<idea-plugin`+xi+`><xi:include href="mid.xml" `+attribute+`="a.property"/></idea-plugin>`,
				map[string]string{"META-INF/mid.xml": `<idea-plugin><extensions/></idea-plugin>`})
			if !strings.Contains(got, "xi:include") {
				t.Errorf("a dynamic include must survive:\n%s", got)
			}
		})
	}
}

// A nested include resolves too, and it resolves into the position it holds inside what the outer include contributed
// (`contentModuleEmbedding.kt:536-561`).
func TestANestedIncludeResolves(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><xi:include href="outer.xml"/></idea-plugin>`,
		map[string]string{
			"META-INF/outer.xml": `<idea-plugin` + xi + `><one/><xi:include href="inner.xml"/><three/></idea-plugin>`,
			"META-INF/inner.xml": `<idea-plugin><two/></idea-plugin>`,
		})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <one />
  <two />
  <three />
</idea-plugin>`)
}

// A nested include whose remote root does not match the pointer contributes no element, and the include element is
// then **deleted** rather than left in place. An empty answer and no answer are different bytes
// (`contentModuleEmbedding.kt:542-546`).
func TestANestedIncludeThatContributesNothingIsDeleted(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><xi:include href="outer.xml"/></idea-plugin>`,
		map[string]string{
			"META-INF/outer.xml": `<idea-plugin` + xi + `><one/><xi:include href="inner.xml"/></idea-plugin>`,
			// the remote root is not `idea-plugin`, so the default pointer matches nothing
			"META-INF/inner.xml": `<something><two/></something>`,
		})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <one />
</idea-plugin>`)
}

// An include one level down resolves: the walk recurses into every non-include child
// (`contentModuleEmbedding.kt:580-583`).
func TestAnIncludeBelowTheRootResolves(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><extensions><xi:include href="mid.xml"/></extensions></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><one/></idea-plugin>`})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <extensions>
    <one />
  </extensions>
</idea-plugin>`)
}

// An `xpointer` that names a sub-element contributes that element's children and not the root's
// (`contentModuleEmbedding.kt:610-614`).
func TestAnXPointerWithASubTagTakesThatChildsChildren(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><xi:include href="mid.xml" xpointer="xpointer(/idea-plugin/extensions/*)"/></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><id>skipped</id><extensions><one/><two/></extensions></idea-plugin>`})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <one />
  <two />
</idea-plugin>`)
}

// A remote root the pointer does not name contributes nothing, silently, and the include is deleted
// (`contentModuleEmbedding.kt:605-608`).
func TestARemoteRootWithAnotherNameContributesNothing(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><id>a</id><xi:include href="mid.xml"/></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<other><one/></other>`})

	equals(t, got, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <id>a</id>
</idea-plugin>`)
}

// The three shapes of `toLoadPath`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/PlatformModules.kt:548-555`).
func TestTheLoadPathOfAnHref(t *testing.T) {
	for href, want := range map[string]string{
		"plugin.xml":                     "META-INF/plugin.xml",
		"/META-INF/plugin.xml":           "META-INF/plugin.xml",
		"intellij.example.module.xml":    "intellij.example.module.xml",
		"fleet.example.module.xml":       "fleet.example.module.xml",
		"a/relative/path.xml":            "META-INF/a/relative/path.xml",
		"/intellij.at.the.root.only.xml": "intellij.at.the.root.only.xml",
	} {
		if got := structural.ToLoadPath(href); got != want {
			t.Errorf("%s: got %s, want %s", href, got, want)
		}
	}
}

// A load path no declared file answers fails, and the failure names the path and every declared one. Every other step
// of the platform's search reads a JPS project model, which the action refuses to load
// (`DevDistPluginDescriptorMain.kt:243-277`).
func TestAnUnresolvableIncludeFails(t *testing.T) {
	element := read(t, `<idea-plugin`+xi+`><xi:include href="absent.xml"/></idea-plugin>`)
	err := structural.ResolveIncludes(element, resolverOver(map[string]string{
		"META-INF/present.xml": `<idea-plugin/>`,
	}))
	if err == nil {
		t.Fatal("an unresolvable include must fail")
	}
	for _, expected := range []string{"META-INF/absent.xml", "META-INF/present.xml"} {
		if !strings.Contains(err.Error(), expected) {
			t.Errorf("the failure must name %s: %v", expected, err)
		}
	}
}

// An `xml:base` is refused rather than ignored (`contentModuleEmbedding.kt:520-523`).
func TestAnXmlBaseIsRefused(t *testing.T) {
	element := read(t, `<idea-plugin`+xi+`><xi:include href="mid.xml" xml:base="somewhere"/></idea-plugin>`)
	err := structural.ResolveIncludes(element, resolverOver(map[string]string{"META-INF/mid.xml": `<idea-plugin/>`}))
	if err == nil || !strings.Contains(err.Error(), "`base` attribute is not supported") {
		t.Errorf("got %v", err)
	}
}

// An include with no `href` is refused (`contentModuleEmbedding.kt:518`).
func TestAnIncludeWithNoHrefIsRefused(t *testing.T) {
	element := read(t, `<idea-plugin`+xi+`><xi:include/></idea-plugin>`)
	err := structural.ResolveIncludes(element, resolverOver(nil))
	if err == nil || !strings.Contains(err.Error(), "missing href") {
		t.Errorf("got %v", err)
	}
}

// An unsupported pointer is refused, both halves of it (`contentModuleEmbedding.kt:593`, `:599`).
func TestAnUnsupportedXPointerIsRefused(t *testing.T) {
	for name, pointer := range map[string]string{
		"not an xpointer":     "/idea-plugin/*",
		"not a children path": "xpointer(//idea-plugin)",
	} {
		t.Run(name, func(t *testing.T) {
			element := read(t, `<idea-plugin`+xi+`><xi:include href="mid.xml" xpointer="`+pointer+`"/></idea-plugin>`)
			err := structural.ResolveIncludes(element, resolverOver(map[string]string{
				"META-INF/mid.xml": `<idea-plugin><one/></idea-plugin>`,
			}))
			if err == nil || !strings.Contains(err.Error(), "nsupported") {
				t.Errorf("got %v", err)
			}
		})
	}
}

// A prefix the descriptor never declared is not the XInclude namespace, so the element is an ordinary child. JDOM
// matches a namespace by URI (`contentModuleEmbedding.kt:505`), and this is the trap a text search falls into.
func TestAnUndeclaredPrefixIsNotAnInclude(t *testing.T) {
	got := resolve(t,
		`<idea-plugin><xi:include href="mid.xml"/></idea-plugin>`,
		map[string]string{"META-INF/mid.xml": `<idea-plugin><one/></idea-plugin>`})
	if !strings.Contains(got, "xi:include") {
		t.Errorf("an undeclared prefix names no include:\n%s", got)
	}
}

// A `<content>` inside a CDATA body is prose. The stage walks the tree, so it cannot see it - which is the trap
// `build/internal/content/descriptor.go` measured at 152 false survivors over one product.
func TestAnIncludeInsideCdataIsProse(t *testing.T) {
	got := resolve(t,
		`<idea-plugin`+xi+`><description><![CDATA[<xi:include href="absent.xml"/>]]></description></idea-plugin>`,
		nil)
	if !strings.Contains(got, "&lt;xi:include href=&quot;absent.xml&quot;/&gt;") {
		t.Errorf("the prose must survive as escaped text:\n%s", got)
	}
}

// The root itself may not be an include (`contentModuleEmbedding.kt:509`).
func TestAnIncludeRootIsRefused(t *testing.T) {
	element := read(t, `<xi:include`+xi+` href="mid.xml"/>`)
	if err := structural.ResolveIncludes(element, resolverOver(nil)); err == nil {
		t.Error("an include root must be refused")
	}
}

// The extra search path is prepended for a content module the search path does not name, and the resolver is returned
// unchanged for one it does (`contentModuleEmbedding.kt:384-403`).
func TestTheExtraSearchPathIsAddedOnlyForAnUnknownModule(t *testing.T) {
	cache := structural.NewCache(nil)
	resolver := structural.NewResolver([]structural.Scope{{Modules: []string{"intellij.example"}, Cache: cache}})
	if resolver.CopyWithExtraSearchPath("intellij.example", cache) != resolver {
		t.Error("a module the search path names must answer the same resolver")
	}
	if resolver.CopyWithExtraSearchPath("intellij.other", cache) == resolver {
		t.Error("a module the search path does not name must prepend a scope")
	}
}
