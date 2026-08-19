package com.intellij.database.util;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.SelectionModel;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.EditMaximizedView;
import com.intellij.database.run.ui.table.TableResultView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.InputEvent;

public final class DataGridUIUtil {
  public static Color softHighlightOf(Color c1) {
    if(c1 == null) return null;
    int i = 0x10;
    return new JBColor(new Color(Math.max(0, c1.getRed() - i), Math.max(0, c1.getGreen() - i), Math.max(0, c1.getBlue() - i)),
                       new Color(Math.min(255, c1.getRed() + i), Math.min(255, c1.getGreen() + i), Math.min(255, c1.getBlue() + i)));
  }

  public static @NotNull Color toGrayscale(@NotNull Color color) {
    float avg = (0.3f * color.getRed() / 255.0f) + (0.59f * color.getBlue() / 255.0f) + (0.11f * color.getGreen() / 255.0f);
    return new Color(avg, avg, avg);
  }

  /* copy from DbUIUtil */
  public static void showPopup(@NotNull JBPopup popup, @Nullable Editor editor, @Nullable AnActionEvent event) {
    InputEvent inputEvent = event == null ? null : event.getInputEvent();
    Object eventSource = inputEvent == null ? null : inputEvent.getSource();
    if (editor != null && editor.getComponent().isShowing()) {
      popup.showInBestPositionFor(editor);
    }
    else if (event == null) {
      // todo ignore for now..
    }
    else if (eventSource instanceof InplaceButton || eventSource instanceof ActionButton) {
      // title action or toolbar button
      popup.setMinimumSize(((JComponent)eventSource).getSize());
      popup.showUnderneathOf((Component)eventSource);
    }
    else {
      popup.showInBestPositionFor(event.getDataContext());
    }
  }

  public static void updateAllToolbarsUnder(Component component) {
    UIUtil.uiTraverser(component).filter(ActionToolbarImpl.class)
      .forEach(ActionToolbarImpl::updateActionsImmediately);
  }

  public static boolean inCell(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    Component contextComponent = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Component resultView = grid.getResultView().getComponent();
    return resultView instanceof TableResultView table
           ? table.isCellComponent(contextComponent)
           : contextComponent == resultView;
  }

  public static @Nullable Object getLeadSelectionCellValue(@NotNull DataGrid grid, @NotNull AnActionEvent e, boolean single) {
    SelectionModel<GridRow, GridColumn> selectionModel = grid.getSelectionModel();
    if (!single || selectionModel.getSelectedRowCount() == 1 && selectionModel.getSelectedColumnCount() == 1) {
      ModelIndex<GridRow> row = selectionModel.getLeadSelectionRow();
      ModelIndex<GridColumn> column = selectionModel.getLeadSelectionColumn();
      return row.asInteger() == -1 || column.asInteger() == -1
             ? null
             : grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column);
    }
    return null;
  }

  public static boolean isInsideGrid(@NotNull DataGrid grid, @NotNull Component component) {
    Component focus = grid.getPreferredFocusedComponent();
    if (SwingUtilities.isDescendingFrom(component, focus)) return true;
    // The pinned/frozen region is a separate table placed in the scroll pane's row header, not under the main result
    // view, so also treat components in that row header as inside the grid (DBE-26267).
    JScrollPane scrollPane = ComponentUtil.getParentOfType(JScrollPane.class, focus);
    JViewport rowHeader = scrollPane == null ? null : scrollPane.getRowHeader();
    return rowHeader != null && SwingUtilities.isDescendingFrom(component, rowHeader);
  }

  public static boolean isInsideEditMaximizedView(@Nullable EditMaximizedView view, @NotNull Component component) {
    return view != null && SwingUtilities.isDescendingFrom(component, view.getPreferedFocusComponent());
  }
}
