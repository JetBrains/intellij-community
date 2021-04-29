// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

/**
 * Layout code generator shared between BorderLayout and CardLayout.
 */
public class SimpleLayoutCodeGenerator extends LayoutCodeGenerator {
  protected final Type myLayoutType;
  private static final Method ourConstructor = Method.getMethod("void <init>(int,int)");

  public SimpleLayoutCodeGenerator(final Type layoutType) {
    myLayoutType = layoutType;
  }

  @Override
  public void generateContainerLayout(final LwContainer lwContainer, final GeneratorAdapter generator, final int componentLocal) {
    generator.loadLocal(componentLocal);

    generator.newInstance(myLayoutType);
    generator.dup();
    generator.push(Utils.getHGap(lwContainer.getLayout()));
    generator.push(Utils.getVGap(lwContainer.getLayout()));

    generator.invokeConstructor(myLayoutType, ourConstructor);

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
    generator.push((String) lwComponent.getCustomLayoutConstraints());
    generator.invokeVirtual(ourContainerType, ourAddMethod);
  }
}
