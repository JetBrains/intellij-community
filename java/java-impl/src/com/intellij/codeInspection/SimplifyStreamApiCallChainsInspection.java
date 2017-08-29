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
package com.intellij.codeInspection;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
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
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.*;
import static com.siyeh.ig.psiutils.MethodCallUtils.getQualifierMethodCall;

/**
 * @author Pavel.Dolgov
 * @author Tagir Valeev
 */
public class SimplifyStreamApiCallChainsInspection extends BaseJavaBatchLocalInspectionTool {
  private static final CallMatcher COLLECTION_STREAM =
    instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "stream").parameterCount(0);
  private static final CallMatcher OPTIONAL_STREAM =
    instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "stream").parameterCount(0);
  private static final CallMatcher STREAM_FIND =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "findFirst", "findAny").parameterCount(0);
  private static final CallMatcher STREAM_FILTER =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "filter").parameterTypes(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE);
  private static final CallMatcher STREAM_FIND_FIRST =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "findFirst").parameterCount(0);
  private static final CallMatcher STREAM_SORTED =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "sorted");
  private static final CallMatcher STREAM_MAP =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "map").parameterTypes(CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION);
  private static final CallMatcher BASE_STREAM_MAP =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "map").parameterCount(1);
  private static final CallMatcher STREAM_ANY_MATCH =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "anyMatch").parameterCount(1);
  private static final CallMatcher STREAM_RANGE =
    staticCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "range").parameterCount(2);
  private static final CallMatcher STREAM_NONE_MATCH =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "noneMatch").parameterCount(1);
  private static final CallMatcher STREAM_ALL_MATCH =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "allMatch").parameterCount(1);
  private static final CallMatcher STREAM_COLLECT =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "collect").parameterCount(1);
  private static final CallMatcher OPTIONAL_IS_PRESENT =
    instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "isPresent").parameterCount(0);
  private static final CallMatcher BOOLEAN_EQUALS =
    instanceCall(CommonClassNames.JAVA_LANG_BOOLEAN, "equals").parameterCount(1);
  private static final CallMatcher STREAM_OF =
    staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "of").parameterTypes("T");

  private static final CallMatcher N_COPIES =
    staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "nCopies").parameterTypes("int", "T");
  private static final CallMatcher COMPARATOR_REVERSED =
    instanceCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "reversed").parameterCount(0);

  private static final CallMatcher STREAM_INT_MAP_TO_OBJ =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "mapToObj").parameterTypes("java.util.function.IntFunction");
  private static final CallMatcher STREAM_INT_MAP =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "map").parameterTypes("java.util.function.IntUnaryOperator");
  private static final CallMatcher STREAM_INT_TO_DOUBLE_MAP =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "mapToDouble").parameterTypes("java.util.function.IntToDoubleFunction");
  private static final CallMatcher STREAM_INT_TO_LONG_MAP =
    instanceCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "mapToLong").parameterTypes("java.util.function.IntToLongFunction");

  private static final CallMatcher STREAM_INT_MAP_TO_ALL = anyOf(
    STREAM_INT_MAP,
    STREAM_INT_MAP_TO_OBJ,
    STREAM_INT_TO_LONG_MAP,
    STREAM_INT_TO_DOUBLE_MAP
  );

  private static final CallMatcher STREAM_MATCH = anyOf(STREAM_ANY_MATCH, STREAM_NONE_MATCH, STREAM_ALL_MATCH);

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
    SortedFirstToMinMaxFix.handler()
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
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
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
        if(parameter instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression collectorCall = (PsiMethodCallExpression)parameter;
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
                                           "Can be replaced with '" + replacement + "' constructor",
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

  public static PsiElement simplifyStreamExpressions(PsiElement element) {
    boolean replaced = true;
    while(replaced) {
      replaced = false;
      Map<PsiMethodCallExpression, CallChainSimplification> callToSimplification =
        StreamEx.ofTree(element, e -> StreamEx.of(e.getChildren()))
          .select(PsiMethodCallExpression.class)
          .mapToEntry(CALL_TO_FIX_MAPPER::mapFirst)
          .nonNullValues()
          .toMap();
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
    return staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, name).parameterCount(parameterCount);
  }

  static boolean isParentNegated(PsiMethodCallExpression methodCall) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(methodCall.getParent());
    return parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
  }

  static boolean isArgumentLambdaNegated(PsiMethodCallExpression methodCall) {
    PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
    if(expressions.length != 1) return false;
    PsiExpression arg = expressions[0];
    if(!(arg instanceof PsiLambdaExpression)) return false;
    PsiElement body = ((PsiLambdaExpression)arg).getBody();
    return body instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)body);
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
    String getName();
    void applyFix(@NotNull Project project, PsiElement element);
  }

  interface CallChainSimplification extends CallChainFix {
    String getMessage();

    default void applyFix(@NotNull Project project, PsiElement element) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if (call != null) {
        simplify(call);
      }
    }

    PsiElement simplify(PsiMethodCallExpression element);
  }

  private static class SimplifyCallChainFix implements LocalQuickFix {
    private final CallChainFix myFix;

    SimplifyCallChainFix(CallChainFix fix) {
      myFix = fix;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myFix.getName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Simplify stream call chain";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myFix.applyFix(project, descriptor.getStartElement());
    }
  }

  private static class ReplaceCollectionStreamFix implements CallChainSimplification {
    private static final CallMatcher EMPTY_LIST =
      staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptyList").parameterCount(0);
    private static final CallMatcher EMPTY_SET =
      staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "emptySet").parameterCount(0);
    private static final CallMatcher SINGLETON_LIST =
      staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singletonList").parameterCount(1);
    private static final CallMatcher SINGLETON =
      staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singleton").parameterCount(1);
    private static final CallMatcher AS_LIST = staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "asList").parameterCount(1);
    private static final CallMatcher ENUMSET_OF = staticCall("java.util.EnumSet", "of");

    private static final CallMapper<ReplaceCollectionStreamFix> COLLECTION_TO_STREAM_MAPPER = new CallMapper<ReplaceCollectionStreamFix>()
      .register(EMPTY_LIST,
                new ReplaceCollectionStreamFix("Collections.emptyList()", CommonClassNames.JAVA_UTIL_STREAM_STREAM, EMPTY_METHOD))
      .register(EMPTY_SET,
                new ReplaceCollectionStreamFix("Collections.emptySet()", CommonClassNames.JAVA_UTIL_STREAM_STREAM, EMPTY_METHOD))
      .register(SINGLETON, call -> hasSingleArrayArgument(call)
                                   ? null : new ReplaceSingletonWithStreamOfFix("Collections.singleton()"))
      .register(SINGLETON_LIST, call -> hasSingleArrayArgument(call)
                                        ? null : new ReplaceSingletonWithStreamOfFix("Collections.singletonList()"))
      .register(AS_LIST, call -> hasSingleArrayArgument(call)
                                 ? new ReplaceCollectionStreamFix("Arrays.asList()", CommonClassNames.JAVA_UTIL_ARRAYS, STREAM_METHOD)
                                 : new ReplaceCollectionStreamFix("Arrays.asList()", CommonClassNames.JAVA_UTIL_STREAM_STREAM, OF_METHOD))
      .register(ENUMSET_OF, call ->
        isEnumSetReplaceableWithStream(call) ? new ReplaceCollectionStreamFix("EnumSet.of()", CommonClassNames.JAVA_UTIL_STREAM_STREAM,
                                                                              OF_METHOD) : null);

    private final String myClassName;
    private final String myMethodName;
    private final String myQualifierCall;

    private ReplaceCollectionStreamFix(String qualifierCall, String className, String methodName) {
      myQualifierCall = qualifierCall;
      myClassName = className;
      myMethodName = methodName;
    }

    @NotNull
    public String getMessage() {
      return myQualifierCall + ".stream() can be replaced with " + ClassUtil.extractClassName(myClassName) + "." + myMethodName + "()";
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace " + myQualifierCall + ".stream() with " + ClassUtil.extractClassName(myClassName) + "." + myMethodName + "()";
    }

    @Nullable
    protected String getTypeParameter(@NotNull PsiMethodCallExpression qualifierCall) {
      PsiType[] parameters = qualifierCall.getMethodExpression().getTypeParameters();
      return parameters.length == 1 ? parameters[0].getCanonicalText() : null;
    }

    @Nullable
    @Override
    public PsiElement simplify(PsiMethodCallExpression streamCall) {
      PsiMethodCallExpression collectionCall = getQualifierMethodCall(streamCall);
      if (collectionCall == null) return null;
      streamCall.getArgumentList().replace(collectionCall.getArgumentList());
      String typeParameter = getTypeParameter(collectionCall);
      String replacement;
      if (typeParameter != null) {
        replacement = myClassName + ".<" + typeParameter + ">" + myMethodName;
      }
      else {
        replacement = myClassName + "." + myMethodName;
      }
      Project project = streamCall.getProject();
      PsiExpression newMethodExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, streamCall);
      return JavaCodeStyleManager.getInstance(project).shortenClassReferences(streamCall.getMethodExpression().replace(newMethodExpression));
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
        PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiReferenceExpression.class);
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

  private static class ReplaceSingletonWithStreamOfFix extends ReplaceCollectionStreamFix {
    private ReplaceSingletonWithStreamOfFix(String qualifierCall) {
      super(qualifierCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, OF_METHOD);
    }

    @Nullable
    @Override
    protected String getTypeParameter(@NotNull PsiMethodCallExpression qualifierCall) {
      String typeParameter = super.getTypeParameter(qualifierCall);
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
      instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "forEach", "forEachOrdered").parameterCount(1);

    private final String myStreamMethod;
    private final String myReplacementMethod;
    private final boolean myChangeSemantics;

    public ReplaceForEachMethodFix(String streamMethod, String replacementMethod, boolean changeSemantics) {
      myStreamMethod = streamMethod;
      myReplacementMethod = replacementMethod;
      myChangeSemantics = changeSemantics;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace 'stream()." + myStreamMethod +
             "()' with '" + myReplacementMethod + "()'" +
             (myChangeSemantics ? " (may change semantics)" : "");
    }

    @NotNull
    public String getMessage() {
      return "The 'stream()." + myStreamMethod +
             "()' chain can be replaced with '" + myReplacementMethod + "()'" +
             (myChangeSemantics ? " (may change semantics)" : "");
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression streamMethodCall) {
      PsiMethodCallExpression collectionStreamCall = getQualifierMethodCall(streamMethodCall);
      if (collectionStreamCall == null) return null;
      PsiExpression collectionExpression = collectionStreamCall.getMethodExpression().getQualifierExpression();
      if (collectionExpression == null) return null;
      collectionStreamCall.replace(collectionExpression);
      ExpressionUtils.bindCallTo(streamMethodCall, myReplacementMethod);
      return streamMethodCall;
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_FOR_EACH, call -> {
        PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
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
      handler("summingDouble", 1, "mapToDouble({0}).sum()", false));

    private final String myCollector;
    private final String myStreamSequence;
    private final String myStreamSequenceStripped;
    private final boolean myChangeSemantics;

    public ReplaceCollectorFix(String collector, String streamSequence, boolean changeSemantics) {
      myCollector = collector;
      myStreamSequence = streamSequence;
      myStreamSequenceStripped = streamSequence.replaceAll("\\([^)]+\\)", "()");
      myChangeSemantics = changeSemantics;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace 'collect(" + myCollector +
             "())' with '" + myStreamSequenceStripped + "'" +
             (myChangeSemantics ? " (may change semantics when result is null)" : "");
    }

    @Override
    public void applyFix(@NotNull Project project, PsiElement element) {
      if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression collectCall = (PsiMethodCallExpression)element;
        PsiExpression qualifierExpression = collectCall.getMethodExpression().getQualifierExpression();
        if (qualifierExpression != null) {
          PsiElement parameter = collectCall.getArgumentList().getExpressions()[0];
          if (parameter instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression collectorCall = (PsiMethodCallExpression)parameter;
            PsiExpression[] collectorArgs = collectorCall.getArgumentList().getExpressions();
            String result = MessageFormat.format(myStreamSequence, Arrays.stream(collectorArgs).map(PsiExpression::getText).toArray());
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiExpression replacement = factory.createExpressionFromText(qualifierExpression.getText() + "." + result, collectCall);
            addBoxingIfNecessary(factory, collectCall.replace(replacement));
          }
        }
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
              RedundantCastUtil.removeCast((PsiTypeCastExpression)cast);
            }
          }
        }
      }
    }

    @NotNull
    public String getMessage() {
      return "The 'collect(" + myCollector +
             "())' call can be replaced with '" + myStreamSequenceStripped + "'" +
             (myChangeSemantics ? " (may change semantics when result is null)" : "");
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
      return "Replace 'filter()." + myFindMethodName + "().isPresent()' with 'anyMatch()'";
    }

    @Override
    public void applyFix(@NotNull Project project, PsiElement element) {
      if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression isPresentCall = (PsiMethodCallExpression)element;
        PsiExpression isPresentQualifier = isPresentCall.getMethodExpression().getQualifierExpression();
        if(isPresentQualifier instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression findCall = (PsiMethodCallExpression)isPresentQualifier;
          PsiExpression findQualifier = findCall.getMethodExpression().getQualifierExpression();
          if(findQualifier instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression filterCall = (PsiMethodCallExpression)findQualifier;
            PsiElement replacement = element.replace(filterCall);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiElement filterName = ((PsiMethodCallExpression)replacement).getMethodExpression().getReferenceNameElement();
            LOG.assertTrue(filterName != null);
            filterName.replace(factory.createIdentifier(ANY_MATCH_METHOD));
          }
        }
      }
    }

    @NotNull
    public String getMessage() {
      return "The 'filter()." + myFindMethodName + "().isPresent()' chain can be replaced with 'anyMatch()'";
    }
  }

  private static class SimplifyMatchNegationFix implements CallChainSimplification {
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
      return "Replace "+myFrom+" with "+myTo+"(...)";
    }

    public String getMessage() {
      return myFrom+" can be replaced with "+myTo+"(...)";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression methodCall) {
      String from = methodCall.getMethodExpression().getReferenceName();
      if (from == null) return null;
      boolean removeParentNegation;
      boolean removeLambdaNegation;
      switch (from) {
        case ALL_MATCH_METHOD:
          removeLambdaNegation = true;
          removeParentNegation = myTo.equals(ANY_MATCH_METHOD);
          break;
        case ANY_MATCH_METHOD:
          removeParentNegation = true;
          removeLambdaNegation = myTo.equals(ALL_MATCH_METHOD);
          break;
        case NONE_MATCH_METHOD:
          removeParentNegation = myTo.equals(ANY_MATCH_METHOD);
          removeLambdaNegation = myTo.equals(ALL_MATCH_METHOD);
          break;
        default:
          return null;
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
      .register(collectorMatcher("toList", 0), CommonClassNames.JAVA_UTIL_ARRAY_LIST)
      .register(collectorMatcher("toSet", 0), CommonClassNames.JAVA_UTIL_HASH_SET)
      .register(collectorMatcher("toCollection", 1), SimplifyCollectionCreationFix::getCollectionClass);

    private String myReplacement;

    public SimplifyCollectionCreationFix(String replacement) {
      myReplacement = replacement;
    }

    @Override
    public String getName() {
      return "Replace with '"+myReplacement+"' constructor";
    }

    @Override
    public void applyFix(@NotNull Project project, PsiElement element) {
      if(!(element instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression collectCall = (PsiMethodCallExpression)element;
      PsiType type = collectCall.getType();
      PsiClass resolvedType = PsiUtil.resolveClassInClassTypeOnly(type);
      if(resolvedType == null || resolvedType instanceof PsiTypeParameter) return;
      PsiMethodCallExpression streamCall = getQualifierMethodCall(collectCall);
      if(streamCall == null) return;
      PsiExpression collectionExpression = streamCall.getMethodExpression().getQualifierExpression();
      if(collectionExpression == null) return;
      String typeText = type.getCanonicalText();
      if(CommonClassNames.JAVA_UTIL_LIST.equals(resolvedType.getQualifiedName()) ||
         CommonClassNames.JAVA_UTIL_SET.equals(resolvedType.getQualifiedName())) {
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
        PsiDiamondTypeUtil.replaceExplicitWithDiamond(classReference.getParameterList());
      }
      CodeStyleManager.getInstance(project).reformat(newExpression);
    }

    @Nullable
    private static String getCollectionClass(PsiMethodCallExpression call) {
      PsiMethodReferenceExpression methodRef = tryCast(call.getArgumentList().getExpressions()[0], PsiMethodReferenceExpression.class);
      if (methodRef == null || !methodRef.isConstructor()) return null;
      PsiMethod ctor = tryCast(methodRef.resolve(), PsiMethod.class);
      if (ctor == null || ctor.getParameterList().getParametersCount() != 0) return null;
      PsiClass aClass = ctor.getContainingClass();
      if (aClass == null) return null;
      String name = aClass.getQualifiedName();
      if (name != null && name.startsWith("java.util.") &&
          Stream.of(aClass.getConstructors()).anyMatch(SimplifyCollectionCreationFix::isCollectionConstructor)) {
        return name;
      }
      return null;
    }

    @Contract("null -> false")
    private static boolean isCollectionConstructor(PsiMethod ctor) {
      if (ctor == null || !ctor.getModifierList().hasExplicitModifier(PsiModifier.PUBLIC)) return false;
      PsiParameterList list = ctor.getParameterList();
      if (list.getParametersCount() != 1) return false;
      PsiTypeElement typeElement = list.getParameters()[0].getTypeElement();
      if (typeElement == null) return false;
      PsiType type = typeElement.getType();
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      return aClass != null && CommonClassNames.JAVA_UTIL_COLLECTION.equals(aClass.getQualifiedName());
    }
  }

  private static class ReplaceWithPeekFix implements CallChainSimplification {

    @Override
    public String getName() {
      return "Replace with 'peek'";
    }

    @Override
    public String getMessage() {
      return "Can be replaced with 'peek'";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiLambdaExpression lambda =
        tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
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
          tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
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
      instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "anyMatch", "allMatch", "noneMatch", "forEach", "forEachOrdered")
        .parameterCount(1);
    private static final CallMatcher OPTIONAL_TERMINAL =
      anyOf(instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "findFirst", "findAny").parameterCount(0),
            instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "min", "max", "reduce").parameterCount(1));
    private ReplacementMode myMode;

    enum ReplacementMode {
      OPTIONAL, FUNCTION, NEGATED_FUNCTION
    }

    public SimpleStreamOfFix(ReplacementMode mode) {
      myMode = mode;
    }

    @Override
    public String getName() {
      switch (myMode) {
        case OPTIONAL:
          return "Replace with 'Optional.of'";
        default:
          return "Use Stream element explicitly";
      }
    }

    @Override
    public String getMessage() {
      return "Unnecessary single-element Stream";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression streamOfCall) {
      PsiExpression streamOfArg = ArrayUtil.getFirstElement(streamOfCall.getArgumentList().getExpressions());
      if (streamOfArg == null) return null;
      PsiMethodCallExpression nextCall = ExpressionUtils.getCallForQualifier(streamOfCall);
      PsiExpression nextArg = ArrayUtil.getFirstElement(nextCall.getArgumentList().getExpressions());
      String replacement;
      if (myMode == ReplacementMode.OPTIONAL) {
        replacement = CommonClassNames.JAVA_UTIL_OPTIONAL + ".of(" + streamOfArg.getText() + ")";
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
          PsiExpression arg = PsiUtil.skipParenthesizedExprDown(nextCall.getArgumentList().getExpressions()[0]);
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
    private static final CallMatcher MAP_TO_OBJ = instanceCall(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM, "mapToObj").parameterCount(1);

    @Override
    public String getName() {
      return "Replace with 'boxed'";
    }

    @Override
    public String getMessage() {
      return "Can be replaced with 'boxed'";
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
        if (target instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)target;
          // Integer::new or Integer::valueOf
          if (targetClass == method.getContainingClass() &&
              (method.isConstructor() || method.getName().equals("valueOf")) && method.getParameterList().getParametersCount() == 1) {
            return true;
          }
        }
      }
      if (arg instanceof PsiLambdaExpression) {
        PsiLambdaExpression lambda = (PsiLambdaExpression)arg;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return false;
        PsiParameter parameter = parameters[0];
        PsiExpression expression = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
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
    private static final CallMatcher TO_ARRAY = instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "toArray");
    private final String myReplacement;

    private ReplaceWithToArrayFix(String replacement) {
      myReplacement = replacement;
    }

    @Override
    public String getName() {
      return "Replace 'stream().toArray()' with 'toArray()'";
    }

    @Override
    public String getMessage() {
      return "Can be replaced with collection.toArray()";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression toArrayCall) {
      PsiMethodCallExpression streamCall = getQualifierMethodCall(toArrayCall);
      if(streamCall == null) return null;
      PsiExpression collectionExpression = streamCall.getMethodExpression().getQualifierExpression();
      if(collectionExpression == null) return null;
      CommentTracker ct = new CommentTracker();
      return ct.replaceAndRestoreComments(toArrayCall, ct.text(collectionExpression) + ".toArray(" + myReplacement + ")");
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(TO_ARRAY, methodCall -> {
        if (!COLLECTION_STREAM.test(getQualifierMethodCall(methodCall))) return null;
        PsiArrayType type = getArrayType(methodCall);
        if (type == null) return null;
        String replacement = type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT + "[]") ? "" :
                             "new " + type.getCanonicalText().replaceFirst("\\[]", "[0]");
        return new ReplaceWithToArrayFix(replacement);
      });
    }

    @Nullable
    private static PsiArrayType getArrayType(PsiMethodCallExpression call) {
      PsiType type = call.getType();
      if (!(type instanceof PsiArrayType)) return null;
      PsiArrayType candidate = (PsiArrayType)type;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length == 0) return candidate;
      if (args.length != 1) return null;
      PsiExpression supplier = args[0];
      if (supplier instanceof PsiMethodReferenceExpression) {
        // like toArray(String[]::new)
        PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)supplier;
        PsiTypeElement qualifierType = methodRef.getQualifierType();
        if (methodRef.isConstructor() && qualifierType != null && candidate.isAssignableFrom(qualifierType.getType())) {
          return candidate;
        }
      }
      else if (supplier instanceof PsiLambdaExpression) {
        // like toArray(size -> new String[size])
        PsiLambdaExpression lambda = (PsiLambdaExpression)supplier;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return null;
        PsiParameter sizeParameter = parameters[0];
        PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
        if (body instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)body;
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
      instanceCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "map", "mapToLong", "mapToDouble", "mapToObj")
        .parameterCount(1);
    private static final CallMatcher INT_STREAM_RANGE =
      staticCall(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM, "range").parameterTypes("int", "int");
    private static final CallMatcher MIN_INT =
      anyOf(
        staticCall(CommonClassNames.JAVA_LANG_MATH, "min").parameterTypes("int", "int"),
        staticCall(CommonClassNames.JAVA_LANG_INTEGER, "min").parameterTypes("int", "int"));

    private final String myName;

    public ReplaceWithElementIterationFix(IndexedContainer container, String name) {
      PsiExpression qualifier = container.getQualifier();
      String qualifierText = PsiExpressionTrimRenderer.render(qualifier, 50);
      PsiType type = qualifier.getType();
      String replacement = type instanceof PsiArrayType ? "Arrays.stream(" + qualifierText + ")" : qualifierText + ".stream()";
      myName = "Replace IntStream.range()." + name + "() with " + replacement;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String getMessage() {
      return "Can be replaced with element iteration";
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
        replacement = CommonClassNames.JAVA_UTIL_ARRAYS + ".stream(" + containerQualifier.getText() + ")";
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
      if (!(mapper instanceof PsiLambdaExpression)) return null;
      PsiLambdaExpression lambda = (PsiLambdaExpression)mapper;
      PsiParameter indexParameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
      PsiElement body = lambda.getBody();
      if (body == null || indexParameter == null) return null;
      String nameCandidate = null;
      if (containerQualifier instanceof PsiReferenceExpression) {
        String name = ((PsiReferenceExpression)containerQualifier).getReferenceName();
        if (name != null) {
          nameCandidate = StringUtil.unpluralize(name);
          if (name.equals(nameCandidate)) {
            nameCandidate = null;
          }
        }
      }
      JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      SuggestedNameInfo info =
        javaCodeStyleManager.suggestVariableName(VariableKind.PARAMETER, nameCandidate, null, elementType, true);
      nameCandidate = ArrayUtil.getFirstElement(info.names);
      String name = javaCodeStyleManager.suggestUniqueVariableName(nameCandidate == null ? "item" : nameCandidate, mapToObjCall, true);
      Collection<PsiReference> refs = ReferencesSearch.search(indexParameter, new LocalSearchScope(body)).findAll();
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
      if (mapper instanceof PsiLambdaExpression) {
        PsiLambdaExpression lambda = (PsiLambdaExpression)mapper;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return null;
        PsiParameter indexParameter = parameters[0];
        PsiElement body = lambda.getBody();
        if (body == null) return null;
        Collection<PsiReference> refs = ReferencesSearch.search(indexParameter, new LocalSearchScope(body)).findAll();
        if (!refs.isEmpty() &&
            refs.stream()
              .allMatch(ref -> limitedContainer.myContainer.extractGetExpressionFromIndex(tryCast(ref, PsiExpression.class)) != null)) {
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
    private boolean myInvert;

    public RemoveBooleanIdentityFix(boolean invert) {
      myInvert = invert;
    }

    @Override
    public String getName() {
      return "Merge with previous 'map' call";
    }

    @Override
    public String getMessage() {
      return "Can be merged with previous 'map' call";
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
      return ct.replaceAndRestoreComments(call, ct.markUnchanged(qualifier));
    }

    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_MATCH, call -> {
        PsiMethodCallExpression qualifierCall = getQualifierMethodCall(call);
        if (!STREAM_MAP.test(qualifierCall)) return null;
        PsiExpression qualifierArg = PsiUtil.skipParenthesizedExprDown(qualifierCall.getArgumentList().getExpressions()[0]);
        PsiExpression predicate = call.getArgumentList().getExpressions()[0];
        boolean invert = false;
        if (!isBooleanIdentity(predicate)) {
          Boolean target = getBooleanEqualsTarget(predicate);
          if (target == null || (!target && "anyMatch".equals(call.getMethodExpression().getReferenceName()))) return null;
          invert = !target;
          if (qualifierArg instanceof PsiMethodReferenceExpression) {
            PsiMethod method = tryCast(((PsiMethodReferenceExpression)qualifierArg).resolve(), PsiMethod.class);
            if (method == null) return null;
            if (!PsiType.BOOLEAN.equals(method.getReturnType()) && !NullableNotNullManager.isNotNull(method)) return null;
          }
          else if (!(qualifierArg instanceof PsiLambdaExpression) ||
                   DfaUtil.inferLambdaNullity((PsiLambdaExpression)qualifierArg) != Nullness.NOT_NULL) {
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
      arg = PsiUtil.skipParenthesizedExprDown(arg);
      if (FunctionalExpressionUtils.isFunctionalReferenceTo(arg, CommonClassNames.JAVA_LANG_BOOLEAN, PsiType.BOOLEAN,
                                                            "booleanValue", PsiType.EMPTY_ARRAY) ||
          FunctionalExpressionUtils.isFunctionalReferenceTo(arg, CommonClassNames.JAVA_LANG_BOOLEAN, null,
                                                            "valueOf", PsiType.BOOLEAN)) {
        return true;
      }
      return arg instanceof PsiLambdaExpression && LambdaUtil.isIdentityLambda((PsiLambdaExpression)arg);
    }

    @Nullable
    private static Boolean getBooleanEqualsTarget(PsiExpression arg) {
      // Boolean.TRUE::equals or x -> Boolean.TRUE.equals(x)
      arg = PsiUtil.skipParenthesizedExprDown(arg);
      PsiReferenceExpression qualifier = null;
      if (arg instanceof PsiMethodReferenceExpression) {
        PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)arg;
        if (!BOOLEAN_EQUALS.methodReferenceMatches(methodRef)) return null;
        qualifier = tryCast(methodRef.getQualifierExpression(), PsiReferenceExpression.class);
      }
      else if (arg instanceof PsiLambdaExpression) {
        PsiLambdaExpression lambda = (PsiLambdaExpression)arg;
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
      if (containingClass != null && CommonClassNames.JAVA_LANG_BOOLEAN.equals(containingClass.getQualifiedName())) {
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
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression == null) return null;
      if (expression instanceof PsiFunctionalExpression) return text;
      if (expression instanceof PsiConditionalExpression) {
        PsiConditionalExpression ternary = (PsiConditionalExpression)expression;
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
      if (type.rawType().equalsToText(CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION)) return adapted;
      PsiClass typeClass = type.resolve();
      // Disable inspection if type of expression is some subtype which defines its own 'apply' methods
      // to avoid possible resolution clashes
      if (typeClass == null) return null;
      PsiMethod[] methods = typeClass.findMethodsByName("apply", true);
      if (methods.length != 1 ||
          methods[0].getContainingClass() == null ||
          !CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION.equals(methods[0].getContainingClass().getQualifiedName())) {
        return null;
      }
      return adapted;
    }
  }

  private static class ReplaceStreamSupportWithCollectionStreamFix implements CallChainSimplification {
    private static final CallMatcher STREAM_SUPPORT = staticCall("java.util.stream.StreamSupport", "stream")
      .parameterTypes("java.util.Spliterator", "boolean");
    private static final CallMatcher SPLITERATOR =
      instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "spliterator").parameterCount(0);
    private final String myQualifierText;
    private final boolean myParallel;

    public ReplaceStreamSupportWithCollectionStreamFix(PsiExpression qualifier, boolean parallel) {
      myQualifierText = PsiExpressionTrimRenderer.render(qualifier, 50);
      myParallel = parallel;
    }

    @Override
    public String getName() {
      return "Replace with '" + myQualifierText + "." + getMethodName() + "' call";
    }

    @Override
    public String getMessage() {
      return "Can be replaced with '" + myQualifierText + "." + (getMethodName()) + "' call";
    }

    @NotNull
    private String getMethodName() {
      return myParallel ? "parallelStream" : "stream";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 2) return null;
      PsiMethodCallExpression spliteratorCall = tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
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
        PsiMethodCallExpression spliterator = tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
        if (!SPLITERATOR.test(spliterator)) return null;
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(spliterator.getMethodExpression().getQualifierExpression());
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
      return "Replace with Arrays.stream()";
    }

    @Override
    public String getMessage() {
      return "Can be replaced with Arrays.stream()";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      PsiMethodCallExpression mapCall = ExpressionUtils.getCallForQualifier(call);
      if(mapCall == null) return null;
      return new CommentTracker().replaceAndRestoreComments(mapCall, myReplacement);
    }

    @NotNull
    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_RANGE, call -> {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        PsiMethodCallExpression maybeMap = ExpressionUtils.getCallForQualifier(call);
        if (maybeMap == null) return null;
        if (!STREAM_INT_MAP_TO_ALL.test(maybeMap)) return null;
        PsiExpression arg = maybeMap.getArgumentList().getExpressions()[0];
        PsiLambdaExpression lambda = tryCast(arg, PsiLambdaExpression.class);
        if (lambda == null) return null;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) return null;
        PsiArrayAccessExpression arrayAccess = tryCast(lambda.getBody(), PsiArrayAccessExpression.class);
        if (arrayAccess == null) return null;
        PsiExpression index = arrayAccess.getIndexExpression();

        if (!ExpressionUtils.isReferenceTo(index, parameters[0])) return null;

        PsiExpression arrayExpr = arrayAccess.getArrayExpression();

        PsiExpression leftBound = args[0];
        PsiExpression rightBound = args[1];
        return new RangeToArrayStreamFix(
          CommonClassNames.JAVA_UTIL_ARRAYS + ".stream(" + arrayExpr.getText() + "," + leftBound.getText() + "," + rightBound.getText() + ")");
      });
    }
  }

  static class NCopiesToGenerateStreamFix implements CallChainSimplification {

    private final @NotNull String myReplacement;

    NCopiesToGenerateStreamFix(@NotNull String replacement) {myReplacement = replacement;}

    @Override
    public String getName() {
      return "Replace with Stream.generate()";
    }

    @Override
    public String getMessage() {
      return "Can be replaced with Stream.generate()";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression streamCall) {
      PsiElement maybeMap = ExpressionUtils.getCallForQualifier(streamCall);
      if(maybeMap == null) return null;
      Project project = streamCall.getProject();
      PsiClassType classType = PsiType.getTypeByName(CommonClassNames.JAVA_UTIL_STREAM_STREAM, project, GlobalSearchScope.allScope(project));
      PsiClass streamClass = classType.resolve();
      if(streamClass == null) return null;
      ImportUtils.addImportIfNeeded(streamClass, streamCall); // TODO Idea doesn't want to import j.u.s.Stream
      return new CommentTracker().replaceAndRestoreComments(maybeMap, myReplacement);
    }

    @NotNull
    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(COLLECTION_STREAM, call -> {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        PsiMethodCallExpression maybeNCopies = tryCast(qualifier, PsiMethodCallExpression.class);
        if(!N_COPIES.test(maybeNCopies)) return null;
        PsiExpression[] nCopiesArgs = maybeNCopies.getArgumentList().getExpressions();
        PsiExpression count = nCopiesArgs[0];
        PsiExpression obj = nCopiesArgs[1];
        if(!ExpressionUtils.isSimpleExpression(obj)) return null;

        PsiMethodCallExpression maybeMap = ExpressionUtils.getCallForQualifier(call);
        if(maybeMap == null) return null;
        if(!BASE_STREAM_MAP.test(maybeMap)) return null;
        PsiLambdaExpression lambda = tryCast(maybeMap.getArgumentList().getExpressions()[0], PsiLambdaExpression.class);
        if(lambda == null) return null;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if(parameters.length != 1) return null;
        PsiParameter lambdaVar = parameters[0];
        PsiElement body = lambda.getBody();
        if(body == null) return null;
        if (VariableAccessUtils.variableIsUsed(lambdaVar, body)) return null;
        return new NCopiesToGenerateStreamFix(CommonClassNames.JAVA_UTIL_STREAM_STREAM + ".generate(()->" + body.getText() + ").limit(" + count.getText() + ")");
      });
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
      return "Replace with " + myMethodName + "()";
    }

    @Override
    public String getMessage() {
      return "Can be replaced with " + myMethodName + "()";
    }

    @Override
    public PsiElement simplify(PsiMethodCallExpression call) {
      return new CommentTracker().replaceAndRestoreComments(call, myReplacement);
    }

    @NotNull
    static CallHandler<CallChainSimplification> handler() {
      return CallHandler.of(STREAM_FIND_FIRST, call -> {
        PsiMethodCallExpression maybeSorted = tryCast(call.getMethodExpression().getQualifierExpression(), PsiMethodCallExpression.class);
        if (!STREAM_SORTED.test(maybeSorted)) return null;
        PsiExpression[] args = maybeSorted.getArgumentList().getExpressions();
        PsiExpression qualifier = maybeSorted.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return null;

        final String comparator;
        boolean reversed = false;
        if (args.length == 1) {
          PsiExpression maybeComparator = args[0];
          if (maybeComparator instanceof PsiMethodCallExpression && COMPARATOR_REVERSED.test((PsiMethodCallExpression)maybeComparator)) {
            PsiExpression comparatorQualifier = ((PsiMethodCallExpression)maybeComparator).getMethodExpression().getQualifierExpression();
            if(comparatorQualifier == null) return null;
            comparator = comparatorQualifier.getText();
            reversed = true;
          } else {
            PsiType comparatorType = maybeComparator.getType();
            if (comparatorType == null || !InheritanceUtil.isInheritor(comparatorType, CommonClassNames.JAVA_UTIL_COMPARATOR)) return null;
            comparator = maybeComparator.getText();
          }
        } else if (args.length == 0) {
          comparator = "";
        } else return null;

        String methodName = reversed ? "max" : "min";
        return new SortedFirstToMinMaxFix(methodName, qualifier.getText() + "." + methodName + "(" + comparator + ")");
      });
    }
  }
}
