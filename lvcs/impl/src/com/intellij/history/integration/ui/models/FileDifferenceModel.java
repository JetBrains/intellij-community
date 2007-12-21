package com.intellij.history.integration.ui.models;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;

public abstract class FileDifferenceModel {
  private IdeaGateway myGateway;
  private boolean isRightContentCurrent;

  protected FileDifferenceModel(IdeaGateway gw, boolean currentRightContent) {
    myGateway = gw;
    isRightContentCurrent = currentRightContent;
  }

  public String getTitle() {
    return getRightEntry().getPath();
  }

  public String getLeftTitle() {
    return formatTitle(getLeftEntry());
  }

  public String getRightTitle() {
    if (isRightContentCurrent) return LocalHistoryBundle.message("current.revision");
    return formatTitle(getRightEntry());
  }

  protected abstract Entry getLeftEntry();

  protected abstract Entry getRightEntry();

  private String formatTitle(Entry e) {
    return FormatUtil.formatTimestamp(e.getTimestamp()) + " - " + e.getName();
  }

  public abstract DiffContent getLeftDiffContent(RevisionProcessingProgress p);

  public DiffContent getRightDiffContent(RevisionProcessingProgress p) {
    if (isRightContentCurrent) return getEditableRightDiffContent(p);
    return getReadOnlyRightDiffContent(p);
  }

  protected abstract DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p);

  protected abstract DiffContent getEditableRightDiffContent(RevisionProcessingProgress p);

  protected SimpleContent createSimpleDiffContent(String content, Entry e) {
    return new SimpleContent(content, myGateway.getFileType(e.getName()));
  }

  protected Project getProject() {
    return myGateway.getProject();
  }

  protected Document getDocument() {
    return myGateway.getDocumentFor(getRightEntry().getPath());
  }
}
