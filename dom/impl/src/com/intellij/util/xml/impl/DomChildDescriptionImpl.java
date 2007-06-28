/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class DomChildDescriptionImpl implements DomChildrenDescription {
  private final XmlName myTagName;
  private final Type myType;

  protected DomChildDescriptionImpl(final XmlName tagName, @NotNull final Type type) {
    myTagName = tagName;
    myType = type;
  }

  @NotNull
  public String getXmlElementName() {
    return myTagName.getLocalName();
  }

  @NotNull
  public final XmlName getXmlName() {
    return myTagName;
  }

  @NotNull
  public List<? extends DomElement> getStableValues(@NotNull final DomElement parent) {
    final List<? extends DomElement> list = getValues(parent);
    final ArrayList<DomElement> result = new ArrayList<DomElement>(list.size());
    final DomManager domManager = parent.getManager();
    for (int i = 0; i < list.size(); i++) {
      final int i1 = i;
      result.add(domManager.createStableValue(new Factory<DomElement>() {
        @Nullable
        public DomElement create() {
          if (!parent.isValid()) return null;

          final List<? extends DomElement> domElements = getValues(parent);
          return domElements.size() > i1 ? domElements.get(i1) : null;
        }
      }));
    }
    return result;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  @NotNull
  public String getCommonPresentableName(@NotNull DomElement parent) {
    return getCommonPresentableName(getDomNameStrategy(parent));
  }

  @NotNull
  public DomNameStrategy getDomNameStrategy(@NotNull DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ReflectionUtil.getRawType(myType), false);
    return strategy == null ? parent.getNameStrategy() : strategy;
  }

  @NotNull
  public final GenericInfoImpl getChildGenericInfo(Project project) {
    return DomManagerImpl.getDomManager(project).getGenericInfo(myType);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DomChildDescriptionImpl that = (DomChildDescriptionImpl)o;

    if (myTagName != null ? !myTagName.equals(that.myTagName) : that.myTagName != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myTagName != null ? myTagName.hashCode() : 0);
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }
}
