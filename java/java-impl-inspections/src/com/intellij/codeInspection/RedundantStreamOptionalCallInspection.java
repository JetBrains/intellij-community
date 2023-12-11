// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.*;
import static com.siyeh.ig.psiutils.StreamApiUtil.findSubsequentCall;

public final class RedundantStreamOptionalCallInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(RedundantStreamOptionalCallInspection.class);
  private static final CallMatcher NATURAL_OR_REVERSED_COMPARATOR = anyOf(
    staticCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "naturalOrder", "reverseOrder").parameterCount(0),
    staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "reverseOrder").parameterCount(0)
  );
  private static final CallMatcher COMPARATOR_REVERSE = instanceCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "reversed").parameterCount(0);
  private static final Set<String> INTERESTING_NAMES =
    Set.of("map", "filter", "distinct", "sorted", "sequential", "parallel", "unordered", "flatMap");
  private static final Set<String> CALLS_MAKING_SORT_USELESS = Set.of("sorted", "anyMatch", "allMatch", "noneMatch", "count",
                                                                                 "min", "max");
  private static final Map<String, String> CALLS_MAKING_SORT_USELESS_PARALLEL = Map.of("findAny", "findFirst",
                                                                                       "forEach", "forEachOrdered");
  private static final Set<String> CALLS_KEEPING_SORT_ORDER =
    Set.of("filter", "distinct", "boxed", "asLongStream", "asDoubleStream");
  private static final Set<String> CALLS_KEEPING_ELEMENTS_DISTINCT =
    Set.of("filter", "boxed", "asLongStream", "limit", "skip", "sorted", "takeWhile", "dropWhile");
  private static final Set<String> CALLS_AFFECTING_PARALLELIZATION = Set.of("sequential", "parallel");
  private static final Set<String> CALLS_USELESS_FOR_SINGLE_ELEMENT_STREAM = Set.of("sorted", "distinct");
  private static final Set<String> BOX_UNBOX_NAMES = Set.of("valueOf", "booleanValue", "byteValue", "charValue", "shortValue", "intValue", "longValue", "floatValue", "doubleValue");
  private static final Set<String> STANDARD_STREAM_INTERMEDIATE_OPERATIONS = Set.of("asDoubleStream", "asLongStream", "boxed", "distinct", "dropWhile", "filter", "flatMap", "flatMapToDouble",
         "flatMapToInt", "flatMapToLong", "flatMapToObj", "limit", "map", "mapToDouble", "mapToInt", "mapToLong", "mapToObj", "onClose",
         "parallel", "peek", "sequential", "skip", "takeWhile", "unordered");
  private static final Set<String> STANDARD_STREAM_TERMINAL_OPERATIONS = Set.of
    ("allMatch", "anyMatch", "average", "collect", "count", "findAny", "findFirst", "forEach", "forEachOrdered", "max", "min",
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
  private static final CallMatcher STREAM_MAP =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "map").parameterTypes(CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION);
  private static final CallMatcher COLLECTION_STREAM_SOURCE =
    instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "parallelStream", "stream").parameterCount(0);
  private static final CallMatcher PARALLEL_STREAM_SOURCE =
    instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "parallelStream").parameterCount(0);

  @SuppressWarnings("PublicField")
  public boolean USELESS_BOXING_IN_STREAM_MAP = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("USELESS_BOXING_IN_STREAM_MAP", JavaBundle.message("inspection.redundant.stream.optional.call.option.streamboxing")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
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
        boolean optional = OptionalUtil.isJdkOptionalClassName(className);
        boolean stream = InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM);
        if (!optional && !stream) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (name.equals("flatMap") && args.length == 1 && isIdentityMapping(args[0], false)) {
          PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
          if (STREAM_MAP.test(qualifierCall) && qualifierCall.getMethodExpression().getTypeParameters().length == 0 &&
              !isIdentityMapping(qualifierCall.getArgumentList().getExpressions()[0], false)) {
            String message = JavaBundle.message("inspection.redundant.stream.optional.call.message", name) +
                             ": " + JavaBundle.message("inspection.redundant.stream.optional.call.explanation.map.flatMap");
            holder.registerProblem(call, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getRange(call),
                                   new RemoveCallFix(name, "map"));
          }
        }
        if (!EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(qualifier.getType(), call.getType())) return;
        switch (name) {
          case "filter" -> {
            if (args.length == 1 && isTruePredicate(args[0])) {
              register(call, JavaBundle.message("inspection.redundant.stream.optional.call.explanation.filter"));
            }
          }
          case "map" -> {
            boolean allowBoxUnbox =
              USELESS_BOXING_IN_STREAM_MAP || optional || StreamApiUtil.getStreamElementType(call.getType()) instanceof PsiPrimitiveType;
            if (args.length == 1 && isIdentityMapping(args[0], allowBoxUnbox)) {
              register(call, null);
            }
          }
          case "flatMap" -> {
            if (args.length == 1) {
              if (optional) {
                if (FunctionalExpressionUtils
                      .isFunctionalReferenceTo(args[0], CommonClassNames.JAVA_UTIL_OPTIONAL, null, "of", new PsiType[1]) ||
                    FunctionalExpressionUtils
                      .isFunctionalReferenceTo(args[0], CommonClassNames.JAVA_UTIL_OPTIONAL, null, "ofNullable", new PsiType[1])) {
                  register(call, null);
                }
              }
              else {
                if (FunctionalExpressionUtils
                  .isFunctionalReferenceTo(args[0], CommonClassNames.JAVA_UTIL_STREAM_STREAM, null, "of", new PsiType[1])) {
                  register(call, null);
                }
              }
            }
          }
          case "sorted" -> {
            if (args.length <= 1) {
              PsiMethodCallExpression furtherCall = findCallThatSpoilsSorting(call);
              if (furtherCall != null) {
                String furtherCallName = furtherCall.getMethodExpression().getReferenceName();
                String terminalReplacement = CALLS_MAKING_SORT_USELESS_PARALLEL.get(furtherCallName);
                if (terminalReplacement != null && !isParallelStream(call)) return;
                if (("max".equals(furtherCallName) || "min".equals(furtherCallName)) &&
                    !sortingCancelsPreviousSorting(call, furtherCall)) {
                  return;
                }
                LocalQuickFix additionalFix = null;
                if ("toSet".equals(furtherCallName) || "toCollection".equals(furtherCallName)) {
                  additionalFix = new CollectToOrderedSetFix();
                }
                else if (terminalReplacement != null) {
                  additionalFix = new ReplaceTerminalCallFix(terminalReplacement);
                }
                String message = "sorted".equals(furtherCallName)
                                 ? JavaBundle.message("inspection.redundant.stream.optional.call.explanation.sorted.twice")
                                 : terminalReplacement != null
                                   ? JavaBundle.message("inspection.redundant.stream.optional.call.explanation.sorted.parallel",
                                                        furtherCallName)
                                   : JavaBundle.message("inspection.redundant.stream.optional.call.explanation.sorted", furtherCallName);
                register(call, message, LocalQuickFix.notNullElements(additionalFix));
              }
            }
          }
          case "distinct" -> {
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall =
                findSubsequentCall(call, Predicate.isEqual("distinct"), CALLS_KEEPING_ELEMENTS_DISTINCT::contains);
              if (furtherCall != null && furtherCall.getArgumentList().isEmpty()) {
                register(furtherCall, JavaBundle.message("inspection.redundant.stream.optional.call.explanation.distinct"));
              }
              Predicate<PsiMethodCallExpression> setCollector =
                COLLECTOR_TO_SET.or(RedundantStreamOptionalCallInspection::isToCollectionSet);
              if (findSubsequentCall(call, c -> false, setCollector,
                                     Set.of("unordered", "parallel", "sequential", "sorted")::contains) != null) {
                register(call, JavaBundle.message("inspection.redundant.stream.optional.call.explanation.distinct.set"));
              }
            }
          }
          case "unordered" -> {
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall =
                findSubsequentCall(call, Predicate.isEqual("unordered"), n -> !n.equals("sorted"));
              if (furtherCall != null && furtherCall.getArgumentList().isEmpty()) {
                register(furtherCall, JavaBundle.message("inspection.redundant.stream.optional.call.explanation.unordered"));
              }
            }
          }
          case "sequential", "parallel" -> {
            if (args.length == 0) {
              PsiMethodCallExpression furtherCall = findSubsequentCall(call, CALLS_AFFECTING_PARALLELIZATION::contains, n -> true);
              if (furtherCall != null && furtherCall.getArgumentList().isEmpty()) {
                register(call, JavaBundle.message("inspection.redundant.stream.optional.call.explanation.parallel",
                                                  furtherCall.getMethodExpression().getReferenceName()));
              }
              else {
                PsiMethodCallExpression previousCall = MethodCallUtils.getQualifierMethodCall(call);
                while (true) {
                  if (previousCall == null) break;
                  String prevName = previousCall.getMethodExpression().getReferenceName();
                  if (prevName == null) break;
                  if (CALLS_AFFECTING_PARALLELIZATION.contains(prevName)) break; // already reported on previous call
                  if (COLLECTION_STREAM_SOURCE.test(previousCall)) {
                    boolean previousParallel = prevName.equals("parallelStream");
                    boolean curParallel = name.equals("parallel");
                    if (previousParallel == curParallel) {
                      register(call,
                               JavaBundle.message(curParallel ? "inspection.redundant.stream.optional.call.explanation.parallel.source" :
                                                  "inspection.redundant.stream.optional.call.explanation.sequential.source"));
                      break;
                    }
                  }
                  if (StreamApiUtil.getStreamElementType(previousCall.getType()) == null) break;
                  previousCall = MethodCallUtils.getQualifierMethodCall(previousCall);
                }
              }
            }
          }
        }
      }

      private static boolean isParallelStream(PsiMethodCallExpression call) {
        while(true) {
          PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
          if (qualifier == null) return false;
          if (StreamApiUtil.getStreamElementType(qualifier.getType()) == null) return false;
          String name = call.getMethodExpression().getReferenceName();
          if (name == null) return false;
          if (name.equals("sequential")) return false;
          if (name.equals("parallel")) return true;
          if (!(qualifier instanceof PsiMethodCallExpression qualifierCall)) return false;
          if (PARALLEL_STREAM_SOURCE.test(qualifierCall)) return true;
          call = qualifierCall;
        }
      }

      private void handleSingleElementStream(PsiMethodCallExpression call) {
        PsiMethodCallExpression subsequentCall =
          findSubsequentCall(call, CALLS_USELESS_FOR_SINGLE_ELEMENT_STREAM::contains,
                             name -> STANDARD_STREAM_INTERMEDIATE_OPERATIONS.contains(name) && !name.startsWith("flatMap"));
        if (subsequentCall != null) {
          register(subsequentCall, JavaBundle.message("inspection.redundant.stream.optional.call.explanation.at.most.one"));
          return;
        }
        Predicate<String> standardNoSorted = name -> STANDARD_STREAM_INTERMEDIATE_OPERATIONS.contains(name) && !name.equals("sorted");
        PsiMethodCallExpression parallelCall = findSubsequentCall(call, "parallel"::equals, standardNoSorted);
        if (parallelCall != null && findSubsequentCall(call, STANDARD_STREAM_TERMINAL_OPERATIONS::contains, standardNoSorted) != null) {
          register(parallelCall, JavaBundle.message("inspection.redundant.stream.optional.call.explanation.parallel.single"));
        }
      }

      private void register(PsiMethodCallExpression call, @Nls String explanation, @NotNull LocalQuickFix @NotNull ... additionalFixes) {
        String methodName = Objects.requireNonNull(call.getMethodExpression().getReferenceName());
        String message = explanation != null
                         ? JavaBundle.message("inspection.redundant.stream.optional.call.message.with.explanation", methodName, explanation)
                         : JavaBundle.message("inspection.redundant.stream.optional.call.message", methodName);
        holder.registerProblem(call, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, getRange(call),
                               ArrayUtil.prepend(new RemoveCallFix(methodName), additionalFixes));
      }
    };
  }

  @Nullable
  private static PsiMethodCallExpression findCallThatSpoilsSorting(@NotNull PsiMethodCallExpression call) {
    PsiMethodCallExpression furtherCall = call;
    do {
      furtherCall = findSubsequentCall(furtherCall, o ->
                                         CALLS_MAKING_SORT_USELESS.contains(o) || CALLS_MAKING_SORT_USELESS_PARALLEL.containsKey(o),
                                       UNORDERED_COLLECTOR, CALLS_KEEPING_SORT_ORDER::contains);
    }
    while (furtherCall != null && "sorted".equals(furtherCall.getMethodExpression().getReferenceName()) &&
           !sortingCancelsPreviousSorting(call, furtherCall));
    return furtherCall;
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
    while (comparator instanceof PsiMethodCallExpression call) {
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
    if (expression instanceof PsiLambdaExpression lambda) {
      if (LambdaUtil.isIdentityLambda(lambda)) return true;
      if (!allowBoxUnbox) return false;
      PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return false;
      PsiParameter parameter = parameters[0];
      PsiMethodCallExpression call = tryCast(PsiUtil.skipParenthesizedExprDown(body), PsiMethodCallExpression.class);
      if (call == null) return false;
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      String referenceName = methodExpression.getReferenceName();
      if (referenceName == null || !BOX_UNBOX_NAMES.contains(referenceName)) return false;
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (ExpressionUtils.isReferenceTo(qualifier, parameter) && args.length > 0) return false;
      if (args.length != 1 || !ExpressionUtils.isReferenceTo(args[0], parameter)) return false;
      return isBoxUnboxMethod(call.resolveMethod());
    }
    if (!allowBoxUnbox || !(expression instanceof PsiMethodReferenceExpression methodRef)) return false;
    String referenceName = methodRef.getReferenceName();
    if (referenceName == null || !BOX_UNBOX_NAMES.contains(referenceName)) return false;
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

  private static class RemoveCallFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull String myMethodName;
    private final @Nullable String myBindPreviousCall;

    RemoveCallFix(@NotNull String methodName) {
      this(methodName, null);
    }

    RemoveCallFix(@NotNull String methodName, @Nullable String bindPreviousCall) {
      myMethodName = methodName;
      myBindPreviousCall = bindPreviousCall;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if (myBindPreviousCall != null) {
        return JavaBundle.message("inspection.redundant.stream.optional.call.fix.bind.name", myMethodName, myBindPreviousCall);
      }
      return JavaBundle.message("inspection.redundant.stream.optional.call.fix.name", myMethodName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.stream.optional.call.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = tryCast(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      if (myBindPreviousCall != null) {
        PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
        if (qualifierCall == null) return;
        ExpressionUtils.bindCallTo(qualifierCall, myMethodName);
      }
      new CommentTracker().replaceAndRestoreComments(call, qualifier);
    }
  }

  private static final class ReplaceTerminalCallFix extends PsiUpdateModCommandQuickFix implements HighPriorityAction {
    private final String myNewName;

    private ReplaceTerminalCallFix(String newName) { this.myNewName = newName; }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.redundant.stream.optional.call.fix.replace.terminal.text", myNewName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.redundant.stream.optional.call.fix.replace.terminal");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiMethodCallExpression call)) return;
      PsiMethodCallExpression furtherCall = findCallThatSpoilsSorting(call);
      if (furtherCall == null) return;
      String name = furtherCall.getMethodExpression().getReferenceName();
      if (name == null || !myNewName.equals(CALLS_MAKING_SORT_USELESS_PARALLEL.get(name))) return;
      ExpressionUtils.bindCallTo(furtherCall, myNewName);
    }
  }

  private static class CollectToOrderedSetFix extends PsiUpdateModCommandQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.stream.optional.call.fix.collect.to.ordered.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression sortCall = tryCast(element, PsiMethodCallExpression.class);
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
