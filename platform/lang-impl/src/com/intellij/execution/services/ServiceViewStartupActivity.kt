// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ForkJoinPool;

final class ServiceViewStartupActivity implements StartupActivity.DumbAware {
  private ServiceViewStartupActivity() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @Override
  public void runActivity(@NotNull Project project) {
    if (ServiceViewContributor.CONTRIBUTOR_EP_NAME.getExtensionList().isEmpty()) {
      ServiceViewContributor.CONTRIBUTOR_EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
        @Override
        public void extensionAdded(@NotNull ServiceViewContributor<?> extension, @NotNull PluginDescriptor pluginDescriptor) {
          ServiceViewManager.getInstance(project);
        }
      }, project);
    }
    else {
      // init manager to check availability on background thread and register tool window
      ToolWindowManager.getInstance(project).invokeLater(() -> {
        ForkJoinPool.commonPool().execute(() -> ServiceViewManager.getInstance(project));
      });
    }
  }
}
