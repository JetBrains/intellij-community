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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.SimplifyStreamApiCallChainsInspection.getQualifierMethodCall;
import static com.intellij.codeInspection.SimplifyStreamApiCallChainsInspection.isCallOf;

/**
 * @author Tagir Valeev
 */
public class ReplaceInefficientStreamCountInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ReplaceInefficientStreamCountInspection.class);

  private static final String COUNT_METHOD = "count";
  private static final String SIZE_METHOD = "size";
  private static final String STREAM_METHOD = "stream";
  private static final String FLAT_MAP_METHOD = "flatMap";

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
        if (isCallOf(method, CommonClassNames.JAVA_UTIL_STREAM_STREAM, COUNT_METHOD, 0)) {
          final PsiMethodCallExpression qualifierCall = getQualifierMethodCall(methodCall);
          if (qualifierCall == null) return;
          final PsiMethod qualifier = qualifierCall.resolveMethod();
          final CountFix fix;
          if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
            fix = new CountFix(false);
          }
          else if (isCallOf(qualifier, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FLAT_MAP_METHOD, 1) &&
                   doesFlatMapCallCollectionStream(qualifierCall)) {
            fix = new CountFix(true);
          }
          else {
            return;
          }
          PsiElement nameElement = methodCall.getMethodExpression().getReferenceNameElement();
          LOG.assertTrue(nameElement != null);
          holder.registerProblem(methodCall, nameElement.getTextRange().shiftRight(-methodCall.getTextOffset()), fix.getMessage(), fix);
        }
      }
    };
  }

  boolean doesFlatMapCallCollectionStream(PsiMethodCallExpression flatMapCall) {
    PsiExpression[] parameters = flatMapCall.getArgumentList().getExpressions();
    if(parameters.length != 1) {
      return false;
    }
    PsiElement parameter = parameters[0];
    if (parameter instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)parameter;
      PsiElement resolvedMethodRef = methodRef.resolve();
      if (resolvedMethodRef instanceof PsiMethod && isCallOf((PsiMethod)resolvedMethodRef,
                                                             CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
        return true;
      }
    }
    else if (parameter instanceof PsiLambdaExpression) {
      PsiExpression expression = extractLambdaReturnExpression((PsiLambdaExpression)parameter);
      if (expression instanceof PsiMethodCallExpression) {
        PsiMethod lambdaMethod = ((PsiMethodCallExpression)expression).resolveMethod();
        if (isCallOf(lambdaMethod, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) {
          return true;
        }
      }
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
      PsiMethod qualifier = qualifierCall.resolveMethod();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if(myFlatMapMode) {
        replaceFlatMap(countName, qualifierCall, qualifier, factory);
      }
      else {
        replaceSimpleCount(countCall, qualifierCall, qualifier);
      }
    }

    private static void replaceSimpleCount(PsiMethodCallExpression countCall,
                                           PsiMethodCallExpression qualifierCall,
                                           PsiMethod qualifier) {
      if (!isCallOf(qualifier, CommonClassNames.JAVA_UTIL_COLLECTION, STREAM_METHOD, 0)) return;
      PsiReferenceExpression methodExpression = qualifierCall.getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if(qualifierExpression == null) return;
      methodExpression.handleElementRename(SIZE_METHOD);
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

    private static void replaceFlatMap(PsiElement countName,
                                       PsiMethodCallExpression qualifierCall,
                                       PsiMethod qualifier,
                                       PsiElementFactory factory) {
      if (!isCallOf(qualifier, CommonClassNames.JAVA_UTIL_STREAM_STREAM, FLAT_MAP_METHOD, 1)) return;
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
