// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author anna
 */
public final class RefactoringConflictsUtilImpl implements RefactoringConflictsUtil {
  private RefactoringConflictsUtilImpl() { }

  @Override
  public void analyzeHierarchyConflictsAfterMethodModifierChange(@NotNull PsiMethod method,
                                                                 @NotNull @PsiModifier.ModifierConstant String modifier,
                                                                 @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    JavaChangeSignatureUsageProcessor.ConflictSearcher.searchForHierarchyConflicts(method, conflicts, modifier);
  }

  @Override
  public void analyzeAccessibilityConflictsAfterMemberMove(@NotNull PsiClass targetClass,
                                                           @Nullable String newVisibility,
                                                           @NotNull Set<? extends PsiMember> membersToMove,
                                                           @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    analyzeAccessibilityConflictsAfterMemberMove(membersToMove, targetClass, newVisibility, targetClass, null, Conditions.alwaysTrue(),
                                                 conflicts);
  }

  @Override
  public void analyzeAccessibilityConflictsAfterMemberMove(@NotNull Set<? extends PsiMember> membersToMove,
                                                           @Nullable PsiClass targetClass,
                                                           @Nullable String newVisibility,
                                                           @NotNull PsiElement context,
                                                           @Nullable Set<? extends PsiMethod> abstractMethods,
                                                           @NotNull Condition<? super PsiReference> ignorePredicate,
                                                           @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(newVisibility)) { //Still need to check for access object
      newVisibility = PsiModifier.PUBLIC;
    }

