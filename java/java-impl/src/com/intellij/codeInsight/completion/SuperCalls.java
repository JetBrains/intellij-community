// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author peter
 */
final class SuperCalls {
  static Set<LookupElement> suggestQualifyingSuperCalls(PsiElement element,
                                                        PsiJavaReference javaReference,
                                                        ElementFilter elementFilter,
                                                        JavaCompletionProcessor.Options options,
                                                        Condition<? super String> nameCondition) {
    Set<LookupElement> set = new LinkedHashSet<>();
    for (final String className : getContainingClassNames(element)) {
      PsiReferenceExpression fakeSuper = JavaCompletionUtil.createReference(className + ".super.rulez", element);
      PsiElement leaf = Objects.requireNonNull(fakeSuper.getReferenceNameElement());

      JavaCompletionProcessor superProcessor = new JavaCompletionProcessor(leaf, elementFilter, options, nameCondition);
      fakeSuper.processVariants(superProcessor);

      for (CompletionElement completionElement : superProcessor.getResults()) {
        for (LookupElement item : JavaCompletionUtil.createLookupElements(completionElement, javaReference)) {
          set.add(withQualifiedSuper(className, item));
        }
      }
    }
    return set;
  }

  @NotNull
  private static LookupElement withQualifiedSuper(final String className, LookupElement item) {
    return PrioritizedLookupElement.withExplicitProximity(new LookupElementDecorator<LookupElement>(item) {

      @Override
      public void renderElement(LookupElementPresentation presentation) {
        super.renderElement(presentation);
        presentation.setItemText(className + ".super." + presentation.getItemText());
      }

      @Override
      public void handleInsert(@NotNull InsertionContext context) {
        context.commitDocument();
        PsiJavaCodeReferenceElement ref = PsiTreeUtil
          .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
        if (ref != null) {
          context.getDocument().insertString(ref.getTextRange().getStartOffset(),  className + ".");
        }

        super.handleInsert(context);
      }
    }, -1);
  }

  private static Set<String> getContainingClassNames(PsiElement position) {
    Set<String> result = new LinkedHashSet<>();
    boolean add = false;
    while (position != null) {
      if (position instanceof PsiAnonymousClass) {
        add = true;
      }
      else if (add && position instanceof PsiClass) {
        ContainerUtil.addIfNotNull(result, ((PsiClass)position).getName());
      }
      position = position.getParent();
    }
    return result;
  }
}
