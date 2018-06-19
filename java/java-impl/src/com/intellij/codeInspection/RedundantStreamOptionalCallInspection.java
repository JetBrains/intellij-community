// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.*;
import static com.siyeh.ig.psiutils.StreamApiUtil.findSubsequentCall;

public class RedundantStreamOptionalCallInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(RedundantStreamOptionalCallInspection.class);
  private static final CallMatcher NATURAL_OR_REVERSED_COMPARATOR = anyOf(
    staticCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "naturalOrder", "reverseOrder").parameterCount(0),
    staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "reverseOrder").parameterCount(0)
  );
  private static final CallMatcher COMPARATOR_REVERSE = instanceCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "reversed").parameterCount(0);
  private static final Set<String> INTERESTING_NAMES =
    ContainerUtil.set("map", "filter", "distinct", "sorted", "sequential", "parallel", "unordered", "flatMap");
  private static final Set<String> CALLS_MAKING_SORT_USELESS = ContainerUtil.set("sorted", "anyMatch", "allMatch", "noneMatch", "count");
  private static final Set<String> CALLS_KEEPING_SORT_ORDER =
    ContainerUtil.set("filter", "distinct", "boxed", "asLongStream", "asDoubleStream");
  private static final Set<String> CALLS_KEEPING_ELEMENTS_DISTINCT =
    ContainerUtil.set("filter", "boxed", "asLongStream", "limit", "skip", "sorted", "takeWhile", "dropWhile");
  private static final Set<String> CALLS_AFFECTING_PARALLELIZATION = ContainerUtil.set("sequential", "parallel");
  private static final Set<String> CALLS_USELESS_FOR_SINGLE_ELEMENT_STREAM = ContainerUtil.set("sorted", "distinct");
  private static final Set<String> BOX_UNBOX_NAMES = ContainerUtil
    .set("valueOf", "booleanValue", "byteValue", "charValue", "shortValue", "intValue", "longValue", "floatValue", "doubleValue");
  private static final Set<String> STANDARD_STREAM_INTERMEDIATE_OPERATIONS = ContainerUtil
    .set("asDoubleStream", "asLongStream", "boxed", "distinct", "dropWhile", "filter", "flatMap", "flatMapToDouble",
         "flatMapToInt", "flatMapToLong", "flatMapToObj", "limit", "map", "mapToDouble", "mapToInt", "mapToLong", "mapToObj", "onClose",
         "parallel", "peek", "sequential", "skip", "takeWhile", "unordered");
  private static final Set<String> STANDARD_STREAM_TERMINAL_OPERATIONS = ContainerUtil
    .set("allMatch", "anyMatch", "average", "collect", "count", "findAny", "findFirst", "forEach", "forEachOrdered", "max", "min",
         "noneMatch", "reduce", "sum", "summaryStatistics", "toArray");

  private static final CallMatcher COLLECTOR_TO_SET =
    staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toSet", "toUnmodifiableSet").parameterCount(0);
  private static final CallMatcher COLLECTOR_TO_COLLECTION =
    staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toCollection").parameterCount(1);
  private static final CallMatcher COLLECTOR_TO_MAP =
    staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toMap", "toUnmodifiableMap").parameterTypes(
      CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION, CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION);
  private static final CallMatcher UNORDERED_COLLECTORS = anyOf(COLLECTOR_TO_MAP, COLLECTOR_TO_SET);
  private static final Predicate<PsiMethodCallExpression> UNORDERED_COLLECTOR =
    UNORDERED_COLLECTORS.or(RedundantStreamOptionalCallInspection::isUnorderedToCollection);
  private static final CallMatcher STREAM_OF_SINGLE =
    anyOf(
      staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "of").parameterTypes("T"),
      staticCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "of").parameterTypes("int"),
      staticCall(CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM, "of").parameterTypes("long"),
      staticCall(CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM, "of").parameterTypes("double")
    );

  @SuppressWarnings("PublicField")
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
        if (STREAM_OF_SINGLE.test(call)) {
          handleSingleElementStream(call);
        }
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
              PsiMethodCallExpression furtherCall = call;
              do {
                furtherCall = findSubsequentCall(furtherCall, CALLS_MAKING_SORT_USELESS::contains, UNORDERED_COLLECTOR,
                                                 CALLS_KEEPING_SORT_ORDER::contains);
              }
              while (furtherCall != null && "sorted".equals(furtherCall.getMethodExpression().getReferenceName()) &&
                     !sortingCancelsPreviousSorting(call, furtherCall));
              if (furtherCall != null) {
                String furtherCallName = furtherCall.getMethodExpression().getReferenceName();
                LocalQuickFix additionalFix = null;
                if ("toSet".equals(furtherCallName) || "toCollection".equals(furtherCallName)) {
                  additionalFix = new CollectToOrderedSetFix();
                }
                register(call, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.sorted", furtherCallName),
                         additionalFix);
              }
            }
            break;
          case "distinct":
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall =
                findSubsequentCall(call, Predicate.isEqual("distinct"), CALLS_KEEPING_ELEMENTS_DISTINCT::contains);
              if (furtherCall != null && furtherCall.getArgumentList().isEmpty()) {
                register(furtherCall, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.distinct"));
              }
              Predicate<PsiMethodCallExpression> setCollector =
                COLLECTOR_TO_SET.or(RedundantStreamOptionalCallInspection::isToCollectionSet);
              if (findSubsequentCall(call, c -> false, setCollector,
                                     ContainerUtil.set("unordered", "parallel", "sequential", "sorted")::contains) != null) {
                register(call, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.distinct.set"));
              }
            }
            break;
          case "unordered":
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall =
                findSubsequentCall(call, Predicate.isEqual("unordered"), n -> !n.equals("sorted"));
              if (furtherCall != null && furtherCall.getArgumentList().isEmpty()) {
                register(furtherCall, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.unordered"));
              }
            }
            break;
          case "sequential":
          case "parallel":
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall = findSubsequentCall(call, CALLS_AFFECTING_PARALLELIZATION::contains, n -> true);
              if (furtherCall != null && furtherCall.getArgumentList().isEmpty()) {
                register(call, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.parallel",
                                                         furtherCall.getMethodExpression().getReferenceName()));
              }
            }
            break;
        }
      }

      private void handleSingleElementStream(PsiMethodCallExpression call) {
        PsiMethodCallExpression subsequentCall =
          findSubsequentCall(call, CALLS_USELESS_FOR_SINGLE_ELEMENT_STREAM::contains,
                             name -> STANDARD_STREAM_INTERMEDIATE_OPERATIONS.contains(name) && !name.startsWith("flatMap"));
        if (subsequentCall != null) {
          register(subsequentCall, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.at.most.one"));
          return;
        }
        Predicate<String> standardNoSorted = name -> STANDARD_STREAM_INTERMEDIATE_OPERATIONS.contains(name) && !name.equals("sorted");
        PsiMethodCallExpression parallelCall = findSubsequentCall(call, "parallel"::equals, standardNoSorted);
        if (parallelCall != null && findSubsequentCall(call, STANDARD_STREAM_TERMINAL_OPERATIONS::contains, standardNoSorted) != null) {
          register(parallelCall, InspectionsBundle.message("inspection.redundant.stream.optional.call.explanation.parallel.single"));
        }
      }

      private void register(PsiMethodCallExpression call, String explanation, LocalQuickFix... additionalFixes) {
        String methodName = call.getMethodExpression().getReferenceName();
        String message = InspectionsBundle.message("inspection.redundant.stream.optional.call.message", methodName);
        if (explanation != null) {
          message += ": " + explanation;
        }
        holder.registerProblem(call, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, getRange(call),
                               ArrayUtil.prepend(new RemoveCallFix(methodName), additionalFixes));
      }
    };
  }

  private static boolean sortingCancelsPreviousSorting(PsiMethodCallExpression call, PsiMethodCallExpression furtherCall) {
    PsiExpression comparator = skipReversed(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
    PsiExpression nextComparator = skipReversed(ArrayUtil.getFirstElement(furtherCall.getArgumentList().getExpressions()));
    if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(comparator, nextComparator)) {
      return true;
    }
    boolean isNatural = comparator == null || NATURAL_OR_REVERSED_COMPARATOR.matches(comparator);
    boolean isNextNatural = nextComparator == null || NATURAL_OR_REVERSED_COMPARATOR.matches(nextComparator);
    return isNatural && isNextNatural;
  }

  private static PsiExpression skipReversed(PsiExpression comparator) {
    comparator = PsiUtil.skipParenthesizedExprDown(comparator);
    while (comparator instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)comparator;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (!COMPARATOR_REVERSE.test(call) || qualifier == null) {
        break;
      }
      comparator = PsiUtil.skipParenthesizedExprDown(qualifier);
    }
    return comparator;
  }

  static boolean isUnorderedToCollection(PsiMethodCallExpression call) {
    if (!COLLECTOR_TO_COLLECTION.test(call)) return false;
    PsiClass aClass = FunctionalExpressionUtils.getClassOfDefaultConstructorFunction(call.getArgumentList().getExpressions()[0]);
    return aClass != null && CommonClassNames.JAVA_UTIL_HASH_SET.equals(aClass.getQualifiedName());
  }

  static boolean isToCollectionSet(PsiMethodCallExpression call) {
    if (!COLLECTOR_TO_COLLECTION.test(call)) return false;
    PsiClass aClass = FunctionalExpressionUtils.getClassOfDefaultConstructorFunction(call.getArgumentList().getExpressions()[0]);
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_SET);
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
    if (list.isEmpty()) {
      primitiveCandidate = method.getReturnType();
    }
    else if (list.getParametersCount() == 1) {
      primitiveCandidate = list.getParameters()[0].getType();
    }
    if (!(primitiveCandidate instanceof PsiPrimitiveType)) return false;
    return Objects.equals(((PsiPrimitiveType)primitiveCandidate).getBoxedTypeName(), aClass.getQualifiedName());
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

  private static class CollectToOrderedSetFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.redundant.stream.optional.call.fix.collect.to.ordered.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression sortCall = tryCast(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (sortCall == null) return;
      PsiMethodCallExpression collector =
        findSubsequentCall(sortCall, c -> false, UNORDERED_COLLECTOR, CALLS_KEEPING_SORT_ORDER::contains);
      if (collector == null) return;
      CommentTracker ct = new CommentTracker();
      String replacementText = CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS + ".toCollection(java.util.LinkedHashSet::new)";
      PsiElement result = ct.replaceAndRestoreComments(collector, replacementText);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
    }
  }
}
