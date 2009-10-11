/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.integration.ui.views.table;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.HistoryDialogModel;

import javax.swing.table.AbstractTableModel;

public class RevisionsTableModel extends AbstractTableModel {
  private final HistoryDialogModel myModel;

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
    if (column == 0) return LocalHistoryBundle.message("revisions.table.date");
    if (column == 1) return LocalHistoryBundle.message("revisions.table.revision");
    if (column == 2) return LocalHistoryBundle.message("revisions.table.action");
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
