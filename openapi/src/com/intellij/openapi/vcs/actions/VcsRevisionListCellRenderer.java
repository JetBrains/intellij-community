package com.intellij.openapi.vcs.actions;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.vcs.history.VcsFileRevision;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class VcsRevisionListCellRenderer extends ColoredListCellRenderer {
  private static final DateFormat DATE_FORMAT = SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT);

  protected void customizeCellRenderer(
    JList list,
    Object value,
    int index,
    boolean selected,
    boolean hasFocus
    ) {
    final VcsFileRevision revision = ((VcsFileRevision)value);
    append(revision.getRevisionNumber().asString() + " " + DATE_FORMAT.format(revision.getRevisionDate()) + " " + revision.getAuthor(),
      SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
  }
}
