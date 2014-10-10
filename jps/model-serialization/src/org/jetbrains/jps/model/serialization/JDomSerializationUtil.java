/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  @NonNls protected static final String COMPONENT_ELEMENT = "component";
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
