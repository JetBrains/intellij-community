/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface DomChildrenDescription extends AbstractDomChildrenDescription {

  @NotNull
  XmlName getXmlName();

  @NotNull
  String getXmlElementName();

  @NotNull
  String getCommonPresentableName(@NotNull DomNameStrategy strategy);

  @NotNull
  String getCommonPresentableName(@NotNull DomElement parent);

}
