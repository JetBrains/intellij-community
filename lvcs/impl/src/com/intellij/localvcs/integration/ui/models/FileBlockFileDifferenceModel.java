package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.EditorFactory;

public class FileBlockFileDifferenceModel extends FileDifferenceModel {
  private SelectedBlockCalculator myCalculator;
  private Revision myLeftRevision;
  private Revision myRightRevision;

  public FileBlockFileDifferenceModel(SelectedBlockCalculator c, Revision left, Revision right) {
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
  public DiffContent getLeftDiffContent(IdeaGateway gw, EditorFactory ef) {
    return getDiffContent(gw, ef, myLeftRevision);
  }

  @Override
  public DiffContent getRightDiffContent(IdeaGateway gw, EditorFactory ef) {
    return getDiffContent(gw, ef, myRightRevision);
  }

  private SimpleContent getDiffContent(IdeaGateway gw, EditorFactory ef, Revision r) {
    return createDiffContent(gw, ef, getContentOf(r), r.getEntry());
  }

  private String getContentOf(Revision r) {
    return myCalculator.getBlock(r).getBlockContent();
  }
}
