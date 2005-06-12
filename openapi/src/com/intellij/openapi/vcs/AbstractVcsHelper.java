/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.versionBrowser.VersionsProvider;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.List;
import java.util.Arrays;
import java.io.File;

public abstract class AbstractVcsHelper {
  public static AbstractVcsHelper getInstance(Project project) {
    return project.getComponent(AbstractVcsHelper.class);
  }

  public abstract void showErrors(List abstractVcsExceptions, String tabDisplayName);

  public abstract void markFileAsUpToDate(VirtualFile file);

  public abstract LvcsAction startVcsAction(String actionName);

  public abstract void finishVcsAction(com.intellij.openapi.localVcs.LvcsAction action);

  /**
   * Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction
   * and returns all exceptions together.
   */
  public abstract List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters);

  /**
   * If the file was moved/renamed, this method will return path
   * to the last known up-to-date revision
   */
  public abstract String getUpToDateFilePath(VirtualFile file);

  public abstract Refreshable createCheckinProjectPanel(Project project);

  public abstract List<VcsException> doCheckinProject(CheckinProjectPanel checkinProjectPanel,
                                                      Object checkinParameters,
                                                      AbstractVcs abstractVcs);

  public abstract void doCheckinFiles(VirtualFile[] files, Object checkinParameters);

  public abstract void optimizeImportsAndReformatCode(Collection<VirtualFile> files,
                                                      VcsConfiguration configuration,
                                                      Runnable finishAction,
                                                      boolean checkinProject);

  public abstract CheckinProjectDialogImplementer createCheckinProjectDialog(String title,
                                                                             boolean requestComments,
                                                                             Collection<String> roots);

  public void showError(final VcsException e, final String s) {
    showErrors(Arrays.asList(new VcsException[]{e}), s);
  }

  public abstract void showAnnotation(FileAnnotation annotation, VirtualFile file);

  public abstract void showDifferences(final VcsFileRevision cvsVersionOn,
                                       final VcsFileRevision cvsVersionOn1,
                                       final File file);

  public abstract void showChangesBrowser(VersionsProvider versionsProvider);

  public abstract void showRevisions(List<AbstractRevisions> revisions, final String title);
}
