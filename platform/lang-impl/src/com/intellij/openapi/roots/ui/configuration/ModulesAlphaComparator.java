// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.module.Module;

import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 */
public final class ModulesAlphaComparator implements Comparator<Module>{

  public static final ModulesAlphaComparator INSTANCE = new ModulesAlphaComparator();

  @Override
  public int compare(Module module1, Module module2) {
    if (module1 == null && module2 == null) return 0;
    if (module1 == null) return -1;
    if (module2 == null) return 1;
    return module1.getName().compareToIgnoreCase(module2.getName());
  }
}
