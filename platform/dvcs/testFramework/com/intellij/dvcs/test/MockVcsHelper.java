/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.dvcs.test;

import com.intellij.ide.errorTreeView.HotfixData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitResultHandler;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Kirill Likhodedov
 */
public class MockVcsHelper extends AbstractVcsHelper {
  private volatile boolean myCommitDialogShown;
  private volatile boolean myMergeDialogShown;

  private CommitHandler myCommitHandler;
  private MergeHandler myMergeHandler;

  public MockVcsHelper(@NotNull Project project) {
    super(project);
  }

  @Override
  public void showErrors(List<VcsException> abstractVcsExceptions, @NotNull String tabDisplayName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showErrors(Map<HotfixData, List<VcsException>> exceptionGroups, @NotNull String tabDisplayName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<VcsException> runTransactionRunnable(AbstractVcs vcs, TransactionRunnable runnable, Object vcsParameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showAnnotation(FileAnnotation annotation, VirtualFile file, AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showDifferences(VcsFileRevision cvsVersionOn, VcsFileRevision cvsVersionOn1, File file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showChangesListBrowser(CommittedChangeList changelist, @Nls String title) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showChangesBrowser(List<CommittedChangeList> changelists) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showChangesBrowser(List<CommittedChangeList> changelists, @Nls String title) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showChangesBrowser(CommittedChangesProvider provider,
                                 RepositoryLocation location,
                                 @Nls String title,
                                 @Nullable Component parent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showWhatDiffersBrowser(@Nullable Component parent, Collection<Change> changes, @Nls String title) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T extends CommittedChangeList, U extends ChangeBrowserSettings> T chooseCommittedChangeList(@NotNull CommittedChangesProvider<T, U> provider,
                                                                                                      RepositoryLocation location) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void openCommittedChangesTab(AbstractVcs vcs, VirtualFile root, ChangeBrowserSettings settings, int maxCount, String title) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void openCommittedChangesTab(CommittedChangesProvider provider,
                                      RepositoryLocation location,
                                      ChangeBrowserSettings settings,
                                      int maxCount,
                                      String title) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<VirtualFile> showMergeDialog(List<VirtualFile> files,
                                           MergeProvider provider,
                                           @NotNull MergeDialogCustomizer mergeDialogCustomizer) {
    myMergeDialogShown = true;
    if (myMergeHandler != null) {
      myMergeHandler.showMergeDialog();
    }
    return Collections.emptyList();
  }

  public boolean mergeDialogWasShown() {
    return myMergeDialogShown;
  }

  @Override
  public void showFileHistory(VcsHistoryProvider vcsHistoryProvider, FilePath path, AbstractVcs vcs, String repositoryPath) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showFileHistory(VcsHistoryProvider vcsHistoryProvider,
                              AnnotationProvider annotationProvider,
                              FilePath path,
                              String repositoryPath,
                              AbstractVcs vcs) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showRollbackChangesDialog(List<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<VirtualFile> selectFilesToProcess(List<VirtualFile> files,
                                                      String title,
                                                      @Nullable String prompt,
                                                      String singleFileTitle,
                                                      String singleFilePromptTemplate,
                                                      VcsShowConfirmationOption confirmationOption) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<FilePath> selectFilePathsToProcess(List<FilePath> files,
                                                       String title,
                                                       @Nullable String prompt,
                                                       String singleFileTitle,
                                                       String singleFilePromptTemplate,
                                                       VcsShowConfirmationOption confirmationOption) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean commitChanges(@NotNull Collection<Change> changes, @NotNull LocalChangeList initialChangeList,
                               @NotNull String commitMessage, @Nullable CommitResultHandler customResultHandler) {
    myCommitDialogShown = true;
    if (myCommitHandler != null) {
      boolean success = myCommitHandler.commit(commitMessage);
      if (customResultHandler != null) {
        if (success) {
          customResultHandler.onSuccess(commitMessage);
        }
        else {
          customResultHandler.onFailure();
        }
      }
      return success;
    }
    if (customResultHandler != null) {
      customResultHandler.onFailure();
    }
    return false;
  }

  public void registerHandler(CommitHandler handler) {
    myCommitHandler = handler;
  }

  public void registerHandler(MergeHandler handler) {
    myMergeHandler = handler;
  }

  public boolean commitDialogWasShown() {
    return myCommitDialogShown;
  }

  public interface CommitHandler {
    boolean commit(String commitMessage);
  }

  public interface MergeHandler {
    void showMergeDialog();
  }

}
