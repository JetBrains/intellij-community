package com.intellij.ide.util;

import com.intellij.openapi.components.ServiceManager;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class ModuleRendererFactory {
  public static ModuleRendererFactory getInstance() {
    return ServiceManager.getService(ModuleRendererFactory.class);
  }

  public abstract DefaultListCellRenderer getModuleRenderer();
}
