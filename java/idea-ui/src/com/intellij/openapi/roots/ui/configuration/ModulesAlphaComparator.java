package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;

import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1
 * @author 2003
 */
public class ModulesAlphaComparator implements Comparator<Module>{
  public int compare(Module module1, Module module2) {
    final String name1 = module1.getName();
    final String name2 = module2.getName();
    return name1.compareToIgnoreCase(name2);
  }
}
