// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.bugs.MismatchedCollectionQueryUpdateInspection;
import com.siyeh.ig.callMatcher.CallHandler;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

import static com.intellij.codeInspection.util.OptionalUtil.*;
import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.psi.util.PsiUtil.skipParenthesizedExprDown;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.*;
import static com.siyeh.ig.psiutils.MethodCallUtils.getQualifierMethodCall;

/**
 * @author Tagir Valeev
 */
public final class SimplifyStreamApiCallChainsInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher COLLECTION_STREAM = instanceCall(JAVA_UTIL_COLLECTION, "stream").parameterCount(0);
  private static final CallMatcher COLLECTION_SIZE_CHECK = instanceCall(JAVA_UTIL_COLLECTION, "size", "isEmpty").parameterCount(0);
  private static final CallMatcher COLLECTION_CONTAINS = instanceCall(JAVA_UTIL_COLLECTION, "contains").parameterCount(1);
  private static final CallMatcher OPTIONAL_STREAM = instanceCall(JAVA_UTIL_OPTIONAL, "stream").parameterCount(0);
  private static final CallMatcher STREAM_FIND = instanceCall(JAVA_UTIL_STREAM_STREAM, "findFirst", "findAny").parameterCount(0);
  private static final CallMatcher STREAM_FILTER =
    instanceCall(JAVA_UTIL_STREAM_STREAM, "filter").parameterTypes(JAVA_UTIL_FUNCTION_PREDICATE);
  private static final CallMatcher STREAM_FIND_FIRST = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "findFirst").parameterCount(0);
  private static final CallMatcher STREAM_SORTED = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "sorted");
  private static final CallMatcher STREAM_MAP = instanceCall(JAVA_UTIL_STREAM_STREAM, "map").parameterTypes(JAVA_UTIL_FUNCTION_FUNCTION);
  private static final CallMatcher BASE_STREAM_MAP = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "map").parameterCount(1);
  private static final CallMatcher STREAM_ANY_MATCH = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "anyMatch").parameterCount(1);
  private static final CallMatcher INT_STREAM_RANGE = staticCall(JAVA_UTIL_STREAM_INT_STREAM, "range").parameterTypes("int", "int");
  private static final CallMatcher STREAM_NONE_MATCH = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "noneMatch").parameterCount(1);
  private static final CallMatcher STREAM_ALL_MATCH = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "allMatch").parameterCount(1);
  private static final CallMatcher STREAM_COLLECT = instanceCall(JAVA_UTIL_STREAM_STREAM, "collect").parameterCount(1);
  private static final CallMatcher OPTIONAL_IS_PRESENT = instanceCall(JAVA_UTIL_OPTIONAL, "isPresent").parameterCount(0);
  private static final CallMatcher BOOLEAN_EQUALS = instanceCall(JAVA_LANG_BOOLEAN, "equals").parameterCount(1);
  private static final CallMatcher STREAM_OF = staticCall(JAVA_UTIL_STREAM_STREAM, "of").parameterTypes("T");
  private static final CallMatcher ARRAYS_STREAM = anyOf(
    staticCall(JAVA_UTIL_STREAM_STREAM, "of").parameterTypes("T..."),
    staticCall(JAVA_UTIL_ARRAYS, "stream").parameterTypes("T[]"));

  private static final CallMatcher N_COPIES = staticCall(JAVA_UTIL_COLLECTIONS, "nCopies").parameterTypes("int", "T");
  private static final CallMatcher COMPARATOR_REVERSED = instanceCall(JAVA_UTIL_COMPARATOR, "reversed").parameterCount(0);

  private static final CallMatcher STREAM_INT_MAP_TO_ALL =
    instanceCall(JAVA_UTIL_STREAM_INT_STREAM, "map", "mapToObj", "mapToDouble", "mapToLong").parameterCount(1);
  private static final CallMatcher STREAM_MAP_TO_ALL =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "map", "mapToInt", "mapToDouble", "mapToLong").parameterCount(1);

  private static final CallMatcher STREAM_MATCH = anyOf(STREAM_ANY_MATCH, STREAM_NONE_MATCH, STREAM_ALL_MATCH);

  private static final CallMatcher COLLECTORS_TO_LIST = staticCall(JAVA_UTIL_STREAM_COLLECTORS, "toList", "toUnmodifiableList")
    .parameterCount(0);
  private static final CallMatcher MAP_ENTRY_SET = instanceCall(JAVA_UTIL_MAP, "entrySet").parameterCount(0);
  private static final CallMatcher STREAM_TAKE_WHILE =
    instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "takeWhile").parameterCount(1).withLanguageLevelAtLeast(
      LanguageLevel.JDK_1_9);
  private static final CallMatcher STREAM_ITERATE = anyOf(
    staticCall(JAVA_UTIL_STREAM_STREAM, "iterate").parameterCount(2),
    staticCall(JAVA_UTIL_STREAM_INT_STREAM, "iterate").parameterCount(2),
    staticCall(JAVA_UTIL_STREAM_LONG_STREAM, "iterate").parameterCount(2),
    staticCall(JAVA_UTIL_STREAM_DOUBLE_STREAM, "iterate").parameterCount(2));

  private static final CallMatcher OPTIONAL_OR_ELSE_GET =
    anyOf(
      exactInstanceCall(JAVA_UTIL_OPTIONAL, "orElseGet").parameterCount(1),
      exactInstanceCall(OPTIONAL_INT, "orElseGet").parameterCount(1),
      exactInstanceCall(OPTIONAL_LONG, "orElseGet").parameterCount(1),
      exactInstanceCall(OPTIONAL_DOUBLE, "orElseGet").parameterCount(1)
    );

  private static final CallMapper<CallChainSimplification> CALL_TO_FIX_MAPPER = new CallMapper<>(
    ReplaceCollectionStreamFix.handler(),
    ReplaceWithToArrayFix.handler(),
    ReplaceStreamSupportWithCollectionStreamFix.handler(),
    ReplaceWithBoxedFix.handler(),
    ReplaceWithElementIterationFix.handler(),
    ReplaceForEachMethodFix.handler(),
    RemoveBooleanIdentityFix.handler(),
    ReplaceWithPeekFix.handler(),
    SimpleStreamOfFix.handler(),
    RangeToArrayStreamFix.handler(),
    NCopiesToGenerateStreamFix.handler(),
    SortedFirstToMinMaxFix.handler(),
    AllMatchContainsFix.handler(),
    AnyMatchContainsFix.handler(),
    JoiningStringsFix.handler(),
    ReplaceWithCollectorsJoiningFix.handler(),
    EntrySetMapFix.handler(),
    CollectorToListSize.handler(),
    IterateTakeWhileFix.handler(),
    FilterAndMapUseSameMethodChainFix.handler(),
    ReplaceWithOrElseThrowFix.handler()
  ).registerAll(SimplifyMatchNegationFix.handlers());

  private static final Logger LOG = Logger.getInstance(SimplifyStreamApiCallChainsInspection.class);

  private static final String FOR_EACH_METHOD = "forEach";
  private static final String IF_PRESENT_METHOD = "ifPresent";
  private static final String STREAM_METHOD = "stream";
  private static final String EMPTY_METHOD = "empty";
  private static final String OF_METHOD = "of";
  private static final String ANY_MATCH_METHOD = "anyMatch";
  private static final String NONE_MATCH_METHOD = "noneMatch";
  private static final String ALL_MATCH_METHOD = "allMatch";

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression methodCall) {
        PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        CALL_TO_FIX_MAPPER.mapAll(methodCall)
          .forEach(
            simplification -> holder.registerProblem(nameElement, simplification.getMessage(), new SimplifyCallChainFix(simplification)));
        if (STREAM_COLLECT.test(methodCall)) {
          handleStreamCollect(methodCall);
        }
        else if (OPTIONAL_IS_PRESENT.test(methodCall)) {
          handleOptionalIsPresent(methodCall);
        }
      }

      private void handleOptionalIsPresent(PsiMethodCallExpression methodCall) {
        PsiMethodCallExpression optionalQualifier = getQualifierMethodCall(methodCall);
        if (!STREAM_FIND.test(optionalQualifier)) return;
        PsiMethodCallExpression streamQualifier = getQualifierMethodCall(optionalQualifier);
        if (!STREAM_FILTER.test(streamQualifier)) return;
        ReplaceOptionalIsPresentChainFix fix =
          new ReplaceOptionalIsPresentChainFix(optionalQualifier.getMethodExpression().getReferenceName());
        holder.registerProblem(methodCall, getCallChainRange(methodCall, streamQualifier), fix.getMessage(), new SimplifyCallChainFix(fix));
      }

      private void handleStreamCollect(PsiMethodCallExpression methodCall) {
        PsiElement parameter = methodCall.getArgumentList().getExpressions()[0];
        if(parameter instanceof PsiMethodCallExpression collectorCall) {
          ReplaceCollectorFix fix = ReplaceCollectorFix.COLLECTOR_TO_FIX_MAPPER.mapFirst(collectorCall);
          if (fix != null) {
            TextRange range = methodCall.getTextRange();
            PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
            if (nameElement != null) {
              range = new TextRange(nameElement.getTextOffset(), range.getEndOffset());
            }
            holder.registerProblem(methodCall, range.shiftRight(-methodCall.getTextOffset()), fix.getMessage(),
                                   new SimplifyCallChainFix(fix));
          } else {
            if(!(PsiUtil.resolveClassInClassTypeOnly(methodCall.getType()) instanceof PsiTypeParameter)) {
              String replacement = SimplifyCollectionCreationFix.COLLECTOR_TO_CLASS_MAPPER.mapFirst(collectorCall);
              if (replacement != null) {
                PsiMethodCallExpression qualifier = getQualifierMethodCall(methodCall);
                if (COLLECTION_STREAM.test(qualifier)) {
                  PsiElement startElement = qualifier.getMethodExpression().getReferenceNameElement();
                  if (startElement != null) {
                    holder.registerProblem(methodCall, new TextRange(startElement.getTextOffset() - methodCall.getTextOffset(),
                                                                     methodCall.getTextLength()),
                                           JavaBundle.message("inspection.message.can.be.replaced.with.0.constructor", replacement),
                                           new SimplifyCallChainFix(new SimplifyCollectionCreationFix(replacement)));
                  }
                }
              }
            }
          }
        }
      }
    };
  }

  /**
   * Simplify any stream expressions encountered within given element
   * @param element element to process
   * @param keepStream if true, no simplification which changes stream to non-stream will be performed
   * @return the resulting element (may differ from the passed one if it was completely replaced)
   */
  public static PsiElement simplifyStreamExpressions(PsiElement element, boolean keepStream) {
    boolean replaced = true;
    while(replaced) {
      replaced = false;
      Map<PsiMethodCallExpression, CallChainSimplification> callToSimplification =
        StreamEx.ofTree(element, e -> StreamEx.of(e.getChildren()))
          .select(PsiMethodCallExpression.class)
          .mapToEntry(CALL_TO_FIX_MAPPER::mapFirst)
          .nonNullValues()
          .chain(s -> keepStream ? s.filterValues(CallChainSimplification::keepsStream) : s)
          .toCustomMap(LinkedHashMap::new);
      for (Map.Entry<PsiMethodCallExpression, CallChainSimplification> entry : callToSimplification.entrySet()) {
        if(entry.getKey().isValid()) {
          PsiElement replacement = entry.getValue().simplify(entry.getKey());
          if(replacement != null) {
            replaced = true;
            if(element == entry.getKey()) {
              element = replacement;
            }
          }
        }
      }
    }
    return element;
  }

  static CallMatcher collectorMatcher(String name, int parameterCount) {
    return staticCall(JAVA_UTIL_STREAM_COLLECTORS, name).parameterCount(parameterCount);
  }

  @NotNull
  protected static TextRange getCallChainRange(@NotNull PsiMethodCallExpression expression,
                                               @NotNull PsiMethodCallExpression qualifierExpression) {
    final PsiReferenceExpression qualifierMethodExpression = qualifierExpression.getMethodExpression();
    final PsiElement qualifierNameElement = qualifierMethodExpression.getReferenceNameElement();
    final int startOffset = (qualifierNameElement != null ? qualifierNameElement : qualifierMethodExpression).getTextOffset();
    final int endOffset = expression.getMethodExpression().getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset).shiftRight(-expression.getTextOffset());
  }

  interface CallChainFix {
    @IntentionName String getName();
    void applyFix(@NotNull Project project, PsiElement element);
  }

  interface CallChainSimplification extends CallChainFix {
    @InspectionMessage String getMessage();

    default boolean keepsStream() {
      return true;
    }

    @Override
    default void applyFix(@NotNull Project project, PsiElement element) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if (call != null) {
        simplify(call);
      }
    }

    PsiElement simplify(PsiMethodCallExpression element);
  }

  private static class SimplifyCallChainFix extends PsiUpdateModCommandQuickFix {
    private final CallChainFix myFix;

    SimplifyCallChainFix(CallChainFix fix) {
      myFix = fix;
    }

    @NotNull
    @Override
    public String getName() {
      return myFix.getName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.simplify.stream.call.chain");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      myFix.applyFix(project, element);
    }
  }

  private static class ReplaceCollectionStreamFix implements CallChainSimplification {
    private static final CallMatcher EMPTY_LIST =
      staticCall(JAVA_UTIL_COLLECTIONS, "emptyList").parameterCount(0);
    private static final CallMatcher EMPTY_SET =
      staticCall(JAVA_UTIL_COLLECTIONS, "emptySet").parameterCount(0);
    private static final CallMatcher SINGLETON_LIST =
      staticCall(JAVA_UTIL_COLLECTIONS, "singletonList").parameterCount(1);
    private static final CallMatcher SINGLETON =
      staticCall(JAVA_UTIL_COLLECTIONS, "singleton").parameterCount(1);
    private static final CallMatcher AS_LIST = staticCall(JAVA_UTIL_ARRAYS, "asList").parameterCount(1);
    private static final CallMatcher ENUMSET_OF = staticCall("java.util.EnumSet", "of");
    private static final CallMatcher LIST_OF = staticCall(JAVA_UTIL_LIST, "of");

    private static final CallMapper<ReplaceCollectionStreamFix> COLLECTION_TO_STREAM_MAPPER = new CallMapper<ReplaceCollectionStreamFix>()
      .register(EMPTY_LIST,
                new ReplaceCollectionStreamFix("Collections.emptyList()", JAVA_UTIL_STREAM_STREAM, EMPTY_METHOD))
      .register(EMPTY_SET,
                new ReplaceCollectionStreamFix("Collections.emptySet()", JAVA_UTIL_STREAM_STREAM, EMPTY_METHOD))
      .register(SINGLETON, call -> hasSingleArrayArgument(call)
                                   ? null : new ReplaceSingletonWithStreamOfFix("Collections.singleton()"))
      .register(SINGLETON_LIST, call -> hasSingleArrayArgument(call)
                                        ? null : new ReplaceSingletonWithStreamOfFix("Collections.singletonList()"))
      .register(AS_LIST, call -> hasSingleArrayArgument(call)
                                 ? new ReplaceCollectionStreamFix("Arrays.asList()", JAVA_UTIL_ARRAYS, STREAM_METHOD)
                                 : new ReplaceCollectionStreamFix("Arrays.asList()", JAVA_UTIL_STREAM_STREAM, OF_METHOD))
      .register(ENUMSET_OF, call ->
        isEnumSetReplaceableWithStream(call) ? new ReplaceCollectionStreamFix("EnumSet.of()", JAVA_UTIL_STREAM_STREAM,
                                                                              OF_METHOD) : null)
      .register(LIST_OF, call -> new ReplaceCollectionStreamFix("List.of()", JAVA_UTIL_STREAM_STREAM, OF_METHOD));

    private static final String STREAM_SUFFIX = ".stream()";

    private final String myClassName;
    private final String myMethodName;
    private final String myQualifierCall;

    private ReplaceCollectionStreamFix(String qualifierCall, String className, String methodName) {
      myQualifierCall = qualifierCall;
      myClassName = className;
      myMethodName = methodName;
    }

    @Override
    @NotNull
    public String getMessage() {
      String oldExpr = myQualifierCall + STREAM_SUFFIX;
      String newExpr = ClassUtil.extractClassName(myClassName) + "." + myMethodName + "()";
      return JavaBundle.message("simplify.stream.inspection.message.can.be.replaced", oldExpr, newExpr);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("quickfix.text.replace.0.stream.with.1.2", myQualifierCall, ClassUtil.extractClassName(myClassName), myMethodName);
    }

    @Nullable
    protected String getTypeParameter(@NotNull CommentTracker ct, @NotNull PsiMethodCallExpression qualifierCall) {
      PsiReferenceParameterList parameterList = qualifierCall.getMethodExpression().getParameterList();
      if (parameterList == null) return null;
      PsiTypeElement[] elements = parameterList.getTypeParameterElements();
      return elements.length == 1 ? ct.text(elements[0]) : null;
    }

    @Nullable
    @Override
    public PsiElement simplify(PsiMethodCallExpression streamCall) {
      PsiMethodCallExpression collectionCall = getQualifierMethodCall(streamCall);
      if (collectionCall == null) return null;
      CommentTracker ct = new CommentTracker();
      ct.replace(streamCall.getArgumentList(), collectionCall.getArgumentList());
      String typeParameter = getTypeParameter(ct, collectionCall);
      String replacement;
      if (typeParameter != null) {
        replacement = myClassName + ".<" + typeParameter + ">" + myMethodName;
      }
      else {
        replacement = myClassName + "." + myMethodName;
      }
      Project project = streamCall.getProject();
      PsiElement result = ct.replaceAndRestoreComments(streamCall.getMethodExpression(), replacement);
      return JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
    }

    public static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(COLLECTION_STREAM, methodCall -> COLLECTION_TO_STREAM_MAPPER.mapFirst(getQualifierMethodCall(methodCall)));
    }

    private static boolean isEnumSetReplaceableWithStream(PsiMethodCallExpression call) {
      // Check that all arguments are enum different enum constants from the same enum
      PsiExpression[] expressions = call.getArgumentList().getExpressions();
      if (expressions.length == 0) return false;
      Set<String> names = new HashSet<>();
      PsiClass enumClass = null;
      for (PsiExpression arg : expressions) {
        PsiReferenceExpression ref = tryCast(skipParenthesizedExprDown(arg), PsiReferenceExpression.class);
        if (ref == null) return false;
        PsiEnumConstant enumConstant = tryCast(ref.resolve(), PsiEnumConstant.class);
        if (enumConstant == null || !names.add(enumConstant.getName())) return false;
        if (enumClass == null) {
          enumClass = enumConstant.getContainingClass();
        }
        else if (enumConstant.getContainingClass() != enumClass) {
          return false;
        }
      }
      return true;
    }

    private static boolean hasSingleArrayArgument(PsiMethodCallExpression qualifierCall) {
      final PsiExpression[] argumentExpressions = qualifierCall.getArgumentList().getExpressions();
      if (argumentExpressions.length == 1) {
        PsiType type = argumentExpressions[0].getType();
        if (type instanceof PsiArrayType) {
          PsiType methodType = qualifierCall.getType();
          // Rule out cases like Arrays.<String[]>asList(stringArr)
          if (methodType instanceof PsiClassType) {
            PsiType[] parameters = ((PsiClassType)methodType).getParameters();
            if (parameters.length == 1 && TypeConversionUtil.isAssignable(parameters[0], type)
                && !TypeConversionUtil.isAssignable(parameters[0], ((PsiArrayType)type).getComponentType())) {
              return false;
            }
          }
          return true;
        }
      }
      return false;
    }
  }

  private static final class ReplaceSingletonWithStreamOfFix extends ReplaceCollectionStreamFix {
    private ReplaceSingletonWithStreamOfFix(String qualifierCall) {
      super(qualifierCall, JAVA_UTIL_STREAM_STREAM, OF_METHOD);
    }

    @Nullable
    @Override
    protected String getTypeParameter(@NotNull CommentTracker ct, @NotNull PsiMethodCallExpression qualifierCall) {
      String typeParameter = super.getTypeParameter(ct, qualifierCall);
      if (typeParameter != null) {
        return typeParameter;
      }
      PsiType[] argTypes = qualifierCall.getArgumentList().getExpressionTypes();
      if (argTypes.length == 1) {
        PsiType argType = argTypes[0];
        if (argType instanceof PsiArrayType) {
          return argType.getCanonicalText();
        }
      }
      return null;
    }
  }

  static class ReplaceForEachMethodFix implements CallChainSimplification {
    private static final CallMatcher STREAM_FOR_EACH =
      instanceCall(JAVA_UTIL_STREAM_STREAM, "forEach", "forEachOrdered").parameterCount(1);
    private static final String STREAM_PREFIX = "stream().";

    private final String myStreamMethod;
    private final String myReplacementMethod;
    private final boolean myChangeSemantics;

    ReplaceForEachMethodFix(String streamMethod, String replacementMethod, boolean changeSemantics) {
      myStreamMethod = streamMethod;
      myReplacementMethod = replacementMethod;
      myChangeSemantics = changeSemantics;
    }

    @Override
    public boolean keepsStream() {
      return false;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle
        .message("quickfix.text.replace.stream.0.with.1.2", myStreamMethod, myReplacementMethod, myChangeSemantics ? " (may change semantics)" : "");
    }

    @Override
    @NotNull
    public String getMessage() {
      String before = "'" + STREAM_PREFIX + myStreamMethod + "()'";
      String after = myReplacementMethod + "()'";
      return JavaBundle.message(myChangeSemantics
                                ? "simplify.stream.inspection.message.can.be.replaced.may.change.semantics"
                                : "simplify.stream.inspection.message.can.be.replaced", before, after);
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression streamMethodCall) {
      PsiMethodCallExpression collectionStreamCall = getQualifierMethodCall(streamMethodCall);
      if (collectionStreamCall == null) return null;
      PsiExpression collectionExpression = ExpressionUtils.getEffectiveQualifier(collectionStreamCall.getMethodExpression());
      if (collectionExpression == null) return null;
      collectionStreamCall.replace(collectionExpression);
      ExpressionUtils.bindCallTo(streamMethodCall, myReplacementMethod);
      return streamMethodCall;
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_FOR_EACH, call -> {
        PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
        if (qualifierCall == null || ExpressionUtils.getEffectiveQualifier(qualifierCall.getMethodExpression()) == null) return null;
        if (COLLECTION_STREAM.test(qualifierCall)) {
          return new ReplaceForEachMethodFix(call.getMethodExpression().getReferenceName(), FOR_EACH_METHOD, true);
        }
        if (OPTIONAL_STREAM.test(qualifierCall)) {
          return new ReplaceForEachMethodFix(call.getMethodExpression().getReferenceName(), IF_PRESENT_METHOD, false);
        }
        return null;
      });
    }
  }

  private static class ReplaceCollectorFix implements CallChainFix {
    static final CallMapper<ReplaceCollectorFix> COLLECTOR_TO_FIX_MAPPER = new CallMapper<>(
      handler("counting", 0, "count()", false),
      handler("minBy", 1, "min({0})", true),
      handler("maxBy", 1, "max({0})", true),
      handler("mapping", 2, "map({0}).collect({1})", false),
      handler("reducing", 1, "reduce({0})", true),
      handler("reducing", 2, "reduce({0}, {1})", false),
      handler("reducing", 3, "map({1}).reduce({0}, {2})", false),
      handler("summingInt", 1, "mapToInt({0}).sum()", false),
      handler("summingLong", 1, "mapToLong({0}).sum()", false),
      handler("summingDouble", 1, "mapToDouble({0}).sum()", false),
      CallHandler.of(collectorMatcher("toUnmodifiableList", 0).withLanguageLevelAtLeast(LanguageLevel.JDK_16), call -> {
        return elementTypeMatches(PsiTreeUtil.getParentOfType(call, PsiMethodCallExpression.class))
               ? new ReplaceCollectorFix("toUnmodifiableList", "toList()", false) : null;
      }),
      CallHandler.of(collectorMatcher("toList", 0).withLanguageLevelAtLeast(LanguageLevel.JDK_16), call -> {
        PsiMethodCallExpression collectCall = PsiTreeUtil.getParentOfType(call, PsiMethodCallExpression.class);
        return MismatchedCollectionQueryUpdateInspection.isUnmodified(collectCall) && elementTypeMatches(collectCall)
               ? new ReplaceCollectorFix("toList", "toList()", false)
               : null;
      }));

    @Contract("null -> false")
    private static boolean elementTypeMatches(@Nullable PsiMethodCallExpression collect) {
      if (collect == null) return false;
      PsiExpression qualifier = collect.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return false;
      PsiType streamElementType = PsiUtil.substituteTypeParameter(qualifier.getType(), JAVA_UTIL_STREAM_STREAM, 0, false);
      PsiType collectionElementType = PsiUtil.substituteTypeParameter(collect.getType(), JAVA_UTIL_COLLECTION, 0, false);
      return streamElementType != null && collectionElementType != null && streamElementType.equals(collectionElementType);
    }

    private final String myCollector;
    private final String myStreamSequence;
    private final String myStreamSequenceStripped;
    private final boolean myChangeSemantics;

    ReplaceCollectorFix(String collector, String streamSequence, boolean changeSemantics) {
      myCollector = collector;
      myStreamSequence = streamSequence;
      myStreamSequenceStripped = streamSequence.replaceAll("\\([^)]+\\)", "()");
      myChangeSemantics = changeSemantics;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("quickfix.text.replace.collect.0.with.1.2", myCollector, myStreamSequenceStripped,
                                myChangeSemantics ? " (may change semantics when result is null)" : "");
    }

    @Override
    public void applyFix(@NotNull Project project, PsiElement element) {
      if (element instanceof PsiMethodCallExpression collectCall) {
        PsiExpression qualifierExpression = collectCall.getMethodExpression().getQualifierExpression();
        if (qualifierExpression == null) return;
        PsiExpression[] collectArgs = collectCall.getArgumentList().getExpressions();
        if (collectArgs.length != 1) return;
        PsiMethodCallExpression collectorCall = tryCast(skipParenthesizedExprDown(collectArgs[0]), PsiMethodCallExpression.class);
        if (collectorCall == null) return;
        PsiExpression[] collectorArgs = collectorCall.getArgumentList().getExpressions();
        String result = MessageFormat.format(myStreamSequence, Arrays.stream(collectorArgs).map(PsiExpression::getText).toArray());
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiMethodCallExpression mock = (PsiMethodCallExpression)factory.createExpressionFromText(result, collectCall);
        if (mock.getMethodExpression().getQualifierExpression() == null) {
          ExpressionUtils.bindCallTo(collectCall, Objects.requireNonNull(mock.getMethodExpression().getReferenceName()));
          collectCall.getArgumentList().replace(mock.getArgumentList());
        } else {
          PsiExpression replacement = factory.createExpressionFromText(qualifierExpression.getText() + "." + result, collectCall);
          collectCall = (PsiMethodCallExpression)collectCall.replace(replacement);
        }
        addBoxingIfNecessary(factory, collectCall);
      }
    }

    /*
    Replacements like .collect(counting()) -> .count() change the result type from boxed to primitive
    In rare cases it's necessary to add cast to return back to boxed type
    example:
    List<Integer> intList; List<String> stringList;
    intList.remove(stringList.stream().collect(summingInt(String::length)) -- remove given element
    intList.remove(stringList.stream().mapToInt(String::length).sum()) -- remove element by index
    */
    private static void addBoxingIfNecessary(PsiElementFactory factory, PsiElement expression) {
      if(expression instanceof PsiExpression) {
        PsiType type = ((PsiExpression)expression).getType();
        if(type instanceof PsiPrimitiveType) {
          PsiClassType boxedType = ((PsiPrimitiveType)type).getBoxedType(expression);
          if(boxedType != null) {
            PsiExpression castExpression =
              factory.createExpressionFromText("(" + boxedType.getCanonicalText() + ") " + expression.getText(), expression);
            PsiElement cast = expression.replace(castExpression);
            if (cast instanceof PsiTypeCastExpression && RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)cast)) {
              RemoveRedundantCastUtil.removeCast((PsiTypeCastExpression)cast);
            }
          }
        }
      }
    }

    @NotNull
    public @InspectionMessage String getMessage() {
      String before = "collect(" + myCollector + "())";
      return JavaBundle.message(myChangeSemantics
                                ? "simplify.stream.inspection.message.can.be.replaced.may.change.semantics"
                                : "simplify.stream.inspection.message.can.be.replaced", before, myStreamSequenceStripped);
    }

    static CallHandler<ReplaceCollectorFix> handler(String collectorName, int parameterCount, String template, boolean changeSemantics) {
      return CallHandler.of(collectorMatcher(collectorName, parameterCount),
                            call -> new ReplaceCollectorFix(collectorName, template, changeSemantics));
    }
  }

  private static class ReplaceOptionalIsPresentChainFix implements CallChainFix {
    private final String myFindMethodName;

    ReplaceOptionalIsPresentChainFix(String findMethodName) {
      myFindMethodName = findMethodName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("quickfix.text.replace.filter.0.is.present.with.any.match", myFindMethodName);
    }

    @Override
    public void applyFix(@NotNull Project project, PsiElement element) {
      if (element instanceof PsiMethodCallExpression isPresentCall &&
          isPresentCall.getMethodExpression().getQualifierExpression() instanceof PsiMethodCallExpression findCall &&
          findCall.getMethodExpression().getQualifierExpression() instanceof PsiMethodCallExpression filterCall) {
        PsiElement replacement = element.replace(filterCall);
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiElement filterName = ((PsiMethodCallExpression)replacement).getMethodExpression().getReferenceNameElement();
        LOG.assertTrue(filterName != null);
        filterName.replace(factory.createIdentifier(ANY_MATCH_METHOD));
      }
    }

    @NotNull
    public @InspectionMessage String getMessage() {
      return JavaBundle.message("inspection.message.filter.is.present.chain.can.be.replaced.with.anymatch", myFindMethodName);
    }
  }

  private static final class SimplifyMatchNegationFix implements CallChainSimplification {
    private final String myFrom, myTo;

    private SimplifyMatchNegationFix(PsiMethodCallExpression call, boolean argNegated, boolean parentNegated, String to) {
      String name = call.getMethodExpression().getReferenceName();
      String arg = argNegated ? "x -> !(...)" : "...";
      String className = Objects.requireNonNull(Objects.requireNonNull(call.resolveMethod()).getContainingClass()).getName();
      myFrom = (parentNegated ? "!" : "") + className + "." + name + "(" + arg + ")";
      myTo = to;
    }

    @Override
    public String getName() {
      return JavaBundle.message("simplify.stream.match.negation.fix.name", myFrom, myTo);
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.x.with.y", myFrom, myTo + "()");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression methodCall) {
      String from = methodCall.getMethodExpression().getReferenceName();
      if (from == null) return null;
      boolean removeParentNegation;
      boolean removeLambdaNegation;
      switch (from) {
        case ALL_MATCH_METHOD -> {
          removeLambdaNegation = true;
          removeParentNegation = myTo.equals(ANY_MATCH_METHOD);
        }
        case ANY_MATCH_METHOD -> {
          removeParentNegation = true;
          removeLambdaNegation = myTo.equals(ALL_MATCH_METHOD);
        }
        case NONE_MATCH_METHOD -> {
          removeParentNegation = myTo.equals(ANY_MATCH_METHOD);
          removeLambdaNegation = myTo.equals(ALL_MATCH_METHOD);
        }
        default -> {
          return null;
        }
      }
      if (removeParentNegation && !isParentNegated(methodCall)) return null;
      if (removeLambdaNegation && !isArgumentLambdaNegated(methodCall)) return null;
      ExpressionUtils.bindCallTo(methodCall, myTo);
      if (removeLambdaNegation) {
        // Casts and array bounds already checked in isArgumentLambdaNegated
        PsiExpression body = (PsiExpression)((PsiLambdaExpression)methodCall.getArgumentList().getExpressions()[0]).getBody();
        PsiExpression negated = BoolUtils.getNegated(body);
        LOG.assertTrue(negated != null);
        body.replace(negated);
      }
      if (removeParentNegation) {
        return PsiUtil.skipParenthesizedExprUp(methodCall.getParent()).replace(methodCall);
      }
      return methodCall;
    }

    static boolean isParentNegated(PsiMethodCallExpression methodCall) {
      if (ExpressionUtil.isEffectivelyUnqualified(methodCall.getMethodExpression())) return false;
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
      return parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
    }

    static boolean isArgumentLambdaNegated(PsiMethodCallExpression methodCall) {
      if (ExpressionUtil.isEffectivelyUnqualified(methodCall.getMethodExpression())) return false;
      PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
      if(expressions.length != 1) return false;
      PsiExpression arg = expressions[0];
      if(!(arg instanceof PsiLambdaExpression)) return false;
      PsiElement body = ((PsiLambdaExpression)arg).getBody();
      return body instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)body);
    }

    static List<CallHandler<CallChainSimplification>> handlers() {
      return Arrays.asList(
        CallHandler.of(STREAM_ANY_MATCH, methodCall -> {
          if (!isParentNegated(methodCall)) return null;
          boolean argNegated = isArgumentLambdaNegated(methodCall);
          return new SimplifyMatchNegationFix(methodCall, argNegated, true, argNegated ? ALL_MATCH_METHOD : NONE_MATCH_METHOD);
        }),
        CallHandler.of(STREAM_NONE_MATCH, methodCall ->
          isParentNegated(methodCall) ? new SimplifyMatchNegationFix(methodCall, false, true, ANY_MATCH_METHOD) : null),
        CallHandler.of(STREAM_NONE_MATCH, methodCall ->
          isArgumentLambdaNegated(methodCall) ? new SimplifyMatchNegationFix(methodCall, true, false, ALL_MATCH_METHOD) : null),
        CallHandler.of(STREAM_ALL_MATCH, methodCall -> {
          if (!isArgumentLambdaNegated(methodCall)) return null;
          boolean parentNegated = isParentNegated(methodCall);
          return new SimplifyMatchNegationFix(methodCall, true, parentNegated, parentNegated ? ANY_MATCH_METHOD : NONE_MATCH_METHOD);
        })
      );
    }
  }

  private static class SimplifyCollectionCreationFix implements CallChainFix {
    static final CallMapper<String> COLLECTOR_TO_CLASS_MAPPER = new CallMapper<String>()
      .register(collectorMatcher("toList", 0), JAVA_UTIL_ARRAY_LIST)
      .register(collectorMatcher("toSet", 0), JAVA_UTIL_HASH_SET)
      .register(collectorMatcher("toCollection", 1), SimplifyCollectionCreationFix::getCollectionClass);

    private final String myReplacement;

    SimplifyCollectionCreationFix(String replacement) {
      myReplacement = replacement;
    }

    @Override
    public String getName() {
      return JavaBundle.message("simplify.stream.collection.creation.fix.name", myReplacement);
    }

    @Override
    public void applyFix(@NotNull Project project, PsiElement element) {
      if(!(element instanceof PsiMethodCallExpression collectCall)) return;
      PsiType type = collectCall.getType();
      PsiClass resolvedType = PsiUtil.resolveClassInClassTypeOnly(type);
      if(resolvedType == null || resolvedType instanceof PsiTypeParameter) return;
      PsiMethodCallExpression streamCall = getQualifierMethodCall(collectCall);
      if(streamCall == null) return;
      PsiExpression collectionExpression = streamCall.getMethodExpression().getQualifierExpression();
      if(collectionExpression == null) return;
      String typeText = type.getCanonicalText();
      if (JAVA_UTIL_LIST.equals(resolvedType.getQualifiedName()) ||
          JAVA_UTIL_SET.equals(resolvedType.getQualifiedName())) {
        PsiType[] parameters = ((PsiClassType)type).getParameters();
        if(parameters.length != 1) return;
        typeText = myReplacement + "<" + parameters[0].getCanonicalText() + ">";
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression result = factory
        .createExpressionFromText("new " + typeText + "(" + collectionExpression.getText() + ")", element);
      PsiNewExpression newExpression = (PsiNewExpression)element.replace(result);
      PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
      LOG.assertTrue(classReference != null);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(classReference);
      if (PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, newExpression, null)) {
        RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(classReference.getParameterList());
      }
      CodeStyleManager.getInstance(project).reformat(newExpression);
    }

    @Nullable
    private static String getCollectionClass(PsiMethodCallExpression call) {
      PsiClass aClass = FunctionalExpressionUtils.getClassOfDefaultConstructorFunction(call.getArgumentList().getExpressions()[0]);
      return ConstructionUtils.isCollectionWithCopyConstructor(aClass) ? aClass.getQualifiedName() : null;
    }
  }

  private static class ReplaceWithPeekFix implements CallChainSimplification {

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "peek");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "peek");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiLambdaExpression lambda =
        tryCast(skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
      if (lambda == null) return null;
      PsiCodeBlock block = tryCast(lambda.getBody(), PsiCodeBlock.class);
      if (block == null) return null;
      PsiReturnStatement statement = tryCast(ArrayUtil.getLastElement(block.getStatements()), PsiReturnStatement.class);
      if (statement == null) return null;
      ExpressionUtils.bindCallTo(call, "peek");
      new CommentTracker().deleteAndRestoreComments(statement);
      LambdaRefactoringUtil.simplifyToExpressionLambda(lambda);
      LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambda);
      return call;
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(BASE_STREAM_MAP, call -> {
        PsiLambdaExpression lambda =
          tryCast(skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
        if (lambda == null) return null;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return null;
        PsiCodeBlock block = tryCast(lambda.getBody(), PsiCodeBlock.class);
        if (block == null) return null;
        PsiStatement[] statements = block.getStatements();
        if (statements.length <= 1) return null;
        PsiReturnStatement returnStatement = tryCast(ArrayUtil.getLastElement(statements), PsiReturnStatement.class);
        PsiParameter parameter = parameters[0];
        if (returnStatement == null || !ExpressionUtils.isReferenceTo(returnStatement.getReturnValue(), parameter)) return null;
        if (VariableAccessUtils.variableIsAssigned(parameter)) return null;
        if (Arrays.stream(statements, 0, statements.length - 1).anyMatch(ControlFlowUtils::containsReturn)) return null;
        return new ReplaceWithPeekFix();
      });
    }
  }

  private static class SimpleStreamOfFix implements CallChainSimplification {
    private static final CallMatcher LAMBDA_TERMINAL =
      instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "anyMatch", "allMatch", "noneMatch", "forEach", "forEachOrdered")
        .parameterCount(1);
    private static final CallMatcher OPTIONAL_TERMINAL =
      anyOf(instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "findFirst", "findAny").parameterCount(0),
            instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "min", "max", "reduce").parameterCount(1));
    private final ReplacementMode myMode;

    enum ReplacementMode {
      OPTIONAL, FUNCTION, NEGATED_FUNCTION
    }

    @Override
    public boolean keepsStream() {
      return false;
    }

    SimpleStreamOfFix(ReplacementMode mode) {
      myMode = mode;
    }

    @Override
    public String getName() {
      if (myMode == ReplacementMode.OPTIONAL) {
        return CommonQuickFixBundle.message("fix.replace.with.x", "Optional.of");
      }
      return JavaBundle.message("simplify.stream.simple.stream.of.fix.name.use.stream.element.explicitly");
    }

    @Override
    public String getMessage() {
      return JavaBundle.message("simplify.stream.simple.stream.of.message");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression streamOfCall) {
      PsiExpression streamOfArg = ArrayUtil.getFirstElement(streamOfCall.getArgumentList().getExpressions());
      if (streamOfArg == null) return null;
      PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(streamOfCall);
      PsiExpression nextArg = ArrayUtil.getFirstElement(nextCall.getArgumentList().getExpressions());
      String replacement;
      if (myMode == ReplacementMode.OPTIONAL) {
        replacement = JAVA_UTIL_OPTIONAL + ".of(" + streamOfArg.getText() + ")";
      }
      else {
        if (nextArg == null) return null;
        PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(nextArg.getType());
        if (method == null) return null;
        String name = method.getName();
        replacement =
          (myMode == ReplacementMode.NEGATED_FUNCTION ? "!" : "") + nextArg.getText() + "." + name + "(" + streamOfArg.getText() + ")";
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(streamOfCall.getProject());
      PsiExpression result = factory.createExpressionFromText(replacement, streamOfCall);
      return nextCall.replace(result);
    }

    public static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_OF, call -> {
        PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(call);
        if (nextCall == null) return null;
        if (LAMBDA_TERMINAL.test(nextCall)) {
          PsiExpression arg = skipParenthesizedExprDown(nextCall.getArgumentList().getExpressions()[0]);
          if (arg instanceof PsiReferenceExpression || arg instanceof PsiMethodCallExpression) {
            PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(arg.getType());
            boolean negate = "noneMatch".equals(nextCall.getMethodExpression().getReferenceName());
            if (method == null ||
                negate && PsiUtil.skipParenthesizedExprUp(nextCall.getParent()) instanceof PsiExpressionStatement) {
              return null;
            }
            return new SimpleStreamOfFix(negate ? ReplacementMode.NEGATED_FUNCTION : ReplacementMode.FUNCTION);
          }
        }
        if (OPTIONAL_TERMINAL.test(nextCall)) {
          return new SimpleStreamOfFix(ReplacementMode.OPTIONAL);
        }
        return null;
      });
    }
  }

  private static class ReplaceWithBoxedFix implements CallChainSimplification {
    private static final CallMatcher MAP_TO_OBJ = instanceCall(JAVA_UTIL_STREAM_BASE_STREAM, "mapToObj").parameterCount(1);

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "boxed");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "boxed");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1) return null;
      ExpressionUtils.bindCallTo(call, "boxed");
      args[0].delete();
      call.getTypeArgumentList().delete();
      return call;
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(MAP_TO_OBJ, call -> {
        PsiExpression arg = call.getArgumentList().getExpressions()[0];
        PsiType type = StreamApiUtil.getStreamElementType(call.getType());
        PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (targetClass == null) return null;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null ||
            !TypeConversionUtil.boxingConversionApplicable(StreamApiUtil.getStreamElementType(qualifier.getType()), type) ||
            !isBoxingFunction(arg, targetClass)) {
          return null;
        }
        return new ReplaceWithBoxedFix();
      });
    }

    @Contract("null, _ -> false")
    private static boolean isBoxingFunction(PsiExpression arg, PsiClass targetClass) {
      if (arg instanceof PsiMethodReferenceExpression) {
        PsiElement target = ((PsiMethodReferenceExpression)arg).resolve();
        if (target instanceof PsiMethod method) {
          // Integer::new or Integer::valueOf
          if (targetClass == method.getContainingClass() &&
              (method.isConstructor() || method.getName().equals("valueOf")) && method.getParameterList().getParametersCount() == 1) {
            return true;
          }
        }
      }
      if (arg instanceof PsiLambdaExpression lambda) {
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return false;
        PsiParameter parameter = parameters[0];
        PsiExpression expression = skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
        // x -> x
        if (ExpressionUtils.isReferenceTo(expression, parameter)) {
          return true;
        }
        if (expression instanceof PsiCallExpression) {
          PsiExpressionList list = ((PsiCallExpression)expression).getArgumentList();
          if (list == null) return false;
          PsiExpression[] args = list.getExpressions();
          if (args.length != 1 || !ExpressionUtils.isReferenceTo(args[0], parameter)) {
            return false;
          }
          // x -> new Integer(x)
          if (expression instanceof PsiNewExpression) {
            PsiJavaCodeReferenceElement ref = ((PsiNewExpression)expression).getClassReference();
            if (ref != null && ref.isReferenceTo(targetClass)) return true;
          }
          // x -> Integer.valueOf(x)
          if (expression instanceof PsiMethodCallExpression) {
            PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
            if (method != null && method.getContainingClass() == targetClass && method.getName().equals("valueOf")) return true;
          }
        }
      }
      return false;
    }
  }

  private static class ReplaceWithToArrayFix implements CallChainSimplification {
    private static final CallMatcher TO_ARRAY = instanceCall(JAVA_UTIL_STREAM_STREAM, "toArray");

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", "stream().toArray()", "toArray()");
    }

    @Override
    public boolean keepsStream() {
      return false;
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "collection.toArray()");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression toArrayCall) {
      if (!TO_ARRAY.test(toArrayCall)) return null;
      CommentTracker ct = new CommentTracker();
      String replacement = getReplacement(toArrayCall, ct);
      if (replacement == null) return null;
      PsiMethodCallExpression streamCall = getQualifierMethodCall(toArrayCall);
      if(streamCall == null) return null;
      PsiExpression collectionExpression = streamCall.getMethodExpression().getQualifierExpression();
      if(collectionExpression == null) return null;
      return ct.replaceAndRestoreComments(toArrayCall, ct.text(collectionExpression) + ".toArray(" + replacement + ")");
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(TO_ARRAY, methodCall -> {
        if (!COLLECTION_STREAM.test(getQualifierMethodCall(methodCall))) return null;
        return getReplacement(methodCall, new CommentTracker()) == null ? null : new ReplaceWithToArrayFix();
      });
    }

    @Nullable
    private static String getReplacement(PsiMethodCallExpression methodCall, CommentTracker ct) {
      PsiArrayType type = getArrayType(methodCall);
      if (type != null && type.equalsToText(JAVA_LANG_OBJECT + "[]")) {
        return "";
      }
      if (PsiUtil.isLanguageLevel11OrHigher(methodCall)) {
        return ct.text(methodCall.getArgumentList().getExpressions()[0]);
      }
      if (type != null) {
        return "new " + type.getCanonicalText().replaceFirst("\\[]", "[0]");
      }
      return null;
    }

    @Nullable
    private static PsiArrayType getArrayType(PsiMethodCallExpression call) {
      PsiType type = call.getType();
      if (!(type instanceof PsiArrayType candidate)) return null;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length == 0) return candidate;
      if (args.length != 1) return null;
      PsiExpression supplier = skipParenthesizedExprDown(args[0]);
      if (supplier instanceof PsiMethodReferenceExpression methodRef) {
        // like toArray(String[]::new)
        PsiTypeElement qualifierType = methodRef.getQualifierType();
        if (methodRef.isConstructor() && qualifierType != null && candidate.isAssignableFrom(qualifierType.getType())) {
          return candidate;
        }
      }
      else if (supplier instanceof PsiLambdaExpression lambda) {
        // like toArray(size -> new String[size])
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return null;
        PsiParameter sizeParameter = parameters[0];
        PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
        if (body instanceof PsiNewExpression newExpression) {
          PsiExpression[] dimensions = newExpression.getArrayDimensions();
          PsiType newExpressionType = newExpression.getType();
          if (dimensions.length != 0 &&
              ExpressionUtils.isReferenceTo(dimensions[0], sizeParameter) &&
              newExpressionType != null &&
              candidate.isAssignableFrom(newExpressionType)) {
            return candidate;
          }
        }
      }
      return null;
    }
  }

  private static class ReplaceWithElementIterationFix implements CallChainSimplification {
    private static final CallMatcher INT_STREAM_MAP =
      instanceCall(JAVA_UTIL_STREAM_INT_STREAM, "map", "mapToLong", "mapToDouble", "mapToObj")
        .parameterCount(1);
    private static final CallMatcher MIN_INT =
      anyOf(
        staticCall(JAVA_LANG_MATH, "min").parameterTypes("int", "int"),
        staticCall(JAVA_LANG_INTEGER, "min").parameterTypes("int", "int"));
    private static final String STREAM_SUFFIX = ".stream()";
    private static final String ARRAY_STREAM_PREFIX = "Arrays.stream(";

    private final @IntentionName String myName;

    ReplaceWithElementIterationFix(IndexedContainer container, String name) {
      PsiExpression qualifier = container.getQualifier();
      String qualifierText = PsiExpressionTrimRenderer.render(qualifier, 50);
      PsiType type = qualifier.getType();
      String replacement = type instanceof PsiArrayType ? ARRAY_STREAM_PREFIX + qualifierText + ")" : qualifierText + STREAM_SUFFIX;
      myName = CommonQuickFixBundle.message("fix.replace.x.with.y", "IntStream.range()." + name + "()", replacement);
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String getMessage() {
      return JavaBundle.message("simplify.stream.replace.with.element.iteration.fix.message");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression mapToObjCall) {
      Project project = mapToObjCall.getProject();
      PsiExpression mapper = ArrayUtil.getFirstElement(mapToObjCall.getArgumentList().getExpressions());
      LimitedContainer limitedContainer = extractContainer(getQualifierMethodCall(mapToObjCall), mapper);
      if (limitedContainer == null) return null;
      IndexedContainer container = limitedContainer.myContainer;
      PsiExpression limit = limitedContainer.myLimit;
      PsiExpression containerQualifier = container.getQualifier();
      PsiType type = containerQualifier.getType();
      PsiType elementType = container.getElementType();
      PsiType outElementType = StreamApiUtil.getStreamElementType(mapToObjCall.getType());
      if (type == null || elementType == null) return null;
      String replacement;
      if (type instanceof PsiArrayType) {
        replacement = JAVA_UTIL_ARRAYS + ".stream(" + containerQualifier.getText() + ")";
      }
      else {
        replacement = ParenthesesUtils.getText(containerQualifier, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".stream()";
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CommentTracker ct = new CommentTracker();
      if (limit != null) {
        replacement += ".limit(" + ct.text(limit) + ")";
      }
      if (mapper instanceof PsiMethodReferenceExpression) {
        mapper = LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)mapper, false, true);
      }
      if (!(mapper instanceof PsiLambdaExpression lambda)) return null;
      PsiParameter indexParameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
      PsiElement body = lambda.getBody();
      if (body == null || indexParameter == null) return null;
      VariableNameGenerator generator = new VariableNameGenerator(mapToObjCall, VariableKind.PARAMETER);
      if (containerQualifier instanceof PsiReferenceExpression) {
        generator.byCollectionName(((PsiReferenceExpression)containerQualifier).getReferenceName());
      }
      String name = generator.byType(elementType).byName("item", "element").generate(true);
      List<PsiReference> refs = new ArrayList<>(ReferencesSearch.search(indexParameter, new LocalSearchScope(body)).findAll());
      refs.sort(Comparator.comparingInt(ref -> ref.getElement().getTextOffset()));

      for (PsiReference ref : refs) {
        PsiExpression getExpression = container.extractGetExpressionFromIndex(tryCast(ref, PsiExpression.class));
        if (getExpression != null) {
          PsiElement result = ct.replace(getExpression, factory.createIdentifier(name));
          if (getExpression == body) {
            body = result;
          }
        }
      }
      PsiLambdaExpression newLambda = (PsiLambdaExpression)factory
        .createExpressionFromText("(" + elementType.getCanonicalText() + " " + name + ")->" + ct.text(body), mapToObjCall);
      PsiParameter newParameter = ArrayUtil.getFirstElement(newLambda.getParameterList().getParameters());
      replacement += StreamRefactoringUtil.generateMapOperation(newParameter, outElementType, newLambda.getBody());
      PsiElement result = ct.replaceAndRestoreComments(mapToObjCall, replacement);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      return CodeStyleManager.getInstance(project).reformat(result);
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(INT_STREAM_MAP, call -> {
        PsiExpression mapper = call.getArgumentList().getExpressions()[0];
        LimitedContainer limitedContainer = extractContainer(getQualifierMethodCall(call), mapper);
        if (limitedContainer == null) return null;
        return new ReplaceWithElementIterationFix(limitedContainer.myContainer, call.getMethodExpression().getReferenceName());
      });
    }

    @Contract("null, _ -> null")
    private static LimitedContainer extractContainer(PsiMethodCallExpression qualifierCall, PsiExpression mapper) {
      if (!INT_STREAM_RANGE.test(qualifierCall)) return null;
      PsiExpression[] rangeArgs = qualifierCall.getArgumentList().getExpressions();
      if (!ExpressionUtils.isZero(rangeArgs[0])) return null;
      PsiExpression bound = ExpressionUtils.resolveExpression(rangeArgs[1]);
      IndexedContainer container = IndexedContainer.fromLengthExpression(bound);
      PsiExpression limit = null;
      if (container == null) {
        if(bound instanceof PsiMethodCallExpression && MIN_INT.test((PsiMethodCallExpression)bound)) {
          PsiExpression[] args = ((PsiMethodCallExpression)bound).getArgumentList().getExpressions();
          container = IndexedContainer.fromLengthExpression(args[0]);
          if(container != null) {
            limit = args[1];
          } else {
            container = IndexedContainer.fromLengthExpression(args[1]);
            if(container != null) {
              limit = args[0];
            }
          }
        }
        if(container == null) return null;
      }
      if (!StreamApiUtil.isSupportedStreamElement(container.getElementType())) return null;
      LimitedContainer limitedContainer = new LimitedContainer(container, limit);
      if (mapper instanceof PsiMethodReferenceExpression && container.isGetMethodReference((PsiMethodReferenceExpression)mapper)) {
        return limitedContainer;
      }
      if (mapper instanceof PsiLambdaExpression lambda) {
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return null;
        PsiParameter indexParameter = parameters[0];
        PsiElement body = lambda.getBody();
        if (body == null) return null;
        Collection<PsiReference> refs = ReferencesSearch.search(indexParameter, new LocalSearchScope(body)).findAll();
        if (!refs.isEmpty() &&
            refs.stream().map(ref -> limitedContainer.myContainer.extractGetExpressionFromIndex(tryCast(ref, PsiExpression.class)))
              .allMatch(expression -> expression != null && !PsiUtil.isAccessedForWriting(expression))) {
          return limitedContainer;
        }
      }
      return null;
    }

    static class LimitedContainer {
      @NotNull final IndexedContainer myContainer;
      @Nullable final PsiExpression myLimit;

      LimitedContainer(@NotNull IndexedContainer container, @Nullable PsiExpression limit) {
        myContainer = container;
        myLimit = limit;
      }
    }
  }

  private static class RemoveBooleanIdentityFix implements CallChainSimplification {
    private final boolean myInvert;

    RemoveBooleanIdentityFix(boolean invert) {
      myInvert = invert;
    }

    @Override
    public String getName() {
      return JavaBundle.message("simplify.stream.remove.boolean.identity.fix.name");
    }

    @Override
    public String getMessage() {
      return JavaBundle.message("simplify.stream.remove.boolean.identity.fix.message");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifier = getQualifierMethodCall(call);
      if (qualifier == null) return null;
      String name = call.getMethodExpression().getReferenceName();
      if (name == null) return null;
      if (myInvert) {
        if (name.equals("allMatch")) {
          name = "noneMatch";
        }
        else if (name.equals("noneMatch")) {
          name = "allMatch";
        }
        else {
          return null;
        }
      }
      PsiExpression[] args = qualifier.getArgumentList().getExpressions();
      CommentTracker ct = new CommentTracker();
      if (args.length == 1) {
        PsiExpression arg = args[0];
        String replacement = adaptToPredicate(ct.markUnchanged(arg));
        if (replacement == null) return null;
        ct.replace(arg, replacement);
      }
      ExpressionUtils.bindCallTo(qualifier, name);
      return ct.replaceAndRestoreComments(call, qualifier);
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_MATCH, call -> {
        PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
        if (!STREAM_MAP.test(qualifierCall)) return null;
        PsiExpression qualifierArg = skipParenthesizedExprDown(qualifierCall.getArgumentList().getExpressions()[0]);
        PsiExpression predicate = call.getArgumentList().getExpressions()[0];
        boolean invert = false;
        if (!isBooleanIdentity(predicate)) {
          Boolean target = getBooleanEqualsTarget(predicate);
          if (target == null || (!target && "anyMatch".equals(call.getMethodExpression().getReferenceName()))) return null;
          invert = !target;
          if (qualifierArg instanceof PsiMethodReferenceExpression) {
            PsiMethod method = tryCast(((PsiMethodReferenceExpression)qualifierArg).resolve(), PsiMethod.class);
            if (method == null) return null;
            if (!PsiTypes.booleanType().equals(method.getReturnType()) && !NullableNotNullManager.isNotNull(method)) return null;
          }
          else if (!(qualifierArg instanceof PsiLambdaExpression) ||
                   DfaUtil.inferLambdaNullability((PsiLambdaExpression)qualifierArg) != Nullability.NOT_NULL) {
            return null;
          }
        }
        else {
          if (adaptToPredicate(qualifierArg) == null) return null;
        }
        return new RemoveBooleanIdentityFix(invert);
      });
    }

    private static boolean isBooleanIdentity(PsiExpression arg) {
      arg = skipParenthesizedExprDown(arg);
      if (FunctionalExpressionUtils.isFunctionalReferenceTo(arg, JAVA_LANG_BOOLEAN, PsiTypes.booleanType(),
                                                            "booleanValue", PsiType.EMPTY_ARRAY) ||
          FunctionalExpressionUtils.isFunctionalReferenceTo(arg, JAVA_LANG_BOOLEAN, null,
                                                            "valueOf", PsiTypes.booleanType())) {
        return true;
      }
      return arg instanceof PsiLambdaExpression && LambdaUtil.isIdentityLambda((PsiLambdaExpression)arg);
    }

    @Nullable
    private static Boolean getBooleanEqualsTarget(PsiExpression arg) {
      // Boolean.TRUE::equals or x -> Boolean.TRUE.equals(x)
      arg = skipParenthesizedExprDown(arg);
      PsiReferenceExpression qualifier = null;
      if (arg instanceof PsiMethodReferenceExpression methodRef) {
        if (!BOOLEAN_EQUALS.methodReferenceMatches(methodRef)) return null;
        qualifier = tryCast(methodRef.getQualifierExpression(), PsiReferenceExpression.class);
      }
      else if (arg instanceof PsiLambdaExpression lambda) {
        PsiParameter parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
        if (parameter == null) return null;
        PsiMethodCallExpression call = tryCast(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()), PsiMethodCallExpression.class);
        if (!BOOLEAN_EQUALS.test(call)) return null;
        if (!ExpressionUtils.isReferenceTo(call.getArgumentList().getExpressions()[0], parameter)) return null;
        qualifier = tryCast(call.getMethodExpression().getQualifierExpression(), PsiReferenceExpression.class);
      }
      if (qualifier == null) return null;
      PsiField field = tryCast(qualifier.resolve(), PsiField.class);
      if (field == null) return null;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null && JAVA_LANG_BOOLEAN.equals(containingClass.getQualifiedName())) {
        String name = field.getName();
        if ("TRUE".equals(name)) return Boolean.TRUE;
        if ("FALSE".equals(name)) return Boolean.FALSE;
      }
      return null;
    }

    /**
     * Returns the possible replacement of given expression to be used as j.u.f.Predicate,
     * or null if it cannot be used as Predicate.
     *
     * @param expression expression to test
     * @return yes, no or unsure
     */
    @Nullable
    private static String adaptToPredicate(PsiExpression expression) {
      if (expression == null) return null;
      String text = expression.getText();
      expression = skipParenthesizedExprDown(expression);
      if (expression == null) return null;
      if (expression instanceof PsiFunctionalExpression) return text;
      if (expression instanceof PsiConditionalExpression ternary) {
        String thenBranch = adaptToPredicate(ternary.getThenExpression());
        String elseBranch = adaptToPredicate(ternary.getElseExpression());
        if (thenBranch == null || elseBranch == null) return null;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
        PsiConditionalExpression copy = (PsiConditionalExpression)factory.createExpressionFromText(text, expression);
        Objects.requireNonNull(copy.getThenExpression()).replace(factory.createExpressionFromText(thenBranch, expression));
        Objects.requireNonNull(copy.getElseExpression()).replace(factory.createExpressionFromText(elseBranch, expression));
        return copy.getText();
      }
      String adapted = ParenthesesUtils.getText(expression, ParenthesesUtils.POSTFIX_PRECEDENCE) + "::apply";
      PsiClassType type = tryCast(expression.getType(), PsiClassType.class);
      if (type == null) return null;
      if (PsiTypesUtil.classNameEquals(type, JAVA_UTIL_FUNCTION_FUNCTION)) return adapted;
      PsiClass typeClass = type.resolve();
      // Disable inspection if type of expression is some subtype which defines its own 'apply' methods
      // to avoid possible resolution clashes
      if (typeClass == null) return null;
      PsiMethod[] methods = typeClass.findMethodsByName("apply", true);
      if (methods.length != 1 ||
          methods[0].getContainingClass() == null ||
          !JAVA_UTIL_FUNCTION_FUNCTION.equals(methods[0].getContainingClass().getQualifiedName())) {
        return null;
      }
      return adapted;
    }
  }

  private static class ReplaceStreamSupportWithCollectionStreamFix implements CallChainSimplification {
    private static final CallMatcher STREAM_SUPPORT = staticCall("java.util.stream.StreamSupport", "stream")
      .parameterTypes("java.util.Spliterator", "boolean");
    private static final CallMatcher SPLITERATOR =
      instanceCall(JAVA_UTIL_COLLECTION, "spliterator").parameterCount(0);
    private final String myQualifierText;
    private final boolean myParallel;

    ReplaceStreamSupportWithCollectionStreamFix(@NotNull PsiExpression qualifier, boolean parallel) {
      myQualifierText = PsiExpressionTrimRenderer.render(qualifier, 50);
      myParallel = parallel;
    }

    @Override
    public String getName() {
      return JavaBundle.message("simplify.stream.replace.support.with.collection.fix.name", myQualifierText, getMethodName());
    }

    @Override
    public String getMessage() {
      return JavaBundle.message("simplify.stream.replace.support.with.collection.fix.message", myQualifierText, getMethodName());
    }

    @NotNull
    private String getMethodName() {
      return myParallel ? "parallelStream" : "stream";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 2) return null;
      PsiMethodCallExpression spliteratorCall = tryCast(skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
      if (spliteratorCall == null) return null;
      ExpressionUtils.bindCallTo(spliteratorCall, getMethodName());
      CommentTracker ct = new CommentTracker();
      return ct.replace(call, spliteratorCall);
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_SUPPORT, call -> {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        PsiExpression parallel = args[1];
        if (!ExpressionUtils.isLiteral(parallel, Boolean.TRUE) && !ExpressionUtils.isLiteral(parallel, Boolean.FALSE)) return null;
        PsiMethodCallExpression spliterator = tryCast(skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
        if (!SPLITERATOR.test(spliterator)) return null;
        PsiExpression qualifier = skipParenthesizedExprDown(spliterator.getMethodExpression().getQualifierExpression());
        if (qualifier == null || (qualifier instanceof PsiThisExpression)) return null;
        return new ReplaceStreamSupportWithCollectionStreamFix(qualifier, ExpressionUtils.isLiteral(parallel, Boolean.TRUE));
      });
    }
  }

  static class RangeToArrayStreamFix implements CallChainSimplification {

    private final @NotNull String myReplacement;

    RangeToArrayStreamFix(@NotNull String replacement) {this.myReplacement = replacement;}

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Arrays.stream()");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "Arrays.stream()");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiMethodCallExpression mapCall = ExpressionUtils.getCallForQualifier(call);
      if(mapCall == null) return null;
      return new CommentTracker().replaceAndRestoreComments(mapCall, myReplacement);
    }

    @NotNull
    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(INT_STREAM_RANGE, call -> {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        PsiMethodCallExpression maybeMap = ExpressionUtils.getCallForQualifier(call);
        if (!STREAM_INT_MAP_TO_ALL.test(maybeMap)) return null;
        PsiExpression arg = maybeMap.getArgumentList().getExpressions()[0];
        PsiLambdaExpression lambda = tryCast(arg, PsiLambdaExpression.class);
        if (lambda == null) return null;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return null;
        PsiExpression lambdaExpr = tryCast(lambda.getBody(), PsiExpression.class);
        if (lambdaExpr == null) return null;
        PsiArrayAccessExpression arrayAccess = tryCast(skipParenthesizedExprDown(lambdaExpr), PsiArrayAccessExpression.class);
        if (arrayAccess == null) return null;
        PsiExpression index = arrayAccess.getIndexExpression();

        if (!ExpressionUtils.isReferenceTo(index, parameters[0])) return null;

        PsiExpression arrayExpr = arrayAccess.getArrayExpression();
        PsiArrayType arrayType = tryCast(arrayExpr.getType(), PsiArrayType.class);
        if (arrayType == null) return null;
        if (!StreamApiUtil.isSupportedStreamElement(arrayType.getComponentType())) return null;
        if (VariableAccessUtils.variableIsUsed(parameters[0], arrayExpr)) return null;

        PsiExpression leftBound = args[0];
        PsiExpression rightBound = args[1];
        return new RangeToArrayStreamFix(
          JAVA_UTIL_ARRAYS + ".stream(" + arrayExpr.getText() + "," + leftBound.getText() + "," + rightBound.getText() + ")");
      });
    }
  }

  static class NCopiesToGenerateStreamFix implements CallChainSimplification {

    private final @NotNull String myReplacement;

    NCopiesToGenerateStreamFix(@NotNull String replacement) {myReplacement = replacement;}

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Stream.generate()");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "Stream.generate()");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression streamCall) {
      PsiElement maybeMap = ExpressionUtils.getCallForQualifier(streamCall);
      if(maybeMap == null) return null;
      Project project = streamCall.getProject();
      PsiElement result = new CommentTracker().replaceAndRestoreComments(maybeMap, myReplacement);
      return JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
    }

    @NotNull
    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(COLLECTION_STREAM, call -> {
        PsiMethodCallExpression maybeNCopies = getQualifierMethodCall(call);
        if(!N_COPIES.test(maybeNCopies)) return null;
        PsiExpression[] nCopiesArgs = maybeNCopies.getArgumentList().getExpressions();
        PsiExpression count = nCopiesArgs[0];
        PsiExpression obj = nCopiesArgs[1];
        if(!ExpressionUtils.isSafelyRecomputableExpression(obj)) return null;

        PsiMethodCallExpression maybeMap = ExpressionUtils.getCallForQualifier(call);
        if(!STREAM_MAP_TO_ALL.test(maybeMap)) return null;
        PsiExpression arg = maybeMap.getArgumentList().getExpressions()[0];
        PsiLambdaExpression lambda = tryCast(skipParenthesizedExprDown(arg), PsiLambdaExpression.class);
        if(lambda == null) return null;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if(parameters.length != 1) return null;
        PsiParameter lambdaVar = parameters[0];
        PsiExpression body = tryCast(lambda.getBody(), PsiExpression.class);
        if (body == null || body.getType() == null) return null;
        String streamClass = getStreamClassName(maybeMap);
        if (VariableAccessUtils.variableIsUsed(lambdaVar, body)) return null;
        return new NCopiesToGenerateStreamFix(streamClass + ".generate(()->" + body.getText() + ").limit(" + count.getText() + ")");
      });
    }

    private static String getStreamClassName(@NotNull PsiMethodCallExpression call) {
      String name = MethodCallUtils.getMethodName(call);
      if (name == null) return JAVA_UTIL_STREAM_STREAM;
      return switch (name) {
        case "mapToInt" -> JAVA_UTIL_STREAM_INT_STREAM;
        case "mapToLong" -> JAVA_UTIL_STREAM_LONG_STREAM;
        case "mapToDouble" -> JAVA_UTIL_STREAM_DOUBLE_STREAM;
        default -> JAVA_UTIL_STREAM_STREAM;
      };
    }
  }

  static class SortedFirstToMinMaxFix implements CallChainSimplification {
    private final String myMethodName;
    private final String myReplacement;

    SortedFirstToMinMaxFix(String methodName, String replacement) {
      myMethodName = methodName;
      myReplacement = replacement;
    }


    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myMethodName + "()");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", myMethodName + "()");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      return new CommentTracker().replaceAndRestoreComments(call, myReplacement);
    }

    @NotNull
    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_FIND_FIRST, call -> {
        PsiMethodCallExpression maybeSorted = getQualifierMethodCall(call);
        if (!STREAM_SORTED.test(maybeSorted)) return null;
        PsiExpression[] args = maybeSorted.getArgumentList().getExpressions();
        PsiExpression qualifier = maybeSorted.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return null;

        final String comparator;
        boolean reversed = false;
        if (args.length == 1) {
          PsiExpression maybeComparator = skipParenthesizedExprDown(args[0]);
          if (maybeComparator instanceof PsiMethodCallExpression && COMPARATOR_REVERSED.test((PsiMethodCallExpression)maybeComparator)) {
            PsiExpression comparatorQualifier = ((PsiMethodCallExpression)maybeComparator).getMethodExpression().getQualifierExpression();
            if(comparatorQualifier == null) return null;
            comparator = comparatorQualifier.getText();
            reversed = true;
          } else {
            if (maybeComparator == null) return null;
            PsiType comparatorType = FunctionalExpressionUtils.getFunctionalExpressionType(maybeComparator);
            if (!InheritanceUtil.isInheritor(comparatorType, JAVA_UTIL_COMPARATOR)) return null;
            comparator = maybeComparator.getText();
          }
        } else return null;

        String methodName = reversed ? "max" : "min";
        return new SortedFirstToMinMaxFix(methodName, qualifier.getText() + "." + methodName + "(" + comparator + ")");
      });
    }
  }

  static class AnyMatchContainsFix implements CallChainSimplification {
    final SmartPsiElementPointer<PsiExpression> myValuePointer;

    AnyMatchContainsFix(@NotNull PsiExpression value) {
      myValuePointer = SmartPointerManager.createPointer(value);
    }

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "List.contains()");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "List.contains()");
    }

    @Override
    public boolean keepsStream() {
      return false;
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiExpression value = myValuePointer.getElement();
      if (value == null) return null;
      PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
      if (qualifierCall == null) return null;
      PsiExpressionList qualifierArgs = qualifierCall.getArgumentList();
      CommentTracker ct = new CommentTracker();
      PsiReferenceParameterList typeParameters = qualifierCall.getMethodExpression().getParameterList();
      String typeParametersText = typeParameters == null ? "" : ct.text(typeParameters);
      String factory;
      if (PsiUtil.isLanguageLevel9OrHigher(call) && MethodCallUtils.isVarArgCall(qualifierCall) &&
          ContainerUtil
            .and(qualifierArgs.getExpressions(), e -> NullabilityUtil.getExpressionNullability(e, true) == Nullability.NOT_NULL)) {
        factory = JAVA_UTIL_LIST + "." + typeParametersText + "of";
      } else {
        factory = JAVA_UTIL_ARRAYS + "." + typeParametersText + "asList";
      }
      PsiElement result = ct.replaceAndRestoreComments(call, factory +
                                                             ct.text(qualifierArgs) + ".contains(" + ct.text(value) + ")");
      return JavaCodeStyleManager.getInstance(result.getProject()).shortenClassReferences(result);
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_ANY_MATCH, call -> {
        PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
        if (!ARRAYS_STREAM.test(qualifierCall)) return null;
        PsiExpression arg = skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
        if (arg instanceof PsiMethodReferenceExpression) {
          PsiMethod method = tryCast(((PsiMethodReferenceExpression)arg).resolve(), PsiMethod.class);
          if (MethodUtils.isEquals(method)) {
            PsiExpression qualifier = ((PsiMethodReferenceExpression)arg).getQualifierExpression();
            if (qualifier != null) {
              return new AnyMatchContainsFix(qualifier);
            }
          }
        }
        if (arg instanceof PsiLambdaExpression lambda) {
          PsiParameter[] parameters = lambda.getParameterList().getParameters();
          if (parameters.length != 1) return null;
          PsiParameter parameter = parameters[0];
          PsiExpression lambdaBody = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
          EqualityCheck check = EqualityCheck.from(lambdaBody);
          if (check == null) return null;
          PsiExpression left = check.getLeft();
          PsiExpression right = check.getRight();
          if (ExpressionUtils.isReferenceTo(left, parameter) && ExpressionUtils.isSafelyRecomputableExpression(right)) {
            return new AnyMatchContainsFix(right);
          }
          if (ExpressionUtils.isReferenceTo(right, parameter) && ExpressionUtils.isSafelyRecomputableExpression(left)) {
            return new AnyMatchContainsFix(left);
          }
        }
        return null;
      });
    }
  }

  static class AllMatchContainsFix implements CallChainSimplification {

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "containsAll");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "containsAll");
    }

    @Override
    public boolean keepsStream() {
      return false;
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiExpression left = extractLeft(call);
      if (left == null) return null;
      PsiExpression right = extractRight(call);
      if (right == null) return null;
      CommentTracker ct = new CommentTracker();
      String replacement = ct.text(right) + ".containsAll(" + ct.text(left) + ")";
      return ct.replaceAndRestoreComments(call, replacement);
    }

    @Nullable
    private static PsiExpression extractRight(PsiMethodCallExpression allMatchCall) {
      PsiExpression arg = skipParenthesizedExprDown(allMatchCall.getArgumentList().getExpressions()[0]);
      if (arg instanceof PsiMethodReferenceExpression methodRef) {
        if (COLLECTION_CONTAINS.methodReferenceMatches(methodRef) &&
            !PsiMethodReferenceUtil.isStaticallyReferenced(methodRef) &&
            !ExpressionUtil.isEffectivelyUnqualified(methodRef)) {
          return methodRef.getQualifierExpression();
        }
      }
      else if (arg instanceof PsiLambdaExpression lambda) {
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length == 1) {
          PsiParameter parameter = parameters[0];
          PsiExpression expression = skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
          PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
          if (COLLECTION_CONTAINS.test(call) &&
              ExpressionUtils.isReferenceTo(call.getArgumentList().getExpressions()[0], parameter) &&
              !ExpressionUtil.isEffectivelyUnqualified(call.getMethodExpression())) {
            return call.getMethodExpression().getQualifierExpression();
          }
        }
      }
      return null;
    }

    @Nullable
    private static PsiExpression extractLeft(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
      if (!COLLECTION_STREAM.test(qualifierCall) || ExpressionUtil.isEffectivelyUnqualified(qualifierCall.getMethodExpression())) {
        return null;
      }
      return skipParenthesizedExprDown(qualifierCall.getMethodExpression().getQualifierExpression());
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(instanceCall(JAVA_UTIL_STREAM_STREAM, "allMatch").parameterCount(1), call -> {
        if (extractLeft(call) == null || extractRight(call) == null) return null;
        return new AllMatchContainsFix();
      });
    }
  }

  static class JoiningStringsFix implements CallChainSimplification {
    static final CallMatcher COLLECTOR_JOINING = staticCall(JAVA_UTIL_STREAM_COLLECTORS, "joining")
      .parameterCount(0);
    static final CallMatcher COLLECTOR_JOINING_DELIMITER = staticCall(JAVA_UTIL_STREAM_COLLECTORS, "joining")
      .parameterTypes(JAVA_LANG_CHAR_SEQUENCE);

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "String.join");
    }

    @Override
    public boolean keepsStream() {
      return false;
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "String.join");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiExpression delimiter = extractDelimiter(call);
      if (delimiter == null) return null;
      PsiMethodCallExpression qualifier = getQualifierMethodCall(call);
      if (qualifier == null) return null;
      CommentTracker ct = new CommentTracker();

      String argList;
      if (ARRAYS_STREAM.matches(qualifier)) {
        PsiElement[] args = qualifier.getArgumentList().getChildren();
        argList = StreamEx.of(args, 1, args.length - 1).map(ct::text).joining();
      }
      else if (COLLECTION_STREAM.matches(qualifier)) {
        PsiExpression collection = ExpressionUtils.getEffectiveQualifier(qualifier.getMethodExpression());
        if (collection == null) return null;
        argList = ct.text(collection);
      }
      else {
        return null;
      }
      String delimiterText = ct.text(delimiter);
      if (delimiterText.isEmpty()) {
        delimiterText = "\"\"";
      }
      return ct.replaceAndRestoreComments(call, JAVA_LANG_STRING + ".join(" + delimiterText + "," + argList + ")");
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_COLLECT, call -> {
        if (extractDelimiter(call) == null) return null;
        PsiMethodCallExpression qualifier = getQualifierMethodCall(call);
        if (qualifier == null) return null;
        if (ARRAYS_STREAM.matches(qualifier) ||
            (COLLECTION_STREAM.matches(qualifier) && ExpressionUtils.getEffectiveQualifier(qualifier.getMethodExpression()) != null)) {
          PsiType elementType = StreamApiUtil.getStreamElementType(qualifier.getType());
          if (InheritanceUtil.isInheritor(elementType, JAVA_LANG_CHAR_SEQUENCE)) {
            return new JoiningStringsFix();
          }
        }
        return null;
      });
    }

    @Nullable
    private static PsiExpression extractDelimiter(PsiMethodCallExpression call) {
      PsiMethodCallExpression collector =
        tryCast(skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiMethodCallExpression.class);
      PsiExpression delimiter;
      if (COLLECTOR_JOINING.test(collector)) {
        return new PsiEmptyExpressionImpl();
      }
      if (COLLECTOR_JOINING_DELIMITER.test(collector)) {
        delimiter = collector.getArgumentList().getExpressions()[0];
        return ExpressionUtils.isSafelyRecomputableExpression(delimiter) ? delimiter : null;
      }
      return null;
    }
  }


  static class ReplaceWithCollectorsJoiningFix implements CallChainSimplification {
    static final CallMatcher STRING_JOIN = staticCall(JAVA_LANG_STRING, "join").parameterCount(2);

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Collectors.joining");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "Collectors.joining");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      Context context = Context.from(call);
      if (context == null) return null;
      String collectorText = JAVA_UTIL_STREAM_COLLECTORS + ".joining(" + context.myDelimiterExpression.getText() + ")";
      new CommentTracker().replaceAndRestoreComments(context.myCollectorsToListCall, collectorText);
      CommentTracker ct = new CommentTracker();
      ct.markUnchanged(call);
      PsiElement result = ct.replaceAndRestoreComments(context.myStringJoinCall, call);
      return JavaCodeStyleManager.getInstance(result.getProject()).shortenClassReferences(result);
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_COLLECT, call -> {
        if (Context.from(call) == null) return null;
        return new ReplaceWithCollectorsJoiningFix();
      });
    }

    private static final class Context {
      final PsiMethodCallExpression myStringJoinCall;
      final PsiExpression myDelimiterExpression;
      final PsiExpression myCollectorsToListCall;

      private Context(PsiMethodCallExpression stringJoinCall,
                      PsiExpression delimiterExpression,
                      PsiExpression collectorsToListCall) {
        myStringJoinCall = stringJoinCall;
        myDelimiterExpression = delimiterExpression;
        myCollectorsToListCall = collectorsToListCall;
      }

      static Context from(PsiMethodCallExpression call) {
        PsiExpressionList argumentList = call.getArgumentList();
        if (argumentList.getExpressionCount() != 1) return null;
        PsiExpression firstArg = argumentList.getExpressions()[0];
        PsiMethodCallExpression argument = tryCast(skipParenthesizedExprDown(firstArg), PsiMethodCallExpression.class);
        if (!COLLECTORS_TO_LIST.matches(argument)) return null;
        PsiExpressionList arguments = tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiExpressionList.class);
        if (arguments == null) return null;
        if (arguments.getExpressionCount() != 2) return null;
        PsiMethodCallExpression maybeJoinCall = tryCast(PsiUtil.skipParenthesizedExprUp(arguments.getParent()), PsiMethodCallExpression.class);
        if (!STRING_JOIN.matches(maybeJoinCall)) return null;
        PsiExpression[] argumentExpressions = arguments.getExpressions();
        if (skipParenthesizedExprDown(argumentExpressions[1]) != call) return null;
        PsiExpression delimiter = argumentExpressions[0];
        if (delimiter == null) return null;
        if(!InheritanceUtil.isInheritor(delimiter.getType(), JAVA_LANG_CHAR_SEQUENCE)) return null;
        PsiMethodCallExpression stream = getQualifierMethodCall(call);
        if (stream == null) return null;
        PsiType elementType = StreamApiUtil.getStreamElementType(stream.getType());
        if (!InheritanceUtil.isInheritor(elementType, JAVA_LANG_CHAR_SEQUENCE)) return null;
        return new Context(maybeJoinCall, delimiter, argument);
      }
    }
  }

  static class EntrySetMapFix implements CallChainSimplification {
    private final @NotNull String myMapMethod;
    private final boolean myDeleteMap;
    private final @NotNull String @NotNull [] myNames;

    EntrySetMapFix(@NotNull String entryMethod, boolean deleteMap) {
      myMapMethod = entryMethod.equals("getKey") ? "keySet" : "values";
      myDeleteMap = deleteMap;
      myNames = myMapMethod.equals("keySet") ? new String[]{"k", "key"} : new String[]{"v", "value"};
    }

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "." + myMapMethod + "().stream()");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "." + myMapMethod + "().stream()");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
      if (qualifierCall == null) return null;
      if (myDeleteMap) {
        CommentTracker ct = new CommentTracker();
        call = (PsiMethodCallExpression)ct.replaceAndRestoreComments(call, qualifierCall);
      }
      PsiMethodCallExpression result = call;
      while (call != null) {
        if (STREAM_MAP_TO_ALL.test(call) || STREAM_FILTER.test(call)) {
          PsiExpression arg = skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]);
          if (arg instanceof PsiLambdaExpression) {
            updateLambda((PsiLambdaExpression)arg);
          }
          else if (arg instanceof PsiMethodReferenceExpression) {
            PsiType type = LambdaUtil.getFunctionalInterfaceReturnType((PsiFunctionalExpression)arg);
            String name = new VariableNameGenerator(arg, VariableKind.PARAMETER).byType(type).byName(myNames).generate(false);
            new CommentTracker().replaceAndRestoreComments(arg, name + "->" + name);
          }
        }
        call = getQualifierMethodCall(call);
        if (MAP_ENTRY_SET.test(call)) {
          ExpressionUtils.bindCallTo(call, myMapMethod);
          break;
        }
      }
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      return result;
    }

    private void updateLambda(PsiLambdaExpression lambda) {
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return;
      PsiParameter parameter = parameters[0];
      PsiType type = PsiUtil.substituteTypeParameter(parameter.getType(), JAVA_UTIL_MAP_ENTRY, 1, true);
      PsiElement body = lambda.getBody();
      if (body == null) return;
      List<PsiMethodCallExpression> calls = new ArrayList<>();
      PsiLocalVariable declaration = null;
      for (PsiReferenceExpression ref : VariableAccessUtils.getVariableReferences(parameter, body)) {
        PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(ref);
        if (call != null) {
          calls.add(call);
          if (declaration == null) {
            PsiLocalVariable var = tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiLocalVariable.class);
            if (var != null && var.getParent() instanceof PsiDeclarationStatement && var.getParent().getParent() == body) {
              declaration = var;
            }
          }
        }
      }
      String name = declaration == null ? null : declaration.getName();
      if (name == null) {
        name = new VariableNameGenerator(lambda, VariableKind.PARAMETER).byType(type).byName(myNames).generate(false);
      }
      for (PsiMethodCallExpression call : calls) {
        PsiElement result = new CommentTracker().replaceAndRestoreComments(call, name);
        if (call == body) {
          body = result;
        }
      }
      if (declaration != null) {
        new CommentTracker().deleteAndRestoreComments(declaration);
      }
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(lambda, name + "->" + ct.text(body));
    }

    public static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_MAP_TO_ALL, call -> {
        PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
        List<String> methods = new ArrayList<>(Arrays.asList("getKey", "getValue"));
        while (STREAM_FILTER.test(qualifierCall)) {
          String methodName = getSingleCalledMethodName(qualifierCall.getArgumentList().getExpressions()[0]);
          if (methodName == null || !methods.contains(methodName)) return null;
          methods = Collections.singletonList(methodName);
          qualifierCall = getQualifierMethodCall(qualifierCall);
        }
        if (!COLLECTION_STREAM.test(qualifierCall)) return null;
        PsiMethodCallExpression qualifierQualifierCall = getQualifierMethodCall(qualifierCall);
        if (!MAP_ENTRY_SET.test(qualifierQualifierCall)) return null;
        PsiExpression arg = call.getArgumentList().getExpressions()[0];
        for (String method : methods) {
          if (FunctionalExpressionUtils.isFunctionalReferenceTo(arg, JAVA_UTIL_MAP_ENTRY, null, method)) {
            return new EntrySetMapFix(method, "map".equals(call.getMethodExpression().getReferenceName()));
          }
        }
        String methodName = getSingleCalledMethodName(arg);
        if (methodName != null && methods.contains(methodName)) {
          return new EntrySetMapFix(methodName, false);
        }
        return null;
      });
    }

    @Nullable
    private static String getSingleCalledMethodName(PsiExpression arg) {
      PsiLambdaExpression lambda = tryCast(skipParenthesizedExprDown(arg), PsiLambdaExpression.class);
      if (lambda == null) return null;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return null;
      PsiParameter parameter = parameters[0];
      PsiElement body = lambda.getBody();
      if (body == null) return null;
      String methodName = null;
      for (PsiReferenceExpression ref : VariableAccessUtils.getVariableReferences(parameter, body)) {
        PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(ref);
        if (call == null || !call.getArgumentList().isEmpty()) return null;
        String name = call.getMethodExpression().getReferenceName();
        if (name == null || (methodName != null && !methodName.equals(name))) return null;
        methodName = name;
      }
      return methodName;
    }
  }

  static class CollectorToListSize implements CallChainSimplification {
    private final boolean mySize;

    CollectorToListSize(boolean size) {
      mySize = size;
    }

    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "count()");
    }

    @Override
    public String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "count()");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiExpression streamExpression = call.getMethodExpression().getQualifierExpression();
      if (streamExpression == null) return null;
      PsiMethodCallExpression sizeCheck = ExpressionUtils.getCallForQualifier(call);
      if (sizeCheck == null) return null;
      CommentTracker ct = new CommentTracker();
      String replacement;
      if (mySize) {
        boolean addCast = true;
        PsiType expectedType = ExpectedTypeUtils.findExpectedType(sizeCheck, false);
        if (PsiTypes.longType().equals(expectedType)) addCast = false;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(sizeCheck.getParent());
        if (parent instanceof PsiBinaryExpression &&
            ComparisonUtils.isComparisonOperation(((PsiBinaryExpression)parent).getOperationTokenType())) {
          addCast = false;
        }
        replacement = (addCast ? "(int)" : "") + ct.text(streamExpression) + ".count()";
      } else {
        replacement = ct.text(streamExpression) + ".count() == 0";
      }
      return ct.replaceAndRestoreComments(sizeCheck, replacement);
    }

    public static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_COLLECT, call -> {
        if (!COLLECTORS_TO_LIST.matches(call.getArgumentList().getExpressions()[0])) return null;
        PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(call);
        if (!COLLECTION_SIZE_CHECK.test(nextCall)) return null;
        return new CollectorToListSize("size".equals(nextCall.getMethodExpression().getReferenceName()));
      });
    }
  }

  private static class IterateTakeWhileFix implements CallChainSimplification {
    @Override
    public String getName() {
      return JavaBundle.message("simplify.stream.inspection.iterate.take.while.fix.name");
    }

    @Override
    public String getMessage() {
      return JavaBundle.message("simplify.stream.inspection.iterate.take.while.fix.message");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
      if (!STREAM_ITERATE.test(qualifierCall)) return null;
      CommentTracker ct = new CommentTracker();
      PsiExpression predicate = call.getArgumentList().getExpressions()[0];
      PsiExpressionList argList = qualifierCall.getArgumentList();
      argList.addAfter(ct.markUnchanged(predicate), argList.getExpressions()[0]);
      return ct.replaceAndRestoreComments(call, qualifierCall);
    }

    public static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_TAKE_WHILE, call -> {
        PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
        if (STREAM_ITERATE.test(qualifierCall)) {
          return new IterateTakeWhileFix();
        }
        return null;
      });
    }
  }

  private static class FilterAndMapUseSameMethodChainFix implements CallChainSimplification {

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("simplify.stream.swap.filter.and.map.fix.name");
    }

    @Override
    @NotNull
    public @InspectionMessage String getMessage() {
      return JavaBundle.message("simplify.stream.swap.filter.and.map.fix.message");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression mapCall) {
      final PsiMethodCallExpression filterCall = getQualifierMethodCall(mapCall);
      if (filterCall == null) return null;
      final PsiExpressionList mapArgList = mapCall.getArgumentList();
      final PsiExpressionList filterArgList = filterCall.getArgumentList();
      final PsiElement tempMapArgList = mapArgList.copy();
      final VariableNameGenerator generator = new VariableNameGenerator(filterCall, VariableKind.PARAMETER);
      final PsiMethodCallExpression candidateToRemove = findCandidateToRemove(mapCall);
      if (candidateToRemove == null) return null;
      final PsiType type = candidateToRemove.getMethodExpression().getType();
      if (type == null) return null;
      final String newName = generator.byExpression(candidateToRemove).byType(type).generate(false);
      final PsiExpression filterArg = skipParenthesizedExprDown(ArrayUtil.getFirstElement(filterCall.getArgumentList().getExpressions()));
      if (filterArg == null) return null;
      final PsiLambdaExpression filterLambda = tryCast(filterArg, PsiLambdaExpression.class);
      if (filterLambda == null) return null;
      final CommentTracker ct = new CommentTracker();
      final PsiLambdaExpression expression = (PsiLambdaExpression)JavaPsiFacade.getElementFactory(filterLambda.getProject())
        .createExpressionFromText(newName + "->{}", filterLambda);
      ct.replace(candidateToRemove, newName);
      ct.replace(filterLambda.getParameterList(), expression.getParameterList());
      ct.replace(mapArgList, filterArgList);
      ct.replace(filterArgList, tempMapArgList);
      ExpressionUtils.bindCallTo(mapCall, "filter");
      ExpressionUtils.bindCallTo(filterCall, "map");
      return mapCall;
    }

    public static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_MAP, mapCall -> {
        if (mapCall.getTypeArguments().length != 0) return null;
        final PsiMethodCallExpression filterCall = getQualifierMethodCall(mapCall);
        if (filterCall == null || filterCall.getTypeArguments().length != 0) return null;
        final PsiExpression mapArg = skipParenthesizedExprDown(mapCall.getArgumentList().getExpressions()[0]);
        final PsiMethodReferenceExpression mapMethodRef = tryCast(mapArg, PsiMethodReferenceExpression.class);
        if (mapMethodRef != null) {
          final PsiMethod resolvedCandidateToRemove = findAndResolveCandidateToRemove(mapCall);
          if (resolvedCandidateToRemove == null) return null;
          boolean isStatic = resolvedCandidateToRemove.hasModifierProperty(PsiModifier.STATIC);
          boolean instanceBound = !isStatic && !PsiMethodReferenceUtil.isStaticallyReferenced(mapMethodRef);
          if (isStatic || instanceBound) return null;
          final PsiManager manager = resolvedCandidateToRemove.getManager();
          if (!manager.areElementsEquivalent(resolvedCandidateToRemove, mapMethodRef.resolve())) return null;
          return new FilterAndMapUseSameMethodChainFix();
        }
        final PsiLambdaExpression lambda = tryCast(mapArg, PsiLambdaExpression.class);
        if (lambda == null) return null;
        final PsiElement body = skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
        final PsiMethodCallExpression calledMethodInMap = findSingleCalledMethod(mapArg);
        if (calledMethodInMap == null || body != calledMethodInMap) return null;
        final PsiMethod resolvedCandidateToRemove = findAndResolveCandidateToRemove(mapCall);
        if (resolvedCandidateToRemove == null) return null;
        final PsiManager manager = resolvedCandidateToRemove.getManager();
        if (manager.areElementsEquivalent(resolvedCandidateToRemove, calledMethodInMap.resolveMethod())) {
          return new FilterAndMapUseSameMethodChainFix();
        }
        return null;
      });
    }

    private static PsiMethod findAndResolveCandidateToRemove(PsiMethodCallExpression mapCall) {
      final PsiMethodCallExpression candidateToRemove = findCandidateToRemove(mapCall);
      if (candidateToRemove == null) return null;
      final PsiType type = StreamApiUtil.getStreamElementType(mapCall.getType());
      if (type == null) return null;
      final PsiMethod resolvedCandidateToRemove = candidateToRemove.resolveMethod();
      if (resolvedCandidateToRemove == null) return null;
      final PsiType candidateToRemoveType = resolvedCandidateToRemove.getReturnType();
      if (candidateToRemoveType == null) return null;
      if (!type.equals(candidateToRemoveType) && !candidateToRemoveType.equals(PsiPrimitiveType.getUnboxedType(type))) return null;
      return resolvedCandidateToRemove;
    }

    private static PsiMethodCallExpression findCandidateToRemove(PsiMethodCallExpression mapCall) {
      final PsiMethodCallExpression filterCall = getQualifierMethodCall(mapCall);
      if (!STREAM_FILTER.test(filterCall)) return null;
      return findSingleCalledMethod(filterCall.getArgumentList().getExpressions()[0]);
    }

    private static PsiMethodCallExpression findSingleCalledMethod(PsiExpression arg) {
      final PsiLambdaExpression lambda = tryCast(skipParenthesizedExprDown(arg), PsiLambdaExpression.class);
      if (lambda == null) return null;
      final PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 1) return null;
      final PsiParameter parameter = parameters[0];
      final PsiElement body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      final List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(parameter, body);
      if (references.size() != 1) return null;
      final PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(references.get(0));
      if (call == null || !call.getArgumentList().isEmpty()) return null;
      return call;
    }
  }

  private static class ReplaceWithOrElseThrowFix implements CallChainSimplification {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "orElseThrow");
    }

    @Override
    @NotNull
    public @InspectionMessage String getMessage() {
      return CommonQuickFixBundle.message("fix.can.replace.with.x", "orElseThrow");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression orElseGetCall) {
      final PsiLambdaExpression lambda =
        tryCast(skipParenthesizedExprDown(orElseGetCall.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
      final PsiExpression thrownException = getThrownException(lambda);
      if (thrownException == null) return null;
      final PsiElement body = lambda.getBody();
      assert body != null;
      ExpressionUtils.bindCallTo(orElseGetCall, "orElseThrow");
      new CommentTracker().replaceAndRestoreComments(body, thrownException);
      LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambda);
      return orElseGetCall;
    }

    public static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(OPTIONAL_OR_ELSE_GET, call -> {
        final PsiLambdaExpression lambda =
            tryCast(skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
        final PsiExpression thrownException = getThrownException(lambda);
        if (thrownException == null) return null;
        return new ReplaceWithOrElseThrowFix();
      });
    }

    @Nullable
    private static PsiExpression getThrownException(@Nullable PsiLambdaExpression lambda) {
      if (lambda == null || !lambda.getParameterList().isEmpty()) return null;
      final PsiCodeBlock body = tryCast(lambda.getBody(), PsiCodeBlock.class);
      if (body == null) return null;
      final PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) return null;
      final PsiThrowStatement throwStatement = tryCast(statements[0], PsiThrowStatement.class);
      if (throwStatement == null) return null;
      return throwStatement.getException();
    }
  }
}
