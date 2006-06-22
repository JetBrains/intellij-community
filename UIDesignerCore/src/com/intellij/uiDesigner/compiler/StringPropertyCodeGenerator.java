package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.swing.*;
import java.util.ResourceBundle;

/**
 * @author yole
 */
public class StringPropertyCodeGenerator extends PropertyCodeGenerator implements Opcodes {
  private static final Type myResourceBundleType = Type.getType(ResourceBundle.class);
  private final Method myGetBundleMethod = Method.getMethod("java.util.ResourceBundle getBundle(java.lang.String)");
  private final Method myGetStringMethod = Method.getMethod("java.lang.String getString(java.lang.String)");
  private static final Method myLoadLabelTextMethod = new Method(AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, Type.VOID_TYPE,
                                                                 new Type[] { Type.getType(JLabel.class), Type.getType(String.class) } );
  private static final Method myLoadButtonTextMethod = new Method(AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, Type.VOID_TYPE,
                                                                 new Type[] { Type.getType(AbstractButton.class), Type.getType(String.class) } );

  private boolean myNeedLoadLabelText;
  private boolean myNeedLoadButtonText;
  private String myClassName;
  private boolean myHaveSetDisplayedMnemonicIndex = false;

  public void generateClassStart(ClassVisitor visitor, final String name, final ClassLoader loader) {
    myClassName = name;
    myNeedLoadLabelText = false;
    myNeedLoadButtonText = false;
    try {
      Class c = loader.loadClass(AbstractButton.class.getName());
      if (c.getMethod("getDisplayedMnemonicIndex", new Class[0]) != null) {
        myHaveSetDisplayedMnemonicIndex = true;
      }
    }
    catch (Exception e) {
      // ignore
    }
  }

  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final Class componentClass,
                                        final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        final int componentLocal) {
    if ("text".equals(property.getName()) &&
        (AbstractButton.class.isAssignableFrom(componentClass) || JLabel.class.isAssignableFrom(componentClass))) {
      final StringDescriptor propertyValue = (StringDescriptor)lwComponent.getPropertyValue(property);
      if (propertyValue.getValue() != null) {
        final SupportCode.TextWithMnemonic textWithMnemonic = SupportCode.parseText(propertyValue.getValue());
        if (textWithMnemonic.myMnemonicIndex >= 0) {
          generator.loadLocal(componentLocal);
          generator.push(textWithMnemonic.myText);
          generator.invokeVirtual(Type.getType(componentClass),
                                  new Method(property.getWriteMethodName(),
                                             Type.VOID_TYPE, new Type[] { Type.getType(String.class) } ));

          String setMnemonicMethodName;
          if (AbstractButton.class.isAssignableFrom(componentClass)) {
            setMnemonicMethodName = "setMnemonic";
          }
          else {
            setMnemonicMethodName = "setDisplayedMnemonic";
          }

          generator.loadLocal(componentLocal);
          generator.push(textWithMnemonic.getMnemonicChar());
          generator.invokeVirtual(Type.getType(componentClass),
                                  new Method(setMnemonicMethodName,
                                             Type.VOID_TYPE, new Type[] { Type.CHAR_TYPE } ));

          if (myHaveSetDisplayedMnemonicIndex) {
            generator.loadLocal(componentLocal);
            generator.push(textWithMnemonic.myMnemonicIndex);
            generator.invokeStatic(Type.getType(SupportCode.class),
                                   new Method("setDisplayedMnemonicIndex",
                                              Type.VOID_TYPE, new Type[] { Type.getType(JComponent.class), Type.INT_TYPE } ));
          }
          return true;
        }
      }
      else {
        Method method;
        if (AbstractButton.class.isAssignableFrom(componentClass)) {
          myNeedLoadButtonText = true;
          method = myLoadButtonTextMethod;
        }
        else {
          myNeedLoadLabelText = true;
          method = myLoadLabelTextMethod;
        }

        generator.loadThis();
        generator.loadLocal(componentLocal);
        generator.push(propertyValue.getBundleName());
        generator.invokeStatic(myResourceBundleType, myGetBundleMethod);
        generator.push(propertyValue.getKey());
        generator.invokeVirtual(myResourceBundleType, myGetStringMethod);
        generator.invokeVirtual(Type.getType("L" + myClassName + ";"), method);
        return true;
      }
    }
    return false;
  }

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    StringDescriptor descriptor = (StringDescriptor) value;
    if (descriptor == null) {
      generator.push((String)null);
    }
    else if (descriptor.getValue() !=null) {
      generator.push(descriptor.getValue());
    }
    else {
      generator.push(descriptor.getBundleName());
      generator.invokeStatic(myResourceBundleType, myGetBundleMethod);
      generator.push(descriptor.getKey());
      generator.invokeVirtual(myResourceBundleType, myGetStringMethod);
    }
  }

  public void generateClassEnd(AsmCodeGenerator.FormClassVisitor visitor) {
    if (myNeedLoadLabelText) {
      generateLoadTextMethod(visitor, AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, "javax/swing/JLabel", "setDisplayedMnemonic");
    }
    if (myNeedLoadButtonText) {
      generateLoadTextMethod(visitor, AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, "javax/swing/AbstractButton", "setMnemonic");
    }
  }

  private void generateLoadTextMethod(final AsmCodeGenerator.FormClassVisitor visitor, final String methodName, final String componentClass,
                                      final String setMnemonicMethodName) {
    MethodVisitor mv = visitor.visitNewMethod(ACC_PRIVATE, methodName, "(L" + componentClass + ";Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitTypeInsn(NEW, "java/lang/StringBuffer");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuffer", "<init>", "()V");
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
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");
    Label l1 = new Label();
    mv.visitJumpInsn(IF_ICMPGE, l1);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C");
    mv.visitIntInsn(BIPUSH, 38);
    Label l2 = new Label();
    mv.visitJumpInsn(IF_ICMPNE, l2);
    mv.visitIincInsn(7, 1);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I");
    Label l3 = new Label();
    mv.visitJumpInsn(IF_ICMPNE, l3);
    mv.visitJumpInsn(GOTO, l1);
    mv.visitLabel(l3);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitJumpInsn(IFNE, l2);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C");
    mv.visitIntInsn(BIPUSH, 38);
    mv.visitJumpInsn(IF_ICMPEQ, l2);
    mv.visitInsn(ICONST_1);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C");
    mv.visitVarInsn(ISTORE, 5);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuffer", "length", "()I");
    mv.visitVarInsn(ISTORE, 6);
    mv.visitLabel(l2);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C");
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuffer", "append", "(C)Ljava/lang/StringBuffer;");
    mv.visitInsn(POP);
    mv.visitIincInsn(7, 1);
    mv.visitJumpInsn(GOTO, l0);
    mv.visitLabel(l1);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuffer", "toString", "()Ljava/lang/String;");
    mv.visitMethodInsn(INVOKEVIRTUAL, componentClass, "setText", "(Ljava/lang/String;)V");
    mv.visitVarInsn(ILOAD, 4);
    Label l4 = new Label();
    mv.visitJumpInsn(IFEQ, l4);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitVarInsn(ILOAD, 5);
    mv.visitMethodInsn(INVOKEVIRTUAL, componentClass, setMnemonicMethodName, "(C)V");
    if (myHaveSetDisplayedMnemonicIndex) {
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ILOAD, 6);
      mv.visitMethodInsn(INVOKEVIRTUAL, componentClass, "setDisplayedMnemonicIndex", "(I)V");
    }
    mv.visitLabel(l4);
    mv.visitInsn(RETURN);
    mv.visitMaxs(3, 8);
    mv.visitEnd();
  }
}
