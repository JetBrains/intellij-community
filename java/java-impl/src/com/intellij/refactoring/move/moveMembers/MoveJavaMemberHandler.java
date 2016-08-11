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
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class MoveJavaMemberHandler implements MoveMemberHandler {
  @Override
  @Nullable
  public MoveMembersProcessor.MoveMembersUsageInfo getUsage(@NotNull PsiMember member,
                                                            @NotNull PsiReference psiReference,
                                                            @NotNull Set<PsiMember> membersToMove,
                                                            @NotNull PsiClass targetClass) {
    PsiElement ref = psiReference.getElement();
    if (ref instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (RefactoringHierarchyUtil.willBeInTargetClass(refExpr, membersToMove, targetClass, true)) {
        // both member and the reference to it will be in target class
        if (!RefactoringUtil.isInMovedElement(refExpr, membersToMove)) {
          if (qualifier != null) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // remove qualifier
          }
        }
        else {
          if (qualifier instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression)qualifier).isReferenceTo(member.getContainingClass())) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, qualifier, psiReference);  // change qualifier
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

  @Override
  public void checkConflictsOnUsage(@NotNull MoveMembersProcessor.MoveMembersUsageInfo usageInfo,
                                    @Nullable String newVisibility,
                                    @Nullable PsiModifierList modifierListCopy,
                                    @NotNull PsiClass targetClass,
                                    @NotNull Set<PsiMember> membersToMove,
                                    @NotNull MultiMap<PsiElement, String> conflicts) {
    final PsiElement element = usageInfo.getElement();
    if (element == null) return;

    final PsiMember member = usageInfo.member;
    if (element instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
      PsiClass accessObjectClass = null;
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }

      if (!JavaResolveUtil.isAccessible(member, targetClass, modifierListCopy, element, accessObjectClass, null)) {
        String visibility = newVisibility != null ? newVisibility : VisibilityUtil.getVisibilityStringToDisplay(member);
        String message = RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                                   RefactoringUIUtil.getDescription(member, false),
                                                   visibility,
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
        conflicts.putValue(member, CommonRefactoringUtil.capitalize(message));
      }
    }

    if (member instanceof PsiField && targetClass.isInterface()) {
      ReadWriteAccessDetector accessDetector = ReadWriteAccessDetector.findDetector(member);
      if (accessDetector != null) {
        ReadWriteAccessDetector.Access access = accessDetector.getExpressionAccess(element);
        if (access != ReadWriteAccessDetector.Access.Read) {
          String message = RefactoringUIUtil.getDescription(member, true) + " has write access but is moved to an interface";
          conflicts.putValue(element, CommonRefactoringUtil.capitalize(message));
        }
      }
    } else if (member instanceof PsiField &&
               usageInfo.reference instanceof PsiExpression &&
               member.hasModifierProperty(PsiModifier.FINAL) &&
               PsiUtil.isAccessedForWriting((PsiExpression)usageInfo.reference) &&
               !RefactoringHierarchyUtil.willBeInTargetClass(usageInfo.reference, membersToMove, targetClass, true)) {
      conflicts.putValue(usageInfo.member, "final variable initializer won't be available after move.");
    }

    final PsiReference reference = usageInfo.getReference();
    if (reference != null) {
      RefactoringConflictsUtil.checkAccessibilityConflicts(reference, member, modifierListCopy, targetClass, membersToMove, conflicts);
    }
  }

  @Override
  public void checkConflictsOnMember(@NotNull PsiMember member,
                                     @Nullable String newVisibility,
                                     @Nullable PsiModifierList modifierListCopy,
                                     @NotNull PsiClass targetClass,
                                     @NotNull Set<PsiMember> membersToMove,
                                     @NotNull MultiMap<PsiElement, String> conflicts) {
    if (member instanceof PsiMethod && hasMethod(targetClass, (PsiMethod)member) ||
        member instanceof PsiField && hasField(targetClass, (PsiField)member)) {
      String message = RefactoringBundle.message("0.already.exists.in.the.target.class", RefactoringUIUtil.getDescription(member, false));
      conflicts.putValue(member, CommonRefactoringUtil.capitalize(message));
    }

    RefactoringConflictsUtil.checkUsedElements(member, member, membersToMove, null, targetClass, targetClass, conflicts);
  }

  protected static boolean hasMethod(PsiClass targetClass, PsiMethod method) {
    PsiMethod[] targetClassMethods = targetClass.findMethodsByName(method.getName(), true);
    for (PsiMethod candidate : targetClassMethods) {
      if (candidate != method &&
          MethodSignatureUtil.areSignaturesEqual(method.getSignature(PsiSubstitutor.EMPTY),
                                                 candidate.getSignature(PsiSubstitutor.EMPTY))) {
        return true;
      }
    }
    return false;
  }

  protected static boolean hasField(PsiClass targetClass, PsiField field) {
    final PsiField fieldByName = targetClass.findFieldByName(field.getName(), true);
    return fieldByName != null && fieldByName != field;
  }

  @Override
  public boolean changeExternalUsage(@NotNull MoveMembersOptions options, @NotNull MoveMembersProcessor.MoveMembersUsageInfo usage) {
    final PsiElement element = usage.getElement();
    if (element == null || !element.isValid()) return true;

    if (usage.reference instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)usage.reference;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        if (usage.qualifierClass != null && PsiTreeUtil.getParentOfType(refExpr, PsiSwitchLabelStatement.class) == null) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        }
        else {
          final Project project = element.getProject();
          final PsiClass targetClass =
            JavaPsiFacade.getInstance(project).findClass(options.getTargetClassName(), GlobalSearchScope.projectScope(project));
          if (targetClass != null) {
            final PsiReferenceParameterList parameterList = refExpr.getParameterList();
            if ((targetClass.isEnum() || PsiTreeUtil.isAncestor(targetClass, element, true)) && parameterList != null && parameterList.getTypeArguments().length == 0 && !(refExpr instanceof PsiMethodReferenceExpression)) {
              refExpr.setQualifierExpression(null);
            }
            else {
              changeQualifier(refExpr, targetClass, usage.member);
            }
          }
        }
      }
      else { // no qualifier
        if (usage.qualifierClass != null && (!usage.qualifierClass.isEnum() || PsiTreeUtil.getParentOfType(refExpr, PsiSwitchLabelStatement.class) == null)) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        }
      }
      return true;
    }
    return false;
  }

  protected static void changeQualifier(PsiReferenceExpression refExpr, PsiClass aClass, PsiMember member) throws IncorrectOperationException {
    if (RefactoringUtil.hasOnDemandStaticImport(refExpr, aClass) && !(refExpr instanceof PsiMethodReferenceExpression)) {
      refExpr.setQualifierExpression(null);
    }
    else if (!ImportsUtil.hasStaticImportOn(refExpr, member, false) || refExpr.getQualifierExpression() != null){
      PsiElementFactory factory = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory();
      refExpr.setQualifierExpression(factory.createReferenceExpression(aClass));
    }
  }

  @Override
  @NotNull
  public PsiMember doMove(@NotNull MoveMembersOptions options, @NotNull PsiMember member, PsiElement anchor, @NotNull PsiClass targetClass) {
    if (member instanceof PsiVariable) {
      ((PsiVariable)member).normalizeDeclaration();
    }

    ChangeContextUtil.encodeContextInfo(member, true);

    final PsiMember memberCopy;
    if (options.makeEnumConstant() &&
        member instanceof PsiVariable &&
        EnumConstantsUtil.isSuitableForEnumConstant(((PsiVariable)member).getType(), targetClass)) {
      memberCopy = EnumConstantsUtil.createEnumConstant(targetClass, member.getName(), ((PsiVariable)member).getInitializer());
    }
    else {
      memberCopy = (PsiMember)member.copy();
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && !targetClass.isInterface()) {
        // might need to make modifiers explicit, see IDEADEV-11416
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

  @Override
  public void decodeContextInfo(@NotNull PsiElement scope) {
    ChangeContextUtil.decodeContextInfo(scope, null, null);
  }

  @Override
  @Nullable
  public PsiElement getAnchor(@NotNull final PsiMember member, @NotNull final PsiClass targetClass, final Set<PsiMember> membersToMove) {
    if (member instanceof PsiField && member.hasModifierProperty(PsiModifier.STATIC)) {
      final List<PsiField> afterFields = new ArrayList<>();
      final PsiExpression psiExpression = ((PsiField)member).getInitializer();
      if (psiExpression != null) {
        psiExpression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(final PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement psiElement = expression.resolve();
            if (psiElement instanceof PsiField) {
              final PsiField psiField = (PsiField)psiElement;
              if ((psiField.getContainingClass() == targetClass || membersToMove.contains(psiField))&& !afterFields.contains(psiField)) {
                afterFields.add(psiField);
              }
            }
          }
        });
      }

      if (!afterFields.isEmpty()) {
        Collections.sort(afterFields, (o1, o2) -> -PsiUtilCore.compareElementsByPosition(o1, o2));
        return afterFields.get(0);
      }

      final List<PsiField> beforeFields = new ArrayList<>();
      for (PsiReference psiReference : ReferencesSearch.search(member, new LocalSearchScope(targetClass))) {
        final PsiField fieldWithReference = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiField.class);
        if (fieldWithReference != null && !afterFields.contains(fieldWithReference) && fieldWithReference.getContainingClass() == targetClass) {
          beforeFields.add(fieldWithReference);
        }
      }
      Collections.sort(beforeFields, PsiUtil.BY_POSITION);
      if (!beforeFields.isEmpty()) {
        return beforeFields.get(0).getPrevSibling();
      }
    }
    return null;
  }
}
