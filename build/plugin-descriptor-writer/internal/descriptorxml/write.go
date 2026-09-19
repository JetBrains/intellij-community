// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package descriptorxml

import "strings"

// The serializer, byte for byte as `JDOMUtil.write(Element)` performs it.
//
// The chain is `JDOMUtil.write` → `writeElement(element, "\n")` → `createOutputter("\n")` → `MyXMLOutputter` over
// `DEFAULT_FORMAT` (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:490`, `:524`, `:528`, `:580`,
// `:631`), and `DEFAULT_FORMAT` is
//
//	Format.getCompactFormat().setIndent("  ").setTextMode(Format.TextMode.TRIM).setLineSeparator("\n")
//
// (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:563-566`). The layout rules below are
// `printElement` and its helpers in `community/platform/util/jdom/src/org/jdom/output/XMLOutputter.java`.
//
// Four properties of that configuration decide most of the bytes:
//
//   - no XML declaration and no trailing newline. `output(Element, Writer)` calls `printElement` and nothing else
//     (`XMLOutputter.java:278-282`);
//   - an element with no significant content writes `" />"`, because `expandEmptyElements` is false
//     (`Format.java:375`, `XMLOutputter.java:650`);
//   - `TextMode.TRIM` trims every text run with Java's `String.trim`, which cuts every character at or below `' '` and
//     not only the four XML whitespace characters (`XMLOutputter.java:581-589`);
//   - an element whose content is text only takes no indentation at all, and one with an element child takes full
//     indentation (`XMLOutputter.java:660-673`).

// indentUnit is `Format.setIndent("  ")`.
const indentUnit = "  "

// textMode is the two modes this serializer can be in. `xml:space="preserve"` switches to [modePreserve], which is
// `Format.getRawFormat()` (`XMLOutputter.java:92`, `:611-618`).
type textMode int

const (
	// modeTrim trims each run and indents. It is `DEFAULT_FORMAT`.
	modeTrim textMode = iota
	// modePreserve changes nothing and indents nothing.
	modePreserve
)

// Write serializes an element the way `JDOMUtil.write` does.
func Write(element *Element) string {
	writer := &writer{mode: modeTrim}
	writer.printElement(element, 0)
	return writer.out.String()
}

type writer struct {
	out  strings.Builder
	mode textMode
	// stack is JDOM's `NamespaceStack`: a declaration is printed only when the prefix is not already bound to this URI
	// (`XMLOutputter.java:844-848`).
	stack []Namespace
}

// indents reports whether the current mode indents. `Format.getRawFormat` leaves the indent null, and both `newline`
// and `indent` then write nothing (`XMLOutputter.java:925-943`).
func (w *writer) indents() bool { return w.mode == modeTrim }

func (w *writer) printElement(element *Element, level int) {
	previousMode := w.mode
	if space, stated := element.PrefixedAttribute("xml", "space"); stated {
		switch space {
		case "default":
			w.mode = modeTrim
		case "preserve":
			w.mode = modePreserve
		}
	}

	w.out.WriteString("<")
	w.printElementName(element)

	depth := len(w.stack)
	w.printElementNamespace(element)
	for _, declaration := range element.Namespaces {
		w.printNamespace(declaration)
	}
	w.printAttributes(element)

	start := w.skipLeadingWhite(element.Children, 0)
	size := len(element.Children)
	if start >= size {
		w.out.WriteString(" />")
	} else {
		w.out.WriteString(">")
		if nextNonText(element.Children, start) < size {
			// Mixed content: every child on its own line, one level in.
			w.newline()
			w.printContentRange(element.Children, start, size, level+1)
			w.newline()
			w.indent(level)
		} else {
			// Text only: no indentation, before or after.
			w.printTextRange(element.Children, start, size)
		}
		w.out.WriteString("</")
		w.printElementName(element)
		w.out.WriteString(">")
	}

	w.stack = w.stack[:depth]
	w.mode = previousMode
}

