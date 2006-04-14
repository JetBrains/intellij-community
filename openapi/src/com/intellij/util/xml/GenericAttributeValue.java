/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;

/**
 * @author peter
 */
public interface GenericAttributeValue<T> extends GenericDomValue<T>{
  XmlAttribute getXmlAttribute();
  XmlAttributeValue getXmlAttributeValue();
}
