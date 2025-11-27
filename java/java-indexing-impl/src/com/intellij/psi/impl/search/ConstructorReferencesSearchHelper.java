// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.java.syntax.parser.JavaKeywords;
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
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

class ConstructorReferencesSearchHelper {
  private final PsiManager myManager;

  ConstructorReferencesSearchHelper(@NotNull PsiManager manager) {
    myManager = manager;
  }

  /*
   * The Project is passed around explicitly to avoid invoking PsiElement.getProject each time we need it. There are two reasons:
   * 1. Performance. getProject traverses AST upwards
   * 2. Exception avoidance. Project is needed outside of read action (to run it via DumbService in the first place),
   *    and so getProject would fail with an assertion that read action is required but not present.
   */
  boolean processConstructorReferences(@NotNull Processor<? super PsiReference> processor,
                                       @NotNull PsiMethod constructor,
                                       @NotNull PsiClass containingClass,
                                       @NotNull SearchScope searchScope,
                                       @NotNull Project project,
                                       boolean ignoreAccessScope,
                                       boolean isStrictSignatureSearch,
                                       @NotNull SearchRequestCollector collector) {
    final boolean[] constructorCanBeCalledImplicitly = new boolean[1];
    final boolean[] isEnum = new boolean[1];
    final boolean[] isUnder18 = new boolean[1];

    DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      final PsiParameter[] parameters = constructor.getParameterList().getParameters();
      constructorCanBeCalledImplicitly[0] = parameters.length == 0 || (parameters.length == 1 && parameters[0].isVarArgs());
      isEnum[0] = containingClass.isEnum();
      isUnder18[0] = PsiUtil.getLanguageLevel(containingClass).isAtLeast(LanguageLevel.JDK_1_8);
      return null;
    });

    if (isEnum[0]) {
      if (!processEnumReferences(processor, constructor, project, containingClass, searchScope)) return false;
    }

    // search usages like "new XXX(..)"
    PairProcessor<PsiReference, SearchRequestCollector> processor1 = (reference, collector1) -> {
      PsiElement parent = reference.getElement().getParent();
      if (parent instanceof PsiAnonymousClass) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiNewExpression expression) {
        PsiMethod constructor1 = expression.resolveConstructor();
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

    SearchScope restrictedScope = searchScope instanceof GlobalSearchScope scope
                                  ? scope.intersectWith(new JavaFilesSearchScope(project))
                                  : searchScope;

    ReferencesSearch.searchOptimized(containingClass, restrictedScope, ignoreAccessScope, collector, true, processor1);
    if (isUnder18[0]) {
      if (!process18MethodPointers(processor, constructor, project, containingClass, restrictedScope)) return false;
    }

    // search usages like "this(..)"
    if (!DumbService.getInstance(project).runReadActionInSmartMode(
      () -> processSuperOrThis(containingClass, constructor, constructorCanBeCalledImplicitly[0], searchScope, project,
                               isStrictSignatureSearch, JavaKeywords.THIS, processor))) {
      return false;
    }

    // search usages like "super(..)"
    Processor<PsiClass> processor2 = inheritor -> {
      if (inheritor.getNavigationElement() instanceof PsiClass aClass) {
        return processSuperOrThis(aClass, constructor, constructorCanBeCalledImplicitly[0], searchScope, project,
                                  isStrictSignatureSearch, JavaKeywords.SUPER, processor);
      }
      return true;
    };

    return ClassInheritorsSearch.search(containingClass, searchScope, false).allowParallelProcessing().forEach(processor2);
  }

  private static boolean processEnumReferences(@NotNull Processor<? super PsiReference> processor,
                                               @NotNull PsiMethod constructor,
                                               @NotNull Project project,
                                               @NotNull PsiClass aClass, 
                                               @NotNull SearchScope searchScope) {
    return DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      for (PsiField field : aClass.getFields()) {
        if (field instanceof PsiEnumConstant && PsiSearchScopeUtil.isInScope(searchScope, field)) {
          PsiReference reference = field.getReference();
          if (reference != null && reference.isReferenceTo(constructor) && !processor.process(reference)) {
            return false;
          }
        }
      }
      return true;
    });
  }

  private static boolean process18MethodPointers(@NotNull Processor<? super PsiReference> processor,
                                                 @NotNull PsiMethod constructor,
                                                 @NotNull Project project,
                                                 @NotNull PsiClass aClass, SearchScope searchScope) {
    return ReferencesSearch.search(aClass, searchScope).forEach(reference -> {
      final PsiElement element = reference.getElement();
      return DumbService.getInstance(project).runReadActionInSmartMode(() -> {
        if (element.getParent() instanceof PsiMethodReferenceExpression ref
            && ref.getReferenceNameElement() instanceof PsiKeyword
            && ref.isReferenceTo(constructor)) {
          return processor.process(ref);
        }
        return true;
      });
    });
  }

  private boolean processSuperOrThis(@NotNull PsiClass inheritor,
                                     @NotNull PsiMethod constructor,
                                     boolean constructorCanBeCalledImplicitly,
                                     @NotNull SearchScope searchScope,
                                     @NotNull Project project,
                                     boolean isStrictSignatureSearch,
                                     @NotNull String superOrThisKeyword,
                                     @NotNull Processor<? super PsiReference> processor) {
    PsiMethod[] constructors = inheritor.getConstructors();
    if (constructors.length == 0 && constructorCanBeCalledImplicitly) {
      if (!processImplicitConstructorCall(inheritor, processor, constructor, project, inheritor)) return false;
    }
    for (PsiMethod method : constructors) {
      if (method == constructor && isStrictSignatureSearch || method instanceof SyntheticElement) {
        continue;
      }
      PsiMethodCallExpression thisOrSuperCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
      if (thisOrSuperCall != null) {
        if (PsiSearchScopeUtil.isInScope(searchScope, thisOrSuperCall)) {
          PsiReferenceExpression ref = thisOrSuperCall.getMethodExpression();
          if (ref.textMatches(superOrThisKeyword) && ref.resolve() instanceof PsiMethod referencedConstructor) {
            boolean match = isStrictSignatureSearch
                            ? myManager.areElementsEquivalent(referencedConstructor, constructor)
                            : myManager.areElementsEquivalent(constructor.getContainingClass(), referencedConstructor.getContainingClass());
            if (match && !processor.process(ref)) return false;
          }
        }
        //when we've encountered a super/this call, no implicit ctr calls are possible here
        continue;
      }
      if (constructorCanBeCalledImplicitly && PsiSearchScopeUtil.isInScope(searchScope, method)) {
        if (!processImplicitConstructorCall(method, processor, constructor, project, inheritor)) return false;
      }
    }

    return true;
  }

  private boolean processImplicitConstructorCall(@NotNull PsiMember usage,
                                                 @NotNull Processor<? super PsiReference> processor,
                                                 @NotNull PsiMethod constructor,
                                                 @NotNull Project project,
                                                 @NotNull PsiClass containingClass) {
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
      public @NotNull PsiElement getElement() {
        return usage;
      }

      @Override
      public @NotNull TextRange getRangeInElement() {
        if (usage instanceof PsiNameIdentifierOwner owner) {
          PsiElement identifier = owner.getNameIdentifier();
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
