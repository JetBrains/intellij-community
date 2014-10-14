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
package com.intellij.codeInspection.ex;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges multiple inspections settings {@link #getSourceToolNames()} into another one {@link #getMergedToolName()}
 */
public abstract class InspectionElementsMerger {
  public static final ExtensionPointName<InspectionElementsMerger> EP_NAME = ExtensionPointName.create("com.intellij.inspectionElementsMerger");

  public abstract String getMergedToolName();
  protected abstract String[] getSourceToolNames();

  /**
   * marker node to prevent multiple merging. Is needed when inspection's settings are equal to default and are skipped in profile
   */
  public static String getMergedMarkerName(String toolName) {
    return toolName + "Merged";
  }

  public Element merge(Map<String, Element> inspectionElements) {
    LinkedHashMap<String, Element> scopes = null;
    List<Element> content = null;
    boolean enabled = false;
    String level = null;

    for (String sourceId : getSourceToolNames()) {
      final Element sourceElement = inspectionElements.get(sourceId);
      if (sourceElement != null) {
        if (content == null) {
          content = new ArrayList<Element>();
          scopes = new LinkedHashMap<String, Element>();
        }

        collectContent(sourceElement, content, scopes);

        enabled |= Boolean.parseBoolean(sourceElement.getAttributeValue(ToolsImpl.ENABLED_ATTRIBUTE));
        if (level == null) {
          level = getLevel(sourceElement);
        }
      }
    }
    if (content != null && !content.isEmpty()) {
      final Element toolElement = new Element(InspectionProfileImpl.INSPECTION_TOOL_TAG);
      toolElement.setAttribute(InspectionProfileImpl.CLASS_TAG, getMergedToolName());
      toolElement.setAttribute(ToolsImpl.ENABLED_ATTRIBUTE, String.valueOf(enabled));
      if (level != null) {
        toolElement.setAttribute(ToolsImpl.LEVEL_ATTRIBUTE, level);
      }
      toolElement.setAttribute(ToolsImpl.ENABLED_BY_DEFAULT_ATTRIBUTE, String.valueOf(enabled));

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

  protected static void collectContent(Element sourceElement, List<Element> options, Map<String, Element> scopes) {
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
        options.add(element.clone());
      }
    }
  }
}
