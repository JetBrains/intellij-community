package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * @author yole
 */
public abstract class PropertyCodeGenerator {
  public abstract void generatePushValue(final GeneratorAdapter generator, final Object value);

  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final Class componentClass, final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        final int componentLocal, final String formClassName) {
    return false;
  }

  public void generateClassStart(AsmCodeGenerator.FormClassVisitor visitor, final String name, final ClassLoader loader) {
  }

  public void generateClassEnd(AsmCodeGenerator.FormClassVisitor visitor) {
  }
}
