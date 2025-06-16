// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * A utility that can replace a direct field access with an accessor method call.
 *
 * @param accessorName accessor method name
 * @param kind     accessor kind (exact, overridable, name-based)
 * @param setter   if true, the accessor is a setter
 */
public record FieldAccessFixer(@NotNull String accessorName, @NotNull AccessorKind kind, boolean setter) {
  /**
   * A kind of accessor method, namely how good the method replaces a direct field access.
   */
  public enum AccessorKind {
    /**
     * Accessor accesses the field and cannot be overridden (static, final, or in final class):
     * replacing a field reference with an accessor is safe.
     */
    EXACT,
    /**
     * Accessor accesses the field but can be overridden. Replacing a field reference with an accessor
     * may be not completely safe if it's overridden in a subclass.
     */
    OVERRIDABLE,
    /**
     * Accessor method is selected based on its name (e.g., {@code getSomething} for a field named {@code something}).
     * No guarantee that it does the same.
     */
    NAME_BASED
  }
  
  public void apply(@NotNull PsiReferenceExpression ref) {
    String qualifier = null;
    final PsiExpression qualifierExpression = ref.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifier = qualifierExpression.getText();
    }
    Project project = ref.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression callExpression;
    final String call = (qualifier != null ? qualifier + "." : "") + accessorName;
    if (!setter) {
      callExpression = (PsiMethodCallExpression)elementFactory.createExpressionFromText(call + "()", null);
      callExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(project).reformat(callExpression);
      ref.replace(callExpression);
    } else {
      PsiElement parent = PsiTreeUtil.skipParentsOfType(ref, PsiParenthesizedExpression.class);
      if (parent instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiExpression rExpression = assignmentExpression.getRExpression();
        final String argList = rExpression != null ? rExpression.getText() : "";
        callExpression = (PsiMethodCallExpression)elementFactory.createExpressionFromText(call + "(" +   argList + ")", null);
        callExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(project).reformat(callExpression);
        parent.replace(callExpression);
      }
    }
  }

  /**
   * @param ref reference to a potentially inaccessible field
   * @param target the symbol the ref resolves to at a current place
   * @param place place where reference occurs. Can be the ref itself, or probably a place where the ref is about to be inlined 
   * @return the fixer which will replace the direct field access with an accessor method call; 
   * null if there's no suitable accessor method or the field is already accessible.
   */
  public static @Nullable FieldAccessFixer create(@NotNull PsiReferenceExpression ref, @Nullable PsiElement target, 
                                                  @NotNull PsiElement place) {
    if (!(target instanceof PsiField field)) return null;
    PsiElement qualifier = ref.getQualifier();
    PsiClass accessObjectClass =
      qualifier instanceof PsiExpression expression ? (PsiClass)PsiUtil.getAccessObjectClass(expression).getElement() : null;
    if (PsiUtil.isAccessible(field, place, accessObjectClass)) return null;
    Predicate<PsiMethod> accessTest = m -> PsiUtil.isAccessible(m, place, accessObjectClass);
    if (PsiTypes.nullType().equals(field.getType())) return null;
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return null;
    PsiElement element = PsiTreeUtil.skipParentsOfType(ref, PsiParenthesizedExpression.class);
    boolean setter;
    PsiMethod accessor;
    AccessorKind kind;
    PsiMethod prototype = null;
    if (element instanceof PsiAssignmentExpression assignment && assignment.getOperationTokenType() == JavaTokenType.EQ &&
        PsiTreeUtil.isAncestor(assignment.getLExpression(), ref, false)) {
      setter = true;
      accessor = ContainerUtil.find(containingClass.getMethods(),
                                    method -> PropertyUtil.getFieldOfSetter(method) == field && accessTest.test(method));
      if (accessor == null) {
        prototype = PropertyUtilBase.generateSetterPrototype(field);
      }
    }
    else if (PsiUtil.isAccessedForReading(ref)) {
      setter = false;
      accessor = Arrays.stream(containingClass.getMethods())
        .filter(method -> PropertyUtil.getFieldOfGetter(method) == field && accessTest.test(method))
        .max(Comparator.comparing(m -> JavaPsiRecordUtil.getRecordComponentForAccessor(m) != null))
        .orElse(null);
      if (accessor == null) {
        prototype = PropertyUtilBase.generateGetterPrototype(field);
      }
    }
    else {
      // Increment/decrement, compound update is not supported
      return null;
    }
    if (prototype != null) {
      accessor = containingClass.findMethodBySignature(prototype, true);
      if (accessor == null || !accessTest.test(accessor)) return null;
      kind = AccessorKind.NAME_BASED;
    } else {
      kind = PsiUtil.canBeOverridden(accessor) ? AccessorKind.OVERRIDABLE : AccessorKind.EXACT;
    }
    return new FieldAccessFixer(accessor.getName(), kind, setter);
  }
}
