// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.SmartTypePointerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemListener;

/**
 * @author dsl
 */
public class TypeSelector {
  private final PsiType myType;
  private final JComponent myComponent;
  private final MyComboBoxModel myComboBoxModel;
  private final Project myProject;

  public TypeSelector(PsiType type, @NotNull Project project) {
    myType = type;
    myProject = project;
    myComponent = new JLabel(myType.getPresentableText());
    myComboBoxModel = null;
  }

  public TypeSelector(@NotNull Project project) {
    myProject = project;
    myComboBoxModel = new MyComboBoxModel();
    myComponent = new ComboBox();
    ((ComboBox) myComponent).setModel(myComboBoxModel);
    myType = null;
  }

  public void setTypes(PsiType[] types) {
    if(myComboBoxModel == null) return;
    PsiType oldType;
    if (myComboBoxModel.getSize() > 0) {
      oldType = getSelectedType();
    } else {
      oldType = null;
    }
    myComboBoxModel.setSuggestions(wrapToItems(types, myProject));
    if(oldType != null) {
      for (int i = 0; i < types.length; i++) {
        PsiType type = types[i];
        if(type.equals(oldType)) {
          ((JComboBox) myComponent).setSelectedIndex(i);
          return;
        }
      }
    }
    if (types.length > 0) {
      ((JComboBox) myComponent).setSelectedIndex(0);
    }
  }

  public PsiType[] getTypes() {
    PsiType[] types = PsiType.createArray(myComboBoxModel.mySuggestions.length);
    for (int i = 0; i < types.length; i++) {
      types[i] = myComboBoxModel.mySuggestions[i].getType();
    }
    return types;
  }

  private static PsiTypeItem[] wrapToItems(PsiType @NotNull [] types, @NotNull Project project) {
    PsiTypeItem[] result = new PsiTypeItem[types.length];
    for (int i = 0; i < result.length; i++) {
      result [i] = new PsiTypeItem(types [i], project);
    }
    return result;
  }


  public void addItemListener(ItemListener aListener) {
    if(myComponent instanceof JComboBox) {
      ((JComboBox) myComponent).addItemListener(aListener);
    }
  }

  public void removeItemListener(ItemListener aListener) {
    if (myComponent instanceof JComboBox) {
      ((JComboBox) myComponent).removeItemListener(aListener);
    }
  }

  public ItemListener[] getItemListeners() {
    if (myComponent instanceof JComboBox) {
      return ((JComboBox) myComponent).getItemListeners();
    } else {
      return new ItemListener[0];
    }
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public JComponent getFocusableComponent() {
    if (myComponent instanceof JComboBox) {
      return myComponent;
    } else {
      return null;
    }
  }

  @Nullable
  public PsiType getSelectedType() {
    if (myComponent instanceof JLabel) {
      return myType;
    }
    PsiTypeItem selItem = (PsiTypeItem)((JComboBox)myComponent).getSelectedItem();
    return selItem == null ? null : selItem.getType();
  }

  public void selectType(@NotNull PsiType type) {
    if (myComponent instanceof JComboBox) {
      ((JComboBox)myComponent).setSelectedItem(new PsiTypeItem(type, myProject));
    }
  }

  private static class MyComboBoxModel extends DefaultComboBoxModel {
    private PsiTypeItem[] mySuggestions;

    MyComboBoxModel() {
      mySuggestions = new PsiTypeItem[0];
    }

    // implements javax.swing.ListModel
    @Override
    public int getSize() {
      return mySuggestions.length;
    }

    // implements javax.swing.ListModel
    @Override
    public Object getElementAt(int index) {
      return mySuggestions[index];
    }

    public void setSuggestions(PsiTypeItem[] suggestions) {
      fireIntervalRemoved(this, 0, mySuggestions.length);
      mySuggestions = suggestions;
      fireIntervalAdded(this, 0, mySuggestions.length);
    }
  }

  private static final class PsiTypeItem {
    private final PsiType myType;
    private final SmartTypePointer myTypePointer;

    private PsiTypeItem(@NotNull PsiType type, @NotNull Project project) {
      myType = type;
      myTypePointer = SmartTypePointerManager.getInstance(project).createSmartTypePointer(type);
    }

    @Nullable
    public PsiType getType() {
      return myTypePointer.getType();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PsiTypeItem that = (PsiTypeItem)o;

      if (!Comparing.equal(getType(), that.getType())) return false;

      return true;
    }

    @Override
    public int hashCode() {
      PsiType type = getType();
      return type != null ? type.hashCode() : 0;
    }

    @Override
    public String toString() {
      return myType.getPresentableText();
    }
  }

}
