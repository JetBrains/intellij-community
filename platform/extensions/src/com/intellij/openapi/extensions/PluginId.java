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
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 */
public class PluginId implements Comparable<PluginId> {
  public static final PluginId[] EMPTY_ARRAY = new PluginId[0];

  private static final Map<String, PluginId> ourRegisteredIds = new HashMap<String, PluginId>();

  private final String myIdString;

  private PluginId(String idString) {
    myIdString = idString;
  }

  @Override
  public int compareTo(PluginId o) {
    return myIdString.compareTo(o.myIdString);
  }

  @NotNull
  public static PluginId getId(String idString) {
    PluginId pluginId = ourRegisteredIds.get(idString);
    if (pluginId == null) {
      pluginId = new PluginId(idString);
      ourRegisteredIds.put(idString, pluginId);
    }
    return pluginId;
  }

  @NonNls
  public String getIdString() {
    return myIdString;
  }

  @Override
  public String toString() {
    return getIdString();
  }

  public static Map<String, PluginId> getRegisteredIds() {
    return ourRegisteredIds;
  }
}
