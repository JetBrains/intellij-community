// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class AddItemPopupAction<ItemType> extends ChooseAndAddAction<ItemType> {
  private final @Nls(capitalization = Nls.Capitalization.Title) String myTitle;
  private final Icon myIcon;
  private final int myIndex;

  AddItemPopupAction(ClasspathPanel classpathPanel,
                     int index,
                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                     Icon icon) {
    super(classpathPanel);
    myTitle = title;
    myIcon = icon;
    myIndex = index;
  }

  public boolean hasSubStep() {
    return false;
  }

  public @Nullable PopupStep createSubStep() {
    return null;
  }

  public @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public int getIndex() {
    return myIndex;
  }

}
