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
package com.intellij.externalDependencies;

import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Describes a plugin (and optionally its versions range) which can be required for a project to operate normally.
 *
 * @author nik
 */
public class DependencyOnPlugin implements ProjectExternalDependency, Comparable<DependencyOnPlugin> {
  private final String myPluginId;
  private final String myMinVersion;
  private final String myMaxVersion;
  private final String myChannel;

  public DependencyOnPlugin(@NotNull String pluginId, @Nullable String minVersion, @Nullable String maxVersion, @Nullable String channel) {
    myPluginId = pluginId;
    myMinVersion = minVersion;
    myMaxVersion = maxVersion;
    myChannel = channel;
  }

  public String getPluginId() {
    return myPluginId;
  }

  public String getMinVersion() {
    return myMinVersion;
  }

  public String getMaxVersion() {
    return myMaxVersion;
  }

  public String getChannel() {
    return myChannel;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DependencyOnPlugin plugin = (DependencyOnPlugin)o;

    return myPluginId.equals(plugin.myPluginId)
           && Comparing.equal(myMinVersion, plugin.myMinVersion)
           && Comparing.equal(myMaxVersion, plugin.myMaxVersion)
           && Comparing.equal(myChannel,    plugin.myChannel);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * (31 * myPluginId.hashCode() + Comparing.hashcode(myMinVersion)) + Comparing.hashcode(myMaxVersion)) + Comparing.hashcode(myChannel);
  }

  @Override
  public int compareTo(DependencyOnPlugin o) {
    return ContainerUtil.compareLexicographically(Arrays.asList(myPluginId, myMinVersion, myMaxVersion),
                                                  Arrays.asList(o.myPluginId, o.myMinVersion, o.myMaxVersion));
  }
}
