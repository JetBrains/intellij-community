package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwTabbedPane;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 15:49:27
 * To change this template use File | Settings | File Templates.
 */
public class TabbedPaneLayoutCodeGenerator extends LayoutCodeGenerator {
  private final Type myTabbedPaneType = Type.getType(JTabbedPane.class);
  private final Method myAddTabMethod = Method.getMethod("void addTab(java.lang.String,java.awt.Component)");

  public void generateContainerLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal) {
  }

  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal) {
    generator.loadLocal(parentLocal);
    final LwTabbedPane.Constraints tabConstraints = (LwTabbedPane.Constraints)lwComponent.getCustomLayoutConstraints();
    if (tabConstraints == null){
      throw new IllegalArgumentException("tab constraints cannot be null: " + lwComponent.getId());
    }
    AsmCodeGenerator.pushPropValue(generator, "java.lang.String", tabConstraints.myTitle);
    generator.loadLocal(componentLocal);
    generator.invokeVirtual(myTabbedPaneType, myAddTabMethod);
  }
}
