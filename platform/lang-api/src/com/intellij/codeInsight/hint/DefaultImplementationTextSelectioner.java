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

package com.intellij.codeInsight.hint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class DefaultImplementationTextSelectioner implements ImplementationTextSelectioner {
  private static final Logger LOG = Logger.getInstance(DefaultImplementationTextSelectioner.class);

  @Override
  public int getTextStartOffset(@NotNull final PsiElement parent) {
    final TextRange textRange = parent.getTextRange();
    LOG.assertTrue(textRange != null, parent);
    return textRange.getStartOffset();
  }

  @Override
  public int getTextEndOffset(@NotNull PsiElement element) {
    final TextRange textRange = element.getTextRange();
    LOG.assertTrue(textRange != null, element);
    return textRange.getEndOffset();
  }
}