/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.conversion.CannotConvertException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
@SuppressWarnings({"unchecked"})
public class JDomConvertingUtil extends JDomSerializationUtil {

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
      @Override
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
    copyChildren(from, to, Conditions.<Element>alwaysTrue());
  }

  public static void copyChildren(Element from, Element to, Condition<Element> filter) {
    final List<Element> list = from.getChildren();
    for (Element element : list) {
      if (filter.value(element)) {
        to.addContent((Element)element.clone());
      }
    }
  }

  public static Condition<Element> createElementNameFilter(@NonNls final String elementName) {
    return new Condition<Element>() {
      @Override
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
