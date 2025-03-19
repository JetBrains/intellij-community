package com.intellij.database.editor;

import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.DocumentReferenceProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BiConsumer;

public abstract class TableFileEditor extends TableEditorBase implements DocumentReferenceProvider {
  private final VirtualFile myFile;

  protected TableFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
    super(project);
    myFile = file;
  }

  @Override
  public Collection<DocumentReference> getDocumentReferences() {
    return Collections.singletonList(DocumentReferenceManager.getInstance().create(myFile));
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  protected abstract void configure(@NotNull DataGrid grid, @NotNull DataGridAppearance appearance);

  protected final @NotNull DataGrid createDataGrid(@NotNull GridDataHookUp<GridRow, GridColumn> hookUp) {
    return createDataGrid(hookUp, GridUtil.getGridPopupActions(), false);
  }

  protected final @NotNull DataGrid createDataGrid(@NotNull GridDataHookUp<GridRow, GridColumn> hookUp,
                                                   @NotNull ActionGroup popupActions,
                                                   boolean isHierarchical) {
    final DataGrid grid = GridUtil.createDataGrid(
      getProject(), hookUp, popupActions,
      ((BiConsumer<DataGrid, DataGridAppearance>)this::configure).andThen(GridUtil::configureFullSizeTable),
      isHierarchical
    );
    Disposer.register(this, grid);
    Disposer.register(this, UiNotifyConnector.Once.installOn(grid.getPanel().getComponent(), new Activatable() {
      @Override
      public void showNotify() {
        grid.getDataHookup().getLoader().loadFirstPage(new GridRequestSource(new DataGridRequestPlace(grid)));
      }
    }));
    grid.getDataHookup().addRequestListener(new GridDataHookUp.RequestListener<>() {
      @Override
      public void requestStarted(@NotNull GridRequestSource source) {
        getLoadingPanel().startLoading();
      }

      @Override
      public void error(@NotNull GridRequestSource source, @NotNull ErrorInfo errorInfo) {

      }

      @Override
      public void requestProgress(@NotNull GridRequestSource source, @Nullable String progress) {
        getLoadingPanel().setLoadingText(progress);
      }

      @Override
      public void updateCountReceived(@NotNull GridRequestSource source, int updateCount) {

      }

      @Override
      public void requestFinished(@NotNull GridRequestSource source, boolean success) {
        getLoadingPanel().setLoadingText(null);
        getLoadingPanel().stopLoading();
      }

      private @NotNull JBLoadingPanel getLoadingPanel() {
        return grid.getPanel().getComponent();
      }
    }, grid);
    return grid;
  }
}
