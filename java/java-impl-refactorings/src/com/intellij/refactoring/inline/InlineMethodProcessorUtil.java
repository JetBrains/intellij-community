// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

@ApiStatus.Internal
public final class InlineMethodProcessorUtil {
  private static final Logger LOG = Logger.getInstance(InlineMethodProcessorUtil.class);

  private InlineMethodProcessorUtil() { }

  public static UsageInfo @NotNull [] findUsages(@NotNull PsiMethod method,
                                                  @Nullable PsiReference reference,
                                                  @NotNull SearchScope refactoringScope,
                                                  boolean inlineThisOnly,
                                                  boolean deleteTheDeclaration,
                                                  boolean searchInComments,
                                                  boolean searchForTextOccurrences) {
    if (inlineThisOnly) return new UsageInfo[]{new UsageInfo(Objects.requireNonNull(reference))};
    Set<UsageInfo> usages = ConcurrentCollectionFactory.createConcurrentSet();
    if (reference != null) {
      usages.add(new UsageInfo(reference.getElement()));
    }
    for (PsiReference ref : MethodReferencesSearch.search(method, refactoringScope, true).findAll()) {
      usages.add(new UsageInfo(ref.getElement()));
    }

    if (deleteTheDeclaration) {
      OverridingMethodsSearch.search(method, refactoringScope, true)
        .forEach(overridingMethod -> {
          if (shouldDeleteOverrideAttribute(method, overridingMethod)) {
            usages.add(new OverrideAttributeUsageInfo(overridingMethod));
          }
          return true;
        });
    }

    if (searchInComments || searchForTextOccurrences) {
      final NonCodeUsageInfoFactory infoFactory = new NonCodeUsageInfoFactory(method, method.getName()) {
        @Override
        public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
          if (PsiTreeUtil.isAncestor(method, usage, false)) return null;
          return super.createUsageInfo(usage, startOffset, endOffset);
        }
      };
      if (searchInComments) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(method, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
        TextOccurrencesUtil.addUsagesInStringsAndComments(method, refactoringScope, stringToSearch, usages, infoFactory);
      }

      if (searchForTextOccurrences && refactoringScope instanceof GlobalSearchScope scope) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(method, NonCodeSearchDescriptionLocation.NON_JAVA);
        TextOccurrencesUtil.addTextOccurrences(method, stringToSearch, scope, usages, infoFactory);
      }
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static boolean shouldDeleteOverrideAttribute(@NotNull PsiMethod inlinedMethod, @NotNull PsiMethod overridingMethod) {
    return ContainerUtil.and(overridingMethod.getHierarchicalMethodSignature().getSuperSignatures(), signature -> {
      PsiMethod superMethod = signature.getMethod();
      if (superMethod == inlinedMethod) {
        return true;
      }
      if (JavaLanguage.INSTANCE == overridingMethod.getLanguage() &&
          Objects.requireNonNull(superMethod.getContainingClass()).isInterface()) {
        return !PsiUtil.isAvailable(JavaFeature.OVERRIDE_INTERFACE, overridingMethod);
      }
      return false;
    });
  }

