// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.awt.*;

public final class FlowLayoutCodeGenerator extends LayoutCodeGenerator {
  private static final Type ourFlowLayoutType = Type.getType(FlowLayout.class);
  private static final Method ourConstructor = Method.getMethod("void <init>(int,int,int)");

  @Override
  public void generateContainerLayout(final LwContainer lwContainer, final GeneratorAdapter generator, final int componentLocal) {
    generator.loadLocal(componentLocal);

    FlowLayout flowLayout = (FlowLayout) lwContainer.getLayout();
    generator.newInstance(ourFlowLayoutType);
    generator.dup();
    generator.push(flowLayout.getAlignment());
    generator.push(flowLayout.getHgap());
    generator.push(flowLayout.getVgap());
    generator.invokeConstructor(ourFlowLayoutType, ourConstructor);

    generator.invokeVirtual(ourContainerType, ourSetLayoutMethod);
  }
  @Override
  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal,
                                      final String formClassName) {
    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    generator.invokeVirtual(ourContainerType, ourAddNoConstraintMethod);
  }
}
