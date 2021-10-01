// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.move.moveMembers;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    if (ref instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)ref;
      @Nullable PsiElement qualifier = refExpr.getQualifier();
      if (RefactoringHierarchyUtil.willBeInTargetClass(refExpr, membersToMove, targetClass, true)) {
        // both member and the reference to it will be in target class
        if (!RefactoringUtil.isInMovedElement(refExpr, membersToMove)) {
          if (qualifier != null) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // remove qualifier
          }
        }
        else {
          if (qualifier instanceof PsiReferenceExpression && member.getContainingClass() != null &&
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
                                    @Nullable PsiModifierList modifierListCopy,
                                    @NotNull PsiClass targetClass,
                                    @NotNull Set<PsiMember> membersToMove,
                                    @NotNull MoveMembersOptions moveMembersOptions,
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
        String newVisibility = moveMembersOptions.getExplicitMemberVisibility();
        String visibility = newVisibility != null ? newVisibility : VisibilityUtil.getVisibilityStringToDisplay(member);
        String message = RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                                   RefactoringUIUtil.getDescription(member, false),
                                                   visibility,
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
        conflicts.putValue(member, StringUtil.capitalize(message));
      }
    }

    if (member instanceof PsiField && targetClass.isInterface()) {
      ReadWriteAccessDetector accessDetector = ReadWriteAccessDetector.findDetector(member);
      if (accessDetector != null) {
        ReadWriteAccessDetector.Access access = accessDetector.getExpressionAccess(element);
        if (access != ReadWriteAccessDetector.Access.Read) {
          String message =
            JavaRefactoringBundle.message("move.member.write.access.in.interface.conflict", RefactoringUIUtil.getDescription(member, true));
          conflicts.putValue(element, StringUtil.capitalize(message));
        }
      }
    } else if (member instanceof PsiField &&
               usageInfo.reference instanceof PsiExpression &&
               member.hasModifierProperty(PsiModifier.FINAL) &&
               PsiUtil.isAccessedForWriting((PsiExpression)usageInfo.reference) &&
               !RefactoringHierarchyUtil.willBeInTargetClass(usageInfo.reference, membersToMove, targetClass, true)) {
      conflicts.putValue(usageInfo.member, JavaBundle.message("move.member.final.initializer.conflict"));
    }

    if (toBeConvertedToEnum(moveMembersOptions, member, targetClass) && !isEnumAcceptable(element, targetClass)) {
      conflicts.putValue(element, JavaBundle.message("move.member.enum.conflict"));
    }

    final PsiReference reference = usageInfo.getReference();
    if (reference != null) {
      RefactoringConflictsUtil.checkAccessibilityConflicts(reference, member, modifierListCopy, targetClass, membersToMove, conflicts);
    }
  }

  private static boolean isEnumAcceptable(PsiElement element, PsiClass targetClass) {
    if (element instanceof PsiExpression) {
      ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes((PsiExpression)element, false);
      if (types.length == 1) {
        PsiType type = types[0].getType();
        return type.isAssignableFrom(JavaPsiFacade.getElementFactory(element.getProject()).createType(targetClass));
      }
    }
    return false;
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
      conflicts.putValue(member, StringUtil.capitalize(message));
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
          PsiClass targetClass = JavaPsiFacade.getInstance(project).findClass(options.getTargetClassName(), element.getResolveScope());
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
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(refExpr.getProject());
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
    if (toBeConvertedToEnum(options, member, targetClass)) {
      memberCopy = EnumConstantsUtil.createEnumConstant(targetClass, member.getName(), ((PsiVariable)member).getInitializer());
    }
    else {
      memberCopy = (PsiMember)member.copy();
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && !targetClass.isInterface()) {
        // might need to make modifiers explicit, see IDEADEV-11416
        final PsiModifierList list = memberCopy.getModifierList();
        assert list != null;

        if (!(member instanceof PsiClass && (((PsiClass)member).isEnum() || ((PsiClass)member).isInterface()))) {
          list.setModifierProperty(PsiModifier.STATIC, member.hasModifierProperty(PsiModifier.STATIC));
        }
        if (!(member instanceof PsiClass && ((PsiClass)member).isEnum())) { 
          list.setModifierProperty(PsiModifier.FINAL, member.hasModifierProperty(PsiModifier.FINAL));
        }

        VisibilityUtil.setVisibility(list, VisibilityUtil.getVisibilityModifier(member.getModifierList()));
      }
    }
    member.delete();
    return anchor != null ? (PsiMember)targetClass.addAfter(memberCopy, anchor) : (PsiMember)targetClass.add(memberCopy);
  }

  private static boolean toBeConvertedToEnum(@NotNull MoveMembersOptions options,
                                             @NotNull PsiMember member,
                                             @NotNull PsiClass targetClass) {
    return options.makeEnumConstant() &&
           member instanceof PsiVariable &&
           EnumConstantsUtil.isSuitableForEnumConstant(((PsiVariable)member).getType(), targetClass);
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
        afterFields.sort((o1, o2) -> -PsiUtilCore.compareElementsByPosition(o1, o2));
        return afterFields.get(0);
      }

      final List<PsiField> beforeFields = new ArrayList<>();
      for (PsiReference psiReference : ReferencesSearch.search(member, new LocalSearchScope(targetClass))) {
        final PsiField fieldWithReference = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiField.class);
        if (fieldWithReference != null && !afterFields.contains(fieldWithReference) && fieldWithReference.getContainingClass() == targetClass) {
          beforeFields.add(fieldWithReference);
        }
      }
      beforeFields.sort(PsiUtil.BY_POSITION);
      if (!beforeFields.isEmpty()) {
        return beforeFields.get(0).getPrevSibling();
      }
    }
    return null;
  }
}
