/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger;

import com.intellij.debugger.impl.AlternativeJreClassFinder;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultDebugEnvironment implements DebugEnvironment {
  private final GlobalSearchScope mySearchScope;
  private final RemoteConnection myRemoteConnection;
  private final long myPollTimeout;
  private final ExecutionEnvironment environment;
  private final RunProfileState state;
  private final boolean myNeedParametersSet;

  public DefaultDebugEnvironment(@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state, RemoteConnection remoteConnection, boolean pollConnection) {
    this(environment, state, remoteConnection, pollConnection ? LOCAL_START_TIMEOUT : 0);
  }

  public DefaultDebugEnvironment(@NotNull ExecutionEnvironment environment,
                                 @NotNull RunProfileState state,
                                 RemoteConnection remoteConnection,
                                 long pollTimeout) {
    this.environment = environment;
    this.state = state;
    myRemoteConnection = remoteConnection;
    myPollTimeout = pollTimeout;

    mySearchScope = SearchScopeProvider.createSearchScope(environment.getProject(), environment.getRunProfile());
    myNeedParametersSet = remoteConnection.isServerMode() && remoteConnection.isUseSockets() && "0".equals(remoteConnection.getAddress());
  }

  @Override
  public ExecutionResult createExecutionResult() throws ExecutionException {
    // debug port may have changed, reinit parameters just in case
    if (myNeedParametersSet && state instanceof JavaCommandLine) {
      DebuggerManagerImpl.createDebugParameters(((JavaCommandLine)state).getJavaParameters(),
                                                true,
                                                DebuggerSettings.SOCKET_TRANSPORT,
                                                myRemoteConnection.getAddress(),
                                                false);
    }
    return state.execute(environment.getExecutor(), environment.getRunner());
  }

  @NotNull
  @Override
  public GlobalSearchScope getSearchScope() {
    return mySearchScope;
  }

  @Override
  public boolean isRemote() {
    return state instanceof RemoteState;
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    return myRemoteConnection;
  }

  @Override
  public long getPollTimeout() {
    return myPollTimeout;
  }

  @Override
  public String getSessionName() {
    return environment.getRunProfile().getName();
  }

  @Nullable
  @Override
  public Sdk getAlternativeJre() {
    return AlternativeJreClassFinder.getAlternativeJre(environment.getRunProfile());
  }
}
