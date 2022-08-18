// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.compiler;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

public final class ListModelPropertyCodeGenerator extends PropertyCodeGenerator {
  private final Type myListModelType;
  private static final Method ourInitMethod = Method.getMethod("void <init>()");
  private static final Method ourAddElementMethod = Method.getMethod("void addElement(java.lang.Object)");

  ListModelPropertyCodeGenerator(final Class aClass) {
    myListModelType = Type.getType(aClass);
  }

  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    String[] items = (String[]) value;
    int listModelLocal = generator.newLocal(myListModelType);

    generator.newInstance(myListModelType);
    generator.dup();
    generator.invokeConstructor(myListModelType, ourInitMethod);
    generator.storeLocal(listModelLocal);

    for (String item : items) {
      generator.loadLocal(listModelLocal);
      generator.push(item);
      generator.invokeVirtual(myListModelType, ourAddElementMethod);
    }

    generator.loadLocal(listModelLocal);
  }
}
