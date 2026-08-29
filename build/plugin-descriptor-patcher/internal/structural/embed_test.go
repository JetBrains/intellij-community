// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package structural_test

import (
	"strings"
	"testing"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-patcher/internal/structural"
)

// The curated cases of the content-module stage, one branch each.

// embed runs the stage over a descriptor with these declared content-module descriptors.
func embed(
	t *testing.T,
	descriptor string,
	request structural.ContentRequest,
	seed map[string]string,
) (string, error) {
	t.Helper()
	element := read(t, descriptor)
	content := map[string][]byte{}
	for loadPath, text := range seed {
		content[loadPath] = []byte(text)
	}
	cache := structural.NewCache(content)
	resolver := structural.NewResolver([]structural.Scope{{Modules: []string{"intellij.example"}, Cache: cache}})
	if err := structural.EmbedContentModules(element, request, cache, resolver); err != nil {
		return "", err
	}
	return descriptorxml.Write(element), nil
}

func embedOrFail(t *testing.T, descriptor string, request structural.ContentRequest, seed map[string]string) string {
	t.Helper()
	got, err := embed(t, descriptor, request, seed)
	if err != nil {
		t.Fatalf("embed: %v", err)
	}
	return got
}

// The plain case: a kept `<module/>` receives its own module's descriptor as a CDATA body
// (`contentModuleEmbedding.kt:352`).
func TestAKeptModuleReceivesItsDescriptorAsCdata(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Embeds: true},
		map[string]string{"a.b.xml": `<idea-plugin package="a.b"><extensions/></idea-plugin>`})

	equals(t, got, `<idea-plugin>
  <content>
    <module name="a.b"><![CDATA[<idea-plugin package="a.b">
  <extensions />
</idea-plugin>]]></module>
  </content>
</idea-plugin>`)
}

// The name of the descriptor file is the module name with every `/` turned into a `.`
// (`community/platform/build-scripts/src/org/jetbrains/intellij/build/impl/productModuleLayout.kt:257`).
func TestTheDescriptorFileNameOfAContentModule(t *testing.T) {
	for name, want := range map[string]string{
		"a.b":                        "a.b.xml",
		"intellij.plugin/frontend":   "intellij.plugin.frontend.xml",
		"intellij.a/b/c":             "intellij.a.b.c.xml",
		"intellij.plain.module.name": "intellij.plain.module.name.xml",
	} {
		if got := structural.ContentModuleDescriptorFileName(name); got != want {
			t.Errorf("%s: got %s, want %s", name, got, want)
		}
	}
}

// A `<module/>` the plan refuses is removed, wherever it stands. The assembly's own filter reads the JPS project model,
// and the plan states its refusals instead (`embedContentModulesFromPlan` of `DevDistPluginDescriptorMain.kt`).
func TestAModuleThePlanRefusesIsRemoved(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a"/><module name="dropped"/><module name="b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Refused: []string{"dropped"}, Embeds: false},
		nil)

	equals(t, got, `<idea-plugin>
  <content>
    <module name="a" />
    <module name="b" />
  </content>
</idea-plugin>`)
}

// The filter runs over **every** `<content>` block, which is what an include contributes more of
// (`embedContentModulesFromPlan` of `DevDistPluginDescriptorMain.kt`).
func TestTheFilterRunsOverEveryContentBlock(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a"/><module name="x"/></content>`+
			`<content><module name="y"/><module name="b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Refused: []string{"x", "y"}, Embeds: false},
		nil)

	equals(t, got, `<idea-plugin>
  <content>
    <module name="a" />
  </content>
  <content>
    <module name="b" />
  </content>
</idea-plugin>`)
}

// The invariant of the refusal list: every refusal must reach a `<module/>`. A refusal that reaches none is a plan the
// descriptor has moved away from (`embedContentModulesFromPlan` of `DevDistPluginDescriptorMain.kt`).
func TestAnUnmatchedRefusalIsRefused(t *testing.T) {
	_, err := embed(t,
		`<idea-plugin><content><module name="a"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Refused: []string{"absent"}, Embeds: true},
		map[string]string{"a.xml": `<idea-plugin/>`})
	if err == nil {
		t.Fatal("an unmatched refusal must be refused")
	}
	// The message states the plugin and the name it could not find, because that pair is the whole repair.
	for _, expected := range []string{"absent", "intellij.example"} {
		if !strings.Contains(err.Error(), expected) {
			t.Errorf("the refusal must state %s: %v", expected, err)
		}
	}
}

