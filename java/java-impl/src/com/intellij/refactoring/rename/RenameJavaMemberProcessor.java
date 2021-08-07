// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public abstract class RenameJavaMemberProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaMemberProcessor.class);

  public static void qualifyMember(PsiMember member, PsiElement occurence, String newName) throws IncorrectOperationException {
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass != null) {
      qualifyMember(occurence, newName, containingClass, member.hasModifierProperty(PsiModifier.STATIC));
    }
  }

  protected static void qualifyMember(final PsiElement occurence, final String newName, @NotNull final PsiClass containingClass, final boolean isStatic)
      throws IncorrectOperationException {
    PsiManager psiManager = occurence.getManager();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiManager.getProject());
    if (isStatic) {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(factory.createReferenceExpression(containingClass));
      occurence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified = createQualifiedMemberReference(occurence, newName, containingClass, false);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createMemberReference(PsiMember member, PsiElement context) throws IncorrectOperationException {
    final PsiManager manager = member.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    final String name = member.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    final PsiClass containingClass = member.getContainingClass();
    if (manager.areElementsEquivalent(resolved, member) || containingClass == null) return ref;
    return createQualifiedMemberReference(context, name, containingClass, member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static PsiReferenceExpression createQualifiedMemberReference(final PsiElement context, final String name,
                                                                         @NotNull final PsiClass containingClass, final boolean isStatic) throws IncorrectOperationException {
    PsiReferenceExpression ref;
    final PsiJavaCodeReferenceElement qualifier;

    final PsiManager manager = containingClass.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    if (isStatic) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiJavaCodeReferenceElement)ref.getQualifierExpression();
      final PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
      qualifier.replace(classReference);
    }
    else {
      PsiClass contextClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
      if (InheritanceUtil.isInheritorOrSelf(contextClass, containingClass, true)) {
        ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
        return ref;
      }

      while (contextClass != null && !InheritanceUtil.isInheritorOrSelf(contextClass, containingClass, true)) {
        contextClass = PsiTreeUtil.getParentOfType(contextClass, PsiClass.class, true);
      }

      ref = (PsiReferenceExpression) factory.createExpressionFromText("A.this." + name, null);
      qualifier = ((PsiThisExpression)ref.getQualifierExpression()).getQualifier();
      final PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(contextClass != null ? contextClass : containingClass);
      qualifier.replace(classReference);
    }
    return ref;
  }

  protected static void findMemberHidesOuterMemberCollisions(final PsiMember member, final String newName, final List<? super UsageInfo> result) {
    if (member instanceof PsiCompiledElement) return;
    final PsiClass memberClass = member.getContainingClass();
    for (PsiClass aClass = memberClass != null ? memberClass.getContainingClass() : null; aClass != null; aClass = aClass.getContainingClass()) {
      if (member instanceof PsiMethod) {
        for (PsiMethod conflict : aClass.findMethodsByName(newName, true)) {
          findMemberHidesOuterMemberCollisions(member, conflict, memberClass, result);
        }
      }
      else {
        final PsiMember conflict = aClass.findFieldByName(newName, false);
        if (conflict == null) continue;
        findMemberHidesOuterMemberCollisions(member, conflict, memberClass, result);
      }
    }
  }

  private static void findMemberHidesOuterMemberCollisions(PsiMember member,
                                                           PsiMember anotherMember,
                                                           PsiClass memberClass, 
                                                           List<? super UsageInfo> result) {
    ReferencesSearch.search(anotherMember).forEach(reference -> {
      PsiElement refElement = reference.getElement();
      if (refElement instanceof PsiReferenceExpression && ((PsiReferenceExpression)refElement).isQualified()) return true;
      if (PsiTreeUtil.isAncestor(memberClass, refElement, false)) {
        MemberHidesOuterMemberUsageInfo info = new MemberHidesOuterMemberUsageInfo(refElement, member);
        result.add(info);
      }
      return true;
    });
  }

  protected static void qualifyOuterMemberReferences(final List<? extends MemberHidesOuterMemberUsageInfo> outerHides) throws IncorrectOperationException {
    for (MemberHidesOuterMemberUsageInfo usage : outerHides) {
      final PsiElement element = usage.getElement();
      PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
      PsiMember member = (PsiMember)usage.getReferencedElement();
      PsiReferenceExpression ref = createMemberReference(member, collidingRef);
      collidingRef.replace(ref);
    }
  }

  protected static void findCollisionsAgainstNewName(final PsiMember memberToRename, final String newName, final List<UsageInfo> result) {
    if (!memberToRename.isPhysical()) {
      return;
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(memberToRename.getProject());
    final List<PsiReference> potentialConflicts = new ArrayList<>();
    for (UsageInfo info : result) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiReferenceExpression) {
        if (((PsiReferenceExpression)element).advancedResolve(false).getCurrentFileResolveScope() instanceof PsiImportStaticStatement &&
            referencesLocalMember(memberToRename, newName, elementFactory, element)) {
          potentialConflicts.add(info.getReference());
        }
      }
    }

    final PsiFile containingFile = memberToRename.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)containingFile).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement staticImport : importList.getImportStaticStatements()) {
          final String referenceName = staticImport.getReferenceName();
          if (referenceName != null && !referenceName.equals(newName)) {
            continue;
          }
          final PsiClass targetClass = staticImport.resolveTargetClass();
          if (targetClass != null) {
            final Set<PsiMember> importedMembers = new HashSet<>();
            if (memberToRename instanceof PsiMethod) {
              for (PsiMethod method : targetClass.findMethodsByName(newName, true)) {
                if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                  importedMembers.add(method);
                }
              }
            }
            else if (memberToRename instanceof PsiField) {
              final PsiField fieldByName = targetClass.findFieldByName(newName, true);
              if (fieldByName != null) {
                importedMembers.add(fieldByName);
              }
            }

            for (PsiMember member : importedMembers) {
              ReferencesSearch.search(member, new LocalSearchScope(containingFile), true).forEach(psiReference -> {
                potentialConflicts.add(psiReference);
                return true;
              });
            }
          }
        }
      }
    }

    for (PsiReference potentialConflict : potentialConflicts) {
      if (potentialConflict instanceof PsiJavaReference) {
        final JavaResolveResult resolveResult = ((PsiJavaReference)potentialConflict).advancedResolve(false);
        final PsiElement conflictElement = resolveResult.getElement();
        if (conflictElement != null) {
          final PsiElement scope = resolveResult.getCurrentFileResolveScope();
          if (scope instanceof PsiImportStaticStatement) {
            result.add(new MemberHidesStaticImportUsageInfo(potentialConflict.getElement(), conflictElement, memberToRename));
          }
        }
      }
    }
  }

  private static boolean referencesLocalMember(PsiMember memberToRename,
                                               String newName,
                                               PsiElementFactory elementFactory,
                                               PsiElement context) {
    if (memberToRename instanceof PsiField) {
      return ((PsiReferenceExpression)elementFactory.createExpressionFromText(newName, context)).resolve() != null;
    }

    if (memberToRename instanceof PsiMethod) {
      final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)elementFactory.createExpressionFromText(newName + "()", context);
      return callExpression.getMethodExpression().multiResolve(false).length > 0;
    }
    return false;
  }

  protected static void qualifyStaticImportReferences(final List<? extends MemberHidesStaticImportUsageInfo> staticImportHides)
      throws IncorrectOperationException {
    for (MemberHidesStaticImportUsageInfo info : staticImportHides) {
      final PsiReference ref = info.getReference();
      if (ref == null) return;
      final PsiElement occurrence = ref.getElement();
      final PsiElement target = info.getReferencedElement();
      if (target instanceof PsiMember) {
        final PsiMember targetMember = (PsiMember)target;
        PsiClass containingClass = targetMember.getContainingClass();
        qualifyMember(occurrence, targetMember.getName(), containingClass, true);
      }
    }
  }
}
