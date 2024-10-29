// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.views;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionItem;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.JBColor;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.FilteringTableModel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.List;
import java.util.*;

public final class RevisionsList {
  public static final int RECENT_PERIOD = 12;
  private final JBTable table;
  private final JComponent component;
  private volatile Set<Long> filteredRevisions;

  public RevisionsList(SelectionListener l) {
    table = new JBTable();
    setModel(new MyModel(Collections.emptyList(), Collections.emptyMap()));

    table.setTableHeader(null);
    table.setShowGrid(false);
    table.setRowMargin(0);
    table.getColumnModel().setColumnMargin(0);

    table.resetDefaultFocusTraversalKeys();

    table.setDefaultRenderer(Object.class, new MyCellRenderer(table));

    table.getEmptyText().setText(VcsBundle.message("history.empty"));

    addSelectionListener(l);

    CopyProvider copyProvider = new MyCellRenderer.MyCopyProvider(table);
    component = UiDataProvider.wrapComponent(table, sink -> {
      sink.set(PlatformDataKeys.COPY_PROVIDER, copyProvider);
    });
  }

  public @NotNull JComponent getComponent() {
    return component;
  }

  public boolean isEmpty() {
    return table.isEmpty();
  }

  public void moveSelection(boolean fwd) {
    int index = table.getSelectionModel().getLeadSelectionIndex();
    int count = table.getRowCount();
    int newIdx = (count + index + (fwd ? 1 : -1)) % count;
    table.getSelectionModel().setSelectionInterval(newIdx, newIdx);
  }