// The negative control of the case above: a refusal the descriptor does state passes, and takes that module out.
func TestAMatchedRefusalPasses(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a"/><module name="b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Refused: []string{"b"}, Embeds: true},
		map[string]string{"a.xml": `<idea-plugin/>`, "b.xml": `<idea-plugin/>`})
	if strings.Contains(got, `name="b"`) {
		t.Errorf("the refused module must be gone:\n%s", got)
	}
}

// An empty refusal list keeps every `<module/>` the descriptor states. This is what every plugin of the `idea` product
// takes today, because its `ContentModuleFilter` refuses nothing.
func TestAnEmptyRefusalListKeepsEveryModule(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a"/><module name="b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Embeds: true},
		map[string]string{"a.xml": `<idea-plugin/>`, "b.xml": `<idea-plugin/>`})
	for _, expected := range []string{`name="a"`, `name="b"`} {
		if !strings.Contains(got, expected) {
			t.Errorf("%s must survive:\n%s", expected, got)
		}
	}
}

// The three `separate-jar` gates, in the platform's order (`embedContentModulesFromPlan` of
// `DevDistPluginDescriptorMain.kt`, `contentModuleEmbedding.kt:284-299`). Every row states one gate's verdict, and the attribute appears in exactly one.
func TestTheThreeSeparateJarGates(t *testing.T) {
	for name, it := range map[string]struct {
		module      string
		descriptor  string
		separateJar map[string]bool
		wants       bool
	}{
		"every gate passes": {
			module: "a.b", descriptor: `<idea-plugin package="a.b"/>`,
			separateJar: map[string]bool{"a.b": true}, wants: true,
		},
		"the descriptor states no package": {
			module: "a.b", descriptor: `<idea-plugin/>`,
			separateJar: map[string]bool{"a.b": true}, wants: false,
		},
		"the name holds a slash": {
			module: "a.b/frontend", descriptor: `<idea-plugin package="a.b.frontend"/>`,
			separateJar: map[string]bool{"a.b/frontend": true}, wants: false,
		},
		"the plan does not name it": {
			module: "a.b", descriptor: `<idea-plugin package="a.b"/>`,
			separateJar: nil, wants: false,
		},
	} {
		t.Run(name, func(t *testing.T) {
			got := embedOrFail(t,
				`<idea-plugin><content><module name="`+it.module+`"/></content></idea-plugin>`,
				structural.ContentRequest{
					MainModule:  "intellij.example",
					SeparateJar: it.separateJar,
					Embeds:      true,
				},
				map[string]string{structural.ContentModuleDescriptorFileName(it.module): it.descriptor})
			if states := strings.Contains(got, `separate-jar=&quot;true&quot;`) ||
				strings.Contains(got, `separate-jar="true"`); states != it.wants {
				t.Errorf("separate-jar present = %t, want %t:\n%s", states, it.wants, got)
			}
		})
	}
}

// The attribute is appended after `package`, because `Element.setAttribute` appends what the element does not state
// and the writer prints the list in order.
func TestSeparateJarIsAppendedAfterThePackage(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
		structural.ContentRequest{
			MainModule:  "intellij.example",
			SeparateJar: map[string]bool{"a.b": true},
			Embeds:      true,
		},
		map[string]string{"a.b.xml": `<idea-plugin package="a.b"/>`})

	equals(t, got, `<idea-plugin>
  <content>
    <module name="a.b"><![CDATA[<idea-plugin package="a.b" separate-jar="true" />]]></module>
  </content>
</idea-plugin>`)
}

