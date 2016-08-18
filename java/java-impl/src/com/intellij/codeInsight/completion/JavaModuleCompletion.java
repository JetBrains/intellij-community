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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.completion.JavaKeywordCompletion.createKeyword;

class JavaModuleCompletion {
  static boolean isModuleFile(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel9OrHigher(file) && PsiJavaModule.MODULE_INFO_FILE.equals(file.getName());
  }

  static void addVariants(@NotNull PsiElement position, @NotNull Consumer<LookupElement> result) {
    if (position instanceof PsiIdentifier) {
      PsiElement context = position.getParent();
      if (context instanceof PsiErrorElement) context = context.getParent();

      addFileHeaderKeywords(position, context, result);

      addModuleStatementKeywords(position, context, result);
    }
  }

  private static void addFileHeaderKeywords(PsiElement position, PsiElement context, Consumer<LookupElement> result) {
    if (context instanceof PsiJavaFile && PsiTreeUtil.prevVisibleLeaf(position) == null) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addModuleStatementKeywords(PsiElement position, PsiElement context, Consumer<LookupElement> result) {
    if (context instanceof PsiJavaModule) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.REQUIRES), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.EXPORTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.USES), TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.PROVIDES), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }
}