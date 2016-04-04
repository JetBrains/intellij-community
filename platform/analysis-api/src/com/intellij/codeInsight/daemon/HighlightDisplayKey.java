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
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HighlightDisplayKey {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.HighlightDisplayKey");

  private static final Map<String,HighlightDisplayKey> ourNameToKeyMap = new THashMap<String, HighlightDisplayKey>();
  private static final Map<String,HighlightDisplayKey> ourIdToKeyMap = new THashMap<String, HighlightDisplayKey>();
  private static final Map<HighlightDisplayKey, Computable<String>> ourKeyToDisplayNameMap = new THashMap<HighlightDisplayKey, Computable<String>>();
  private static final Map<HighlightDisplayKey, String> ourKeyToAlternativeIDMap = new THashMap<HighlightDisplayKey, String>();

  private final String myName;
  private final String myID;

  public static HighlightDisplayKey find(@NonNls @NotNull final String name) {
    return ourNameToKeyMap.get(name);
  }

  @Nullable
  public static HighlightDisplayKey findById(@NonNls @NotNull final String id) {
    HighlightDisplayKey key = ourIdToKeyMap.get(id);
    if (key != null) return key;
    key = ourNameToKeyMap.get(id);
    if (key != null && key.getID().equals(id)) return key;
    return null;
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name) {
    final HighlightDisplayKey key = find(name);
    if (key != null) {
      LOG.error("Key with name \'" + name + "\' already registered with display name: " + getDisplayNameByKey(key));
      return null;
    }
    return new HighlightDisplayKey(name);
  }

  /**
   * @see #register(String, com.intellij.openapi.util.Computable)
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
   * @see #register(String, com.intellij.openapi.util.Computable, String)
   */
  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name,
                                             @NotNull final String displayName,
                                             @NotNull @NonNls final String id) {
    return register(name, new Computable.PredefinedValueComputable<String>(displayName), id);
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls @NotNull final String name,
                                             @NotNull final Computable<String> displayName,
                                             @NotNull @NonNls final String id) {
    final HighlightDisplayKey key = find(name);
    if (key != null) {
      LOG.error("Key with name \'" + name + "\' already registered with display name: " + getDisplayNameByKey(key));
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
