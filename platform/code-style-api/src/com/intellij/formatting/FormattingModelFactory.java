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
package com.intellij.formatting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A factory for standard formatting model implementations. Not to be used directly
 * by plugins - use {@link FormattingModelProvider} instead.
 *
 * @see FormattingModelProvider 
 */
@ApiStatus.Internal
interface FormattingModelFactory {
  FormattingModel createFormattingModelForPsiFile(PsiFile file,
                                                  @NotNull Block rootBlock,
                                                  CodeStyleSettings settings);

  /**
   * Creates a formatting model with a single root block covering the given {@code PsiElement} with its child elements.
   * The formatter will leave a content inside the block unchanged.
   *
   * @param element The element to create a dummy formatting model for.
   * @return The dummy single-block formatting model covering the given element.
   */
  @NotNull
  FormattingModel createDummyFormattingModel(@NotNull PsiElement element);
}
