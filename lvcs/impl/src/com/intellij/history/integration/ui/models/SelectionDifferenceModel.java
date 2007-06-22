package com.intellij.history.integration.ui.models;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.EditorFactory;

public class SelectionDifferenceModel extends FileDifferenceModel {
  private SelectionCalculator myCalculator;
  private Revision myLeftRevision;
  private Revision myRightRevision;

  public SelectionDifferenceModel(SelectionCalculator c, Revision left, Revision right) {
    myCalculator = c;
    myLeftRevision = left;
    myRightRevision = right;
  }

  @Override
  protected Entry getLeftEntry() {
    return myLeftRevision.getEntry();
  }

  @Override
  protected Entry getRightEntry() {
    return myRightRevision.getEntry();
  }

  @Override
  public DiffContent getLeftDiffContent(IdeaGateway gw, EditorFactory ef, RevisionProcessingProgress p) {
    return getDiffContent(gw, ef, myLeftRevision, p);
  }

  @Override
  public DiffContent getRightDiffContent(IdeaGateway gw, EditorFactory ef, RevisionProcessingProgress p) {
    return getDiffContent(gw, ef, myRightRevision, p);
  }

  private SimpleContent getDiffContent(IdeaGateway gw, EditorFactory ef, Revision r, RevisionProcessingProgress p) {
    return createDiffContent(gw, ef, getContentOf(r, p), r.getEntry());
  }

  private String getContentOf(Revision r, RevisionProcessingProgress p) {
    return myCalculator.getSelectionFor(r, p).getBlockContent();
  }
}
