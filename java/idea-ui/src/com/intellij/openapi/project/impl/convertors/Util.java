package com.intellij.openapi.project.impl.convertors;

import org.jdom.Element;

import java.util.Iterator;

class Util {
  @SuppressWarnings({"HardCodedStringLiteral"})
  static Element findComponent(Element root, String className) {
    for (Iterator iterator = root.getChildren("component").iterator(); iterator.hasNext();) {
      Element element = (Element)iterator.next();
      String className1 = element.getAttributeValue("class");
      if (className1 != null && className1.equals(className)) {
        return element;
      }
    }
    return null;
  }
}