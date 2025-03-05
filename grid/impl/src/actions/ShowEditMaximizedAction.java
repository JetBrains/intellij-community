package com.intellij.database.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridPanel.ViewPosition;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.extractors.ImageInfo;
import com.intellij.database.run.ui.EditMaximizedView;
import com.intellij.database.run.ui.EditorCellViewer;
import com.intellij.database.run.ui.ValueTabInfoProvider;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.database.run.ui.EditMaximizedViewKt.findEditMaximized;

/**
 * @author Liudmila Kornilova
 **/
public class ShowEditMaximizedAction extends DumbAwareAction {
  private static final String EDIT_MAXIMIZED_POSITION = "EditMaximizedView.POSITION";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    boolean inCell = DataGridUIUtil.inCell(grid, e);
    if (inCell) {
      Object value = DataGridUIUtil.getLeadSelectionCellValue(grid, e, false);
      e.getPresentation().setText(value instanceof ImageInfo
                                  ? DataGridBundle.message("action.Console.TableResult.EditValueMaximized.view.image")
                                  : DataGridBundle.message("action.Console.TableResult.EditValueMaximized.maximize"));
    }
    else {
      e.getPresentation().setText(DataGridBundle.message("action.Console.TableResult.EditValueMaximized.text"));
    }
    var editMaximized = findEditMaximized(e.getDataContext());
    boolean visible = editMaximized != null && editMaximized.getCurrentTabInfoProvider() instanceof ValueTabInfoProvider;
    e.getPresentation().setEnabledAndVisible(inCell || !visible);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) return;
    EditMaximizedView view = getView(grid, e);
    view.open(tabInfoProvider -> tabInfoProvider instanceof ValueTabInfoProvider);

    if (grid.isEditable()) {
      JComponent focusComponent = view.getPreferedFocusComponent();
      if (focusComponent != null) focusComponent.requestFocus();
      EditorCellViewer cellViewer = ObjectUtils.tryCast(view.getCurrentTabInfoProvider().getViewer(), EditorCellViewer.class);
      if (cellViewer != null) cellViewer.selectAll();
    }
  }

  public static EditMaximizedView getView(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    EditMaximizedView view = findEditMaximized(e.getDataContext());
    if (view == null) {
      view = new EditMaximizedView(grid);
      Disposer.register(grid, view);
      grid.getPanel().putSideView(view, getPosition(), null);
    }
    return view;
  }

  public static void savePosition(@NotNull ViewPosition position) {
    PropertiesComponent.getInstance().setValue(EDIT_MAXIMIZED_POSITION, position.name());
  }

  public static boolean someViewIsShown(@NotNull DataGrid grid) {
    var panel = grid.getPanel();
    return panel.getSideView(ViewPosition.RIGHT) != null || panel.getSideView(ViewPosition.BOTTOM) != null;
  }

  public static @NotNull ViewPosition getPosition() {
    try {
      String name = PropertiesComponent.getInstance().getValue(EDIT_MAXIMIZED_POSITION);
      if (name == null) return ViewPosition.RIGHT;
      return ViewPosition.valueOf(name);
    }
    catch (IllegalArgumentException e) {
      return ViewPosition.RIGHT;
    }
  }
}