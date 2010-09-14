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
package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.SequenceIterator;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author cdr
 */
public class CompilerPerfTestAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    final CompilerManager compilerManager = CompilerManager.getInstance(project);

    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    Collection<BackendCompiler> compilers = configuration.getRegisteredJavaCompilers();
    final Iterator<BackendCompiler> it = new SequenceIterator<BackendCompiler>(compilers.iterator(),compilers.iterator(),compilers.iterator(),compilers.iterator(),compilers.iterator(),compilers.iterator(),compilers.iterator());

    CompileStatusNotification callback = new CompileStatusNotification() {
      volatile long start;
      BackendCompiler compiler;

      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        if (compiler == null) {
          next();
          return;
        }
        final long finish = System.currentTimeMillis();
        //System.out.println("Compiled with " +
        //                   compiler.getName() +
        //                   " in " +
        //                   TimeUnit.MILLISECONDS.toMinutes(finish - start) + "m" +TimeUnit.MILLISECONDS.toSeconds((finish - start)%60000) + "s" +
        //                   " with " +
        //                   errors +
        //                   " errors, " +
        //                   warnings +
        //                   " warnings, aborted=" +
        //                   aborted+"; free memory="+Runtime.getRuntime().freeMemory()+" bytes");
        //ProfilingUtil.forceCaptureMemorySnapshot();
        next();
      }

      void next() {
        if (!it.hasNext()) return;
        compiler = it.next();
        if (compiler.getId().equals("Jikes")|| compiler.getId().contains("Eclipse")) {
          next();
          return;
        }
        boolean success = compiler.checkCompiler(compilerManager.createProjectCompileScope(project));
        if (!success) {
          next();
          return;
        }
        configuration.setDefaultCompiler(compiler);
        start = System.currentTimeMillis();
        compilerManager.rebuild(this);
      }
    };

    callback.finished(false,0,0,null);
  }
}
