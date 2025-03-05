package com.intellij.database.run.ui.grid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.grid.core.impl.icons.GridCoreImplIcons;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Liudmila Kornilova
 **/
public class GridSortingPanel extends GridEditorPanelBase {
  private final GridSortingModel<GridRow, GridColumn> mySortingModel;
  private JBPopup myFilterHistoryPopup;

  GridSortingPanel(@NotNull Project project, @NotNull DataGrid grid,
                   @NotNull GridSortingModel<GridRow, GridColumn> sortingModel,
                   @NotNull Document document) {
    super(project, grid,
          grid.getDataHookup().getSortingPrefix(),
          grid.getDataHookup().getSortingEmptyText(),
          document);
    mySortingModel = sortingModel;

    sortingModel.addListener(new GridSortingModel.Listener() {
      @Override
      public void onPsiUpdated() {
        setHighlighter();
      }

      @Override
      public void onPrefixUpdated() {
        updatePrefix(grid.getDataHookup().getSortingPrefix(), grid.getDataHookup().getSortingEmptyText());
      }
    }, myGrid);

    Function<@NotNull DataGrid, @Nullable GridEditorPanel> getPanel = g -> g.getFilterComponent().getSortingPanel();
    // a workaround for IDEA-127322. IdeaVim tries to run actions registered on editor component before using it's overrides
    new ApplyAction(getPanel).registerCustomShortcutSet(CommonShortcuts.ENTER, myEditor.getComponent());
    new CancelAction(getPanel).registerCustomShortcutSet(CommonShortcuts.ESCAPE, myEditor.getComponent());
    new ShowHistoryAction(getPanel).registerCustomShortcutSet(getShowHistoryShortcut(), myEditor.getComponent());

    add(new HistoryIcon(GridCoreImplIcons.SortHistory), BorderLayout.WEST);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(200, -1);
  }

  @Override
  void clearText() {
    boolean wasApplied = mySortingModel != null && !mySortingModel.getAppliedSortingText().isEmpty();
    ApplicationManager.getApplication().runWriteAction(() -> myEditor.getDocument().setText(""));
    if (wasApplied) apply();
  }

  @Override
  public @NotNull @NlsContexts.PopupContent String getInvalidTextErrorMessage() {
    return DataGridBundle.message("popup.content.invalid.table.sorting");
  }

  @Override
  public void showHistoryPopup() {
    if (myFilterHistoryPopup != null) {
      myFilterHistoryPopup.cancel();
      myFilterHistoryPopup = null;
    }
    myFilterHistoryPopup = createHistoryPopup(mySortingModel.getHistory(), myProject, myEditor, () -> apply());

    myFilterHistoryPopup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myFilterHistoryPopup = null;
      }
    });

    if (myGrid.getPanel().getComponent().isShowing()) {
      myFilterHistoryPopup.showUnderneathOf(this);
    }
  }

  @Override
  public void apply() {
    super.apply();

    GridUtil.activeGridListener().onSortingApplied(myGrid);
  }
}
