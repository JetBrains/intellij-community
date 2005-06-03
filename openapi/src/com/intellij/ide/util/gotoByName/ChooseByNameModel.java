
package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ChooseByNameModel {
  String getPromptText();

  String getNotInMessage();
  String getNotFoundMessage();
  /** return null to hide checkbox panel */
  @Nullable String getCheckBoxName();
  char getCheckBoxMnemonic();
  boolean loadInitialCheckBoxState();
  void saveInitialCheckBoxState(boolean state);

  ListCellRenderer getListCellRenderer();

  String[] getNames(boolean checkBoxState);
  Object[] getElementsByName(String name, boolean checkBoxState);
  @Nullable
  String getElementName(Object element);
}