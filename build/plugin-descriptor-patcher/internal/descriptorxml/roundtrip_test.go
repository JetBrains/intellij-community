// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package descriptorxml_test

import (
	"strings"
	"testing"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
)

// The curated round-trip cases: one construct each, and every expectation is the text the platform wrote.
//
// Every `want` below was produced by `JDOMUtil.write(JDOMUtil.load(source))` on a real classpath, not by reading this
// port's own output back. `internal/stamps/population_test.go` proves the same pair over a whole product's 163
// descriptors, and these cases say which rule each byte comes from so that a failure names the rule rather than a
// plugin.
func TestTheRoundTripReproducesTheSerializer(t *testing.T) {
	cases := []struct {
		name   string
		source string
		want   string
	}{
		{
			name:   "an element with no content writes a space and a slash",
			source: `<idea-plugin><depends/></idea-plugin>`,
			want:   "<idea-plugin>\n  <depends />\n</idea-plugin>",
		},
		{
			name:   "an element whose content is text takes no indentation",
			source: "<idea-plugin>\n  <id>com.example</id>\n</idea-plugin>",
			want:   "<idea-plugin>\n  <id>com.example</id>\n</idea-plugin>",
		},
		{
			name:   "every text run is trimmed",
			source: "<idea-plugin><name>\n   Example\n  </name></idea-plugin>",
			want:   "<idea-plugin>\n  <name>Example</name>\n</idea-plugin>",
		},
		{
			name:   "a whitespace-only element is empty and writes a space and a slash",
			source: "<idea-plugin><name>   </name></idea-plugin>",
			want:   "<idea-plugin>\n  <name />\n</idea-plugin>",
		},
		{
			name:   "a comment is deleted",
			source: "<idea-plugin>\n  <!-- a note -->\n  <id>a</id>\n</idea-plugin>",
			want:   "<idea-plugin>\n  <id>a</id>\n</idea-plugin>",
		},
		{
			name:   "a comment between two text runs does not split the run",
			source: "<idea-plugin><name>one<!-- x -->two</name></idea-plugin>",
			want:   "<idea-plugin>\n  <name>onetwo</name>\n</idea-plugin>",
		},
		{
			name:   "an XML declaration is deleted, and the output has no declaration of its own",
			source: "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<idea-plugin><id>a</id></idea-plugin>",
			want:   "<idea-plugin>\n  <id>a</id>\n</idea-plugin>",
		},
		{
			name:   "a CDATA section becomes plain escaped text",
			source: "<idea-plugin><description><![CDATA[<b>bold</b>]]></description></idea-plugin>",
			want:   "<idea-plugin>\n  <description>&lt;b&gt;bold&lt;/b&gt;</description>\n</idea-plugin>",
		},
		{
			name:   "a CDATA section is folded into the text run around it",
			source: "<idea-plugin><name>one <![CDATA[ two]]></name></idea-plugin>",
			want:   "<idea-plugin>\n  <name>one  two</name>\n</idea-plugin>",
		},
		{
			name:   "a double quote is escaped inside element text, and an apostrophe is not",
			source: "<idea-plugin><name>say \"it's\"</name></idea-plugin>",
			want:   "<idea-plugin>\n  <name>say &quot;it's&quot;</name>\n</idea-plugin>",
		},
		{
			name:   "an attribute keeps a single-quoted value and takes double quotes",
			source: `<idea-plugin><module name='a.b'/></idea-plugin>`,
			want:   "<idea-plugin>\n  <module name=\"a.b\" />\n</idea-plugin>",
		},
		{
			name:   "a newline inside an attribute value becomes a space, and a reference stays a newline",
			source: "<idea-plugin><module name=\"a&#10;b\" other=\"c\nd\"/></idea-plugin>",
			want:   "<idea-plugin>\n  <module name=\"a&#10;b\" other=\"c d\" />\n</idea-plugin>",
		},
		{
			name:   "a declared prefix is printed before every attribute, whatever the source order was",
			source: `<idea-plugin url="u" xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="h"/></idea-plugin>`,
			want: "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\" url=\"u\">\n" +
				"  <xi:include href=\"h\" />\n" +
				"</idea-plugin>",
		},
		{
			name:   "an element with both text and an element child indents both",
			source: "<idea-plugin>text<id>a</id></idea-plugin>",
			want:   "<idea-plugin>\n  text\n  <id>a</id>\n</idea-plugin>",
		},
		{
			name:   "nesting adds one indent level a step",
			source: "<idea-plugin><extensions><ep id=\"x\"/></extensions></idea-plugin>",
			want:   "<idea-plugin>\n  <extensions>\n    <ep id=\"x\" />\n  </extensions>\n</idea-plugin>",
		},
		{
			name:   "the five predefined entities are resolved and then re-escaped",
			source: "<idea-plugin><name>&lt;&amp;&gt;&quot;&apos;</name></idea-plugin>",
			want:   "<idea-plugin>\n  <name>&lt;&amp;&gt;&quot;'</name>\n</idea-plugin>",
		},
	}
	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			element, err := descriptorxml.Read(testCase.source)
			if err != nil {
				t.Fatalf("read: %v", err)
			}
			if got := descriptorxml.Write(element); got != testCase.want {
				t.Errorf("got:\n%s\nwant:\n%s", got, testCase.want)
			}
		})
	}
}

