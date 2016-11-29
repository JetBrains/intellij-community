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
package com.intellij.refactoring.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author anna
 * Date: 05-Oct-2009
 */
public class RefactoringConflictsUtil {
  private RefactoringConflictsUtil() { }

  public static void analyzeAccessibilityConflicts(@NotNull Set<PsiMember> membersToMove,
                                                   @NotNull PsiClass targetClass,
                                                   @NotNull MultiMap<PsiElement, String> conflicts,
                                                   @Nullable String newVisibility) {
    analyzeAccessibilityConflicts(membersToMove, targetClass, conflicts, newVisibility, targetClass, null);
  }

  public static void analyzeAccessibilityConflicts(@NotNull Set<PsiMember> membersToMove,
                                                   @Nullable PsiClass targetClass,
                                                   @NotNull MultiMap<PsiElement, String> conflicts,
                                                   @Nullable String newVisibility,
                                                   @NotNull PsiElement context,
                                                   @Nullable Set<PsiMethod> abstractMethods) {
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(newVisibility)) { //Still need to check for access object
      newVisibility = PsiModifier.PUBLIC;
    }

    for (PsiMember member : membersToMove) {
      checkUsedElements(member, member, membersToMove, abstractMethods, targetClass, context, conflicts);
      checkAccessibilityConflicts(member, newVisibility, targetClass, membersToMove, conflicts);
    }
  }

  public static void checkAccessibilityConflicts(@NotNull PsiMember member,
                                                 @PsiModifier.ModifierConstant @Nullable String newVisibility,
                                                 @Nullable PsiClass targetClass,
                                                 @NotNull Set<? extends PsiMember> membersToMove,
                                                 @NotNull MultiMap<PsiElement, String> conflicts) {
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
      catch (IncorrectOperationException ignore) { } // do nothing and hope for the best
    }

    checkAccessibilityConflicts(member, modifierListCopy, targetClass, membersToMove, conflicts);
  }

  public static void checkAccessibilityConflicts(@NotNull PsiMember member,
                                                 @Nullable PsiModifierList modifierListCopy,
                                                 @Nullable PsiClass targetClass,
                                                 @NotNull Set<? extends PsiMember> membersToMove,
                                                 @NotNull MultiMap<PsiElement, String> conflicts) {
    for (PsiReference psiReference : ReferencesSearch.search(member)) {
      checkAccessibilityConflicts(psiReference, member, modifierListCopy, targetClass, membersToMove, conflicts);
    }
  }

  public static void checkAccessibilityConflicts(@NotNull PsiReference reference,
                                                 @NotNull PsiMember member,
                                                 @Nullable PsiModifierList modifierListCopy,
                                                 @Nullable PsiClass targetClass,
                                                 @NotNull Set<? extends PsiMember> membersToMove,
                                                 @NotNull MultiMap<PsiElement, String> conflicts) {
    JavaPsiFacade manager = JavaPsiFacade.getInstance(member.getProject());
    PsiElement ref = reference.getElement();
    if (!RefactoringHierarchyUtil.willBeInTargetClass(ref, membersToMove, targetClass, false)) {
      // check for target class accessibility
      if (targetClass != null && !manager.getResolveHelper().isAccessible(targetClass, targetClass.getModifierList(), ref, null, null)) {
        String message = RefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                   RefactoringUIUtil.getDescription(targetClass, true),
                                                   VisibilityUtil.getVisibilityStringToDisplay(targetClass),
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true));
        message = CommonRefactoringUtil.capitalize(message);
        conflicts.putValue(targetClass, message);
      }
      // check for member accessibility
      else if (!manager.getResolveHelper().isAccessible(member, modifierListCopy, ref, targetClass, null)) {
        String message = RefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                   RefactoringUIUtil.getDescription(member, true),
                                                   VisibilityUtil.toPresentableText(VisibilityUtil.getVisibilityModifier(modifierListCopy)),
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true));
        message = CommonRefactoringUtil.capitalize(message);
        conflicts.putValue(member, message);
      }
    }
  }

  public static void checkUsedElements(PsiMember member,
                                     PsiElement scope,
                                     @NotNull Set<PsiMember> membersToMove,
                                     @Nullable Set<PsiMethod> abstractMethods,
                                     @Nullable PsiClass targetClass,
                                     @NotNull PsiElement context,
                                     MultiMap<PsiElement, String> conflicts) {
    checkUsedElements(member, scope, membersToMove, abstractMethods, targetClass, null, context, conflicts);
  }

  public static void checkUsedElements(PsiMember member,
                                       PsiElement scope,
                                       @NotNull Set<PsiMember> membersToMove,
                                       @Nullable Set<PsiMethod> abstractMethods,
                                       @Nullable PsiClass targetClass,
                                       PsiClass accessClass,
                                       @NotNull PsiElement context,
                                       MultiMap<PsiElement, String> conflicts) {
    final Set<PsiMember> moving = new HashSet<>(membersToMove);
    if (abstractMethods != null) {
      moving.addAll(abstractMethods);
    }
    if (scope instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        PsiExpression qualifier = refExpr.getQualifierExpression();
        PsiClass qualifierAccessClass = (PsiClass)(qualifier != null && !(qualifier instanceof PsiSuperExpression) ? PsiUtil.getAccessObjectClass(qualifier).getElement() : accessClass);
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false) &&
            (qualifierAccessClass == null || !RefactoringHierarchyUtil.willBeInTargetClass(qualifierAccessClass, moving, targetClass, false))) {
          checkAccessibility((PsiMember)refElement, context, qualifierAccessClass, member, conflicts);
        }
      }
    }
    else if (scope instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)scope;
      final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass != null) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(anonymousClass, moving, targetClass, false)) {
          checkAccessibility(anonymousClass, context, anonymousClass, member, conflicts);
        }
      }
      else {
        final PsiMethod refElement = newExpression.resolveConstructor();
        if (refElement != null) {
          if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
            checkAccessibility(refElement, context, accessClass, member, conflicts);
          }
        }
      }
    }
    else if (scope instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
          checkAccessibility((PsiMember)refElement, context, accessClass, member, conflicts);
        }
      }
    }

    for (PsiElement child : scope.getChildren()) {
      if (child instanceof PsiWhiteSpace || child instanceof PsiComment) continue;
      checkUsedElements(member, child, membersToMove, abstractMethods, targetClass, child instanceof PsiClass ? (PsiClass)child : accessClass, context, conflicts);
    }
  }

  public static void checkAccessibility(PsiMember refMember,
                                        @NotNull PsiElement newContext,
                                        @Nullable PsiClass accessClass,
                                        PsiMember member,
                                        MultiMap<PsiElement, String> conflicts) {
    if (!PsiUtil.isAccessible(refMember, newContext, accessClass)) {
      String message = RefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                 RefactoringUIUtil.getDescription(refMember, true),
                                                 VisibilityUtil.getVisibilityStringToDisplay(refMember),
                                                 RefactoringUIUtil.getDescription(member, false));
      message = CommonRefactoringUtil.capitalize(message);
      conflicts.putValue(refMember, message);
    }
    else if (newContext instanceof PsiClass && refMember instanceof PsiField && refMember.getContainingClass() == member.getContainingClass()) {
      final PsiField fieldInSubClass = ((PsiClass)newContext).findFieldByName(refMember.getName(), false);
      if (fieldInSubClass != null && 
          !refMember.hasModifierProperty(PsiModifier.STATIC) && 
          fieldInSubClass != refMember &&
          !member.hasModifierProperty(PsiModifier.STATIC)) {
        conflicts.putValue(refMember, CommonRefactoringUtil.capitalize(RefactoringUIUtil.getDescription(fieldInSubClass, true) +
                                                                       " would hide " + RefactoringUIUtil.getDescription(refMember, true) +
                                                                       " which is used by moved " + RefactoringUIUtil.getDescription(member, false)));
      }
    }
  }

  public static void analyzeModuleConflicts(final Project project,
                                            final Collection<? extends PsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final PsiElement target,
                                            final MultiMap<PsiElement,String> conflicts) {
    if (scopes == null) return;
    final VirtualFile vFile = PsiUtilCore.getVirtualFile(target);
    if (vFile == null) return;

    analyzeModuleConflicts(project, scopes, usages, vFile, conflicts);
  }

  public static void analyzeModuleConflicts(final Project project,
                                            final Collection<? extends PsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final VirtualFile vFile,
                                            final MultiMap<PsiElement, String> conflicts) {
    if (scopes == null) return;
    for (final PsiElement scope : scopes) {
      if (scope instanceof PsiPackage) return;
    }

    final Module targetModule = ModuleUtilCore.findModuleForFile(vFile, project);
    if (targetModule == null) return;
    final GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(targetModule);
    final HashSet<PsiElement> reported = new HashSet<>();
    for (final PsiElement scope : scopes) {
      scope.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          final PsiElement resolved = reference.resolve();
          if (resolved != null &&
              !reported.contains(resolved) &&
              !CommonRefactoringUtil.isAncestor(resolved, scopes) &&
              !(resolved instanceof LightElement) &&
              !haveElementInScope(resolved)) {
            if (resolved instanceof PsiMethod) {
              for (PsiMethod superMethod : ((PsiMethod)resolved).findDeepestSuperMethods()) {
                if (haveElementInScope(superMethod)) return;
              }
            }
            final String scopeDescription = RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(reference), true);
            final String message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.in.module.2",
                                                             RefactoringUIUtil.getDescription(resolved, true),
                                                             scopeDescription,
                                                             CommonRefactoringUtil.htmlEmphasize(targetModule.getName()));
            conflicts.putValue(resolved, CommonRefactoringUtil.capitalize(message));
            reported.add(resolved);
          }
        }

        private boolean haveElementInScope(PsiElement resolved) {
          if (PsiSearchScopeUtil.isInScope(resolveScope, resolved)){
            return true;
          }
          if (!resolved.getManager().isInProject(resolved)) {
            if (resolved instanceof PsiMember) {
              final PsiClass containingClass = ((PsiMember)resolved).getContainingClass();
              if (containingClass != null) {
                final String fqn = containingClass.getQualifiedName();
                if (fqn != null) {
                  final PsiClass classFromTarget = JavaPsiFacade.getInstance(project).findClass(fqn, resolveScope);
                  if (classFromTarget != null) {
                    if (resolved instanceof PsiMethod) {
                      return classFromTarget.findMethodsBySignature((PsiMethod)resolved, true).length > 0;
                    }
                    if (resolved instanceof PsiField ) {
                      return classFromTarget.findFieldByName(((PsiField)resolved).getName(), false) != null;
                    }
                    if (resolved instanceof PsiClass) {
                      return classFromTarget.findInnerClassByName(((PsiClass)resolved).getName(), false) != null;
                    }
                  }
                }
              }
            }
            if (resolved instanceof PsiClass) {
              final String fqn = ((PsiClass)resolved).getQualifiedName();
              return fqn != null && JavaPsiFacade.getInstance(project).findClass(fqn, resolveScope) != null;
            }
          }
          return false;
        }
      });
    }

    boolean isInTestSources = ModuleRootManager.getInstance(targetModule).getFileIndex().isInTestSourceContent(vFile);
    NextUsage:
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null && PsiTreeUtil.getParentOfType(element, PsiImportStatement.class, false) == null) {

        for (PsiElement scope : scopes) {
          if (PsiTreeUtil.isAncestor(scope, element, false)) continue NextUsage;
        }

        final GlobalSearchScope resolveScope1 = element.getResolveScope();
        if (!resolveScope1.isSearchInModuleContent(targetModule, isInTestSources)) {
          final PsiFile usageFile = element.getContainingFile();
          PsiElement container;
          if (usageFile instanceof PsiJavaFile) {
            container = ConflictsUtil.getContainer(element);
          }
          else {
            container = usageFile;
          }
          final String scopeDescription = RefactoringUIUtil.getDescription(container, true);
          final VirtualFile usageVFile = usageFile.getVirtualFile();
          if (usageVFile != null) {
            Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(usageVFile);
            if (module != null) {
              final String message;
              final PsiElement referencedElement;
              if (usage instanceof MoveRenameUsageInfo) {
                referencedElement = ((MoveRenameUsageInfo)usage).getReferencedElement();
              }
              else {
                referencedElement = usage.getElement();
              }
              assert referencedElement != null : usage;
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
              conflicts.putValue(referencedElement, CommonRefactoringUtil.capitalize(message));
            }
          }
        }
      }
    }
  }
}
