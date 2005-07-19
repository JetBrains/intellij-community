/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public interface LocalQuickFix {
  String getName();

  /**
   * Called to apply the fix.
   * @param project {@link com.intellij.openapi.project.Project}
   * @param descriptor problem reported by the tool which provided this quick fix action
   */
  void applyFix(Project project, ProblemDescriptor descriptor);

  //to appear in "Apply Fix" statement when multiple Quick Fixes exist
  String getFamilyName();
}
