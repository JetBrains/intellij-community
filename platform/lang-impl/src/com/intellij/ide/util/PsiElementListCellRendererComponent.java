// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

final class PsiElementListCellRendererComponent extends JPanel {

  PsiElementListCellRendererComponent() {
    super(new BorderLayout());
  }

  private class MyAccessibleContext extends JPanel.AccessibleJPanel {
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
