/*
 * Copyright 2006-2020 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplaceAllDotInspection extends BaseInspection {

  private static final String REGEX_META_CHARS = ".$|()[{^?*+\\";

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    final PsiExpression expression = (PsiExpression)infos[1];
    final String methodName = methodCallExpression.getMethodExpression().getReferenceName();
    if (isFileSeparator(expression)) {
      return InspectionGadgetsBundle.message("replace.all.file.separator.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("replace.all.dot.problem.descriptor", methodName);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "SuspiciousRegexArgument";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "ReplaceAllDot";
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[1];
    if (!(expression instanceof PsiLiteralExpression)) {
      return null;
    }
    return new EscapeCharacterFix();
  }

  private static class EscapeCharacterFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.all.dot.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiLiteralExpression expression)) {
        return;
      }
      final String text = expression.getText();
      final StringBuilder newExpression = new StringBuilder();
      int length = text.length();
      for (int i = 0; i < length; i++) {
        char c = text.charAt(i);
        if (StringUtil.containsChar(REGEX_META_CHARS, c)) {
          newExpression.append("\\\\");
        }
        newExpression.append(c);
      }
      PsiReplacementUtil.replaceExpression(expression, newExpression.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReplaceAllDotVisitor();
  }

  private static class ReplaceAllDotVisitor extends BaseInspectionVisitor {

    private static final CallMatcher.Simple MATCHER = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "replaceAll", "split");

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MATCHER.test(expression)) return;
      PsiExpression arg = ArrayUtil.getFirstElement(expression.getArgumentList().getExpressions());
      if (arg == null) return;
      ExpressionUtils.nonStructuralChildren(arg).
        forEach(argument -> {
          if (PsiUtil.isConstantExpression(argument) && ExpressionUtils.hasStringType(argument)) {
            final String value = (String)ExpressionUtils.computeConstantExpression(argument);
            if (isRegexMetaChar(value, !isOnTheFly())) {
              registerError(argument, expression, argument);
            }
          }
          if (isFileSeparator(argument)) {
            registerError(argument, expression, argument);
          }
        });
    }

    private static boolean isRegexMetaChar(String s, boolean includeErrors) {
      return s != null && s.length() == 1 && (includeErrors ? REGEX_META_CHARS : ".$|^").contains(s);
    }
  }

  private static boolean isFileSeparator(PsiExpression argument) {
    if (argument instanceof PsiReferenceExpression ref &&
        "separator".equals(ref.getReferenceName()) &&
        ref.resolve() instanceof PsiField field) {
      PsiClass cls = field.getContainingClass();
      return cls != null && CommonClassNames.JAVA_IO_FILE.equals(cls.getQualifiedName());
    }
    return false;
  }
}