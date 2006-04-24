/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.diagnostic.logging;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 22-Mar-2006
 */
public abstract class AdditionalTabComponent extends JPanel implements Disposable {
  protected AdditionalTabComponent(LayoutManager layout) {
    super(layout);
  }

  protected AdditionalTabComponent() {
  }

  public abstract String getTabTitle();

  public JComponent getComponent(){
    return this;
  }
}
