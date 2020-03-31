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

public class EntireFileDifferenceModel extends FileDifferenceModel {
  private final Entry myLeft;
  private final Entry myRight;

  public EntireFileDifferenceModel(Project p, IdeaGateway gw, Entry left, Entry right, boolean editableRightContent) {
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
  protected boolean isLeftContentAvailable(RevisionProcessingProgress p) {
    return myLeft.getContent().isAvailable();
  }

  @Override
  protected boolean isRightContentAvailable(RevisionProcessingProgress p) {
    return myRight.getContent().isAvailable();
  }

  @Override
  protected DiffContent doGetLeftDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myLeft);
  }

  @Override
  protected DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myRight);
  }

  @Override
  protected DiffContent getEditableRightDiffContent(RevisionProcessingProgress p) {
    Document d = getDocument();
    return DiffContentFactory.getInstance().create(myProject, d);
  }

  private DiffContent getDiffContent(Entry e) {
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
