// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.lw.LwComponent;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class CardLayoutCodeGenerator extends SimpleLayoutCodeGenerator {
  private static final Method ourGetLayoutMethod = Method.getMethod("java.awt.LayoutManager getLayout()");
  private static final Method ourShowMethod = Method.getMethod("void show(java.awt.Container,java.lang.String)");

  CardLayoutCodeGenerator() {
    super(Type.getType(CardLayout.class));
  }

  @Override
  public void generateComponentLayout(LwComponent lwComponent,
                                      GeneratorAdapter generator,
                                      int componentLocal,
                                      int parentLocal,
                                      String formClassName) {
    super.generateComponentLayout(lwComponent, generator, componentLocal, parentLocal, formClassName);

    String defaultCard = (String)lwComponent.getParent().getClientProperty(UIFormXmlConstants.LAYOUT_CARD);
    if (lwComponent.getId().equals(defaultCard)) {
      generator.loadLocal(parentLocal);
      generator.invokeVirtual(ourContainerType, ourGetLayoutMethod);
      generator.checkCast(myLayoutType);
      generator.loadLocal(parentLocal);
      generator.push((String) lwComponent.getCustomLayoutConstraints());
      generator.invokeVirtual(myLayoutType, ourShowMethod);
    }
  }
}