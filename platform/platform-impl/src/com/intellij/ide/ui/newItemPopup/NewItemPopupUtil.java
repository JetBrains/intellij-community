// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.newItemPopup;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class NewItemPopupUtil {

  private NewItemPopupUtil() {} //just don't do it

  public static JBPopup createNewItemPopup(@NotNull @PopupTitle String title, @NotNull JComponent content, @Nullable JComponent preferableFocusComponent) {
    return JBPopupFactory.getInstance()
    .createComponentPopupBuilder(content, preferableFocusComponent)
    .setTitle(title)
    .setResizable(false)
    .setModalContext(true)
    .setFocusable(true)
    .setRequestFocus(true)
    .setMovable(true)
    .setBelongsToGlobalPopupStack(true)
    .setCancelKeyEnabled(true)
    .setCancelOnWindowDeactivation(false)
    .setCancelOnClickOutside(true)
    .addUserData("SIMPLE_WINDOW")
    .createPopup();

  }
}
