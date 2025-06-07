/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.NormalizeDeclarationFix;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class CStyleArrayDeclarationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  private static final TokenSet BRACKET_TOKENS = TokenSet.create(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);
  public boolean ignoreVariables = false;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiMethod method) {
      return InspectionGadgetsBundle.message("cstyle.array.method.declaration.problem.descriptor", method.getName());
    }
    final int choice;
    if (info instanceof PsiField) choice = 1;
    else if (info instanceof PsiParameter) choice = 2;
    else if (info instanceof PsiRecordComponent) choice = 3;
    else choice = 4;
    return InspectionGadgetsBundle.message("cstyle.array.variable.declaration.problem.descriptor",
                                           Integer.valueOf(choice), ((PsiVariable)info).getName());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreVariables", JavaAnalysisBundle.message("inspection.c.style.array.declarations.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new NormalizeDeclarationFix(true);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CStyleArrayDeclarationVisitor();
  }

  private class CStyleArrayDeclarationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      if (ignoreVariables || variable instanceof PsiRecordComponent) {
        // C-style array declaration in records are not accepted -- see https://bugs.openjdk.org/browse/JDK-8250629
        return;
      }
      if (variable.isUnnamed()) {
        // C-style array declaration in unnamed variables are not accepted
        return;
      }
      if (variable instanceof PsiParameter parameter && parameter.isVarArgs()) {
        // not compilable, fix is handled by error highlighting
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null || typeElement.isInferredType()) {
        return; // true for enum constants or lambda parameters
      }
      final PsiType declaredType = variable.getType();
      if (declaredType.getArrayDimensions() == 0) {
        return;
      }
      final PsiType elementType = typeElement.getType();
      if (elementType.equals(declaredType)) {
        return;
      }
      if (isVisibleHighlight(variable)) {
        highlightBrackets(variable, variable.getNameIdentifier());
      }
      else {
        registerError(variable, variable);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiType returnType = method.getReturnType();
      if (returnType == null || returnType.getArrayDimensions() == 0) {
        return;
      }
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (type.equals(returnType)) {
        return;
      }
      if (InspectionProjectProfileManager.isInformationLevel(getShortName(), method)) {
        registerError(typeElement, method);
        registerMethodError(method, method);
      }
      highlightBrackets(method, method.getParameterList());
    }

    private void highlightBrackets(@NotNull PsiElement problemElement, PsiElement anchor) {
      PsiElement start = null;
      PsiElement end = null;
      while (anchor != null) {
        if (anchor instanceof PsiAnnotation) {
          if (start == null) start = anchor;
        }
        else if (PsiUtil.isJavaToken(anchor, BRACKET_TOKENS)) {
          if (start == null) start = anchor;
          end = anchor;
        }
        anchor = anchor.getNextSibling();
      }
      if (start != null && end != null) registerErrorAtRange(start, end, problemElement);
    }
  }
}