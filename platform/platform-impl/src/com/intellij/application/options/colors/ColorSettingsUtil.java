// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.application.options.colors;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * @author lesya
 */
public final class ColorSettingsUtil {
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

  public static List<AttributesDescriptor> getAllAttributeDescriptors(@NotNull ColorAndFontDescriptorsProvider provider) {
    List<AttributesDescriptor> result = new ArrayList<>();
    Collections.addAll(result, provider.getAttributeDescriptors());
    if (isInspectionColorsPage(provider)) {
      addInspectionSeverityAttributes(result);
    }
    return result;
  }

  public static List<ColorDescriptor> getAllColorDescriptors(@NotNull ColorAndFontDescriptorsProvider provider) {
    List<ColorDescriptor> result = new ArrayList<>();
    Collections.addAll(result, provider.getColorDescriptors());
    return result;
  }

  private static boolean isInspectionColorsPage(ColorAndFontDescriptorsProvider provider) {
    // the first registered page implementing InspectionColorSettingsPage
    // gets the inspection attribute descriptors added to its list
    if (!(provider instanceof InspectionColorSettingsPage)) return false;
    for(ColorSettingsPage settingsPage: ColorSettingsPage.EP_NAME.getExtensionList()) {
      if (settingsPage == provider) break;
      if (settingsPage instanceof InspectionColorSettingsPage) return false;
    }
    return true;
  }

  @NotNull
  @Unmodifiable
  public static List<@NotNull Pair<TextAttributesKey, @Nls String>> getErrorTextAttributes() {
    List<Pair<TextAttributesKey, @Nls String>> attributes = new ArrayList<>(
      List.of(
       new Pair<>(CodeInsightColors.ERRORS_ATTRIBUTES, OptionsBundle.message("options.java.attribute.descriptor.error")),
       new Pair<>(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES, OptionsBundle.message("options.java.attribute.descriptor.unknown.symbol")),
       new Pair<>(CodeInsightColors.WARNINGS_ATTRIBUTES, OptionsBundle.message("options.java.attribute.descriptor.warning")),
       new Pair<>(CodeInsightColors.WEAK_WARNING_ATTRIBUTES, OptionsBundle.message("options.java.attribute.descriptor.weak.warning")),
       new Pair<>(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES, OptionsBundle.message("options.java.attribute.descriptor.unused.symbol")),
       new Pair<>(CodeInsightColors.DEPRECATED_ATTRIBUTES,OptionsBundle.message("options.java.attribute.descriptor.deprecated.symbol") ),
       new Pair<>(CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES, OptionsBundle.message("options.java.attribute.descriptor.marked.for.removal.symbol")),
       new Pair<>(CodeInsightColors.DUPLICATE_FROM_SERVER, OptionsBundle.message("options.java.attribute.descriptor.server.duplicate")),
       new Pair<>(CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING, OptionsBundle.message("options.java.attribute.descriptor.server.problems")),
       new Pair<>(CodeInsightColors.RUNTIME_ERROR, OptionsBundle.message("options.java.attribute.descriptor.runtime"))
    ));

    attributes.add(new Pair<>(TextAttributesKey.find("REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES"), 
                              OptionsBundle.message("options.language.defaults.reassigned.local.variable")));

    for (SeveritiesProvider provider : SeveritiesProvider.EP_NAME.getExtensionList()) {
      for (HighlightInfoType highlightInfoType : provider.getSeveritiesHighlightInfoTypes()) {
        final TextAttributesKey attributesKey = highlightInfoType.getAttributesKey();
        attributes.add(new Pair<>(attributesKey, toDisplayName(attributesKey)));
      }
    }

    return List.copyOf(attributes);
  }

  private static void addInspectionSeverityAttributes(List<? super AttributesDescriptor> descriptors) {
    for (Pair<TextAttributesKey, @Nls String> pair : getErrorTextAttributes()) {
      descriptors.add(new AttributesDescriptor(pair.second, pair.first));
    }
  }

  private static @NotNull @NlsContexts.AttributeDescriptor String toDisplayName(@NotNull TextAttributesKey attributesKey) {
    return OptionsBundle.message(
      "options.java.attribute.descriptor.errors.group",
      StringUtil.capitalize(StringUtil.toLowerCase(attributesKey.getExternalName()).replaceAll("_", " ")));
  }
}
