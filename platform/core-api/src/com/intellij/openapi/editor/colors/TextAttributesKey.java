// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.openapi.util.NullableLazyValue.volatileLazyNullable;


/**
 * A type of item with a distinct highlighting in an editor or in other views.
 * Use one of {@link #createTextAttributesKey(String)} {@link #createTextAttributesKey(String, TextAttributesKey)}
 * to create a new key, fallbacks will help to find colors in all colors schemes.
 * Specifying different attributes for different color schemes is possible using additionalTextAttributes extension point.
 */
public final class TextAttributesKey implements Comparable<TextAttributesKey> {
  public static final TextAttributesKey[] EMPTY_ARRAY = new TextAttributesKey[0];
  private static final Logger LOG = Logger.getInstance(TextAttributesKey.class);
  private static final String TEMP_PREFIX = "TEMP::";
  private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();

  private static final ConcurrentMap<String, TextAttributesKey> ourRegistry = new ConcurrentHashMap<>();

  private static final NullableLazyValue<TextAttributeKeyDefaultsProvider> ourDefaultsProvider =
    volatileLazyNullable(() -> ApplicationManager.getApplication().getService(TextAttributeKeyDefaultsProvider.class));

  private final @NotNull String myExternalName;
  private final TextAttributes myDefaultAttributes;
  private final TextAttributesKey myFallbackAttributeKey;

  private TextAttributesKey(@NotNull String externalName, TextAttributes defaultAttributes, TextAttributesKey fallbackAttributeKey) {
    myExternalName = externalName;

    myDefaultAttributes = defaultAttributes;
    myFallbackAttributeKey = fallbackAttributeKey;

    if (fallbackAttributeKey != null) {
      checkForCycle(fallbackAttributeKey);
    }
  }

  private void checkForCycle(@NotNull TextAttributesKey fallbackAttributeKey) {
    for (TextAttributesKey key = fallbackAttributeKey; key != null; key = key.myFallbackAttributeKey) {
      if (equals(key)) {
        throw new IllegalArgumentException("Can't use this fallback key: " + fallbackAttributeKey + ":" +
          " Cycle detected: " + StringUtil.join(JBIterable.generate(myFallbackAttributeKey, o -> o == this ? null : o.myFallbackAttributeKey), "->"));
      }
    }
  }

  //read external only
  public TextAttributesKey(@NotNull Element element) {
    String name = JDOMExternalizerUtil.readField(element, "myExternalName");

    Element myDefaultAttributesElement = JDOMExternalizerUtil.readOption(element, "myDefaultAttributes");
    TextAttributes defaultAttributes = myDefaultAttributesElement == null ? null : new TextAttributes(myDefaultAttributesElement);
    myExternalName = Objects.requireNonNull(name);
    myDefaultAttributes = defaultAttributes;
    myFallbackAttributeKey = null;
  }

  @NotNull
  public static TextAttributesKey find(@NotNull @NonNls String externalName) {
    return ourRegistry.computeIfAbsent(externalName, name -> new TextAttributesKey(name, null, null));
  }

  @Override
  @NlsSafe
  public String toString() {
    return myExternalName
           + (myFallbackAttributeKey == null && myDefaultAttributes == null ? "" : " (")
           + (myFallbackAttributeKey == null ? "" : "fallbackKey: " + myFallbackAttributeKey)
           + (myDefaultAttributes == null ? "" : "; defaultAttributes: " + myDefaultAttributes)
           + (myFallbackAttributeKey == null && myDefaultAttributes == null ? "" : ")")
      ;
  }

  @NotNull
  @NlsSafe
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

