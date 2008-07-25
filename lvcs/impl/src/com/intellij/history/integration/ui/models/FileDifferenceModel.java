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
    Entry e = getRightEntry();
    if (e == null) e = getLeftEntry();
    return e.getPath();
  }

  public String getLeftTitle(RevisionProcessingProgress p) {
    if (!hasLeftEntry()) return LocalHistoryBundle.message("file.does.not.exist");
    return formatTitle(getLeftEntry(), isLeftContentAvailable(p));
  }

  public String getRightTitle(RevisionProcessingProgress p) {
    if (!hasRightEntry()) return LocalHistoryBundle.message("file.does.not.exist"); 
    if (!isRightContentAvailable(p)) {
      return formatTitle(getRightEntry(), false);
    }
    if (isRightContentCurrent) return LocalHistoryBundle.message("current.revision");
    return formatTitle(getRightEntry(), true);
  }

  private String formatTitle(Entry e, boolean isAvailable) {
    String result = FormatUtil.formatTimestamp(e.getTimestamp()) + " - " + e.getName();
    if (!isAvailable) {
      result += " - " + LocalHistoryBundle.message("content.not.available");
    }
    return result;
  }

  protected abstract Entry getLeftEntry();

  protected abstract Entry getRightEntry();

  public DiffContent getLeftDiffContent(RevisionProcessingProgress p) {
    if (!canShowLeftEntry(p)) return new SimpleContent("");
    return doGetLeftDiffContent(p);
  }

  protected abstract DiffContent doGetLeftDiffContent(RevisionProcessingProgress p);

  public DiffContent getRightDiffContent(RevisionProcessingProgress p) {
    if (!canShowRightEntry(p)) return new SimpleContent("");
    if (isRightContentCurrent) return getEditableRightDiffContent(p);
    return getReadOnlyRightDiffContent(p);
  }

  private boolean canShowLeftEntry(RevisionProcessingProgress p) {
    return hasLeftEntry() && isLeftContentAvailable(p);
  }

  private boolean canShowRightEntry(RevisionProcessingProgress p) {
    return hasRightEntry() && isRightContentAvailable(p);
  }

  private boolean hasLeftEntry() {
    return getLeftEntry() != null;
  }

  private boolean hasRightEntry() {
    return getRightEntry() != null;
  }

  protected abstract boolean isLeftContentAvailable(RevisionProcessingProgress p);

  protected abstract boolean isRightContentAvailable(RevisionProcessingProgress p);

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
