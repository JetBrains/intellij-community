/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.AnnotatedElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public interface DomChildrenDescription extends AnnotatedElement {

  @NotNull
  XmlName getXmlName();

  @NotNull
  List<? extends DomElement> getValues(@NotNull DomElement parent);

  @NotNull
  Type getType();

  @NotNull
  String getXmlElementName();

  @NotNull
  List<? extends DomElement> getStableValues(@NotNull DomElement parent);

  @NotNull
  String getCommonPresentableName(@NotNull DomNameStrategy strategy);

  @NotNull
  String getCommonPresentableName(@NotNull DomElement parent);

  @NotNull
  DomNameStrategy getDomNameStrategy(@NotNull DomElement parent);

}
