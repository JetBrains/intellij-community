/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.Type;
import com.intellij.uiDesigner.lw.ColorDescriptor;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class ColorPropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type myColorType = Type.getType(Color.class);
  private static final Type myObjectType = Type.getType(Object.class);
  private static final Type myUIManagerType = Type.getType(UIManager.class);
  private static final Type mySystemColorType = Type.getType(SystemColor.class);

  private static Method myInitMethod = Method.getMethod("void <init>(int)");

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    ColorDescriptor descriptor = (ColorDescriptor) value;
    if (descriptor.getColor() != null) {
      generator.newInstance(myColorType);
      generator.dup();
      generator.push(descriptor.getColor().getRGB());
      generator.invokeConstructor(myColorType, myInitMethod);
    }
    else if (descriptor.getSwingColor() != null) {
      generator.push(descriptor.getSwingColor());
      generator.invokeStatic(myUIManagerType,
                             new Method("getColor", myColorType, new Type[] { myObjectType } ));
    }
    else if (descriptor.getSystemColor() != null) {
      generator.getStatic(mySystemColorType, descriptor.getSystemColor(), mySystemColorType);
    }
    else if (descriptor.getAWTColor() != null) {
      generator.getStatic(myColorType, descriptor.getAWTColor(), myColorType);
    }
  }
}
