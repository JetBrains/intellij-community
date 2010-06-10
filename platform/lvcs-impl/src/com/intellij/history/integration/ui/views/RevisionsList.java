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

package com.intellij.history.integration.ui.views;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

public class RevisionsList {
  public static final int RECENT_PERIOD = 12;
  private final JBTable table;

  public RevisionsList(SelectionListener l) {
    table = new JBTable();
    table.setModel(new MyModel(Collections.<Revision>emptyList(), Collections.EMPTY_MAP));

    table.setDefaultRenderer(Object.class, new MyCellRenderer());
    table.setTableHeader(null);
    table.setShowGrid(false);
    table.setRowMargin(0);
    table.getColumnModel().setColumnMargin(0);

    addSelectionListener(l);
  }

  public JComponent getComponent() {
    return table;
  }

  private void addSelectionListener(SelectionListener listener) {
    final SelectionListener l = listener;

    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      private int mySelectedRow1 = 0;
      private int mySelectedRow2 = 0;
      private final SelectionListener mySelectionListener = l;

      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        ListSelectionModel sm = table.getSelectionModel();
        mySelectedRow1 = sm.getMinSelectionIndex();
        mySelectedRow2 = sm.getMaxSelectionIndex();

        mySelectionListener.revisionsSelected(mySelectedRow1, mySelectedRow2);
      }
    });
  }

  public void updateData(HistoryDialogModel model) {
    Set<Long> sel = new THashSet<Long>();
    MyModel m = (MyModel)table.getModel();
    for (int i : table.getSelectedRows()) {
      sel.add(m.getValueAt(i, 0).getChangeSetId());
    }

    List<Revision> newRevs = model.getRevisions();

    Date today = new Date();

    Map<Revision, Period> periods = new THashMap<Revision, Period>();
    List<Integer> indices = new ArrayList<Integer>();
    for (int i = 0; i < newRevs.size(); i++) {
      Revision each = newRevs.get(i);
      boolean recent = today.getTime() - each.getTimestamp() < 1000 * 60 * 60 * RECENT_PERIOD;
      if (recent) {
        if (i == 0) {
          periods.put(each, Period.RECENT);
          indices.add(i);
        }
      }
      else {
        periods.put(each, Period.OLDER);
        indices.add(i);
        break;
      }
    }

    table.setModel(new MyModel(newRevs, periods));

    final MyCellRenderer template = new MyCellRenderer();
    table.setRowHeight(template.getPrefHeight());
    for (Integer each : indices) {
      table.setRowHeight(each, template.getPrefHeightWithPeriod());
    }

    for (int i = 0; i < newRevs.size(); i++) {
      Revision r = newRevs.get(i);
      if (sel.contains(r.getChangeSetId())) {
        table.getSelectionModel().addSelectionInterval(i, i);
      }
    }
    if (table.getSelectionModel().isSelectionEmpty()) {
      table.getSelectionModel().addSelectionInterval(0, 0);
    }
  }

  public interface SelectionListener {
    void revisionsSelected(int first, int last);
  }

  private enum Period {
    RECENT(LocalHistoryBundle.message("revisions.table.period.recent", RECENT_PERIOD)),
    OLDER(LocalHistoryBundle.message("revisions.table.period.older"));

    private final String myDisplayString;

    private Period(String displayString) {
      myDisplayString = displayString;
    }

    public String getDisplayString() {
      return myDisplayString;
    }
  }

  public static class MyModel extends AbstractTableModel {
    private final List<Revision> myRevisions;
    private final Map<Revision, Period> myPeriods;

    public MyModel(List<Revision> revisions, Map<Revision, Period> periods) {
      myRevisions = revisions;
      myPeriods = periods;
    }

    public int getColumnCount() {
      return 1;
    }

    public int getRowCount() {
      return myRevisions.size();
    }

    public Revision getValueAt(int rowIndex, int columnIndex) {
      return myRevisions.get(rowIndex);
    }

    public Period getPeriod(Revision r) {
      return myPeriods.get(r);
    }
  }

  public static class MyCellRenderer implements TableCellRenderer {
    private static final Color BLUE = new Color(230, 230, 250);
    private static final Color PINK = new Color(255, 235, 205);

    private final DefaultTableCellRenderer myTemplate = new DefaultTableCellRenderer();

    private final JPanel myWrapperPanel = new JPanel();
    private final JPanel myItemPanel = new JPanel();

    private final MyBorder myBorder = new MyBorder();

    private final SeparatorWithText myPeriodLabel = new SeparatorWithText();

    private final JLabel myDateLabel = new JLabel(" ");
    private final JLabel myFilesNumberLabel = new JLabel(" ");
    private final JLabel myTitleLabel = new JLabel(" ");


    public MyCellRenderer() {
      myWrapperPanel.setLayout(new BorderLayout());
      myWrapperPanel.add(myPeriodLabel, BorderLayout.NORTH);
      myWrapperPanel.add(myItemPanel, BorderLayout.CENTER);

      myItemPanel.setBorder(myBorder);
      myItemPanel.setLayout(new BorderLayout());
      JPanel north = new JPanel(new BorderLayout());
      north.add(myDateLabel, BorderLayout.WEST);
      north.add(myFilesNumberLabel, BorderLayout.EAST);
      myItemPanel.add(north, BorderLayout.NORTH);
      myItemPanel.add(myTitleLabel, BorderLayout.CENTER);

      myWrapperPanel.setOpaque(false);
      north.setOpaque(false);
      myItemPanel.setOpaque(true);

      final Font f = myDateLabel.getFont();
      final Font smallFont = f.deriveFont(Math.max(8.f, f.getSize() * 0.8f));
      myDateLabel.setFont(smallFont);
    }

    public int getPrefHeight() {
      myItemPanel.doLayout();
      return myItemPanel.getPreferredSize().height;
    }

    public int getPrefHeightWithPeriod() {
      myPeriodLabel.setCaption("a");
      myWrapperPanel.doLayout();
      return myWrapperPanel.getPreferredSize().height;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) return myWrapperPanel; // null erroneously comes from JPanel.getAccessibleChild

      Revision r = (Revision)value;
      LabelsAndColor labelsAndColor = getLabelsAndColor(r);

      final Period p = ((MyModel)table.getModel()).getPeriod(r);
      if (p == null) {
        myPeriodLabel.setVisible(false);
      }
      else {
        myPeriodLabel.setVisible(true);
        myPeriodLabel.setCaption(p.getDisplayString());
      }

      myBorder.set(row == table.getModel().getRowCount() - 1);

      myDateLabel.setText(ensureString(StringUtil.formatDate(r.getTimestamp(), FormatUtil.FORMAT)));
      String text = ensureString(labelsAndColor.titleText);
      if (r.getChangeSetName() != null) {
        text = "<html><b>" + text + "</b></html>";
      }
      myTitleLabel.setText(text);
      myFilesNumberLabel.setText(ensureString(labelsAndColor.filesNumberText));

      JComponent orig = (JComponent)myTemplate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      Color bg = isSelected ? orig.getBackground() : labelsAndColor.color;
      Color fg = isSelected ? orig.getForeground() : Color.GRAY;
      myItemPanel.setBackground(bg);
      myDateLabel.setForeground(fg);
      myFilesNumberLabel.setForeground(fg);

      myTitleLabel.setForeground(orig.getForeground());
      myWrapperPanel.setBackground(table.getBackground());

      return myWrapperPanel;
    }

    private String ensureString(String s) {
      return StringUtil.isEmpty(s) ? " " : s;
    }

    private LabelsAndColor getLabelsAndColor(Revision r) {
      Color color = null;
      String title = r.getLabel();
      if (title == null) {
        title = r.getChangeSetName();
        if (title != null) color = PINK;
      }
      else {
        color = BLUE;
      }

      final Pair<List<String>, Integer> affected = r.getAffectedFileNames();

      String filesNumber = "";
      if (affected.second > 0) {
        String message = LocalHistoryBundle.message("revisions.table.filesCount", affected.second);
        filesNumber = StringUtil.pluralize(message, affected.second);
      }
      if (title == null) {
        title = StringUtil.join(affected.first, ", ");
        if (affected.first.size() < affected.second) title += "...";
      }
      if (r.getLabelColor() != -1) color = new Color(r.getLabelColor());
      return new LabelsAndColor(title, filesNumber, color);
    }

    private static class LabelsAndColor {
      String titleText;
      String filesNumberText;
      Color color;

      private LabelsAndColor(String titleText, String filesNumberText, Color color) {
        this.titleText = titleText;
        this.filesNumberText = filesNumberText;
        this.color = color;
      }
    }

    private static class MyBorder extends EmptyBorder {
      private boolean isLast;

      private MyBorder() {
        super(2, 2, 2, 2);
      }

      public void set(boolean isLast) {
        this.isLast = isLast;
      }

      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(UIUtil.getBorderSeparatorColor());
        Graphics2D g2d = (Graphics2D)g;
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{1}, 1));
        g.drawLine(x, y, x + width, y);
        if (isLast) {
          g.drawLine(x, y + height - 1, x + width, y + height - 1);
        }
      }
    }
  }
}
