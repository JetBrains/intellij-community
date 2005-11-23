package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.swing.*;
import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 17:31:15
 * To change this template use File | Settings | File Templates.
 */
public class StringPropertyCodeGenerator extends PropertyCodeGenerator {
  private static final Type myResourceBundleType = Type.getType(ResourceBundle.class);
  private final Method myGetBundleMethod = Method.getMethod("java.util.ResourceBundle getBundle(java.lang.String)");
  private final Method myGetStringMethod = Method.getMethod("java.lang.String getString(java.lang.String)");

  @Override
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

          generator.loadLocal(componentLocal);
          generator.push(textWithMnemonic.myMnemonicIndex);
          generator.invokeStatic(Type.getType(SupportCode.class),
                                 new Method("setDisplayedMnemonicIndex",
                                            Type.VOID_TYPE, new Type[] { Type.getType(JComponent.class), Type.INT_TYPE } ));
          return true;
        }
      }
      else {
        generator.loadLocal(componentLocal);
        generator.push(property.getWriteMethodName());
        generator.push(propertyValue.getBundleName());
        generator.push(propertyValue.getKey());

        generator.invokeStatic(
          Type.getType(SupportCode.class),
          Method.getMethod("void setTextFromBundle(javax.swing.JComponent,java.lang.String,java.lang.String,java.lang.String)"));
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
}
