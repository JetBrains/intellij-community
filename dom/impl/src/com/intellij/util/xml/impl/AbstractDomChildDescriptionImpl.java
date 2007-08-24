/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Factory;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.ReflectionUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public abstract class AbstractDomChildDescriptionImpl implements AbstractDomChildrenDescription, Comparable<AbstractDomChildDescriptionImpl> {
  private final Type myType;
  private Map<Class, Annotation> myCustomAnnotations;

  protected AbstractDomChildDescriptionImpl(final Type type) {
    myType = type;
  }

  public final void addCustomAnnotation(@NotNull Annotation annotation) {
    if (myCustomAnnotations == null) myCustomAnnotations = new THashMap<Class, Annotation>();
    myCustomAnnotations.put(annotation.annotationType(), annotation);
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
    return myCustomAnnotations == null ? null : (T)myCustomAnnotations.get(annotationClass);
  }

  @NotNull
  public final List<? extends DomElement> getStableValues(@NotNull final DomElement parent) {
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
  public final Type getType() {
    return myType;
  }

  @NotNull
  public DomNameStrategy getDomNameStrategy(@NotNull DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ReflectionUtil.getRawType(myType), false);
    return strategy == null ? parent.getNameStrategy() : strategy;
  }
}
