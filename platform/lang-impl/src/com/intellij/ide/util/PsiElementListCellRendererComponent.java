// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ui.popup.list.SelectablePanel;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

final class PsiElementListCellRendererComponent extends SelectablePanel {

  PsiElementListCellRendererComponent() {
    setLayout(new BorderLayout());
  }

  private final class MyAccessibleContext extends JPanel.AccessibleJPanel {
    @Override
    public String getAccessibleName() {
      LayoutManager lm = getLayout();
      assert lm instanceof BorderLayout;
      Component leftCellRendererComp = ((BorderLayout)lm).getLayoutComponent(BorderLayout.WEST);
      return leftCellRendererComp instanceof Accessible ?
             leftCellRendererComp.getAccessibleContext().getAccessibleName() : super.getAccessibleName();
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new MyAccessibleContext();
    }
    return accessibleContext;
  }
}
