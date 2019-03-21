// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

@ApiStatus.Experimental
public interface ServiceViewManager {
  Key<Boolean> SERVICE_VIEW_MASTER_COMPONENT = Key.create("SERVICE_VIEW_MASTER_COMPONENT");

  static ServiceViewManager getInstance(Project project) {
    return ServiceManager.getService(project, ServiceViewManager.class);
  }

  @NotNull
  Promise<Void> selectNode(Object node, boolean activate, boolean focus);
}
