// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.NullnessUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.StreamApiUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;


public class NullableComparatorInspection extends AbstractBaseJavaLocalInspectionTool {

  private final CallMatcher.Simple STREAM_SORTED =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "sorted").parameterCount(1);
  private final CallMatcher.Simple LIST_SORT = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "sort").parameterCount(1);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if(qualifier == null) return;
        PsiClassType elementType;
        if(STREAM_SORTED.test(call)) {
          elementType = tryCast(StreamApiUtil.getStreamElementType(qualifier.getType()), PsiClassType.class);
          if (elementType == null) return;
        } else if (LIST_SORT.test(call)) {
          PsiType type = PsiUtil.substituteTypeParameter(qualifier.getType(), CommonClassNames.JAVA_UTIL_LIST, 0, false);
          elementType = tryCast(type, PsiClassType.class);
          if(elementType == null) return;
        } else return;
        if(InheritanceUtil.isInheritor(elementType.resolve(), CommonClassNames.JAVA_LANG_COMPARABLE)) return;
        PsiExpression comparator = call.getArgumentList().getExpressions()[0];
        Nullness nullness = NullnessUtil.getExpressionNullness(comparator);
        if(nullness != Nullness.NULLABLE) return;
        holder.registerProblem(comparator, "Nullable comparator passed to sort method on elements not implementing Comparable");
      }
    };
  }

}
