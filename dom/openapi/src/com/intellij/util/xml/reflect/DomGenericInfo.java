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

  @Nullable DomFixedChildDescription getFixedChildDescription(@NonNls String tagName);

  @Nullable DomFixedChildDescription getFixedChildDescription(@NonNls String tagName, @NonNls String namespaceKey);

  @Nullable DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName);

  @Nullable DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName, @NonNls String namespaceKey);

  @Nullable
  DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName);

  @Nullable
  DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName, @NonNls String namespaceKey);

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
  @Deprecated
  @Nullable
  XmlElement getNameElement(DomElement element);

  @Nullable
  GenericDomValue getNameDomElement(DomElement element);

}