    for (PsiMember member : membersToMove) {
      analyzeUsedElementsAfterMove(member, member, membersToMove, abstractMethods, targetClass, context, conflicts);
      checkAccessibilityConflictsAfterMove(member, newVisibility, targetClass, membersToMove, conflicts, ignorePredicate);
    }
  }

  public static void checkAccessibilityConflictsAfterMove(@NotNull PsiMember member,
                                                          @PsiModifier.ModifierConstant @Nullable String newVisibility,
                                                          @Nullable PsiClass targetClass,
                                                          @NotNull Set<? extends PsiMember> membersToMove,
                                                          @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                          @NotNull Condition<? super PsiReference> ignorePredicate) {
    PsiModifierList modifierListCopy = member.getModifierList();
    if (modifierListCopy != null) {
      modifierListCopy = (PsiModifierList)modifierListCopy.copy();
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && containingClass.isInterface()) {
        VisibilityUtil.setVisibility(modifierListCopy, PsiModifier.PUBLIC);
      }
    }
    if (newVisibility != null && modifierListCopy != null) {
      try {
        VisibilityUtil.setVisibility(modifierListCopy, newVisibility);
      }
      catch (IncorrectOperationException ignore) {
      } // do nothing and hope for the best
    }

    checkAccessibilityConflictsAfterMove(member, modifierListCopy, targetClass, membersToMove, conflicts, ignorePredicate);
  }

  private static void checkAccessibilityConflictsAfterMove(@NotNull PsiMember member,
                                                           @Nullable PsiModifierList modifierListCopy,
                                                           @Nullable PsiClass targetClass,
                                                           @NotNull Set<? extends PsiMember> membersToMove,
                                                           @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                           @NotNull Condition<? super PsiReference> ignorePredicate) {
    for (PsiReference psiReference : ReferencesSearch.search(member).asIterable()) {
      if (ignorePredicate.value(psiReference)) {
        checkAccessibilityConflictsAfterMove(psiReference, member, modifierListCopy, targetClass, membersToMove, conflicts);
      }
    }
  }

  public static void checkAccessibilityConflictsAfterMove(@NotNull PsiReference reference,
                                                          @NotNull PsiMember member,
                                                          @Nullable PsiModifierList modifierListCopy,
                                                          @Nullable PsiClass targetClass,
                                                          @NotNull Set<? extends PsiMember> membersToMove,
                                                          @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    PsiElement ref = reference.getElement();
    if (!RefactoringHierarchyUtil.willBeInTargetClass(ref, membersToMove, targetClass, true)) {
      JavaPsiFacade manager = JavaPsiFacade.getInstance(member.getProject());
      // check for target class accessibility
      if (targetClass != null && !manager.getResolveHelper().isAccessible(targetClass, targetClass.getModifierList(), ref, null, null)) {
        String message = JavaRefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                       RefactoringUIUtil.getDescription(targetClass, true),
                                                       VisibilityUtil.getVisibilityStringToDisplay(targetClass),
                                                       RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true));
        conflicts.putValue(targetClass, StringUtil.capitalize(message));
      }
      // check for member accessibility
      else if (!JavaResolveUtil.isAccessible(member, targetClass, modifierListCopy, ref, null, null)) {
        String message = JavaRefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                       RefactoringUIUtil.getDescription(member, false),
                                                       VisibilityUtil.toPresentableText(
                                                         VisibilityUtil.getVisibilityModifier(modifierListCopy)),
                                                       RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true));
        conflicts.putValue(ref, StringUtil.capitalize(message));
      }
    }
  }

  public static void analyzeUsedElementsAfterMove(PsiMember member,
                                                  PsiElement scope,
                                                  @NotNull Set<? extends PsiMember> membersToMove,
                                                  @Nullable Set<? extends PsiMethod> abstractMethods,
                                                  @Nullable PsiClass targetClass,
                                                  @NotNull PsiElement context,
                                                  MultiMap<PsiElement, @DialogMessage String> conflicts) {
    checkUsedElements(member, scope, membersToMove, abstractMethods, targetClass, null, context, conflicts);
  }

  private static void checkUsedElements(PsiMember member,
                                        PsiElement scope,
                                        @NotNull Set<? extends PsiMember> membersToMove,
                                        @Nullable Set<? extends PsiMethod> abstractMethods,
                                        @Nullable PsiClass targetClass,
                                        PsiClass accessClass,
                                        @NotNull PsiElement context,
                                        MultiMap<PsiElement, @DialogMessage String> conflicts) {
    final Set<PsiMember> moving = new HashSet<>(membersToMove);
    if (abstractMethods != null) {
      moving.addAll(abstractMethods);
    }
    if (scope instanceof PsiReferenceExpression refExpr) {
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        PsiExpression qualifier = refExpr.getQualifierExpression();
        PsiClass qualifierAccessClass = (PsiClass)(qualifier != null && !(qualifier instanceof PsiSuperExpression) ? PsiUtil.getAccessObjectClass(qualifier).getElement()
                                                                                                                   : accessClass != null && PsiTreeUtil.isAncestor(((PsiMember)refElement).getContainingClass(), accessClass, true) ? null : accessClass);
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false) &&
            (qualifierAccessClass == null || !RefactoringHierarchyUtil.willBeInTargetClass(qualifierAccessClass, moving, targetClass, false))) {
          analyzeAccessibilityAfterMove((PsiMember)refElement, context, qualifierAccessClass, member, conflicts);
        }
      }
    }
    else if (scope instanceof PsiNewExpression newExpression) {
      final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass != null) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(anonymousClass, moving, targetClass, false)) {
          analyzeAccessibilityAfterMove(anonymousClass, context, anonymousClass, member, conflicts);
        }
      }
      else {
        final PsiMethod refElement = newExpression.resolveConstructor();
        if (refElement != null) {
          if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
            analyzeAccessibilityAfterMove(refElement, context, accessClass, member, conflicts);
          }
        }
      }
    }
    else if (scope instanceof PsiJavaCodeReferenceElement refExpr) {
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
          analyzeAccessibilityAfterMove((PsiMember)refElement, context, accessClass, member, conflicts);
        }
      }
    }

    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiWhiteSpace || child instanceof PsiComment) continue;
      checkUsedElements(member, child, membersToMove, abstractMethods, targetClass,
                        child instanceof PsiClass ? (PsiClass)child : accessClass, context, conflicts);
    }
  }

  public static void analyzeAccessibilityAfterMove(PsiMember refMember,
                                                   @NotNull PsiElement newContext,
                                                   @Nullable PsiClass accessClass,
                                                   PsiMember member,
                                                   MultiMap<PsiElement, @DialogMessage String> conflicts) {
    PsiResolveHelper helper = JavaPsiFacade.getInstance(newContext.getProject()).getResolveHelper();
    if (!helper.isAccessible(refMember, refMember.getModifierList(), newContext, accessClass, newContext)) {
      String message = JavaRefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                     RefactoringUIUtil.getDescription(refMember, true),
                                                     VisibilityUtil.getVisibilityStringToDisplay(refMember),
                                                     RefactoringUIUtil.getDescription(member, false));
      conflicts.putValue(refMember, StringUtil.capitalize(message));
    }
    else if (newContext instanceof PsiClass aClass &&
             refMember instanceof PsiField &&
             refMember.getContainingClass() == member.getContainingClass()) {
      PsiField fieldInSubClass = aClass.findFieldByName(refMember.getName(), false);
      if (fieldInSubClass != null &&
          !refMember.hasModifierProperty(PsiModifier.STATIC) &&
          fieldInSubClass != refMember &&
          !member.hasModifierProperty(PsiModifier.STATIC)) {
        String message = JavaRefactoringBundle.message("dialog.message.0.would.hide.which.1.used.by.moved.2",
                                                       RefactoringUIUtil.getDescription(fieldInSubClass, true),
                                                       RefactoringUIUtil.getDescription(refMember, true),
                                                       RefactoringUIUtil.getDescription(member, false));
        conflicts.putValue(refMember, StringUtil.capitalize(message));
      }
    }
  }

  @Override
  public void analyzeModuleConflicts(@NotNull Project project,
                                     @Nullable Collection<? extends PsiElement> scopes,
                                     UsageInfo[] usages,
                                     @NotNull VirtualFile vFile,
                                     @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    if (scopes == null) return;
    for (final PsiElement scope : scopes) {
      if (scope instanceof PsiPackage) return;
    }

    final Module targetModule = ModuleUtilCore.findModuleForFile(vFile, project);
    if (targetModule == null) return;
    final GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(targetModule);
    final HashSet<PsiElement> reported = new HashSet<>();
    Consumer<PsiJavaCodeReferenceElement> processor = new Consumer<>() {
      @Override
      public void accept(PsiJavaCodeReferenceElement reference) {
        final PsiElement resolved = reference.resolve();
        if (resolved != null &&
            !reported.contains(resolved) &&
            !CommonRefactoringUtil.isAncestor(resolved, scopes) &&
            !(resolved instanceof LightElement) &&
            resolved.isPhysical() &&
            !haveElementInScope(resolved)) {
          if (resolved instanceof PsiMethod method) {
            for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
              if (haveElementInScope(superMethod)) return;
            }
          }
          final String scopeDescription = RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(reference), true);
          final String message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.in.module.2",
                                                           RefactoringUIUtil.getDescription(resolved, true),
                                                           scopeDescription,
                                                           CommonRefactoringUtil.htmlEmphasize(targetModule.getName()));
          conflicts.putValue(reference, StringUtil.capitalize(message));
          reported.add(resolved);
        }
      }

      private boolean haveElementInScope(PsiElement resolved) {
        if (PsiSearchScopeUtil.isInScope(resolveScope, resolved)) {
          return true;
        }
        if (!resolved.getManager().isInProject(resolved)) {
          if (resolved instanceof PsiMember member) {
            final PsiClass containingClass = member.getContainingClass();
            if (containingClass != null) {
              final String fqn = containingClass.getQualifiedName();
              if (fqn != null) {
                final PsiClass classFromTarget = JavaPsiFacade.getInstance(project).findClass(fqn, resolveScope);
                if (classFromTarget != null) {
                  if (resolved instanceof PsiMethod method) {
                    return classFromTarget.findMethodsBySignature(method, true).length > 0;
                  }
                  if (resolved instanceof PsiField field) {
                    return classFromTarget.findFieldByName(field.getName(), false) != null;
                  }
                  if (resolved instanceof PsiClass aClass) {
                    return classFromTarget.findInnerClassByName(aClass.getName(), false) != null;
                  }
                }
              }
            }
          }
          if (resolved instanceof PsiClass aClass) {
            final String fqn = aClass.getQualifiedName();
            return fqn != null && JavaPsiFacade.getInstance(project).findClass(fqn, resolveScope) != null;
          }
        }
        return false;
      }
    };
    for (final PsiElement scope : scopes) {
      JavaElementVisitor visitor;
      if (scope instanceof PsiCompiledElement) {
        // For compiled element walking visitor should not be used: see PsiWalkingState#elementStarted
        visitor = new JavaRecursiveElementVisitor() {
          @Override
          public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            processor.accept(reference);
          }
        };
      } else {
        visitor = new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            processor.accept(reference);
          }
        };
      }
      ProgressManager.progress(
        JavaRefactoringBundle.message("processing.progress.text", SymbolPresentationUtil.getSymbolPresentableText(scope)));
      scope.accept(visitor);
    }

    boolean isInTestSources = ModuleRootManager.getInstance(targetModule).getFileIndex().isInTestSourceContent(vFile);
    NextUsage:
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      final PsiElement referencedElement = (usage instanceof MoveRenameUsageInfo info) ? info.getReferencedElement() : usage.getElement();
      assert referencedElement != null : usage;
      final PsiFile movedFile = referencedElement.getContainingFile();
      if (!(movedFile instanceof PsiJavaFile)) continue NextUsage; // don't create conflicts for elements we are not responsible for
      if (element != null && PsiTreeUtil.getParentOfType(element, PsiImportStatement.class, false) == null) {
        for (PsiElement scope : scopes) {
          if (PsiTreeUtil.isAncestor(scope, element, false)) continue NextUsage;
        }

        final GlobalSearchScope resolveScope1 = element.getResolveScope();
        if (!resolveScope1.isSearchInModuleContent(targetModule, isInTestSources)) {
          final PsiFile usageFile = element.getContainingFile();
          PsiElement container = (usageFile instanceof PsiJavaFile) ? ConflictsUtil.getContainer(element) : usageFile;
          final String scopeDescription = RefactoringUIUtil.getDescription(container, true);
          final VirtualFile usageVFile = usageFile.getVirtualFile();
          if (usageVFile != null) {
            Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(usageVFile);
            if (module != null) {
              final String message;
              if (module == targetModule && isInTestSources) {
                message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.production.of.module.2",
                                                    RefactoringUIUtil.getDescription(referencedElement, true),
                                                    scopeDescription,
                                                    CommonRefactoringUtil.htmlEmphasize(module.getName()));
              }
              else {
                message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.module.2",
                                                    RefactoringUIUtil.getDescription(referencedElement, true),
                                                    scopeDescription,
                                                    CommonRefactoringUtil.htmlEmphasize(module.getName()));
              }
              conflicts.putValue(element, StringUtil.capitalize(message));
            }
          }
        }
      }
    }
  }

  @Override
  public void analyzeMethodConflictsAfterParameterDelete(@NotNull PsiMethod method,
                                                         @NotNull PsiParameter parameter,
                                                         @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    JavaSafeDeleteProcessor.collectMethodConflicts(conflicts, method, parameter);
  }
}
