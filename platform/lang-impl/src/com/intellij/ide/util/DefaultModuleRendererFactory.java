package com.intellij.ide.util;

import javax.swing.*;

/**
 * @author yole
 */
public class DefaultModuleRendererFactory extends ModuleRendererFactory {
  public DefaultListCellRenderer getModuleRenderer() {
    return new PsiElementModuleRenderer();
  }
}
