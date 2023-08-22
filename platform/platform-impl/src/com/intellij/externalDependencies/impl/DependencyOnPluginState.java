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
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("plugin")
public class DependencyOnPluginState {
  DependencyOnPluginState() {
  }

  DependencyOnPluginState(DependencyOnPlugin dependency) {
    myId = dependency.getPluginId();
    myMinVersion = dependency.getRawMinVersion();
    myMaxVersion = dependency.getRawMaxVersion();
  }

  @Attribute("id")
  public String myId;
  @Attribute("min-version")
  public String myMinVersion;
  @Attribute("max-version")
  public String myMaxVersion;
}
