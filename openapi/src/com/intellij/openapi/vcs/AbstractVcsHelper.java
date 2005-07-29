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
package com.intellij.openapi.vcs;

import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.versionBrowser.VersionsProvider;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

  public abstract void showMergeDialog(List<VirtualFile> files, MergeProvider provider, final AnActionEvent e);
}
