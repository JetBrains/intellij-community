/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class AdjustFunctionContextFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Function<PsiMethodCallExpression, Function<PsiType, String>>
    MAP_NAME_ADJUSTER = (PsiMethodCallExpression call) -> (PsiType type) -> {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier != null) {
      PsiType inType = StreamApiUtil.getStreamElementType(qualifier.getType());
      if (type.equals(inType)) return "map";
    }
    if (PsiType.INT.equals(type)) return "mapToInt";
    if (PsiType.LONG.equals(type)) return "mapToLong";
    if (PsiType.DOUBLE.equals(type)) return "mapToDouble";
    return "mapToObj";
  };
  private static final Function<PsiType, String> FLAT_MAP_NAME_ADJUSTER = type -> {
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM)) return "flatMapToInt";
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM)) return "flatMapToLong";
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM)) return "flatMapToDouble";
    if(InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) return "flatMap";
    return null;
  };

  private static final CallMapper<Function<PsiType, String>> METHOD_NAME_ADJUSTER = new CallMapper<Function<PsiType, String>>()
    .register(
      CallMatcher.anyOf(
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "map", "mapToLong", "mapToDouble"),
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM, "map", "mapToInt", "mapToDouble"),
        CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM, "map", "mapToInt", "mapToLong")
      ), MAP_NAME_ADJUSTER)
    .register(
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "flatMap", "flatMapToInt", "flatMapToLong", "flatMapToDouble"),
      FLAT_MAP_NAME_ADJUSTER);

  private final String myOriginalName;
  private final String myNewName;

  protected AdjustFunctionContextFix(@NotNull PsiMethodCallExpression call, @NotNull String targetMethodName) {
    super(call);
    myOriginalName = call.getMethodExpression().getReferenceName();
    myNewName = targetMethodName;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(startElement, PsiMethodCallExpression.class);
    if (call == null) return;
    ExpressionUtils.bindCallTo(call, myNewName);
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("adjust.method.accepting.functional.expression.fix.text", myOriginalName, myNewName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("adjust.method.accepting.functional.expression.fix.family.name");
  }

  @Contract("null -> null")
  @Nullable
  public static AdjustFunctionContextFix createFix(PsiElement context) {
    if (!(context instanceof PsiExpression)) return null;
    PsiExpression expression = (PsiExpression)context;
    PsiFunctionalExpression fn = PsiTreeUtil.getParentOfType(context, PsiFunctionalExpression.class, false);
    if (fn == null) return null;
    PsiExpressionList expressionList = ObjectUtils.tryCast(fn.getParent(), PsiExpressionList.class);
    if (expressionList == null || expressionList.getExpressions().length != 1) return null;
    PsiMethodCallExpression call = ObjectUtils.tryCast(expressionList.getParent(), PsiMethodCallExpression.class);
    Function<PsiType, String> remapper = METHOD_NAME_ADJUSTER.mapFirst(call);
    if (remapper == null) return null;
    PsiType actualReturnType;
    if(expression instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)expression;
      actualReturnType = PsiMethodReferenceUtil.getMethodReferenceReturnType(methodRef);
    } else {
      actualReturnType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, true, () -> expression.getType());
    }
    String targetMethodName = remapper.apply(actualReturnType);
    if (targetMethodName == null) return null;
    return new AdjustFunctionContextFix(call, targetMethodName);
  }
}
