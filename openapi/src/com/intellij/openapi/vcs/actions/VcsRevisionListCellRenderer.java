package com.intellij.openapi.vcs.actions;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.vcs.history.VcsFileRevision;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class VcsRevisionListCellRenderer extends ColoredListCellRenderer {
  public static final DateFormat DATE_FORMAT = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT);

  protected void customizeCellRenderer(
    JList list,
    Object value,
    int index,
    boolean selected,
    boolean hasFocus
    ) {

    append(getRevisionString(((VcsFileRevision)value)),
      SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
  }

  private String getRevisionString(final VcsFileRevision revision) {
    final StringBuffer result = new StringBuffer();
    result.append(revision.getRevisionNumber().asString());
    final String branchName = revision.getBranchName();
    if (branchName != null && branchName.length() > 0) {
      result.append("(");
      result.append(branchName);
      result.append(")");
    }
    result.append(" ");
    result.append(DATE_FORMAT.format(revision.getRevisionDate()));
    result.append(" ");
    result.append(revision.getAuthor());
    return  result.toString();
  }
}
