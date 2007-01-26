/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface GenericAttributeValue<T> extends GenericDomValue<T>{
  @Nullable
  XmlAttribute getXmlAttribute();
  
  @Nullable
  XmlAttributeValue getXmlAttributeValue();
}
