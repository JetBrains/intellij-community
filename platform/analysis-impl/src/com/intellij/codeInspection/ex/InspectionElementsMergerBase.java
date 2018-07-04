/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.*;


public abstract class InspectionElementsMergerBase extends InspectionElementsMerger {

  /**
   * serialize old inspection settings as they could appear in old profiles
   */
  protected Element writeOldSettings(String sourceToolName) throws WriteExternalException {
    final Element sourceElement = new Element(InspectionProfileImpl.INSPECTION_TOOL_TAG);
    sourceElement.setAttribute(InspectionProfileImpl.CLASS_TAG, sourceToolName);
    sourceElement.setAttribute(ToolsImpl.ENABLED_ATTRIBUTE, String.valueOf(isEnabledByDefault(sourceToolName)));
    sourceElement.setAttribute(ToolsImpl.LEVEL_ATTRIBUTE, getDefaultSeverityLevel(sourceToolName));
    sourceElement.setAttribute(ToolsImpl.ENABLED_BY_DEFAULT_ATTRIBUTE, String.valueOf(isEnabledByDefault(sourceToolName)));
    return sourceElement;
  }

  protected String getDefaultSeverityLevel(String sourceToolName) {
    return HighlightSeverity.WARNING.getName();
  }

  protected boolean isEnabledByDefault(String sourceToolName) {
    return true;
  }

  /**
   * marker node to prevent multiple merging. Is needed when inspection's settings are equal to default and are skipped in profile
   */
  protected static String getMergedMarkerName(String toolName) {
    return toolName + "Merged";
  }

  protected boolean markSettingsMerged(Map<String, Element> inspectionsSettings) {
    final Element merge = merge(inspectionsSettings, true);
    if (merge != null) {
      final Element defaultElement = merge(Collections.emptyMap(), true);
      return !JDOMUtil.areElementsEqual(merge, defaultElement);
    }
    return false;
  }

  protected boolean areSettingsMerged(Map<String, Element> inspectionsSettings, Element inspectionElement) {
    final Element merge = merge(inspectionsSettings, true);
    return merge != null && JDOMUtil.areElementsEqual(merge, inspectionElement);
  }

  protected Element merge(Map<String, Element> inspectionElements) {
    return merge(inspectionElements, false);
  }

  protected Element merge(Map<String, Element> inspectionElements, boolean includeDefaults) {
    LinkedHashMap<String, Element> scopes = new LinkedHashMap<>();
    LinkedHashMap<String, Set<String>> mentionedTools = new LinkedHashMap<>();
    boolean enabled = false;
    String level = null;

    final Element toolElement = new Element(InspectionProfileImpl.INSPECTION_TOOL_TAG);

    for (String sourceToolName : getSourceToolNames()) {
      Element sourceElement = getSourceElement(inspectionElements, sourceToolName);

      if (sourceElement == null) {
        if (includeDefaults) {
          try {
            sourceElement = writeOldSettings(sourceToolName);
          }
          catch (WriteExternalException ignored) {}
        } 
        else {
          enabled |= isEnabledByDefault(sourceToolName);
          if (level == null) {
            level = getDefaultSeverityLevel(sourceToolName);
          }
        }
      }

      if (sourceElement != null) {
        collectContent(sourceToolName, sourceElement, toolElement, scopes, mentionedTools);

        enabled |= Boolean.parseBoolean(sourceElement.getAttributeValue(ToolsImpl.ENABLED_ATTRIBUTE));
        if (level == null) {
          level = getLevel(sourceElement);
        }
      }
    }
    if (!toolElement.getChildren().isEmpty()) {
      toolElement.setAttribute(InspectionProfileImpl.CLASS_TAG, getMergedToolName());
      toolElement.setAttribute(ToolsImpl.ENABLED_ATTRIBUTE, String.valueOf(enabled));
      if (level != null) {
        toolElement.setAttribute(ToolsImpl.LEVEL_ATTRIBUTE, level);
      }
      toolElement.setAttribute(ToolsImpl.ENABLED_BY_DEFAULT_ATTRIBUTE, String.valueOf(enabled));

      for (String scopeName : scopes.keySet()) {
        Element scopeEl = scopes.get(scopeName);
        Set<String> toolsWithScope = mentionedTools.get(scopeName);
        //copy default settings if tool has no such scope defined
        for (String sourceToolName : getSourceToolNames()) {
          if (!toolsWithScope.contains(sourceToolName)) {
            copyDefaultSettings(scopeEl, inspectionElements, sourceToolName);
          }
        }
        toolElement.addContent(scopeEl);
      }
      
      return toolElement;
    }
    return null;
  }

  protected Element getSourceElement(Map<String, Element> inspectionElements, String sourceToolName) {
    return inspectionElements.get(sourceToolName);
  }

  private void copyDefaultSettings(Element targetElement, Map<String, Element> inspectionElements, String sourceToolName) {
    Element oldElement = getSourceElement(inspectionElements, sourceToolName);
    if (oldElement != null) {
      Element defaultElement = wrapElement(sourceToolName, oldElement, targetElement);
      oldElement.getChildren().stream()
        .filter(child -> !"scope".equals(child.getName()))
        .forEach(child -> defaultElement.addContent(child.clone()));
    }
  }

  private static String getLevel(Element element) {
    return element != null ? element.getAttributeValue(ToolsImpl.LEVEL_ATTRIBUTE) : HighlightSeverity.WARNING.getName();
  }

  protected void collectContent(String sourceToolName,
                                Element sourceElement,
                                Element toolElement,
                                Map<String, Element> scopes,
                                LinkedHashMap<String, Set<String>> mentionedTools) {
    if (sourceElement != null) {
      Element wrapElement = wrapElement(sourceToolName, sourceElement, toolElement);
      for (Element element : sourceElement.getChildren()) {
        if ("scope".equals(element.getName())) {
          String scopeName = element.getAttributeValue("name");
          if (scopeName != null) {
            mentionedTools.computeIfAbsent(scopeName, s -> new HashSet<>()).add(sourceToolName);
            copyScopeContent(sourceToolName, element, scopes.computeIfAbsent(scopeName, key -> {
              Element scopeElement = element.clone();
              scopeElement.removeContent();
              return scopeElement;
            }));
          }
        }
        else {
          wrapElement.addContent(element.clone());
        }
      }
    }
  }

  private void copyScopeContent(String sourceToolName, Element element, Element scopeElement) {
    Element wrappedScope = wrapElement(sourceToolName, element, scopeElement);
    for (Element scopeEl : element.getChildren()) {
      wrappedScope.addContent(scopeEl.clone());
    }
  }

  protected Element wrapElement(String sourceToolName, Element sourceElement, Element toolElement) {
    return toolElement;
  }
}
