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

/**
 * Describes a task to be executed before or after compilation.
 *
 * @see CompilerManager#addAfterTask(CompileTask)
 * @see CompilerManager#addBeforeTask(CompileTask)
 */
public interface CompileTask {
  /**
   * Executes the task.
   *
   * @param context current compile context
   * @return true if execution succeeded, false otherwise. If the task returns false, the compilation
   *         is aborted, and it's expected that the task adds a message defining the reason for the failure
   *         to the compile context.
   */
  boolean execute(CompileContext context);
}
