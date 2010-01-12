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

package com.intellij.application.options.colors;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.util.containers.HashMap;

import java.util.Map;

/**
 * @author lesya
 */
public class ColorSettingsUtil {
  private ColorSettingsUtil() {
  }

  public static Map<TextAttributesKey, String> keyToDisplayTextMap(final ColorSettingsPage page) {
    final AttributesDescriptor[] attributeDescriptors = page.getAttributeDescriptors();
    final Map<TextAttributesKey, String> displayText = new HashMap<TextAttributesKey, String>();
    for (AttributesDescriptor attributeDescriptor : attributeDescriptors) {
      final TextAttributesKey key = attributeDescriptor.getKey();
      displayText.put(key, attributeDescriptor.getDisplayName());
    }
    return displayText;
  }

  static boolean isSharedScheme(EditorColorsScheme selected) {
      return ((EditorColorsManagerImpl) EditorColorsManager.getInstance()).getSchemesManager().isShared(selected);
  }
}
