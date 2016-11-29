/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.task;

/**
 * @author Vladislav.Soroka
 * @since 8/5/2016
 */
public class ProjectTaskResult {
  private final boolean aborted;
  private final int errors;
  private final int warnings;

  public ProjectTaskResult(boolean aborted, int errors, int warnings) {
    this.aborted = aborted;
    this.errors = errors;
    this.warnings = warnings;
  }

  public boolean isAborted() {
    return aborted;
  }

  public int getErrors() {
    return errors;
  }

  public int getWarnings() {
    return warnings;
  }
}
