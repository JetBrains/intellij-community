package com.intellij.database.actions;

import com.intellij.database.datagrid.AggregatorWidget;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.ui.Aggregator;
import com.intellij.database.run.ui.TableAggregatorWidgetHelper;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public final class CopyAggregatorResult extends DumbAwareAction {
  private static final char ABBREVIATION_SUFFIX = '\u2026'; // 2026 '...'
  private static final int MAX_AGGREGATOR_NAME_LENGTH = 10;

  private DataGrid myGrid = null;

  public void setGrid(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) grid = myGrid;
    if (grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    TableAggregatorWidgetHelper helper = ObjectUtils
      .tryCast(grid.getResultView().getComponent().getClientProperty(AggregatorWidget.AGGREGATOR_WIDGET_HELPER_KEY),
               TableAggregatorWidgetHelper.class);
    if (helper == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    Aggregator aggregator = helper.getAggregator();
    if (aggregator == null) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }
    AggregatorWidget.Factory t = StatusBarWidgetFactory.EP_NAME.findExtension(AggregatorWidget.Factory.class);
    boolean isWidgetShown = t != null && StatusBarWidgetSettings.getInstance().isEnabled(t);
    String aggregatorName = helper.getAggregator().getSimpleName();
    if (aggregatorName.length() >= MAX_AGGREGATOR_NAME_LENGTH) {
      aggregatorName = aggregatorName.substring(0, MAX_AGGREGATOR_NAME_LENGTH) + ABBREVIATION_SUFFIX;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(isWidgetShown);
    e.getPresentation().setText(isWidgetShown ? getTemplateText() + " (" + aggregatorName + ")" : getTemplateText());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) grid = myGrid;
    if (grid == null) return;
    TableAggregatorWidgetHelper helper = ObjectUtils
      .tryCast(grid.getResultView().getComponent().getClientProperty(AggregatorWidget.AGGREGATOR_WIDGET_HELPER_KEY),
               TableAggregatorWidgetHelper.class);
    if (helper == null) return;
    helper.getResultText().thenAccept(result -> {
      ApplicationManager.getApplication().invokeLater(() -> CopyPasteManager.getInstance().setContents(new StringSelection(result)));
    });
  }
}
