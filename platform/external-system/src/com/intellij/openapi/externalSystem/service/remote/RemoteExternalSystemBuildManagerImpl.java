/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.remote;

import com.intellij.openapi.externalSystem.build.ExternalSystemBuildManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskDescriptor;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 4/9/13 7:49 PM
 */
public class RemoteExternalSystemBuildManagerImpl<S extends ExternalSystemExecutionSettings>
  extends AbstractRemoteExternalSystemService<S> implements RemoteExternalSystemBuildManager<S>
{
  
  @NotNull private final ExternalSystemBuildManager<S> myDelegate;

  public RemoteExternalSystemBuildManagerImpl(@NotNull ExternalSystemBuildManager<S> delegate) {
    myDelegate = delegate;
  }

  @Override
  public Collection<ExternalSystemTaskDescriptor> listTasks(@NotNull final ExternalSystemTaskId id,
                                                            @NotNull final String projectPath,
                                                            @Nullable final S settings) throws RemoteException, ExternalSystemException
  {
    return execute(id, new Producer<Collection<ExternalSystemTaskDescriptor>>() {
      @Nullable
      @Override
      public Collection<ExternalSystemTaskDescriptor> produce() {
        return myDelegate.listTasks(id, projectPath, settings);
      }
    });
  }

  @Override
  public void executeTasks(@NotNull final ExternalSystemTaskId id,
                           @NotNull final List<String> taskNames,
                           @NotNull final String projectPath,
                           @Nullable final S settings) throws RemoteException, ExternalSystemException
  {
    execute(id, new Producer<Object>() {
      @Nullable
      @Override
      public Object produce() {
        myDelegate.executeTasks(id, taskNames, projectPath, settings); 
        return null;
      }
    });
  }
}
