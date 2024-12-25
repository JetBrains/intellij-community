// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@Service(Service.Level.APP)
public final class InProcessExternalSystemCommunicationManager implements ExternalSystemCommunicationManager {
  @SuppressWarnings("unchecked")
  @Override
  public @Nullable RemoteExternalSystemFacade acquire(@NotNull String id, @NotNull ProjectSystemId externalSystemId) throws Exception {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;
    InProcessExternalSystemFacadeImpl result = new InProcessExternalSystemFacadeImpl(manager.getProjectResolverClass(),
                                                                                     manager.getTaskManagerClass());
    result.applyProgressManager(ExternalSystemProgressNotificationManagerImpl.getInstanceImpl());
    return result;
  }

  @Override
  public void release(@NotNull String id, @NotNull ProjectSystemId externalSystemId) {
  }

  @Override
  public boolean isAlive(@NotNull RemoteExternalSystemFacade facade) {
    return facade instanceof InProcessExternalSystemFacadeImpl;
  }

  @Override
  public void clear() {
  }
}
