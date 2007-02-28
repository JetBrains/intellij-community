package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.Entry;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;

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

  public SimpleContent getLeftDiffContent(FileTypeManager tm, EditorFactory ef) {
    return getDiffContent(tm, ef, myLeft);
  }

  public SimpleContent getRightDiffContent(FileTypeManager tm, EditorFactory ef) {
    return getDiffContent(tm, ef, myRight);
  }

  private SimpleContent getDiffContent(FileTypeManager tm, EditorFactory ef, Entry e) {
    FileType t = tm.getFileTypeByFileName(e.getName());
    return new SimpleContent(getContentOf(e), t, ef);
  }

  private String getContentOf(Entry e) {
    return new String(e.getContent().getBytes());
  }
}
