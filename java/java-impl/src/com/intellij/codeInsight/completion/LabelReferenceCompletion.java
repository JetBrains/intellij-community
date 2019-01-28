// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author peter
 */
class LabelReferenceCompletion extends CompletionProvider<CompletionParameters> {
  static final ElementPattern<PsiElement> LABEL_REFERENCE = psiElement().afterLeaf(PsiKeyword.BREAK, PsiKeyword.CONTINUE);

  /**
   * Returns:<br/>
   * {@link Boolean#TRUE} - when the position is inside a value break statement of a switch expression;<br/>
   * {@link Boolean#FALSE} - when the position is inside a label break statement;<br/>
   * {@code null} - when a the position is not in a break statement.
   */
  static @Nullable Boolean isBreakValueOrLabelPosition(@NotNull PsiElement position) {
    PsiElement parent = position.getParent();
    if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifierExpression() == null) {
      PsiElement grand = parent.getParent();
      if (grand instanceof PsiBreakStatement) {
        return ((PsiBreakStatement)grand).findExitedElement() instanceof PsiSwitchExpression;
      }
    }

    return null;
  }

  static List<LookupElement> processLabelReference(PsiLabelReference ref) {
    return processLabelVariants(Arrays.asList(ref.getVariants()));
  }

  static List<LookupElement> processLabelVariants(Collection<?> variants) {
    return ContainerUtil.map(variants, s -> TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SEMICOLON));
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiReference ref = position.getContainingFile().findReferenceAt(parameters.getOffset());
    if (ref instanceof PsiLabelReference) {
      result.addAllElements(processLabelReference((PsiLabelReference)ref));
    }
    else if (isBreakValueOrLabelPosition(position) == Boolean.FALSE) {
      result.addAllElements(processLabelVariants(PsiImplUtil.findAllEnclosingLabels(position)));
    }
  }
}