package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 17:25:48
 * To change this template use File | Settings | File Templates.
 */
public interface PropertyCodeGenerator {
  void generatePushValue(final GeneratorAdapter generator, final Object value);
}
