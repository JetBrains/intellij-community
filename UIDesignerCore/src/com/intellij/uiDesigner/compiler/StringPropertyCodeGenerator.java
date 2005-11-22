package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.Type;
import com.intellij.uiDesigner.lw.StringDescriptor;

import java.util.ResourceBundle;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 17:31:15
 * To change this template use File | Settings | File Templates.
 */
public class StringPropertyCodeGenerator implements PropertyCodeGenerator {
  private static final Type myResourceBundleType = Type.getType(ResourceBundle.class);
  private final Method myGetBundleMethod = Method.getMethod("java.util.ResourceBundle getBundle(java.lang.String)");
  private final Method myGetStringMethod = Method.getMethod("java.lang.String getString(java.lang.String)");

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
