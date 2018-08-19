// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.VolatileNullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * A type of item with a distinct highlighting in an editor or in other views.
 */
public final class TextAttributesKey implements Comparable<TextAttributesKey> {
  private static final String TEMP_PREFIX = "TEMP::";
  private static final Logger LOG = Logger.getInstance(TextAttributesKey.class);
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();

  private static final ConcurrentMap<String, TextAttributesKey> ourRegistry = new ConcurrentHashMap<>();

  private static final NullableLazyValue<TextAttributeKeyDefaultsProvider> ourDefaultsProvider =
    new VolatileNullableLazyValue<TextAttributeKeyDefaultsProvider>() {
      @Nullable
      @Override
      protected TextAttributeKeyDefaultsProvider compute() {
        return ServiceManager.getService(TextAttributeKeyDefaultsProvider.class);
      }
    };

  private final String myExternalName;
  private final TextAttributes myDefaultAttributes;
  private final TextAttributesKey myFallbackAttributeKey;

  private TextAttributesKey(@NotNull String externalName, TextAttributes defaultAttributes, TextAttributesKey fallbackAttributeKey) {
    myExternalName = externalName;

    myDefaultAttributes = defaultAttributes;
    myFallbackAttributeKey = fallbackAttributeKey;

    if (fallbackAttributeKey != null) {
      JBIterable<TextAttributesKey> it = JBIterable.generate(myFallbackAttributeKey, o -> o == this ? null : o.myFallbackAttributeKey);
      if (equals(it.find(o -> equals(o)))) {
        throw new IllegalArgumentException("Can't use this fallback key: "+fallbackAttributeKey+": Cycle detected: " + StringUtil.join(it, "->"));
      }
    }
  }

  //read external only
  public TextAttributesKey(@NotNull Element element) {
    String name = JDOMExternalizerUtil.readField(element, "myExternalName");

    Element myDefaultAttributesElement = JDOMExternalizerUtil.readOption(element, "myDefaultAttributes");
    TextAttributes defaultAttributes = myDefaultAttributesElement == null ? null : new TextAttributes(myDefaultAttributesElement);
    myExternalName = name;
    myDefaultAttributes = defaultAttributes;
    myFallbackAttributeKey = null;
  }

  @NotNull
  public static TextAttributesKey find(@NotNull @NonNls String externalName) {
    return ourRegistry.computeIfAbsent(externalName, name -> new TextAttributesKey(name, null, null));
  }

  @Override
  public String toString() {
    return myExternalName;
  }

