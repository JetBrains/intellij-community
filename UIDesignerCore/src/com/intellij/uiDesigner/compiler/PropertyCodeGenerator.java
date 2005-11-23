package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 17:25:48
 * To change this template use File | Settings | File Templates.
 */
public abstract class PropertyCodeGenerator {
  public abstract void generatePushValue(final GeneratorAdapter generator, final Object value);

  public boolean generateCustomSetValue(final LwComponent lwComponent,
                                        final Class componentClass, final LwIntrospectedProperty property,
                                        final GeneratorAdapter generator,
                                        final int componentLocal) {
    return false;
  }
}
