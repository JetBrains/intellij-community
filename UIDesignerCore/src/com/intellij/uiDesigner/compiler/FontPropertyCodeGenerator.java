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
import com.intellij.uiDesigner.lw.FontDescriptor;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 * @noinspection HardCodedStringLiteral
 */
public class FontPropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type ourFontType = Type.getType(Font.class);
  private static final Type ourUIManagerType = Type.getType(UIManager.class);
  private static final Type ourObjectType = Type.getType(Object.class);

  private static final Method ourInitMethod = Method.getMethod("void <init>(java.lang.String,int,int)");
  private static final Method ourGetFontMethod = new Method("getFont", ourFontType, new Type[] { ourObjectType } );

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    FontDescriptor descriptor = (FontDescriptor) value;
    final Font font = descriptor.getFont();
    if (font != null) {
      generator.newInstance(ourFontType);
      generator.dup();
      generator.push(font.getName());
      generator.push(font.getStyle());
      generator.push(font.getSize());
      generator.invokeConstructor(ourFontType, ourInitMethod);
    }
    else if (descriptor.getSwingFont() != null) {
      generator.push(descriptor.getSwingFont());
      generator.invokeStatic(ourUIManagerType, ourGetFontMethod);
    }
    else {
      throw new IllegalStateException("Unknown font type");
    }
  }
}
