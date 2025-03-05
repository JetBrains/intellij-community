package com.intellij.database.datagrid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.actions.CopyAggregatorResult;
import com.intellij.database.actions.ShowAggregateViewAction;
import com.intellij.database.editor.TableEditorBase;
import com.intellij.database.run.actions.ChooseAggregatorsAction.SelectSingleAggregatorAction;
import com.intellij.database.run.ui.TableAggregatorWidgetHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.awt.event.MouseEvent;

import static com.intellij.database.run.actions.ChooseAggregatorsAction.getChildrenImpl;

public final class AggregatorWidget extends GridWidget
  implements Consumer<MouseEvent>, StatusBarWidget.Multiframe {
  public static final String AGGREGATOR_WIDGET_HELPER_KEY = "ResultViewAggregatorWidgetHelper";

  public static final String ID = "GridAggregator";

  public AggregatorWidget(@NotNull Project project) {
    super(project);
  }

  @Override
  protected @NotNull String getWidgetHelperKey() {
    return AGGREGATOR_WIDGET_HELPER_KEY;
  }

  @Override
  public @NotNull String ID() {
    return ID;
  }

  @Override
  public @NotNull StatusBarWidget.WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  public void consume(MouseEvent event) {
    ScriptedStatusBarWidgetGroup popupGroup = new ScriptedStatusBarWidgetGroup(myGrid, myStatusBar);
    ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.STATUS_BAR_PLACE, popupGroup);
    myComponentShown = true;
    menu.getComponent().addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        myComponentShown = false;
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        myComponentShown = false;
      }
    });
    menu.getComponent().show(event.getComponent(), event.getPoint().x, event.getPoint().y);
  }

  @Override
  public @Nullable String getTooltipText() {
    return !isReady() ? null : DataGridBundle.message("status.bar.grid.aggregator.widget.display.name");
  }

  @Override
  public @NotNull Consumer<MouseEvent> getClickConsumer() {
    return this;
  }

  @Override
  public float getAlignment() {
    return Component.RIGHT_ALIGNMENT;
  }

  @Override
  public @NotNull StatusBarWidget copy() {
    return new AggregatorWidget(myProject);
  }

  private boolean isReady() {
    return myGrid != null && !myGrid.isEditing() && myGrid.getVisibleRowsCount() * myGrid.getVisibleColumns().size() > 0;
  }

  public static final class Factory extends StatusBarEditorBasedWidgetFactory {
    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
      return DataGridBundle.message("status.bar.grid.aggregator.widget.display.name");
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
      return new AggregatorWidget(project);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
      return getFileEditor(statusBar) instanceof TableEditorBase;
    }
  }

  private static final class ScriptedStatusBarWidgetGroup extends ActionGroup implements DumbAware {
    private final DataGrid myGrid;
    private final StatusBar myStatusBar;

    ScriptedStatusBarWidgetGroup(@Nullable DataGrid grid, @Nullable StatusBar statusBar) {
      myGrid = grid;
      myStatusBar = statusBar;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (e == null || myGrid == null || myStatusBar == null) return EMPTY_ARRAY;
      TableAggregatorWidgetHelper helper = ObjectUtils
        .tryCast(myGrid.getResultView().getComponent().getClientProperty(AGGREGATOR_WIDGET_HELPER_KEY),
                 TableAggregatorWidgetHelper.class);
      if (helper == null) return EMPTY_ARRAY;
      JBIterable<AnAction> children = getChildrenImpl(myGrid, (factory, factories) -> {
        return new SelectSingleAggregatorAction(myGrid, myStatusBar, helper, factory, factories, () -> GridUtil.getSettings(myGrid));
      });
      ShowAggregateViewAction showAggregateViewAction =
        (ShowAggregateViewAction)ActionManager.getInstance().getAction("Console.TableResult.AggregateView");
      CopyAggregatorResult copyAggregatorResult =
        (CopyAggregatorResult)ActionManager.getInstance().getAction("Console.TableResult.CopyAggregatorResult");
      showAggregateViewAction.setGrid(myGrid);
      copyAggregatorResult.setGrid(myGrid);
      JBIterable<AnAction> copySection = JBIterable.of(copyAggregatorResult, new Separator());
      JBIterable<AnAction> openAggregatorSection = JBIterable.of(new Separator(), showAggregateViewAction);
      JBIterable<AnAction> allElements = copySection.append(children.append(openAggregatorSection));
      if (ActionPlaces.STATUS_BAR_PLACE.equals(e.getPlace())) {
        return allElements.map(o -> o instanceof Separator ? Separator.getInstance() : o).toArray(EMPTY_ARRAY);
      }
      return allElements.toArray(EMPTY_ARRAY);
    }
  }
}
