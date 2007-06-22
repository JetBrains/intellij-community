package com.intellij.history.integration.ui.models;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.EditorFactory;

public abstract class FileDifferenceModel {
  public String getTitle() {
    return getRightEntry().getPath();
  }

  public String getLeftTitle() {
    return formatTitle(getLeftEntry());
  }

  public String getRightTitle() {
    return formatTitle(getRightEntry());
  }

  protected abstract Entry getLeftEntry();

  protected abstract Entry getRightEntry();

  private String formatTitle(Entry e) {
    return FormatUtil.formatTimestamp(e.getTimestamp()) + " - " + e.getName();
  }

  public abstract DiffContent getLeftDiffContent(IdeaGateway gw, EditorFactory ef, RevisionProcessingProgress p);

  public abstract DiffContent getRightDiffContent(IdeaGateway gw, EditorFactory ef, RevisionProcessingProgress p);

  protected SimpleContent createDiffContent(IdeaGateway gw, EditorFactory ef, String content, Entry e) {
    return new SimpleContent(content, gw.getFileType(e.getName()), ef);
  }

}
