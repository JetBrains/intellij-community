/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.util.Locale;

/**
 * @author yole
 * @noinspection HardCodedStringLiteral
 */
public class FontPropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type ourFontType = Type.getType(Font.class);
  private static final Type ourUIManagerType = Type.getType("Ljavax/swing/UIManager;");
  private static final Type ourObjectType = Type.getType(Object.class);
  private static final Type ourStringType = Type.getType(String.class);
  private static final Type ourSystemType = Type.getType(System.class);
  private static final Type ourLocaleType = Type.getType(Locale.class);
  private static final Type ourStyleContextType = Type.getType(StyleContext.class);
  private static final Type ourFontUIResource = Type.getType(FontUIResource.class);

  private static final Method ourInitFontMethod = Method.getMethod("void <init>(java.lang.String,int,int)");
  private static final Method ourInitStyleContextMethod = Method.getMethod("void <init>()");
  private static final Method ourInitFontUIResourceMethod = Method.getMethod("void <init>(java.awt.Font)");

  private static final Method ourUIManagerGetFontMethod = new Method("getFont", ourFontType, new Type[]{ourObjectType});
  private static final Method ourGetNameMethod = new Method("getName", ourStringType, new Type[0]);
  private static final Method ourGetFamilyMethod = new Method("getFamily", ourStringType, new Type[0]);
  private static final Method ourGetSizeMethod = new Method("getSize", Type.INT_TYPE, new Type[0]);
  private static final Method ourGetStyleMethod = new Method("getStyle", Type.INT_TYPE, new Type[0]);
  private static final Method ourCanDisplayMethod = new Method("canDisplay", Type.BOOLEAN_TYPE, new Type[]{Type.CHAR_TYPE});
  private static final Method ourGetPropertyMethod = new Method("getProperty", ourStringType, new Type[]{ourStringType, ourStringType});
  private static final Method ourToLowerCaseMethod = new Method("toLowerCase", ourStringType, new Type[]{ourLocaleType});
  private static final Method ourStartsWithMethod = new Method("startsWith", Type.BOOLEAN_TYPE, new Type[]{ourStringType});
  private static final Method ourGetFontMethod = new Method("getFont", ourFontType, new Type[] {ourStringType, Type.INT_TYPE, Type.INT_TYPE});

  @Override
  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final InstrumentationClassFinder.PseudoClass componentClass,
                                        final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        GetFontMethodProvider fontMethodProvider,
                                        final int componentLocal, final String formClassName) {
    FontDescriptor descriptor = (FontDescriptor)property.getPropertyValue(lwComponent);
    Label fontNullLabel = generator.newLabel();
    generatePushFont(generator, fontMethodProvider, componentLocal, lwComponent, descriptor, property.getReadMethodName(), fontNullLabel);

    Method setFontMethod = new Method(property.getWriteMethodName(), Type.VOID_TYPE, new Type[]{ourFontType});
    Type componentType = AsmCodeGenerator.typeFromClassName(lwComponent.getComponentClassName());
    generator.invokeVirtual(componentType, setFontMethod);
    generator.mark(fontNullLabel);

    return true;
  }

  public static void generatePushFont(GeneratorAdapter generator,
                                      GetFontMethodProvider fontMethodProvider,
                                      int componentLocal,
                                      LwComponent lwComponent,
                                      FontDescriptor descriptor,
                                      String readMethodName,
                                      Label fontNullLabel) {
    if (descriptor.isFixedFont()) {
      generator.loadThis();

      generator.push(descriptor.getFontName());
      generator.push(descriptor.getFontStyle());
      generator.push(descriptor.getFontSize());

      generator.loadLocal(componentLocal);
      Type componentType = AsmCodeGenerator.typeFromClassName(lwComponent.getComponentClassName());
      Method getFontMethod = new Method(readMethodName, ourFontType, new Type[0]);
      generator.invokeVirtual(componentType, getFontMethod);

      generator.invokeVirtual(fontMethodProvider.getMainClassType(), fontMethodProvider.getFontMethod());
    }
    else {
      generator.push(descriptor.getSwingFont());
      generator.invokeStatic(ourUIManagerType, ourUIManagerGetFontMethod);
    }

    if (fontNullLabel != null) {
      int fontResult = generator.newLocal(ourFontType);
      generator.storeLocal(fontResult);
      generator.loadLocal(fontResult);
      generator.ifNull(fontNullLabel);
      generator.loadLocal(componentLocal);
      generator.loadLocal(fontResult);
    }
  }

  @Override
  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    throw new IllegalStateException("Unknown font type");
  }

  public static Method createGetFontMethod() {
    return new Method(AsmCodeGenerator.GET_FONT_METHOD_NAME, ourFontType,
                      new Type[]{ourStringType, Type.INT_TYPE, Type.INT_TYPE, ourFontType});
  }

  // Source code for this bytecode in com/intellij/uiDesigner/make/FormSourceCodeGenerator.java:299
  public static void buildGetFontMethod(GeneratorAdapter generator) {
    Label fontNonNull = new Label();
    generator.loadArg(3); // font
    generator.ifNonNull(fontNonNull);
    generator.push((String)null);
    generator.returnValue();

    generator.mark(fontNonNull);
    Label nameNonNull = new Label();
    generator.loadArg(0); // name
    generator.ifNonNull(nameNonNull);

    generator.loadArg(3); // font
    generator.invokeVirtual(ourFontType, ourGetNameMethod);
    int nameLocal = generator.newLocal(ourStringType);
    generator.storeLocal(nameLocal);

    Label createFont = new Label();
    generator.goTo(createFont);
    generator.mark(nameNonNull);

    generator.newInstance(ourFontType);
    generator.dup();
    generator.loadArg(0); // name
    generator.push(Font.PLAIN);
    generator.push(10);
    generator.invokeConstructor(ourFontType, ourInitFontMethod);

    int testFont = generator.newLocal(ourFontType);
    generator.storeLocal(testFont);

    Label fontGetNameLabel = new Label();
    generator.loadLocal(testFont);
    generator.push('a');
    generator.invokeVirtual(ourFontType, ourCanDisplayMethod);
    generator.ifZCmp(GeneratorAdapter.EQ, fontGetNameLabel);
    generator.loadLocal(testFont);
    generator.push('1');
    generator.invokeVirtual(ourFontType, ourCanDisplayMethod);
    generator.ifZCmp(GeneratorAdapter.EQ, fontGetNameLabel);

    generator.loadArg(0); // name
    generator.storeLocal(nameLocal);
    generator.goTo(createFont);
    generator.mark(fontGetNameLabel);

    generator.loadArg(3); // font
    generator.invokeVirtual(ourFontType, ourGetNameMethod);
    generator.storeLocal(nameLocal);
    generator.mark(createFont);

    generator.newInstance(ourFontType);
    generator.dup();

    generator.loadLocal(nameLocal);

    Label checkStyle1 = new Label();
    Label checkStyle2 = new Label();
    generator.loadArg(1); // style
    generator.ifZCmp(GeneratorAdapter.LT, checkStyle1);
    generator.loadArg(1); // style
    generator.goTo(checkStyle2);
    generator.mark(checkStyle1);
    generator.loadArg(3); // font
    generator.invokeVirtual(ourFontType, ourGetStyleMethod);
    generator.mark(checkStyle2);

    Label checkSize1 = new Label();
    Label checkSize2 = new Label();
    generator.loadArg(2); // size
    generator.ifZCmp(GeneratorAdapter.LT, checkSize1);
    generator.loadArg(2); // size
    generator.goTo(checkSize2);
    generator.mark(checkSize1);
    generator.loadArg(3); // font
    generator.invokeVirtual(ourFontType, ourGetSizeMethod);
    generator.mark(checkSize2);

    generator.invokeConstructor(ourFontType, ourInitFontMethod);
    int font = generator.newLocal(ourFontType);
    generator.storeLocal(font);

    generator.push("os.name");
    generator.push("");
    generator.invokeStatic(ourSystemType, ourGetPropertyMethod);
    generator.getStatic(ourLocaleType, "ENGLISH", ourLocaleType);
    generator.invokeVirtual(ourStringType, ourToLowerCaseMethod);
    generator.push("mac");
    generator.invokeVirtual(ourStringType, ourStartsWithMethod);
    int isMac = generator.newLocal(Type.BOOLEAN_TYPE);
    generator.storeLocal(isMac);

    Label styleContextLabel = new Label();
    generator.loadLocal(isMac);
    generator.ifZCmp(GeneratorAdapter.EQ, styleContextLabel);

    generator.newInstance(ourFontType);
    generator.dup();
    generator.loadLocal(font);
    generator.invokeVirtual(ourFontType, ourGetFamilyMethod);
    generator.loadLocal(font);
    generator.invokeVirtual(ourFontType, ourGetStyleMethod);
    generator.loadLocal(font);
    generator.invokeVirtual(ourFontType, ourGetSizeMethod);
    generator.invokeConstructor(ourFontType, ourInitFontMethod);

    Label resultBlock = new Label();
    generator.goTo(resultBlock);
    generator.mark(styleContextLabel);

    generator.newInstance(ourStyleContextType);
    generator.dup();
    generator.invokeConstructor(ourStyleContextType, ourInitStyleContextMethod);
    generator.loadLocal(font);
    generator.invokeVirtual(ourFontType, ourGetFamilyMethod);
    generator.loadLocal(font);
    generator.invokeVirtual(ourFontType, ourGetStyleMethod);
    generator.loadLocal(font);
    generator.invokeVirtual(ourFontType, ourGetSizeMethod);
    generator.invokeVirtual(ourStyleContextType, ourGetFontMethod);

    generator.mark(resultBlock);
    int fontWithFallback = generator.newLocal(ourFontType);
    generator.storeLocal(fontWithFallback);

    Label newFontResource = new Label();
    Label returnBlock = new Label();

    generator.loadLocal(fontWithFallback);
    generator.instanceOf(ourFontUIResource);
    generator.ifZCmp(GeneratorAdapter.EQ, newFontResource);
    generator.loadLocal(fontWithFallback);
    generator.goTo(returnBlock);
    generator.mark(newFontResource);
    generator.newInstance(ourFontUIResource);
    generator.dup();
    generator.loadLocal(fontWithFallback);
    generator.invokeConstructor(ourFontUIResource, ourInitFontUIResourceMethod);
    generator.mark(returnBlock);
    generator.returnValue();

    generator.endMethod();
  }
}