  public static void collectConflicts(@NotNull PsiMethod method,
                                      @Nullable PsiReference reference,
                                      UsageInfo @NotNull [] usages,
                                      @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                      @NotNull Function<PsiReference, InlineTransformer> transformerChooser,
                                      boolean inlineThisOnly) {
    if (!inlineThisOnly) {
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        String className = Objects.requireNonNull(superMethod.getContainingClass()).getQualifiedName();
        final String message = superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
                               JavaRefactoringBundle.message("inlined.method.implements.method.from.0", className) :
                               JavaRefactoringBundle.message("inlined.method.overrides.method.from.0", className);
        conflicts.putValue(superMethod, message);
      }

      for (UsageInfo info : usages) {
        final PsiElement element = info.getElement();
        if (element instanceof PsiDocMethodOrFieldRef && !PsiTreeUtil.isAncestor(method, element, false)) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inline.method.used.in.javadoc"));
        }
        if (element instanceof PsiLiteralExpression &&
            ContainerUtil.or(element.getReferences(), JavaLangClassMemberReference.class::isInstance)) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inline.method.used.in.reflection"));
        }
        if (element instanceof PsiMethodReferenceExpression ref) {
          processSideEffectsInMethodReferenceQualifier(conflicts, ref);
        }
        if (element instanceof PsiReferenceExpression ref && transformerChooser.apply(ref).isFallBackTransformer()) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
        }

        final String errorMessage = InlineMethodProcessor.checkUnableToInsertCodeBlock(method.getBody(), element);
        if (errorMessage != null) {
          conflicts.putValue(element, errorMessage);
        }
      }
    }
    else if (reference != null && transformerChooser.apply(reference).isFallBackTransformer()) {
      conflicts.putValue(reference.getElement(),
                         JavaRefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
    }
    else if (reference instanceof PsiMethodReferenceExpression ref) {
      processSideEffectsInMethodReferenceQualifier(conflicts, ref);
    }
    InlineMethodProcessor.addInaccessibleMemberConflicts(method, usages, new ReferencedElementsCollector(), conflicts);
    addInaccessibleSuperCallsConflicts(method, usages, conflicts);
  }

  private static void processSideEffectsInMethodReferenceQualifier(@NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                                   @NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
    if (qualifierExpression != null) {
      final List<PsiElement> sideEffects = new ArrayList<>();
      SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
      if (!sideEffects.isEmpty()) {
        conflicts.putValue(methodReferenceExpression, JavaRefactoringBundle.message("inline.method.qualifier.usage.side.effect"));
      }
    }
  }

  private static void addInaccessibleSuperCallsConflicts(@NotNull PsiMethod method,
                                                          UsageInfo @NotNull [] usages,
                                                          MultiMap<PsiElement, @DialogMessage String> conflicts) {
    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {}

      @Override
      public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {}

      @Override
      public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
        super.visitSuperExpression(expression);
        final PsiType type = expression.getType();
        final PsiClass superClass = PsiUtil.resolveClassInType(type);
        if (superClass != null) {
          final Set<PsiClass> targetContainingClasses = new HashSet<>();
          PsiElement qualifiedCall = null;
          for (UsageInfo info : usages) {
            final PsiElement element = info.getElement();
            if (element != null) {
              final PsiClass targetContainingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
              if (targetContainingClass != null &&
                  (!InheritanceUtil.isInheritorOrSelf(targetContainingClass, superClass, true) ||
                   PsiUtil.getEnclosingStaticElement(element, targetContainingClass) != null)) {
                targetContainingClasses.add(targetContainingClass);
              }
              else if (element instanceof PsiReferenceExpression ref && !ExpressionUtil.isEffectivelyUnqualified(ref)) {
                qualifiedCall = ref.getQualifierExpression();
              }
            }
          }
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
          LOG.assertTrue(methodCallExpression != null);
          if (!targetContainingClasses.isEmpty()) {
            String names = StringUtil.join(targetContainingClasses, psiClass -> RefactoringUIUtil.getDescription(psiClass, false), ",");
            String message = JavaRefactoringBundle.message("inline.method.calls.not.accessible.in", methodCallExpression.getText(), names);
            conflicts.putValue(expression, message);
          }

          if (qualifiedCall != null) {
            conflicts.putValue(expression, JavaRefactoringBundle.message("inline.method.calls.not.accessible.on.qualifier",
                                                                         methodCallExpression.getText(), qualifiedCall.getText()));
          }
        }
      }
    });
  }

  public static Map<Language, InlineHandler.Inliner> initInliners(@NotNull PsiMethod method,
                                                                   UsageInfo @NotNull [] usages,
                                                                   boolean inlineThisOnly,
                                                                   @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    return GenericInlineHandler.initInliners(method, usages, new InlineHandler.Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return inlineThisOnly;
      }
    }, conflicts, JavaLanguage.INSTANCE);
  }

  static class OverrideAttributeUsageInfo extends UsageInfo {
    OverrideAttributeUsageInfo(@NotNull PsiElement element) {
      super(element);
    }
  }
}