// A layout that embeds nothing still filters. Such a descriptor keeps its `<module/>` elements empty, and the assembly
// agrees (`embedContentModulesFromPlan` of `DevDistPluginDescriptorMain.kt`).
func TestALayoutThatEmbedsNothingStillFilters(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a"/><module name="dropped"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Refused: []string{"dropped"}, Embeds: false},
		// no descriptor is declared, and none is asked for
		nil)

	equals(t, got, `<idea-plugin>
  <content>
    <module name="a" />
  </content>
</idea-plugin>`)
}

// A `<module/>` that already holds content keeps it: the stage is the one that puts a body there
// (`contentModuleEmbedding.kt:339-341`).
func TestAModuleThatAlreadyHoldsContentIsLeftAlone(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a.b"><![CDATA[<idea-plugin package="already"/>]]></module></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Embeds: true},
		map[string]string{"a.b.xml": `<idea-plugin package="fresh"/>`})

	if !strings.Contains(got, "already") || strings.Contains(got, "fresh") {
		t.Errorf("an existing body must survive:\n%s", got)
	}
}

// An embedded descriptor resolves its own includes, through a search path the content module heads
// (`contentModuleEmbedding.kt:328`, `:347`).
func TestAnEmbeddedDescriptorResolvesItsOwnIncludes(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Embeds: true},
		map[string]string{
			"a.b.xml":            `<idea-plugin` + xi + ` package="a.b"><xi:include href="extra.xml"/></idea-plugin>`,
			"META-INF/extra.xml": `<idea-plugin><extensions/></idea-plugin>`,
		})

	equals(t, got, `<idea-plugin>
  <content>
    <module name="a.b"><![CDATA[<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude" package="a.b">
  <extensions />
</idea-plugin>]]></module>
  </content>
</idea-plugin>`)
}

// A content module whose descriptor no declared file answers fails, and the failure names the file. Every other way to
// find it needs a JPS project model (`contentModuleEmbedding.kt:311-321`).
func TestAnUndeclaredContentModuleDescriptorFails(t *testing.T) {
	_, err := embed(t,
		`<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Embeds: true},
		nil)
	if err == nil || !strings.Contains(err.Error(), "a.b.xml") {
		t.Errorf("got %v", err)
	}
}

// A `<module/>` with no name is refused (`embedContentModulesFromPlan` of `DevDistPluginDescriptorMain.kt`).
func TestAModuleWithNoNameIsRefused(t *testing.T) {
	_, err := embed(t,
		`<idea-plugin><content><module/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Embeds: true},
		nil)
	if err == nil || !strings.Contains(err.Error(), "states no name") {
		t.Errorf("got %v", err)
	}
}

// A `<module/>` inside a CDATA body is prose, and the stage walks the tree.
func TestAModuleInsideCdataIsNotADeclaration(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><description><![CDATA[<content><module name="prose"/></content>]]></description>`+
			`<content><module name="a"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Refused: []string{"a"}, Embeds: false},
		nil)
	if !strings.Contains(got, "&lt;module name=&quot;prose&quot;/&gt;") {
		t.Errorf("the prose must survive:\n%s", got)
	}
}

// A body that itself holds a CDATA section: the inner section is folded into text on read, so the embedded text states
// it as escaped prose and the frame does not nest. A nested `]]>` would end the outer section and break the descriptor.
func TestAnEmbeddedDescriptorWithProseDoesNotNestACdataFrame(t *testing.T) {
	got := embedOrFail(t,
		`<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
		structural.ContentRequest{MainModule: "intellij.example", Embeds: true},
		map[string]string{"a.b.xml": `<idea-plugin package="a.b"><description><![CDATA[<b>x</b>]]></description></idea-plugin>`})

	if strings.Count(got, "<![CDATA[") != 1 || strings.Count(got, "]]>") != 1 {
		t.Errorf("exactly one CDATA frame must survive:\n%s", got)
	}
	if !strings.Contains(got, "<description>&lt;b&gt;x&lt;/b&gt;</description>") {
		t.Errorf("the inner prose must be escaped text:\n%s", got)
	}
}
