// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.convert;

import com.intellij.conversion.CannotConvertException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.Constants;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class JDomConvertingUtil {
  private JDomConvertingUtil() {
  }

  public static Element load(Path file) throws CannotConvertException {
    try {
      return JDOMUtil.load(file);
    }
    catch (JDOMException | IOException e) {
      throw new CannotConvertException(file + ": " + e.getMessage(), e);
    }
  }

  public static String getOptionValue(Element element, String optionName) {
    return JDOMExternalizerUtil.readField(element, optionName);
  }

  public static Predicate<Element> createAttributeValueFilter(@NonNls String name, @NonNls String value) {
    return createAttributeValueFilter(name, Collections.singleton(value));
  }

  public static Predicate<Element> createAttributeValueFilter(@NonNls String name, @NonNls Collection<String> value) {
    return element -> value.contains(element.getAttributeValue(name));
  }

  public static Predicate<Element> createElementWithAttributeFilter(String elementName, String attributeName, String attributeValue) {
    Predicate<Element> f1 = createElementNameFilter(elementName);
    Predicate<Element> f2 = createAttributeValueFilter(attributeName, attributeValue);
    return it -> f1.test(it) && f2.test(it);
  }

  public static Predicate<Element> createElementNameFilter(final @NonNls String elementName) {
    return element -> elementName.equals(element.getName());
  }

  public static List<Element> removeChildren(final Element element, Predicate<? super Element> filter) {
    List<Element> toRemove = new ArrayList<>();
    final List<Element> list = element.getChildren();
    for (Element e : list) {
      if (filter.test(e)) {
        toRemove.add(e);
      }
    }
    for (Element e : toRemove) {
      element.removeContent(e);
    }
    return toRemove;
  }

  public static @Nullable Element findChild(Element parent, Predicate<? super Element> filter) {
    final List<Element> list = parent.getChildren();
    for (Element e : list) {
      if (filter.test(e)) {
        return e;
      }
    }
    return null;
  }

  public static void removeDuplicatedOptions(final Element element) {
    List<Element> children = new ArrayList<>(element.getChildren(Constants.OPTION));
    Set<String> names = new HashSet<>();
    for (Element child : children) {
      if (!names.add(child.getAttributeValue(Constants.NAME))) {
        element.removeContent(child);
      }
    }
  }
}
