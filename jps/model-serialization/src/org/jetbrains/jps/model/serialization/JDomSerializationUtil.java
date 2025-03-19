// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.Constants;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JDomSerializationUtil {
  public static final @NonNls String COMPONENT_ELEMENT = "component";

  private JDomSerializationUtil() {
  }

  public static @Nullable Element findComponent(@Nullable Element root, @NonNls String componentName) {
    for (Element element : JDOMUtil.getChildren(root, COMPONENT_ELEMENT)) {
      if (isComponent(componentName, element)) {
        return element;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public static boolean isComponent(@NotNull String componentName, @NotNull Element element) {
    return componentName.equals(element.getAttributeValue(Constants.NAME));
  }

  @ApiStatus.Internal
  public static Element createComponentElement(final String componentName) {
    final Element element = new Element(COMPONENT_ELEMENT);
    element.setAttribute(Constants.NAME, componentName);
    return element;
  }

  @ApiStatus.Internal
  public static @NotNull Element findOrCreateComponentElement(@NotNull Element root, @NotNull String componentName) {
    Element component = findComponent(root, componentName);
    if (component == null) {
      component = createComponentElement(componentName);
      addComponent(root, component);
    }
    return component;
  }

  @ApiStatus.Internal
  public static void addComponent(@NotNull Element root, @NotNull Element component) {
    String componentName = component.getAttributeValue(Constants.NAME);
    Element old = findComponent(root, componentName);
    if (old != null) {
      root.removeContent(old);
    }

    for (int i = 0; i < root.getContent().size(); i++) {
      Content o = root.getContent().get(i);
      if (o instanceof Element) {
        Element element = (Element)o;
        if (element.getName().equals(COMPONENT_ELEMENT)) {
          final String name = element.getAttributeValue(Constants.NAME);
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
