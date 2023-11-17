/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.ui.models;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FileDifferenceModel {
  protected final Project myProject;
  protected final IdeaGateway myGateway;
  private final boolean isRightContentCurrent;

  protected FileDifferenceModel(Project p, @NotNull IdeaGateway gw, boolean currentRightContent) {
    myProject = p;
    myGateway = gw;
    isRightContentCurrent = currentRightContent;
  }

  public @NlsContexts.DialogTitle String getTitle() {
    Entry e = getRightEntry();
    if (e == null) e = getLeftEntry();
    if (e == null) return null;
    return FileUtil.toSystemDependentName(e.getPath());
  }

  public @NlsContexts.Label String getLeftTitle(@NotNull RevisionProcessingProgress p) {
    Entry leftEntry = getLeftEntry();
    if (leftEntry == null) return LocalHistoryBundle.message("file.does.not.exist");
    return formatTitle(leftEntry, isLeftContentAvailable(p));
  }

  public @NlsContexts.Label String getRightTitle(@NotNull RevisionProcessingProgress p) {
    Entry rightEntry = getRightEntry();
    if (rightEntry == null) return LocalHistoryBundle.message("file.does.not.exist");
    if (isRightContentAvailable(p)) {
      return isRightContentCurrent ? LocalHistoryBundle.message("current.revision") : formatTitle(rightEntry, true);
    }
    else {
      return formatTitle(rightEntry, false);
    }
  }

  private static @NlsContexts.Label String formatTitle(@NotNull Entry e, boolean isAvailable) {
    String result = DateFormatUtil.formatDateTime(e.getTimestamp()) + " - " + e.getName();
    if (!isAvailable) {
      result += " - " + LocalHistoryBundle.message("content.not.available");
    }
    return result;
  }

  protected abstract @Nullable Entry getLeftEntry();

  protected abstract @Nullable Entry getRightEntry();

  public @NotNull DiffContent getLeftDiffContent(@NotNull RevisionProcessingProgress p) {
    Entry leftEntry = getLeftEntry();
    if (leftEntry == null) return DiffContentFactory.getInstance().createEmpty();
    if (isLeftContentAvailable(p)) {
      DiffContent content = getReadOnlyLeftDiffContent(p);
      if (content != null) return content;
    }
    return DiffContentFactory.getInstance().create(LocalHistoryBundle.message("content.not.available"));
  }

  public @NotNull DiffContent getRightDiffContent(@NotNull RevisionProcessingProgress p) {
    Entry rightEntry = getRightEntry();
    if (rightEntry == null) return DiffContentFactory.getInstance().createEmpty();
    if (isRightContentAvailable(p)) {
      DiffContent content = isRightContentCurrent ? getEditableRightDiffContent(p) : getReadOnlyRightDiffContent(p);
      if (content != null) return content;
    }
    return DiffContentFactory.getInstance().create(LocalHistoryBundle.message("content.not.available"));
  }

  public boolean isLeftContentAvailable() {
    return isLeftContentAvailable(new RevisionProcessingProgress.Empty());
  }

  public boolean isRightContentAvailable() {
    return isRightContentAvailable(new RevisionProcessingProgress.Empty());
  }

  protected abstract boolean isLeftContentAvailable(@NotNull RevisionProcessingProgress p);

  protected abstract boolean isRightContentAvailable(@NotNull RevisionProcessingProgress p);

  protected abstract @Nullable DiffContent getReadOnlyLeftDiffContent(@NotNull RevisionProcessingProgress p);

  protected abstract @Nullable DiffContent getReadOnlyRightDiffContent(@NotNull RevisionProcessingProgress p);

  protected abstract @Nullable DiffContent getEditableRightDiffContent(@NotNull RevisionProcessingProgress p);

  public static @NotNull ContentDiffRequest createRequest(@NotNull FileDifferenceModel model,
                                                          @NotNull RevisionProcessingProgress progress) {
    return ReadAction.compute(() -> {
      progress.processingLeftRevision();
      DiffContent left = model.getLeftDiffContent(progress);

      progress.processingRightRevision();
      DiffContent right = model.getRightDiffContent(progress);

      return new SimpleDiffRequest(model.getTitle(), left, right, model.getLeftTitle(progress), model.getRightTitle(progress));
    });
  }
}
