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

import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
class SuperCalls {
  static Set<LookupElement> suggestQualifyingSuperCalls(PsiElement element,
                                          PsiJavaReference javaReference,
                                          ElementFilter elementFilter,
                                          JavaCompletionProcessor.Options options,
                                          Condition<String> nameCondition) {
    Set<LookupElement> set = ContainerUtil.newLinkedHashSet();
    for (final String className : getContainingClassNames(element)) {
      PsiReferenceExpression fakeSuper = JavaCompletionUtil.createReference(className + ".super.rulez", element);
      PsiElement leaf = ObjectUtils.assertNotNull(fakeSuper.getReferenceNameElement());

      JavaCompletionProcessor superProcessor = new JavaCompletionProcessor(leaf, elementFilter, options, nameCondition);
      fakeSuper.processVariants(superProcessor);

      for (CompletionElement completionElement : superProcessor.getResults()) {
        for (LookupElement item : JavaCompletionUtil.createLookupElements(completionElement, javaReference, superProcessor)) {
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
      public void handleInsert(InsertionContext context) {
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
    Set<String> result = ContainerUtil.newLinkedHashSet();
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
