package com.intellij.database.run.ui.table;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.MouseWheelEvent;

public class TableResultViewFactory implements ResultViewFactory {
  public static final TableResultViewFactory TABLE_FACTORY = new TableResultViewFactory();

  @Override
  public @NotNull ResultView createResultView(@NotNull DataGrid grid,
                                             @NotNull ActionGroup columnHeaderActions,
                                             @NotNull ActionGroup rowHeaderActions) {
    return new TableResultView(grid, columnHeaderActions, rowHeaderActions);
  }

  @Override
  public @NotNull JComponent wrap(@NotNull DataGrid grid, @NotNull ResultView resultView) {
    return resultView.getComponent();
  }

  /**
   * The view's scroll pane: focus requests go to the lead cell's table, the font-size wheel gesture zooms, and Select All sits in the
   * corner.
   */
  static TableScrollPane createScrollPane(DataGrid grid, GridTable mainTable, TableSelectionModel selectionModel, TableResultView view) {
    MyCornerToolsComponent cornerComponent = new MyCornerToolsComponent(grid, mainTable, view.getViewColumnModel(), selectionModel);
    mainTable.setColumnHeaderBackgroundListener(cornerComponent::setTransparentHeaderBackground);

    // The scroll pane is the view's component, and a focus request on it goes to the table that shows the lead cell.
    // The protected variants can't be called on a table from here, so they forward to the nearest public one.
    TableScrollPane scrollPane = new TableScrollPane(mainTable, grid, cornerComponent, grid.getAutoscrollLocker()) {
      @Override
      public void requestFocus() {
        view.leadColumnTable().requestFocus();
      }

      @Override
      public boolean requestFocus(boolean temporary) {
        return view.leadColumnTable().requestFocus(temporary);
      }

      @Override
      public void requestFocus(FocusEvent.Cause cause) {
        view.leadColumnTable().requestFocus(cause);
      }

      @Override
      protected boolean requestFocus(boolean temporary, FocusEvent.Cause cause) {
        return view.leadColumnTable().requestFocus(temporary);
      }

      @Override
      public boolean requestFocusInWindow() {
        return view.leadColumnTable().requestFocusInWindow();
      }

      @Override
      protected boolean requestFocusInWindow(boolean temporary) {
        return view.leadColumnTable().requestFocusInWindow();
      }

      @Override
      public boolean requestFocusInWindow(FocusEvent.Cause cause) {
        return view.leadColumnTable().requestFocusInWindow(cause);
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
    view.getViewColumnModel().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@NotNull ListSelectionEvent e) {
        grid.getHiddenColumnSelectionHolder().setWholeRowSelected(false);
      }
    });

    return scrollPane;
  }

  /** The upper leading corner: a Select All button, which clears the selection instead when every cell is selected. */
  private static final class MyCornerToolsComponent extends JBViewport {
    private final DataGrid myGrid;
    /** The main table, whose row selection and row count Select All reads, and whose background the corner takes. */
    private final GridTable myMainTable;
    /** The view's column model, which holds the grid's columns. */
    private final TableColumnModel myViewColumnModel;
    /** The grid's selection model, which Select All changes. */
    private final TableSelectionModel mySelectionModel;
    private boolean myTransparentHeaderBackground;

    MyCornerToolsComponent(@NotNull DataGrid grid,
                           @NotNull GridTable mainTable,
                           @NotNull TableColumnModel viewColumnModel,
                           @NotNull TableSelectionModel selectionModel) {
      myGrid = grid;
      myMainTable = mainTable;
      myViewColumnModel = viewColumnModel;
      mySelectionModel = selectionModel;
      EditorHeaderComponent header = new EditorHeaderComponent();
      header.setOpaque(false);
      add(header);
      AnAction action = DumbAwareAction.create(DataGridBundle.message("action.select.all.text"), EmptyIcon.ICON_18, e -> {
        selectAll();
        GridUtil.focusDataGrid(myGrid);
      });
      ActionButton button = new ActionButton(action, null, "", JBUI.emptySize());
      header.add(button, BorderLayout.CENTER);
      header.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM | SideBorder.RIGHT));
    }

    /** The main table's column header turned transparent or back, and the corner follows it. */
    void setTransparentHeaderBackground(boolean transparent) {
      myTransparentHeaderBackground = transparent;
    }

    @Override
    public Color getBackground() {
      // JViewport's constructor installs its colors and reads the background before the fields are set.
      if (myMainTable == null) return super.getBackground();
      return myTransparentHeaderBackground
             ? myMainTable.getBackground()
             : DataGridUIUtil.softHighlightOf(myMainTable.getBackground());
    }

    void selectAll() {
      ListSelectionModel rowSelection = myMainTable.getSelectionModel();
      int firstRow = rowSelection.getMinSelectionIndex();
      int lastRow = rowSelection.getMaxSelectionIndex();
      ListSelectionModel columnSelection = myViewColumnModel.getSelectionModel();
      int firstPosition = columnSelection.getMinSelectionIndex();
      int lastPosition = columnSelection.getMaxSelectionIndex();
      boolean deselect = lastRow - firstRow + 1 == myMainTable.getRowCount() &&
                         lastPosition - firstPosition + 1 == myViewColumnModel.getColumnCount();
      if (deselect) {
        mySelectionModel.clearSelection();
      }
      else {
        myGrid.getAutoscrollLocker().runWithLock(() -> {
          mySelectionModel.setRowSelectionInterval(0, myMainTable.getRowCount());
          mySelectionModel.setColumnSelectionInterval(0, myViewColumnModel.getColumnCount());
        });
      }
      myGrid.getHiddenColumnSelectionHolder().setWholeRowSelected(!deselect);
    }
  }
}
