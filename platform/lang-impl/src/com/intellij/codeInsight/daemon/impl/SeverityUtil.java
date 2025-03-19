// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public final class SeverityUtil {
  public static @NotNull Collection<SeverityRegistrar.SeverityBasedTextAttributes> getRegisteredHighlightingInfoTypes(@NotNull SeverityRegistrar registrar) {
    List<SeverityRegistrar.SeverityBasedTextAttributes> collection = new ArrayList<>(registrar.allRegisteredAttributes());
    for (HighlightInfoType type : SeverityRegistrar.standardSeverities()) {
      if (HighlightInfoType.INFORMATION.equals(type) || HighlightInfoType.INFO.equals(type)) continue;
      collection.add(getSeverityBasedTextAttributes(registrar, type));
    }
    collection.sort(Comparator.comparing(SeverityRegistrar.SeverityBasedTextAttributes::getSeverity, registrar.reversed()));
    return collection;
  }

  private static @NotNull SeverityRegistrar.SeverityBasedTextAttributes getSeverityBasedTextAttributes(@NotNull SeverityRegistrar registrar, @NotNull HighlightInfoType type) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes textAttributes = scheme.getAttributes(type.getAttributesKey());
    if (textAttributes != null) {
      return new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes, (HighlightInfoType.HighlightInfoTypeImpl)type);
    }
    TextAttributes severity = registrar.getTextAttributesBySeverity(type.getSeverity(null));
    return new SeverityRegistrar.SeverityBasedTextAttributes(severity, (HighlightInfoType.HighlightInfoTypeImpl)type);
  }
}
