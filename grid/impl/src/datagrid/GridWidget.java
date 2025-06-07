package com.intellij.database.datagrid;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid.ActiveGridListener;
import com.intellij.database.editor.TableEditorBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.ClickListener;
import com.intellij.util.Consumer;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.util.concurrent.CompletableFuture;

import static com.intellij.database.datagrid.DataGrid.ACTIVE_GRID_CHANGED_TOPIC;

/**
 * @author Liudmila Kornilova
 **/
public abstract class GridWidget implements StatusBarWidget, CustomStatusBarWidget, StatusBarWidget.TextPresentation {
  protected StatusBar myStatusBar;
  private CompletableFuture<@NlsContexts.Label String> myFuture = null;
  protected DataGrid myGrid;
  protected final Project myProject;
  private GridWidgetHelper myHelper;
  protected boolean myComponentShown;
  private FocusListener myFocusListener;
  private final LazyInitializer.LazyValue<TextPanel> myComponent = LazyInitializer.create(() -> {
    var result = new TextPanel();
    installClickListener(result);
    return result;
  });

  public GridWidget(@NotNull Project project) {
    myProject = project;
  }

  private void installClickListener(TextPanel result) {
    Consumer<MouseEvent> clickConsumer = getClickConsumer();
    if (clickConsumer != null) {
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (!e.isPopupTrigger() && MouseEvent.BUTTON1 == e.getButton()) {
            clickConsumer.consume(e);
          }
          return true;
        }
      }.installOn(result, true);
    }
  }

  @Override
  public JComponent getComponent() {
    return myComponent.get();
  }

  protected abstract @NotNull String getWidgetHelperKey();

  @Override
  public @NotNull @NlsContexts.Label String getText() {
    GridWidget positionWidget =
      myStatusBar == null ? null : ObjectUtils.tryCast(myStatusBar.getWidget(GridPositionWidget.ID), GridWidget.class);
    GridWidget aggregateWidget =
      myStatusBar == null ? null : ObjectUtils.tryCast(myStatusBar.getWidget(AggregatorWidget.ID), GridWidget.class);
    boolean componentShown =
      positionWidget != null && positionWidget.myComponentShown || aggregateWidget != null && aggregateWidget.myComponentShown;
    if (myHelper == null || !(componentShown || myGrid != null && myGrid.getResultView().getComponent().hasFocus())) {
      return "";
    }
    myFuture = myHelper.getText();
    myFuture.thenAccept(result -> ApplicationManager.getApplication().invokeLater(() -> {
      myComponent.get().setText(result);
      myComponent.get().setVisible(!StringUtil.isEmptyOrSpaces(result));
      myStatusBar.updateWidget(ID());
    }));
    return myFuture.getNow(DataGridBundle.message("status.bar.grid.aggregator.widget.calculating"));
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
    if (myGrid != null) {
      myGrid.getResultView().getComponent().removeFocusListener(myFocusListener);
      myGrid = null;
      myFocusListener = null;
    }
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(ACTIVE_GRID_CHANGED_TOPIC, new ActiveGridListener() {
      @Override
      public void changed(@NotNull DataGrid grid) {
        set(grid, statusBar);
      }

      @Override
      public void closed() {
        set(null, statusBar);
      }
    });
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        DataGrid grid = getDataGrid(statusBar);
        set(grid, statusBar);
      }
    });
    set(getDataGrid(statusBar), statusBar);
  }

  private @Nullable DataGrid getDataGrid(@NotNull StatusBar bar) {
    if (myProject.isDisposed()) return null;
    FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(bar);
    return fileEditor instanceof TableEditorBase ? ((TableEditorBase)fileEditor).getDataGrid() : null;
  }

  protected void set(@Nullable DataGrid grid, @NotNull StatusBar statusBar) {
    if (grid != null && !isOurGrid(grid, statusBar)) return;
    if (myGrid != grid) {
      if (myGrid != null) {
        myGrid.getResultView().getComponent().removeFocusListener(myFocusListener);
        myFocusListener = null;
      }
      myGrid = grid;
      if (myGrid != null) {
        myFocusListener = new FocusListener() {
          @Override
          public void focusGained(FocusEvent e) {
            update(statusBar);
          }

          @Override
          public void focusLost(FocusEvent e) {
            update(statusBar);
          }
        };
        myGrid.getResultView().getComponent().addFocusListener(myFocusListener);
      }
      myHelper = grid == null
                 ? null
                 : ObjectUtils.tryCast(grid.getResultView().getComponent().getClientProperty(getWidgetHelperKey()), GridWidgetHelper.class);
    }
    update(statusBar);
  }

  private void update(@NotNull StatusBar statusBar) {
    String text = getText();
    myComponent.get().setText(text);
    myComponent.get().setVisible(!StringUtil.isEmptyOrSpaces(text));
    statusBar.updateWidget(ID());
  }

  protected boolean isOurGrid(@NotNull DataGrid grid, @NotNull StatusBar bar) {
    return isOurComponent(grid.getPanel().getComponent(), myProject, bar);
  }

  private static boolean isOurComponent(@NotNull JComponent component, @NotNull Project project, @NotNull StatusBar bar) {
    return WindowManager.getInstance().getStatusBar(component, project) == bar;
  }

  public interface GridWidgetHelper {
    @NotNull
    CompletableFuture<@NlsContexts.Label String> getText();
  }
}
