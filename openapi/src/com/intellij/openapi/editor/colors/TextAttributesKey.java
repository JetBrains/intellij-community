/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public final class TextAttributesKey implements Comparable<TextAttributesKey> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.colors.TextAttributesKey");
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();

  private final String myExternalName;
  private TextAttributes myDefaultAttributes = NULL_ATTRIBUTES;
  private static Map<String, TextAttributesKey> ourRegistry = new HashMap<String, TextAttributesKey>();

  private TextAttributesKey(String externalName) {
    myExternalName = externalName;
    if (ourRegistry.containsKey(myExternalName)) {
      LOG.error("Key " + myExternalName + " already registered.");
    }
    else {
      ourRegistry.put(myExternalName, this);
    }
  }

  @NotNull public static TextAttributesKey find(String externalName) {
    TextAttributesKey key = ourRegistry.get(externalName);
    return key != null ? key : new TextAttributesKey(externalName);
  }

  public String toString() {
    return myExternalName;
  }

  public String getExternalName() {
    return myExternalName;
  }

  public int compareTo(TextAttributesKey key) {
    return myExternalName.compareTo(key.myExternalName);
  }

  public TextAttributes getDefaultAttributes() {
    if (myDefaultAttributes == NULL_ATTRIBUTES) {
      myDefaultAttributes = null;
      EditorColorsManager manager = EditorColorsManager.getInstance();

      if (manager != null) { // Can be null in test mode
        myDefaultAttributes = manager.getGlobalScheme().getAttributes(this);
      }
    }

    return myDefaultAttributes;
  }

  public static TextAttributesKey createTextAttributesKey(String externalName, TextAttributes defaultAttributes) {
    TextAttributesKey key = ourRegistry.get(externalName);
    if (key == null) {
      key = find(externalName);
    }
    if (key.getDefaultAttributes() == null) {
      key.myDefaultAttributes = defaultAttributes;
    }
    return key;
  }

  public static TextAttributesKey createTextAttributesKey(@NonNls String externalName) {
    return find(externalName);
  }
}