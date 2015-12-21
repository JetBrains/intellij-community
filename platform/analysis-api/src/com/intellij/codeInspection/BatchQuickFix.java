/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: anna
 * Date: 10/27/11
 */
public interface BatchQuickFix<D extends CommonProblemDescriptor> {
  /**
   * Called to apply the cumulative fix. Is invoked in WriteAction
   *
   * @param project    {@link com.intellij.openapi.project.Project}
   * @param descriptors problem reported by the tool on which fix should work
   * @param psiElementsToIgnore elements to be excluded from view during post-refresh
   * @param refreshViews post-refresh inspection results view; would remove collected elements from the view
   */
  void applyFix(@NotNull final Project project,
                @NotNull final D[] descriptors,
                @NotNull final List<PsiElement> psiElementsToIgnore,
                @Nullable final Runnable refreshViews);
}
