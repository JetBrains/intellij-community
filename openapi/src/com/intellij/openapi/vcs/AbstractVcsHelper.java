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

import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.localvcs.integration.LocalHistoryAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Component which provides means to invoke different VCS-related services.
 */
public abstract class AbstractVcsHelper {
  public static AbstractVcsHelper getInstance(Project project) {
    return ServiceManager.getService(project, AbstractVcsHelper.class);
  }

  public abstract void showErrors(List<VcsException> abstractVcsExceptions, String tabDisplayName);

  public abstract LocalHistoryAction startVcsAction(String actionName);

  public abstract void finishVcsAction(LocalHistoryAction action);

  /**
   * Runs the runnable inside the vcs transaction (if needed), collects all exceptions, commits/rollbacks transaction
   * and returns all exceptions together.
   */
  public abstract List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters);

  public abstract void optimizeImportsAndReformatCode(Collection<VirtualFile> files,
                                                      VcsConfiguration configuration,
                                                      Runnable finishAction,
                                                      boolean checkinProject);

  public void showError(final VcsException e, final String s) {
    showErrors(Arrays.asList(e), s);
  }

  public abstract void showAnnotation(FileAnnotation annotation, VirtualFile file);

  public abstract void showDifferences(final VcsFileRevision cvsVersionOn, final VcsFileRevision cvsVersionOn1, final File file);

  public abstract void showChangesBrowser(List<CommittedChangeList> changelists);

  public abstract void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title);

  public abstract void showChangesBrowser(CommittedChangeList changelist, @Nls String title);

  public abstract void showChangesBrowser(CommittedChangesProvider provider,
                                          final RepositoryLocation location,
                                          @Nls String title,
                                          @Nullable final Component parent);

  public abstract void showChangesBrowser(Component parent, Collection<Change> changes, @Nls String title);

  @Nullable
  public abstract <T extends CommittedChangeList, U extends ChangeBrowserSettings> T chooseCommittedChangeList(CommittedChangesProvider<T, U> provider,
                                                                                                               RepositoryLocation location);

  public abstract void openCommittedChangesTab(CommittedChangesProvider provider,
                                               VirtualFile root,
                                               ChangeBrowserSettings settings,
                                               int maxCount,
                                               final String title);

  public abstract void openCommittedChangesTab(CommittedChangesProvider provider,
                                               RepositoryLocation location,
                                               ChangeBrowserSettings settings,
                                               int maxCount,
                                               final String title);

  @NotNull
  public abstract List<VirtualFile> showMergeDialog(List<VirtualFile> files, MergeProvider provider);

  /**
   * Performs pre-checkin code analysis on the specified files.
   *
   * @param files the files to analyze.
   * @return the list of problems found during the analysis.
   * @throws ProcessCanceledException if the analysis was cancelled by the user.
   * @since 5.1
   */
  public abstract List<CodeSmellInfo> findCodeSmells(List<VirtualFile> files) throws ProcessCanceledException;

  /**
   * Shows the specified list of problems found during pre-checkin code analysis in a Messages pane.
   *
   * @param smells the problems to show.
   * @since 5.1
   */
  public abstract void showCodeSmellErrors(final List<CodeSmellInfo> smells);

  public abstract void showFileHistory(VcsHistoryProvider vcsHistoryProvider, FilePath path);

  public abstract void showFileHistory(VcsHistoryProvider vcsHistoryProvider, AnnotationProvider annotationProvider, FilePath path);

  /**
   * Shows the "Rollback Changes" dialog with the specified list of changes.
   *
   * @param changes the changes to show in the dialog.
   */
  public abstract void showRollbackChangesDialog(List<Change> changes);

  @Nullable
  public abstract Collection<VirtualFile> selectFilesToProcess(List<VirtualFile> files,
                                                               final String title,
                                                               @Nullable final String prompt,
                                                               final String singleFileTitle,
                                                               final String singleFilePromptTemplate,
                                                               final VcsShowConfirmationOption confirmationOption);

  @Nullable
  public abstract Collection<FilePath> selectFilePathsToProcess(List<FilePath> files,
                                                                final String title,
                                                                @Nullable final String prompt,
                                                                final String singleFileTitle,
                                                                final String singleFilePromptTemplate,
                                                                final VcsShowConfirmationOption confirmationOption);
}
