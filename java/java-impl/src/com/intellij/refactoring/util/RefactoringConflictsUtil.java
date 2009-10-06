/*
 * User: anna
 * Date: 05-Oct-2009
 */
package com.intellij.refactoring.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public class RefactoringConflictsUtil {
  public static void setVisibility(PsiModifierList modifierList, @Modifier String newVisibility) throws IncorrectOperationException {
    modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
    modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
    modifierList.setModifierProperty(newVisibility, true);
  }

  public static void analyzeAccessibilityConflicts(@NotNull Set<PsiMember> membersToMove,
                                                final PsiClass targetClass,
                                                final MultiMap<PsiElement, String> conflicts, String newVisibility) {
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(newVisibility)) { //Still need to check for access object
      newVisibility = PsiModifier.PUBLIC;
    }

    for (PsiMember member : membersToMove) {
      checkUsedElements(member, member, membersToMove, targetClass, conflicts);

      PsiModifierList modifierList = member.getModifierList();
      if (modifierList!=null) modifierList= (PsiModifierList)modifierList.copy();

      if (newVisibility != null) {
        try {
          if (modifierList!=null)    setVisibility(modifierList, newVisibility);
        }
        catch (IncorrectOperationException ex) {
          /* do nothing and hope for the best */
        }
      }
      JavaPsiFacade manager = JavaPsiFacade.getInstance(member.getProject());
      for (PsiReference psiReference : ReferencesSearch.search(member)) {
        PsiElement ref = psiReference.getElement();
        if (!RefactoringHierarchyUtil.willBeInTargetClass(ref, membersToMove, targetClass, false)) {
          //Check for target class accessibility
          if (!manager.getResolveHelper().isAccessible(targetClass, targetClass.getModifierList(), ref, null, null)) {
            String message = RefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                       RefactoringUIUtil.getDescription(targetClass, true),
                                                       VisibilityUtil.getVisibilityStringToDisplay(targetClass),
                                                       RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true));
            message = CommonRefactoringUtil.capitalize(message);
            conflicts.putValue(targetClass, message);
          }
          //check for member accessibility
          else if (!manager.getResolveHelper().isAccessible(member, modifierList, ref, null, null)) {
            String message = RefactoringBundle.message("0.is.1.and.will.not.be.accessible.from.2.in.the.target.class",
                                                       RefactoringUIUtil.getDescription(member, true),
                                                       VisibilityUtil.getVisibilityStringToDisplay(member),
                                                       RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true));
            message = CommonRefactoringUtil.capitalize(message);
            conflicts.putValue(member, message);
          }
        }
      }
    }
  }

  public static void checkUsedElements(PsiMember member, PsiElement scope, @NotNull Set<PsiMember> membersToMove, PsiClass newContext, MultiMap<PsiElement, String> conflicts) {
    if(scope instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, membersToMove, newContext, false)){
          PsiExpression qualifier = refExpr.getQualifierExpression();
          PsiClass accessClass = (PsiClass)(qualifier != null ? PsiUtil.getAccessObjectClass(qualifier).getElement() : null);
          checkAccessibility((PsiMember)refElement, newContext, accessClass, member, conflicts);
        }
      }
    }
    else if (scope instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)scope;
      final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass != null) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(anonymousClass, membersToMove, newContext, false)){
          checkAccessibility(anonymousClass, newContext, anonymousClass, member, conflicts);
        }
      } else {
        final PsiMethod refElement = newExpression.resolveConstructor();
        if (refElement != null) {
          if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, membersToMove, newContext, false)) {
            checkAccessibility(refElement, newContext, null, member, conflicts);
          }
        }
      }
    }
    else if (scope instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, membersToMove, newContext, false)){
          checkAccessibility((PsiMember)refElement, newContext, null, member, conflicts);
        }
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (!(child instanceof PsiWhiteSpace)) {
        checkUsedElements(member, child, membersToMove, newContext, conflicts);
      }
    }
  }

  public static void checkAccessibility(PsiMember refMember,
                                         PsiClass newContext,
                                         PsiClass accessClass,
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
  }

  public static void analyzeModuleConflicts(Project project,
                                            Collection<? extends PsiElement> scope,
                                            final UsageInfo[] usages,
                                            PsiElement target,
                                            final MultiMap<PsiElement,String> conflicts) {
    if (scope == null) return;
    final VirtualFile vFile = PsiUtilBase.getVirtualFile(target);
    if (vFile == null) return;
    analyzeModuleConflicts(project, scope, usages, vFile, conflicts);
  }

  public static void analyzeModuleConflicts(Project project,
                                            final Collection<? extends PsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final VirtualFile vFile,
                                            final MultiMap<PsiElement, String> conflicts) {
    if (scopes == null) return;

    for (final PsiElement scope : scopes) {
      if (scope instanceof PsiPackage || scope instanceof PsiDirectory) return;
    }

    final Module targetModule = ModuleUtil.findModuleForFile(vFile, project);
    if (targetModule == null) return;
    final GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(targetModule);
    final HashSet<PsiElement> reported = new HashSet<PsiElement>();
    for (final PsiElement scope : scopes) {
      scope.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          final PsiElement resolved = reference.resolve();
          if (resolved != null && !reported.contains(resolved) && !CommonRefactoringUtil.isAncestor(resolved, scopes) &&
              !PsiSearchScopeUtil.isInScope(resolveScope, resolved)) {
            final String scopeDescription =
              RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(reference), true);
            final String message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.in.module.2",
                                                             CommonRefactoringUtil.capitalize(
                                                               RefactoringUIUtil.getDescription(resolved, true)), scopeDescription,
                                                                                                               CommonRefactoringUtil.htmlEmphasize(
                                                                                                                 targetModule.getName()));
            conflicts.putValue(resolved, message);
            reported.add(resolved);
          }
        }
      });
    }

    boolean isInTestSources = ModuleRootManager.getInstance(targetModule).getFileIndex().isInTestSourceContent(vFile);
    NextUsage:
    for (UsageInfo usage : usages) {
      if (usage instanceof MoveRenameUsageInfo) {
        final MoveRenameUsageInfo moveRenameUsageInfo = (MoveRenameUsageInfo)usage;
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
              if (container == null) container = usageFile;
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
                final PsiElement referencedElement = moveRenameUsageInfo.getReferencedElement();
                if (module == targetModule && isInTestSources) {
                  message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.production.of.module.2",
                                                      CommonRefactoringUtil.capitalize(
                                                        RefactoringUIUtil.getDescription(referencedElement, true)),
                                                      scopeDescription,
                                                      CommonRefactoringUtil.htmlEmphasize(module.getName()));
                }
                else {
                  message = RefactoringBundle.message("0.referenced.in.1.will.not.be.accessible.from.module.2",
                                                      CommonRefactoringUtil.capitalize(
                                                        RefactoringUIUtil.getDescription(referencedElement, true)),
                                                      scopeDescription,
                                                      CommonRefactoringUtil.htmlEmphasize(module.getName()));
                }
                conflicts.putValue(referencedElement, message);
              }
            }
          }
        }
      }
    }
  }
}