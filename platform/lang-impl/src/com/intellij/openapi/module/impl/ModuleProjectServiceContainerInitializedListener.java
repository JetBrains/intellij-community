// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectServiceContainerInitializedListener;
import org.jetbrains.annotations.NotNull;

public class ModuleProjectServiceContainerInitializedListener implements ProjectServiceContainerInitializedListener {
  @Override
  public void serviceCreated(@NotNull Project project) {
    Activity activity = StartUpMeasurer.startActivity("module loading");
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    if (!(moduleManager instanceof ModuleManagerImpl)) {
      return;
    }

    ModuleManagerImpl manager = (ModuleManagerImpl)moduleManager;
    manager.loadModules(manager.myModuleModel);
    activity.end();
    activity.setDescription("module count: " + manager.myModuleModel.getModules().length);
  }
}