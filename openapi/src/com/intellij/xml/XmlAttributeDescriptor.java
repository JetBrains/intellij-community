/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.xml;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;

/**
 * @author Mike
 */
public interface XmlAttributeDescriptor extends PsiMetaData{
  XmlAttributeDescriptor[] EMPTY = new XmlAttributeDescriptor[0];

  /**
   * Should return either simple or qualified name depending on the schema/DTD properties.
   * This name should be used in XML documents
   */
  String getDefaultName();

  boolean isRequired();
  boolean isFixed();
  boolean hasIdType();
  boolean hasIdRefType();

  String getDefaultValue();

  //todo: refactor to hierarchy of value descriptor?
  boolean isEnumerated();
  String[] getEnumeratedValues();

  String validateValue(XmlElement context, String value);
}
