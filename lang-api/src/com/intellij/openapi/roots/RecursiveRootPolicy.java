package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.util.containers.HashSet;

import java.util.Set;

/**
 * @author yole
 */
public class RecursiveRootPolicy<R> extends RootPolicy<R> {
  private Set<Module> myProcessedModules = new HashSet<Module>();

  public R visitModuleOrderEntry(final ModuleOrderEntry moduleOrderEntry, final R value) {
    final Module module = moduleOrderEntry.getModule();
    if (!myProcessedModules.contains(module)) {
      myProcessedModules.add(module);
      return ModuleRootManager.getInstance(module).processOrder(this, value);
    }
    return value;
  }
}
