// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DebugEnvironment {

  int LOCAL_START_TIMEOUT = 30000;

  @Nullable
  ExecutionResult createExecutionResult() throws ExecutionException;

  @NotNull
  GlobalSearchScope getSearchScope();

  @Nullable
  default Sdk getAlternativeJre() {
    return null;
  }

  @Nullable
  default Sdk getRunJre() {
    return null;
  }

  boolean isRemote();

  RemoteConnection getRemoteConnection();

  long getPollTimeout();

  @NlsSafe
  String getSessionName();
}
