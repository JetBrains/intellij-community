/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
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
      return newDateTimeApiPresent != Boolean.FALSE;
    }

  }
}
