// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.compiler;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;

public class EnumPropertyCodeGenerator extends PropertyCodeGenerator {
  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    final Type enumType = Type.getType(value.getClass());
    generator.getStatic(enumType, value.toString(), enumType);
  }
}
