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

package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoldingPolicy {
  private FoldingPolicy() {}

  public static boolean isCollapseByDefault(PsiElement element) {
    final Language lang = element.getLanguage();
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(lang);
    return foldingBuilder != null && foldingBuilder.isCollapsedByDefault(element.getNode());
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getSignature(PsiElement element) {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      String signature = provider.getSignature(element);
      if (signature != null) return signature;
    }
    return null;
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static PsiElement restoreBySignature(@NotNull PsiFile file, String signature) {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      PsiElement result = provider.restoreBySignature(file, signature);
      if (result != null) return result;
    }
    return null;
  }
}
