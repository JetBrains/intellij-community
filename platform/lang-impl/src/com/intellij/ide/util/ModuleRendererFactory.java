// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util;

import com.intellij.openapi.extensions.ExtensionPointName;

import javax.swing.*;


public abstract class ModuleRendererFactory {
  private static final ExtensionPointName<ModuleRendererFactory> EP_NAME = ExtensionPointName.create("com.intellij.moduleRendererFactory");

  public static ModuleRendererFactory findInstance(Object element) {
    for (ModuleRendererFactory factory : EP_NAME.getExtensions()) {
      if (factory.handles(element)) {
        return factory;
      }
    }
    assert false : "No factory found for " + element;
    return null;
  }

  protected boolean handles(final Object element) {
    return true;
  }

  public abstract DefaultListCellRenderer getModuleRenderer();

  public boolean rendersLocationString() {
    return false;
  }
}
