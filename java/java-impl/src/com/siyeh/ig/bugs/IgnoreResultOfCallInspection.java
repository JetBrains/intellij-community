/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ig.psiutils.*;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER;

public final class IgnoreResultOfCallInspection extends BaseInspection {
  private static final CallMatcher STREAM_COLLECT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "collect").parameterCount(1);
  private static final CallMatcher COLLECTOR_TO_COLLECTION =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toCollection").parameterCount(1);

  private static final CallMatcher KNOWN_ARGUMENT_SIDE_EFFECTS = CallMatcher.anyOf(
    (CallMatcher.instanceCall( "java.nio.channels.FileChannel","write")));

  private static final CallMapper<String> KNOWN_EXCEPTIONAL_SIDE_EFFECTS = new CallMapper<String>()
    .register(CallMatcher.staticCall("java.util.regex.Pattern", "compile"), "java.util.regex.PatternSyntaxException")
    .register(CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_SHORT, "parseShort", "valueOf", "decode"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BYTE, "parseByte", "valueOf", "decode"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "parseInt", "valueOf", "decode"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "parseLong", "valueOf", "decode"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "parseDouble", "valueOf"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat", "valueOf")), "java.lang.NumberFormatException")
    .register(CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS,
                                       "getMethod", "getDeclaredMethod", "getConstructor", "getDeclaredConstructor"),
              "java.lang.NoSuchMethodException")
    .register(CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS,
                                       "getField", "getDeclaredField"), "java.lang.NoSuchFieldException");
  private static final CallMatcher MOCK_LIBS_EXCLUDED_QUALIFIER_CALLS =
    CallMatcher.anyOf(
      CallMatcher.instanceCall("org.mockito.stubbing.Stubber", "when"),
      CallMatcher.staticCall("org.mockito.Mockito", "verify"),
      CallMatcher.instanceCall("org.jmock.Expectations", "allowing", "ignoring", "never", "one", "oneOf", "with")
        .parameterTypes("T"),
      //new version of jmock
      CallMatcher.instanceCall("org.jmock.AbstractExpectations", "allowing", "ignoring", "never", "one", "oneOf", "with")
        .parameterTypes("T"),
      CallMatcher.instanceCall("org.jmock.syntax.ReceiverClause", "of").parameterTypes("T"));

  private static final CallMatcher TEST_OR_MOCK_CONTAINER_METHODS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("org.assertj.core.api.Assertions", "assertThatThrownBy", "catchThrowable", "catchThrowableOfType"),
      CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertDoesNotThrow", "assertThrows", "assertThrowsExactly"),
      CallMatcher.staticCall(JUnitCommonClassNames.ORG_JUNIT_ASSERT, "assertThrows"),
      CallMatcher.instanceCall("org.mockito.MockedStatic", "when", "verify")
    );

  private static final Set<String> CHECK_ANNOTATIONS = Set.of(
    "javax.annotation.CheckReturnValue", "org.assertj.core.util.CheckReturnValue", "com.google.errorprone.annotations.CheckReturnValue");
  private final MethodMatcher myMethodMatcher;
  /**
   * @noinspection PublicField
   */
  public boolean m_reportAllNonLibraryCalls = false;

  public IgnoreResultOfCallInspection() {
    myMethodMatcher = new MethodMatcher(true, "callCheckString")
      .add(CommonClassNames.JAVA_IO_FILE, ".*")
      .add("java.io.InputStream","read|skip|available|markSupported")
      .add("java.io.Reader","read|skip|ready|markSupported")
      .add(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "capacity|codePointAt|codePointBefore|codePointCount|indexOf|lastIndexOf|offsetByCodePoints|substring|subSequence")
      .add(CommonClassNames.JAVA_LANG_BOOLEAN,".*")
      .add(CommonClassNames.JAVA_LANG_BYTE,".*")
      .add(CommonClassNames.JAVA_LANG_CHARACTER,".*")
      .add(CommonClassNames.JAVA_LANG_DOUBLE,".*")
      .add(CommonClassNames.JAVA_LANG_FLOAT,".*")
      .add(CommonClassNames.JAVA_LANG_INTEGER,".*")
      .add(CommonClassNames.JAVA_LANG_LONG,".*")
      .add(CommonClassNames.JAVA_LANG_MATH,".*")
      .add(CommonClassNames.JAVA_LANG_OBJECT,"equals|hashCode|toString")
      .add(CommonClassNames.JAVA_LANG_SHORT,".*")
      .add(CommonClassNames.JAVA_LANG_STRICT_MATH,".*")
      .add(CommonClassNames.JAVA_LANG_STRING,".*")
      .add("java.lang.Thread", "interrupted")
      .add("java.math.BigDecimal",".*")
      .add("java.math.BigInteger",".*")
      .add("java.net.InetAddress",".*")
      .add(CommonClassNames.JAVA_NET_URI,".*")
      .add("java.nio.channels.AsynchronousChannelGroup",".*")
      .add("java.nio.channels.Channel","isOpen")
      .add("java.nio.channels.FileChannel","open|map|lock|tryLock|write")
      .add("java.nio.channels.ScatteringByteChannel","read")
      .add("java.nio.channels.SocketChannel","open|socket|isConnected|isConnectionPending")
      .add(CommonClassNames.JAVA_UTIL_ARRAYS, ".*")
      .add(CommonClassNames.JAVA_UTIL_COLLECTIONS, "(?!addAll).*")
      .add(CommonClassNames.JAVA_UTIL_LIST, "of")
      .add(CommonClassNames.JAVA_UTIL_MAP, "of|ofEntries|entry")
      .add(CommonClassNames.JAVA_UTIL_SET, "of")
      .add(CommonClassNames.JAVA_UTIL_UUID,".*")
      .add("java.util.concurrent.BlockingQueue", "offer|remove")
      .add("java.util.concurrent.CountDownLatch","await|getCount")
      .add("java.util.concurrent.ExecutorService","awaitTermination|isShutdown|isTerminated")
      .add("java.util.concurrent.ForkJoinPool","awaitQuiescence")
      .add("java.util.concurrent.Semaphore","tryAcquire|availablePermits|isFair|hasQueuedThreads|getQueueLength|getQueuedThreads")
      .add("java.util.concurrent.locks.Condition","await|awaitNanos|awaitUntil")
      .add("java.util.concurrent.locks.Lock","tryLock|newCondition")
      .add("java.util.regex.Matcher","pattern|toMatchResult|start|end|group|groupCount|matches|find|lookingAt|quoteReplacement|replaceAll|replaceFirst|regionStart|regionEnd|hasTransparentBounds|hasAnchoringBounds|hitEnd|requireEnd")
      .add("java.util.regex.Pattern",".*")
      .add(CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM,".*")
      .add(CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM,".*")
      .add(CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM,".*")
      .add(CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM,".*")
      .add(CommonClassNames.JAVA_UTIL_STREAM_STREAM,".*")
      .finishDefault();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      myMethodMatcher.getTable("").prefix("myMethodMatcher"),
      checkbox("m_reportAllNonLibraryCalls", InspectionGadgetsBundle.message("result.of.method.call.ignored.non.library.option"))
    );
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ResultOfMethodCallIgnored";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass containingClass = (PsiClass)infos[0];
    final String className = containingClass.getName();
    return InspectionGadgetsBundle.message("result.of.method.call.ignored.problem.descriptor", className);
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    myMethodMatcher.readSettings(element);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    super.writeSettings(element);
    myMethodMatcher.writeSettings(element);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IgnoreResultOfCallVisitor();
  }

  private class IgnoreResultOfCallVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      if (PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType(expression))) {
        PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          visitCalledExpression(expression, (PsiMethod)resolve, null);
        }
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (ExpressionUtils.isVoidContext(expression)) {
        final PsiMethod method = expression.resolveMethod();
        if (method == null || method.isConstructor()) {
          return;
        }
        visitCalledExpression(expression, method, expression.getParent());
      }
    }

    private void visitCalledExpression(PsiExpression call, PsiMethod method, @Nullable PsiElement errorContainer) {
      if (shouldReport(call, method, errorContainer)) {
        registerMethodCallOrRefError(call, method.getContainingClass());
      }
    }

    private boolean shouldReport(PsiExpression call, PsiMethod method, @Nullable PsiElement errorContainer) {
      final PsiType returnType = method.getReturnType();
      if (PsiTypes.voidType().equals(returnType) || TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, returnType)) return false;
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) return false;
      if (errorContainer != null && PsiUtilCore.hasErrorElementChild(errorContainer)) return false;
      if (call instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression previousCall = MethodCallUtils.getQualifierMethodCall((PsiMethodCallExpression)call);
        if (MOCK_LIBS_EXCLUDED_QUALIFIER_CALLS.test(previousCall)) return false;
      }
      if (PropertyUtil.isSimpleGetter(method)) {
        return !MethodUtils.hasCanIgnoreReturnValueAnnotation(method, null);
      }
      if (method instanceof PsiCompiledElement) {
        final PsiMethod sourceMethod = ObjectUtils.tryCast(method.getNavigationElement(), PsiMethod.class);
        if (sourceMethod != null && PropertyUtil.isSimpleGetter(sourceMethod)) {
          return !MethodUtils.hasCanIgnoreReturnValueAnnotation(method, null);
        }
      }
      if (isInTestContainer(call)) return false;
      if (m_reportAllNonLibraryCalls && !LibraryUtil.classIsInLibrary(aClass)) {
        return !MethodUtils.hasCanIgnoreReturnValueAnnotation(method, null);
      }
      if(isKnownArgumentSideEffect(call)){
        return false;
      }
      if (isKnownExceptionalSideEffectCaught(call)) {
        return false;
      }
      if (isPureMethod(method, call)) {
        return !MethodUtils.hasCanIgnoreReturnValueAnnotation(method, null);
      }

      if (isHardcodedException(call)) return false;
      PsiElement stop;
      if (!myMethodMatcher.matches(method)) {
        final PsiAnnotation annotation = findCheckReturnValueAnnotation(method);
        if (annotation == null) return false;
        stop = (PsiElement)annotation.getOwner();
      }
      else {
        stop = null;
      }
      return !MethodUtils.hasCanIgnoreReturnValueAnnotation(method, stop);
    }

    private static PsiAnnotation findCheckReturnValueAnnotation(PsiMethod method) {
      final PsiAnnotation annotation = MethodUtils.findAnnotationInTree(method, null, CHECK_ANNOTATIONS);
      return annotation == null ? getAnnotationByShortNameCheckReturnValue(method) : annotation;
    }

    private static PsiAnnotation getAnnotationByShortNameCheckReturnValue(PsiMethod method) {
      for (PsiAnnotation psiAnnotation : method.getAnnotations()) {
        String qualifiedName = psiAnnotation.getQualifiedName();
        if (qualifiedName != null && "CheckReturnValue".equals(StringUtil.getShortName(qualifiedName))) {
          return psiAnnotation;
        }
      }
      return null;
    }

    private static boolean isKnownArgumentSideEffect(PsiExpression call) {
      if (!(call instanceof PsiMethodCallExpression callExpression)) {
        return false;
      }
      if (!KNOWN_ARGUMENT_SIDE_EFFECTS.test(callExpression)) {
        return false;
      }
      PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
      if (method == null) {
        return false;
      }
      PsiExpressionList list = callExpression.getArgumentList();
      for (PsiExpression argument : list.getExpressions()) {
        if (TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(argument.getType())) {
          continue;
        }
        if (argument instanceof  PsiReferenceExpression referenceExpression &&
            referenceExpression.resolve() instanceof PsiVariable variable) {
          List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable, method);
          if (ContainerUtil.exists(references, ref -> ref.getTextOffset() > argument.getTextOffset())) {
            return true;
          }
        }
      }
      return false;
    }

    private static boolean isKnownExceptionalSideEffectCaught(PsiExpression call) {
      String exception = null;
      if (call instanceof PsiMethodCallExpression) {
        exception = KNOWN_EXCEPTIONAL_SIDE_EFFECTS.mapFirst((PsiMethodCallExpression)call);
      }
      else if (call instanceof PsiMethodReferenceExpression) {
        exception = KNOWN_EXCEPTIONAL_SIDE_EFFECTS.mapFirst((PsiMethodReferenceExpression)call);
      }
      if (exception == null) return false;
      PsiClass exceptionClass = JavaPsiFacade.getInstance(call.getProject()).findClass(exception, call.getResolveScope());
      if (exceptionClass == null) return false;
      PsiTryStatement parentTry = PsiTreeUtil.getParentOfType(call, PsiTryStatement.class);
      if (parentTry == null || !PsiTreeUtil.isAncestor(parentTry.getTryBlock(), call, true)) return false;
      return ContainerUtil.exists(ExceptionUtils.getExceptionTypesHandled(parentTry),
                                  type -> InheritanceUtil.isInheritor(exceptionClass, type.getCanonicalText()));
    }

    private static boolean isHardcodedException(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression call)) return false;
      if (STREAM_COLLECT.test(call)) {
        PsiMethodCallExpression collector =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiMethodCallExpression.class);
        if (COLLECTOR_TO_COLLECTION.test(collector)) {
          PsiLambdaExpression lambda = ObjectUtils
            .tryCast(PsiUtil.skipParenthesizedExprDown(collector.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
          if (lambda != null) {
            PsiExpression body = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
            if (body instanceof PsiReferenceExpression && ((PsiReferenceExpression)body).resolve() instanceof PsiVariable) {
              // .collect(toCollection(() -> var)) : the result is written into given collection
              return true;
            }
          }
        }
      }

      return false;
    }

    private static boolean isPureMethod(PsiMethod method, PsiExpression call) {
      final boolean honorInferred = Registry.is("ide.ignore.call.result.inspection.honor.inferred.pure");
      if (!honorInferred && !JavaMethodContractUtil.hasExplicitContractAnnotation(method)) return false;
      if (!JavaMethodContractUtil.isPure(method) || hasTrivialReturnValue(method)) return false;
      if (!SideEffectChecker.mayHaveExceptionalSideEffect(method)) return true;
      if (!(call instanceof PsiCallExpression) || JavaMethodContractUtil.getMethodCallContracts(method, null).isEmpty()) return false;
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(call);
      return result != null && result.cannotFailByContract((PsiCallExpression)call);
    }

    private static boolean isInTestContainer(PsiExpression call) {
      PsiElement psiElement = PsiTreeUtil.getNonStrictParentOfType(call, PsiFunctionalExpression.class, PsiAnonymousClass.class);
      PsiElement expressionList;
      if (psiElement instanceof PsiFunctionalExpression expression) {
        PsiType lambdaType = expression.getFunctionalInterfaceType();
        if (lambdaType == null || InheritanceUtil.isInheritor(lambdaType, JAVA_UTIL_FUNCTION_SUPPLIER)) return false;
        PsiElement skipParenthesizedExprUp = PsiUtil.skipParenthesizedExprUp(expression);
        if (skipParenthesizedExprUp == null) return false;
        expressionList = PsiUtil.skipParenthesizedExprUp(skipParenthesizedExprUp.getParent());
      }
      else if (psiElement instanceof PsiAnonymousClass psiAnonymousClass) {
        if (!LambdaUtil.isFunctionalType(psiAnonymousClass.getBaseClassType()) ||
            InheritanceUtil.isInheritor(psiAnonymousClass, JAVA_UTIL_FUNCTION_SUPPLIER)) return false;
        if (!(psiAnonymousClass.getParent() instanceof PsiNewExpression psiNewExpression)) return false;
        PsiElement skipParenthesizedExprUp = PsiUtil.skipParenthesizedExprUp(psiNewExpression);
        if (skipParenthesizedExprUp == null) return false;
        expressionList = PsiUtil.skipParenthesizedExprUp(skipParenthesizedExprUp.getParent());
      }
      else {
        return false;
      }
      if (!(expressionList instanceof PsiExpressionList psiExpressionList) ||
          !(psiExpressionList.getParent() instanceof PsiMethodCallExpression methodCallExpression)) return false;
      return TEST_OR_MOCK_CONTAINER_METHODS.test(methodCallExpression);
    }

    private static boolean hasTrivialReturnValue(PsiMethod method) {
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
      ContractReturnValue nonFailingReturnValue = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
      return nonFailingReturnValue != null &&
             (nonFailingReturnValue.equals(ContractReturnValue.returnThis()) ||
              nonFailingReturnValue instanceof ContractReturnValue.ParameterReturnValue);
    }

    private void registerMethodCallOrRefError(PsiExpression call, PsiClass aClass) {
      if (call instanceof PsiMethodCallExpression) {
        registerMethodCallError((PsiMethodCallExpression)call, aClass);
      }
      else if (call instanceof PsiMethodReferenceExpression){
        registerError(ObjectUtils.notNull(((PsiMethodReferenceExpression)call).getReferenceNameElement(), call), aClass);
      }
    }
  }
}