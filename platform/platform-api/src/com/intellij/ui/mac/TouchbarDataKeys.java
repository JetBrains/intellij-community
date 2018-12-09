// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface TouchbarDataKeys {
  DataKey<ActionGroup> ACTIONS_KEY = DataKey.create("TouchBarActions");

  Key<ActionDesc> ACTIONS_DESCRIPTOR_KEY = Key.create("TouchBarActions.Descriptor");
  Key<DlgButtonDesc> DIALOG_BUTTON_DESCRIPTOR_KEY = Key.create("TouchBar.Dialog.SouthPanel.Button.Descriptor");

  @NotNull
  static ActionDesc putActionDescriptor(@NotNull AnAction action) {
    ActionDesc result = action.getTemplatePresentation().getClientProperty(ACTIONS_DESCRIPTOR_KEY);
    if (result == null)
      action.getTemplatePresentation().putClientProperty(ACTIONS_DESCRIPTOR_KEY, result = new ActionDesc());
    return result;
  }

  @NotNull
  static DlgButtonDesc putDialogButtonDescriptor(@NotNull JButton button, int orderIndex) {
    return putDialogButtonDescriptor(button, orderIndex, false);
  }

  @NotNull
  static DlgButtonDesc putDialogButtonDescriptor(@NotNull JButton button, int orderIndex, boolean isMainGroup) {
    DlgButtonDesc result = UIUtil.getClientProperty(button, DIALOG_BUTTON_DESCRIPTOR_KEY);
    if (result == null)
      UIUtil.putClientProperty(button, DIALOG_BUTTON_DESCRIPTOR_KEY, result = new DlgButtonDesc(orderIndex));
    result.setMainGroup(isMainGroup);
    return result;
  }

  class DlgButtonDesc {
    private final int myOrderIndex;
    private boolean myIsMainGroup = false;
    private boolean myIsDefault = false;

    DlgButtonDesc(int orderIndex) { this.myOrderIndex = orderIndex; }

    public boolean isMainGroup() { return myIsMainGroup; }
    public boolean isDefault() { return myIsDefault; }
    public int getOrder() { return myOrderIndex; }

    public DlgButtonDesc setMainGroup(boolean mainGroup) { myIsMainGroup = mainGroup; return this; }
    public DlgButtonDesc setDefault(boolean aDefault) { myIsDefault = aDefault; return this; }
  }

  class ActionDesc {
    private boolean myShowText = false;
    private boolean myShowImage = true;
    private boolean myReplaceEsc = false;
    private boolean myCombineWithDlgButtons = false;
    private boolean myIsMainGroup = false;
    private JComponent myContextComponent = null;

    public boolean isShowText() { return myShowText; }
    public boolean isShowImage() { return myShowImage; }
    public boolean isReplaceEsc() { return myReplaceEsc; }
    public boolean isCombineWithDlgButtons() { return myCombineWithDlgButtons; }
    public boolean isMainGroup() { return myIsMainGroup; }
    public JComponent getContextComponent() { return myContextComponent; }

    public ActionDesc setShowText(boolean showText) { myShowText = showText; return this; }
    public ActionDesc setShowImage(boolean showImage) { myShowImage = showImage; return this; }
    public ActionDesc setReplaceEsc(boolean replaceEsc) { myReplaceEsc = replaceEsc; return this; }
    public ActionDesc setCombineWithDlgButtons(boolean combineWithDlgButtons) { myCombineWithDlgButtons = combineWithDlgButtons; return this; }
    public ActionDesc setMainGroup(boolean mainGroup) { myIsMainGroup = mainGroup; return this; }
    public ActionDesc setContextComponent(JComponent contextComponent) { myContextComponent = contextComponent; return this; }
  }
}
