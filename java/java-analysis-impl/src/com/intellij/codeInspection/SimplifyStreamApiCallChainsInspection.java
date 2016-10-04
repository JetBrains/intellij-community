/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.*;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Arrays;

/**
 * @author Pavel.Dolgov
 */
public class SimplifyStreamApiCallChainsInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + SimplifyStreamApiCallChainsInspection.class.getName());

  private static final String FOR_EACH_METHOD = "forEach";
  private static final String FOR_EACH_ORDERED_METHOD = "forEachOrdered";
  private static final String STREAM_METHOD = "stream";
  private static final String EMPTY_METHOD = "empty";
  private static final String AS_LIST_METHOD = "asList";
  private static final String OF_METHOD = "of";
  private static final String EMPTY_LIST_METHOD = "emptyList";
  private static final String EMPTY_SET_METHOD = "emptySet";
  private static final String SINGLETON_LIST_METHOD = "singletonList";
  private static final String SINGLETON_METHOD = "singleton";
  private static final String COLLECT_METHOD = "collect";
  private static final String IS_PRESENT_METHOD = "isPresent";
  private static final String FIND_ANY_METHOD = "findAny";
  private static final String FIND_FIRST_METHOD = "findFirst";
  private static final String FILTER_METHOD = "filter";
  private static final String ANY_MATCH_METHOD = "anyMatch";
  private static final String NONE_MATCH_METHOD = "noneMatch";
  private static final String ALL_MATCH_METHOD = "allMatch";

  private static final String COUNTING_COLLECTOR = "counting";
  private static final String MIN_BY_COLLECTOR = "minBy";
  private static final String MAX_BY_COLLECTOR = "maxBy";
  private static final String MAPPING_COLLECTOR = "mapping";
  private static final String REDUCING_COLLECTOR = "reducing";
  private static final String SUMMING_INT_COLLECTOR = "summingInt";
  private static final String SUMMING_LONG_COLLECTOR = "summingLong";
  private static final String SUMMING_DOUBLE_COLLECTOR = "summingDouble";

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
        final PsiMethod method = methodCall.resolveMethod();
        if (isCallOf(method, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
          handleCollectionStream(methodCall);
        }
        else if (isCallOf(method, CommonClassNames.JAVA_UTIL_STREAM_STREAM, COLLECT_METHOD, 1)) {
          handleStreamCollect(methodCall);
        }
        else if (isCallOf(method, CommonClassNames.JAVA_UTIL_OPTIONAL, IS_PRESENT_METHOD, 0)) {
          handleOptionalIsPresent(methodCall);
        }
        else if (isCallOf(method, CommonClassNames.JAVA_UTIL_STREAM_STREAM, ANY_MATCH_METHOD, 1)) {
          if(isParentNegated(methodCall)) {
            boolean argNegated = isArgumentLambdaNegated(methodCall);
            registerMatchFix(methodCall,
                             new SimplifyMatchNegationFix(argNegated ? "!Stream.anyMatch(x -> !(...))" : "!Stream.anyMatch(...)",
                                                          argNegated ? ALL_MATCH_METHOD : NONE_MATCH_METHOD));
          }
        }
        else if (isCallOf(method, CommonClassNames.JAVA_UTIL_STREAM_STREAM, NONE_MATCH_METHOD, 1)) {
          if(isParentNegated(methodCall)) {
            registerMatchFix(methodCall, new SimplifyMatchNegationFix("!Stream.noneMatch(...)", ANY_MATCH_METHOD));
          }
          if(isArgumentLambdaNegated(methodCall)) {
            registerMatchFix(methodCall, new SimplifyMatchNegationFix("Stream.noneMatch(x -> !(...))", ALL_MATCH_METHOD));
          }
        }
        else if (isCallOf(method, CommonClassNames.JAVA_UTIL_STREAM_STREAM, ALL_MATCH_METHOD, 1)) {
          if(isArgumentLambdaNegated(methodCall)) {
            boolean parentNegated = isParentNegated(methodCall);
            registerMatchFix(methodCall,
                             new SimplifyMatchNegationFix(parentNegated ? "!Stream.allMatch(x -> !(...))" : "Stream.allMatch(x -> !(...))",
                                                          parentNegated ? ANY_MATCH_METHOD : NONE_MATCH_METHOD));
          }
        }
        else {
          handleStreamForEach(methodCall, method);
        }
      }

      void registerMatchFix(PsiMethodCallExpression methodCall, SimplifyMatchNegationFix fix) {
        PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
        if(nameElement != null) {
          holder.registerProblem(nameElement, fix.getMessage(), new SimplifyCallChainFix(fix));
        }
      }

      private void handleOptionalIsPresent(PsiMethodCallExpression methodCall) {
        PsiExpression optionalQualifier = methodCall.getMethodExpression().getQualifierExpression();
        if(optionalQualifier instanceof PsiMethodCallExpression) {
          PsiMethod optionalProducer = ((PsiMethodCallExpression)optionalQualifier).resolveMethod();
          if (isCallOf(optionalProducer, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FIND_FIRST_METHOD, 0) ||
              isCallOf(optionalProducer, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FIND_ANY_METHOD, 0)) {
            PsiExpression streamQualifier = ((PsiMethodCallExpression)optionalQualifier).getMethodExpression().getQualifierExpression();
            if(streamQualifier instanceof PsiMethodCallExpression) {
              PsiMethod streamMethod = ((PsiMethodCallExpression)streamQualifier).resolveMethod();
              if(isCallOf(streamMethod, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FILTER_METHOD, 1)) {
                ReplaceOptionalIsPresentChainFix fix = new ReplaceOptionalIsPresentChainFix(optionalProducer.getName());
                holder
                  .registerProblem(methodCall, getCallChainRange(methodCall, (PsiMethodCallExpression)streamQualifier), fix.getMessage(),
                                   new SimplifyCallChainFix(fix));
              }
            }
          }
        }
      }

      private void handleStreamForEach(PsiMethodCallExpression methodCall, PsiMethod method) {
        final String name;
        if (isCallOf(method, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FOR_EACH_METHOD, 1)) {
          name = FOR_EACH_METHOD;
        }
        else if (isCallOf(method, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FOR_EACH_ORDERED_METHOD, 1)) {
          name = FOR_EACH_ORDERED_METHOD;
        }
        else {
          return;
        }
        final PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
        if (qualifierCall == null) return;
        final PsiMethod qualifier = qualifierCall.resolveMethod();
        if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
          final ReplaceStreamMethodFix fix = new ReplaceStreamMethodFix(name, FOR_EACH_METHOD, true);
          holder
            .registerProblem(methodCall, getCallChainRange(methodCall, qualifierCall), fix.getMessage(), new SimplifyCallChainFix(fix));
        }
      }

      private void handleStreamCollect(PsiMethodCallExpression methodCall) {
        PsiElement parameter = methodCall.getArgumentList().getExpressions()[0];
        if(parameter instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression collectorCall = (PsiMethodCallExpression)parameter;
          PsiMethod collectorMethod = collectorCall.resolveMethod();
          ReplaceCollectorFix fix = null;
          if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, COUNTING_COLLECTOR, 0)) {
            fix = new ReplaceCollectorFix(COUNTING_COLLECTOR, "count()", false);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, MIN_BY_COLLECTOR, 1)) {
            fix = new ReplaceCollectorFix(MIN_BY_COLLECTOR, "min({0})", true);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, MAX_BY_COLLECTOR, 1)) {
            fix = new ReplaceCollectorFix(MAX_BY_COLLECTOR, "max({0})", true);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, MAPPING_COLLECTOR, 2)) {
            fix = new ReplaceCollectorFix(MAPPING_COLLECTOR, "map({0}).collect({1})", false);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, REDUCING_COLLECTOR, 1)) {
            fix = new ReplaceCollectorFix(REDUCING_COLLECTOR, "reduce({0})", true);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, REDUCING_COLLECTOR, 2)) {
            fix = new ReplaceCollectorFix(REDUCING_COLLECTOR, "reduce({0}, {1})", false);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, REDUCING_COLLECTOR, 3)) {
            fix = new ReplaceCollectorFix(REDUCING_COLLECTOR, "map({1}).reduce({0}, {2})", false);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, SUMMING_INT_COLLECTOR, 1)) {
            fix = new ReplaceCollectorFix(SUMMING_INT_COLLECTOR, "mapToInt({0}).sum()", false);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, SUMMING_LONG_COLLECTOR, 1)) {
            fix = new ReplaceCollectorFix(SUMMING_LONG_COLLECTOR, "mapToLong({0}).sum()", false);
          } else if(isCallOf(collectorMethod, CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, SUMMING_DOUBLE_COLLECTOR, 1)) {
            fix = new ReplaceCollectorFix(SUMMING_DOUBLE_COLLECTOR, "mapToDouble({0}).sum()", false);
          }
          if (fix != null &&
              collectorCall.getArgumentList().getExpressions().length == collectorMethod.getParameterList().getParametersCount()) {
            TextRange range = methodCall.getTextRange();
            PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
            if(nameElement != null) {
              range = new TextRange(nameElement.getTextOffset(), range.getEndOffset());
            }
            holder.registerProblem(methodCall, range.shiftRight(-methodCall.getTextOffset()), fix.getMessage(),
                                   new SimplifyCallChainFix(fix));
          }
        }
      }

      private void handleCollectionStream(PsiMethodCallExpression methodCall) {
        final PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
        if (qualifierCall == null) return;
        final PsiMethod qualifier = qualifierCall.resolveMethod();
        ReplaceCollectionStreamFix fix = null;
        if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_ARRAYS, AS_LIST_METHOD, 1)) {
          if (hasSingleArrayArgument(qualifierCall)) {
            fix = new ArraysAsListSingleArrayFix();
          }
          else {
            fix = new ReplaceWithStreamOfFix("Arrays.asList()");
          }
        }
        else if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_COLLECTIONS, SINGLETON_LIST_METHOD, 1)) {
          if (!hasSingleArrayArgument(qualifierCall)) {
            fix = new ReplaceSingletonWithStreamOfFix("Collections.singletonList()");
          }
        }
        else if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_COLLECTIONS, SINGLETON_METHOD, 1)) {
          if (!hasSingleArrayArgument(qualifierCall)) {
            fix = new ReplaceSingletonWithStreamOfFix("Collections.singleton()");
          }
        }
        else if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_COLLECTIONS, EMPTY_LIST_METHOD, 0)) {
          fix = new ReplaceWithStreamEmptyFix(EMPTY_LIST_METHOD);
        }
        else if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_COLLECTIONS, EMPTY_SET_METHOD, 0)) {
          fix = new ReplaceWithStreamEmptyFix(EMPTY_SET_METHOD);
        }
        if (fix != null) {
          holder.registerProblem(methodCall, null, fix.getMessage(), new SimplifyCallChainFix(fix));
        }
      }
    };
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

  static boolean hasSingleArrayArgument(PsiMethodCallExpression qualifierCall) {
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

  @Nullable
  static PsiMethodCallExpression getQualifierMethodCall(PsiMethodCallExpression methodCall) {
    final PsiExpression qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      return (PsiMethodCallExpression)qualifierExpression;
    }
    return null;
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

  @Contract("null, _, _, _ -> false")
  protected static boolean isCallOf(@Nullable PsiMethod method,
                                    @NotNull String className,
                                    @NotNull String methodName,
                                    int parametersCount) {
    if (method == null) return false;
    if (methodName.equals(method.getName()) && method.getParameterList().getParametersCount() == parametersCount) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && className.equals(containingClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  interface CallChainFix {
    String getName();
    void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor);
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
      myFix.applyFix(project, descriptor);
    }
  }

  private static abstract class CallChainFixBase implements CallChainFix {
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getStartElement();
      if (element instanceof PsiMethodCallExpression) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
        final PsiMethodCallExpression expression = (PsiMethodCallExpression)element;
        final PsiExpression forEachMethodQualifier = expression.getMethodExpression().getQualifierExpression();
        if (forEachMethodQualifier instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression previousExpression = (PsiMethodCallExpression)forEachMethodQualifier;
          final PsiExpression qualifierExpression = previousExpression.getMethodExpression().getQualifierExpression();
          replaceMethodCall(expression, previousExpression, qualifierExpression);
        }
      }
    }

    protected abstract void replaceMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                              @NotNull PsiMethodCallExpression qualifierCall,
                                              @Nullable PsiExpression qualifierExpression);
  }

  private static abstract class ReplaceCollectionStreamFix extends CallChainFixBase {
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

    @Override
    protected void replaceMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                     @NotNull PsiMethodCallExpression qualifierCall,
                                     @Nullable PsiExpression qualifierExpression) {
      methodCall.getArgumentList().replace(qualifierCall.getArgumentList());

      final Project project = methodCall.getProject();
      String typeParameter = getTypeParameter(qualifierCall);
      String replacement;
      if (typeParameter != null) {
        replacement = myClassName + ".<" + typeParameter + ">" + myMethodName;
      }
      else {
        replacement = myClassName + "." + myMethodName;
      }
      final PsiExpression newMethodExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, methodCall);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(methodCall.getMethodExpression().replace(newMethodExpression));
    }
  }

  private static class ReplaceWithStreamOfFix extends ReplaceCollectionStreamFix {
    private ReplaceWithStreamOfFix(String qualifierCall) {
      super(qualifierCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, OF_METHOD);
    }
  }

  private static class ReplaceSingletonWithStreamOfFix extends ReplaceWithStreamOfFix {
    private ReplaceSingletonWithStreamOfFix(String qualifierCall) {
      super(qualifierCall);
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

  private static class ArraysAsListSingleArrayFix extends ReplaceCollectionStreamFix {
    private ArraysAsListSingleArrayFix() {
      super("Arrays.asList()", CommonClassNames.JAVA_UTIL_ARRAYS, STREAM_METHOD);
    }
  }

  private static class ReplaceWithStreamEmptyFix extends ReplaceCollectionStreamFix {
    private ReplaceWithStreamEmptyFix(String qualifierMethodName) {
      super("Collections." + qualifierMethodName + "()", CommonClassNames.JAVA_UTIL_STREAM_STREAM, EMPTY_METHOD);
    }
  }

  static class ReplaceStreamMethodFix extends CallChainFixBase {
    private final String myStreamMethod;
    private final String myCollectionMethod;
    private final boolean myChangeSemantics;

    public ReplaceStreamMethodFix(String streamMethod, String collectionMethod, boolean changeSemantics) {
      myStreamMethod = streamMethod;
      myCollectionMethod = collectionMethod;
      myChangeSemantics = changeSemantics;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace Collection.stream()." + myStreamMethod +
             "() with Collection." + myCollectionMethod + "()" +
             (myChangeSemantics ? " (may change semantics)" : "");
    }

    @NotNull
    public String getMessage() {
      return "Collection.stream()." + myStreamMethod +
             "() can be replaced with Collection." + myCollectionMethod + "()" +
             (myChangeSemantics ? " (may change semantics)" : "");
    }

    @Override
    protected void replaceMethodCall(@NotNull PsiMethodCallExpression methodCall,
                                     @NotNull PsiMethodCallExpression qualifierCall,
                                     @Nullable PsiExpression qualifierExpression) {
      if (qualifierExpression != null) {
        final PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
        if (nameElement != null) {
          qualifierCall.replace(qualifierExpression);
          if (!myStreamMethod.equals(myCollectionMethod)) {
            final Project project = methodCall.getProject();
            PsiIdentifier collectionMethod = JavaPsiFacade.getElementFactory(project).createIdentifier(myCollectionMethod);
            nameElement.replace(collectionMethod);
          }
        }
      }
    }
  }

  private static class ReplaceCollectorFix implements CallChainFix {
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
      return "Replace Stream.collect(" + myCollector +
             "()) with Stream." + myStreamSequenceStripped +
             (myChangeSemantics ? " (may change semantics when result is null)" : "");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression collectCall = (PsiMethodCallExpression)element;
        PsiExpression qualifierExpression = collectCall.getMethodExpression().getQualifierExpression();
        if (qualifierExpression != null) {
          PsiElement parameter = collectCall.getArgumentList().getExpressions()[0];
          if (parameter instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression collectorCall = (PsiMethodCallExpression)parameter;
            PsiExpression[] collectorArgs = collectorCall.getArgumentList().getExpressions();
            String result = MessageFormat.format(myStreamSequence, Arrays.stream(collectorArgs).map(PsiExpression::getText).toArray());
            if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
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
      return "Stream.collect(" + myCollector +
             "()) can be replaced with Stream." + myStreamSequenceStripped +
             (myChangeSemantics ? " (may change semantics when result is null)" : "");
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
      return "Replace Stream.filter()." + myFindMethodName + "().isPresent() with Stream.anyMatch()";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (element instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression isPresentCall = (PsiMethodCallExpression)element;
        PsiExpression isPresentQualifier = isPresentCall.getMethodExpression().getQualifierExpression();
        if(isPresentQualifier instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression findCall = (PsiMethodCallExpression)isPresentQualifier;
          PsiExpression findQualifier = findCall.getMethodExpression().getQualifierExpression();
          if(findQualifier instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression filterCall = (PsiMethodCallExpression)findQualifier;
            if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
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
      return "Stream.filter()." + myFindMethodName + "().isPresent() can be replaced with Stream.anyMatch()";
    }
  }

  private static class SimplifyMatchNegationFix implements CallChainFix {
    private final String myFrom, myTo;

    private SimplifyMatchNegationFix(String from, String to) {
      myFrom = from;
      myTo = to;
    }

    @Override
    public String getName() {
      return "Replace "+myFrom+" with Stream."+myTo+"(...)";
    }

    public String getMessage() {
      return myFrom+" can be replaced with Stream."+myTo+"(...)";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(element instanceof PsiIdentifier) {
        String from = element.getText();
        boolean removeParentNegation;
        boolean removeLambdaNegation;
        switch(from) {
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
            return;
        }
        PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (methodCall == null) return;
        if (removeParentNegation && !isParentNegated(methodCall)) return;
        if (removeLambdaNegation && !isArgumentLambdaNegated(methodCall)) return;
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        element.replace(factory.createIdentifier(myTo));
        if (removeLambdaNegation) {
          // Casts and array bounds already checked in isArgumentLambdaNegated
          PsiExpression body = (PsiExpression)((PsiLambdaExpression)methodCall.getArgumentList().getExpressions()[0]).getBody();
          PsiExpression negated = BoolUtils.getNegated(body);
          LOG.assertTrue(negated != null);
          body.replace(negated);
        }
        if (removeParentNegation) {
          PsiUtil.skipParenthesizedExprUp(methodCall.getParent()).replace(methodCall);
        }
      }
    }
  }
}
