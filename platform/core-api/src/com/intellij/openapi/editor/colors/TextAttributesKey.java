/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.VolatileNullableLazyValue;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;


/**
 * A type of item with a distinct highlighting in an editor or in other views.
 */
public final class TextAttributesKey implements Comparable<TextAttributesKey> {
  private static final Logger LOG = Logger.getInstance("#" + TextAttributesKey.class.getName());
  
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();
  private static final ConcurrentMap<String, TextAttributesKey> ourRegistry = ContainerUtil.newConcurrentMap();
  private static final NullableLazyValue<TextAttributeKeyDefaultsProvider> ourDefaultsProvider = new VolatileNullableLazyValue<TextAttributeKeyDefaultsProvider>() {
    @Nullable
    @Override
    protected TextAttributeKeyDefaultsProvider compute() {
      return ServiceManager.getService(TextAttributeKeyDefaultsProvider.class);
    }
  };

  private final String myExternalName;
  private TextAttributes myDefaultAttributes = NULL_ATTRIBUTES;
  private TextAttributesKey myFallbackAttributeKey;

  private TextAttributesKey(String externalName) {
    myExternalName = externalName;
  }

  //read external only
  public TextAttributesKey(@NotNull Element element) {
    this(JDOMExternalizerUtil.readField(element, "myExternalName"));
    Element myDefaultAttributesElement = JDOMExternalizerUtil.getOption(element, "myDefaultAttributes");
    if (myDefaultAttributesElement != null) {
      myDefaultAttributes = new TextAttributes(myDefaultAttributesElement);
    }
  }

  @NotNull
  public static TextAttributesKey find(@NotNull @NonNls String externalName) {
    return ConcurrencyUtil.cacheOrGet(ourRegistry, externalName, new TextAttributesKey(externalName));
  }

  public String toString() {
    return myExternalName;
  }

  public String getExternalName() {
    return myExternalName;
  }

  @Override
  public int compareTo(@NotNull TextAttributesKey key) {
    return myExternalName.compareTo(key.myExternalName);
  }

  /**
   * Registers a text attribute key with the specified identifier.
   *
   * @param externalName      the unique identifier of the key.
   * @return the new key instance, or an existing instance if the key with the same
   *         identifier was already registered.
   */
  @NotNull public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName) {
    return find(externalName);
  }

  public void writeExternal(Element element) {
    JDOMExternalizerUtil.writeField(element, "myExternalName", myExternalName);

    if (myDefaultAttributes != NULL_ATTRIBUTES) {
      Element option = JDOMExternalizerUtil.writeOption(element, "myDefaultAttributes");
      myDefaultAttributes.writeExternal(option);
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TextAttributesKey that = (TextAttributesKey)o;

    if (!myExternalName.equals(that.myExternalName)) return false;

    return true;
  }

  public int hashCode() {
    return myExternalName.hashCode();
  }

  /**
   * Returns the default text attributes associated with the key.
   *
   * @return the text attributes.
   */
  public TextAttributes getDefaultAttributes() {
    if (myDefaultAttributes == NULL_ATTRIBUTES) {
      myDefaultAttributes = null;
      final TextAttributeKeyDefaultsProvider provider = ourDefaultsProvider.getValue();
      if (provider != null) {
        myDefaultAttributes = provider.getDefaultAttributes(this);
      }
    }
    else if (myDefaultAttributes == null) {
      myDefaultAttributes = NULL_ATTRIBUTES;
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
   * @deprecated Use {@link #createTextAttributesKey(String, TextAttributesKey)} to guarantee compatibility with generic color schemes.
   */
  @NotNull
  @Deprecated
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName, TextAttributes defaultAttributes) {
    TextAttributesKey key = find(externalName);
    if (key.myDefaultAttributes == null || key.myDefaultAttributes == NULL_ATTRIBUTES) {
      key.myDefaultAttributes = defaultAttributes;
    }
    return key;
  }


  /**
   * Registers a text attribute key with the specified identifier and a fallback key. If text attributes for the key are not defined in
   * a color scheme, they will be acquired by the fallback key if possible.
   * <p>Fallback keys can be chained, for example, text attribute key
   * A can depend on key B which in turn can depend on key C. So if text attributes neither for A nor for B are found, they will be
   * acquired by the key C.
   * <p>Fallback keys can be used from any place including language's own definitions. Note that there is a common set of keys called
   * <code>DefaultLanguageHighlighterColors</code> which can be used as a base. Scheme designers are supposed to set colors for these
   * keys primarily and using them guarantees that most (if not all) text attributes will be shown correctly for the language
   * regardless of a color scheme.
   *
   * @param externalName         the unique identifier of the key.
   * @param fallbackAttributeKey the fallback key to use if text attributes for this key are not defined.
   * @return the new key instance, or an existing instance if the key with the same
   *         identifier was already registered.
   */
  @NotNull
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName, TextAttributesKey fallbackAttributeKey) {
    TextAttributesKey key = find(externalName);
    key.setFallbackAttributeKey(fallbackAttributeKey);
    return key;
  }

  public TextAttributesKey getFallbackAttributeKey() {
    return myFallbackAttributeKey;
  }

  public void setFallbackAttributeKey(TextAttributesKey fallbackAttributeKey) {
    myFallbackAttributeKey = fallbackAttributeKey;
    if (fallbackAttributeKey != null) {
      checkDependencies(fallbackAttributeKey, new HashSet<TextAttributesKey>());
    }
  }
  
  @TestOnly
  public static void removeTextAttributesKey(@NonNls @NotNull String externalName) {
    if (ourRegistry.containsKey(externalName)) {
      ourRegistry.remove(externalName);
    }
  }

  public interface TextAttributeKeyDefaultsProvider {
    TextAttributes getDefaultAttributes(TextAttributesKey key);
  }

  private void checkDependencies(@Nullable TextAttributesKey key, Set<TextAttributesKey> referencedKeys) {
    if (key != null) {
      if (!referencedKeys.contains(key)) {
        referencedKeys.add(key);
        TextAttributesKey fallbackKey = key.getFallbackAttributeKey();
        if (fallbackKey != null) {
          checkDependencies(fallbackKey, referencedKeys);
        }
      }
      else {
        StringBuilder sb = new StringBuilder();
        sb.append("Cyclic TextAttributesKey dependency found: ");
        printDependencyLoop(sb, key);
        myFallbackAttributeKey = null;
        LOG.error(sb.toString());
      }
    }
  }

  private void printDependencyLoop(@NotNull StringBuilder stringBuilder,
                                   @NotNull TextAttributesKey currNode) {
    stringBuilder.append(currNode.getExternalName()).append("->");
    TextAttributesKey fallbackKey = currNode.getFallbackAttributeKey();
    if (fallbackKey == this) {
      stringBuilder.append(getExternalName());
      return;
    }
    printDependencyLoop(stringBuilder, fallbackKey);
  }
}
