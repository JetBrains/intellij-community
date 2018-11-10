/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionConfidence {

  /**
   * @deprecated not used anymore, only the user controls whether the lookup will be focused
   */
  @Deprecated
  @NotNull
  public ThreeState shouldFocusLookup(@NotNull CompletionParameters parameters) { 
    return ThreeState.UNSURE; 
  }

  /**
   * This method is invoked first when a completion autopopup is scheduled. Extensions are able to cancel this completion process based on location.
   * For example, in string literals or comments completion autopopup may do more harm than good.
   */
  @NotNull
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    return ThreeState.UNSURE;
  }
}
