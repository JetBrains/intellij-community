// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HighlightDisplayKey {
  private static final Logger LOG = Logger.getInstance(HighlightDisplayKey.class);

  private static final Map<String,HighlightDisplayKey> ourNameToKeyMap = new ConcurrentHashMap<>();
  private static final Map<String,HighlightDisplayKey> ourIdToKeyMap = new ConcurrentHashMap<>();
  private static final Map<HighlightDisplayKey, Computable<String>> ourKeyToDisplayNameMap = new ConcurrentHashMap<>();
  private static final Map<HighlightDisplayKey, String> ourKeyToAlternativeIDMap = new ConcurrentHashMap<>();

  private final String myName;
  private final String myID;

  public static HighlightDisplayKey find(@NonNls @NotNull final String name) {
    return ourNameToKeyMap.get(name);
  }

  @Nullable
  public static HighlightDisplayKey findById(@NonNls @NotNull final String id) {
    HighlightDisplayKey key = ourIdToKeyMap.get(id);
    if (key != null) return key;
    key = find(id);
    if (key != null && key.getID().equals(id)) return key;
    return null;
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name) {
    final HighlightDisplayKey key = find(name);
    if (key != null) {
      LOG.error("Key with name '" + name + "' already registered with display name: " + getDisplayNameByKey(key));
      return null;
    }
    return new HighlightDisplayKey(name);
  }

  /**
   * @see #register(String, Computable)
   */
  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name, @NotNull final String displayName) {
    return register(name, displayName, name);
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name, @NotNull Computable<String> displayName) {
    return register(name, displayName, name);
  }


  /**
   * @see #register(String, Computable, String)
   */
  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name,
                                             @NotNull final String displayName,
                                             @NotNull @NonNls final String id) {
    return register(name, new Computable.PredefinedValueComputable<>(displayName), id);
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name,
                                             @NotNull final Computable<String> displayName,
                                             @NotNull @NonNls final String id) {
    final HighlightDisplayKey key = find(name);
    if (key != null) {
      LOG.error("Key with name '" + name + "' already registered with display name: " + getDisplayNameByKey(key));
      return null;
    }
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name, id);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name,
                                             @NotNull final Computable<String> displayName,
                                             @NonNls @NotNull final String id,
                                             @NonNls @Nullable final String alternativeID) {
    final HighlightDisplayKey key = register(name, displayName, id);
    if (alternativeID != null) {
      ourKeyToAlternativeIDMap.put(key, alternativeID);
    }
    return key;
  }

  public static void unregister(@NotNull String shortName) {
    HighlightDisplayKey key = ourNameToKeyMap.remove(shortName);
    if (key != null) {
      ourIdToKeyMap.remove(key.myID);
      ourKeyToAlternativeIDMap.remove(key);
      ourKeyToDisplayNameMap.remove(key);
    }
  }

  @NotNull
  public static HighlightDisplayKey findOrRegister(@NonNls @NotNull String name, @NotNull final String displayName) {
    return findOrRegister(name, displayName, null);
  }

  @NotNull
  public static HighlightDisplayKey findOrRegister(@NonNls @NotNull final String name,
                                                   @NotNull final String displayName,
                                                   @NonNls @Nullable final String id) {
    HighlightDisplayKey key = find(name);
    if (key == null) {
      key = register(name, displayName, id != null ? id : name);
      assert key != null : name;
    }
    return key;
  }

  @Nullable
  public static String getDisplayNameByKey(@Nullable HighlightDisplayKey key) {
    if (key == null) {
      return null;
    }
    else {
      final Computable<String> computable = ourKeyToDisplayNameMap.get(key);
      return computable == null ? null : computable.compute();
    }
  }

  public static String getAlternativeID(@NotNull HighlightDisplayKey key) {
    return ourKeyToAlternativeIDMap.get(key);
  }


  private HighlightDisplayKey(@NonNls @NotNull final String name) {
    this(name, name);
  }

  public HighlightDisplayKey(@NonNls @NotNull final String name, @NonNls @NotNull final String ID) {
    myName = name;
    myID = ID;
    ourNameToKeyMap.put(myName, this);
    if (!Comparing.equal(ID, name)) {
      ourIdToKeyMap.put(ID, this);
    }
  }

  public String toString() {
    return myName;
  }

  @NotNull
  public String getID(){
    return myID;
  }
}
