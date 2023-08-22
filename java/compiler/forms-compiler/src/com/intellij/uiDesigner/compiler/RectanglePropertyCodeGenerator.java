// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.awt.*;

public final class RectanglePropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type myRectangleType = Type.getType(Rectangle.class);
  private static final Method myInitMethod = Method.getMethod("void <init>(int,int,int,int)");

  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    final Rectangle rc = (Rectangle) value;
    generator.newInstance(myRectangleType);
    generator.dup();
    generator.push(rc.x);
    generator.push(rc.y);
    generator.push(rc.width);
    generator.push(rc.height);
    generator.invokeConstructor(myRectangleType, myInitMethod);
  }
}
