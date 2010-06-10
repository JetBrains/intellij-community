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
package com.intellij.refactoring.move.moveMembers;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */

public class MoveJavaMemberHandler implements MoveMemberHandler {
  public MoveMembersProcessor.MoveMembersUsageInfo getUsage(PsiMember member,
                                                            PsiReference psiReference,
                                                            Set<PsiMember> membersToMove,
                                                            PsiClass targetClass) {
    PsiElement ref = psiReference.getElement();
    if (ref instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (RefactoringHierarchyUtil.willBeInTargetClass(refExpr, membersToMove, targetClass, true)) {
        // both member and the reference to it will be in target class
        if (!isInMovedElement(refExpr, membersToMove)) {
          if (qualifier != null) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // remove qualifier
          }
        }
        else {
          if (qualifier instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression)qualifier).isReferenceTo(member.getContainingClass())) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // change qualifier
          }
        }
      }
      else {
        // member in target class, the reference will be outside target class
        if (qualifier == null) {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, refExpr, psiReference); // add qualifier
        }
        else {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, qualifier, psiReference); // change qualifier
        }
      }
    }
    return null;
  }

  private static boolean isInMovedElement(PsiElement element, Set<PsiMember> membersToMove) {
    for (PsiMember member : membersToMove) {
      if (PsiTreeUtil.isAncestor(member, element, false)) return true;
    }
    return false;
  }

  public boolean changeExternalUsage(MoveMembersOptions options, MoveMembersProcessor.MoveMembersUsageInfo usage) {
    if (!usage.getElement().isValid()) return true;

    if (usage.reference instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)usage.reference;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        if (usage.qualifierClass != null && PsiTreeUtil.getParentOfType(refExpr, PsiSwitchLabelStatement.class) == null) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        }
        else {
          refExpr.setQualifierExpression(null);
        }
      }
      else { // no qualifier
        if (usage.qualifierClass != null && PsiTreeUtil.getParentOfType(refExpr, PsiSwitchLabelStatement.class) == null) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        }
      }
      return true;
    }
    return false;
  }

  public PsiMember doMove(MoveMembersOptions options, PsiMember member, PsiElement anchor, PsiClass targetClass) {
    if (member instanceof PsiVariable) {
      ((PsiVariable)member).normalizeDeclaration();
    }

    ChangeContextUtil.encodeContextInfo(member, true);
    if (targetClass == null) return null;


    final PsiMember memberCopy;
    if (options.makeEnumConstant() &&
        member instanceof PsiVariable &&
        EnumConstantsUtil.isSuitableForEnumConstant(((PsiVariable)member).getType(), targetClass)) {
      memberCopy = EnumConstantsUtil.createEnumConstant(targetClass, member.getName(), ((PsiVariable)member).getInitializer());
    }
    else {
      memberCopy = (PsiMember)member.copy();
      if (member.getContainingClass().isInterface() && !targetClass.isInterface()) {
        //might need to make modifiers explicit, see IDEADEV-11416
        final PsiModifierList list = memberCopy.getModifierList();
        assert list != null;
        list.setModifierProperty(PsiModifier.STATIC, member.hasModifierProperty(PsiModifier.STATIC));
        list.setModifierProperty(PsiModifier.FINAL, member.hasModifierProperty(PsiModifier.FINAL));
        VisibilityUtil.setVisibility(list, VisibilityUtil.getVisibilityModifier(member.getModifierList()));
      }
    }
    member.delete();
    return anchor != null ? (PsiMember)targetClass.addAfter(memberCopy, anchor) : (PsiMember)targetClass.add(memberCopy);
  }

  public void decodeContextInfo(PsiElement scope) {
    ChangeContextUtil.decodeContextInfo(scope, null, null);
  }

  private static void changeQualifier(PsiReferenceExpression refExpr, PsiClass aClass, PsiMember member) throws IncorrectOperationException {
    if (RefactoringUtil.hasOnDemandStaticImport(refExpr, aClass)) {
      refExpr.setQualifierExpression(null);
    }
    else if (!RefactoringUtil.hasStaticImportOn(refExpr, member)){
      PsiElementFactory factory = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory();
      refExpr.setQualifierExpression(factory.createReferenceExpression(aClass));
    }
  }

  @Nullable
  public PsiElement getAnchor(final PsiMember member, final PsiClass targetClass) {
    if (member instanceof PsiField && member.hasModifierProperty(PsiModifier.STATIC)) {
      final List<PsiField> afterFields = new ArrayList<PsiField>();
      final PsiExpression psiExpression = ((PsiField)member).getInitializer();
      if (psiExpression != null) {
        psiExpression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(final PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement psiElement = expression.resolve();
            if (psiElement instanceof PsiField) {
              final PsiField psiField = (PsiField)psiElement;
              if (psiField.getContainingClass() == targetClass && !afterFields.contains(psiField)) {
                afterFields.add(psiField);
              }
            }
          }
        });
      }

      final Comparator<PsiField> fieldComparator = new Comparator<PsiField>() {
        public int compare(final PsiField o1, final PsiField o2) {
          return -PsiUtilBase.compareElementsByPosition(o1, o2);
        }
      };

      if (!afterFields.isEmpty()) {
        Collections.sort(afterFields, fieldComparator);
        return afterFields.get(0);
      }

      final List<PsiField> beforeFields = new ArrayList<PsiField>();
      for (PsiReference psiReference : ReferencesSearch.search(member, new LocalSearchScope(targetClass))) {
        final PsiField fieldWithReference = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiField.class);
        if (fieldWithReference != null && !afterFields.contains(fieldWithReference)) {
          beforeFields.add(fieldWithReference);
        }
      }
      Collections.sort(beforeFields, fieldComparator);
      if (!beforeFields.isEmpty()) {
        return beforeFields.get(0).getPrevSibling();
      }
    }
    return null;
  }
}
