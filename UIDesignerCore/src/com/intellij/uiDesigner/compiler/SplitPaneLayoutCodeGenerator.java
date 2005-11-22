package com.intellij.uiDesigner.compiler;

import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwSplitPane;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.11.2005
 * Time: 15:23:45
 * To change this template use File | Settings | File Templates.
 */
public class SplitPaneLayoutCodeGenerator extends LayoutCodeGenerator {
  private final Type mySplitPaneType = Type.getType(JSplitPane.class);
  private final Method mySetLeftMethod = Method.getMethod("void setLeftComponent(java.awt.Component)");
  private final Method mySetRightMethod = Method.getMethod("void setRightComponent(java.awt.Component)");

  public void generateContainerLayout(final LwComponent lwComponent, final GeneratorAdapter generator, final int componentLocal) {
  }

  public void generateComponentLayout(final LwComponent lwComponent,
                                      final GeneratorAdapter generator,
                                      final int componentLocal,
                                      final int parentLocal) {
    generator.loadLocal(parentLocal);
    generator.loadLocal(componentLocal);
    if (LwSplitPane.POSITION_LEFT.equals(lwComponent.getCustomLayoutConstraints())) {
      generator.invokeVirtual(mySplitPaneType, mySetLeftMethod);
    }
    else {
      generator.invokeVirtual(mySplitPaneType, mySetRightMethod);
    }
  }
}
