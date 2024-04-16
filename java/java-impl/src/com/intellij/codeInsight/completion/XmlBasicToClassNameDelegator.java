// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class XmlBasicToClassNameDelegator extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, final @NotNull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiFile file = position.getContainingFile();
    if (parameters.getCompletionType() != CompletionType.BASIC || !JavaCompletionContributor.mayStartClassName(result)) {
      return;
    }

    final boolean empty = result.runRemainingContributors(parameters, true).isEmpty();

    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty && JavaClassReferenceCompletionContributor.findJavaClassReference(file, parameters.getOffset()) != null ||
        parameters.isExtendedCompletion()) {
      JavaClassNameCompletionContributor.addAllClasses(parameters, true, result.getPrefixMatcher(), lookupElement -> {
        JavaPsiClassReferenceElement classElement = lookupElement.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
        if (classElement != null) {
          classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }
        lookupElement.putUserData(XmlCompletionContributor.WORD_COMPLETION_COMPATIBLE, Boolean.TRUE); //todo think of a less dirty interaction
        result.addElement(lookupElement);
      });
    }
  }

}
