package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.FormatUtil;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.EditorFactory;

public class FileDifferenceModel {
  private Entry myLeft;
  private Entry myRight;

  public FileDifferenceModel(Entry left, Entry right) {
    myLeft = left;
    myRight = right;
  }

  public String getTitle() {
    return myRight.getPath();
  }

  public String getLeftTitle() {
    return formatTitle(myLeft);
  }

  public String getRightTitle() {
    return formatTitle(myRight);
  }

  private String formatTitle(Entry e) {
    return FormatUtil.formatTimestamp(e.getTimestamp()) + " - " + e.getName();
  }

  public DiffContent getLeftDiffContent(IdeaGateway gw, EditorFactory ef) {
    return getDiffContent(gw, ef, myLeft);
  }

  public DiffContent getRightDiffContent(IdeaGateway gw, EditorFactory ef) {
    return getDiffContent(gw, ef, myRight);
  }

  private SimpleContent getDiffContent(IdeaGateway gw, EditorFactory ef, Entry e) {
    return new SimpleContent(getContentOf(e), gw.getFileType(e.getName()), ef);
  }

  private String getContentOf(Entry e) {
    return new String(e.getContent().getBytes());
  }
}
