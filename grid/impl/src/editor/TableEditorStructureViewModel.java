package com.intellij.database.editor;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey.Rieder
 */
public class TableEditorStructureViewModel implements StructureViewModel, StructureViewModel.ElementInfoProvider {

  private final Project myProject;
  private final DataGrid myDataGrid;
  private final List<Object> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Disposable myDisposable = Disposer.newDisposable();

  public TableEditorStructureViewModel(@NotNull TableEditorBase editor) {
    this(editor.getProject(), editor.getDataGrid());
  }

  public TableEditorStructureViewModel(@NotNull Project project, @NotNull DataGrid dataGrid) {
    myProject = project;
    myDataGrid = dataGrid;
    myDataGrid.addDataGridListener(new DataGridListener() {
      @Override
      public void onSelectionChanged(DataGrid dataGrid) {
        for (Object listener : myListeners) {
          if (listener instanceof FileEditorPositionListener) {
            ((FileEditorPositionListener)listener).onCurrentElementChanged();
          }
        }
      }

      @Override
      public void onContentChanged(DataGrid dataGrid, @Nullable GridRequestSource.RequestPlace place) {
        for (Object listener : myListeners) {
          if (listener instanceof ModelListener) {
            ((ModelListener)listener).onModelChanged();
          }
        }
      }
    }, myDisposable);
  }

  @Override
  public @Nullable Object getCurrentEditorElement() {
    ModelIndex<GridColumn> column = myDataGrid.getSelectionModel().getSelectedColumn();
    return column.isValid(myDataGrid) ? DataGridPomTarget.wrapColumn(myProject, myDataGrid, column) : null;
  }

  @Override
  public void addEditorPositionListener(@NotNull FileEditorPositionListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeEditorPositionListener(@NotNull FileEditorPositionListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void addModelListener(@NotNull ModelListener modelListener) {
    myListeners.add(modelListener);
  }

  @Override
  public void removeModelListener(@NotNull ModelListener modelListener) {
    myListeners.remove(modelListener);
  }

  @Override
  public @NotNull StructureViewTreeElement getRoot() {
    return new RootElement();
  }

  @Override
  public Grouper @NotNull [] getGroupers() {
    return Grouper.EMPTY_ARRAY;
  }

  @Override
  public Sorter @NotNull [] getSorters() {
    return new Sorter[]{Sorter.ALPHA_SORTER};
  }

  @Override
  public Filter @NotNull [] getFilters() {
    return Filter.EMPTY_ARRAY;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposable);
  }

  @Override
  public boolean shouldEnterElement(Object element) {
    return false;
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return element instanceof ColElement;
  }

  private class RootElement implements StructureViewTreeElement {
    @Override
    public Object getValue() {
      return this;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
      return new PresentationData();
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
      GridModel<GridRow, GridColumn> model = myDataGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
      List<ModelIndex<GridColumn>> columns = model.getColumnIndices().asList();
      if (!columns.isEmpty()) {
        TreeElement[] result = new TreeElement[columns.size()];
        for (int i = 0; i < result.length; i++) {
          result[i] = new ColElement(DataGridPomTarget.wrapColumn(myProject, myDataGrid, columns.get(i)));
        }
        return result;
      }
      return GridHelper.get(myDataGrid).getChildrenFromModel(myDataGrid).toArray(TreeElement.EMPTY_ARRAY);
    }
  }

  private static final class ColElement extends PsiTreeElementBase<PsiElement> implements ColoredItemPresentation, LocationPresentation {
    private ColElement(PsiElement element) {
      super(element);
    }

    @Override
    public void navigate(boolean requestFocus) {
      DataGridPomTarget.Column target = DataGridPomTarget.unwrapColumn(getElement());
      if (target == null) return;
      boolean enabled = target.dataGrid.isColumnEnabled(target.column);
      if (!enabled) target.dataGrid.setColumnEnabled(target.column, true);
      HierarchicalColumnsCollapseManager collapseManager = target.dataGrid.getHierarchicalColumnsCollapseManager();
      if (collapseManager != null && collapseManager.isColumnHiddenDueToCollapse(target.column)) return;
      target.navigate(requestFocus);
    }

    @Override
    public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
      return Collections.emptyList();
    }

    @Override
    public @Nullable String getPresentableText() {
      DataGridPomTarget.Column target = DataGridPomTarget.unwrapColumn(getElement());
      GridColumn column = target != null ? target.getColumn() : null;
      return column != null ? column.getName() : null;
    }

    @Override
    public Icon getIcon(boolean open) {
      DataGridPomTarget.Column target = DataGridPomTarget.unwrapColumn(getElement());
      GridColumn column = target == null ? null : target.getColumn();
      if (column == null) return null;
      return GridHelper.get(target.dataGrid).getColumnIcon(target.dataGrid, column, true);
    }

    @Override
    public String getLocationString() {
      DataGridPomTarget.Column target = DataGridPomTarget.unwrapColumn(getElement());
      if (target == null) return null;
      return GridHelper.get(target.dataGrid).getLocationString(getElement());
    }

    @Override
    public boolean isSearchInLocationString() {
      return true;
    }

    @Override
    public String getLocationPrefix() {
      return " ";
    }

    @Override
    public String getLocationSuffix() {
      return "";
    }

    @Override
    public @Nullable TextAttributesKey getTextAttributesKey() {
      DataGridPomTarget.Column target = DataGridPomTarget.unwrapColumn(getElement());
      ModelIndex<GridColumn> column = target != null ? target.column : null;
      return column != null && !target.dataGrid.isColumnEnabled(column) ? DataGridColors.STRUCTURE_HIDDEN_COLUMN : null;
    }
  }
}
