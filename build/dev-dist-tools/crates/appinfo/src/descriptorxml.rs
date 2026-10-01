// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The element tree a plugin descriptor patch runs over, and the two halves of the round trip the platform performs.
//!
//! The platform reads a descriptor with `JDOMUtil.load` and writes every stage with `JDOMUtil.write`. That pair
//! rewrites whitespace, attribute quoting and CDATA on every descriptor, before any patch runs. So the bytes a plugin's
//! main jar receives are the bytes this round trip produces.
//!
//! The round trip covers the constructs of the descriptors that the rules declare, and nothing else. [`read`] refuses
//! every other construct and names it, so a new construct in a descriptor stops the build instead of changing bytes.
//! The two halves are asymmetric on purpose:
//!
//! - the reader drops a comment, the XML declaration and every whitespace-only text run. It folds a CDATA section into
//!   plain text.
//! - the writer indents with two spaces and trims each text run. It prints a CDATA section only where the patch put
//!   one back.

mod read;
mod write;

pub use read::read;
pub use write::write;

/// One namespace declaration: a prefix and the URI it binds. An empty prefix is the default namespace.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Namespace {
    pub prefix: String,
    pub uri: String,
}

/// One attribute of an element. The name is the qualified name that the document wrote, `xsi:schemaLocation` for
/// example.
///
/// `uri` is the namespace of a prefixed attribute, and it is empty for an attribute without a prefix. The writer
/// declares it before the attribute when no ancestor binds the prefix (`XMLOutputter.java:873-876`). An include moves
/// an element into a document whose root can lack the declaration.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Attribute {
    pub name: String,
    pub uri: String,
    pub value: String,
}

/// One child of an element.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Node {
    /// A child element.
    Element(Element),
    /// A text run. The reader never produces a whitespace-only one.
    Text(String),
    /// A text run the writer frames as `<![CDATA[…]]>`. The reader never produces one, because the platform's stream
    /// reader coalesces text. Only the patch creates one, for `description`, `change-notes` and an embedded module.
    CData(String),
}

impl Node {
    pub const fn as_element(&self) -> Option<&Element> {
        match self {
            Self::Element(element) => Some(element),
            _ => None,
        }
    }

    pub const fn as_element_mut(&mut self) -> Option<&mut Element> {
        match self {
            Self::Element(element) => Some(element),
            _ => None,
        }
    }
}

/// One element of the tree.
///
/// `prefix` and `uri` are the element's own namespace. `namespaces` are the declarations the document wrote on this
/// element, in document order. Only a root element holds one, because the reader refuses a declaration below the root.
/// The writer prints the element's own namespace first, then these, then the attributes. So a declared `xmlns:xi`
/// always comes before every attribute, whatever the source order was.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Element {
    pub name: String,
    pub prefix: String,
    pub uri: String,
    pub namespaces: Vec<Namespace>,
    pub attributes: Vec<Attribute>,
    pub children: Vec<Node>,
}

impl Element {
    /// Returns an element with no namespace, no attribute and no child.
    pub fn new(name: &str) -> Self {
        Self {
            name: name.to_owned(),
            ..Self::default()
        }
    }

    /// Returns the name the writer prints: the prefix, a colon and the local name, or the local name alone.
    pub fn qualified_name(&self) -> String {
        if self.prefix.is_empty() {
            self.name.clone()
        } else {
            format!("{}:{}", self.prefix, self.name)
        }
    }

    /// Returns the position in `children` of the first child element with this name and **no namespace**.
    ///
    /// The namespace condition is not an omission. `Element.getChild(String)` resolves against
    /// `Namespace.NO_NAMESPACE` (`community/platform/util/jdom/src/org/jdom/Element.java:1452`). So an `xi:include`
    /// never answers a lookup for `include`, and every lookup of the patch has this shape.
    pub fn child_index(&self, name: &str) -> Option<usize> {
        self.children.iter().position(|node| {
            node.as_element()
                .is_some_and(|element| element.name == name && element.has_no_namespace())
        })
    }

