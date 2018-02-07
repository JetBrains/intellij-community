/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger;

import com.intellij.debugger.impl.AlternativeJreClassFinder;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

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

    mySearchScope = createSearchScope(environment.getProject(), environment.getRunProfile());
    myNeedParametersSet = remoteConnection.isServerMode() && remoteConnection.isUseSockets() && "0".equals(remoteConnection.getAddress());
  }

  private static GlobalSearchScope createSearchScope(@NotNull Project project, @Nullable RunProfile runProfile) {
    GlobalSearchScope scope = SearchScopeProvider.createSearchScope(project, runProfile);
    if (scope.equals(GlobalSearchScope.allScope(project))) {
      // prefer sources over class files
      return new DelegatingGlobalSearchScope(scope) {
        final ProjectFileIndex myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final Comparator<VirtualFile> myScopeComparator =
          Comparator.comparing(myProjectFileIndex::isInSourceContent)
            .thenComparing(myProjectFileIndex::isInLibrarySource)
            .thenComparing(super::compare);

        @Override
        public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
          return myScopeComparator.compare(file1, file2);
        }
      };
    }
    return scope;
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

  @Nullable
  @Override
  public Sdk getRunJre() {
    if (state instanceof JavaCommandLine) {
      try {
        return ((JavaCommandLine)state).getJavaParameters().getJdk();
      }
      catch (ExecutionException ignore) {
      }
    }
    return ProjectRootManager.getInstance(environment.getProject()).getProjectSdk();
  }
}
