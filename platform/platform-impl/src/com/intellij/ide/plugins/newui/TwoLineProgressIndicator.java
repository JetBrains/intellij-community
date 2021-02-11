// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TwoLineProgressIndicator extends OneLineProgressIndicator {
  public TwoLineProgressIndicator() {
    this(true);
  }

  public TwoLineProgressIndicator(boolean canBeCancelled) {
    super(true, canBeCancelled);
  }

  @Override
  protected void createCompactTextAndProgress(@NotNull JPanel component) {
    JPanel textWrapper = new NonOpaquePanel(new BorderLayout());
    textWrapper.add(myText, BorderLayout.CENTER);
    myText.recomputeSize();

    NonOpaquePanel progressWrapper = new NonOpaquePanel(new BorderLayout());
    progressWrapper.setBorder(JBUI.Borders.emptyRight(4));
    progressWrapper.add(myProgress, BorderLayout.CENTER);

    component.add(textWrapper, BorderLayout.NORTH);
    component.add(progressWrapper, BorderLayout.CENTER);
  }
}