  @NotNull
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
   * @param externalName the unique identifier of the key.
   * @return the new key instance, or an existing instance if the key with the same
   * identifier was already registered.
   */
  @NotNull
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName) {
    return find(externalName);
  }

  public void writeExternal(Element element) {
    JDOMExternalizerUtil.writeField(element, "myExternalName", myExternalName);

    if (myDefaultAttributes != null) {
      Element option = JDOMExternalizerUtil.writeOption(element, "myDefaultAttributes");
      myDefaultAttributes.writeExternal(option);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TextAttributesKey that = (TextAttributesKey)o;

    if (!myExternalName.equals(that.myExternalName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myExternalName.hashCode();
  }

  // can't use RecursionManager unfortunately because quite a few crazy tests would start screaming about prevented recursive access
  private static final ThreadLocal<Set<String>> CALLED_RECURSIVELY = ThreadLocal.withInitial(()->new THashSet<>());
  /**
   * Returns the default text attributes associated with the key.
   *
   * @return the text attributes.
   */
  public TextAttributes getDefaultAttributes() {
    if (myDefaultAttributes == null) {
      final TextAttributeKeyDefaultsProvider provider = ourDefaultsProvider.getValue();
      if (provider != null) {
        Set<String> called = CALLED_RECURSIVELY.get();
        if (!called.add(myExternalName)) return null;
        try {
          return ObjectUtils.notNull(provider.getDefaultAttributes(this), NULL_ATTRIBUTES);
        }
        finally {
          called.remove(myExternalName);
        }
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
   * identifier was already registered.
   * @deprecated Use {@link #createTextAttributesKey(String, TextAttributesKey)} to guarantee compatibility with generic color schemes.
   */
  @NotNull
  @Deprecated
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName, TextAttributes defaultAttributes) {
    TextAttributesKey result = ourRegistry.get(externalName);
    TextAttributesKey fallbackAttributeKey;
    if (result == null) {
      fallbackAttributeKey = null;
    }
    else {
      if (Comparing.equal(result.getDefaultAttributes(), defaultAttributes)) {
        return result;
      }
      // ouch. Someone's re-creating already existing key with different attributes.
      // Have to remove the old one from the map, create the new one with correct attributes, re-insert to the map
      fallbackAttributeKey = result.getFallbackAttributeKey();
      ourRegistry.remove(externalName, result);
    }
    TextAttributesKey newKey = new TextAttributesKey(externalName, defaultAttributes, fallbackAttributeKey);
    return ConcurrencyUtil.cacheOrGet(ourRegistry, externalName, newKey);
  }

  /**
   * Registers a temp text attribute key with the specified identifier and default attributes.
   * The attribute of the temp attribute key is not serialized and not copied while TextAttributesScheme
   * manipulations.
   *
   * @param externalName      the unique identifier of the key.
   * @param defaultAttributes the default text attributes associated with the key.
   * @return the new key instance, or an existing instance if the key with the same
   * identifier was already registered.
   */
  @NotNull
  public static TextAttributesKey createTempTextAttributesKey(@NonNls @NotNull String externalName, TextAttributes defaultAttributes) {
    return createTextAttributesKey(TEMP_PREFIX + externalName, defaultAttributes);
  }

  /**
   * Registers a text attribute key with the specified identifier and a fallback key. If text attributes for the key are not defined in
   * a color scheme, they will be acquired by the fallback key if possible.
   * <p>Fallback keys can be chained, for example, text attribute key
   * A can depend on key B which in turn can depend on key C. So if text attributes neither for A nor for B are found, they will be
   * acquired by the key C.
   * <p>Fallback keys can be used from any place including language's own definitions. Note that there is a common set of keys called
   * {@code DefaultLanguageHighlighterColors} which can be used as a base. Scheme designers are supposed to set colors for these
   * keys primarily and using them guarantees that most (if not all) text attributes will be shown correctly for the language
   * regardless of a color scheme.
   *
   * @param externalName         the unique identifier of the key.
   * @param fallbackAttributeKey the fallback key to use if text attributes for this key are not defined.
   * @return the new key instance, or an existing instance if the key with the same
   * identifier was already registered.
   */
  @NotNull
  public static TextAttributesKey createTextAttributesKey(@NonNls @NotNull String externalName, TextAttributesKey fallbackAttributeKey) {
    TextAttributesKey existing = ourRegistry.get(externalName);
    TextAttributes defaultAttributes;
    if (existing == null) {
      defaultAttributes = null;
    }
    else {
      if (Comparing.equal(existing.getFallbackAttributeKey(), fallbackAttributeKey)) {
        return existing;
      }
      // ouch. Someone's re-creating already existing key with different attributes.
      // Have to remove the old one from the map, create the new one with correct attributes, re-insert to the map
      if (existing.getFallbackAttributeKey() != null) {
        throw new IllegalStateException("TextAttributeKey(name:'" + externalName +"', fallbackAttributeKey:'"+fallbackAttributeKey+"') "+
                                        " was already registered with the other fallback attribute key: "+existing.getFallbackAttributeKey());
      }
      defaultAttributes = existing.getDefaultAttributes();
      ourRegistry.remove(externalName, existing);
    }
    TextAttributesKey newKey = new TextAttributesKey(externalName, defaultAttributes, fallbackAttributeKey);
    return ConcurrencyUtil.cacheOrGet(ourRegistry, externalName, newKey);
  }

  @Nullable
  public TextAttributesKey getFallbackAttributeKey() {
    return myFallbackAttributeKey;
  }

  /**
   * @deprecated Use {@link #createTextAttributesKey(String, TextAttributesKey)} instead
   */
  @Deprecated
  public void setFallbackAttributeKey(@Nullable TextAttributesKey fallbackAttributeKey) {
  }

  @TestOnly
  static void removeTextAttributesKey(@NonNls @NotNull String externalName) {
    ourRegistry.remove(externalName);
  }

  public static boolean isTemp(@NotNull TextAttributesKey key) {
    return key.getExternalName().startsWith(TEMP_PREFIX);
  }

  @FunctionalInterface
  public interface TextAttributeKeyDefaultsProvider {
    TextAttributes getDefaultAttributes(@NotNull TextAttributesKey key);
  }

  /**
   * @deprecated For internal use only.
   */
  @Deprecated static final
  TextAttributesKey DUMMY_DEPRECATED_ATTRIBUTES = createTextAttributesKey("__deprecated__");
}
