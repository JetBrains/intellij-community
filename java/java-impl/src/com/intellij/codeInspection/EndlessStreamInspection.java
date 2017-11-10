// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

public class EndlessStreamInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Set<String> ALL_CONSUMING_OPERATIONS = new HashSet<>(Arrays.asList(
    "sorted",
    "distinct",
    "count",
    "reduce",
    "max",
    "min",
    "sum",
    "average",
    "collect",
    "toArray",
    "forEach"
  ));

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.redundant.stream.optional.call.option.streamboxing"), this,
                                          "USELESS_BOXING_IN_STREAM_MAP");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiReferenceExpression methodExpression = call.getMethodExpression();
        String name = methodExpression.getReferenceName();
        if (name == null || !ALL_CONSUMING_OPERATIONS.contains(name)) return;
        // TODO
      }
    };
  }

  @Nullable
  private static PsiMethodCallExpression findSubsequentCall(PsiMethodCallExpression call,
                                                            Predicate<String> isWantedCall,
                                                            Predicate<String> isAllowedIntermediateCall) {
    for (PsiMethodCallExpression chainCall = ExpressionUtils.getCallForQualifier(call); chainCall != null;
         chainCall = ExpressionUtils.getCallForQualifier(chainCall)) {
      String name = chainCall.getMethodExpression().getReferenceName();
      if (name == null) return null;
      if (isWantedCall.test(name)) return chainCall;
      if (!isAllowedIntermediateCall.test(name) ||
          !InheritanceUtil.isInheritor(chainCall.getType(), CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM)) {
        return null;
      }
    }
    return null;
  }
}
