package com.intellij.history.integration.ui.models;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.EditorFactory;

public class EntireFileDifferenceModel extends FileDifferenceModel {
  private Entry myLeft;
  private Entry myRight;

  public EntireFileDifferenceModel(Entry left, Entry right) {
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
  public DiffContent getLeftDiffContent(IdeaGateway gw, EditorFactory ef, RevisionProcessingProgress p) {
    return getDiffContent(gw, ef, myLeft);
  }

  @Override
  public DiffContent getRightDiffContent(IdeaGateway gw, EditorFactory ef, RevisionProcessingProgress p) {
    return getDiffContent(gw, ef, myRight);
  }

  private SimpleContent getDiffContent(IdeaGateway gw, EditorFactory ef, Entry e) {
    return createDiffContent(gw, ef, getContentOf(e), e);
  }

  private String getContentOf(Entry e) {
    return new String(e.getContent().getBytes());
  }
}