// printContentRange is `XMLOutputter.java:703-745`. A run of text children is printed as one indented block, and each
// element child takes its own line.
func (w *writer) printContentRange(children []Node, start int, end int, level int) {
	index := start
	for index < end {
		firstNode := index == start
		if children[index].Kind != KindElement {
			first := w.skipLeadingWhite(children, index)
			index = nextNonText(children, first)
			if first < index {
				if !firstNode {
					w.newline()
				}
				w.indent(level)
				w.printTextRange(children, first, index)
			}
			continue
		}
		if !firstNode {
			w.newline()
		}
		w.indent(level)
		w.printElement(children[index].Element, level)
		index++
	}
}

// printTextRange is `XMLOutputter.java:765-833`. It pads the join of two runs with one space when either side had
// whitespace there, which is the only place this serializer inserts a character no node holds.
func (w *writer) printTextRange(children []Node, start int, end int) {
	start = w.skipLeadingWhite(children, start)
	if start >= len(children) {
		return
	}
	end = w.skipTrailingWhite(children, end)
	hadPrevious := false
	previous := ""
	for i := start; i < end; i++ {
		next := children[i].Text
		if next == "" {
			continue
		}
		if hadPrevious && w.mode == modeTrim {
			if endsWithWhite(previous) || startsWithWhite(next) {
				w.out.WriteString(" ")
			}
		}
		if children[i].Kind == KindCDATA {
			w.printCDATA(next)
		} else {
			w.printString(next)
		}
		previous = next
		hadPrevious = true
	}
}

// printCDATA is `XMLOutputter.java:553-561`. The body is trimmed and never escaped.
func (w *writer) printCDATA(text string) {
	if w.mode == modeTrim {
		text = javaTrim(text)
	}
	w.out.WriteString("<![CDATA[")
	w.out.WriteString(text)
	w.out.WriteString("]]>")
}

// printString is `XMLOutputter.java:581-589`.
func (w *writer) printString(text string) {
	if w.mode == modeTrim {
		text = javaTrim(text)
	}
	w.out.WriteString(EscapeElementText(text))
}

func (w *writer) printElementName(element *Element) {
	if element.Prefix != "" {
		w.out.WriteString(element.Prefix)
		w.out.WriteString(":")
	}
	w.out.WriteString(element.Name)
}

// printElementNamespace is `XMLOutputter.java:888-902`. The XML namespace is never declared, and no-namespace is
// declared only to reclaim a prefix another declaration took.
func (w *writer) printElementNamespace(element *Element) {
	if element.Prefix == "xml" {
		return
	}
	if element.Prefix == "" && element.URI == "" && w.boundURI("") == "" {
		return
	}
	w.printNamespace(Namespace{Prefix: element.Prefix, URI: element.URI})
}

func (w *writer) printNamespace(declaration Namespace) {
	if declaration.URI == w.boundURI(declaration.Prefix) {
		return
	}
	w.out.WriteString(" xmlns")
	if declaration.Prefix != "" {
		w.out.WriteString(":")
		w.out.WriteString(declaration.Prefix)
	}
	w.out.WriteString("=\"")
	w.out.WriteString(EscapeAttributeValue(declaration.URI))
	w.out.WriteString("\"")
	w.stack = append(w.stack, declaration)
}

// printAttributes is `XMLOutputter.java:863-886`. A prefixed attribute declares its namespace first, unless the prefix
// is `xml`.
func (w *writer) printAttributes(element *Element) {
	for _, attribute := range element.Attributes {
		if attribute.Prefix != "" && attribute.Prefix != "xml" {
			w.printNamespace(Namespace{Prefix: attribute.Prefix, URI: attribute.URI})
		}
		w.out.WriteString(" ")
		if attribute.Prefix != "" {
			w.out.WriteString(attribute.Prefix)
			w.out.WriteString(":")
		}
		w.out.WriteString(attribute.Name)
		w.out.WriteString("=\"")
		w.out.WriteString(EscapeAttributeValue(attribute.Value))
		w.out.WriteString("\"")
	}
}

// boundURI is `NamespaceStack.getURI`. The empty answer stands for "not bound", which is what JDOM's null means at
// `XMLOutputter.java:844` and `:897`.
func (w *writer) boundURI(prefix string) string {
	for i := len(w.stack) - 1; i >= 0; i-- {
		if w.stack[i].Prefix == prefix {
			return w.stack[i].URI
		}
	}
	return ""
}

func (w *writer) newline() {
	if w.indents() {
		w.out.WriteString("\n")
	}
}

