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
package com.intellij.openapi.compiler;

import java.util.EventListener;

/**
 * A listener for compiler events.
 *
 * @see CompilerManager#addCompilationStatusListener(CompilationStatusListener)
 */
public interface CompilationStatusListener extends EventListener {
  /**
   * Invoked in a Swing dispatch thread after the compilation is finished.
   *
   * @param aborted  true if compilation has been cancelled
   * @param errors   error count
   * @param warnings warning count
   * @param compileContext context for the finished compilation
   */
  default void compilationFinished(boolean aborted, int errors, int warnings, final CompileContext compileContext){
  }

  default void automakeCompilationFinished(int errors, int warnings, final CompileContext compileContext) {
  }

  default void fileGenerated(String outputRoot, String relativePath) {
  }
}
