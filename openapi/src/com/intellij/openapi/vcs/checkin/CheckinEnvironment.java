/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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

  RollbackProvider createRollbackProviderOn(AbstractRevisions[] selectedRevisions);

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
}
