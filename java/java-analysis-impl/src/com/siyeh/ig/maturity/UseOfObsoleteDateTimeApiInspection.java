// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class UseOfObsoleteDateTimeApiInspection extends BaseInspection {

  static final Set<String> dateTimeNames = Set.of("java.util.Date", "java.util.Calendar", "java.util.GregorianCalendar", "java.util.TimeZone", "java.util.SimpleTimeZone");

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("use.of.obsolete.date.time.api.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObsoleteDateTimeApiVisitor();
  }

  private static class ObsoleteDateTimeApiVisitor extends BaseInspectionVisitor {

    private Boolean newDateTimeApiPresent = null;

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement referenceElement) {
      if (!isNewDateTimeApiPresent(referenceElement)) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(referenceElement, PsiImportStatementBase.class) != null) {
        return;
      }
      super.visitReferenceElement(referenceElement);
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass targetClass)) return;

      String qualifiedName = targetClass.getQualifiedName();
      if (qualifiedName == null || !dateTimeNames.contains(qualifiedName)) {
        return;
      }

      PsiTypeElement typeElement = PsiTreeUtil.getTopmostParentOfType(referenceElement, PsiTypeElement.class);
      if (typeElement != null) {
        final PsiElement parent = typeElement.getParent();
        if (parent instanceof PsiMethod method) {
          if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
            return;
          }
        }
        else if (parent instanceof PsiParameter parameter) {
          if (LibraryUtil.isOverrideOfLibraryMethodParameter(parameter)) {
            return;
          }
        }
      }
      registerError(referenceElement);
    }

    private boolean isNewDateTimeApiPresent(PsiElement context) {
      if (newDateTimeApiPresent == null) {
        newDateTimeApiPresent = ClassUtils.findClass("java.time.Instant", context) != null;
      }
      return newDateTimeApiPresent;
    }
  }
}
