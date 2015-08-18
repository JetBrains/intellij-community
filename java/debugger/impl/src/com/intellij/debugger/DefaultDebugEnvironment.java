/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SearchScopeProvider;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public class DefaultDebugEnvironment implements DebugEnvironment {
  private final GlobalSearchScope mySearchScope;
  private final RemoteConnection myRemoteConnection;
  private final boolean myPollConnection;
  private final ExecutionEnvironment environment;
  private final RunProfileState state;

  public DefaultDebugEnvironment(@NotNull ExecutionEnvironment environment, @NotNull RunProfileState state, RemoteConnection remoteConnection, boolean pollConnection) {
    this.environment = environment;
    this.state = state;
    myRemoteConnection = remoteConnection;
    myPollConnection = pollConnection;

    mySearchScope = SearchScopeProvider.createSearchScope(environment.getProject(), environment.getRunProfile());
  }

  @Override
  public ExecutionResult createExecutionResult() throws ExecutionException {
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
  public boolean isPollConnection() {
    return myPollConnection;
  }

  @Override
  public String getSessionName() {
    return environment.getRunProfile().getName();
  }
}
