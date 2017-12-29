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

package com.intellij.execution.testframework;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeSelectionModel;
import java.util.Collection;
import java.util.Iterator;

public abstract class JavaAwareTestConsoleProperties<T extends ModuleBasedConfiguration<JavaRunConfigurationModule> & CommonJavaRunConfigurationParameters> extends SMTRunnerConsoleProperties {
  public JavaAwareTestConsoleProperties(final String testFrameworkName, RunConfiguration configuration, Executor executor) {
    super(configuration, testFrameworkName, executor);
    setPrintTestingStartedTime(false);
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
  public int getSelectionMode() {
    return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  }

  @Override
  public boolean fixEmptySuite() {
    return ResetConfigurationModuleAdapter.tryWithAnotherModule(getConfiguration(), isDebug());
  }

  @Nullable
  @Override
  public Navigatable getErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace) {
    //navigate to the first stack trace
    return getStackTraceErrorNavigatable(location, stacktrace);
  }

  @Nullable
  public static Navigatable getStackTraceErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace) {
    final PsiLocation<?> psiLocation = location.toPsiLocation();
    final PsiClass containingClass = psiLocation.getParentElement(PsiClass.class);
    if (containingClass == null) return null;
    final String qualifiedName = containingClass.getQualifiedName();
    if (qualifiedName == null) return null;
    String containingMethod = null;
    for (Iterator<Location<PsiMethod>> iterator = psiLocation.getAncestors(PsiMethod.class, false); iterator.hasNext();) {
      final PsiMethod psiMethod = iterator.next().getPsiElement();
      if (containingClass.equals(psiMethod.getContainingClass())) containingMethod = psiMethod.getName();
    }
    if (containingMethod == null) return null;
    StackTraceLine lastLine = null;
    final String[] stackTrace = new LineTokenizer(stacktrace).execute();
    for (String aStackTrace : stackTrace) {
      final StackTraceLine line = new StackTraceLine(containingClass.getProject(), aStackTrace);
      if (containingMethod.equals(line.getMethodName()) && qualifiedName.equals(line.getClassName())) {
        lastLine = line;
        break;
      }
    }
    return lastLine != null ? lastLine.getOpenFileDescriptor(containingClass.getContainingFile().getVirtualFile()) : null;
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

  @Override
  public boolean isEditable() {
    return Registry.is("editable.java.test.console");
  }
}