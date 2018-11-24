/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testframework.autotest.AbstractAutoTestManager;
import com.intellij.execution.testframework.autotest.AutoTestWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

@State(
  name = "JavaAutoRunManager",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public class JavaAutoRunManager extends AbstractAutoTestManager {
  @NotNull
  public static JavaAutoRunManager getInstance(Project project) {
    return ServiceManager.getService(project, JavaAutoRunManager.class);
  }

  public JavaAutoRunManager(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected AutoTestWatcher createWatcher(Project project) {
    return new AutoTestWatcher() {
      private boolean myHasErrors = false;
      private Disposable myEventDisposable;

      @Override
      public void activate() {
        if (myEventDisposable != null) {
          return;
        }

        myEventDisposable = Disposer.newDisposable();
        project.getMessageBus().connect(myEventDisposable).subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
          private boolean myFoundFilesToMake = false;

          @Override
          public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
            if (!myFoundFilesToMake) return;
            if (errors == 0) {
              restartAllAutoTests(0);
            }
            myHasErrors = errors == 0;
            myFoundFilesToMake = false;
          }

          @Override
          public void automakeCompilationFinished(int errors, int warnings, CompileContext compileContext) {
            compilationFinished(false, errors, warnings, compileContext);
          }

          @Override
          public void fileGenerated(String outputRoot, String relativePath) {
            myFoundFilesToMake = true;
          }
        });
      }

      @Override
      public void deactivate() {
        Disposable eventDisposable = myEventDisposable;
        if (eventDisposable != null) {
          myEventDisposable = null;
          Disposer.dispose(eventDisposable);
        }
      }

      @Override
      public boolean isUpToDate(int modificationStamp) {
        return !myHasErrors;
      }
    };
  }
}
