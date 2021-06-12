// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.compiler;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.awt.*;

/**
 * @noinspection HardCodedStringLiteral
 */
public class DimensionPropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type myDimensionType = Type.getType(Dimension.class);
  private static final Method myInitMethod = Method.getMethod("void <init>(int,int)");

  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    Dimension dimension = (Dimension) value;
    generator.newInstance(myDimensionType);
    generator.dup();
    generator.push(dimension.width);
    generator.push(dimension.height);
    generator.invokeConstructor(myDimensionType, myInitMethod);
  }
}
