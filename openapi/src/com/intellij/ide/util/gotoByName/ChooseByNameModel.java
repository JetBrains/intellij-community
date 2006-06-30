/*
* Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ChooseByNameModel {
  String getPromptText();

  String getNotInMessage();
  String getNotFoundMessage();
  /** return null to hide checkbox panel */
  @Nullable String getCheckBoxName();

  /**
   * @deprecated Mark mnemonic char with '&' ('&&' for mac if mnemonic char is 'N') in checkbox name instead
   */
  char getCheckBoxMnemonic();


  boolean loadInitialCheckBoxState();
  void saveInitialCheckBoxState(boolean state);

  ListCellRenderer getListCellRenderer();

  /**
   * Returns the list of names to show in the chooser.
   *
   * @param checkBoxState the current state of the chooser checkbox (for example, [x] Include non-project classes for Ctrl-N)
   * @return the names to show. All items in the returned array must be non-null.
   *
   */
  String[] getNames(boolean checkBoxState);
  Object[] getElementsByName(String name, boolean checkBoxState);
  @Nullable
  String getElementName(Object element);
}