package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.ClassVisitor;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;

/**
 * @author yole
 */
public abstract class PropertyCodeGenerator {
  public abstract void generatePushValue(final GeneratorAdapter generator, final Object value);

  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final Class componentClass, final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        final int componentLocal) {
    return false;
  }

  public void generateClassStart(ClassVisitor visitor, final String name, final ClassLoader loader) {
  }

  public void generateClassEnd(AsmCodeGenerator.FormClassVisitor visitor) {
  }
}
