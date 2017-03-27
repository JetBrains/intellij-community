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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.siyeh.ig.psiutils.MethodCallUtils.getQualifierMethodCall;

/**
 * @author Tagir Valeev
 */
public class ReplaceInefficientStreamCountInspection extends BaseJavaBatchLocalInspectionTool {
  private static final CallMatcher STREAM_COUNT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "count").parameterCount(0);
  private static final CallMatcher COLLECTION_STREAM =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "stream").parameterCount(0);
  private static final CallMatcher STREAM_FLAT_MAP =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "flatMap").parameterTypes(
      CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION);

  private static final CallMapper<CountFix> FIX_MAPPER = new CallMapper<CountFix>()
    .register(COLLECTION_STREAM, call -> new CountFix(false))
    .register(STREAM_FLAT_MAP, call -> doesFlatMapCallCollectionStream(call) ? new CountFix(true) : null);

  private static final Logger LOG = Logger.getInstance(ReplaceInefficientStreamCountInspection.class);

  private static final String SIZE_METHOD = "size";
  private static final String STREAM_METHOD = "stream";

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
        if (STREAM_COUNT.test(methodCall)) {
          PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
          CountFix fix = FIX_MAPPER.mapFirst(qualifierCall);
          if (fix != null) {
            PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
            LOG.assertTrue(nameElement != null);
            holder.registerProblem(methodCall, nameElement.getTextRange().shiftRight(-methodCall.getTextOffset()), fix.getMessage(), fix);
          }
        }
      }
    };
  }

  static boolean doesFlatMapCallCollectionStream(PsiMethodCallExpression flatMapCall) {
    PsiElement function = flatMapCall.getArgumentList().getExpressions()[0];
    if (function instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)function;
      if (!STREAM_METHOD.equals(methodRef.getReferenceName())) return false;
      PsiMethod method = ObjectUtils.tryCast(methodRef.resolve(), PsiMethod.class);
      if (method != null && STREAM_METHOD.equals(method.getName()) && method.getParameterList().getParametersCount() == 0) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_UTIL_COLLECTION.equals(containingClass.getQualifiedName())) {
          return true;
        }
      }
    }
    else if (function instanceof PsiLambdaExpression) {
      PsiExpression expression = extractLambdaReturnExpression((PsiLambdaExpression)function);
      return expression instanceof PsiMethodCallExpression && COLLECTION_STREAM.test((PsiMethodCallExpression)expression);
    }
    return false;
  }

  @Nullable
  private static PsiExpression extractLambdaReturnExpression(PsiLambdaExpression lambda) {
    PsiElement lambdaBody = lambda.getBody();
    PsiExpression expression = null;
    if (lambdaBody instanceof PsiExpression) {
      expression = (PsiExpression)lambdaBody;
    }
    else if (lambdaBody instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)lambdaBody).getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
        expression = ((PsiReturnStatement)statements[0]).getReturnValue();
      }
    }
    return PsiUtil.skipParenthesizedExprDown(expression);
  }

  private static class CountFix implements LocalQuickFix {
    private final boolean myFlatMapMode;

    CountFix(boolean flatMapMode) {
      myFlatMapMode = flatMapMode;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myFlatMapMode
             ? "Replace Stream.flatMap().count() with Stream.mapToLong().sum()"
             : "Replace Collection.stream().count() with Collection.size()";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace inefficient Stream.count()";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiMethodCallExpression)) return;
      PsiMethodCallExpression countCall = (PsiMethodCallExpression)element;
      PsiElement countName = countCall.getMethodExpression().getReferenceNameElement();
      if (countName == null) return;
      PsiMethodCallExpression qualifierCall = getQualifierMethodCall(countCall);
      if (qualifierCall == null) return;
      if(myFlatMapMode) {
        replaceFlatMap(countName, qualifierCall);
      }
      else {
        replaceSimpleCount(countCall, qualifierCall);
      }
    }

    private static void replaceSimpleCount(PsiMethodCallExpression countCall, PsiMethodCallExpression qualifierCall) {
      if (!COLLECTION_STREAM.test(qualifierCall)) return;
      PsiReferenceExpression methodExpression = qualifierCall.getMethodExpression();
      ExpressionUtils.bindCallTo(qualifierCall, SIZE_METHOD);
      boolean addCast = true;
      PsiElement toReplace = countCall;
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(countCall.getParent());
      if(parent instanceof PsiExpressionStatement) {
        addCast = false;
      } else if(parent instanceof PsiTypeCastExpression) {
        PsiTypeElement castElement = ((PsiTypeCastExpression)parent).getCastType();
        if(castElement != null && castElement.getType() instanceof PsiPrimitiveType) {
          addCast = false;
          if(PsiType.INT.equals(castElement.getType())) {
            toReplace = parent;
          }
        }
      }
      CommentTracker ct = new CommentTracker();
      PsiReferenceParameterList parameterList = methodExpression.getParameterList();
      if(parameterList != null) {
        ct.delete(parameterList);
      }
      String replacementText = (addCast ? "(long) " : "") + ct.text(methodExpression)+"()";
      PsiElement replacement = ct.replaceAndRestoreComments(toReplace, replacementText);
      if (replacement instanceof PsiTypeCastExpression && RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)replacement)) {
        RedundantCastUtil.removeCast((PsiTypeCastExpression)replacement);
      }
    }

    private static void replaceFlatMap(PsiElement countName, PsiMethodCallExpression qualifierCall) {
      if (!STREAM_FLAT_MAP.test(qualifierCall)) return;
      PsiElement flatMapName = qualifierCall.getMethodExpression().getReferenceNameElement();
      if (flatMapName == null) return;
      PsiElement parameter = qualifierCall.getArgumentList().getExpressions()[0];
      PsiElement streamCallName = null;
      if (parameter instanceof PsiMethodReferenceExpression) {
        PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)parameter;
        streamCallName = methodRef.getReferenceNameElement();
      }
      else if (parameter instanceof PsiLambdaExpression) {
        PsiExpression expression = extractLambdaReturnExpression((PsiLambdaExpression)parameter);
        if (expression instanceof PsiMethodCallExpression) {
          streamCallName = ((PsiMethodCallExpression)expression).getMethodExpression().getReferenceNameElement();
        }
      }
      if (streamCallName == null || !streamCallName.getText().equals("stream")) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(qualifierCall.getProject());
      streamCallName.replace(factory.createIdentifier("size"));
      flatMapName.replace(factory.createIdentifier("mapToLong"));
      countName.replace(factory.createIdentifier("sum"));
      PsiReferenceParameterList parameterList = qualifierCall.getMethodExpression().getParameterList();
      if(parameterList != null) {
        parameterList.delete();
      }
    }

    public String getMessage() {
      return myFlatMapMode ? "Stream.flatMap().count() can be replaced with Stream.mapToLong().sum()" :
             "Collection.stream().count() can be replaced with Collection.size()";
    }
  }
}
