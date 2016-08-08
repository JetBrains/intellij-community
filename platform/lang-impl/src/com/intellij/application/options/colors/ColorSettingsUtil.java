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

package com.intellij.application.options.colors;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author lesya
 */
public class ColorSettingsUtil {
  private ColorSettingsUtil() {
  }

  public static Map<TextAttributesKey, String> keyToDisplayTextMap(final ColorSettingsPage page) {
    final List<AttributesDescriptor> attributeDescriptors = getAllAttributeDescriptors(page);
    final Map<TextAttributesKey, String> displayText = new HashMap<>();
    for (AttributesDescriptor attributeDescriptor : attributeDescriptors) {
      final TextAttributesKey key = attributeDescriptor.getKey();
      displayText.put(key, attributeDescriptor.getDisplayName());
    }
    return displayText;
  }

  public static List<AttributesDescriptor> getAllAttributeDescriptors(ColorAndFontDescriptorsProvider provider) {
    List<AttributesDescriptor> result = new ArrayList<>();
    Collections.addAll(result, provider.getAttributeDescriptors());
    if (isInspectionColorsPage(provider)) {
      addInspectionSeverityAttributes(result);
    }
    return result;
  }

  private static boolean isInspectionColorsPage(ColorAndFontDescriptorsProvider provider) {
    // the first registered page implementing InspectionColorSettingsPage
    // gets the inspection attribute descriptors added to its list
    if (!(provider instanceof InspectionColorSettingsPage)) return false;
    for(ColorSettingsPage settingsPage: Extensions.getExtensions(ColorSettingsPage.EP_NAME)) {
      if (settingsPage == provider) break;
      if (settingsPage instanceof InspectionColorSettingsPage) return false;
    }
    return true;        
  }

  static boolean isSharedScheme(EditorColorsScheme selected) {
      return false;
  }

  private static void addInspectionSeverityAttributes(List<AttributesDescriptor> descriptors) {
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.unknown.symbol"), CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES));
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.deprecated.symbol"), CodeInsightColors.DEPRECATED_ATTRIBUTES));
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.unused.symbol"), CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES));
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.error"), CodeInsightColors.ERRORS_ATTRIBUTES));
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.warning"), CodeInsightColors.WARNINGS_ATTRIBUTES));
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.weak.warning"), CodeInsightColors.WEAK_WARNING_ATTRIBUTES));
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.server.problems"), CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING));
    descriptors.add(new AttributesDescriptor(OptionsBundle.message("options.java.attribute.descriptor.server.duplicate"), CodeInsightColors.DUPLICATE_FROM_SERVER));

    for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
      for (HighlightInfoType highlightInfoType : provider.getSeveritiesHighlightInfoTypes()) {
        final TextAttributesKey attributesKey = highlightInfoType.getAttributesKey();
        descriptors.add(new AttributesDescriptor(toDisplayName(attributesKey), attributesKey));
      }
    }
  }

  @NotNull
  private static String toDisplayName(@NotNull TextAttributesKey attributesKey) {
    //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
    return OptionsBundle.message(
      "options.java.attribute.descriptor.errors.group",
      StringUtil.capitalize(attributesKey.getExternalName().toLowerCase().replaceAll("_", " ")));
  }
}
