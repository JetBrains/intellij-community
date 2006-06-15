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
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A type of item with a distinct highlighting in an editor or in other views.
 */

public final class TextAttributesKey implements Comparable<TextAttributesKey>, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.colors.TextAttributesKey");
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();

  public String myExternalName;
  public TextAttributes myDefaultAttributes = NULL_ATTRIBUTES;
  private static Map<String, TextAttributesKey> ourRegistry = new HashMap<String, TextAttributesKey>();

  private TextAttributesKey(String externalName) {
    myExternalName = externalName;
    register();
  }

  //read external only
  public TextAttributesKey() {
  }

  private void register() {
    if (ourRegistry.containsKey(myExternalName)) {
      LOG.error("Key " + myExternalName + " already registered.");
    }
    else {
      ourRegistry.put(myExternalName, this);
    }
  }

  @NotNull public static TextAttributesKey find(@NotNull @NonNls String externalName) {
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

  /**
   * Returns the default text attributes associated with the key.
   *
   * @return the text attributes.
   */

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

  /**
   * Registers a text attribute key with the specified identifier and default attributes.
   *
   * @param externalName      the unique identifier of the key.
   * @param defaultAttributes the default text attributes associated with the key.
   * @return the new key instance, or an existing instance if the key with the same
   *         identifier was already registered.
   */

  public static TextAttributesKey createTextAttributesKey(@NonNls String externalName,
                                                          TextAttributes defaultAttributes) {
    TextAttributesKey key = ourRegistry.get(externalName);
    if (key == null) {
      key = find(externalName);
    }
    if (key.getDefaultAttributes() == null) {
      key.myDefaultAttributes = defaultAttributes;
    }
    return key;
  }

  /**
   * Registers a text attribute key with the specified identifier.
   *
   * @param externalName      the unique identifier of the key.
   * @return the new key instance, or an existing instance if the key with the same
   *         identifier was already registered.
   */
  public static TextAttributesKey createTextAttributesKey(@NonNls String externalName) {
    return find(externalName);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TextAttributesKey that = (TextAttributesKey)o;

    if (!myExternalName.equals(that.myExternalName)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myExternalName.hashCode();
    return result;
  }
}
