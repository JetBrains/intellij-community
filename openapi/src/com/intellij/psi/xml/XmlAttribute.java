/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.PsiNamedElement;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public interface XmlAttribute extends XmlElement, PsiNamedElement {
  XmlAttribute[] EMPTY_ARRAY = new XmlAttribute[0];

  String getName();
  String getLocalName();
  String getNamespace();

  XmlTag getParent();

  String getValue();

  boolean isNamespaceDeclaration();

  XmlAttributeDescriptor getDescriptor();

  // Tree functions

  // TODO: remove this. For tree functions XmlChildRole.XXX_FINDER should be used.
  // In this case function is also used to get references from attribute value
  XmlAttributeValue getValueElement();

  void setValue(String value) throws IncorrectOperationException;
}
