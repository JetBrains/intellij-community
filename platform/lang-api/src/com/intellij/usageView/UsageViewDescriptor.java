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
package com.intellij.usageView;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UsageViewDescriptor {
  /**
   * @return an array of elements whose usages were searched or {@link com.intellij.psi.PsiElement#EMPTY_ARRAY} if not available
   */
  @NotNull
  PsiElement[] getElements();

  String getProcessedElementsHeader();

  String getCodeReferencesText(int usagesCount, int filesCount);

  @Nullable
  String getCommentReferencesText(int usagesCount, int filesCount);
}