/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.module;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.roots.ModifiableRootModel;

public interface ModuleConfigurationEditor extends Configurable {
  void saveData();
  void moduleStateChanged();
}