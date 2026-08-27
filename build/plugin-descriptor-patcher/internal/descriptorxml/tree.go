// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The element tree a plugin descriptor patch runs over, and the two halves of the round trip the platform performs.
//
// The platform reads a descriptor with `JDOMUtil.load` and writes every stage with `JDOMUtil.write`. That pair rewrites
// whitespace, attribute quoting and CDATA on every descriptor, before any patch runs, so the bytes a plugin's main jar
// receives are the bytes this round trip produces. A port of the patch that is not byte-exact on the round trip is not
// a port at all.
//
// So this package models what the platform's reader keeps and what the platform's writer prints, and nothing else. The
// two are asymmetric on purpose:
//
//   - the reader drops a comment, a processing instruction, a DTD and every whitespace-only text run, and it folds a
//     CDATA section into plain text;
//   - the writer re-indents with two spaces, trims each text run, and prints a CDATA section only where the patch put
//     one back.
//
// `read.go` and `write.go` each state the platform site they mirror, line by line.
package descriptorxml

// Namespace is one namespace declaration: a prefix and the URI it binds. An empty prefix is the default namespace.
type Namespace struct {
	Prefix string
	URI    string
}

// Attribute is one attribute of an element. The prefix is kept as the document wrote it, because the writer prints the
// qualified name from the prefix and never from the URI. The URI is carried too, because a prefixed attribute makes the
// writer declare the namespace (`community/platform/util/jdom/src/org/jdom/output/XMLOutputter.java:868-872`).
type Attribute struct {
	Prefix string
	Name   string
	URI    string
	Value  string
}

// NodeKind is which of the three child kinds a [Node] is.
type NodeKind int

const (
	// KindElement is a child element.
	KindElement NodeKind = iota
	// KindText is a text run. The platform's reader never produces a whitespace-only one.
	KindText
	// KindCDATA is a text run the writer must frame as `<![CDATA[…]]>`. The platform's reader never produces one,
	// because its stream reader coalesces text: only the patch creates one, for `description` and `change-notes`.
	KindCDATA
)

// Node is one child of an element.
type Node struct {
	Kind NodeKind
	// Text carries the run for [KindText] and [KindCDATA].
	Text string
	// Element carries the child for [KindElement].
	Element *Element
}

// Element is one element of the tree.
//
// `Prefix` and `URI` are the element's own namespace. `Namespaces` are the declarations the document wrote on this
// element, in document order, which the platform's reader turns into JDOM "additional namespaces". The writer prints
// the element's own namespace first, then these, then the attributes, so a declared `xmlns:xi` always precedes every
// attribute whatever the source order was.
type Element struct {
	Name       string
	Prefix     string
	URI        string
	Namespaces []Namespace
	Attributes []Attribute
	Children   []Node
}

// TextNode returns a text child.
func TextNode(text string) Node { return Node{Kind: KindText, Text: text} }

// CDATANode returns a child the writer frames as a CDATA section.
func CDATANode(text string) Node { return Node{Kind: KindCDATA, Text: text} }

// ElementNode returns an element child.
func ElementNode(element *Element) Node { return Node{Kind: KindElement, Element: element} }

// Child returns the first child element with this name and **no namespace**, or nil.
//
// The namespace condition is not an omission. `Element.getChild(String)` resolves against `Namespace.NO_NAMESPACE`
// (`community/platform/util/jdom/src/org/jdom/Element.java:1452`), so an `xi:include` never answers a lookup for
// `include`, and every lookup the patch performs is of this shape.
func (e *Element) Child(name string) *Element {
	for i := range e.Children {
		child := e.Children[i]
		if child.Kind == KindElement && child.Element.Name == name && child.Element.Prefix == "" && child.Element.URI == "" {
			return child.Element
		}
	}
	return nil
}

