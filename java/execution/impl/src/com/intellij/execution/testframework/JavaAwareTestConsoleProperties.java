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

/*
 * User: anna
 * Date: 20-Feb-2008
 */
package com.intellij.execution.testframework;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeSelectionModel;
import java.util.Collection;

public abstract class JavaAwareTestConsoleProperties<T extends ModuleBasedConfiguration<JavaRunConfigurationModule> & CommonJavaRunConfigurationParameters> extends SMTRunnerConsoleProperties {
  public JavaAwareTestConsoleProperties(final String testFrameworkName, RunConfiguration configuration, Executor executor) {
    super(configuration, testFrameworkName, executor, false);
  }

  @Override
  public boolean isPaused() {
    final DebuggerSession debuggerSession = getDebugSession();
    return debuggerSession != null && debuggerSession.isPaused();
  }

  @Override
  public T getConfiguration() {
    return (T)super.getConfiguration();
  }

  @Override
  protected int getSelectionMode() {
    return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  }

  @Override
  public boolean fixEmptySuite() {
    return ResetConfigurationModuleAdapter.tryWithAnotherModule(getConfiguration(), isDebug());
  }

  @Nullable
  public DebuggerSession getDebugSession() {
    final DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(getProject());
    if (debuggerManager == null) return null;
    final Collection<DebuggerSession> sessions = debuggerManager.getSessions();
    for (final DebuggerSession debuggerSession : sessions) {
      if (getConsole() == debuggerSession.getProcess().getExecutionResult().getExecutionConsole()) return debuggerSession;
    }
    return null;
  }

}