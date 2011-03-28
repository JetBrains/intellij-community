/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nullable;

public abstract class ProcessProxyFactory {
  public abstract boolean isBreakGenLibraryAvailable();

  public static ProcessProxyFactory getInstance() {
    return ServiceManager.getService(ProcessProxyFactory.class);
  }

  @Nullable
  public abstract ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException;

  @Nullable
  public abstract ProcessProxy getAttachedProxy(ProcessHandler processHandler);
}