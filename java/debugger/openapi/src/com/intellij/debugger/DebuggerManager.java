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
package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.util.Function;

/**
 * @author lex
 */
public abstract class DebuggerManager implements ProjectComponent {
  public static DebuggerManager getInstance(Project project) {
    return project.getComponent(DebuggerManager.class);
  }

  public abstract DebugProcess getDebugProcess(ProcessHandler processHandler);

  public abstract void addDebugProcessListener(ProcessHandler processHandler, DebugProcessListener listener);

  public abstract void removeDebugProcessListener(ProcessHandler processHandler, DebugProcessListener listener);

  public abstract boolean isDebuggerManagerThread();

  public abstract void addClassNameMapper(NameMapper mapper);

  public abstract void removeClassNameMapper(NameMapper mapper);

  public abstract String getVMClassQualifiedName(PsiClass aClass);

  /**
   * @deprecated use {@link PositionManagerFactory} extension point instead
   */
  @Deprecated
  public abstract void registerPositionManagerFactory(Function<DebugProcess, PositionManager> factory);
}
