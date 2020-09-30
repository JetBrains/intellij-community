// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.remote.wrapper;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemTaskManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.RemoteException;
import java.util.List;

/**
 * @author Denis Zhdanov
 */
public class ExternalSystemTaskManagerWrapper<S extends ExternalSystemExecutionSettings>
  extends AbstractRemoteExternalSystemServiceWrapper<S, RemoteExternalSystemTaskManager<S>>
  implements RemoteExternalSystemTaskManager<S> {

  @NotNull private final RemoteExternalSystemProgressNotificationManager myProgressManager;

  public ExternalSystemTaskManagerWrapper(@NotNull RemoteExternalSystemTaskManager<S> delegate,
                                          @NotNull RemoteExternalSystemProgressNotificationManager progressManager) {
    super(delegate);
    myProgressManager = progressManager;
  }

  @Override
  public void executeTasks(@NotNull ExternalSystemTaskId id,
                           @NotNull List<String> taskNames,
                           @NotNull String projectPath,
                           @Nullable S settings,
                           @Nullable String jvmParametersSetup) throws RemoteException, ExternalSystemException {
    try {
      getDelegate().executeTasks(id, taskNames, projectPath, settings, jvmParametersSetup);
      myProgressManager.onSuccess(id);
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
    finally {
      myProgressManager.onEnd(id);
    }
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id) throws RemoteException, ExternalSystemException {
    return getDelegate().cancelTask(id);
  }
}

