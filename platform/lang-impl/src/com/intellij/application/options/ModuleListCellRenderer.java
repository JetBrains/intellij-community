package com.intellij.application.options;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.ui.ListCellRendererWrapper;

import javax.swing.*;

/**
* @author yole
*/
public class ModuleListCellRenderer extends ListCellRendererWrapper<Module> {
  @Override
  public void customize(JList list, Module module, int index, boolean selected, boolean hasFocus) {
    if (module == null) {
      setText("[none]");
    }
    else {
      setIcon(ModuleType.get(module).getIcon());
      setText(module.getName());
    }
  }
}
