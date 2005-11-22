package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.Type;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 17:12:03
 * To change this template use File | Settings | File Templates.
 */
public class ScrollPaneLayoutCodeGenerator extends LayoutCodeGenerator {
  private final Type myScrollPaneType = Type.getType(JScrollPane.class);
  private final Method mySetViewportViewMethod = Method.getMethod("void setViewportView(java.awt.Component)");

  public void generateContainerLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal) {
  }

  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal) {
    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    generator.invokeVirtual(myScrollPaneType, mySetViewportViewMethod);
  }
}
