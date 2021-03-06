// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ChooseByNameModel {

  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getPromptText();

  @NotNull
  @NlsContexts.Label
  String getNotInMessage();

  @NotNull
  @NlsContexts.Label
  String getNotFoundMessage();

  /**
   * return null to hide checkbox panel
   */
  @Nullable
  @NlsContexts.Label
  String getCheckBoxName();

  /**
   * @deprecated Mark mnemonic char with '&' ('&&' for mac if mnemonic char is 'N') in checkbox name instead
   */
  @Deprecated
  default char getCheckBoxMnemonic() { return 0; }


  boolean loadInitialCheckBoxState();

  void saveInitialCheckBoxState(boolean state);

  @NotNull
  ListCellRenderer getListCellRenderer();

  /**
   * Returns the list of names to show in the chooser.
   *
   * @param checkBoxState the current state of the chooser checkbox (for example, [x] Include non-project classes for Ctrl-N)
   * @return the names to show. All items in the returned array must be non-null.
   */
  String @NotNull @Nls [] getNames(boolean checkBoxState);

  Object @NotNull [] getElementsByName(@NotNull String name, boolean checkBoxState, @NotNull String pattern);

  @Nullable
  String getElementName(@NotNull Object element);

  String @NotNull [] getSeparators();

  @Nullable
  String getFullName(@NotNull Object element);

  @Nullable @NonNls
  String getHelpId();

  boolean willOpenEditor();

  boolean useMiddleMatching();
}