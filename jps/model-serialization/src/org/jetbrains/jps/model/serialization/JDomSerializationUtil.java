/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class JDomSerializationUtil {
  @NonNls public static final String COMPONENT_ELEMENT = "component";
  @NonNls protected static final String OPTION_ELEMENT = "option";
  @NonNls protected static final String NAME_ATTRIBUTE = "name";
  @NonNls protected static final String VALUE_ATTRIBUTE = "value";

  @Nullable
  public static Element findComponent(@Nullable Element root, @NonNls String componentName) {
    for (Element element : JDOMUtil.getChildren(root, COMPONENT_ELEMENT)) {
      if (componentName.equals(element.getAttributeValue(NAME_ATTRIBUTE))) {
        return element;
      }
    }
    return null;
  }

  public static Element createComponentElement(final String componentName) {
    final Element element = new Element(COMPONENT_ELEMENT);
    element.setAttribute(NAME_ATTRIBUTE, componentName);
    return element;
  }

  @NotNull
  public static Element findOrCreateComponentElement(@NotNull Element root, @NotNull String componentName) {
    Element component = findComponent(root, componentName);
    if (component == null) {
      component = createComponentElement(componentName);
      addComponent(root, component);
    }
    return component;
  }

  public static void addComponent(final Element root, final Element component) {
    String componentName = component.getAttributeValue(NAME_ATTRIBUTE);
    final Element old = findComponent(root, componentName);
    if (old != null) {
      root.removeContent(old);
    }

    for (int i = 0; i < root.getContent().size(); i++) {
      Object o = root.getContent().get(i);
      if (o instanceof Element) {
        Element element = (Element)o;
        if (element.getName().equals(COMPONENT_ELEMENT)) {
          final String name = element.getAttributeValue(NAME_ATTRIBUTE);
          if (componentName.compareTo(name) < 0) {
            root.addContent(i, component);
            return;
          }
        }
      }
    }
    root.addContent(component);
  }

}