// IndexOfChild returns the position of a child element inside `Children`, or -1.
//
// The position is what decides the bytes when the patch inserts an element after an anchor, so it counts every child
// and not only the elements.
func (e *Element) IndexOfChild(child *Element) int {
	for i := range e.Children {
		if e.Children[i].Kind == KindElement && e.Children[i].Element == child {
			return i
		}
	}
	return -1
}

// InsertChild puts an element at this position, moving every later child one place along.
func (e *Element) InsertChild(index int, child *Element) {
	node := ElementNode(child)
	e.Children = append(e.Children, Node{})
	copy(e.Children[index+1:], e.Children[index:])
	e.Children[index] = node
}

// RemoveChild detaches a child element. It reports whether it found one.
func (e *Element) RemoveChild(child *Element) bool {
	index := e.IndexOfChild(child)
	if index < 0 {
		return false
	}
	e.Children = append(e.Children[:index], e.Children[index+1:]...)
	return true
}

// Text returns the element's text the way `Element.getText` builds it: every text and CDATA child concatenated, with
// every child **element** skipped (`community/platform/util/jdom/src/org/jdom/Element.java:497-521`).
//
// So an element with markup inside loses that markup when the patch reads this and writes a CDATA section back. That
// is what the platform does, and the CDATA restoration of `description` relies on it.
func (e *Element) Text() string {
	if len(e.Children) == 0 {
		return ""
	}
	if len(e.Children) == 1 {
		if e.Children[0].Kind == KindElement {
			return ""
		}
		return e.Children[0].Text
	}
	var builder []byte
	for _, child := range e.Children {
		if child.Kind != KindElement {
			builder = append(builder, child.Text...)
		}
	}
	return string(builder)
}

// SetText replaces every child with one text run, the way `Element.setText` does
// (`community/platform/util/jdom/src/org/jdom/Element.java:626-632`).
//
// An empty string still adds a text node. The writer then prints `<name />`, because a whitespace-only run is
// insignificant to it. A caller that wants no child at all clears `Children` itself.
func (e *Element) SetText(text string) {
	e.Children = []Node{TextNode(text)}
}

// SetCDATA replaces every child with one CDATA run, the way `Element.setContent(CDATA)` does
// (`community/platform/util/jdom/src/org/jdom/Element.java:928-932`).
func (e *Element) SetCDATA(text string) {
	e.Children = []Node{CDATANode(text)}
}

// Attribute returns the value of an attribute with no prefix, and whether the element states one.
func (e *Element) Attribute(name string) (string, bool) {
	for _, attribute := range e.Attributes {
		if attribute.Name == name && attribute.Prefix == "" {
			return attribute.Value, true
		}
	}
	return "", false
}

// PrefixedAttribute returns the value of an attribute with this prefix, and whether the element states one.
func (e *Element) PrefixedAttribute(prefix string, name string) (string, bool) {
	for _, attribute := range e.Attributes {
		if attribute.Name == name && attribute.Prefix == prefix {
			return attribute.Value, true
		}
	}
	return "", false
}

// SetAttribute sets an unprefixed attribute, in place when the element already states one and appended otherwise.
//
// The in-place rule is load-bearing: `Element.setAttribute` replaces the value inside JDOM's attribute list without
// moving the entry, and the writer prints that list in order.
func (e *Element) SetAttribute(name string, value string) {
	for i := range e.Attributes {
		if e.Attributes[i].Name == name && e.Attributes[i].Prefix == "" {
			e.Attributes[i].Value = value
			return
		}
	}
	e.Attributes = append(e.Attributes, Attribute{Name: name, Value: value})
}

// RemoveAttribute drops an unprefixed attribute. It reports whether it found one.
func (e *Element) RemoveAttribute(name string) bool {
	for i := range e.Attributes {
		if e.Attributes[i].Name == name && e.Attributes[i].Prefix == "" {
			e.Attributes = append(e.Attributes[:i], e.Attributes[i+1:]...)
			return true
		}
	}
	return false
}
