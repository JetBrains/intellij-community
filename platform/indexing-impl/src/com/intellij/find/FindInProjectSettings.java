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

package com.intellij.find;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface FindInProjectSettings {

  static FindInProjectSettings getInstance(Project project) {
    return ServiceManager.getService(project, FindInProjectSettings.class);
  }

  void addStringToFind(@NotNull String s);

  void addStringToReplace(@NotNull String s);

  void addDirectory(@NotNull String s);

  @NotNull
  String[] getRecentFindStrings();

  @NotNull
  String[] getRecentReplaceStrings();

  @NotNull
  List<String> getRecentDirectories();
}
