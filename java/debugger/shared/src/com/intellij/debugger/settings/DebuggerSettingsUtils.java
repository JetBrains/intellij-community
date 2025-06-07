// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DebuggerSettingsUtils {
  private static final Logger LOG = Logger.getInstance(DebuggerSettingsUtils.class);

  private DebuggerSettingsUtils() { }

  public static ClassFilter[] readFilters(List<? extends Element> children) {
    if (ContainerUtil.isEmpty(children)) {
      return ClassFilter.EMPTY_ARRAY;
    }

    //do not leave null elements in the resulting array in case of read errors
    List<ClassFilter> filters = new ArrayList<>(children.size());
    for (Element child : children) {
      try {
        filters.add(create(child));
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
    return filters.toArray(ClassFilter.EMPTY_ARRAY);
  }

  public static void writeFilters(@NotNull Element parentNode,
                                  @NonNls String tagName,
                                  ClassFilter[] filters) throws WriteExternalException {
    for (ClassFilter filter : filters) {
      Element element = new Element(tagName);
      parentNode.addContent(element);
      DefaultJDOMExternalizer.writeExternal(filter, element);
    }
  }

  public static ClassFilter create(Element element) throws InvalidDataException {
    ClassFilter filter = new ClassFilter();
    DefaultJDOMExternalizer.readExternal(filter, element);
    filter.matches(""); // compile the pattern in advance to have it ready before doing deepCopy
    return filter;
  }

  public static boolean filterEquals(ClassFilter[] filters1, ClassFilter[] filters2) {
    if (filters1.length != filters2.length) {
      return false;
    }
    final Set<ClassFilter> f1 = new HashSet<>(Math.max((int)(filters1.length / .75f) + 1, 16));
    final Set<ClassFilter> f2 = new HashSet<>(Math.max((int)(filters2.length / .75f) + 1, 16));
    Collections.addAll(f1, filters1);
    Collections.addAll(f2, filters2);
    return f2.equals(f1);
  }
}
