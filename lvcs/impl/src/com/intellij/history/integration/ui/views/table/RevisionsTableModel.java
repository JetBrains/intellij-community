package com.intellij.history.integration.ui.views.table;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.ui.models.HistoryDialogModel;

import javax.swing.table.AbstractTableModel;

public class RevisionsTableModel extends AbstractTableModel {
  private HistoryDialogModel myModel;

  public RevisionsTableModel(HistoryDialogModel m) {
    myModel = m;
  }

  public int getColumnCount() {
    return 3;
  }

  public int getRowCount() {
    return myModel.getRevisions().size();
  }

  @Override
  public String getColumnName(int column) {
    if (column == 0) return "Date";
    if (column == 1) return "Revision";
    if (column == 2) return "Action";
    return null;
  }

  public Object getValueAt(int row, int column) {
    Revision r = getRevisionAt(row);
    if (column == 0) return FormatUtil.formatTimestamp(r.getTimestamp());
    if (column == 1) return r.getName();
    if (column == 2) return r.getCauseChangeName();
    return null;
  }

  public Revision getRevisionAt(int row) {
    return myModel.getRevisions().get(row);
  }
}
