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