/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.xml;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

/**
 * @author Mike
 */
public interface XmlElementDescriptor extends PsiMetaData{
  XmlElementDescriptor[] EMPTY_ARRAY = new XmlElementDescriptor[0];

  String getQualifiedName();

  /**
   * Should return either simple or qualified name depending on the schema/DTD properties.
   * This name should be used in XML documents
   */
  String getDefaultName();

  //todo: refactor to support full DTD spec
  XmlElementDescriptor[] getElementsDescriptors(XmlTag context);
  XmlElementDescriptor getElementDescriptor(XmlTag childTag);

  XmlAttributeDescriptor[] getAttributesDescriptors();
  XmlAttributeDescriptor getAttributeDescriptor(String attributeName);
  XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute);

  XmlNSDescriptor getNSDescriptor();

  int getContentType();

  int CONTENT_TYPE_EMPTY = 0;
  int CONTENT_TYPE_ANY = 1;
  int CONTENT_TYPE_CHILDREN = 2;
  int CONTENT_TYPE_MIXED = 3;
}
