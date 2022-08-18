// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.awt.*;

public abstract class LayoutCodeGenerator {
  static final Method ourSetLayoutMethod = Method.getMethod("void setLayout(java.awt.LayoutManager)");
  static final Type ourContainerType = Type.getType(Container.class);
  static final Method ourAddMethod = Method.getMethod("void add(java.awt.Component,java.lang.Object)");
  static final Method ourAddNoConstraintMethod = Method.getMethod("java.awt.Component add(java.awt.Component)");

  public void generateContainerLayout(final LwContainer lwContainer, final GeneratorAdapter generator, final int componentLocal) {
  }

  public abstract void generateComponentLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal,
                                               final int parentLocal, final String formClassName);

  static void newDimensionOrNull(final GeneratorAdapter generator, final Dimension dimension) {
    if (dimension.width == -1 && dimension.height == -1) {
      generator.visitInsn(Opcodes.ACONST_NULL);
    }
    else {
      AsmCodeGenerator.pushPropValue(generator, "java.awt.Dimension", dimension);
    }
  }

  public String mapComponentClass(final String componentClassName) {
    return componentClassName;
  }
}
