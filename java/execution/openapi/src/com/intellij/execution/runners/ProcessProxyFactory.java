// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract @Nullable ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException;

  public abstract @Nullable ProcessProxy getAttachedProxy(ProcessHandler processHandler);
}