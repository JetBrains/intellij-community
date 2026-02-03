// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.compiler;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;

public abstract class PropertyCodeGenerator {
  public abstract void generatePushValue(final GeneratorAdapter generator, final Object value);

  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final InstrumentationClassFinder.PseudoClass componentClass, final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        GetFontMethodProvider fontMethodProvider,
                                        final int componentLocal, final String formClassName) throws IOException, ClassNotFoundException {
    return false;
  }

  public void generateClassStart(AsmCodeGenerator.FormClassVisitor visitor, final String name, final InstrumentationClassFinder classFinder) {
  }

  public void generateClassEnd(AsmCodeGenerator.FormClassVisitor visitor) {
  }
}
