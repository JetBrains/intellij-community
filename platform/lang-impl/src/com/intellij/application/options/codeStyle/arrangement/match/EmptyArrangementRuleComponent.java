// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ui.ArrangementRepresentationAware;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class EmptyArrangementRuleComponent extends JPanel implements ArrangementRepresentationAware {
  
  private final int myHeight;
  
  public EmptyArrangementRuleComponent(int height) {
    super(new GridBagLayout());
    myHeight = height;
    add(new JLabel(ApplicationBundle.message("arrangement.text.empty.rule")), new GridBag().anchor(GridBagConstraints.WEST));
    setBackground(UIUtil.getDecoratedRowColor());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width, myHeight);
  }
}
