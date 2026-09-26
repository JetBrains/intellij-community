// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  default @Nullable Sdk getAlternativeJre() {
    return null;
  }

  default @Nullable Sdk getRunJre() {
    return null;
  }

  boolean isRemote();

  RemoteConnection getRemoteConnection();

  long getPollTimeout();

  @NlsSafe
  String getSessionName();
}
