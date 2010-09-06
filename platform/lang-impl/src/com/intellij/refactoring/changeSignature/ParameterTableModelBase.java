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

import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.RowEditableTableModel;
import com.intellij.util.ArrayUtil;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ParameterTableModelBase<P extends ParameterInfo> extends AbstractTableModel implements RowEditableTableModel {

  private List<P> myParameterInfos;
  private List<PsiCodeFragment> myTypeCodeFraments;
  private List<PsiCodeFragment> myDefaultValuesCodeFragments;
  protected final PsiElement myTypeContext;
  protected final PsiElement myDefaultValueContext;
  private final ChangeSignatureDialogBase myDialog;
  private final Class<P> myParameterInfoClass;
  static final String ANY_VAR_COLUMN_NAME = RefactoringBundle.message("column.name.any.var");

  public ParameterTableModelBase(PsiElement typeContext,
                                 PsiElement defaultValueContext,
                                 ChangeSignatureDialogBase dialog,
                                 Class<P> clazz) {
    myTypeContext = typeContext;
    myDefaultValueContext = defaultValueContext;
    myDialog = dialog;
    myParameterInfoClass = clazz;
  }

  public Class getColumnClass(int columnIndex) {
    if (columnIndex == 3) return Boolean.class;
    return super.getColumnClass(columnIndex);
  }

  public List<PsiCodeFragment> getCodeFragments() {
    return Collections.unmodifiableList(myTypeCodeFraments);
  }

  public List<PsiCodeFragment> getDefaultValueFragments() {
    return Collections.unmodifiableList(myDefaultValuesCodeFragments);
  }

  public P[] getParameters() {
    return ArrayUtil.toObjectArray(myParameterInfos, myParameterInfoClass);
  }

  protected abstract P createParameterInfo();

  protected abstract boolean isEllipsisType(int row);

  protected abstract PsiCodeFragment createDefaultValueCodeFragment(final String expressionText);

  protected abstract PsiCodeFragment createParameterTypeCodeFragment(final String typeText);

  public void addRow() {
    P parameterInfo = createParameterInfo();
    myParameterInfos.add(parameterInfo);
    myTypeCodeFraments.add(createParameterTypeCodeFragment(""));
    myDefaultValuesCodeFragments.add(createDefaultValueCodeFragment(parameterInfo.getDefaultValue()));
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
    P info = myParameterInfos.get(rowIndex);
    switch (columnIndex) {
      case 0:
        return myTypeCodeFraments.get(rowIndex);
      case 1:
        return info.getName();
      case 2:
        return myDefaultValuesCodeFragments.get(rowIndex);
      case 3:
        return Boolean.valueOf(info.isUseAnySingleVariable());

      default:
        throw new IllegalArgumentException();
    }
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= myParameterInfos.size()) return;
    String s = aValue instanceof String ? (String)aValue : "";
    s = s.trim();
    P info = myParameterInfos.get(rowIndex);
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
        info.setUseAnySingleVariable(((Boolean)aValue).booleanValue());
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

      case 3:
        if (myDialog.isGenerateDelegate()) return false;
        // fallback
      case 2:
        return !isEllipsisType(rowIndex) && myParameterInfos.get(rowIndex).getOldIndex() < 0;

      default:
        throw new IllegalArgumentException();
    }
  }

  public void setParameterInfos(List<P> parameterInfos) {
    myParameterInfos = parameterInfos;
    myTypeCodeFraments = new ArrayList<PsiCodeFragment>(parameterInfos.size());
    myDefaultValuesCodeFragments = new ArrayList<PsiCodeFragment>(parameterInfos.size());
    for (P parameterInfo : parameterInfos) {
      myTypeCodeFraments.add(createParameterTypeCodeFragment(parameterInfo.getTypeText()));
      myDefaultValuesCodeFragments.add(createDefaultValueCodeFragment(parameterInfo.getDefaultValue()));
    }
  }

}
