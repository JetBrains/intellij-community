package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.Type;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 17:46:41
 * To change this template use File | Settings | File Templates.
 */
public class InsetsPropertyCodeGenerator extends PropertyCodeGenerator {
  private final Type myInsetsType = Type.getType(Insets.class);

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    final Insets insets = (Insets) value;
    generator.newInstance(myInsetsType);
    generator.dup();
    generator.push(insets.top);
    generator.push(insets.left);
    generator.push(insets.bottom);
    generator.push(insets.right);
    generator.invokeConstructor(myInsetsType, Method.getMethod("void <init>(int,int,int,int)"));
  }
}
