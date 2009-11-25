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
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HighlightDisplayKey {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.HighlightDisplayKey");

  private static final HashMap<String,HighlightDisplayKey> ourNameToKeyMap = new HashMap<String, HighlightDisplayKey>();
  private static final HashMap<String,HighlightDisplayKey> ourIdToKeyMap = new HashMap<String, HighlightDisplayKey>();
  private static final Map<HighlightDisplayKey, String> ourKeyToDisplayNameMap = new HashMap<HighlightDisplayKey, String>();
  private static final Map<HighlightDisplayKey, String> ourKeyToAlternativeIDMap = new HashMap<HighlightDisplayKey, String>();

  private final String myName;
  private final String myID;

  public static HighlightDisplayKey find(@NonNls String name){
    return ourNameToKeyMap.get(name);
  }

  public static HighlightDisplayKey findById(@NonNls String id){
    HighlightDisplayKey key = ourIdToKeyMap.get(id);
    if (key != null) return key;
    key = ourNameToKeyMap.get(id);
    if (key != null && key.getID().equals(id)) return key;
    return null;
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls String name) {
    if (find(name) != null) {
      LOG.info("Key with name \'" + name + "\' already registered");
      return null;
    }
    return new HighlightDisplayKey(name);
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls String name, String displayName, @NonNls String id){
    if (find(name) != null) {
      LOG.info("Key with name \'" + name + "\' already registered");
      return null;
    }
    HighlightDisplayKey highlightDisplayKey = new HighlightDisplayKey(name, id);
    ourKeyToDisplayNameMap.put(highlightDisplayKey, displayName);
    return highlightDisplayKey;
  }

  @Nullable
  public static HighlightDisplayKey register(@NonNls String name, String displayName) {
    return register(name, displayName, name);
  }

  public static String getDisplayNameByKey(HighlightDisplayKey key){
    return ourKeyToDisplayNameMap.get(key);
  }

  private HighlightDisplayKey(String name) {
    this(name, name);
  }

  public HighlightDisplayKey(@NonNls final String name, @NonNls final String ID) {
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

  public String getID(){
    return myID;
  }

  public static HighlightDisplayKey register(String shortName, String displayName, String id, String alternativeID) {
    final HighlightDisplayKey key = register(shortName, displayName, id);
    if (alternativeID != null) {
      ourKeyToAlternativeIDMap.put(key, alternativeID);
    }
    return key;
  }

  public static String getAlternativeID(HighlightDisplayKey key) {
    return ourKeyToAlternativeIDMap.get(key);
  }
}
