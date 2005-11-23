package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.Type;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 17:43:00
 * To change this template use File | Settings | File Templates.
 */
public class DimensionPropertyCodeGenerator extends PropertyCodeGenerator {
  private final Type myDimensionType = Type.getType(Dimension.class);

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    Dimension dimension = (Dimension) value;
    generator.newInstance(myDimensionType);
    generator.dup();
    generator.push(dimension.width);
    generator.push(dimension.height);
    generator.invokeConstructor(myDimensionType, Method.getMethod("void <init>(int,int)"));
  }
}
