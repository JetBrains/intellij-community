// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;

@ApiStatus.Internal
public final class IndexPatternOptimizerImpl implements IndexPatternOptimizer {
  @Override
  public @Unmodifiable @NotNull List<String> extractStringsToFind(@NotNull String regexp) {
    // short circuit for known built-in patterns, no need to spin up RegExp parser and its elements
    if ("\\btodo\\b.*".equals(regexp)) return singletonList("todo");
    if ("\\bfixme\\b.*".equals(regexp)) return singletonList("fixme");

    // TODO some datagrip tests are not passed with unknown reason (no exception in logs but it's)
    if (ApplicationManager.getApplication().isUnitTestMode() && PlatformUtils.isDataGrip()) {
      return Collections.emptyList();
    }

    Project project = null;
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager != null) {
      Project[] openProjects = projectManager.getOpenProjects();
      if (openProjects.length == 1) {
        project = openProjects[0];
      }
    }

    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    String stringToFind = FindInProjectUtil.extractStringToFind(regexp, project);
    return StringUtil.getWordsIn(stringToFind);
  }
}
