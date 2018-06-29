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
package com.intellij.psi.impl.search;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
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
class ConstructorReferencesSearchHelper {
  private final PsiManager myManager;

  ConstructorReferencesSearchHelper(@NotNull PsiManager manager) {
    myManager = manager;
  }

  /*
   * Project is passed around explicitly to avoid invoking PsiElement.getProject each time we need it. There are two reasons:
   * 1. Performance. getProject traverses AST upwards
   * 2. Exception avoidance. Project is needed outside of read action (to run it via DumbService in the first place),
   *    and so getProject would fail with an assertion that read action is required but not present.
   */
  boolean processConstructorReferences(@NotNull final Processor<PsiReference> processor,
                                       @NotNull final PsiMethod constructor,
                                       @NotNull final PsiClass containingClass,
                                       @NotNull final SearchScope searchScope,
                                       @NotNull final Project project,
                                       boolean ignoreAccessScope,
                                       final boolean isStrictSignatureSearch,
                                       @NotNull SearchRequestCollector collector) {
    final boolean[] constructorCanBeCalledImplicitly = new boolean[1];
    final boolean[] isEnum = new boolean[1];
    final boolean[] isUnder18 = new boolean[1];

    DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      final PsiParameter[] parameters = constructor.getParameterList().getParameters();
      constructorCanBeCalledImplicitly[0] = parameters.length == 0;
      if (!constructorCanBeCalledImplicitly[0]) {
        constructorCanBeCalledImplicitly[0] = parameters.length == 1 && parameters[0].isVarArgs();
      }
      isEnum[0] = containingClass.isEnum();
      isUnder18[0] = PsiUtil.getLanguageLevel(containingClass).isAtLeast(LanguageLevel.JDK_1_8);
      return null;
    });

    if (isEnum[0]) {
      if (!processEnumReferences(processor, constructor, project, containingClass)) return false;
    }

    // search usages like "new XXX(..)"
    PairProcessor<PsiReference, SearchRequestCollector> processor1 = (reference, collector1) -> {
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
    };

    SearchScope restrictedScope = searchScope instanceof GlobalSearchScope
                                  ? ((GlobalSearchScope)searchScope).intersectWith(new JavaFilesSearchScope(project))
                                  : searchScope;

    ReferencesSearch.searchOptimized(containingClass, restrictedScope, ignoreAccessScope, collector, true, processor1);
    if (isUnder18[0]) {
      if (!process18MethodPointers(processor, constructor, project, containingClass, restrictedScope)) return false;
    }

    // search usages like "this(..)"
    if (!DumbService.getInstance(project).runReadActionInSmartMode(
      () -> processSuperOrThis(containingClass, constructor, constructorCanBeCalledImplicitly[0], searchScope, project,
                               isStrictSignatureSearch,
                               PsiKeyword.THIS, PsiKeyword.SUPER, processor))) {
      return false;
    }

    // search usages like "super(..)"
    Processor<PsiClass> processor2 = inheritor -> {
      final PsiElement navigationElement = inheritor.getNavigationElement();
      if (navigationElement instanceof PsiClass) {
        return processSuperOrThis((PsiClass)navigationElement, constructor, constructorCanBeCalledImplicitly[0], searchScope, project,
                                  isStrictSignatureSearch, PsiKeyword.SUPER, PsiKeyword.THIS, processor);
      }
      return true;
    };

    return ClassInheritorsSearch.search(containingClass, searchScope, false).forEach(processor2);
  }

  private static boolean processEnumReferences(@NotNull final Processor<PsiReference> processor,
                                               @NotNull final PsiMethod constructor,
                                               @NotNull final Project project,
                                               @NotNull final PsiClass aClass) {
    return DumbService.getInstance(project).runReadActionInSmartMode(() -> {
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
    });
  }

  private static boolean process18MethodPointers(@NotNull final Processor<PsiReference> processor,
                                                 @NotNull final PsiMethod constructor,
                                                 @NotNull final Project project,
                                                 @NotNull PsiClass aClass, SearchScope searchScope) {
    return ReferencesSearch.search(aClass, searchScope).forEach(reference -> {
      final PsiElement element = reference.getElement();
      if (element != null) {
        return DumbService.getInstance(project).runReadActionInSmartMode(() -> {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiMethodReferenceExpression &&
              ((PsiMethodReferenceExpression)parent).getReferenceNameElement() instanceof PsiKeyword) {
            if (((PsiMethodReferenceExpression)parent).isReferenceTo(constructor)) {
              if (!processor.process((PsiReference)parent)) return false;
            }
          }
          return true;
        });
      }
      return true;
    });
  }

  private boolean processSuperOrThis(@NotNull PsiClass inheritor,
                                     @NotNull PsiMethod constructor,
                                     final boolean constructorCanBeCalledImplicitly,
                                     @NotNull SearchScope searchScope,
                                     @NotNull Project project,
                                     final boolean isStrictSignatureSearch,
                                     @NotNull String superOrThisKeyword,
                                     @NotNull String thisOrSuperKeyword,
                                     @NotNull Processor<PsiReference> processor) {
    PsiMethod[] constructors = inheritor.getConstructors();
    if (constructors.length == 0 && constructorCanBeCalledImplicitly) {
      if (!processImplicitConstructorCall(inheritor, processor, constructor, project, inheritor)) return false;
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
              else if (refExpr.textMatches(thisOrSuperKeyword)) {
                continue;
              }
            }
          }
        }
      }
      if (constructorCanBeCalledImplicitly && PsiSearchScopeUtil.isInScope(searchScope, method)) {
        if (!processImplicitConstructorCall(method, processor, constructor, project, inheritor)) return false;
      }
    }

    return true;
  }

  private boolean processImplicitConstructorCall(@NotNull final PsiMember usage,
                                                 @NotNull final Processor<PsiReference> processor,
                                                 @NotNull final PsiMethod constructor,
                                                 @NotNull final Project project,
                                                 @NotNull final PsiClass containingClass) {
    if (containingClass instanceof PsiAnonymousClass) return true;

    PsiClass ctrClass = constructor.getContainingClass();
    if (ctrClass == null) return true;

    boolean isImplicitSuper = DumbService.getInstance(project).runReadActionInSmartMode(
      () -> myManager.areElementsEquivalent(ctrClass, containingClass.getSuperClass()));
    if (!isImplicitSuper) {
      return true;
    }

    PsiElement resolved = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(usage, project, ctrClass);

    boolean resolvesToThisConstructor = DumbService.getInstance(project).runReadActionInSmartMode(
      () -> myManager.areElementsEquivalent(constructor, resolved));

    if (!resolvesToThisConstructor) {
      return true;
    }
    return processor.process(new LightMemberReference(myManager, usage, PsiSubstitutor.EMPTY) {
      @Override
      public PsiElement getElement() {
        return usage;
      }

      @Override
      public TextRange getRangeInElement() {
        if (usage instanceof PsiNameIdentifierOwner) {
          PsiElement identifier = ((PsiNameIdentifierOwner)usage).getNameIdentifier();
          if (identifier != null) {
            final int startOffsetInParent = identifier.getStartOffsetInParent();
            if (startOffsetInParent >= 0) { // -1 for light elements generated e.g. by lombok
              return TextRange.from(startOffsetInParent, identifier.getTextLength());
            }
            else {
              return new UnfairTextRange(-1, -1);
            }
          }
        }
        return super.getRangeInElement();
      }
    });
  }
}
