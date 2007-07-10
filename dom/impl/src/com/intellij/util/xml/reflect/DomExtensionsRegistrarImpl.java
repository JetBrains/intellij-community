/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.SmartList;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.XmlName;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DomExtensionsRegistrarImpl implements DomExtensionsRegistrar {
  private final List<DomExtensionImpl> myAttributes = new SmartList<DomExtensionImpl>();
  private final List<DomExtensionImpl> myFixeds = new SmartList<DomExtensionImpl>();
  private final List<DomExtensionImpl> myCollections = new SmartList<DomExtensionImpl>();
  private final Set<Object> myDependencies = new THashSet<Object>();

  public List<DomExtensionImpl> getAttributes() {
    return myAttributes;
  }
  public List<DomExtensionImpl> getFixeds() {
    return myFixeds;
  }

  public List<DomExtensionImpl> getCollections() {
    return myCollections;
  }

  @NotNull
  public final DomExtension registerFixedNumberChildrenExtension(@NotNull final XmlName name, @NotNull final Type type, final int count) {
    assert count > 0;
    return addExtension(myFixeds, name, type).setCount(count);
  }

  @NotNull
  public DomExtension registerFixedNumberChildExtension(@NotNull final XmlName name, @NotNull final Type type) {
    return registerFixedNumberChildrenExtension(name, type, 1);
  }

  @NotNull
  public DomExtension registerCollectionChildrenExtension(@NotNull final XmlName name, @NotNull final Type type) {
    return addExtension(myCollections, name, type);
  }

  @NotNull
  public DomExtension registerAttributeChildExtension(@NotNull final XmlName name, final Type parameterType) {
    return addExtension(myAttributes, name, ParameterizedTypeImpl.make(GenericAttributeValue.class, new Type[]{parameterType}, null));
  }

  private static DomExtensionImpl addExtension(final List<DomExtensionImpl> list, final XmlName name, final Type type) {
    final DomExtensionImpl extension = new DomExtensionImpl(type, name);
    list.add(extension);
    return extension;
  }

  public final void addDependencies(Object[] deps) {
    myDependencies.addAll(Arrays.asList(deps));
  }

  public Object[] getDependencies() {
    return myDependencies.toArray();
  }
}
