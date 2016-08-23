/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionItem;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.JBColor;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.TableCell;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
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
    table.setModel(new MyModel(Collections.<RevisionItem>emptyList(), Collections.<RevisionItem, Period>emptyMap()));

    table.setTableHeader(null);
    table.setShowGrid(false);
    table.setRowMargin(0);
    table.getColumnModel().setColumnMargin(0);

    table.resetDefaultFocusTraversalKeys();

    table.setDefaultRenderer(Object.class, new MyCellRenderer(table));

    table.getEmptyText().setText(VcsBundle.message("history.empty"));

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
    Set<Long> sel = new THashSet<>();
    MyModel m = (MyModel)table.getModel();
    for (int i : table.getSelectedRows()) {
      if (i >= m.getRowCount()) continue;
      sel.add(m.getValueAt(i, 0).revision.getChangeSetId());
    }

    List<RevisionItem> newRevs = model.getRevisions();

    Date today = new Date();

    Map<RevisionItem, Period> periods = new THashMap<>();
    for (int i = 0; i < newRevs.size(); i++) {
      RevisionItem each = newRevs.get(i);
      boolean recent = today.getTime() - each.revision.getTimestamp() < 1000 * 60 * 60 * RECENT_PERIOD;
      if (recent) {
        if (i == 0) {
          periods.put(each, Period.RECENT);
        }
      }
      else {
        periods.put(each, periods.isEmpty() ? Period.OLD : Period.OLDER);
        break;
      }
    }

    table.setModel(new MyModel(newRevs, periods));

    for (int i = 0; i < newRevs.size(); i++) {
      RevisionItem r = newRevs.get(i);
      if (sel.contains(r.revision.getChangeSetId())) {
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
    OLDER(LocalHistoryBundle.message("revisions.table.period.older")),
    OLD(LocalHistoryBundle.message("revisions.table.period.old"));

    private final String myDisplayString;

    private Period(String displayString) {
      myDisplayString = displayString;
    }

    public String getDisplayString() {
      return myDisplayString;
    }
  }

  public static class MyModel extends AbstractTableModel {
    private final List<RevisionItem> myRevisions;
    private final Map<RevisionItem, Period> myPeriods;

    public MyModel(List<RevisionItem> revisions, Map<RevisionItem, Period> periods) {
      myRevisions = revisions;
      myPeriods = periods;
    }

    public int getColumnCount() {
      return 1;
    }

    public int getRowCount() {
      return myRevisions.size();
    }

    public RevisionItem getValueAt(int rowIndex, int columnIndex) {
      return myRevisions.get(rowIndex);
    }

    public Period getPeriod(RevisionItem r) {
      return myPeriods.get(r);
    }
  }

  public static class MyCellRenderer implements TableCellRenderer {
    private static final Color USER_LABEL_COLOR = new JBColor(new Color(230, 230, 250), new Color(89, 96, 74));
    private static final Insets BORDER_INSETS = new Insets(2, 5, 2, 5);

    private final DefaultTableCellRenderer myTemplate = new DefaultTableCellRenderer();

    private final JPanel myWrapperPanel = new JPanel();
    private final JPanel myItemPanel = new JPanel();

    private final MyBorder myBorder = new MyBorder(BORDER_INSETS);
    private final SeparatorWithText myPeriodLabel = new SeparatorWithText();

    private final JBLabel myDateLabel = new JBLabel();

    private final JBLabel myFilesCountLabel = new JBLabel();
    private final JBLabel myTitleLabel = new JBLabel();

    private final JPanel myLabelPanel = new JPanel();
    private final MyLabelContainer myLabelContainer = new MyLabelContainer();
    private final JBLabel myLabelLabel = new JBLabel();

    private final ExpandableItemsHandler<TableCell> myToolTipHandler;

    public MyCellRenderer(JBTable table) {
      myToolTipHandler = table.getExpandableItemsHandler();
      JPanel headersPanel = new JPanel(new BorderLayout());
      headersPanel.setOpaque(false);
      headersPanel.add(myPeriodLabel, BorderLayout.NORTH);
      headersPanel.add(myLabelPanel, BorderLayout.CENTER);

      myLabelContainer.add(myLabelLabel);

      myLabelPanel.setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension size = myLabelContainer.getPreferredSize();
          JBInsets.addTo(size, parent.getInsets());
          return size;
        }

        @Override
        public void layoutContainer(Container parent) {
          Insets i = parent.getInsets();

          int pw = parent.getWidth() - i.left - i.right;

          Dimension pref = myLabelContainer.getPreferredSize();
          int w = Math.min(pw, pref.width);
          int h = pref.height;

          int x = i.left + pw - w;
          int y = i.top;

          myLabelContainer.setBounds(x, y, w, h);
        }
      });
      myLabelPanel.setOpaque(false);
      myLabelPanel.add(myLabelContainer);

      final JPanel layoutPanel = new JPanel(new BorderLayout());
      layoutPanel.setOpaque(false);

      layoutPanel.add(headersPanel, BorderLayout.NORTH);
      layoutPanel.add(myItemPanel, BorderLayout.CENTER);

      myWrapperPanel.setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          return layoutPanel.getPreferredSize();
        }

        @Override
        public void layoutContainer(Container parent) {
          Dimension size = parent.getSize();
          Insets i = parent.getInsets();
          Dimension pref = layoutPanel.getPreferredSize();
          layoutPanel.setBounds(i.left, i.top, size.width - i.left - i.right, pref.height);
        }
      });
      myWrapperPanel.add(layoutPanel);

      myItemPanel.setBorder(myBorder);
      myItemPanel.setLayout(new BorderLayout());
      JPanel north = new JPanel(new BorderLayout());
      north.setOpaque(false);
      north.add(myDateLabel, BorderLayout.WEST);
      north.add(myFilesCountLabel, BorderLayout.EAST);

      JPanel south = new JPanel(new BorderLayout());
      south.add(myTitleLabel, BorderLayout.CENTER);
      south.setOpaque(false);
      myItemPanel.add(north, BorderLayout.NORTH);
      myItemPanel.add(south, BorderLayout.SOUTH);

      myLabelLabel.setBorder(new EmptyBorder(0, 5, 1, 5));
      myLabelPanel.setBorder(new MyBorder(new Insets(4, 20, 3, 1)));

      myWrapperPanel.setOpaque(false);
      myItemPanel.setOpaque(true);

      myWrapperPanel.setBackground(table.getBackground());

      myDateLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
      myFilesCountLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
      myLabelLabel.setComponentStyle(UIUtil.ComponentStyle.REGULAR);
      myTitleLabel.setComponentStyle(UIUtil.ComponentStyle.REGULAR);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) return myWrapperPanel; // null erroneously comes from JPanel.getAccessibleChild

      RevisionItem r = (RevisionItem)value;
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

      myDateLabel.setText(ensureString(DateFormatUtil.formatPrettyDateTime(r.revision.getTimestamp())));
      myFilesCountLabel.setText(ensureString(labelsAndColor.filesCount));

      myTitleLabel.setFont(myTitleLabel.getFont().deriveFont(labelsAndColor.isNamed ? Font.BOLD : Font.PLAIN));
      myTitleLabel.setText(ensureString(labelsAndColor.title));

      JComponent orig = (JComponent)myTemplate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (labelsAndColor.label == null) {
        myLabelPanel.setVisible(false);
      }
      else {
        myLabelPanel.setVisible(true);
        myLabelLabel.setBackground(labelsAndColor.label.second);
        myLabelContainer.set(labelsAndColor.label.second);
        myLabelLabel.setText(ensureString(labelsAndColor.label.first));
      }

      Color fg = orig.getForeground();
      Color bg = isSelected && !table.isFocusOwner() ? UIUtil.getListUnfocusedSelectionBackground() : orig.getBackground();

      myDateLabel.setForeground(isSelected ? fg : JBColor.GRAY);
      myFilesCountLabel.setForeground(myDateLabel.getForeground());
      myTitleLabel.setForeground(isSelected || labelsAndColor.isNamed ? fg : JBColor.DARK_GRAY);

      myItemPanel.setBackground(bg);

      myWrapperPanel.doLayout();
      int height = myWrapperPanel.getPreferredSize().height;
      //table.setRowHeight causes extra repaint of the table, so we try to avoid it.
      if (table.getRowHeight(row) != height && height > 0) {
        table.setRowHeight(row, height);
      }

      return myWrapperPanel;
    }

    private String ensureString(String s) {
      return StringUtil.isEmpty(s) ? " " : s;
    }

    private LabelsAndColor getLabelsAndColor(RevisionItem item) {
      Revision r = item.revision;

      final Pair<List<String>, Integer> affected = r.getAffectedFileNames();

      String title = r.getChangeSetName();
      boolean named = title != null;
      if (title == null) {
        title = StringUtil.join(affected.first, ", ");
        if (affected.first.size() < affected.second) title += "...";
      }

      String filesCount = StringUtil.pluralize(LocalHistoryBundle.message("revisions.table.filesCount", affected.second), affected.second);

      Pair<String, Color> label = null;
      if (!item.labels.isEmpty()) {
        Revision first = item.labels.getFirst();
        label = Pair.create(first.getLabel(), first.getLabelColor() == -1 ? USER_LABEL_COLOR : new Color(first.getLabelColor()));
      }

      return new LabelsAndColor(named, title, filesCount, label);
    }

    private static class LabelsAndColor {
      final boolean isNamed;
      final String title;
      final String filesCount;
      final Pair<String, Color> label;

      private LabelsAndColor(boolean isNamed, String title, String filesCount, Pair<String, Color> label) {
        this.isNamed = isNamed;
        this.title = title;
        this.filesCount = filesCount;
        this.label = label;
      }
    }

    private static class MyBorder extends EmptyBorder {
      private boolean isLast;

      private MyBorder(Insets insets) {
        super(insets);
      }

      public void set(boolean isLast) {
        this.isLast = isLast;
      }

      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setColor(UIUtil.getBorderColor());
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{1}, 1));
        g2d.drawLine(x, y, x + width, y);
        if (isLast) {
          g2d.drawLine(x, y + height - 1, x + width, y + height - 1);
        }
        g2d.dispose();
      }
    }

    private static class MyLabelContainer extends JPanel {
      private MyLabelContainer() {
        super(new BorderLayout());
      }

      public void set(Color c) {
        setBackground(c);
      }

      @Override
      public Dimension getMinimumSize() {
        return super.getMinimumSize();
      }

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(getBackground());
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight() - 2, getHeight() - 2);

        g2d.setColor(getBackground().darker());
        g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight() - 2, getHeight() - 2);
        g2d.dispose();
      }
    }
  }
}
