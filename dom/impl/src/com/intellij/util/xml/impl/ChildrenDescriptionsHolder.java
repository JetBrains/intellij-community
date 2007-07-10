/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class ChildrenDescriptionsHolder<T extends DomChildDescriptionImpl> {
  private final Map<XmlName, T> myMap = new TreeMap<XmlName, T>();
  private final ChildrenDescriptionsHolder<T> myDelegate;

  public ChildrenDescriptionsHolder(@Nullable final ChildrenDescriptionsHolder<T> delegate) {
    myDelegate = delegate;
  }

  public ChildrenDescriptionsHolder() {
    this(null);
  }

  final T addDescription(@NotNull T t) {
    myMap.put(t.getXmlName(), t);
    return t;
  }

  final void addDescriptions(@NotNull Collection<T> collection) {
    for (final T t : collection) {
      addDescription(t);
    }
  }

  @Nullable
  final T getDescription(final XmlName name) {
    final T t = myMap.get(name);
    if (t != null) return t;
    return myDelegate != null ? myDelegate.getDescription(name) : null;
  }

  @Nullable
  final T getDescription(@NotNull final String localName, String namespaceKey) {
    return getDescription(new XmlName(localName, namespaceKey));
  }

  @Nullable
  final T findDescription(@NotNull final String localName) {
    for (final XmlName xmlName : myMap.keySet()) {
      if (xmlName.getLocalName().equals(localName)) return myMap.get(xmlName);
    }
    return myDelegate != null ? myDelegate.findDescription(localName) : null;
  }

  @NotNull
  final List<T> getDescriptions() {
    final TreeSet<T> set = new TreeSet<T>(myMap.values());
    if (myDelegate != null) {
      set.addAll(myDelegate.myMap.values());
    }
    return new ArrayList<T>(set);
  }

  final void clear() {
    myMap.clear();
  }


}
