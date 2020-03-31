// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  String @NotNull [] getRecentFindStrings();

  String @NotNull [] getRecentReplaceStrings();

  @NotNull
  List<String> getRecentDirectories();
}
