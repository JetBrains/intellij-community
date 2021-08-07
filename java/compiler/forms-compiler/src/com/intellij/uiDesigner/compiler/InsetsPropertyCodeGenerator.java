// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.compiler;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.awt.*;

public class InsetsPropertyCodeGenerator extends PropertyCodeGenerator {
  private final Type myInsetsType = Type.getType(Insets.class);

  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    final Insets insets = (Insets)value;
    generator.newInstance(myInsetsType);
    generator.dup();
    generator.push(insets.top);
    generator.push(insets.left);
    generator.push(insets.bottom);
    generator.push(insets.right);
    generator.invokeConstructor(myInsetsType, Method.getMethod("void <init>(int,int,int,int)"));
  }
}
