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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Common base interface for quick fixes provided by local and global inspections.
 *
 * @author anna
 * @since 6.0
 * @see CommonProblemDescriptor#getFixes()
 */
public interface QuickFix<D extends CommonProblemDescriptor> {
  QuickFix[] EMPTY_ARRAY = new QuickFix[0];

  /**
   * Returns the name of the quick fix.
   *
   * @return the name of the quick fix.
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  String getName();

  /**
   * @return text to appear in "Apply Fix" popup when multiple Quick Fixes exist (in the results of batch code inspection). For example,
   * if the name of the quickfix is "Create template &lt;filename&gt", the return value of getFamilyName() should be "Create template".
   * If the name of the quickfix does not depend on a specific element, simply return getName().
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  String getFamilyName();

  /**
   * Called to apply the fix.
   *
   * @param project    {@link Project}
   * @param descriptor problem reported by the tool which provided this quick fix action
   */
  void applyFix(@NotNull Project project, @NotNull D descriptor);
}
