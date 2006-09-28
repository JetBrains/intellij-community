/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.openapi.project.Project;
import com.intellij.util.xml.AnnotatedElement;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public interface DomChildrenDescription extends AnnotatedElement {
  String getXmlElementName();
  @NotNull
  List<? extends DomElement> getValues(DomElement parent);
  @NotNull
  List<? extends DomElement> getStableValues(DomElement parent);
  Type getType();
  String getCommonPresentableName(DomNameStrategy strategy);
  String getCommonPresentableName(DomElement parent);
  DomNameStrategy getDomNameStrategy(DomElement parent);
  DomGenericInfo getChildGenericInfo(Project project);
}
