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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class InlineStreamMapAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(InlineStreamMapAction.class.getName());

  private static final Set<String> MAP_METHODS =
    StreamEx.of("map", "mapToInt", "mapToLong", "mapToDouble", "mapToObj", "boxed", "asLongStream", "asDoubleStream").toSet();

  public static final Set<String> NEXT_METHODS = StreamEx
    .of("flatMap", "flatMapToInt", "flatMapToLong", "flatMapToDouble", "forEach", "forEachOrdered", "anyMatch", "noneMatch", "allMatch")
    .append(MAP_METHODS).toSet();

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull final PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return false;
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression)) return false;
    final PsiElement gParent = parent.getParent();
    if (!(gParent instanceof PsiMethodCallExpression)) return false;
    PsiMethodCallExpression curCall = (PsiMethodCallExpression)gParent;
    if (!isMapCall(curCall)) return false;
    PsiMethodCallExpression nextCall = getNextExpressionToMerge(curCall);
    if(nextCall == null) return false;
    String key = curCall.getArgumentList().isEmpty() || nextCall.getArgumentList().isEmpty() ?
                 "intention.inline.map.merge.text" : "intention.inline.map.inline.text";
    setText(CodeInsightBundle.message(key, element.getText(), nextCall.getMethodExpression().getReferenceName()));
    return true;
  }

  private static boolean isMapCall(@NotNull PsiMethodCallExpression methodCallExpression) {
    String name = methodCallExpression.getMethodExpression().getReferenceName();
    if (name == null || !MAP_METHODS.contains(name)) return false;

    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    if (!name.startsWith("map") && expressions.length == 0) return true;
    if (expressions.length != 1) return false;
    if (!StreamRefactoringUtil.isRefactoringCandidate(expressions[0], true)) return false;

    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return false;
    final PsiClass containingClass = method.getContainingClass();
    return InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM);
  }

  @Nullable
  private static PsiMethodCallExpression getNextExpressionToMerge(PsiMethodCallExpression methodCallExpression) {
    PsiElement parent = methodCallExpression.getParent();
    if(!(parent instanceof PsiReferenceExpression)) return null;
    PsiElement gParent = parent.getParent();
    if(!(gParent instanceof PsiMethodCallExpression)) return null;
    String nextName = ((PsiReferenceExpression)parent).getReferenceName();
    PsiMethodCallExpression nextCall = (PsiMethodCallExpression)gParent;
    if(nextName == null || !NEXT_METHODS.contains(nextName) || translateName(methodCallExpression, nextCall) == null) return null;
    PsiExpressionList argumentList = (nextCall).getArgumentList();
    PsiExpression[] expressions = argumentList.getExpressions();
    if(expressions.length == 0) {
      if (!nextName.equals("boxed") && !nextName.equals("asLongStream") && !nextName.equals("asDoubleStream")) return null;
      return nextCall;
    }
    if (expressions.length != 1 || !StreamRefactoringUtil.isRefactoringCandidate(expressions[0], false)) return null;

    return nextCall;
  }

  /**
   * Generate name of joint method call which combines two given calls
   *
   * @param prevCall previous call (assumed to be in MAP_METHODS)
   * @param nextCall next call (assumed to be in NEXT_METHODS)
   * @return a name of the resulting method
   */
  @Nullable
  private static String translateName(@NotNull PsiMethodCallExpression prevCall, @NotNull PsiMethodCallExpression nextCall) {
    PsiMethod nextMethod = nextCall.resolveMethod();
    if (nextMethod == null) return null;
    String nextName = nextMethod.getName();
    PsiMethod method = prevCall.resolveMethod();
    if (method == null) return null;
    PsiClass prevClass = method.getContainingClass();
    if (prevClass == null) return null;
    String prevClassName = prevClass.getQualifiedName();
    if (prevClassName == null) return null;
    String prevName = method.getName();
    if (nextName.endsWith("Match") || nextName.startsWith("forEach")) return nextName;
    if (nextName.equals("map")) {
      return translateMap(prevName);
    }
    if (prevName.equals("map")) {
      return translateMap(nextName);
    }
    if(MAP_METHODS.contains(nextName)) {
      PsiType type = nextMethod.getReturnType();
      if(!(type instanceof PsiClassType)) return null;
      PsiClass nextClass = ((PsiClassType)type).resolve();
      if(nextClass == null) return null;
      String nextClassName = nextClass.getQualifiedName();
      if(nextClassName == null) return null;
      if(prevClassName.equals(nextClassName)) return "map";
      switch(nextClassName) {
        case CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM:
          return "mapToInt";
        case CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM:
          return "mapToLong";
        case CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM:
          return "mapToDouble";
        case CommonClassNames.JAVA_UTIL_STREAM_STREAM:
          return "mapToObj";
        default:
          return null;
      }
    }
    if(nextName.equals("flatMap") && prevClassName.equals(CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
      return mapToFlatMap(prevName);
    }
    return null;
  }

  @Contract(pure = true)
  @Nullable
  private static String mapToFlatMap(String mapMethod) {
    switch (mapMethod) {
      case "map":
        return "flatMap";
      case "mapToInt":
        return "flatMapToInt";
      case "mapToLong":
        return "flatMapToLong";
      case "mapToDouble":
        return "flatMapToDouble";
    }
    // Something unsupported passed: ignore
    return null;
  }

  @Contract(pure = true)
  @NotNull
  private static String translateMap(String nextMethod) {
    switch (nextMethod) {
      case "boxed":
        return "mapToObj";
      case "asLongStream":
        return "mapToLong";
      case "asDoubleStream":
        return "mapToDouble";
      default:
        return nextMethod;
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.inline.map.family");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiMethodCallExpression mapCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if(mapCall == null) return;

    PsiMethodCallExpression nextCall = getNextExpressionToMerge(mapCall);
    if(nextCall == null) return;

    PsiReferenceExpression nextRef = nextCall.getMethodExpression();
    PsiExpression nextQualifier = nextRef.getQualifierExpression();
    if(nextQualifier == null) return;

    String newName = translateName(mapCall, nextCall);
    if(newName == null) return;

    PsiLambdaExpression previousLambda = getLambda(mapCall);

    LOG.assertTrue(previousLambda != null);
    PsiExpression previousBody = LambdaUtil.extractSingleExpressionFromBody(previousLambda.getBody());
    LOG.assertTrue(previousBody != null);

    PsiLambdaExpression lambda = getLambda(nextCall);
    LOG.assertTrue(lambda != null);

    CommentTracker ct = new CommentTracker();

    if(!lambda.isPhysical()) {
      lambda = (PsiLambdaExpression)nextCall.getArgumentList().add(lambda);
    }
    PsiElement body = lambda.getBody();
    LOG.assertTrue(body != null);
    ct.markUnchanged(body);

    PsiParameter[] nextParameters = lambda.getParameterList().getParameters();
    LOG.assertTrue(nextParameters.length == 1);
    PsiParameter[] prevParameters = previousLambda.getParameterList().getParameters();
    LOG.assertTrue(prevParameters.length == 1);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    for(PsiReference ref : ReferencesSearch.search(nextParameters[0], new LocalSearchScope(body)).findAll()) {
      PsiElement e = ref.getElement();
      PsiExpression replacement = ct.markUnchanged(previousBody);
      if (e.getParent() instanceof PsiExpression &&
          ParenthesesUtils.areParenthesesNeeded(previousBody, (PsiExpression)e.getParent(), false)) {
        replacement = factory.createExpressionFromText("(a)", e);
        PsiExpression parenthesized = ((PsiParenthesizedExpression)replacement).getExpression();
        LOG.assertTrue(parenthesized != null);
        parenthesized.replace(previousBody);
      }
      ct.replace(e, replacement);
    }
    ct.replace(nextParameters[0], prevParameters[0]);
    ExpressionUtils.bindReferenceTo(nextRef, newName);
    PsiExpression prevQualifier = mapCall.getMethodExpression().getQualifierExpression();
    if(prevQualifier == null) {
      ct.deleteAndRestoreComments(nextQualifier);
    } else {
      ct.replaceAndRestoreComments(nextQualifier, prevQualifier);
    }
    CodeStyleManager.getInstance(project).reformat(lambda);
  }

  @Nullable
  private static PsiLambdaExpression getLambda(PsiMethodCallExpression call) {
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if(expressions.length == 1) {
      PsiExpression expression = expressions[0];
      if(expression instanceof PsiLambdaExpression) return (PsiLambdaExpression)expression;
      if(expression instanceof PsiMethodReferenceExpression) {
        return LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)expression, false, true);
      }
      return null;
    }
    if(expressions.length != 0) return null;
    PsiMethod method = call.resolveMethod();
    if(method == null) return null;
    PsiClass containingClass = method.getContainingClass();
    if(containingClass == null) return null;
    String className = containingClass.getQualifiedName();
    if(className == null) return null;
    String varName;
    String type;
    switch (className) {
      case CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM:
        varName = "i";
        type = CommonClassNames.JAVA_LANG_INTEGER;
        break;
      case CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM:
        varName = "l";
        type = CommonClassNames.JAVA_LANG_LONG;
        break;
      case CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM:
        varName = "d";
        type = CommonClassNames.JAVA_LANG_DOUBLE;
        break;
      default:
        return null;
    }
    varName = JavaCodeStyleManager.getInstance(call.getProject()).suggestUniqueVariableName(varName, call, true);
    String expression;
    if("boxed".equals(method.getName())) {
      expression = varName+" -> ("+type+")"+varName;
    } else if("asLongStream".equals(method.getName())) {
      expression = varName+" -> (long)"+varName;
    } else if("asDoubleStream".equals(method.getName())) {
      expression = varName+" -> (double)"+varName;
    } else return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());
    return (PsiLambdaExpression)factory.createExpressionFromText(expression, call);
  }
}
