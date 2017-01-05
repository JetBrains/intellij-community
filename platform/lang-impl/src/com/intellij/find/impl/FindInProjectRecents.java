/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.find.impl;

import com.intellij.find.FindInProjectSettings;
import com.intellij.find.FindSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(
  name = "FindInProjectRecents",
  storages = {@Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)}
)
final class FindInProjectRecents extends FindInProjectSettingsBase implements FindInProjectSettings {
  public static FindInProjectSettings getInstance(Project project) {
    return ServiceManager.getService(project, FindInProjectSettings.class);
  }

  @NotNull
  public List<String> getRecentDirectories() {
    ArrayList<String> strings = new ArrayList<>(FindSettings.getInstance().getRecentDirectories());
    strings.addAll(super.getRecentDirectories());
    return strings;
  }

  @NotNull
  public String[] getRecentFindStrings(){
    return ArrayUtil.mergeArrays(FindSettings.getInstance().getRecentFindStrings(), super.getRecentFindStrings());
  }

  @NotNull
  public String[] getRecentReplaceStrings(){
    return ArrayUtil.mergeArrays(FindSettings.getInstance().getRecentReplaceStrings(), super.getRecentReplaceStrings());
  }
}
