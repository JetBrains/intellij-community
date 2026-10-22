package com.intellij.database.run.ui.table;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.ResultView;
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

import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseWheelEvent;

public class TableResultViewFactory implements ResultViewFactory {
  public static final TableResultViewFactory TABLE_FACTORY = new TableResultViewFactory();

  @Override
  public @NotNull ResultView createResultView(@NotNull DataGrid resultPanel, @NotNull ActionGroup columnHeaderActions, @NotNull ActionGroup rowHeaderActions) {
    return new TableResultView(resultPanel, columnHeaderActions, rowHeaderActions);
  }

  @Override
  public @NotNull JComponent wrap(@NotNull DataGrid grid, @NotNull ResultView resultView) {
    return ((TableResultView)resultView).getScrollPane();
  }

  static TableScrollPane createScrollPane(DataGrid grid, GridTable table, TableResultView view) {
    Ref<Boolean> myTransparentHeaderBg = new Ref<>(false);
    MyCornerToolsComponent cornerComponent = new MyCornerToolsComponent(grid.getProject()) {
      @Override
      GridTable getTable() {
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
      public boolean requestFocusInWindow() {
        return table.requestFocusInWindow();
      }

      @Override
      public void requestFocus() {
        table.requestFocus();
      }

      @Override
      protected void processMouseWheelEvent(MouseWheelEvent e) {
        if (EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled() &&
            !MouseGestureManager.getInstance().hasTrackpad() && EditorUtil.isChangeFontSize(e)) {
          int rotation = e.getWheelRotation();
          if (rotation != 0) view.changeFontSize(-rotation, 1);
          return;
        }
        super.processMouseWheelEvent(e);
      }

      @Override
      public void paint(Graphics g) {
        view.startPaintingSession();
        try {
          super.paint(g);
        }
        finally {
          view.endPaintingSession();
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

    abstract GridTable getTable();

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
          TableSelectionModel model = ObjectUtils.tryCast(getTable().gridSelectionModel(), TableSelectionModel.class);
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
