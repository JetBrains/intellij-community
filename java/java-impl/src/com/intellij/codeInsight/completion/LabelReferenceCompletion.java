/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class LabelReferenceCompletion extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> LABEL_REFERENCE = psiElement().afterLeaf(PsiKeyword.BREAK, PsiKeyword.CONTINUE);

  static void processLabelReference(CompletionResultSet result, PsiLabelReference ref) {
    for (String s : ref.getVariants()) {
      result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SEMICOLON));
    }
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiReference ref = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
    if (ref instanceof PsiLabelReference) {
      processLabelReference(result, (PsiLabelReference)ref);
    }
  }
}