    /// Returns the element at the position that [`Element::child_index`] returns.
    pub fn child(&self, name: &str) -> Option<&Self> {
        self.child_index(name).and_then(|index| self.children[index].as_element())
    }

    /// Returns the first child element with this name in this namespace URI.
    ///
    /// It is `Element.getChild(String, Namespace)`
    /// (`community/platform/util/jdom/src/org/jdom/Element.java:1425-1435`), which matches on the URI and never on
    /// the prefix. The includes stage needs it for `xi:fallback`, whose namespace is the namespace of the
    /// `xi:include`.
    pub fn child_in_namespace(&self, name: &str, uri: &str) -> Option<&Self> {
        self.children
            .iter()
            .filter_map(Node::as_element)
            .find(|element| element.name == name && element.uri == uri)
    }

    /// Reports whether this element has no namespace, which is what `Element.getChild(String)` asks of a child.
    pub const fn has_no_namespace(&self) -> bool {
        self.uri.is_empty()
    }

    /// Replaces the child at this position with these elements.
    ///
    /// It is `Element.setContent(int, Collection)` (`community/platform/util/jdom/src/org/jdom/Element.java:781-785`),
    /// which removes the one child and inserts the collection where it stood. So an empty replacement deletes the
    /// child. That is how an `xi:include` that resolves to nothing leaves the tree.
    pub fn replace_child_at(&mut self, index: usize, replacement: Vec<Self>) {
        self.children.splice(index..=index, replacement.into_iter().map(Node::Element));
    }

    /// Puts an element at this position, and moves every later child one place along.
    ///
    /// The position counts every child and not only the elements, because it decides the bytes when the patch inserts
    /// an element after an anchor.
    pub fn insert_child(&mut self, index: usize, child: Self) {
        self.children.insert(index, Node::Element(child));
    }

    /// Returns the element's text the way `Element.getText` builds it: every text and CDATA child joined, with every
    /// child **element** skipped (`community/platform/util/jdom/src/org/jdom/Element.java:497-521`).
    ///
    /// So an element with markup inside loses that markup when the patch reads this and writes a CDATA section back.
    /// The platform does the same, and the CDATA restoration of `description` relies on it.
    pub fn text(&self) -> String {
        let mut result = String::new();
        for node in &self.children {
            match node {
                Node::Text(text) | Node::CData(text) => result.push_str(text),
                Node::Element(_) => {}
            }
        }
        result
    }

    /// Replaces every child with one text run, the way `Element.setText` does
    /// (`community/platform/util/jdom/src/org/jdom/Element.java:626-632`).
    ///
    /// An empty string still adds a text node. The writer then prints `<name />`, because a whitespace-only run is
    /// insignificant to it.
    pub fn set_text(&mut self, text: &str) {
        self.children = vec![Node::Text(text.to_owned())];
    }

    /// Replaces every child with one CDATA run, the way `Element.setContent(CDATA)` does
    /// (`community/platform/util/jdom/src/org/jdom/Element.java:928-932`).
    pub fn set_cdata(&mut self, text: String) {
        self.children = vec![Node::CData(text)];
    }

    /// Returns the value of the attribute with this qualified name.
    pub fn attribute(&self, name: &str) -> Option<&str> {
        self.attributes
            .iter()
            .find(|attribute| attribute.name == name)
            .map(|attribute| attribute.value.as_str())
    }

    /// Sets an attribute. An attribute the element already states changes in place, and a new one goes last.
    ///
    /// The in-place rule decides the bytes. `Element.setAttribute` replaces the value inside the JDOM attribute list
    /// and does not move the entry, and the writer prints that list in order.
    pub fn set_attribute(&mut self, name: &str, value: &str) {
        match self.attributes.iter_mut().find(|attribute| attribute.name == name) {
            Some(attribute) => attribute.value = value.to_owned(),
            None => self.attributes.push(Attribute {
                name: name.to_owned(),
                uri: String::new(),
                value: value.to_owned(),
            }),
        }
    }

    /// Removes an attribute, if the element states it.
    pub fn remove_attribute(&mut self, name: &str) {
        self.attributes.retain(|attribute| attribute.name != name);
    }
}

#[cfg(test)]
mod tests;
