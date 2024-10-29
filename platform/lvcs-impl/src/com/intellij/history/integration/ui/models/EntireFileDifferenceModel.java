// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui.models;

import com.intellij.diff.contents.DiffContent;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.project.Project;
import com.intellij.platform.lvcs.impl.diff.EntryDiffContentKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class EntireFileDifferenceModel extends FileDifferenceModel {
  private final @Nullable Entry myLeft;
  private final @Nullable Entry myRight;

  public EntireFileDifferenceModel(Project p, IdeaGateway gw, @Nullable Entry left, @Nullable Entry right, boolean editableRightContent) {
    super(p, gw, editableRightContent);
    myLeft = left;
    myRight = right;
  }

  @Override
  protected Entry getLeftEntry() {
    return myLeft;
  }

  @Override
  protected Entry getRightEntry() {
    return myRight;
  }

  @Override
  protected boolean isLeftContentAvailable(@NotNull RevisionProcessingProgress p) {
    return myLeft != null && myLeft.getContent().isAvailable();
  }

  @Override
  protected boolean isRightContentAvailable(@NotNull RevisionProcessingProgress p) {
    return myRight != null && myRight.getContent().isAvailable();
  }

  @Override
  protected @Nullable DiffContent getReadOnlyLeftDiffContent(@NotNull RevisionProcessingProgress p) {
    return getDiffContent(myLeft);
  }

  @Override
  protected @Nullable DiffContent getReadOnlyRightDiffContent(@NotNull RevisionProcessingProgress p) {
    return getDiffContent(myRight);
  }

  @Override
  protected @Nullable DiffContent getEditableRightDiffContent(@NotNull RevisionProcessingProgress p) {
    if (myRight == null) return null;

    return EntryDiffContentKt.createCurrentDiffContent(myProject, myGateway, myRight.getPath());
  }

  private @Nullable DiffContent getDiffContent(@Nullable Entry e) {
    if (e == null) return null;
    return EntryDiffContentKt.createDiffContent(myProject, myGateway, e);
  }
}
