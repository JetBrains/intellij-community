package com.intellij.openapi.externalSystem.service.remote.wrapper;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.RemoteException;

/**
 * Intercepts calls to the target {@link RemoteExternalSystemProjectResolver} and
 * {@link ExternalSystemTaskNotificationListener#onQueued(ExternalSystemTaskId, String) updates 'queued' task status}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 7:21 PM
 */
public class ExternalSystemProjectResolverWrapper<S extends ExternalSystemExecutionSettings>
  extends AbstractRemoteExternalSystemServiceWrapper<S, RemoteExternalSystemProjectResolver<S>>
  implements RemoteExternalSystemProjectResolver<S>
{

  @NotNull private final RemoteExternalSystemProgressNotificationManager myProgressManager;

  public ExternalSystemProjectResolverWrapper(@NotNull RemoteExternalSystemProjectResolver<S> delegate,
                                              @NotNull RemoteExternalSystemProgressNotificationManager progressManager)
  {
    super(delegate);
    myProgressManager = progressManager;
  }

  @Nullable
  @Override
  public DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                                    @NotNull String projectPath,
                                                    boolean isPreviewMode,
                                                    @Nullable S settings)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException, RemoteException
  {
    myProgressManager.onQueued(id, projectPath);
    try {
      DataNode<ProjectData> projectDataNode = getDelegate().resolveProjectInfo(id, projectPath, isPreviewMode, settings);
      myProgressManager.onSuccess(id);
      return projectDataNode;
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
