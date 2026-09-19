// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package descriptorxml

import (
	"fmt"
	"strconv"
	"strings"
	"unicode/utf8"
)

// Read parses a descriptor the way `JDOMUtil.load` does and returns the root element.
//
// ### Why this is a scanner of its own and not `encoding/xml`
//
// The platform's reader is Aalto, configured at
// `community/platform/util/xmlDom/src/com/intellij/util/xml/dom/StaxFactory.kt:17-34`, driven by
// `community/platform/util/src/com/intellij/openapi/util/SafeStAXStreamBuilder.kt`. Three of its decisions have no
// setting in `encoding/xml`:
//
//  1. `doCoalesceText(true)` merges a CDATA section into the text run around it, so the tree holds one text node where
//     the document wrote three. `encoding/xml` reports each piece as its own `CharData`, and a trimming writer then
//     produces different bytes for `foo <![CDATA[ bar]]>`: one node trims to `foo  bar`, three nodes trim and pad to
//     `foo bar`.
//  2. `SUPPORT_DTD = false` with `IS_REPLACING_ENTITY_REFERENCES = false` means an entity that is not one of the five
//     predefined ones reaches the builder as `ENTITY_REFERENCE`, and `SafeStAXStreamBuilder.kt:178` **drops** it.
//     `encoding/xml` either fails on it or, with an entity map, expands it.
//  3. the writer prints a qualified name from the **prefix** the document wrote
//     (`community/platform/util/jdom/src/org/jdom/output/XMLOutputter.java:1330-1341`). `encoding/xml`'s `Token`
//     resolves a prefix to a URI and discards it; `RawToken` keeps it but then the tree is half hand-built anyway.
//
// A tokenizer of its own settles all three at the point where they are decided, and it is the call
// `community/build/content-module-packer` made for its writer for the same reason.
//
// ### What this drops, and where the platform drops it
//
//   - a comment, a processing instruction and a DTD: `SafeStAXStreamBuilder.kt:21`, `:178`
//   - a whitespace-only text run: `SafeStAXStreamBuilder.kt:171-177`
//   - an entity reference that is not predefined: `SafeStAXStreamBuilder.kt:178`
func Read(text string) (*Element, error) {
	reader := &reader{input: text}
	root, err := reader.readDocument()
	if err != nil {
		return nil, err
	}
	return root, nil
}

type reader struct {
	input string
	at    int
	// scopes is the namespace stack, innermost last. Each entry is one element's own declarations.
	scopes [][]Namespace
}

func (r *reader) readDocument() (*Element, error) {
	var root *Element
	for {
		r.skipSpace()
		if r.at >= len(r.input) {
			break
		}
		if !strings.HasPrefix(r.input[r.at:], "<") {
			return nil, r.errorf("text outside the root element")
		}
		switch {
		case strings.HasPrefix(r.input[r.at:], "<!--"):
			if err := r.skipUntil("<!--", "-->"); err != nil {
				return nil, err
			}
		case strings.HasPrefix(r.input[r.at:], "<?"):
			if err := r.skipUntil("<?", "?>"); err != nil {
				return nil, err
			}
		case strings.HasPrefix(r.input[r.at:], "<!DOCTYPE"):
			if err := r.skipDoctype(); err != nil {
				return nil, err
			}
		default:
			if root != nil {
				return nil, r.errorf("a second root element")
			}
			element, err := r.readElement()
			if err != nil {
				return nil, err
			}
			root = element
		}
	}
	if root == nil {
		// `buildJdom` answers an empty document with `Element("empty")`
		// (`community/platform/util/src/com/intellij/openapi/util/SafeStAXStreamBuilder.kt:143-146`).
		return &Element{Name: "empty"}, nil
	}
	return root, nil
}

// readElement reads one element and everything under it. `r.at` stands on the opening `<`.
func (r *reader) readElement() (*Element, error) {
	r.at++
	prefix, name, err := r.readQualifiedName()
	if err != nil {
		return nil, err
	}

	var attributes []Attribute
	var declarations []Namespace
	for {
		r.skipSpace()
		if r.at >= len(r.input) {
			return nil, r.errorf("the element %q does not end", name)
		}
		if strings.HasPrefix(r.input[r.at:], "/>") || strings.HasPrefix(r.input[r.at:], ">") {
			break
		}
		attributePrefix, attributeName, err := r.readQualifiedName()
		if err != nil {
			return nil, err
		}
		r.skipSpace()
		if r.at >= len(r.input) || r.input[r.at] != '=' {
			return nil, r.errorf("the attribute %q has no value", attributeName)
		}
		r.at++
		r.skipSpace()
		value, err := r.readAttributeValue()
		if err != nil {
			return nil, err
		}
		// Aalto reports a namespace declaration through `getNamespacePrefix`, never through `getAttributeLocalName`,
		// so it is a declaration here and not an attribute.
		switch {
		case attributePrefix == "" && attributeName == "xmlns":
			declarations = append(declarations, Namespace{Prefix: "", URI: value})
		case attributePrefix == "xmlns":
			declarations = append(declarations, Namespace{Prefix: attributeName, URI: value})
		default:
			attributes = append(attributes, Attribute{Prefix: attributePrefix, Name: attributeName, Value: value})
		}
	}

	r.scopes = append(r.scopes, declarations)
	defer func() { r.scopes = r.scopes[:len(r.scopes)-1] }()

	// An attribute's namespace resolves inside the element's own scope, and an unprefixed attribute has no namespace
	// whatever the default namespace binds.
	for i := range attributes {
		if attributes[i].Prefix != "" {
			attributes[i].URI = r.resolve(attributes[i].Prefix)
		}
	}

	element := &Element{
		Name:       name,
		Prefix:     prefix,
		URI:        r.resolve(prefix),
		Namespaces: declarations,
		Attributes: attributes,
	}

	if strings.HasPrefix(r.input[r.at:], "/>") {
		r.at += 2
		return element, nil
	}
	r.at++

	if err := r.readChildren(element); err != nil {
		return nil, err
	}
	return element, nil
}

