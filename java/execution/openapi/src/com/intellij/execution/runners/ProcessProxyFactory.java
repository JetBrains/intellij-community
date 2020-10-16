// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

public abstract class ProcessProxyFactory {
  public static ProcessProxyFactory getInstance() {
    return ApplicationManager.getApplication().getService(ProcessProxyFactory.class);
  }

  @Nullable
  public abstract ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException;

  @Nullable
  public abstract ProcessProxy getAttachedProxy(ProcessHandler processHandler);
}