  private void addSelectionListener(@NotNull SelectionListener listener) {
    table.getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;

      ListSelectionModel sm = table.getSelectionModel();
      int selectedRow1 = sm.getMinSelectionIndex();
      int selectedRow2 = sm.getMaxSelectionIndex();

      FilteringTableModel<?> model = getFilteringModel();
      int origRow1 = model.getOriginalIndex(selectedRow1);
      int origRow2 = model.getOriginalIndex(selectedRow2);
      listener.revisionsSelected(origRow1, origRow2);
    });
  }

  public void updateData(HistoryDialogModel model) {
    Set<Long> sel = new HashSet<>();
    TableModel tm = table.getModel();
    for (int i : table.getSelectedRows()) {
      if (i >= tm.getRowCount()) continue;
      sel.add(((RevisionItem)tm.getValueAt(i, 0)).revision.getChangeSetId());
    }

    List<RevisionItem> newRevs = model.getRevisions();

    Date today = new Date();

    Map<RevisionItem, Period> periods = new HashMap<>();
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

    setModel(new MyModel(newRevs, periods));

    FilteringTableModel<?> fm = getFilteringModel();
    for (int i = 0; i < fm.getRowCount(); i++) {
      RevisionItem r = (RevisionItem)fm.getValueAt(i, 0);
      if (sel.contains(r.revision.getChangeSetId())) {
        table.getSelectionModel().addSelectionInterval(i, i);
      }
    }
    if (table.getSelectionModel().isSelectionEmpty()) {
      table.getSelectionModel().addSelectionInterval(0, 0);
    }
  }

  private void setModel(MyModel newModel) {
    FilteringTableModel<RevisionItem> fModel = new FilteringTableModel<>(newModel, RevisionItem.class);
    table.setModel(fModel);
    fModel.setFilter(this::filterRevision);
  }

  private boolean filterRevision(RevisionItem r) {
    if (filteredRevisions == null) return true;
    return filteredRevisions.contains(r.revision.getChangeSetId());
  }

  public void setFilteredRevisions(Set<Long> filtered) {
    filteredRevisions = filtered;
    List<Object> sel = storeSelection();
    getFilteringModel().refilter();
    restoreSelection(sel);
  }

  private void restoreSelection(@NotNull List<Object> sel) {
    ListSelectionModel sm = table.getSelectionModel();
    sm.clearSelection();
    for (Object o : sel) {
      int idx = -1;
      for (int i = 0, e = table.getModel().getRowCount(); i < e; ++i) {
        if (table.getModel().getValueAt(i, 0) == o) {
          idx = i;
          break;
        }
      }
      sm.addSelectionInterval(idx, idx);
    }
    if (sm.isSelectionEmpty() && table.getRowCount() > 0) {
      sm.setSelectionInterval(0, 0);
    }
    sm.setValueIsAdjusting(false);
  }

  private @NotNull List<Object> storeSelection() {
    ListSelectionModel sm = table.getSelectionModel();
    sm.setValueIsAdjusting(true);
    List<Object> sel = new ArrayList<>();
    for (int index : sm.getSelectedIndices()) {
      sel.add(table.getModel().getValueAt(index, 0));
    }
    return sel;
  }

  private FilteringTableModel<?> getFilteringModel() {
    return (FilteringTableModel<?>)table.getModel();
  }

  private static MyModel getMyModel(@NotNull JTable table) {
    return ((MyModel)((FilteringTableModel<?>)table.getModel()).getOriginalModel());
  }

  public interface SelectionListener {
    void revisionsSelected(int first, int last);
  }

  private enum Period {
    RECENT(LocalHistoryBundle.message("revisions.table.period.recent", RECENT_PERIOD)),
    OLDER(LocalHistoryBundle.message("revisions.table.period.older")),
    OLD(LocalHistoryBundle.message("revisions.table.period.old"));

    private final @NlsContexts.Label String myDisplayString;

    Period(@NlsContexts.Label String displayString) {
      myDisplayString = displayString;
    }

    public @NlsContexts.Label String getDisplayString() {
      return myDisplayString;
    }
  }

  private static final class MyModel extends AbstractTableModel {
    private final List<? extends RevisionItem> myRevisions;
    private final Map<RevisionItem, Period> myPeriods;

    MyModel(List<? extends RevisionItem> revisions, Map<RevisionItem, Period> periods) {
      myRevisions = revisions;
      myPeriods = periods;
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public int getRowCount() {
      return myRevisions.size();
    }

    @Override
    public RevisionItem getValueAt(int rowIndex, int columnIndex) {
      return myRevisions.get(rowIndex);
    }

    public Period getPeriod(RevisionItem r) {
      return myPeriods.get(r);
    }
  }

  public static final class MyCellRenderer implements TableCellRenderer {
    private static final Color USER_LABEL_COLOR = new JBColor(new Color(230, 230, 250), new Color(89, 96, 74));
    private static final Insets BORDER_INSETS = new Insets(2, 5, 2, 5);

    private final DefaultTableCellRenderer myTemplate = new DefaultTableCellRenderer();

    private final MyWrapperPanel myWrapperPanel = new MyWrapperPanel();
    private final JPanel myItemPanel = new JPanel();

    private final MyBorder myBorder = new MyBorder(BORDER_INSETS);
    private final SeparatorWithText myPeriodLabel = new SeparatorWithText();

    private final JBLabel myDateLabel = new JBLabel();

    private final JBLabel myFilesCountLabel = new JBLabel();
    private final JBLabel myTitleLabel = new JBLabel();

    private final JPanel myLabelPanel = new JPanel();
    private final MyLabelContainer myLabelContainer = new MyLabelContainer();
    private final JBLabel myLabelLabel = new JBLabel();

    public MyCellRenderer(@NotNull JBTable table) {
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

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) return myWrapperPanel; // null erroneously comes from JPanel.getAccessibleChild

      RevisionItem r = (RevisionItem)value;
      LabelsAndColor labelsAndColor = getLabelsAndColor(r);

      final Period p = getMyModel(table).getPeriod(r);
      if (p == null) {
        myPeriodLabel.setVisible(false);
      }
      else {
        myPeriodLabel.setVisible(true);
        myPeriodLabel.setCaption(p.getDisplayString());
      }

      myBorder.set(row == table.getModel().getRowCount() - 1);

      myDateLabel.setText(ensureString(DateFormatUtil.formatDateTime(r.revision.getTimestamp())));
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
      Color bg = isSelected && !table.isFocusOwner() ? UIUtil.getListSelectionBackground(false) : orig.getBackground();

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

    private static @NlsContexts.Label String ensureString(@NlsContexts.Label String s) {
      return StringUtil.isEmpty(s) ? " " : s;
    }

    private static LabelsAndColor getLabelsAndColor(@NotNull RevisionItem item) {
      Revision r = item.revision;

      final Pair<List<String>, Integer> affected = r.getAffectedFileNames();

      String title = r.getChangeSetName();
      boolean named = title != null;
      if (title == null) {
        title = StringUtil.join(affected.first, ", ");
        if (affected.first.size() < affected.second) title += "...";
      }

      String filesCount = LocalHistoryBundle.message("revisions.table.filesCount", affected.second);

      Pair<@NlsContexts.Label String, Color> label = null;
      if (!item.labels.isEmpty()) {
        Revision first = item.labels.getFirst();
        label = Pair.create(first.getLabel(), first.getLabelColor() == -1 ? USER_LABEL_COLOR : new Color(first.getLabelColor()));
      }

      return new LabelsAndColor(named, title, filesCount, label);
    }

    /**
     * Given each item in the list of revisions contains multiple strings,
     * we customize the containing panel to expose an accessible name
     * combining all these strings so that screen readers announce these
     * strings as the active list item changes.
     */
    private final class MyWrapperPanel extends JPanel {
      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = new AccessibleMyWrapperPanel();
        }
        return accessibleContext;
      }

      protected final class AccessibleMyWrapperPanel extends AccessibleJPanel {
        @Override
        public AccessibleRole getAccessibleRole() {
          return AccessibleRole.LABEL;
        }

        @Override
        public String getAccessibleName() {
          if (myPeriodLabel.isVisible()) {
            return AccessibleContextUtil.getCombinedName(", ", myPeriodLabel, myTitleLabel, myFilesCountLabel, myDateLabel);
          }
          else {
            return AccessibleContextUtil.getCombinedName(", ", myTitleLabel, myFilesCountLabel, myDateLabel);
          }
        }
      }
    }

    private static final class LabelsAndColor {
      final boolean isNamed;
      final @NlsContexts.Label String title;
      final @NlsContexts.Label String filesCount;
      final Pair<@NlsContexts.Label String, Color> label;

      private LabelsAndColor(boolean isNamed,
                             @NlsContexts.Label String title,
                             @NlsContexts.Label String filesCount,
                             Pair<@NlsContexts.Label String, Color> label) {
        this.isNamed = isNamed;
        this.title = title;
        this.filesCount = filesCount;
        this.label = label;
      }
    }

    private static final class MyBorder extends EmptyBorder {
      private boolean isLast;

      private MyBorder(Insets insets) {
        super(insets);
      }

      public void set(boolean isLast) {
        this.isLast = isLast;
      }

      @Override
      public void paintBorder(Component c, @NotNull Graphics g, int x, int y, int width, int height) {
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setColor(JBColor.border());
        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{1}, 1));
        g2d.drawLine(x, y, x + width, y);
        if (isLast) {
          g2d.drawLine(x, y + height - 1, x + width, y + height - 1);
        }
        g2d.dispose();
      }
    }

    private static final class MyLabelContainer extends JPanel {
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
      protected void paintComponent(@NotNull Graphics g) {
        Graphics2D g2d = (Graphics2D)g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(getBackground());
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight() - 2, getHeight() - 2);

        g2d.setColor(getBackground().darker());
        g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight() - 2, getHeight() - 2);
        g2d.dispose();
      }
    }

    private static final class MyCopyProvider implements CopyProvider {
      private final @NotNull JBTable myTable;

      private MyCopyProvider(@NotNull JBTable table) {
        myTable = table;
      }

      @Override
      public void performCopy(@NotNull DataContext dataContext) {
        TableModel model = myTable.getModel();

        StringBuilder sb = new StringBuilder();
        for (int row : myTable.getSelectedRows()) {
          RevisionItem r = (RevisionItem)model.getValueAt(row, 0);

          LabelsAndColor labelsAndColor = getLabelsAndColor(r);
          String time = DateFormatUtil.formatDateTime(r.revision.getTimestamp());
          String title = labelsAndColor.title;
          String filesCount = labelsAndColor.filesCount;
          if (sb.length() != 0) sb.append("\n");
          sb.append(time).append(", ")
            .append(filesCount).append(": ")
            .append(title);
        }
        CopyPasteManager.getInstance().setContents(new TextTransferable(sb));
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isCopyEnabled(@NotNull DataContext dataContext) {
        return myTable.getSelectedRowCount() > 0;
      }

      @Override
      public boolean isCopyVisible(@NotNull DataContext dataContext) {
        return true;
      }
    }
  }
}
