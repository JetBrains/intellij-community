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
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UnusedDeclarationInspectionMerger extends InspectionElementsMerger {
  @Override
  public String getNewToolName() {
    return UnusedDeclarationInspectionBase.SHORT_NAME;
  }

  @Override
  public Element merge(Map<String, Element> elements) {
    final Element unusedSymbolElement = elements.get("UNUSED_SYMBOL");
    final Element unusedDeclarationElement = elements.get("UnusedDeclaration");
    if (unusedDeclarationElement != null || unusedSymbolElement != null) {
      final Element toolElement = new Element(InspectionProfileImpl.INSPECTION_TOOL_TAG);
      final LinkedHashMap<String, Element> scopes = new LinkedHashMap<String, Element>();
      final List<Element> content = new ArrayList<Element>();
      boolean enabled = cloneContent(unusedDeclarationElement, content, scopes);
      enabled |= cloneContent(unusedSymbolElement, content, scopes);
      
      toolElement.setAttribute(InspectionProfileImpl.CLASS_TAG, getNewToolName());
      toolElement.setAttribute(ToolsImpl.ENABLED_ATTRIBUTE, String.valueOf(enabled));

      String level = getLevel(unusedSymbolElement);
      if (level == null) {
        level = getLevel(unusedDeclarationElement);
      }

      if (level != null) {
        toolElement.setAttribute(ToolsImpl.LEVEL_ATTRIBUTE, level);
      }

      for (Element scopeEl : scopes.values()) {
        toolElement.addContent(scopeEl);
      }
      for (Element element : content) {
        toolElement.addContent(element);
      }
      return toolElement;
    }
    return null;
  }

  private static String getLevel(Element element) {
    return element != null ? element.getAttributeValue(ToolsImpl.LEVEL_ATTRIBUTE) : HighlightSeverity.WARNING.getName();
  }
  
  protected static boolean cloneContent(Element sourceElement, List<Element> elements, Map<String, Element> scopes) {
    if (sourceElement != null) {
      for (Element element : sourceElement.getChildren()) {
        if ("scope".equals(element.getName())) {
          String scopeName = element.getAttributeValue("name");
          if (scopes.containsKey(scopeName)) {
            Element scopeElement = scopes.get(scopeName);
            for (Element scopeEl : element.getChildren()) {
              scopeElement.addContent(scopeEl.clone());
            }
          } else {
            scopes.put(scopeName, element.clone());
          }
          continue;
        }
        elements.add(element.clone());
      }
      return Boolean.parseBoolean(sourceElement.getAttributeValue("enabled"));
    }
    return false;
  }
}
