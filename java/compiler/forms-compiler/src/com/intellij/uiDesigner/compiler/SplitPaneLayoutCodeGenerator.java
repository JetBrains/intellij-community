// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwSplitPane;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import javax.swing.*;

public final class SplitPaneLayoutCodeGenerator extends LayoutCodeGenerator {
  private final Type mySplitPaneType = Type.getType(JSplitPane.class);
  private final Method mySetLeftMethod = Method.getMethod("void setLeftComponent(java.awt.Component)");
  private final Method mySetRightMethod = Method.getMethod("void setRightComponent(java.awt.Component)");

  @Override
  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal,
                                      final String formClassName) {
    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    if (LwSplitPane.POSITION_LEFT.equals(lwComponent.getCustomLayoutConstraints())) {
      generator.invokeVirtual(mySplitPaneType, mySetLeftMethod);
    }
    else {
      generator.invokeVirtual(mySplitPaneType, mySetRightMethod);
    }
  }
}
