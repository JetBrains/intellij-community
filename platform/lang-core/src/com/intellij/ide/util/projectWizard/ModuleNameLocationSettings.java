// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import org.jetbrains.annotations.NotNull;

public interface ModuleNameLocationSettings {
  @NotNull
  String getModuleName();

  void setModuleName(@NotNull String moduleName);

  @NotNull
  String getModuleContentRoot();

  void setModuleContentRoot(@NotNull String path);
}
