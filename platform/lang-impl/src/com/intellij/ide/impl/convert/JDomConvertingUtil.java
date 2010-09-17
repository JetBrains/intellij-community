/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.impl.convert;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.conversion.CannotConvertException;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
@SuppressWarnings({"unchecked"})
public class JDomConvertingUtil {
  @NonNls private static final String COMPONENT_ELEMENT = "component";
  @NonNls private static final String OPTION_ELEMENT = "option";
  @NonNls private static final String NAME_ATTRIBUTE = "name";
  @NonNls private static final String VALUE_ATTRIBUTE = "value";

  private JDomConvertingUtil() {
  }

  public static Document loadDocument(File file) throws CannotConvertException {
    try {
      return JDOMUtil.loadDocument(file);
    }
    catch (JDOMException e) {
      throw new CannotConvertException(file.getAbsolutePath() + ": " + e.getMessage(), e);
    }
    catch (IOException e) {
      throw new CannotConvertException(file.getAbsolutePath() + ": " + e.getMessage(), e);
    }
  }

  public static String getOptionValue(Element element, String optionName) {
    return JDOMExternalizerUtil.readField(element, optionName);
  }

  @Nullable
  public static String getSettingsValue(@Nullable Element element) {
    return element != null ? element.getAttributeValue("value") : null;
  }

  @Nullable
  public static Element getSettingsElement(@Nullable Element element, String name) {
    for (Element child : JDOMUtil.getChildren(element, "setting")) {
      if (child.getAttributeValue("name").equals(name)) {
        return child;
      }
    }
    return null;
  }

  public static Condition<Element> createAttributeValueFilter(@NonNls final String name, @NonNls final String value) {
    return createAttributeValueFilter(name, Collections.singleton(value));
  }

  public static Condition<Element> createAttributeValueFilter(@NonNls final String name, @NonNls final Collection<String> value) {
    return new Condition<Element>() {
      public boolean value(final Element element) {
        return value.contains(element.getAttributeValue(name));
      }
    };
  }

  public static Condition<Element> createOptionElementFilter(@NonNls final String optionName) {
    return createElementWithAttributeFilter(OPTION_ELEMENT, NAME_ATTRIBUTE, optionName);
  }

  public static Condition<Element> createElementWithAttributeFilter(final String elementName, final String attributeName, final String attributeValue) {
    return Conditions.and(createElementNameFilter(elementName),
                          createAttributeValueFilter(attributeName, attributeValue));
  }

  public static void copyAttributes(Element from, Element to) {
    final List<Attribute> attributes = from.getAttributes();
    for (Attribute attribute : attributes) {
      to.setAttribute(attribute.getName(), attribute.getValue());
    }
  }

  public static void copyChildren(Element from, Element to) {
    copyChildren(from, to, Condition.TRUE);
  }

  public static void copyChildren(Element from, Element to, Condition<Element> filter) {
    final List<Element> list = from.getChildren();
    for (Element element : list) {
      if (filter.value(element)) {
        to.addContent((Element)element.clone());
      }
    }
  }

  @Nullable
  public static Element findComponent(Element root, @NonNls String componentName) {
    final List<Element> list = root.getChildren(COMPONENT_ELEMENT);
    for (Element element : list) {
      if (componentName.equals(element.getAttributeValue(NAME_ATTRIBUTE))) {
        return element;
      }
    }
    return null;
  }

  public static Condition<Element> createElementNameFilter(@NonNls final String elementName) {
    return new Condition<Element>() {
      public boolean value(final Element element) {
        return elementName.equals(element.getName());
      }
    };
  }

  public static List<Element> removeChildren(final Element element, final Condition<Element> filter) {
    List<Element> toRemove = new ArrayList<Element>();
    final List<Element> list = element.getChildren();
    for (Element e : list) {
      if (filter.value(e)) {
        toRemove.add(e);
      }
    }
    for (Element e : toRemove) {
      element.removeContent(e);
    }
    return toRemove;
  }

  public static Element createOptionElement(String name, String value) {
    final Element element = new Element(OPTION_ELEMENT);
    element.setAttribute(NAME_ATTRIBUTE, name);
    element.setAttribute(VALUE_ATTRIBUTE, value);
    return element;
  }

  public static void addChildAfter(final Element parent, final Element child, final Condition<Element> filter, boolean addFirstIfNotFound) {
    List list = parent.getContent();
    for (int i = 0; i < list.size(); i++) {
      Object o = list.get(i);
      if (o instanceof Element && filter.value((Element)o)) {
        if (i < list.size() - 1) {
          parent.addContent(i + 1, child);
        }
        else {
          parent.addContent(child);
        }
        return;
      }
    }
    if (addFirstIfNotFound) {
      parent.addContent(0, child);
    }
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

  @Nullable
  public static Element findChild(Element parent, final Condition<Element> filter) {
    final List<Element> list = parent.getChildren();
    for (Element e : list) {
      if (filter.value(e)) {
        return e;
      }
    }
    return null;
  }

  public static void removeDuplicatedOptions(final Element element) {
    List<Element> children = new ArrayList<Element>(element.getChildren(OPTION_ELEMENT));
    Set<String> names = new HashSet<String>();
    for (Element child : children) {
      if (!names.add(child.getAttributeValue(NAME_ATTRIBUTE))) {
        element.removeContent(child);
      }
    }
  }
}
