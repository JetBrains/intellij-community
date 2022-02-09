// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class InspectionElementsMergerBase extends InspectionElementsMerger {

  /**
   * serialize old inspection settings as they could appear in old profiles
   */
  protected Element writeOldSettings(@NotNull String sourceToolName) throws WriteExternalException {
    final Element sourceElement = new Element(InspectionProfileImpl.INSPECTION_TOOL_TAG);
    sourceElement.setAttribute(InspectionProfileImpl.CLASS_TAG, sourceToolName);
    sourceElement.setAttribute(ToolsImpl.ENABLED_ATTRIBUTE, String.valueOf(isEnabledByDefault(sourceToolName)));
    sourceElement.setAttribute(ToolsImpl.LEVEL_ATTRIBUTE, getDefaultSeverityLevel(sourceToolName));
    sourceElement.setAttribute(ToolsImpl.ENABLED_BY_DEFAULT_ATTRIBUTE, String.valueOf(isEnabledByDefault(sourceToolName)));
    return sourceElement;
  }

  private static String getDefaultSeverityLevel(@NotNull String sourceToolName) {
    return HighlightSeverity.WARNING.getName();
  }

  protected boolean isEnabledByDefault(@NotNull String sourceToolName) {
    return true;
  }

  /**
   * marker node to prevent multiple merging. Is needed when inspection's settings are equal to default and are skipped in profile
   */
  @NotNull
  static String getMergedMarkerName(@NotNull String toolName) {
    return toolName + "Merged";
  }

  boolean markSettingsMerged(@NotNull Map<String, Element> inspectionsSettings) {
    final Element merge = merge(inspectionsSettings, true);
    if (merge != null) {
      final Element defaultElement = merge(Collections.emptyMap(), true);
      return !JDOMUtil.areElementsEqual(merge, defaultElement);
    }
    return false;
  }

  protected boolean areSettingsMerged(@NotNull Map<String, Element> inspectionsSettings, @NotNull Element inspectionElement) {
    final Element merge = merge(inspectionsSettings, true);
    return merge != null && JDOMUtil.areElementsEqual(merge, inspectionElement);
  }

  protected Element merge(@NotNull Map<String, Element> inspectionElements) {
    return merge(inspectionElements, false);
  }

  protected Element merge(@NotNull Map<String, Element> inspectionElements, boolean includeDefaults) {
    LinkedHashMap<String, Element> scopes = new LinkedHashMap<>();
    LinkedHashMap<String, Set<String>> mentionedTools = new LinkedHashMap<>();
    boolean enabled = false;
    boolean enabledByDefault = false;
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
          enabledByDefault |= isEnabledByDefault(sourceToolName);
          enabled |= enabledByDefault;
          if (level == null) {
            level = getDefaultSeverityLevel(sourceToolName);
          }
        }
      }

      if (sourceElement != null) {
        collectContent(sourceToolName, sourceElement, toolElement, scopes, mentionedTools);

        enabled |= Boolean.parseBoolean(sourceElement.getAttributeValue(ToolsImpl.ENABLED_ATTRIBUTE));
        enabledByDefault |= Boolean.parseBoolean(sourceElement.getAttributeValue(ToolsImpl.ENABLED_BY_DEFAULT_ATTRIBUTE));
        if (level == null) {
          level = getLevel(sourceElement);
        }
      }
    }
    if (writeMergedContent(toolElement)) {
      toolElement.setAttribute(InspectionProfileImpl.CLASS_TAG, getMergedToolName());
      toolElement.setAttribute(ToolsImpl.ENABLED_ATTRIBUTE, String.valueOf(enabled));
      if (level != null) {
        toolElement.setAttribute(ToolsImpl.LEVEL_ATTRIBUTE, level);
      }
      toolElement.setAttribute(ToolsImpl.ENABLED_BY_DEFAULT_ATTRIBUTE, String.valueOf(enabledByDefault));

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

  protected boolean writeMergedContent(@NotNull Element toolElement) {
    return !toolElement.getChildren().isEmpty();
  }

  protected Element getSourceElement(@NotNull Map<String, Element> inspectionElements, @NotNull String sourceToolName) {
    return inspectionElements.get(sourceToolName);
  }

  private void copyDefaultSettings(@NotNull Element targetElement, @NotNull Map<String, Element> inspectionElements, @NotNull String sourceToolName) {
    Element oldElement = getSourceElement(inspectionElements, sourceToolName);
    if (oldElement != null) {
      Element defaultElement = transformElement(sourceToolName, oldElement, targetElement);
      oldElement.getChildren().stream()
        .filter(child -> !"scope".equals(child.getName()))
        .forEach(child -> defaultElement.addContent(child.clone()));
    }
  }

  private static String getLevel(Element element) {
    return element != null ? element.getAttributeValue(ToolsImpl.LEVEL_ATTRIBUTE) : HighlightSeverity.WARNING.getName();
  }

  private void collectContent(@NotNull String sourceToolName,
                              @NotNull Element sourceElement,
                              @NotNull Element toolElement,
                              @NotNull Map<String, Element> scopes,
                              @NotNull Map<String, Set<String>> mentionedTools) {
    Element wrapElement = transformElement(sourceToolName, sourceElement, toolElement);
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

  private void copyScopeContent(@NotNull String sourceToolName, @NotNull Element element, @NotNull Element scopeElement) {
    Element wrappedScope = transformElement(sourceToolName, element, scopeElement);
    for (Element scopeEl : element.getChildren()) {
      wrappedScope.addContent(scopeEl.clone());
    }
  }

  protected Element transformElement(@NotNull String sourceToolName, @NotNull Element sourceElement, @NotNull Element toolElement) {
    return toolElement;
  }
}
