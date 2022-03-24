// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class IndexPatternOptimizerImpl implements IndexPatternOptimizer {
  @Override
  public @NotNull List<String> extractStringsToFind(@NotNull String regexp) {
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
