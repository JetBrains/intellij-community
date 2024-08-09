// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

/**
 * HighlightDisplayKey is a unique identifier of an inspection.
 */
public final class HighlightDisplayKey {
  private static final Logger LOG = Logger.getInstance(HighlightDisplayKey.class);

  private static final Map<String, HighlightDisplayKey> ourShortNameToKeyMap = new ConcurrentHashMap<>();
  private static final Map<String, HighlightDisplayKey> ourIdToKeyMap = new ConcurrentHashMap<>();
  private static final Map<HighlightDisplayKey, Computable<@Nls(capitalization = Sentence) String>> ourKeyToDisplayNameMap = new ConcurrentHashMap<>();
  private static final Map<HighlightDisplayKey, String> ourKeyToAlternativeIDMap = new ConcurrentHashMap<>();

  private final String myShortName;
  private final String myID;

  /**
   * @return a HighlightDisplayKey for a given inspection by its {@link LocalInspectionTool#getShortName short name}
   * or {@code null} if the inspection is not registered.
   */
  public static @Nullable HighlightDisplayKey find(@NonNls @NotNull String shortName) {
    return ourShortNameToKeyMap.get(shortName);
  }

  /**
   * @return a HighlightDisplayKey for a given inspection by its {@link LocalInspectionTool#getID() id}
   * or {@code null} if the inspection is not registered.
   */
  public static @Nullable HighlightDisplayKey findById(@NonNls @NotNull String id) {
    HighlightDisplayKey key = ourIdToKeyMap.get(id);
    if (key != null) return key;
    key = find(id);
    if (key != null && key.getID().equals(id)) return key;
    return null;
  }

  /**
   * @deprecated Use {@link #register(String, Computable, String)} instead.
   * Storing a display name in the key as a String does not work for i18n purposes.
   */
  @Deprecated
  public static @Nullable HighlightDisplayKey register(@NonNls @NotNull String shortName,
                                                       @NotNull String displayName,
                                                       @NotNull @NonNls String id) {
    return register(shortName, new Computable.PredefinedValueComputable<>(displayName), id);
  }

  public static @Nullable HighlightDisplayKey register(@NonNls @NotNull String shortName,
                                                       @NotNull Computable<@Nls(capitalization = Sentence) String> displayName,
                                                       @NotNull @NonNls String id) {
    HighlightDisplayKey key = find(shortName);
    if (key != null) {
      LOG.error("Key with shortName '" + shortName + "' already registered with display name: " + getDisplayNameByKey(key));
      return null;
    }
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(shortName, id);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  public static @Nullable HighlightDisplayKey register(@NonNls @NotNull String shortName,
                                                       @NotNull Computable<@Nls(capitalization = Sentence) String> displayName,
                                                       @NonNls @NotNull String id,
                                                       @NonNls @Nullable String alternativeID) {
    HighlightDisplayKey key = register(shortName, displayName, id);
    if (alternativeID != null) {
      ourKeyToAlternativeIDMap.put(key, alternativeID);
    }
    return key;
  }

  public static void unregister(@NotNull String shortName) {
    HighlightDisplayKey key = ourShortNameToKeyMap.remove(shortName);
    if (key != null) {
      ourIdToKeyMap.remove(key.myID);
      ourKeyToAlternativeIDMap.remove(key);
      ourKeyToDisplayNameMap.remove(key);
    }
  }

  /**
   * @param shortName {@link LocalInspectionTool#getShortName()}
   * @param displayName {@link  LocalInspectionTool#getDisplayName()}
   *
   * @return an already existing or a new key corresponding to the provided details
   */
  public static @NotNull HighlightDisplayKey findOrRegister(@NonNls @NotNull String shortName,
                                                            @Nls(capitalization = Sentence) @NotNull String displayName) {
    return findOrRegister(shortName, displayName, null);
  }

  /**
   * @param shortName {@link LocalInspectionTool#getShortName()}
   * @param displayName {@link  LocalInspectionTool#getDisplayName()}
   * @param id {@link LocalInspectionTool#getID()} or {@code null} if it is equal to shortName.
   *
   * @return an already existing or a new key corresponding to the provided details
   */
  public static @NotNull HighlightDisplayKey findOrRegister(@NonNls @NotNull String shortName,
                                                            @Nls(capitalization = Sentence) @NotNull String displayName,
                                                            @NonNls @Nullable String id) {
    HighlightDisplayKey key = find(shortName);
    if (key == null) {
      id = id != null ? id : shortName;
      key = new HighlightDisplayKey(shortName, id);
      ourKeyToDisplayNameMap.put(key, new Computable.PredefinedValueComputable<>(displayName));
    }
    return key;
  }

  @Nls(capitalization = Sentence)
  @Nullable
  public static String getDisplayNameByKey(@Nullable HighlightDisplayKey key) {
    if (key == null) {
      return null;
    }
    else {
      Computable<@Nls(capitalization = Sentence) String> computable = ourKeyToDisplayNameMap.get(key);
      return computable == null ? null : computable.compute();
    }
  }

  /**
   * Alternative inspection tool ID is a descriptive name to be used in "suppress" comments and annotations
   * in modules with alternative classpath storage.
   *
   * @return alternative inspection tool ID.
   */
  public static String getAlternativeID(@NotNull HighlightDisplayKey key) {
    return ourKeyToAlternativeIDMap.get(key);
  }

  public HighlightDisplayKey(@NonNls @NotNull String shortName, @NonNls @NotNull String ID) {
    myShortName = shortName;
    myID = ID;
    ourShortNameToKeyMap.put(myShortName, this);
    if (!Objects.equals(ID, shortName)) {
      ourIdToKeyMap.put(ID, this);
    }
  }

  public String toString() {
    return myShortName;
  }

  /**
   * @see LocalInspectionTool#getID()
   *
   * @return inspection tool ID.
   */
  public @NotNull String getID() {
    return myID;
  }
}
