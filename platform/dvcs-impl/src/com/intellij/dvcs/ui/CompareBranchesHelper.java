// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.branch.DvcsCompareSettings;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public interface CompareBranchesHelper {
  @NotNull
  Project getProject();

  @NotNull
  RepositoryManager getRepositoryManager();

  @NotNull
  DvcsCompareSettings getDvcsCompareSettings();

  @NlsSafe
  @NotNull
  String formatLogCommand(@NotNull String firstBranch, @NotNull String secondBranch);
}
