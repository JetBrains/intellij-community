// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginHostsConfigurable implements Configurable.NoScroll, Configurable {
  private final ListTableModel<UrlInfo> myModel = new ListTableModel<UrlInfo>() {
    @Override
    public void addRow() {
      addRow(new UrlInfo(""));
    }
  };

  private final AnimatedIcon.Default myAnimatedIcon = new AnimatedIcon.Default();

  private final JBTable myTable = new JBTable(myModel) {
    @Override
    protected void paintComponent(@NotNull Graphics g) {
      super.paintComponent(g);

      Rectangle bounds = g.getClipBounds();
      int startRow = Math.max(0, rowAtPoint(bounds.getLocation()));
      int endRow = rowAtPoint(new Point(bounds.x + bounds.width, bounds.y + bounds.height));
      if (endRow == -1) {
        endRow = myModel.getRowCount() - 1;
      }

      int iconWidth = myAnimatedIcon.getIconWidth();
      int cellHeight = myAnimatedIcon.getIconHeight();
      int offset = JBUI.scale(5);
      int editingRow = getEditingRow();
      int selectedRow = getSelectedRow();
      Border border = UIUtil.getTableFocusCellHighlightBorder();

      for (int i = startRow; i <= endRow; i++) {
        UrlInfo item = myModel.getItem(i);
        if (item.progress && i != editingRow) {
          Rectangle rect = getCellRect(i, 0, true);
          int x = rect.x + rect.width - iconWidth - offset;
          int xx = 0;
          int yy = 0;
          int hh = 0;
          if (i == selectedRow) {
            Insets insets = border.getBorderInsets(null);
            xx = insets.right;
            yy = insets.top;
            hh = insets.top + insets.bottom + 1;
          }
          g.setColor(i == selectedRow ? getSelectionBackground() : getBackground());
          g.fillRect(x - offset, rect.y + yy, iconWidth + offset * 2 - xx, rect.height - hh);
          myAnimatedIcon.paintIcon(this, g, x, rect.y + (rect.height - cellHeight) / 2);
        }
      }
    }

    @Override
    public void editingCanceled(ChangeEvent e) {
      int row = getEditingRow();
      super.editingCanceled(e);
      if (row >= 0 && row < myModel.getRowCount() && StringUtil.isEmpty(myModel.getRowValue(row).name)) {
        myModel.removeRow(row);
      }
    }
  };

  @Nullable
  @Override
  public JComponent createComponent() {
    myModel.setColumnInfos(new ColumnInfo[]{new ColumnInfo<UrlInfo, String>("") {
      @Nullable
      @Override
      public String valueOf(UrlInfo info) {
        return info.name;
      }

      @Override
      public boolean isCellEditable(UrlInfo info) {
        return true;
      }

      @Override
      public void setValue(UrlInfo info, String value) {
        int row = myTable.getSelectedRow();
        if (StringUtil.isEmpty(value) && row >= 0 && row < myModel.getRowCount()) {
          myModel.removeRow(row);
        }
        else {
          info.name = correctRepositoryRule(value);
          validateRepositories(Collections.singletonList(info));
        }
      }
    }});

    myTable.getColumnModel().setColumnMargin(0);
    myTable.setShowColumns(false);
    myTable.setShowGrid(false);

    myTable.getEmptyText().setText(IdeBundle.message("update.no.update.hosts"));

    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myTable.setDefaultRenderer(Object.class, new ColoredTableCellRenderer() {
      final SimpleTextAttributes ERROR_ATTRIBUTES =
        new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DialogWrapper.ERROR_FOREGROUND_COLOR);

      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        if (row >= 0 && row < myModel.getRowCount()) {
          UrlInfo info = myModel.getRowValue(row);
          setBorder(null);
          setForeground(selected ? table.getSelectionForeground() : table.getForeground());
          setBackground(selected ? table.getSelectionBackground() : table.getBackground());
          append(info.name, info.errorTooltip != null && !info.progress ? ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }

      @Override
      protected SimpleTextAttributes modifyAttributes(SimpleTextAttributes attributes) {
        return attributes;
      }
    });

    DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
    editor.setClickCountToStart(1);
    myTable.setDefaultEditor(Object.class, editor);

    createValidatorHandler();

    return ToolbarDecorator.createDecorator(myTable).disableUpDownActions().createPanel();
  }

  private void createValidatorHandler() {
    MouseAdapter listener = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        showErrorPopup(e);
      }

      @Override
      public void mouseExited(MouseEvent event) {
        if (!myTable.contains(event.getX(), event.getY()) || myTable.rowAtPoint(event.getPoint()) == myTable.getEditingRow()) {
          hideErrorPopup();
        }
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        showErrorPopup(e);
      }
    };
    myTable.addMouseListener(listener);
    myTable.addMouseMotionListener(listener);
  }

  private JBPopup myErrorPopup;
  private JLabel myErrorLabel;

  private void showErrorPopup(@NotNull MouseEvent event) {
    int row = myTable.rowAtPoint(event.getPoint());
    if (row == -1 || row == myTable.getEditingRow()) {
      hideErrorPopup();
      return;
    }

    UrlInfo item = myModel.getItem(row);
    if (item.progress || item.errorTooltip == null) {
      hideErrorPopup();
      return;
    }

    if (myErrorPopup != null && myErrorPopup.isVisible() && myErrorPopup.getContent().getParent() != null) {
      myErrorLabel.setText(item.errorTooltip);
      showErrorPopup(row, true);
      return;
    }

    hideErrorPopup();

    myErrorLabel = new JLabel(item.errorTooltip);
    myErrorLabel.setOpaque(true);
    myErrorLabel.setBackground(JBUI.CurrentTheme.Validator.errorBackgroundColor());
    myErrorLabel.setBorder(ComponentValidator.getBorder());

    myErrorPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myErrorLabel, null)
      .setBorderColor(JBUI.CurrentTheme.Validator.errorBorderColor()).setShowShadow(false).createPopup();

    showErrorPopup(row, false);
  }

  private void showErrorPopup(int row, boolean update) {
    Rectangle cellRect = myTable.getCellRect(row, 0, false);
    Point location = new Point(cellRect.x + JBUI.scale(40), cellRect.y - myErrorLabel.getPreferredSize().height - JBUI.scale(4));
    SwingUtilities.convertPointToScreen(location, myTable);

    if (update) {
      myErrorPopup.pack(true, true);
      myErrorPopup.setLocation(location);
    }
    else {
      myErrorPopup.showInScreenCoordinates(myTable, location);
    }
  }

  @Override
  public void disposeUIResources() {
    hideErrorPopup();
  }

  private void hideErrorPopup() {
    if (myErrorPopup != null) {
      myErrorPopup.cancel();
      myErrorPopup = null;
      myErrorLabel = null;
    }
  }

  private void validateRepositories(@NotNull List<UrlInfo> urls) {
    List<UrlInfo> infos = new ArrayList<>();
    List<String> results = new ArrayList<>();

    for (UrlInfo info : urls) {
      info.progress = true;
      infos.add(info);
      results.add(null);
    }

    myTable.repaint();

    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Checking Plugins Repository...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (int i = 0, size = infos.size(); i < size; i++) {
          try {
            if (RepositoryHelper.loadPlugins(infos.get(i).name, indicator).size() == 0) {
              results.set(i, "No plugins found. Please check log file for possible errors.");
            }
          }
          catch (Exception ignore) {
            results.set(i, "Connection failed.");
          }
        }
      }

      @Override
      public void onSuccess() {
        finish(true);
      }

      @Override
      public void onCancel() {
        finish(false);
      }

      private void finish(boolean success) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (success) {
            for (int i = 0, size = infos.size(); i < size; i++) {
              UrlInfo info = infos.get(i);
              info.errorTooltip = results.get(i);
              info.progress = false;
            }
          }
          else {
            for (UrlInfo info : infos) {
              info.progress = false;
            }
          }

          myTable.repaint();
        }, ModalityState.any());
      }
    });
  }

  @Override
  public String getDisplayName() {
    return "Custom Plugin Repositories";
  }

  @Override
  public void reset() {
    List<UrlInfo> infos = new ArrayList<>();
    for (String host : UpdateSettings.getInstance().getStoredPluginHosts()) {
      infos.add(new UrlInfo(host));
    }
    myModel.setItems(infos);

    ApplicationManager.getApplication().invokeLater(() -> validateRepositories(infos), ModalityState.any());
  }

  @Override
  public boolean isModified() {
    List<String> hosts = new ArrayList<>();
    for (UrlInfo item : myModel.getItems()) {
      hosts.add(item.name);
    }
    return !UpdateSettings.getInstance().getStoredPluginHosts().equals(hosts);
  }

  @Override
  public void apply() throws ConfigurationException {
    List<String> list = UpdateSettings.getInstance().getStoredPluginHosts();
    list.clear();
    for (UrlInfo item : myModel.getItems()) {
      list.add(item.name);
    }
  }

  @NotNull
  private static String correctRepositoryRule(@NotNull String input) {
    if (VirtualFileManager.extractProtocol(input) == null) {
      return VirtualFileManager.constructUrl(URLUtil.HTTP_PROTOCOL, input);
    }
    return input;
  }

  private static class UrlInfo {
    String name;
    boolean progress;
    String errorTooltip;

    UrlInfo(@NotNull String name) {
      this.name = name;
    }
  }
}