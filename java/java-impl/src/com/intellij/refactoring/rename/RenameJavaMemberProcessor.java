/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public abstract class RenameJavaMemberProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaMemberProcessor");

  public static void qualifyMember(PsiMember member, PsiElement occurence, String newName) throws IncorrectOperationException {
    qualifyMember(occurence, newName, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static void qualifyMember(final PsiElement occurence, final String newName, final PsiClass containingClass, final boolean isStatic)
      throws IncorrectOperationException {
    PsiManager psiManager = occurence.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    if (isStatic) {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(factory.createReferenceExpression(containingClass));
      occurence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified = createQualifiedMemberReference(occurence, newName, containingClass, isStatic);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createMemberReference(PsiMember member, PsiElement context) throws IncorrectOperationException {
    final PsiManager manager = member.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final String name = member.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    if (manager.areElementsEquivalent(resolved, member)) return ref;
    return createQualifiedMemberReference(context, name, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static PsiReferenceExpression createQualifiedMemberReference(final PsiElement context, final String name,
                                                                         final PsiClass containingClass, final boolean isStatic) throws IncorrectOperationException {
    PsiReferenceExpression ref;
    final PsiJavaCodeReferenceElement qualifier;

    final PsiManager manager = containingClass.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
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

  protected static void findMemberHidesOuterMemberCollisions(final PsiMember member, final String newName, final List<UsageInfo> result) {
    if (member instanceof PsiCompiledElement) return;
    final PsiMember patternMember;
    if (member instanceof PsiMethod) {
      PsiMethod patternMethod = (PsiMethod) member.copy();
      try {
        patternMethod.setName(newName);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }
      patternMember = patternMethod;
    }
    else {
      patternMember = member;
    }

    final PsiClass fieldClass = member.getContainingClass();
    for (PsiClass aClass = fieldClass != null ? fieldClass.getContainingClass() : null; aClass != null; aClass = aClass.getContainingClass()) {
      final PsiMember conflict;
      if (member instanceof PsiMethod) {
        conflict = aClass.findMethodBySignature((PsiMethod)patternMember, true);
      }
      else {
        conflict = aClass.findFieldByName(newName, false);
      }
      if (conflict == null) continue;
      ReferencesSearch.search(conflict).forEach(reference -> {
        PsiElement refElement = reference.getElement();
        if (refElement instanceof PsiReferenceExpression && ((PsiReferenceExpression)refElement).isQualified()) return true;
        if (PsiTreeUtil.isAncestor(fieldClass, refElement, false)) {
          MemberHidesOuterMemberUsageInfo info = new MemberHidesOuterMemberUsageInfo(refElement, member);
          result.add(info);
        }
        return true;
      });
    }
  }

  protected static void qualifyOuterMemberReferences(final List<MemberHidesOuterMemberUsageInfo> outerHides) throws IncorrectOperationException {
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

  protected static void qualifyStaticImportReferences(final List<MemberHidesStaticImportUsageInfo> staticImportHides)
      throws IncorrectOperationException {
    for (MemberHidesStaticImportUsageInfo info : staticImportHides) {
      final PsiReference ref = info.getReference();
      if (ref == null) return;
      final PsiElement occurrence = ref.getElement();
      final PsiElement target = info.getReferencedElement();
      if (target instanceof PsiMember && occurrence != null) {
        final PsiMember targetMember = (PsiMember)target;
        PsiClass containingClass = targetMember.getContainingClass();
        qualifyMember(occurrence, targetMember.getName(), containingClass, true);
      }
    }
  }
}
