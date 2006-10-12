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
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;

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
  private static final Type ourStringType = Type.getType(String.class);

  private static final Method ourInitMethod = Method.getMethod("void <init>(java.lang.String,int,int)");
  private static final Method ourUIManagerGetFontMethod = new Method("getFont", ourFontType, new Type[] { ourObjectType } );
  private static final Method ourGetNameMethod = new Method("getName", ourStringType, new Type[0] );
  private static final Method ourGetSizeMethod = new Method("getSize", Type.INT_TYPE, new Type[0] );
  private static final Method ourGetStyleMethod = new Method("getStyle", Type.INT_TYPE, new Type[0] );

  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final Class componentClass,
                                        final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        final int componentLocal) {
    FontDescriptor descriptor = (FontDescriptor) property.getPropertyValue(lwComponent);
    if (descriptor.isFixedFont() && !descriptor.isFullyDefinedFont()) {
      final int fontLocal = generator.newLocal(ourFontType);
      generator.loadLocal(componentLocal);

      Type componentType = AsmCodeGenerator.typeFromClassName(lwComponent.getComponentClassName());
      Method getFontMethod = new Method(property.getReadMethodName(), ourFontType, new Type[0] );
      generator.invokeVirtual(componentType, getFontMethod);
      generator.storeLocal(fontLocal);

      generator.loadLocal(componentLocal);
      generator.newInstance(ourFontType);
      generator.dup();
      if (descriptor.getFontName() != null) {
        generator.push(descriptor.getFontName());
      }
      else {
        generator.loadLocal(fontLocal);
        generator.invokeVirtual(ourFontType, ourGetNameMethod);
      }

      if (descriptor.getFontStyle() >= 0) {
        generator.push(descriptor.getFontStyle());
      }
      else {
        generator.loadLocal(fontLocal);
        generator.invokeVirtual(ourFontType, ourGetStyleMethod);
      }

      if (descriptor.getFontSize() >= 0) {
        generator.push(descriptor.getFontSize());
      }
      else {
        generator.loadLocal(fontLocal);
        generator.invokeVirtual(ourFontType, ourGetSizeMethod);
      }

      generator.invokeConstructor(ourFontType, ourInitMethod);
      Method setFontMethod = new Method(property.getWriteMethodName(), Type.VOID_TYPE, new Type[] { ourFontType } );
      generator.invokeVirtual(componentType, setFontMethod);
      return true;
    }
    return false;
  }

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    FontDescriptor descriptor = (FontDescriptor) value;
    if (descriptor.isFixedFont()) {
      if (!descriptor.isFullyDefinedFont()) throw new IllegalStateException("Unexpected font state");
      generator.newInstance(ourFontType);
      generator.dup();
      generator.push(descriptor.getFontName());
      generator.push(descriptor.getFontStyle());
      generator.push(descriptor.getFontSize());
      generator.invokeConstructor(ourFontType, ourInitMethod);
    }
    else if (descriptor.getSwingFont() != null) {
      generator.push(descriptor.getSwingFont());
      generator.invokeStatic(ourUIManagerType, ourUIManagerGetFontMethod);
    }
    else {
      throw new IllegalStateException("Unknown font type");
    }
  }
}
