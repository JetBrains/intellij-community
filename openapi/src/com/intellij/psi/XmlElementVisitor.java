/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.xml.*;

public abstract class XmlElementVisitor extends JspElementVisitor {
  public void visitXmlElement(XmlElement element) {
    visitElement(element);
  }

  public void visitXmlFile(XmlFile file) {
    visitFile(file);
  }

  public void visitXmlAttribute(XmlAttribute attribute) {
    visitXmlElement(attribute);
  }

  public void visitXmlComment(XmlComment comment) {
    visitXmlElement(comment);
  }

  public void visitXmlDecl(XmlDecl decl) {
    visitXmlElement(decl);
  }

  public void visitXmlDocument(XmlDocument document) {
    visitXmlElement(document);
  }

  public void visitXmlProlog(XmlProlog prolog) {
    visitXmlElement(prolog);
  }

  public void visitXmlText(XmlText text) {
    visitXmlElement(text);
  }

  public void visitXmlTag(XmlTag tag) {
    visitXmlElement(tag);
  }

  public void visitXmlToken(XmlToken token) {
    visitXmlElement(token);
  }

  public void visitXmlAttributeValue(XmlAttributeValue value) {
    visitXmlElement(value);
  }

  public void visitXmlDoctype(XmlDoctype xmlDoctype) {
    visitXmlElement(xmlDoctype);
  }
}
