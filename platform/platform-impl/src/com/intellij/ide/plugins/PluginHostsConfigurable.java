// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.cellvalidators.*;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginHostsConfigurable implements Configurable.NoScroll, Configurable {
  private final ListTableModel<UrlInfo> myModel = new ListTableModel<>() {
    @Override
    public void addRow() {
      addRow(new UrlInfo(""));
    }
  };

  private final AnimatedIcon.Default myAnimatedIcon = new AnimatedIcon.Default();
  private final Disposable myDisposable = Disposer.newDisposable();

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
      int offset = JBUIScale.scale(5);
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

    ExtendableTextField cellEditor = new ExtendableTextField();
    DefaultCellEditor editor = new StatefulValidatingCellEditor(cellEditor, myDisposable).
      withStateUpdater(vi -> ValidationUtils.setExtension(cellEditor, vi));
    editor.setClickCountToStart(1);
    myTable.setDefaultEditor(Object.class, editor);

    myTable.setDefaultRenderer(Object.class, new ValidatingTableCellRendererWrapper(new ColoredTableCellRenderer() {
        {
          setIpad(JBInsets.emptyInsets());}

        @Override
        protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
          if (row >= 0 && row < myModel.getRowCount()) {
            UrlInfo info = myModel.getRowValue(row);
            setForeground(selected ? table.getSelectionForeground() : table.getForeground());
            setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            append(info.name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }

        @Override
        protected SimpleTextAttributes modifyAttributes(SimpleTextAttributes attributes) {
          return attributes;
        }
      }).
      bindToEditorSize(cellEditor::getPreferredSize).
      withCellValidator((value, row, column) -> {
        if (row >= 0 && row < myModel.getRowCount()) {
          UrlInfo info = myModel.getRowValue(row);
          return info.errorTooltip == null || info.progress ? null : new ValidationInfo(info.errorTooltip);
        }
        else {
          return null;
        }
      }));

    new CellTooltipManager(myDisposable).
      withCellComponentProvider(CellComponentProvider.forTable(myTable)).
      installOn(myTable);

    return ToolbarDecorator.createDecorator(myTable).disableUpDownActions().createPanel();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myDisposable);
  }

  private void validateRepositories(@NotNull List<? extends UrlInfo> urls) {
    List<UrlInfo> infos = new ArrayList<>();
    List<@Nls String> results = new ArrayList<>();

    for (UrlInfo info : urls) {
      info.progress = true;
      infos.add(info);
      results.add(null);
    }

    myTable.repaint();

    ProgressManager.getInstance().run(new Task.Backgroundable(null, IdeBundle.message("progress.title.checking.plugins.repository"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (int i = 0, size = infos.size(); i < size; i++) {
          try {
            if (RepositoryHelper.loadPlugins(infos.get(i).name, null, indicator).isEmpty()) {
              results.set(i, IdeBundle.message("error.no.plugins.found"));
            }
          }
          catch (Exception ignore) {
            results.set(i, IdeBundle.message("error.connection.failed"));
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
    return IdeBundle.message("configurable.PluginHostsConfigurable.display.name");
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
    myTable.editingStopped(null);

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
    @NlsSafe String name;
    boolean progress;
    @Nls String errorTooltip;

    UrlInfo(@NotNull String name) {
      this.name = name;
    }
  }
}