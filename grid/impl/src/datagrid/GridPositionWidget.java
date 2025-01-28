package com.intellij.database.datagrid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.editor.GotoRowAction;
import com.intellij.database.editor.TableEditorBase;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import com.intellij.ui.UIBundle;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;

public final class GridPositionWidget extends GridWidget implements Consumer<MouseEvent>, StatusBarWidget.Multiframe, FileEditorManagerListener {
  public static final String POSITION_WIDGET_HELPER_KEY = "ResultViewPositionWidgetHelper";
  public static final String ID = "GridPosition";

  public GridPositionWidget(@NotNull Project project) {
    super(project);
  }

  @Override
  protected @NotNull String getWidgetHelperKey() {
    return POSITION_WIDGET_HELPER_KEY;
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
    if (!isReady()) return;
    myComponentShown = true;
    try {
      GotoRowAction.showGoToDialog(myGrid);
    }
    finally {
      myComponentShown = false;
    }
  }

  @Override
  public @Nullable String getTooltipText() {
    return !isReady() ? null : UIBundle.message("go.to.line.command.name");
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
  public StatusBarWidget copy() {
    return new GridPositionWidget(myProject);
  }

  private boolean isReady() {
    return myGrid != null && !myGrid.isEditing() && myGrid.getVisibleRowsCount() * myGrid.getVisibleColumns().size() > 0;
  }

  static final class Factory extends StatusBarEditorBasedWidgetFactory {
    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
      return DataGridBundle.message("status.bar.grid.position.widget.display.name");
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
      return new GridPositionWidget(project);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
      return getFileEditor(statusBar) instanceof TableEditorBase;
    }
  }
}
