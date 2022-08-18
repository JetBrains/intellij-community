// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.*;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.function.Predicate;


public abstract class ParameterTablePanel extends AbstractParameterTablePanel<VariableData> {

  private TypeSelector[] myParameterTypeSelectors;
  private JComboBox<VariableData> myTypeRendererCombo;

  public ParameterTablePanel(Project project,
                             VariableData[] variableData,
                             PsiElement... scopeElements) {
    this(paramName -> LanguageNamesValidation.isIdentifier(JavaLanguage.INSTANCE, paramName, project));
    init(variableData, project, scopeElements);
  }

  public ParameterTablePanel(Predicate<? super String> parameterNameValidator) {
    super(new PassParameterColumnInfo(),
          new TypeColumnInfo(),
          new NameColumnInfo(parameterNameValidator));
  }

  public void init(VariableData[] variableData, Project project, PsiElement... scopeElements) {
    super.init(variableData);

    myParameterTypeSelectors = new TypeSelector[getVariableData().length];
    for (int i = 0; i < myParameterTypeSelectors.length; i++) {
      final VariableData data = variableData[i];
      final TypeSelector selector = createSelector(project, data, scopeElements);
      myParameterTypeSelectors[i] = selector;
      getVariableData()[i].type = myParameterTypeSelectors[i].getSelectedType(); //reverse order
    }

    myTypeRendererCombo = new ComboBox<>(getVariableData());
    myTypeRendererCombo.setOpaque(true);
    myTypeRendererCombo.setBorder(null);
    myTypeRendererCombo.setRenderer(SimpleListCellRenderer.create("", value -> value.type.getPresentableText()));


    final TableColumn typeColumn = myTable.getColumnModel().getColumn(1);
    typeColumn.setCellEditor(new AbstractTableCellEditor() {
      TypeSelector myCurrentSelector;
      final JBComboBoxTableCellEditorComponent myEditorComponent = new JBComboBoxTableCellEditorComponent();
      final JLabel myTypeLabel = new JLabel();

      @Override
      @Nullable
      public Object getCellEditorValue() {
        if (myCurrentSelector.getComponent() instanceof JLabel) {
          return myCurrentSelector.getSelectedType();
        }
        return myEditorComponent.getEditorValue();
      }

      @Override
      public Component getTableCellEditorComponent(final JTable table,
                                                   final Object value,
                                                   final boolean isSelected,
                                                   final int row,
                                                   final int column) {
        myCurrentSelector = myParameterTypeSelectors[row];
        if (myParameterTypeSelectors[row].getComponent() instanceof JLabel) {
          PsiType selectedType = myCurrentSelector.getSelectedType();
          if (selectedType != null) {
            myTypeLabel.setText(selectedType.getPresentableText());
          }
          return myTypeLabel;
        }
        myEditorComponent.setCell(table, row, column);
        myEditorComponent.setOptions((Object[])myCurrentSelector.getTypes());
        myEditorComponent.setDefaultValue(getVariableData()[row].type);
        myEditorComponent.setToString(o -> ((PsiType)o).getPresentableText());

        return myEditorComponent;
      }
    });


    myTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      private final JBComboBoxLabel myLabel = new JBComboBoxLabel();

      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        if (value != null) {
          myLabel.setText(((PsiType)value).getPresentableText());
          myLabel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
          myLabel.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
          if (isSelected) {
            myLabel.setSelectionIcon();
          }
          else {
            myLabel.setRegularIcon();
          }
        }
        return myLabel;
      }
    });
  }

  protected TypeSelector createSelector(final Project project, final VariableData data, PsiElement[] scopeElements) {
    final PsiVariable variable = data.variable;
    final PsiExpression[] occurrences = findVariableOccurrences(scopeElements, variable);
    final TypeSelectorManager manager =
      new TypeSelectorManagerImpl(project, data.type, occurrences, areTypesDirected()) {
        @Override
        protected boolean isUsedAfter() {
          return ParameterTablePanel.this.isUsedAfter(variable);
        }
      };
    return manager.getTypeSelector();
  }

  protected boolean isUsedAfter(PsiVariable variable) {
    return false;
  }

  public static PsiExpression[] findVariableOccurrences(final PsiElement[] scopeElements, final PsiVariable variable) {
    final ArrayList<PsiExpression> result = new ArrayList<>();
    for (final PsiElement element : scopeElements) {
      element.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (!expression.isQualified() && expression.isReferenceTo(variable)) {
            result.add(expression);
          }
        }
      });
    }
    return result.toArray(PsiExpression.EMPTY_ARRAY);
  }

  @Override
  protected void exchangeRows(int row, int targetRow, VariableData currentItem) {
    super.exchangeRows(row, targetRow, currentItem);
    TypeSelector currentSelector = myParameterTypeSelectors[row];
    myParameterTypeSelectors[row] = myParameterTypeSelectors[targetRow];
    myParameterTypeSelectors[targetRow] = currentSelector;
    myTypeRendererCombo.setModel(new DefaultComboBoxModel<>(getVariableData()));
  }

  private static class TypeColumnInfo extends ColumnInfo<VariableData, PsiType> {

    TypeColumnInfo() {
      super(JavaRefactoringBundle.message("parameter.type.table.column.title"));
    }

    @Override
    public void setValue(VariableData data, PsiType value) {
      data.type = value;
    }

    @Nullable
    @Override
    public PsiType valueOf(VariableData data) {
      return data.type;
    }

    @Override
    public Class<?> getColumnClass() {
      return PsiType.class;
    }

    @Override
    public boolean isCellEditable(VariableData data) {
      return true;
    }
  }
}
