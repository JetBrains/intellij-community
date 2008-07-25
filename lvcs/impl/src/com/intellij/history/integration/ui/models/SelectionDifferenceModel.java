package com.intellij.history.integration.ui.models;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;

public class SelectionDifferenceModel extends FileDifferenceModel {
  private SelectionCalculator myCalculator;
  private Revision myLeftRevision;
  private Revision myRightRevision;
  private int myFrom;
  private int myTo;

  public SelectionDifferenceModel(IdeaGateway gw,
                                  SelectionCalculator c,
                                  Revision left,
                                  Revision right,
                                  int from,
                                  int to,
                                  boolean editableRightContent) {
    super(gw, editableRightContent);
    myCalculator = c;
    myLeftRevision = left;
    myRightRevision = right;
    myFrom = from;
    myTo = to;
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
  protected boolean isLeftContentAvailable(RevisionProcessingProgress p) {
    return myCalculator.canCalculateFor(myLeftRevision, p);
  }

  @Override
  protected boolean isRightContentAvailable(RevisionProcessingProgress p) {
    return myCalculator.canCalculateFor(myRightRevision, p);
  }

  @Override
  protected DiffContent doGetLeftDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myLeftRevision, p);
  }

  @Override
  protected DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myRightRevision, p);
  }

  @Override
  protected DiffContent getEditableRightDiffContent(RevisionProcessingProgress p) {
    Document d = getDocument();

    int fromOffset = d.getLineStartOffset(myFrom);
    int toOffset = d.getLineEndOffset(myTo);

    return FragmentContent.fromRangeMarker(d.createRangeMarker(fromOffset, toOffset), getProject());
  }

  private SimpleContent getDiffContent(Revision r, RevisionProcessingProgress p) {
    return createSimpleDiffContent(getContentOf(r, p), r.getEntry());
  }

  private String getContentOf(Revision r, RevisionProcessingProgress p) {
    return myCalculator.getSelectionFor(r, p).getBlockContent();
  }
}
