// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import javax.swing.*;

/** @deprecated Use com.intellij.ui.mac.touchbar.Touchbar.setActions instead */
@Deprecated(forRemoval = true)
public interface TouchbarDataKeys {

  /** @deprecated Use com.intellij.ui.mac.touchbar.Touchbar.setActions instead */
  @Deprecated
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

  /** @deprecated Use com.intellij.ui.mac.touchbar.Touchbar.setActions instead */
  @Deprecated(forRemoval = true)
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
