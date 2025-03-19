package com.intellij.database.run.ui.table;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.ResultViewFactory;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.MouseGestureManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class TableResultViewFactory implements ResultViewFactory {
  public static final TableResultViewFactory TABLE_FACTORY = new TableResultViewFactory();

  @Override
  public @NotNull ResultView createResultView(@NotNull DataGrid resultPanel, @NotNull ActionGroup columnHeaderActions, @NotNull ActionGroup rowHeaderActions) {
    TableResultView table = new TableResultView(resultPanel, columnHeaderActions, rowHeaderActions);
    table.getEmptyText().setText(""); //DatabaseMessages.message("table.result.data.loading"));
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setCellSelectionEnabled(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setBackground(resultPanel.getColorsScheme().getDefaultBackground());
    // fixes missing (or plain white) grid on mac
    table.setShowGrid(true);

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
          int rowIndex = table.isTransposed() ? table.columnAtPoint(e.getPoint()) : table.rowAtPoint(e.getPoint());
          int columnIndex = table.isTransposed() ? table.rowAtPoint(e.getPoint()) : table.columnAtPoint(e.getPoint());
          if (rowIndex >= 0 && columnIndex >= 0 && !table.isCellSelected(rowIndex, columnIndex)) {
            int modelRow = table.getRawIndexConverter().row2Model().applyAsInt(rowIndex);
            int modelColumn = table.getRawIndexConverter().column2Model().applyAsInt(columnIndex);
            SelectionModel<GridRow, GridColumn> selectionModel = resultPanel.getSelectionModel();
            selectionModel.setSelection(ModelIndexSet.forRows(resultPanel, modelRow), ModelIndexSet.forColumns(resultPanel, modelColumn));
          }
        }
      }
    });

    return table;
  }

  @Override
  public @NotNull JComponent wrap(@NotNull DataGrid grid, @NotNull ResultView resultView) {
    TableResultView table = (TableResultView)resultView;
    Ref<Boolean> myTransparentHeaderBg = new Ref<>(false);
    MyCornerToolsComponent cornerComponent = new MyCornerToolsComponent(grid.getProject()) {
      @Override
      TableResultView getTable() {
        return table;
      }

      @Override
      DataGrid getGrid() {
        return grid;
      }

      @Override
      public Color getBackground() {
        return myTransparentHeaderBg.get() ? getTable().getBackground() : DataGridUIUtil.softHighlightOf(getTable().getBackground());
      }
    };
    table.addColumnHeaderBackgroundChangedListener((v) -> myTransparentHeaderBg.set(v));

    TableScrollPane scrollPane = new TableScrollPane(table, grid, cornerComponent, grid.getAutoscrollLocker()) {
      @Override
      protected void processMouseWheelEvent(MouseWheelEvent e) {
        if (EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled() &&
            !MouseGestureManager.getInstance().hasTrackpad() && EditorUtil.isChangeFontSize(e)) {
          int rotation = e.getWheelRotation();
          if (rotation != 0) table.changeFontSize(-rotation, 1);
          return;
        }
        super.processMouseWheelEvent(e);
      }

      @Override
      public void paint(Graphics g) {
        table.startPaintingSession();
        try {
          super.paint(g);
        }
        finally {
          table.endPaintingSession();
        }
      }
    };
    table.getColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        grid.getHiddenColumnSelectionHolder().setWholeRowSelected(false);
      }
    });

    return scrollPane;
  }

  private abstract static class MyCornerToolsComponent extends JBViewport {

    MyCornerToolsComponent(@NotNull Project project) {
      EditorHeaderComponent view = new EditorHeaderComponent();
      view.setOpaque(false);
      add(view);
      AnAction action = DumbAwareAction.create(DataGridBundle.message("action.select.all.text"), EmptyIcon.ICON_18, e -> {
        selectAll();
        if (!getTable().hasFocus()) IdeFocusManager.getInstance(project).requestFocus(getTable(), true);
      });
      ActionButton button = new ActionButton(action, null, "", JBUI.emptySize());
      view.add(button, BorderLayout.CENTER);
      view.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM | SideBorder.RIGHT));
    }

    abstract TableResultView getTable();

    abstract DataGrid getGrid();

    void selectAll() {
      ListSelectionModel rowModel = getTable().getSelectionModel();
      int r0 = rowModel.getMinSelectionIndex();
      int r1 = rowModel.getMaxSelectionIndex();
      ListSelectionModel columnModel = getTable().getColumnModel().getSelectionModel();
      int c0 = columnModel.getMinSelectionIndex();
      int c1 = columnModel.getMaxSelectionIndex();
      boolean deselect = r1 - r0 + 1 == getTable().getRowCount() && c1 - c0 + 1 == getTable().getColumnCount();
      if (deselect) {
        getTable().clearSelection();
      }
      else {
        getGrid().getAutoscrollLocker().runWithLock(() -> {
          TableSelectionModel model = ObjectUtils.tryCast(SelectionModelUtil.get(getGrid(), getTable()), TableSelectionModel.class);
          if (model != null) {
            model.setRowSelectionInterval(0, getTable().getRowCount());
            model.setColumnSelectionInterval(0, getTable().getColumnCount());
          }
        });
      }
      getGrid().getHiddenColumnSelectionHolder().setWholeRowSelected(!deselect);
    }
  }
}
