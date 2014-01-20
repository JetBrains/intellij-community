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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class SeverityUtil {
  @NotNull
  public static Collection<SeverityRegistrar.SeverityBasedTextAttributes> getRegisteredHighlightingInfoTypes(@NotNull SeverityRegistrar registrar) {
    Collection<SeverityRegistrar.SeverityBasedTextAttributes> collection = registrar.allRegisteredAttributes();
    for (HighlightInfoType type : registrar.standardSeverities()) {
      collection.add(getSeverityBasedTextAttributes(registrar, type));
    }
    return collection;
  }

  private static SeverityRegistrar.SeverityBasedTextAttributes getSeverityBasedTextAttributes(@NotNull SeverityRegistrar registrar, @NotNull HighlightInfoType type) {
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes textAttributes = scheme.getAttributes(type.getAttributesKey());
    if (textAttributes != null) {
      return new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes, (HighlightInfoType.HighlightInfoTypeImpl)type);
    }
    TextAttributes severity = registrar.getTextAttributesBySeverity(type.getSeverity(null));
    return new SeverityRegistrar.SeverityBasedTextAttributes(severity, (HighlightInfoType.HighlightInfoTypeImpl)type);
  }
}
