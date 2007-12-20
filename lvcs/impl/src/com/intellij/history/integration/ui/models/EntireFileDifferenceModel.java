package com.intellij.history.integration.ui.models;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;

public class EntireFileDifferenceModel extends FileDifferenceModel {
  private Entry myLeft;
  private Entry myRight;

  public EntireFileDifferenceModel(IdeaGateway gw, Entry left, Entry right, boolean editableRightContent) {
    super(gw, editableRightContent);
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
  public DiffContent getLeftDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myLeft);
  }

  @Override
  public DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myRight);
  }

  protected DiffContent getEditableRightDiffContent(RevisionProcessingProgress p) {
    Document d = getDocument();
    return DocumentContent.fromDocument(getProject(), d);
  }

  private SimpleContent getDiffContent(Entry e) {
    return createSimpleDiffContent(getContentOf(e), e);
  }

  private String getContentOf(Entry e) {
    return e.getContent().getString();
  }
}