// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

public final class ToolBarLayoutCodeGenerator extends LayoutCodeGenerator {
  private final static Method ourAddMethod = Method.getMethod("java.awt.Component add(java.awt.Component)");

  @Override
  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal,
                                      final String formClassName) {
    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    generator.invokeVirtual(ourContainerType, ourAddMethod);
  }
}
