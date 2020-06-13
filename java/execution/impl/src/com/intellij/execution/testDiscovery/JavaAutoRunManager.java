// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public static @NotNull JavaAutoRunManager getInstance(Project project) {
    return ServiceManager.getService(project, JavaAutoRunManager.class);
  }

  public JavaAutoRunManager(@NotNull Project project) {
    super(project);
  }

  @Override
  protected @NotNull AutoTestWatcher createWatcher(@NotNull Project project) {
    return new AutoTestWatcher() {
      private boolean myHasErrors = false;
      private Disposable myEventDisposable;

      @Override
      public void activate() {
        if (myEventDisposable != null) {
          return;
        }

        myEventDisposable = Disposer.newDisposable();
        Disposer.register(project, myEventDisposable);
        project.getMessageBus().connect(myEventDisposable).subscribe(CompilerTopics.COMPILATION_STATUS, new CompilationStatusListener() {
          private boolean myFoundFilesToMake = false;

          @Override
          public void compilationFinished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
            if (!myFoundFilesToMake) return;
            if (errors == 0) {
              restartAllAutoTests(0);
            }
            myHasErrors = errors == 0;
            myFoundFilesToMake = false;
          }

          @Override
          public void automakeCompilationFinished(int errors, int warnings, @NotNull CompileContext compileContext) {
            compilationFinished(false, errors, warnings, compileContext);
          }

          @Override
          public void fileGenerated(@NotNull String outputRoot, @NotNull String relativePath) {
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
