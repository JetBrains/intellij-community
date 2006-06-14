/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomGenericInfo {


  @Nullable
  String getElementName(DomElement element);

  @NotNull
  List<? extends DomChildrenDescription> getChildrenDescriptions();

  @NotNull
  List<? extends DomFixedChildDescription> getFixedChildrenDescriptions();

  @NotNull
  List<? extends DomCollectionChildDescription> getCollectionChildrenDescriptions();

  @NotNull
  List<? extends DomAttributeChildDescription> getAttributeChildrenDescriptions();

  @Nullable
  DomChildrenDescription getChildDescription(@NonNls String tagName);

  @Nullable
  DomFixedChildDescription getFixedChildDescription(@NonNls String tagName);

  @Nullable
  DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName);

  @Nullable
  DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName);

  Type[] getConcreteInterfaceVariants();

  /**
   * @return true, if there's no children in the element, only tag value accessors
   */
  boolean isTagValueElement();

  /**
   *
   * @param element
   * @return {@link com.intellij.psi.xml.XmlAttributeValue} or {@link com.intellij.psi.xml.XmlTag}
   */
  @Nullable
  XmlElement getNameElement(DomElement element);

  @Nullable
  GenericDomValue getNameDomElement(DomElement element);

}