func (w *writer) indent(level int) {
	if !w.indents() {
		return
	}
	for i := 0; i < level; i++ {
		w.out.WriteString(indentUnit)
	}
}

// skipLeadingWhite is `XMLOutputter.java:952-974`. In preserve mode it skips nothing.
func (w *writer) skipLeadingWhite(children []Node, start int) int {
	if start < 0 {
		start = 0
	}
	if w.mode != modeTrim {
		return start
	}
	index := start
	for index < len(children) {
		if !isInsignificantWhitespace(children[index]) {
			return index
		}
		index++
	}
	return index
}

// skipTrailingWhite is `XMLOutputter.java:979-999`.
func (w *writer) skipTrailingWhite(children []Node, start int) int {
	if start > len(children) {
		start = len(children)
	}
	if w.mode != modeTrim {
		return start
	}
	index := start
	for index > 0 && isInsignificantWhitespace(children[index-1]) {
		index--
	}
	return index
}

// nextNonText is `XMLOutputter.java:1004-1015`. A CDATA node is a `Text` in JDOM, so it counts as text here too.
func nextNonText(children []Node, start int) int {
	if start < 0 {
		start = 0
	}
	index := start
	for index < len(children) {
		if children[index].Kind == KindElement {
			return index
		}
		index++
	}
	return index
}

// isInsignificantWhitespace is `isAllWhitespace` (`XMLOutputter.java:1018-1039`). An element is never insignificant.
func isInsignificantWhitespace(node Node) bool {
	if node.Kind == KindElement {
		return false
	}
	return isAllXMLWhitespace(node.Text)
}

func startsWithWhite(text string) bool {
	return text != "" && isXMLWhitespaceByte(text[0])
}

func endsWithWhite(text string) bool {
	return text != "" && isXMLWhitespaceByte(text[len(text)-1])
}

// javaTrim is `String.trim`, which cuts every leading and trailing character at or below `' '`. That is broader than
// `Verifier.isXMLWhitespace`, and the difference is real: a `` at the end of a run is trimmed here and is
// significant to `skipLeadingWhite`.
func javaTrim(text string) string {
	start := 0
	end := len(text)
	for start < end && text[start] <= ' ' {
		start++
	}
	for end > start && text[end-1] <= ' ' {
		end--
	}
	return text[start:end]
}

// EscapeElementText is `MyXMLOutputter.escapeElementEntities`
// (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:637-640`), which is
// `escapeText(str, escapeApostrophes = false, escapeSpaces = false, escapeLineEnds = false)`.
//
// It escapes `"` inside element text, which a standard serializer does not, and it leaves `'`, a newline, a carriage
// return and a tab alone.
func EscapeElementText(text string) string {
	return escapeText(text, false)
}

// EscapeAttributeValue is `MyXMLOutputter.escapeAttributeEntities`
// (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:632-635`), which is
// `escapeText(str, escapeApostrophes = false, escapeSpaces = false, escapeLineEnds = true)`.
func EscapeAttributeValue(text string) string {
	return escapeText(text, true)
}

// escapeText is `JDOMUtil.escapeText` over `JDOMUtil.escapeChar`
// (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:587-635`).
func escapeText(text string, escapeLineEnds bool) string {
	needs := false
	for i := 0; i < len(text); i++ {
		if escapeChar(text[i], escapeLineEnds) != "" {
			needs = true
			break
		}
	}
	if !needs {
		return text
	}
	var out strings.Builder
	out.Grow(len(text) + 20)
	for i := 0; i < len(text); i++ {
		if replacement := escapeChar(text[i], escapeLineEnds); replacement != "" {
			out.WriteString(replacement)
			continue
		}
		out.WriteByte(text[i])
	}
	return out.String()
}

func escapeChar(c byte, escapeLineEnds bool) string {
	switch c {
	case '\n':
		if escapeLineEnds {
			return "&#10;"
		}
	case '\r':
		if escapeLineEnds {
			return "&#13;"
		}
	case '\t':
		if escapeLineEnds {
			return "&#9;"
		}
	case '<':
		return "&lt;"
	case '>':
		return "&gt;"
	case '"':
		return "&quot;"
	case '&':
		return "&amp;"
	}
	return ""
}
