// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.EnumDescriptor;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;

public final class EnumPropertyCodeGenerator extends PropertyCodeGenerator {
  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    final EnumDescriptor descriptor = (EnumDescriptor)value;
    // the declared type of the constant, not the type of the constant itself: a constant with a class body is an
    // instance of an anonymous subclass, and the field is declared in the enum class
    final Type enumType = AsmCodeGenerator.typeFromClassName(descriptor.getClassName());
    generator.getStatic(enumType, descriptor.getConstantName(), enumType);
  }
}
