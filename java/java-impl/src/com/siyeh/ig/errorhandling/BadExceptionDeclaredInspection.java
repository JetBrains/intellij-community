/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public final class BadExceptionDeclaredInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public String exceptionsString = "";

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet exceptions =
    new ExternalizableStringSet(
      CommonClassNames.JAVA_LANG_THROWABLE,
      "java.lang.Exception",
      "java.lang.Error",
      "java.lang.RuntimeException",
      "java.lang.NullPointerException",
      "java.lang.ClassCastException",
      "java.lang.ArrayIndexOutOfBoundsException"
    );

  @SuppressWarnings("PublicField")
  public boolean ignoreTestCases = false; // keep for compatibility
  @SuppressWarnings("PublicField")
  public boolean ignoreLibraryOverrides = false;

  public BadExceptionDeclaredInspection() {
    if (!exceptionsString.isEmpty()) {
      exceptions.clear();
      final List<String> strings = StringUtil.split(exceptionsString, ",");
      exceptions.addAll(strings);
      exceptionsString = "";
    }
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("exceptions", InspectionGadgetsBundle.message("choose.exception.label"),
                 new JavaClassValidator().withSuperClass(CommonClassNames.JAVA_LANG_THROWABLE)
                  .withTitle(InspectionGadgetsBundle.message("choose.exception.class"))),
      checkbox("ignoreLibraryOverrides", InspectionGadgetsBundle.message("ignore.exceptions.declared.on.library.override.option")));
  }

  @Override
  public @NotNull String getID() {
    return "ProhibitedExceptionDeclared";
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("bad.exception.declared.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionDeclaredVisitor();
  }

  private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (ignoreLibraryOverrides && LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] references = throwsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement reference : references) {
        final PsiElement element = reference.resolve();
        if (!(element instanceof PsiClass thrownClass)) {
          continue;
        }
        final String qualifiedName = thrownClass.getQualifiedName();
        if (qualifiedName != null && exceptions.contains(qualifiedName)) {
          registerError(reference, reference);
        }
      }
    }
  }
}
