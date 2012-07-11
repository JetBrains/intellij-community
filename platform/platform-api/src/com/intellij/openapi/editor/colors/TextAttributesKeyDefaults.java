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
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Store default TextAttributes by key
 */
public abstract class TextAttributesKeyDefaults {
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();
  private static final ConcurrentHashMap<TextAttributesKey, TextAttributes> ourMap = new ConcurrentHashMap<TextAttributesKey, TextAttributes>();

  /**
   * Returns the default text attributes associated with the key.
   *
   * @return the text attributes.
   * @param key
   */

  public static TextAttributes getDefaultAttributes(TextAttributesKey key) {
    if (!ourMap.containsKey(key)) {
      // E.g. if one text key reuse default attributes of some other predefined key
      ourMap.put(key, NULL_ATTRIBUTES);
      EditorColorsManager manager = EditorColorsManager.getInstance();

      if (manager != null) { // Can be null in test mode

        // It is reasonable to fetch attributes from Default color scheme. Otherwise if we launch IDE and then
        // try switch from custom colors scheme (e.g. with dark background) to default one. Editor will show
        // incorrect highlighting with "traces" of color scheme which was active during IDE startup.
        final EditorColorsScheme defaultColorScheme = manager.getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
        final TextAttributes textAttributes = defaultColorScheme.getAttributes(key);
        if (textAttributes != null)
          ourMap.put(key, textAttributes);
      }
    }

    return ourMap.get(key);
  }

  /**
   * Registers a text attribute key with the specified identifier and default attributes.
   *
   * @param externalName      the unique identifier of the key.
   * @param defaultAttributes the default text attributes associated with the key.
   * @return the new key instance, or an existing instance if the key with the same
   *         identifier was already registered.
   */
  @NotNull
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName, TextAttributes defaultAttributes) {
    TextAttributesKey key = TextAttributesKey.find(externalName);
    if (!ourMap.containsKey(key) || ourMap.get(key) == NULL_ATTRIBUTES) {
      ourMap.put(key, defaultAttributes == null ? NULL_ATTRIBUTES : defaultAttributes);
    }
    return key;
  }
}
