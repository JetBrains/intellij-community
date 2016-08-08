/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.ui.StringTableCellEditor;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashSet;
import java.util.Set;

public class JavaParameterTableModel extends ParameterTableModelBase<ParameterInfoImpl, ParameterTableModelItemBase<ParameterInfoImpl>> {
  private final Project myProject;

  public JavaParameterTableModel(final PsiElement typeContext,
                                 PsiElement defaultValueContext,
                                 final RefactoringDialog dialog) {
    this(typeContext, defaultValueContext,
         new JavaTypeColumn(typeContext.getProject()),
         new JavaNameColumn(typeContext.getProject()),
         new DefaultValueColumn<ParameterInfoImpl, ParameterTableModelItemBase<ParameterInfoImpl>>(typeContext.getProject(), StdFileTypes.JAVA) {
           @Override
           public TableCellEditor doCreateEditor(ParameterTableModelItemBase<ParameterInfoImpl> item) {
             return new EditorWithExpectedType(typeContext);
           }
         },
         new AnyVarColumn<ParameterInfoImpl, ParameterTableModelItemBase<ParameterInfoImpl>>() {
        @Override
        public boolean isCellEditable(ParameterTableModelItemBase<ParameterInfoImpl> item) {
          boolean isGenerateDelegate = ((ChangeSignatureDialogBase)dialog).isGenerateDelegate();
          return !isGenerateDelegate && super.isCellEditable(item);
        }
      });
  }

  protected JavaParameterTableModel(PsiElement typeContext, PsiElement defaultValueContext, ColumnInfo... columns) {
    super(typeContext, defaultValueContext, columns);
    myProject = typeContext.getProject();
  }

  @Override
  protected ParameterTableModelItemBase<ParameterInfoImpl> createRowItem(@Nullable ParameterInfoImpl parameterInfo) {
    if (parameterInfo == null) {
      parameterInfo = new ParameterInfoImpl(-1);
    }
    JavaCodeFragmentFactory f = JavaCodeFragmentFactory.getInstance(myProject);
    final PsiTypeCodeFragment paramTypeCodeFragment =
      f.createTypeCodeFragment(parameterInfo.getTypeText(), myTypeContext, true, JavaCodeFragmentFactory.ALLOW_ELLIPSIS);
    final CanonicalTypes.Type paramType = parameterInfo.getTypeWrapper();
    if (paramType != null) {
      paramType.addImportsTo(paramTypeCodeFragment);
    }
    PsiExpressionCodeFragment defaultValueCodeFragment =
      f.createExpressionCodeFragment(parameterInfo.getDefaultValue(), myDefaultValueContext, null, true);
    defaultValueCodeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);

    return new ParameterTableModelItemBase<ParameterInfoImpl>(parameterInfo, paramTypeCodeFragment, defaultValueCodeFragment) {
      @Override
      public boolean isEllipsisType() {
        try {
          return paramTypeCodeFragment.getType() instanceof PsiEllipsisType;
        }
        catch (PsiTypeCodeFragment.TypeSyntaxException e) {
          return false;
        }
        catch (PsiTypeCodeFragment.NoTypeException e) {
          return false;
        }
      }
    };
  }

  @Override
  public void setValueAtWithoutUpdate(Object aValue, int rowIndex, int columnIndex) {
    super.setValueAtWithoutUpdate(aValue, rowIndex, columnIndex);
    //if type was changed - update default value's expected type
    PsiType type = null;
    if (columnIndex == 0 && aValue instanceof String) {
      try {
        type = JavaPsiFacade.getElementFactory(myProject).createTypeFromText((String)aValue, myTypeContext);
      }
      catch (IncorrectOperationException e) {
        type = null;
      }
    }

    if (type != null) {
      final ParameterTableModelItemBase<ParameterInfoImpl> item = getItem(rowIndex);
      ((PsiExpressionCodeFragmentImpl)item.defaultValueCodeFragment).setExpectedType(type);
    }
  }

  @Nullable
  private static PsiType getRowType(JTable table, int row) {
    try {
      return ((PsiTypeCodeFragment)((JavaParameterTableModel)table.getModel()).getItems().get(row).typeCodeFragment).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return null;
    }
  }

  private static class VariableCompletionTableCellEditor extends StringTableCellEditor {
    public VariableCompletionTableCellEditor(Project project) {
      super(project);
    }

    public Component getTableCellEditorComponent(final JTable table,
                                                 Object value,
                                                 boolean isSelected,
                                                 final int row,
                                                 int column) {
      final EditorTextField textField = (EditorTextField)super.getTableCellEditorComponent(table, value, isSelected, row, column);
      textField.registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          PsiType type = getRowType(table, row);
          if (type != null) {
            completeVariable(textField, type);
          }
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
      textField.setBorder(new LineBorder(table.getSelectionBackground()));
      return textField;
    }

    private static void completeVariable(EditorTextField editorTextField, PsiType type) {
      Editor editor = editorTextField.getEditor();
      String prefix = editorTextField.getText();
      if (prefix == null) prefix = "";
      Set<LookupElement> set = new LinkedHashSet<>();
      JavaCompletionUtil.completeVariableNameForRefactoring(editorTextField.getProject(), set, prefix, type, VariableKind.PARAMETER);

      LookupElement[] lookupItems = set.toArray(new LookupElement[set.size()]);
      editor.getCaretModel().moveToOffset(prefix.length());
      editor.getSelectionModel().removeSelection();
      LookupManager.getInstance(editorTextField.getProject()).showLookup(editor, lookupItems, prefix);
    }
  }

  private static class EditorWithExpectedType extends JavaCodeFragmentTableCellEditor {
    public EditorWithExpectedType(PsiElement typeContext) {
      super(typeContext.getProject());
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      final Component editor = super.getTableCellEditorComponent(table, value, isSelected, row, column);
      final PsiType type = getRowType(table, row);
      if (type != null) {
        ((PsiExpressionCodeFragment)myCodeFragment).setExpectedType(type);
      }
      return editor;
    }
  }

  public static class JavaTypeColumn extends TypeColumn<ParameterInfoImpl, ParameterTableModelItemBase<ParameterInfoImpl>> {
    public JavaTypeColumn(Project project) {
      super(project, StdFileTypes.JAVA);
    }

    @Override
    public TableCellEditor doCreateEditor(ParameterTableModelItemBase<ParameterInfoImpl> o) {
      return new JavaCodeFragmentTableCellEditor(myProject);
    }
  }

  public static class JavaNameColumn extends NameColumn<ParameterInfoImpl, ParameterTableModelItemBase<ParameterInfoImpl>> {
    private final Project myProject;

    public JavaNameColumn(Project project) {
      super(project);
      myProject = project;
    }

    @Override
    public TableCellEditor doCreateEditor(ParameterTableModelItemBase<ParameterInfoImpl> o) {
      return new VariableCompletionTableCellEditor(myProject);
    }

    @Override
    public TableCellRenderer doCreateRenderer(ParameterTableModelItemBase<ParameterInfoImpl> item) {
      return new ColoredTableCellRenderer() {
        public void customizeCellRenderer(JTable table, Object value,
                                          boolean isSelected, boolean hasFocus, int row, int column) {
          if (value == null) return;
          if (isSelected || hasFocus) {
            acquireState(table, true, false, row, column);
            getCellState().updateRenderer(this);
            setPaintFocusBorder(false);
          }
          append((String)value, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, null));
        }
      };


    }
  }
}
