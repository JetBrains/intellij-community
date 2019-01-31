// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TwoLineProgressIndicator extends OneLineProgressIndicator {
  @Override
  protected void createCompactTextAndProgress() {
    JPanel textWrapper = new NonOpaquePanel(new BorderLayout());
    textWrapper.add(myText, BorderLayout.CENTER);
    myText.recomputeSize();

    NonOpaquePanel progressWrapper = new NonOpaquePanel(new BorderLayout());
    progressWrapper.setBorder(JBUI.Borders.emptyRight(4));
    progressWrapper.add(myProgress, BorderLayout.CENTER);

    JComponent component = getComponent();
    component.add(textWrapper, BorderLayout.NORTH);
    component.add(progressWrapper, BorderLayout.CENTER);
  }
}