/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;

/**
 * @author Mike
 */
public interface XmlTag extends XmlElement, PsiNamedElement, PsiMetaOwner, XmlTagChild {
  XmlTag[] EMPTY = new XmlTag[0];

  String getName();
  String getNamespace();
  String getLocalName();

  XmlElementDescriptor getDescriptor();

  XmlAttribute[] getAttributes();
  XmlAttribute getAttribute(String name, String namespace);

  String getAttributeValue(String name);
  String getAttributeValue(String name, String namespace);

  XmlAttribute setAttribute(String name, String namespace, String value) throws IncorrectOperationException;
  XmlAttribute setAttribute(String name, String value) throws IncorrectOperationException;

  XmlTag createChildTag(String localName, String namespace, String bodyText, boolean enforseNamespacesDeep);

  XmlTag[] getSubTags();
  XmlTag[] findSubTags(String qname);
  XmlTag[] findSubTags(String localName, String namespace);
  XmlTag findFirstSubTag(String qname);

  String getNamespaceByPrefix(String prefix);
  String getPrefixByNamespace(String prefix);
  String[] knownNamespaces();

  XmlTagValue getValue();

  XmlNSDescriptor getNSDescriptor(String namespace, boolean strict);

  boolean isEmpty();
}
