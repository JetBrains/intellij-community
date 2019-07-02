// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class LabelReferenceCompletion extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> LABEL_REFERENCE = psiElement().afterLeaf(PsiKeyword.BREAK, PsiKeyword.CONTINUE);

  static List<LookupElement> processLabelReference(PsiLabelReference ref) {
    return ContainerUtil.map(ref.getVariants(), s -> TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SEMICOLON));
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiReference ref = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
    if (ref instanceof PsiLabelReference) {
      result.addAllElements(processLabelReference((PsiLabelReference)ref));
    }
  }
}