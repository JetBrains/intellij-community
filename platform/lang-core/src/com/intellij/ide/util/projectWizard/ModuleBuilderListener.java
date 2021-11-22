// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ModuleBuilderListener extends EventListener {

  void moduleCreated(@NotNull Module module);
  
}
