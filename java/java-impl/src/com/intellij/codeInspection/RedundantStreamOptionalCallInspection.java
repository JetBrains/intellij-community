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
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.StreamApiUtil.findSubsequentCall;

/**
 * @author Tagir Valeev
 */
public class RedundantStreamOptionalCallInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(RedundantStreamOptionalCallInspection.class);
  private static final Set<String> INTERESTING_NAMES =
    ContainerUtil.set("map", "filter", "distinct", "sorted", "sequential", "parallel", "unordered", "flatMap");
  private static final Set<String> CALLS_MAKING_SORT_USELESS = ContainerUtil.set("sorted", "anyMatch", "allMatch", "noneMatch", "count");
  private static final Set<String> CALLS_KEEPING_SORT_ORDER =
    ContainerUtil.set("filter", "distinct", "boxed", "asLongStream", "asDoubleStream");
  private static final Set<String> CALLS_KEEPING_ELEMENTS_DISTINCT =
    ContainerUtil.set("filter", "boxed", "asLongStream", "limit", "skip", "sorted", "takeWhile", "dropWhile");
  private static final Set<String> CALLS_AFFECTING_PARALLELIZATION = ContainerUtil.set("sequential", "parallel");
  private static final Set<String> BOX_UNBOX_NAMES = ContainerUtil
    .set("valueOf", "booleanValue", "byteValue", "charValue", "shortValue", "intValue", "longValue", "floatValue", "doubleValue");

  public boolean USELESS_BOXING_IN_STREAM_MAP = true;

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
        if (name == null || !INTERESTING_NAMES.contains(name)) return;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return;
        PsiMethod method = call.resolveMethod();
        if (method == null) return;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return;
        String className = aClass.getQualifiedName();
        if (className == null) return;
        boolean optional = OptionalUtil.isOptionalClassName(className);
        boolean stream = InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM);
        if (!optional && !stream) return;
        if (!EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(qualifier.getType(), call.getType())) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        switch (name) {
          case "filter":
            if (args.length == 1 && isTruePredicate(args[0])) {
              register(call, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.filter"));
            }
            break;
          case "map":
            boolean allowBoxUnbox =
              USELESS_BOXING_IN_STREAM_MAP || optional || StreamApiUtil.getStreamElementType(call.getType()) instanceof PsiPrimitiveType;
            if (args.length == 1 && isIdentityMapping(args[0], allowBoxUnbox)) {
              register(call, null);
            }
            break;
          case "flatMap":
            if (args.length == 1) {
              if (optional) {
                if (FunctionalExpressionUtils
                      .isFunctionalReferenceTo(args[0], CommonClassNames.JAVA_UTIL_OPTIONAL, null, "of", new PsiType[1]) ||
                    FunctionalExpressionUtils
                      .isFunctionalReferenceTo(args[0], CommonClassNames.JAVA_UTIL_OPTIONAL, null, "ofNullable", new PsiType[1])) {
                  register(call, null);
                }
              }
            }
            break;
          case "sorted":
            if (args.length <= 1) {
              PsiMethodCallExpression furtherCall =
                findSubsequentCall(call, CALLS_MAKING_SORT_USELESS::contains, CALLS_KEEPING_SORT_ORDER::contains);
              if (furtherCall != null) {
                register(call, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.sorted",
                                                         furtherCall.getMethodExpression().getReferenceName()));
              }
            }
            break;
          case "distinct":
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall =
                findSubsequentCall(call, Predicate.isEqual("distinct"), CALLS_KEEPING_ELEMENTS_DISTINCT::contains);
              if (furtherCall != null && furtherCall.getArgumentList().getExpressions().length == 0) {
                register(furtherCall, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.distinct"));
              }
            }
            break;
          case "unordered":
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall =
                findSubsequentCall(call, Predicate.isEqual("unordered"), n -> !n.equals("sorted"));
              if (furtherCall != null && furtherCall.getArgumentList().getExpressions().length == 0) {
                register(furtherCall, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.unordered"));
              }
            }
            break;
          case "sequential":
          case "parallel":
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall = findSubsequentCall(call, CALLS_AFFECTING_PARALLELIZATION::contains, n -> true);
              if (furtherCall != null && furtherCall.getArgumentList().getExpressions().length == 0) {
                register(call, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.parallel",
                                                         furtherCall.getMethodExpression().getReferenceName()));
              }
            }
            break;
        }
      }

      private void register(PsiMethodCallExpression call, String explanation) {
        String methodName = call.getMethodExpression().getReferenceName();
        String message = InspectionsBundle.message("inspection.redundant.stream.optional.call.message", methodName);
        if (explanation != null) {
          message += ": " + explanation;
        }
        holder.registerProblem(call, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, getRange(call),
                               new RemoveCallFix(methodName));
      }
    };
  }

  @NotNull
  static TextRange getRange(PsiMethodCallExpression call) {
    PsiReferenceExpression expression = call.getMethodExpression();
    PsiElement nameElement = expression.getReferenceNameElement();
    LOG.assertTrue(nameElement != null);
    return new TextRange(nameElement.getStartOffsetInParent(), call.getTextLength());
  }

  static boolean isIdentityMapping(PsiExpression expression, boolean allowBoxUnbox) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression &&
        MethodCallUtils
          .isCallToStaticMethod((PsiMethodCallExpression)expression, CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION, "identity", 0)) {
      return true;
    }
    if (expression instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)expression;
      if (LambdaUtil.isIdentityLambda(lambda)) return true;
      if (!allowBoxUnbox) return false;
      PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return false;
      PsiParameter parameter = parameters[0];
      PsiMethodCallExpression call = tryCast(PsiUtil.skipParenthesizedExprDown(body), PsiMethodCallExpression.class);
      if (call == null) return false;
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      if (!BOX_UNBOX_NAMES.contains(methodExpression.getReferenceName())) return false;
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (ExpressionUtils.isReferenceTo(qualifier, parameter) && args.length > 0) return false;
      if (args.length != 1 || !ExpressionUtils.isReferenceTo(args[0], parameter)) return false;
      return isBoxUnboxMethod(call.resolveMethod());
    }
    if (!allowBoxUnbox || !(expression instanceof PsiMethodReferenceExpression)) return false;
    PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)expression;
    if (!BOX_UNBOX_NAMES.contains(methodRef.getReferenceName())) return false;
    PsiMethod method = tryCast(methodRef.resolve(), PsiMethod.class);
    return isBoxUnboxMethod(method);
  }

  @Contract("null -> false")
  private static boolean isBoxUnboxMethod(PsiMethod method) {
    if (method == null) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;
    PsiType primitiveCandidate = null;
    PsiParameterList list = method.getParameterList();
    if (list.getParametersCount() == 0) {
      primitiveCandidate = method.getReturnType();
    }
    else if (list.getParametersCount() == 1) {
      primitiveCandidate = list.getParameters()[0].getType();
    }
    if (!(primitiveCandidate instanceof PsiPrimitiveType)) return false;
    return ((PsiPrimitiveType)primitiveCandidate).getBoxedTypeName().equals(aClass.getQualifiedName());
  }

  static boolean isTruePredicate(PsiExpression expression) {
    PsiLambdaExpression lambda = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiLambdaExpression.class);
    if (lambda != null) {
      PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      return ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(body), Boolean.TRUE);
    }
    return false;
  }

  private static class RemoveCallFix implements LocalQuickFix {
    private final String myMethodName;

    public RemoveCallFix(String methodName) {myMethodName = methodName;}

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.redundant.stream.optional.call.fix.name", myMethodName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.stream.optional.call.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = tryCast(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(call, ct.markUnchanged(qualifier));
    }
  }
}