// readChildren reads content up to the element's end tag.
//
// The text run is accumulated and flushed at every boundary, because the platform's reader coalesces text and then
// drops the run when it is whitespace only.
func (r *reader) readChildren(element *Element) error {
	var run strings.Builder
	flush := func() {
		text := run.String()
		run.Reset()
		if text == "" || isAllXMLWhitespace(text) {
			return
		}
		element.Children = append(element.Children, TextNode(text))
	}

	for {
		if r.at >= len(r.input) {
			return r.errorf("the element %q does not end", element.Name)
		}
		if r.input[r.at] != '<' {
			pieces, err := r.readText()
			if err != nil {
				return err
			}
			// A dropped entity reference is a stream event of its own, so it ends the run the way any other event does.
			// One piece is the ordinary case and adds nothing to the run boundary.
			for i, piece := range pieces {
				if i > 0 {
					flush()
				}
				run.WriteString(piece)
			}
			continue
		}
		switch {
		case strings.HasPrefix(r.input[r.at:], "<!--"):
			if err := r.skipUntil("<!--", "-->"); err != nil {
				return err
			}
		case strings.HasPrefix(r.input[r.at:], "<![CDATA["):
			body, err := r.readCDATA()
			if err != nil {
				return err
			}
			// Coalescing folds the section into the surrounding run rather than making a node of its own.
			run.WriteString(body)
		case strings.HasPrefix(r.input[r.at:], "<?"):
			if err := r.skipUntil("<?", "?>"); err != nil {
				return err
			}
		case strings.HasPrefix(r.input[r.at:], "</"):
			flush()
			end := strings.IndexByte(r.input[r.at:], '>')
			if end < 0 {
				return r.errorf("the end tag of %q does not close", element.Name)
			}
			closing := strings.TrimSpace(r.input[r.at+2 : r.at+end])
			r.at += end + 1
			expected := element.Name
			if element.Prefix != "" {
				expected = element.Prefix + ":" + element.Name
			}
			if closing != expected {
				return r.errorf("the end tag %q does not match %q", closing, expected)
			}
			return nil
		default:
			flush()
			child, err := r.readElement()
			if err != nil {
				return err
			}
			element.Children = append(element.Children, ElementNode(child))
		}
	}
}

// readText reads character data up to the next `<`, as one piece per stream event.
//
// A dropped entity reference splits the data, because the reader that drops it saw an event of its own there. Every
// other input answers with exactly one piece.
func (r *reader) readText() ([]string, error) {
	pieces := []string{""}
	var piece strings.Builder
	for r.at < len(r.input) && r.input[r.at] != '<' {
		if r.input[r.at] == '&' {
			expansion, dropped, err := r.readReference()
			if err != nil {
				return nil, err
			}
			if dropped {
				pieces[len(pieces)-1] = piece.String()
				piece.Reset()
				pieces = append(pieces, "")
				continue
			}
			piece.WriteString(expansion)
			continue
		}
		// XML asks a parser to report `\r\n` and a lone `\r` as `\n`.
		if r.input[r.at] == '\r' {
			piece.WriteByte('\n')
			r.at++
			if r.at < len(r.input) && r.input[r.at] == '\n' {
				r.at++
			}
			continue
		}
		piece.WriteByte(r.input[r.at])
		r.at++
	}
	pieces[len(pieces)-1] = piece.String()
	return pieces, nil
}

// readReference reads one `&…;`. It reports the expansion, or that the platform's reader drops the reference.
func (r *reader) readReference() (string, bool, error) {
	end := strings.IndexByte(r.input[r.at:], ';')
	if end < 0 {
		return "", false, r.errorf("a reference does not end with a semicolon")
	}
	body := r.input[r.at+1 : r.at+end]
	r.at += end + 1
	if strings.HasPrefix(body, "#") {
		digits := body[1:]
		base := 10
		if strings.HasPrefix(digits, "x") || strings.HasPrefix(digits, "X") {
			digits = digits[1:]
			base = 16
		}
		code, err := strconv.ParseInt(digits, base, 64)
		if err != nil || code < 0 || code > utf8.MaxRune {
			return "", false, r.errorf("the character reference &%s; is not a code point", body)
		}
		return string(rune(code)), false, nil
	}
	switch body {
	case "lt":
		return "<", false, nil
	case "gt":
		return ">", false, nil
	case "amp":
		return "&", false, nil
	case "quot":
		return "\"", false, nil
	case "apos":
		return "'", false, nil
	}
	// Not predefined, and the reader resolves no DTD. `SafeStAXStreamBuilder.kt:178` drops the event.
	return "", true, nil
}

