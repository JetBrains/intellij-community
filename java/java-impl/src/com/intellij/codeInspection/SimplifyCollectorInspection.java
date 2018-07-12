// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.FunctionalExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimplifyCollectorInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null || !isCollectorMethod(call, "groupingBy", "groupingByConcurrent")) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length != 2 && args.length != 3) return;
        CombinedCollector combinedCollector = new CombinedCollector(ArrayUtil.getLastElement(args), null, null);
        // Unwrap at most twice to gather collectingAndThen and/or mapping
        combinedCollector = combinedCollector.tryUnwrap().tryUnwrap();
        PsiMethodCallExpression downstream = ObjectUtils.tryCast(combinedCollector.myDownstream, PsiMethodCallExpression.class);
        if (downstream == null ||
            !FunctionalExpressionUtils
              .isFunctionalReferenceTo(combinedCollector.myFinisher, CommonClassNames.JAVA_UTIL_OPTIONAL, null, "get")) {
          return;
        }
        if (isCollectorMethod(downstream, "maxBy", "minBy", "reducing") &&
            downstream.getArgumentList().getExpressionCount() == 1) {
          String replacement = nameElement.getText().equals("groupingBy") ? "toMap" : "toConcurrentMap";
          holder.registerProblem(nameElement, InspectionsBundle.message("inspection.simplify.collector.message", replacement),
                                 new SimplifyCollectorFix(replacement));
        }
      }
    };
  }

  @Contract("null, _ -> false")
  private static boolean isCollectorMethod(PsiMethodCallExpression call, String... methodNames) {
    if (call == null) return false;
    String name = call.getMethodExpression().getReferenceName();
    if (ArrayUtil.contains(name, methodNames)) {
      PsiMethod method = call.resolveMethod();
      if (method != null && method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass aClass = method.getContainingClass();
        return aClass != null && CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS.equals(aClass.getQualifiedName())
               && method.getParameterList().getParametersCount() == call.getArgumentList().getExpressionCount();
      }
    }
    return false;
  }

  static class CombinedCollector {
    final PsiExpression myDownstream;
    final @Nullable PsiExpression myFinisher;
    final @Nullable PsiExpression myMapper;

    CombinedCollector(PsiExpression downstream, @Nullable PsiExpression finisher, @Nullable PsiExpression mapper) {
      myDownstream = PsiUtil.skipParenthesizedExprDown(downstream);
      myFinisher = PsiUtil.skipParenthesizedExprDown(finisher);
      myMapper = PsiUtil.skipParenthesizedExprDown(mapper);
    }

    CombinedCollector tryUnwrap() {
      if (myDownstream instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)myDownstream;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (myFinisher == null && isCollectorMethod(call, "collectingAndThen")) {
          return new CombinedCollector(args[0], args[1], myMapper);
        }
        if (myMapper == null && isCollectorMethod(call, "mapping")) {
          return new CombinedCollector(args[1], myFinisher, args[0]);
        }
      }
      return this;
    }
  }

  private static class SimplifyCollectorFix implements LocalQuickFix {
    private final String myMethodName;

    public SimplifyCollectorFix(String methodName) {
      myMethodName = methodName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.simplify.collector.fix.name", myMethodName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.simplify.collector.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (!isCollectorMethod(call, "groupingBy", "groupingByConcurrent")) return;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 2 && args.length != 3) return;
      CombinedCollector combinedCollector = new CombinedCollector(ArrayUtil.getLastElement(args), null, null);
      // Unwrap at most twice to gather collectingAndThen and/or mapping
      combinedCollector = combinedCollector.tryUnwrap().tryUnwrap();
      PsiMethodCallExpression downstream = ObjectUtils.tryCast(combinedCollector.myDownstream, PsiMethodCallExpression.class);
      if (downstream == null ||
          !FunctionalExpressionUtils.isFunctionalReferenceTo(combinedCollector.myFinisher, CommonClassNames.JAVA_UTIL_OPTIONAL, null, "get")) {
        return;
      }
      PsiExpression[] downstreamArgs = downstream.getArgumentList().getExpressions();
      if (downstreamArgs.length != 1) return;
      PsiExpression downstreamArg = downstreamArgs[0];
      String downstreamName = downstream.getMethodExpression().getReferenceName();
      if (downstreamName == null) return;
      CommentTracker ct = new CommentTracker();
      PsiType collectorType = call.getType();
      PsiType mapType = PsiUtil.substituteTypeParameter(collectorType, "java.util.stream.Collector", 2, false);
      PsiType valueType = PsiUtil.substituteTypeParameter(mapType, CommonClassNames.JAVA_UTIL_MAP, 1, false);
      String valueTypeArg = valueType == null ? "" : "<" + valueType.getCanonicalText() + ">";
      String merger;
      switch (downstreamName) {
        case "minBy":
        case "maxBy":
          merger = "java.util.function.BinaryOperator." + valueTypeArg + downstreamName + "(" + ct.text(downstreamArg) + ")";
          break;
        case "reducing":
          merger = ct.text(downstreamArg);
          break;
        default:
          return;
      }
      String keyMapper = ct.text(args[0]);
      String mapSupplier = args.length == 3 ? ct.text(args[1]) : null;
      String valueMapper =
        combinedCollector.myMapper == null ? CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION + "." + valueTypeArg + "identity()" :
        ct.text(combinedCollector.myMapper);
      String replacement = StreamEx.of(keyMapper, valueMapper, merger, mapSupplier).nonNull()
        .joining(",", CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + "." + myMethodName + "(", ")");
      PsiElement result = ct.replaceAndRestoreComments(call, replacement);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
      result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }
}
