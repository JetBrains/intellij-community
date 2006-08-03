/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.WeakFactoryMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.Implementation;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author peter
*/
class ImplementationClassCache extends WeakFactoryMap<Class<? extends DomElement>, Class<? extends DomElement>> {
  private static final Comparator<Class<? extends DomElement>> CLASS_COMPARATOR = new Comparator<Class<? extends DomElement>>() {
    public int compare(final Class<? extends DomElement> o1, final Class<? extends DomElement> o2) {
      if (o1.isAssignableFrom(o2)) return 1;
      if (o2.isAssignableFrom(o1)) return -1;
      if (o1.equals(o2)) return 0;
      throw new AssertionError("Incompatible implementation classes: " + o1 + " & " + o2);
    }
  };


  private final Map<Class<? extends DomElement>, Class<? extends DomElement>> myImplementationClasses =
  new THashMap<Class<? extends DomElement>, Class<? extends DomElement>>();

  @Nullable
  protected Class<? extends DomElement> create(final Class<? extends DomElement> concreteInterface) {
    final TreeSet<Class<? extends DomElement>> set = new TreeSet<Class<? extends DomElement>>(CLASS_COMPARATOR);
    findImplementationClassDFS(concreteInterface, set);
    if (!set.isEmpty()) {
      return set.first();
    }
    final Implementation implementation = DomReflectionUtil.findAnnotationDFS(concreteInterface, Implementation.class);
    return implementation == null ? null : implementation.value();
  }

  private void findImplementationClassDFS(final Class concreteInterface, SortedSet<Class<? extends DomElement>> results) {
    Class<? extends DomElement> aClass = myImplementationClasses.get(concreteInterface);
    if (aClass != null) {
      results.add(aClass);
    }
    for (final Class aClass1 : ReflectionCache.getInterfaces(concreteInterface)) {
      findImplementationClassDFS(aClass1, results);
    }
  }

  public final <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass) {
    assert domElementClass.isAssignableFrom(implementationClass);
  myImplementationClasses.put(domElementClass, implementationClass);
  clear();
  }

}
