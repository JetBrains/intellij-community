// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface TouchbarDataKeys {
  DataKey<ActionGroup> ACTIONS_KEY = DataKey.create("TouchBarActions");

  Key<ActionGroupDesc> ACTIONS_DESCRIPTOR_KEY = Key.create("TouchBarActions.Descriptor");
  Key<DlgButtonDesc> DIALOG_BUTTON_DESCRIPTOR_KEY = Key.create("TouchBar.Dialog.SouthPanel.Button.Descriptor");

  static void putClientPropertyShowMode(@NotNull ActionGroup group, boolean showText, boolean replaceEsc) {
    group.getTemplatePresentation().putClientProperty(ACTIONS_DESCRIPTOR_KEY, new ActionGroupDesc(showText, replaceEsc));
  }

  static void putClientPropertyDialogButton(@NotNull JButton button, boolean isMainGroup, boolean isDefalut, int orderIndex) {
    UIUtil.putClientProperty(button, DIALOG_BUTTON_DESCRIPTOR_KEY, new DlgButtonDesc(isMainGroup, isDefalut, orderIndex));
  }

  class DlgButtonDesc {
    public final boolean isMainGroup;
    public final boolean isDefalut;
    public final int orderIndex;

    DlgButtonDesc(boolean isMainGroup, boolean isDefalut, int orderIndex) {
      this.isMainGroup = isMainGroup;
      this.isDefalut = isDefalut;
      this.orderIndex = orderIndex;
    }
  }
  class ActionGroupDesc {
    public final boolean showText;
    public final boolean replaceEsc;

    public ActionGroupDesc(boolean showText, boolean replaceEsc) {
      this.showText = showText;
      this.replaceEsc = replaceEsc;
    }
  }
}
