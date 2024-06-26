// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.makeStatic;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

public final class MakeStaticUtil {
  public static InternalUsageInfo[] findClassRefsInMember(PsiTypeParameterListOwner member, boolean includeSelf) {
    PsiClass containingClass = member.getContainingClass();
    ArrayList<InternalUsageInfo> classRefs = new ArrayList<>();
    PsiTreeUtil.processElements(member, e -> {
      addClassRefs(member, classRefs, containingClass, e, includeSelf);
      return true;
    });
    return classRefs.toArray(new InternalUsageInfo[0]);
  }

  public static boolean isParameterNeeded(PsiTypeParameterListOwner member) {
    return findClassRefsInMember(member, false).length > 0;
  }

  private static void addClassRefs(PsiTypeParameterListOwner originalMember, ArrayList<? super InternalUsageInfo> classRefs,
                                   PsiClass containingClass, PsiElement element, boolean includeSelf) {
    if (element instanceof PsiReferenceExpression ref) {
      // resolving only "naked" fields and methods
      if (!ref.isQualified() && ref.resolve() instanceof PsiMember member && !member.hasModifierProperty(PsiModifier.STATIC)) {
        if (originalMember.getManager().areElementsEquivalent(member, originalMember)) {
          if (includeSelf) {
            classRefs.add(new SelfUsageInfo(element, originalMember));
          }
        }
        else {
          final PsiClass memberContainingClass = findContainingClass(ref, member);
          if (!(originalMember instanceof PsiClass) || !isPartOf(memberContainingClass, (PsiClass)originalMember)) {
            if (isPartOf(memberContainingClass, containingClass)) {
              classRefs.add(new InternalUsageInfo(element, member));
            }
          }
        }
      }
    }
    else if (element instanceof PsiThisExpression && !(element.getParent() instanceof PsiReceiverParameter)) {
      PsiJavaCodeReferenceElement qualifier = ((PsiThisExpression) element).getQualifier();
      PsiElement refElement = qualifier != null ?
          qualifier.resolve() : PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (refElement instanceof PsiClass && !refElement.equals(originalMember) && isPartOf((PsiClass)refElement, containingClass)) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).isReferenceTo(originalMember)) {
          if (includeSelf) {
            classRefs.add(new SelfUsageInfo(parent, originalMember));
          }
        } else {
          classRefs.add(new InternalUsageInfo(element, refElement));
        }
      }
    }
    else if (element instanceof PsiSuperExpression) {
      PsiJavaCodeReferenceElement qualifier = ((PsiSuperExpression) element).getQualifier();
      PsiElement refElement = qualifier != null ?
          qualifier.resolve() : PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (refElement instanceof PsiClass) {
        if (isPartOf((PsiClass) refElement, containingClass)) {
          if (!(originalMember instanceof PsiClass && isPartOf((PsiClass)refElement, (PsiClass)originalMember))) {
            classRefs.add(new InternalUsageInfo(element, refElement));
          }
        }
      }
    }
    else if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement classReference = ((PsiNewExpression) element).getClassReference();
      if (classReference != null) {
        PsiElement refElement = classReference.resolve();
        if (refElement instanceof PsiClass) {
          PsiClass hisClass = ((PsiClass) refElement).getContainingClass();
          if (hisClass != originalMember && isPartOf(hisClass, containingClass) && !((PsiClass)refElement).hasModifierProperty(PsiModifier.STATIC)) {
            classRefs.add(new InternalUsageInfo(element, refElement));
          }
        }
      }
    }
  }

  private static @Nullable PsiClass findContainingClass(@NotNull PsiReferenceExpression ref, @NotNull PsiMember member) {
    PsiClass memberClass = member.getContainingClass();
    PsiClass containingClass = PsiUtil.getContainingClass(ref);
    while (containingClass != null && !InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
      if (!member.hasModifierProperty(PsiModifier.STATIC) && containingClass.hasModifierProperty(PsiModifier.STATIC)) return null;
      containingClass = PsiUtil.getContainingClass(containingClass);
    }
    return containingClass;
  }

  private static boolean isPartOf(PsiClass elementClass, PsiClass containingClass) {
    while(elementClass != null) {
      if (InheritanceUtil.isInheritorOrSelf(containingClass, elementClass, true)) return true;
      if (elementClass.hasModifierProperty(PsiModifier.STATIC)) return false;
      elementClass = elementClass.getContainingClass();
    }

    return false;
  }

  public static boolean buildVariableData(PsiTypeParameterListOwner member, ArrayList<? super VariableData> result) {
    final InternalUsageInfo[] classRefsInMethod = findClassRefsInMember(member, false);
    return collectVariableData(member, classRefsInMethod, result);
  }

  public static boolean collectVariableData(PsiMember member, InternalUsageInfo[] internalUsages,
                                            ArrayList<? super VariableData> variableDatum) {
    HashSet<PsiField> reported = new HashSet<>();
    HashSet<PsiField> accessedForWriting = new HashSet<>();
    boolean needClassParameter = false;
    for (InternalUsageInfo usage : internalUsages) {
      final PsiElement referencedElement = usage.getReferencedElement();
      if (usage.isWriting()) {
        accessedForWriting.add((PsiField)referencedElement);
        needClassParameter = true;
      }
      else if (referencedElement instanceof PsiField field) {
        reported.add(field);
      }
      else {
        needClassParameter = true;
      }
    }

    final ArrayList<PsiField> psiFields = new ArrayList<>(reported);
    psiFields.sort(Comparator.comparing(PsiField::getName));
    for (final PsiField field : psiFields) {
      if (accessedForWriting.contains(field)) continue;
      VariableData data = new VariableData(field);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(member.getProject());
      String name = field.getName();
      name = codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD);
      name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      name = CommonJavaRefactoringUtil.suggestUniqueVariableName(name, member, field);
      data.name = name;
      data.passAsParameter = true;
      variableDatum.add(data);
    }
    return needClassParameter;
  }
}
