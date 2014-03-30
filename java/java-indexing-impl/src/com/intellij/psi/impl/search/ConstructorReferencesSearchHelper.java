/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ConstructorReferencesSearchHelper {
  private final PsiManager myManager;

  public ConstructorReferencesSearchHelper(@NotNull PsiManager manager) {
    myManager = manager;
  }

  public boolean processConstructorReferences(@NotNull final Processor<PsiReference> processor,
                                              @NotNull final PsiMethod constructor,
                                              @NotNull final PsiClass containingClass,
                                              @NotNull final SearchScope searchScope,
                                              boolean ignoreAccessScope,
                                              final boolean isStrictSignatureSearch,
                                              @NotNull SearchRequestCollector collector) {
    final boolean[] constructorCanBeCalledImplicitly = new boolean[1];
    final boolean[] isEnum = new boolean[1];
    final boolean[] isUnder18 = new boolean[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        constructorCanBeCalledImplicitly[0] = constructor.getParameterList().getParametersCount() == 0;
        isEnum[0] = containingClass.isEnum();
        isUnder18[0] = PsiUtil.getLanguageLevel(containingClass).isAtLeast(LanguageLevel.JDK_1_8);
      }
    });

    if (isEnum[0]) {
      if (!processEnumReferences(processor, constructor, containingClass)) return false;
    }

    // search usages like "new XXX(..)"
    PairProcessor<PsiReference, SearchRequestCollector> processor1 = new PairProcessor<PsiReference, SearchRequestCollector>() {
      @Override
      public boolean process(PsiReference reference, SearchRequestCollector collector) {
        PsiElement parent = reference.getElement().getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiNewExpression) {
          PsiMethod constructor1 = ((PsiNewExpression)parent).resolveConstructor();
          if (constructor1 != null) {
            if (isStrictSignatureSearch) {
              if (myManager.areElementsEquivalent(constructor, constructor1)) {
                return processor.process(reference);
              }
            }
            else {
              if (myManager.areElementsEquivalent(containingClass, constructor1.getContainingClass())) {
                return processor.process(reference);
              }
            }
          }
        }
        return true;
      }
    };

    ReferencesSearch.searchOptimized(containingClass, searchScope, ignoreAccessScope, collector, true, processor1);
    if (isUnder18[0]) {
      if (!process18MethodPointers(processor, constructor, containingClass, searchScope)) return false;
    }

    // search usages like "this(..)"
    if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return processSuperOrThis(containingClass, constructor, constructorCanBeCalledImplicitly[0], searchScope, isStrictSignatureSearch,
                                  PsiKeyword.THIS, processor);
      }
    })) {
      return false;
    }

    // search usages like "super(..)"
    Processor<PsiClass> processor2 = new Processor<PsiClass>() {
      @Override
      public boolean process(PsiClass inheritor) {
        final PsiElement navigationElement = inheritor.getNavigationElement();
        if (navigationElement instanceof PsiClass) {
          return processSuperOrThis((PsiClass)navigationElement, constructor, constructorCanBeCalledImplicitly[0], searchScope,
                                    isStrictSignatureSearch, PsiKeyword.SUPER, processor);
        }
        return true;
      }
    };

    return ClassInheritorsSearch.search(containingClass, searchScope, false).forEach(processor2);
  }

  private static boolean processEnumReferences(@NotNull final Processor<PsiReference> processor,
                                               @NotNull final PsiMethod constructor,
                                               @NotNull final PsiClass aClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        for (PsiField field : aClass.getFields()) {
          if (field instanceof PsiEnumConstant) {
            PsiReference reference = field.getReference();
            if (reference != null && reference.isReferenceTo(constructor)) {
              if (!processor.process(reference)) {
                return false;
              }
            }
          }
        }
        return true;
      }
    });
  }

  private static boolean process18MethodPointers(@NotNull final Processor<PsiReference> processor,
                                                 @NotNull final PsiMethod constructor,
                                                 @NotNull PsiClass aClass, SearchScope searchScope) {
    return ReferencesSearch.search(aClass, searchScope).forEach(new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(PsiReference reference) {
        final PsiElement element = reference.getElement();
        if (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiMethodReferenceExpression &&
              ((PsiMethodReferenceExpression)parent).getReferenceNameElement() instanceof PsiKeyword) {
            if (((PsiMethodReferenceExpression)parent).isReferenceTo(constructor)) {
              if (!processor.process((PsiReference)parent)) return false;
            }
          }
        }
        return true;
      }
    });
  }

  private boolean processSuperOrThis(@NotNull PsiClass inheritor,
                                     @NotNull PsiMethod constructor,
                                     final boolean constructorCanBeCalledImplicitly,
                                     @NotNull SearchScope searchScope,
                                     final boolean isStrictSignatureSearch,
                                     @NotNull String superOrThisKeyword,
                                     @NotNull Processor<PsiReference> processor) {
    PsiMethod[] constructors = inheritor.getConstructors();
    if (constructors.length == 0 && constructorCanBeCalledImplicitly) {
      if (!processImplicitConstructorCall(inheritor, processor, constructor, inheritor)) return false;
    }
    for (PsiMethod method : constructors) {
      PsiCodeBlock body = method.getBody();
      if (body == null || method == constructor && isStrictSignatureSearch) {
        continue;
      }
      PsiStatement[] statements = body.getStatements();
      if (statements.length != 0) {
        PsiStatement statement = statements[0];
        if (statement instanceof PsiExpressionStatement) {
          PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
          if (expr instanceof PsiMethodCallExpression) {
            PsiReferenceExpression refExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
            if (PsiSearchScopeUtil.isInScope(searchScope, refExpr)) {
              if (refExpr.textMatches(superOrThisKeyword)) {
                PsiElement referencedElement = refExpr.resolve();
                if (referencedElement instanceof PsiMethod) {
                  PsiMethod constructor1 = (PsiMethod)referencedElement;
                  boolean match = isStrictSignatureSearch
                                  ? myManager.areElementsEquivalent(constructor1, constructor)
                                  : myManager.areElementsEquivalent(constructor.getContainingClass(), constructor1.getContainingClass());
                  if (match && !processor.process(refExpr)) return false;
                }
                //as long as we've encountered super/this keyword, no implicit ctr calls are possible here
                continue;
              }
            }
          }
        }
      }
      if (constructorCanBeCalledImplicitly) {
        if (!processImplicitConstructorCall(method, processor, constructor, inheritor)) return false;
      }
    }

    return true;
  }

  private boolean processImplicitConstructorCall(@NotNull final PsiMember usage,
                                                 @NotNull final Processor<PsiReference> processor,
                                                 @NotNull final PsiMethod constructor,
                                                 @NotNull final PsiClass containingClass) {
    if (containingClass instanceof PsiAnonymousClass) return true;
    boolean same = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return myManager.areElementsEquivalent(constructor.getContainingClass(), containingClass.getSuperClass());
      }
    });
    if (!same) {
      return true;
    }
    return processor.process(new LightMemberReference(myManager, usage, PsiSubstitutor.EMPTY) {
      @Override
      public PsiElement getElement() {
        return usage;
      }

      @Override
      public TextRange getRangeInElement() {
        if (usage instanceof PsiClass) {
          PsiIdentifier identifier = ((PsiClass)usage).getNameIdentifier();
          if (identifier != null) return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
        }
        else if (usage instanceof PsiField) {
          PsiIdentifier identifier = ((PsiField)usage).getNameIdentifier();
          return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
        }
        else if (usage instanceof PsiMethod) {
          PsiIdentifier identifier = ((PsiMethod)usage).getNameIdentifier();
          if (identifier != null) return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
        }
        return super.getRangeInElement();
      }
    });
  }
}
