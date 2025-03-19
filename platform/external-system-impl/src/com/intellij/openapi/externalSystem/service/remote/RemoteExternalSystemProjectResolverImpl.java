// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.remote;

import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines common interface for resolving gradle project, i.e. building object-level representation of {@code 'build.gradle'}.
 */
@ApiStatus.Internal
public class RemoteExternalSystemProjectResolverImpl<S extends ExternalSystemExecutionSettings>
  extends AbstractRemoteExternalSystemService<S> implements RemoteExternalSystemProjectResolver<S>
{

  private final ExternalSystemProjectResolver<S> myDelegate;

  public RemoteExternalSystemProjectResolverImpl(@NotNull ExternalSystemProjectResolver<S> delegate) {
    myDelegate = delegate;
  }

  @Override
  public @Nullable DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                                            @NotNull String projectPath,
                                                            boolean isPreviewMode,
                                                            @Nullable S settings,
                                                            @Nullable ProjectResolverPolicy resolverPolicy)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException {
    return execute(id, () ->
      myDelegate.resolveProjectInfo(id, projectPath, isPreviewMode, settings, resolverPolicy, getNotificationListener()));
  }

  @Override
  public boolean cancelTask(final @NotNull ExternalSystemTaskId id)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException {
    return myDelegate.cancelTask(id, getNotificationListener());
  }
}