func (r *reader) readCDATA() (string, error) {
	const open = "<![CDATA["
	end := strings.Index(r.input[r.at+len(open):], "]]>")
	if end < 0 {
		return "", r.errorf("a CDATA section does not end")
	}
	body := r.input[r.at+len(open) : r.at+len(open)+end]
	r.at += len(open) + end + len("]]>")
	return strings.ReplaceAll(strings.ReplaceAll(body, "\r\n", "\n"), "\r", "\n"), nil
}

// readAttributeValue reads a quoted value and normalizes it the way XML asks a parser to.
//
// A literal tab, carriage return or newline becomes one space. A character reference does not, which is why the writer
// can escape a newline back to `&#10;` and the pair round-trips.
func (r *reader) readAttributeValue() (string, error) {
	if r.at >= len(r.input) {
		return "", r.errorf("an attribute value is missing")
	}
	quote := r.input[r.at]
	if quote != '"' && quote != '\'' {
		return "", r.errorf("an attribute value does not start with a quote")
	}
	r.at++
	var value strings.Builder
	for {
		if r.at >= len(r.input) {
			return "", r.errorf("an attribute value does not close")
		}
		c := r.input[r.at]
		if c == quote {
			r.at++
			return value.String(), nil
		}
		if c == '&' {
			expansion, dropped, err := r.readReference()
			if err != nil {
				return "", err
			}
			if !dropped {
				value.WriteString(expansion)
			}
			continue
		}
		if c == '\t' || c == '\n' || c == '\r' {
			value.WriteByte(' ')
			r.at++
			if c == '\r' && r.at < len(r.input) && r.input[r.at] == '\n' {
				r.at++
			}
			continue
		}
		value.WriteByte(c)
		r.at++
	}
}

func (r *reader) readQualifiedName() (string, string, error) {
	start := r.at
	for r.at < len(r.input) && isNameByte(r.input[r.at]) {
		r.at++
	}
	if r.at == start {
		return "", "", r.errorf("a name is missing")
	}
	name := r.input[start:r.at]
	if colon := strings.IndexByte(name, ':'); colon >= 0 {
		return name[:colon], name[colon+1:], nil
	}
	return "", name, nil
}

// resolve maps a prefix to the URI the innermost declaration binds it to.
func (r *reader) resolve(prefix string) string {
	for i := len(r.scopes) - 1; i >= 0; i-- {
		for _, declaration := range r.scopes[i] {
			if declaration.Prefix == prefix {
				return declaration.URI
			}
		}
	}
	if prefix == "xml" {
		return "http://www.w3.org/XML/1998/namespace"
	}
	return ""
}

func (r *reader) skipSpace() {
	for r.at < len(r.input) && isXMLWhitespaceByte(r.input[r.at]) {
		r.at++
	}
}

func (r *reader) skipUntil(open string, close string) error {
	end := strings.Index(r.input[r.at+len(open):], close)
	if end < 0 {
		return r.errorf("a %q does not end with %q", open, close)
	}
	r.at += len(open) + end + len(close)
	return nil
}

func (r *reader) skipDoctype() error {
	depth := 0
	for r.at < len(r.input) {
		switch r.input[r.at] {
		case '[':
			depth++
		case ']':
			depth--
		case '>':
			if depth <= 0 {
				r.at++
				return nil
			}
		}
		r.at++
	}
	return r.errorf("a DOCTYPE does not end")
}

func (r *reader) errorf(format string, args ...any) error {
	line := 1 + strings.Count(r.input[:min(r.at, len(r.input))], "\n")
	return fmt.Errorf("line %d, offset %d: %s", line, r.at, fmt.Sprintf(format, args...))
}

func isNameByte(c byte) bool {
	switch {
	case c >= 'a' && c <= 'z', c >= 'A' && c <= 'Z', c >= '0' && c <= '9':
		return true
	case c == '_' || c == '-' || c == '.' || c == ':' || c >= 0x80:
		return true
	}
	return false
}

// isXMLWhitespaceByte is `Verifier.isXMLWhitespace`
// (`community/platform/util/jdom/src/org/jdom/Verifier.java:1033-1041`), which is four characters and not Unicode
// whitespace.
func isXMLWhitespaceByte(c byte) bool {
	return c == ' ' || c == '\n' || c == '\t' || c == '\r'
}

// isAllXMLWhitespace is `Verifier.isAllXMLWhitespace`
// (`community/platform/util/jdom/src/org/jdom/Verifier.java:1056`). The empty string is all whitespace.
func isAllXMLWhitespace(text string) bool {
	for i := 0; i < len(text); i++ {
		if !isXMLWhitespaceByte(text[i]) {
			return false
		}
	}
	return true
}
