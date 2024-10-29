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

import com.intellij.diff.contents.DiffContent;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.project.Project;
import com.intellij.platform.lvcs.impl.diff.EntryDiffContentKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SelectionDifferenceModel extends FileDifferenceModel {
  private final RevisionSelectionCalculator myCalculator;
  private final Revision myLeftRevision;
  private final Revision myRightRevision;
  private final int myFrom;
  private final int myTo;

  public SelectionDifferenceModel(Project p,
                                  @NotNull IdeaGateway gw,
                                  @NotNull RevisionSelectionCalculator c,
                                  @NotNull Revision left,
                                  @NotNull Revision right,
                                  int from,
                                  int to,
                                  boolean editableRightContent) {
    super(p, gw, editableRightContent);
    myCalculator = c;
    myLeftRevision = left;
    myRightRevision = right;
    myFrom = from;
    myTo = to;
  }

  @Override
  protected Entry getLeftEntry() {
    return myLeftRevision.findEntry();
  }

  @Override
  protected Entry getRightEntry() {
    return myRightRevision.findEntry();
  }

  @Override
  protected boolean isLeftContentAvailable(@NotNull RevisionProcessingProgress p) {
    return myCalculator.canCalculateFor(myLeftRevision, p);
  }

  @Override
  protected boolean isRightContentAvailable(@NotNull RevisionProcessingProgress p) {
    return myCalculator.canCalculateFor(myRightRevision, p);
  }

  @Override
  protected @Nullable DiffContent getReadOnlyLeftDiffContent(@NotNull RevisionProcessingProgress p) {
    return getDiffContent(myLeftRevision, p);
  }

  @Override
  protected @Nullable DiffContent getReadOnlyRightDiffContent(@NotNull RevisionProcessingProgress p) {
    return getDiffContent(myRightRevision, p);
  }

  @Override
  protected @Nullable DiffContent getEditableRightDiffContent(@NotNull RevisionProcessingProgress p) {
    Entry rightEntry = getRightEntry();
    if (rightEntry == null) return null;

    return EntryDiffContentKt.createCurrentDiffContent(myProject, myGateway, rightEntry.getPath(), myFrom, myTo);
  }

  private @Nullable DiffContent getDiffContent(@NotNull Revision r, RevisionProcessingProgress p) {
    Entry e = r.findEntry();
    if (e == null) return null;
    Long changeSetId = r.getChangeSetId();
    if (changeSetId == null) return EntryDiffContentKt.createCurrentDiffContent(myProject, myGateway, e.getPath(), myFrom, myTo);
    return EntryDiffContentKt.createDiffContent(myGateway, e, changeSetId, myCalculator, p);
  }
}
