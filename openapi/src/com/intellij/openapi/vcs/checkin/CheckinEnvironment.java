/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectDialogImplementer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RollbackProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.util.ui.ColumnInfo;

import java.util.List;

/**
 * author: lesya
 */
public interface CheckinEnvironment {
  RevisionsFactory getRevisionsFactory();

  RollbackProvider createRollbackProviderOn(AbstractRevisions[] selectedRevisions, final boolean containsExcluded);

  DifferenceType[] getAdditionalDifferenceTypes();

  ColumnInfo[] getAdditionalColumns(int index);

  RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject(Refreshable panel);

  RefreshableOnComponent createAdditionalOptionsPanelForCheckinFile(Refreshable panel);

  RefreshableOnComponent createAdditionalOptionsPanel(Refreshable panel, boolean checkinProject);

  String getDefaultMessageFor(FilePath[] filesToCheckin);

  void onRefreshFinished();

  void onRefreshStarted();

  AnAction[] getAdditionalActions(int index);

  String prepareCheckinMessage(String text);

  String getHelpId();

  List<VcsException> commit(CheckinProjectDialogImplementer dialog, Project project);

  List<VcsException> commit(FilePath[] roots, Project project, String preparedComment);

  String getCheckinOperationName();

  /**
   * @return true if check in dialog should be shown even if there are no files to check in
   */
  boolean showCheckinDialogInAnyCase();
}
