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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.*;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class UseOfPropertiesAsHashtableInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("properties.object.as.hashtable.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    final String methodName = methodCallExpression.getMethodExpression().getReferenceName();
    final boolean put = HardcodedMethodConstants.PUT.equals(methodName);
    if (!(put || HardcodedMethodConstants.GET.equals(methodName))) {
      return null;
    }
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (PsiExpression argument : arguments) {
      final PsiType type = argument.getType();
      if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return null;
      }
    }
    if (!put) {
      PsiType expectedType = ExpectedTypeUtils.findExpectedType(methodCallExpression, false, true);
      if (expectedType != null && !expectedType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return null;
      }
    }
    return new UseOfPropertiesAsHashtableFix(put);
  }

  private static class UseOfPropertiesAsHashtableFix extends PsiUpdateModCommandQuickFix {

    private final boolean put;

    UseOfPropertiesAsHashtableFix(boolean put) {
      this.put = put;
    }

    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", put ? "setProperty()" : "getProperty()");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("use.of.properties.as.hashtable.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      CommentTracker commentTracker = new CommentTracker();
      @NonNls final StringBuilder newExpression = new StringBuilder();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        newExpression.append(commentTracker.text(qualifierExpression));
        newExpression.append('.');
      }
      if (put) {
        newExpression.append("setProperty(");
      }
      else {
        newExpression.append("getProperty(");
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      newExpression.append(StringUtil.join(arguments, arg -> commentTracker.text(arg), ","));
      newExpression.append(')');

      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfPropertiesAsHashtableVisitor();
  }

  private static class UseOfPropertiesAsHashtableVisitor extends BaseInspectionVisitor {
    private static final CallMatcher HASH_TABLE_CALLS =
      CallMatcher.anyOf(
        CallMatcher.instanceCall("java.util.Hashtable", "put", "putIfAbsent").parameterTypes("K", "V"), // JDK 8 and below
        CallMatcher.instanceCall("java.util.Hashtable", "put", "putIfAbsent").
          parameterTypes(CommonClassNames.JAVA_LANG_OBJECT, CommonClassNames.JAVA_LANG_OBJECT), // JDK 9 and above
        CallMatcher.instanceCall("java.util.Hashtable", "get").parameterTypes(CommonClassNames.JAVA_LANG_OBJECT),
        CallMatcher.instanceCall("java.util.Hashtable", "putAll").parameterTypes(CommonClassNames.JAVA_UTIL_MAP)
      );

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (!HASH_TABLE_CALLS.test(call)) return;
      final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_UTIL_PROPERTIES)) return;
      if ("putAll".equals(call.getMethodExpression().getReferenceName())) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        // putAll with properties or Map<String, String> argument is probably safe,
        // assuming that the original Properties or Map<String, String> object was safely filled
        if (args.length == 1) {
          PsiType type = args[0].getType();
          if (TypeUtils.typeEquals(CommonClassNames.JAVA_UTIL_PROPERTIES, type) ||
            TypeUtils.isJavaLangString(PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true)) &&
            TypeUtils.isJavaLangString(PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true))) {
            return;
          }
        }
      }
      registerMethodCallError(call, call);
    }
  }
}
