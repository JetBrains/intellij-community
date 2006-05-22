/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  Class[] getConcreteInterfaceVariants();

  /**
   * @return true, if there's no children in the element, only tag value accessors
   */
  boolean isTagValueElement();


}
