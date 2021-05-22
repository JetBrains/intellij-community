// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import javax.swing.*;

/**
 * @author yole
 */
final class DefaultModuleRendererFactory extends ModuleRendererFactory {
  @Override
  public DefaultListCellRenderer getModuleRenderer() {
    return new PsiElementModuleRenderer();
  }
}
