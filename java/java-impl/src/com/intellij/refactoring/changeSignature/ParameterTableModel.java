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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.RowEditableTableModel;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
class ParameterTableModel extends AbstractTableModel implements RowEditableTableModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ParameterTableModel");
  private List<ParameterInfoImpl> myParameterInfos;
  private List<PsiTypeCodeFragment> myTypeCodeFraments;
  private List<JavaCodeFragment> myDefaultValuesCodeFragments;
  private final PsiParameterList myParameterList;
  private final PsiReferenceExpression myReferenceExpression; //if change signature was invoked on mehod reference, this is it. Default value is edited in the context of this ref
  private final ChangeSignatureDialog myDialog;
  static final String ANY_VAR_COLUMN_NAME = RefactoringBundle.message("column.name.any.var");

  public ParameterTableModel(PsiParameterList parameterList, final PsiReferenceExpression ref, ChangeSignatureDialog dialog) {
    myParameterList = parameterList;
    myReferenceExpression = ref;
    myDialog = dialog;
  }

  public Class getColumnClass(int columnIndex) {
    if (columnIndex == 3) return Boolean.class;
    return super.getColumnClass(columnIndex);
  }

  public List<PsiTypeCodeFragment> getCodeFraments() {
    return Collections.unmodifiableList(myTypeCodeFraments);
  }

  public List<JavaCodeFragment> getDefaultValueFraments() {
    return Collections.unmodifiableList(myDefaultValuesCodeFragments);
  }

  public ParameterInfoImpl[] getParameters() {
    return myParameterInfos.toArray(new ParameterInfoImpl[myParameterInfos.size()]);
  }


  public void addRow() {
    ParameterInfoImpl info = new ParameterInfoImpl(-1);
    myParameterInfos.add(info);
    myTypeCodeFraments.add(createParameterTypeCodeFragment("", myParameterList));
    myDefaultValuesCodeFragments.add(createDefaultValueCodeFragment("", null));
    fireTableRowsInserted(myParameterInfos.size() - 1, myParameterInfos.size() - 1);
  }

  public void removeRow(int index) {
    myParameterInfos.remove(index);
    myTypeCodeFraments.remove(index);
    myDefaultValuesCodeFragments.remove(index);
    fireTableRowsDeleted(index, index);
  }

  public void exchangeRows(int index1, int index2) {
    Collections.swap(myParameterInfos, index1, index2);
    Collections.swap(myTypeCodeFraments, index1, index2);
    Collections.swap(myDefaultValuesCodeFragments, index1, index2);
    if (index1 < index2) {
      fireTableRowsUpdated(index1, index2);
    }
    else {
      fireTableRowsUpdated(index2, index1);
    }
  }

  public int getRowCount() {
    return myParameterInfos.size();
  }

  public int getColumnCount() {
    return 4;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    ParameterInfoImpl info = myParameterInfos.get(rowIndex);
    switch (columnIndex) {
      case 0:
        return myTypeCodeFraments.get(rowIndex);
      case 1:
        return info.getName();
      case 2:
        return myDefaultValuesCodeFragments.get(rowIndex);
      case 3:
        return Boolean.valueOf(info.useAnySingleVariable);

      default:
        throw new IllegalArgumentException();
    }
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= myParameterInfos.size()) return;
    String s = aValue instanceof String ? (String)aValue : "";
    s = s.trim();
    ParameterInfoImpl info = myParameterInfos.get(rowIndex);
    switch (columnIndex) {
      case 0:
        //info.setType();
        break;

      case 1:
        info.setName(s);
        break;

      case 2:
        break;

      case 3:
        info.setUseAnySingleVariable(((Boolean) aValue).booleanValue());
        break;

      default:
        throw new IllegalArgumentException();
    }
    fireTableCellUpdated(rowIndex, columnIndex);
  }

  public String getColumnName(int column) {
    switch (column) {
      case 0:
        return RefactoringBundle.message("column.name.type");
      case 1:
        return RefactoringBundle.message("column.name.name");
      case 2:
        return RefactoringBundle.message("column.name.default.value");
      case 3:
        return ANY_VAR_COLUMN_NAME;
      default:
        throw new IllegalArgumentException();
    }
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    switch (columnIndex) {
      case 0:
      case 1:
        return true;

      case 2:
        {
          final PsiType typeByRow = getTypeByRow(rowIndex);
          final boolean isEllipsis = typeByRow instanceof PsiEllipsisType;
          ParameterInfoImpl info = myParameterInfos.get(rowIndex);
          return !isEllipsis && info.oldParameterIndex < 0;
        }

      case 3:
        {
          if (myDialog.isGenerateDelegate()) return false;

          final PsiType typeByRow = getTypeByRow(rowIndex);
          final boolean isEllipsis = typeByRow instanceof PsiEllipsisType;
          return !isEllipsis && myParameterInfos.get(rowIndex).oldParameterIndex < 0;
        }

      default:
        throw new IllegalArgumentException();
    }
  }

  private JavaCodeFragment createDefaultValueCodeFragment(final String expressionText, final PsiType expectedType) {
    PsiExpressionCodeFragment codeFragment = JavaPsiFacade.getInstance(myParameterList.getProject()).getElementFactory()
      .createExpressionCodeFragment(expressionText, myReferenceExpression, expectedType, true);
    codeFragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return codeFragment;
  }

  PsiType getTypeByRow(int row) {
    Object typeValueAt = getValueAt(row, 0);
    LOG.assertTrue(typeValueAt instanceof PsiTypeCodeFragment);
    PsiType type;
    try {
      type = ((PsiTypeCodeFragment)typeValueAt).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e1) {
      type = null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e1) {
      type = null;
    }
    return type;
  }

  public void setParameterInfos(List<ParameterInfoImpl> parameterInfos, PsiElement context) {
    myParameterInfos = parameterInfos;
    myTypeCodeFraments = new ArrayList<PsiTypeCodeFragment>(parameterInfos.size());
    myDefaultValuesCodeFragments = new ArrayList<JavaCodeFragment>(parameterInfos.size());
    for (ParameterInfoImpl parameterInfo : parameterInfos) {
      final PsiTypeCodeFragment typeCodeFragment = createParameterTypeCodeFragment(parameterInfo.getTypeText(), context);
      parameterInfo.getTypeWrapper().addImportsTo(typeCodeFragment);
      myTypeCodeFraments.add(typeCodeFragment);
      myDefaultValuesCodeFragments.add(createDefaultValueCodeFragment(parameterInfo.defaultValue, null));
    }
  }

  private PsiTypeCodeFragment createParameterTypeCodeFragment(final String typeText, PsiElement context) {
    return JavaPsiFacade.getInstance(myParameterList.getProject()).getElementFactory().createTypeCodeFragment(
        typeText, context, false, true, true
      );
  }
}
