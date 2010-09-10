/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.ui.*;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ParameterTableModelBase<P extends ParameterInfo> extends ListTableModel<ParameterTableModelItemBase<P>>
  implements RowEditableTableModel {

  protected final PsiElement myTypeContext;
  protected final PsiElement myDefaultValueContext;

  public ParameterTableModelBase(PsiElement typeContext,
                                 PsiElement defaultValueContext,
                                 ColumnInfo... columnInfos) {
    super(columnInfos);
    myTypeContext = typeContext;
    myDefaultValueContext = defaultValueContext;
  }

  protected abstract ParameterTableModelItemBase<P> createRowItem(@Nullable P parameterInfo);

  public void addRow() {
    addRow(createRowItem(null));
  }

  public void setParameterInfos(List<P> parameterInfos) {
    List<ParameterTableModelItemBase<P>> items = new ArrayList<ParameterTableModelItemBase<P>>(parameterInfos.size());
    for (P parameterInfo : parameterInfos) {
      items.add(createRowItem(parameterInfo));
    }
    setItems(items);
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    super.setValueAt(aValue, rowIndex, columnIndex);
    fireTableCellUpdated(rowIndex, columnIndex); // to update signature
  }

  protected static abstract class ColumnInfoBase<P extends ParameterInfo, Aspect>
    extends ColumnInfo<ParameterTableModelItemBase<P>, Aspect> {
    private TableCellRenderer myRenderer;
    private TableCellEditor myEditor;

    public ColumnInfoBase(String name) {
      super(name);
    }

    @Override
    public final TableCellEditor getEditor(ParameterTableModelItemBase<P> o) {
      if (myEditor == null) {
        myEditor = doCreateEditor(o);
      }
      return myEditor;
    }

    @Override
    public final TableCellRenderer getRenderer(ParameterTableModelItemBase<P> item) {
      if (myRenderer == null) {
        final TableCellRenderer original = doCreateRenderer(item);
        myRenderer = new TableCellRenderer() {

          public Component getTableCellRendererComponent(JTable table,
                                                         Object value,
                                                         boolean isSelected,
                                                         boolean hasFocus,
                                                         int row,
                                                         int column) {
            Component component = original.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!table.isCellEditable(row, table.convertColumnIndexToModel(column))) {
              component.setBackground(table.getBackground().darker());
            }
            return component;
          }
        };
      }
      return myRenderer;
    }

    protected abstract TableCellRenderer doCreateRenderer(ParameterTableModelItemBase<P> item);

    protected abstract TableCellEditor doCreateEditor(ParameterTableModelItemBase<P> item);
  }

  protected static class TypeColumn<P extends ParameterInfo> extends ColumnInfoBase<P, PsiCodeFragment> {
    private final Project myProject;
    private final FileType myFileType;

    public TypeColumn(Project project, FileType fileType) {
      super(RefactoringBundle.message("column.name.type"));
      myProject = project;
      myFileType = fileType;
    }

    @Override
    public PsiCodeFragment valueOf(ParameterTableModelItemBase<P> item) {
      return item.typeCodeFragment;
    }

    @Override
    public boolean isCellEditable(ParameterTableModelItemBase<P> pParameterTableModelItemBase) {
      return true;
    }

    public TableCellRenderer doCreateRenderer(ParameterTableModelItemBase<P> pParameterTableModelItemBase) {
      return new CodeFragmentTableCellRenderer(myProject, myFileType);
    }

    public TableCellEditor doCreateEditor(ParameterTableModelItemBase<P> o) {
      return new CodeFragmentTableCellEditorBase(myProject, myFileType);
    }
  }

  protected static class NameColumn<P extends ParameterInfo> extends ColumnInfoBase<P, String> {
    private final Project myProject;

    public NameColumn(Project project) {
      super(RefactoringBundle.message("column.name.name"));
      myProject = project;
    }

    @Override
    public String valueOf(ParameterTableModelItemBase<P> item) {
      return item.parameter.getName();
    }

    @Override
    public void setValue(ParameterTableModelItemBase<P> item, String value) {
      item.parameter.setName(value);
    }

    @Override
    public boolean isCellEditable(ParameterTableModelItemBase<P> pParameterTableModelItemBase) {
      return true;
    }

    public TableCellRenderer doCreateRenderer(ParameterTableModelItemBase<P> item) {
      return new ColoredTableCellRenderer() {
        public void customizeCellRenderer(JTable table, Object value,
                                          boolean isSelected, boolean hasFocus, int row, int column) {
          if (value == null) return;
          append((String)value, new SimpleTextAttributes(Font.PLAIN, null));
        }
      };
    }

    public TableCellEditor doCreateEditor(ParameterTableModelItemBase<P> o) {
      return new StringTableCellEditor(myProject);
    }
  }

  protected static class DefaultValueColumn<P extends ParameterInfo> extends ColumnInfoBase<P, PsiCodeFragment> {
    private final Project myProject;
    private final FileType myFileType;

    public DefaultValueColumn(Project project, FileType fileType) {
      super(RefactoringBundle.message("column.name.default.value"));
      myProject = project;
      myFileType = fileType;
    }

    @Override
    public boolean isCellEditable(ParameterTableModelItemBase<P> item) {
      return !item.isEllipsisType() && item.parameter.getOldIndex() == -1;
    }

    @Override
    public PsiCodeFragment valueOf(ParameterTableModelItemBase<P> item) {
      return item.defaultValueCodeFragment;
    }

    public TableCellRenderer doCreateRenderer(ParameterTableModelItemBase<P> item) {
      return new CodeFragmentTableCellRenderer(myProject, myFileType);
    }

    public TableCellEditor doCreateEditor(ParameterTableModelItemBase<P> item) {
      return new CodeFragmentTableCellEditorBase(myProject, myFileType);
    }
  }

  protected static class AnyVarColumn<P extends ParameterInfo> extends ColumnInfoBase<P, Boolean> {

    public AnyVarColumn() {
      super(RefactoringBundle.message("column.name.any.var"));
    }

    @Override
    public boolean isCellEditable(ParameterTableModelItemBase<P> item) {
      return !item.isEllipsisType() && item.parameter.getOldIndex() == -1;
    }

    @Override
    public Boolean valueOf(ParameterTableModelItemBase<P> item) {
      return item.parameter.isUseAnySingleVariable();
    }

    @Override
    public void setValue(ParameterTableModelItemBase<P> item, Boolean value) {
      item.parameter.setUseAnySingleVariable(value);
    }

    public TableCellRenderer doCreateRenderer(ParameterTableModelItemBase<P> item) {
      return new BooleanTableCellRenderer();
    }

    public TableCellEditor doCreateEditor(ParameterTableModelItemBase<P> item) {
      return new BooleanTableCellEditor(false);
    }

    @Override
    public int getWidth(JTable table) {
      final int headerWidth = table.getFontMetrics(table.getFont()).stringWidth(getName()) + 8;
      return Math.max(new JCheckBox().getPreferredSize().width, headerWidth);
    }
  }

}
