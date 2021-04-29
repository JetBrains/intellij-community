// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.IconDescriptor;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import javax.swing.*;

public class IconPropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type ourImageIconType = Type.getType(ImageIcon.class);
  private static final Method ourInitMethod = Method.getMethod("void <init>(java.net.URL)");
  private static final Method ourGetResourceMethod = Method.getMethod("java.net.URL getResource(java.lang.String)");
  private static final Method ourGetClassMethod = new Method("getClass", "()Ljava/lang/Class;");
  private static final Type ourObjectType = Type.getType(Object.class);
  private static final Type ourClassType = Type.getType(Class.class);

  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    IconDescriptor descriptor = (IconDescriptor) value;
    generator.newInstance(ourImageIconType);
    generator.dup();

    generator.loadThis();
    generator.invokeVirtual(ourObjectType, ourGetClassMethod);
    generator.push("/" + descriptor.getIconPath());
    generator.invokeVirtual(ourClassType, ourGetResourceMethod);

    generator.invokeConstructor(ourImageIconType, ourInitMethod);
  }
}
