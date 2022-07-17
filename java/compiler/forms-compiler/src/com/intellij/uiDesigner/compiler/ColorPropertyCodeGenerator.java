// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.ColorDescriptor;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.awt.*;

public final class ColorPropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type ourColorType = Type.getType(Color.class);
  private static final Type ourObjectType = Type.getType(Object.class);
  private static final Type ourUIManagerType = Type.getType("Ljavax/swing/UIManager;");
  private static final Type ourSystemColorType = Type.getType(SystemColor.class);

  private static final Method ourInitMethod = Method.getMethod("void <init>(int)");
  private static final Method ourGetColorMethod = new Method("getColor", ourColorType, new Type[] { ourObjectType } );

  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    ColorDescriptor descriptor = (ColorDescriptor) value;
    if (descriptor.getColor() != null) {
      generator.newInstance(ourColorType);
      generator.dup();
      generator.push(descriptor.getColor().getRGB());
      generator.invokeConstructor(ourColorType, ourInitMethod);
    }
    else if (descriptor.getSwingColor() != null) {
      generator.push(descriptor.getSwingColor());
      generator.invokeStatic(ourUIManagerType, ourGetColorMethod);
    }
    else if (descriptor.getSystemColor() != null) {
      generator.getStatic(ourSystemColorType, descriptor.getSystemColor(), ourSystemColorType);
    }
    else if (descriptor.getAWTColor() != null) {
      generator.getStatic(ourColorType, descriptor.getAWTColor(), ourColorType);
    }
    else if (descriptor.isColorSet()) {
      throw new IllegalStateException("Unknown color type");
    }
  }
}
