package com.intellij.database.run.ui.grid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridEditorPanel;
import com.intellij.database.datagrid.GridFilteringModel;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.grid.core.impl.icons.GridCoreImplIcons;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.intellij.database.datagrid.GridFilterAndSortingComponent.FILTER_PREFERRED_SIZE;

public class GridFilterPanel extends GridEditorPanelBase {
  private JBPopup myFilterHistoryPopup;

  public GridFilterPanel(@NotNull Project project, @NotNull DataGrid grid) {
    super(project, grid,
          grid.getDataHookup().getFilterPrefix(),
          grid.getDataHookup().getFilterEmptyText(),
          getFilterDocument(grid));
    // TODO (anya) [grid]: similar logic for sort panel ((!) note that the sort panel is attached to the filter panel)
    setVisible(grid.getDataHookup().isFilterApplicable());

    Function<@NotNull DataGrid, @Nullable GridEditorPanel> getPanel = g -> g.getFilterComponent().getFilterPanel();
    // a workaround for IDEA-127322. IdeaVim tries to run actions registered on editor component before using it's overrides
    new ApplyAction(getPanel).registerCustomShortcutSet(CommonShortcuts.ENTER, myEditor.getComponent());
    new CancelAction(getPanel).registerCustomShortcutSet(CommonShortcuts.ESCAPE, myEditor.getComponent());
    new ShowHistoryAction(getPanel).registerCustomShortcutSet(getShowHistoryShortcut(), myEditor.getComponent());

    GridFilteringModel filteringModel = myGrid.getDataHookup().getFilteringModel();

    if (filteringModel != null) {
      filteringModel.addListener(new GridFilteringModel.Listener() {
        @Override
        public void onPsiUpdated() {
          setHighlighter();
        }

        @Override
        public void onPrefixUpdated() {
          updatePrefix(grid.getDataHookup().getFilterPrefix(), grid.getDataHookup().getFilterEmptyText());
        }

        @Override
        public void onApplicableUpdated() {
          setVisible(grid.getDataHookup().isFilterApplicable());
        }
      }, myGrid);
    }

    add(new HistoryIcon(GridCoreImplIcons.FilterHistory), BorderLayout.WEST);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension preferredSize = super.getPreferredSize();
    return new Dimension(Math.max(FILTER_PREFERRED_SIZE, preferredSize.width), preferredSize.height);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  void clearText() {
    GridFilteringModel filteringModel = myGrid.getDataHookup().getFilteringModel();
    boolean wasApplied = filteringModel != null && !StringUtil.isEmpty(filteringModel.getAppliedText());
    setFilterText(this, myGrid, "", -1);
    if (wasApplied) myGrid.getFilterComponent().getFilterPanel().apply();
  }

  @Override
  public @NotNull @NlsContexts.PopupContent String getInvalidTextErrorMessage() {
    return DataGridBundle.message("popup.content.invalid.filter.criteria");
  }

  private static @NotNull Document getFilterDocument(@NotNull DataGrid grid) {
    GridFilteringModel filteringMode = grid.getDataHookup().getFilteringModel();
    return filteringMode == null
           ? EditorFactory.getInstance().createDocument("")
           : filteringMode.getFilterDocument();
  }

  public static void setFilterText(@NotNull GridEditorPanel filterPanel, @NotNull DataGrid grid, @NotNull String filter, int caretPosition) {
    GridFilteringModel model = grid.getDataHookup().getFilteringModel();
    if (model == null) return;

    model.setFilterText(filter);
    if (caretPosition < 0) return;
    filterPanel.getComponent().requestFocusInWindow();
    filterPanel.getEditor().getCaretModel().moveToOffset(caretPosition);
  }

  @Override
  public void showHistoryPopup() {
    if (myFilterHistoryPopup != null) {
      myFilterHistoryPopup.cancel();
      myFilterHistoryPopup = null;
    }
    GridFilteringModel model = myGrid.getDataHookup().getFilteringModel();
    if (model == null) return;
    myFilterHistoryPopup = createHistoryPopup(model.getHistory(), myProject, myEditor, () -> myGrid.getFilterComponent().getFilterPanel().apply());

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

    GridUtil.activeGridListener().onFilterApplied(myGrid);
  }
}
