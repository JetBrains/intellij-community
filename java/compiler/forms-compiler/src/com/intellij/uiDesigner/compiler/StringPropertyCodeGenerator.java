// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.compiler;

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import javax.swing.*;
import java.io.IOException;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public final class StringPropertyCodeGenerator extends PropertyCodeGenerator implements Opcodes {
  private static final Type bundleType = Type.getType(ResourceBundle.class);
  private static final Type stringType = Type.getType(String.class);

  private static final Method getBundleMethod = new Method("getBundle", bundleType,
                                                           new Type[]{stringType});

  private static final Method getDynamicBundleMethod = new Method("getBundle", bundleType,
                                                                  new Type[]{stringType, Type.getType(Class.class)});

  private static final Method myGetStringMethod = new Method("getString", stringType, new Type[]{stringType});

  private static final Method myLoadLabelTextMethod = new Method(AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, Type.VOID_TYPE,
                                                                 new Type[]{Type.getType(JLabel.class), stringType});
  private static final Method myLoadButtonTextMethod = new Method(AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, Type.VOID_TYPE,
                                                                  new Type[]{Type.getType(AbstractButton.class), stringType});

  private final Set<String> myClassesRequiringLoadLabelText = new HashSet<>();
  private final Set<String> myClassesRequiringLoadButtonText = new HashSet<>();
  private boolean myHaveSetDisplayedMnemonicIndex;
  private Type dynamicBundleType;

  @Override
  public void generateClassStart(AsmCodeGenerator.FormClassVisitor visitor, final String name, final InstrumentationClassFinder classFinder) {
    myClassesRequiringLoadLabelText.remove(name);
    myClassesRequiringLoadButtonText.remove(name);
    try {
      InstrumentationClassFinder.PseudoClass pseudo = classFinder.loadClass(AbstractButton.class.getName());
      if (!pseudo.findMethods("getDisplayedMnemonicIndex").isEmpty()) {
        myHaveSetDisplayedMnemonicIndex = true;
      }
    }
    catch (Exception ignored) {}

    if (visitor.useDynamicBundles) {
      try {
        classFinder.loadClass("com.intellij.DynamicBundle");
        dynamicBundleType = Type.getType("Lcom/intellij/DynamicBundle;");
      }
      catch (Exception ignored) {}
    }
  }

  @Override
  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final InstrumentationClassFinder.PseudoClass componentClass,
                                        final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        GetFontMethodProvider fontMethodProvider,
                                        final int componentLocal,
                                        final String formClassName) throws IOException, ClassNotFoundException {
    StringDescriptor propertyValue = (StringDescriptor)lwComponent.getPropertyValue(property);
    String key = propertyValue.getKey();

    if (key != null) {
      propertyValue.setFormClass(formClassName);
    }

    if (!"text".equals(property.getName())) {
      return false;
    }

    InstrumentationClassFinder.PseudoClass abstractButtonClass = componentClass.getFinder().loadClass(AbstractButton.class.getName());
    InstrumentationClassFinder.PseudoClass jLabelClass = componentClass.getFinder().loadClass(JLabel.class.getName());
    if (!(abstractButtonClass.isAssignableFrom(componentClass) || jLabelClass.isAssignableFrom(componentClass))) {
      return false;
    }

    if (key == null) {
      String value = propertyValue.getValue();
      SupportCode.TextWithMnemonic textWithMnemonic = value == null ? null : SupportCode.parseText(value);
      if (textWithMnemonic == null || textWithMnemonic.myMnemonicIndex < 0) {
        return false;
      }

      generator.loadLocal(componentLocal);
      generator.push(textWithMnemonic.myText);
      generator.invokeVirtual(Type.getType(componentClass.getDescriptor()),
                              new Method(property.getWriteMethodName(), Type.VOID_TYPE, new Type[]{stringType}));

      String setMnemonicMethodName;
      if (abstractButtonClass.isAssignableFrom(componentClass)) {
        setMnemonicMethodName = "setMnemonic";
      }
      else {
        setMnemonicMethodName = "setDisplayedMnemonic";
      }

      generator.loadLocal(componentLocal);
      generator.push(textWithMnemonic.getMnemonicChar());
      generator.invokeVirtual(Type.getType(componentClass.getDescriptor()),
                              new Method(setMnemonicMethodName, Type.VOID_TYPE, new Type[]{Type.CHAR_TYPE}));

      if (myHaveSetDisplayedMnemonicIndex) {
        generator.loadLocal(componentLocal);
        generator.push(textWithMnemonic.myMnemonicIndex);
        generator.invokeVirtual(Type.getType(componentClass.getDescriptor()),
                                new Method("setDisplayedMnemonicIndex", Type.VOID_TYPE, new Type[]{Type.INT_TYPE}));
      }
    }
    else {
      Type formClass = Type.getType("L" + formClassName + ";");

      generator.loadThis();
      generator.loadLocal(componentLocal);

      generateGetBundleString(propertyValue, generator);

      Method method;
      if (abstractButtonClass.isAssignableFrom(componentClass)) {
        myClassesRequiringLoadButtonText.add(formClassName);
        method = myLoadButtonTextMethod;
      }
      else {
        myClassesRequiringLoadLabelText.add(formClassName);
        method = myLoadLabelTextMethod;
      }
      generator.invokeVirtual(formClass, method);
    }
    return true;
  }

  private void generateGetBundleString(StringDescriptor descriptor, GeneratorAdapter generator) {
    generator.push(descriptor.getBundleName());

    String formClass = descriptor.getFormClass();
    if (dynamicBundleType != null && formClass != null) {
      Type type = Type.getType("L" + formClass + ";");
      generator.push(type);
      generator.invokeStatic(dynamicBundleType, getDynamicBundleMethod);
    }
    else {
      generator.invokeStatic(bundleType, getBundleMethod);
    }

    generator.push(descriptor.getKey());
    generator.invokeVirtual(bundleType, myGetStringMethod);
  }

  @Override
  public void generatePushValue(GeneratorAdapter generator, Object value) {
    StringDescriptor descriptor = (StringDescriptor)value;
    if (descriptor == null) {
      generator.push((String)null);
    }
    else if (descriptor.getValue() != null) {
      generator.push(descriptor.getValue());
    }
    else {
      generateGetBundleString(descriptor, generator);
    }
  }

  @Override
  public void generateClassEnd(AsmCodeGenerator.FormClassVisitor visitor) {
    if (myClassesRequiringLoadLabelText.contains(visitor.getClassName())) {
      generateLoadTextMethod(visitor, AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, "javax/swing/JLabel", "setDisplayedMnemonic");
      myClassesRequiringLoadLabelText.remove(visitor.getClassName());
    }
    if (myClassesRequiringLoadButtonText.contains(visitor.getClassName())) {
      generateLoadTextMethod(visitor, AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, "javax/swing/AbstractButton", "setMnemonic");
      myClassesRequiringLoadButtonText.remove(visitor.getClassName());
    }
  }

  private void generateLoadTextMethod(final AsmCodeGenerator.FormClassVisitor visitor, final String methodName, final String componentClass,
                                      final String setMnemonicMethodName) {
    MethodVisitor mv = visitor.visitNewMethod(ACC_PRIVATE | ACC_SYNTHETIC, methodName, "(L" + componentClass + ";Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitTypeInsn(NEW, "java/lang/StringBuffer");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuffer", "<init>", "()V", false);
    mv.visitVarInsn(ASTORE, 3);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitInsn(ICONST_M1);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ISTORE, 7);
    Label l0 = new Label();
    mv.visitLabel(l0);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    Label l1 = new Label();
    mv.visitJumpInsn(IF_ICMPGE, l1);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
    mv.visitIntInsn(BIPUSH, 38);
    Label l2 = new Label();
    mv.visitJumpInsn(IF_ICMPNE, l2);
    mv.visitIincInsn(7, 1);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
    Label l3 = new Label();
    mv.visitJumpInsn(IF_ICMPNE, l3);
    mv.visitJumpInsn(GOTO, l1);
    mv.visitLabel(l3);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitJumpInsn(IFNE, l2);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
    mv.visitIntInsn(BIPUSH, 38);
    mv.visitJumpInsn(IF_ICMPEQ, l2);
    mv.visitInsn(ICONST_1);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuffer", "length", "()I", false);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitLabel(l2);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuffer", "append", "(C)Ljava/lang/StringBuffer;", false);
    mv.visitInsn(POP);
    mv.visitIincInsn(7, 1);
    mv.visitJumpInsn(GOTO, l0);
    mv.visitLabel(l1);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuffer", "toString", "()Ljava/lang/String;", false);
    mv.visitMethodInsn(INVOKEVIRTUAL, componentClass, "setText", "(Ljava/lang/String;)V", false);
    mv.visitVarInsn(ILOAD, 4);
    Label l4 = new Label();
    mv.visitJumpInsn(IFEQ, l4);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitVarInsn(ILOAD, 5);
    mv.visitMethodInsn(INVOKEVIRTUAL, componentClass, setMnemonicMethodName, "(C)V", false);
    if (myHaveSetDisplayedMnemonicIndex) {
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ILOAD, 6);
      mv.visitMethodInsn(INVOKEVIRTUAL, componentClass, "setDisplayedMnemonicIndex", "(I)V", false);
    }
    mv.visitLabel(l4);
    mv.visitInsn(RETURN);
    mv.visitMaxs(3, 8);
    mv.visitEnd();
  }
}
