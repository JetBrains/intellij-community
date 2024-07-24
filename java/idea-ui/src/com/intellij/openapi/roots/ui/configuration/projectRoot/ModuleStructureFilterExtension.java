// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/*
  Allows to exclude modules from 'Project Structure' | 'Modules' view
 */
public abstract class ModuleStructureFilterExtension {

  public static final ExtensionPointName<ModuleStructureFilterExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.configuration.moduleStructureFilterExtension");

  public boolean accepts(@NotNull Module module) {
    return true;
  }
}
