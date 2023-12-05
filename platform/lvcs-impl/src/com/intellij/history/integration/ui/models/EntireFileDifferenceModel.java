// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui.models;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.contents.DiffContent;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    Document d = myGateway.getDocument(myRight.getPath());
    if (d == null) return null;

    return DiffContentFactory.getInstance().create(myProject, d);
  }

  private @Nullable DiffContent getDiffContent(@Nullable Entry e) {
    if (e == null) return null;
    byte[] content = e.getContent().getBytes();
    VirtualFile virtualFile = myGateway.findVirtualFile(e.getPath());
    if (virtualFile != null) {
      return DiffContentFactoryEx.getInstanceEx().createDocumentFromBytes(myProject, content, virtualFile);
    }
    else {
      FileType fileType = myGateway.getFileType(e.getName());
      return DiffContentFactoryEx.getInstanceEx().createDocumentFromBytes(myProject, content, fileType, e.getName());
    }
  }
}