// A non-predefined entity reaches the platform's builder as its own stream event, and the builder drops it. The event
// also ends the text run, which is the part a reader cannot guess from the deletion alone.
func TestANonPredefinedEntityIsDroppedAndEndsTheRun(t *testing.T) {
	element, err := descriptorxml.Read("<idea-plugin><name>one&nbsp;two</name></idea-plugin>")
	if err != nil {
		t.Fatal(err)
	}
	name := element.Child("name")
	if name == nil {
		t.Fatal("no name element")
	}
	if len(name.Children) != 2 {
		t.Fatalf("got %d text runs, want 2", len(name.Children))
	}
	if got := descriptorxml.Write(element); got != "<idea-plugin>\n  <name>onetwo</name>\n</idea-plugin>" {
		t.Errorf("got:\n%s", got)
	}
}

// `xml:space="preserve"` switches the serializer to a raw format, which indents nothing and trims nothing.
func TestPreserveSpaceTurnsOffIndentAndTrim(t *testing.T) {
	source := "<idea-plugin><pre xml:space=\"preserve\">  a  <b/>  c  </pre></idea-plugin>"
	want := "<idea-plugin>\n  <pre xml:space=\"preserve\">  a  <b />  c  </pre>\n</idea-plugin>"
	element, err := descriptorxml.Read(source)
	if err != nil {
		t.Fatal(err)
	}
	if got := descriptorxml.Write(element); got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}

// The writer must not end the text with a newline. A jar entry that gained one would differ from every recorded
// descriptor, and the whole port would be off by one byte everywhere.
func TestTheTextHasNoTrailingNewline(t *testing.T) {
	element, err := descriptorxml.Read("<idea-plugin><id>a</id></idea-plugin>")
	if err != nil {
		t.Fatal(err)
	}
	got := descriptorxml.Write(element)
	if strings.HasSuffix(got, "\n") {
		t.Errorf("the text ends with a newline: %q", got)
	}
}

// A malformed descriptor must fail loudly. The platform's reader throws, and an action that writes a truncated
// descriptor instead would fail at class-load time in the IDE.
func TestMalformedInputIsRefused(t *testing.T) {
	cases := map[string]string{
		"an unclosed element":         "<idea-plugin><id>a</id>",
		"a mismatched end tag":        "<idea-plugin><id>a</name></idea-plugin>",
		"an attribute with no value":  `<idea-plugin><module name/></idea-plugin>`,
		"an unterminated CDATA":       "<idea-plugin><description><![CDATA[x</description></idea-plugin>",
		"an unterminated comment":     "<idea-plugin><!-- x </idea-plugin>",
		"text outside the root":       "junk<idea-plugin/>",
		"a second root element":       "<idea-plugin/><idea-plugin/>",
		"a reference with no ending":  "<idea-plugin><name>&amp</name></idea-plugin>",
		"an unquoted attribute value": `<idea-plugin><module name=a/></idea-plugin>`,
	}
	for name, source := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := descriptorxml.Read(source); err == nil {
				t.Errorf("%q was accepted", source)
			}
		})
	}
}
