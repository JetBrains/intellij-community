// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.remote.wrapper;

import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolver;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.RemoteException;

/**
 * Intercepts calls to the target {@link RemoteExternalSystemProjectResolver} and
 * {@link ExternalSystemTaskNotificationListener#onStart(ExternalSystemTaskId, String) updates 'queued' task status}.
 * <p/>
 * Thread-safe.
 */
public class ExternalSystemProjectResolverWrapper<S extends ExternalSystemExecutionSettings>
  extends AbstractRemoteExternalSystemServiceWrapper<S, RemoteExternalSystemProjectResolver<S>>
  implements RemoteExternalSystemProjectResolver<S> {

  @NotNull private final RemoteExternalSystemProgressNotificationManager myProgressManager;

  public ExternalSystemProjectResolverWrapper(@NotNull RemoteExternalSystemProjectResolver<S> delegate,
                                              @NotNull RemoteExternalSystemProgressNotificationManager progressManager) {
    super(delegate);
    myProgressManager = progressManager;
  }

  @Nullable
  @Override
  public DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                                  @NotNull String projectPath,
                                                  boolean isPreviewMode,
                                                  @Nullable S settings,
                                                  @Nullable ProjectResolverPolicy resolverPolicy)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException, RemoteException {
    try {
      return getDelegate().resolveProjectInfo(id, projectPath, isPreviewMode, settings, resolverPolicy);
    }
    catch (ProcessCanceledException e) {
      myProgressManager.onCancel(id);
      throw e.getCause() == null || e.getCause() instanceof ExternalSystemException
            ? e : new ProcessCanceledException(new ExternalSystemException(e.getCause()));
    }
    catch (ExternalSystemException e) {
      myProgressManager.onFailure(id, e);
      throw e;
    }
    catch (Exception e) {
      myProgressManager.onFailure(id, e);
      throw new ExternalSystemException(e);
    }
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException, RemoteException {
    myProgressManager.beforeCancel(id);
    try {
      return getDelegate().cancelTask(id);
    }
    finally {
      myProgressManager.onCancel(id);
    }
  }
}