    return myExternalName.equals(that.myExternalName);
  }

  @Override
  public int hashCode() {
    return myExternalName.hashCode();
  }

  // can't use RecursionManager unfortunately because quite a few crazy tests would start screaming about prevented recursive access
  private static final ThreadLocal<Set<String>> CALLED_RECURSIVELY = ThreadLocal.withInitial(() -> new HashSet<>());

  /**
   * Returns the default text attributes associated with the key.
   *
   * @return the text attributes.
   */
  public TextAttributes getDefaultAttributes() {
    TextAttributes defaultAttributes = myDefaultAttributes;
    if (defaultAttributes == null) {
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
    return defaultAttributes;
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
    return getOrCreate(externalName, defaultAttributes, null);
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
    return getOrCreate(externalName, null, fallbackAttributeKey);
  }

  @NotNull
  private static TextAttributesKey getOrCreate(@NotNull @NonNls String externalName,
                                               TextAttributes defaultAttributes,
                                               TextAttributesKey fallbackAttributeKey) {
    TextAttributesKey existing = ourRegistry.get(externalName);
    if (existing != null
        && (defaultAttributes == null || Comparing.equal(existing.myDefaultAttributes, defaultAttributes))
        && (fallbackAttributeKey == null || Comparing.equal(existing.myFallbackAttributeKey, fallbackAttributeKey))) {
      return existing;
    }
    return ourRegistry.compute(externalName, (oldName, oldKey) -> mergeKeys(oldName, oldKey, defaultAttributes, fallbackAttributeKey));
  }

  @NotNull
  private static TextAttributesKey mergeKeys(@NonNls @NotNull String externalName,
                                             @Nullable TextAttributesKey oldKey,
                                             TextAttributes defaultAttributes,
                                             TextAttributesKey fallbackAttributeKey) {
    if (oldKey == null) return new TextAttributesKey(externalName, defaultAttributes, fallbackAttributeKey);
    // ouch. Someone's re-creating already existing key with different attributes.
    // Have to re-create the new one with correct attributes, re-insert to the map

    // but don't allow to rewrite not-null fallback key
    if (oldKey.myFallbackAttributeKey != null && !oldKey.myFallbackAttributeKey.equals(fallbackAttributeKey)) {
      LOG.error(new IllegalStateException("TextAttributeKey(name:'" + externalName + "', fallbackAttributeKey:'" + fallbackAttributeKey + "') " +
                                      " was already registered with the other fallback attribute key: " + oldKey.myFallbackAttributeKey));
    }

    // but don't allow to rewrite not-null default attributes
    if (oldKey.myDefaultAttributes != null && !oldKey.myDefaultAttributes.equals(defaultAttributes)) {
      LOG.error(new IllegalStateException("TextAttributeKey(name:'" + externalName + "', defaultAttributes:'" + defaultAttributes + "') " +
                                          " was already registered with the other defaultAttributes: " + oldKey.myDefaultAttributes));
    }

    TextAttributes newDefaults = ObjectUtils.chooseNotNull(defaultAttributes, oldKey.myDefaultAttributes); // care with not calling unwanted providers
    TextAttributesKey newFallback = ObjectUtils.chooseNotNull(fallbackAttributeKey, oldKey.myFallbackAttributeKey);
    return new TextAttributesKey(externalName, newDefaults, newFallback);
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

  @Nullable
  public TextAttributesKey getFallbackAttributeKey() {
    return myFallbackAttributeKey;
  }

  /**
   * @deprecated Use {@link #createTextAttributesKey(String, TextAttributesKey)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void setFallbackAttributeKey(@Nullable TextAttributesKey fallbackAttributeKey) {
  }

  @TestOnly
  static void removeTextAttributesKey(@NonNls @NotNull String externalName) {
    ourRegistry.remove(externalName);
  }

  public static boolean isTemp(@NotNull TextAttributesKey key) {
    return key.getExternalName().startsWith(TEMP_PREFIX);
  }

  @ApiStatus.Experimental
  @ApiStatus.Internal
  @NotNull
  public static List<TextAttributesKey> getAllKeys() {
    return new ArrayList<>(ourRegistry.values());
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
