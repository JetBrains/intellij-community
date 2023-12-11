/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.text.StringSearcher;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.*;

public final class ObsoleteCollectionInspection extends BaseInspection {
  private static final int MAX_OCCURRENCES = 20;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreRequiredObsoleteCollectionTypes = true;

  @Override
  @NotNull
  public String getID() {
    return "UseOfObsoleteCollectionType";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "use.obsolete.collection.type.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreRequiredObsoleteCollectionTypes", InspectionGadgetsBundle.message(
        "use.obsolete.collection.type.ignore.library.arguments.option"
      )));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObsoleteCollectionVisitor();
  }

  private class ObsoleteCollectionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final PsiType type = variable.getType();
      if (!isObsoleteCollectionType(type)) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      if (ignoreRequiredObsoleteCollectionTypes && checkReferences(variable)) {
        return;
      }
      registerError(typeElement);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiType returnType = method.getReturnType();
      if (!isObsoleteCollectionType(returnType)) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      if (typeElement == null) {
        return;
      }
      if (ignoreRequiredObsoleteCollectionTypes && checkReferences(method)) {
        return;
      }
      registerError(typeElement);
    }

    @Override
    public void visitNewExpression(
      @NotNull PsiNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      final PsiType type = newExpression.getType();
      if (!isObsoleteCollectionType(type)) {
        return;
      }
      if (ignoreRequiredObsoleteCollectionTypes &&
          isRequiredObsoleteCollectionElement(newExpression)) {
        return;
      }
      registerNewExpressionError(newExpression);
    }

    private static boolean isObsoleteCollectionType(@Nullable PsiType type) {
      if (type == null) {
        return false;
      }
      final PsiType deepComponentType = type.getDeepComponentType();
      final String className = TypeUtils.resolvedClassName(deepComponentType);
      return "java.util.Vector".equals(className) ||
             "java.util.Hashtable".equals(className) ||
             "java.util.Stack".equals(className);
    }

    private boolean checkReferences(PsiNamedElement namedElement) {
      final PsiFile containingFile = namedElement.getContainingFile();
      if (!isOnTheFly() || isCheapToSearchInFile(namedElement)) {
        return ReferencesSearch.search(namedElement, GlobalSearchScope.fileScope(containingFile))
          .anyMatch(ref -> isRequiredObsoleteCollectionElement(ref.getElement()));
      }
      return true;
    }

    private boolean isRequiredObsoleteCollectionElement(PsiElement element) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiVariable variable) {
        final PsiType variableType = variable.getType();
        if (isObsoleteCollectionType(variableType)) {
          return true;
        }
      }
      else if (parent instanceof PsiReturnStatement) {
        final PsiType returnType = PsiTypesUtil.getMethodReturnType(parent);
        if (isObsoleteCollectionType(returnType)) {
          return true;
        }
      }
      else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final PsiType lhsType = lhs.getType();
        if (isObsoleteCollectionType(lhsType)) {
          return true;
        }
      }
      else if (parent instanceof PsiMethodCallExpression) {
        return isRequiredObsoleteCollectionElement(parent);
      }
      if (!(parent instanceof PsiExpressionList argumentList)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiCallExpression callExpression)) {
        return false;
      }
      final int index = getIndexOfArgument(argumentList, element);
      final PsiMethod method = callExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiParameterList parameterList =
        method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (index >= parameters.length) {
        final PsiParameter lastParameter =
          parameters[parameters.length - 1];
        if (!lastParameter.isVarArgs()) {
          return false;
        }
        final PsiType type = lastParameter.getType();
        if (!(type instanceof PsiEllipsisType ellipsisType)) {
          return false;
        }
        final PsiType componentType = ellipsisType.getComponentType();
        return isObsoleteCollectionType(componentType);
      }
      final PsiParameter parameter = parameters[index];
      final PsiType type = parameter.getType();
      return isObsoleteCollectionType(type);
    }

    private static int getIndexOfArgument(PsiExpressionList argumentList,
                                          PsiElement argument) {
      final PsiExpression[] expressions =
        argumentList.getExpressions();
      int index = -1;
      for (PsiExpression expression : expressions) {
        index++;
        if (expression.equals(argument)) {
          break;
        }
      }
      return index;
    }
  }

  private static boolean isCheapToSearchInFile(@NotNull PsiNamedElement element) {
    if (element.getName() == null) return false;
    return CachedValuesManager.getCachedValue(element, () -> {
      PsiFile file = element.getContainingFile();
      return CachedValueProvider.Result.create(calcCheapEnoughToSearchInFile(element, file), file);
    });
  }

  private static boolean calcCheapEnoughToSearchInFile(@NotNull PsiNamedElement element, PsiFile file) {
    String name = element.getName();
    if (name == null) return false;
    StringSearcher searcher = new StringSearcher(name, true, true);
    CharSequence contents = file.getViewProvider().getContents();
    int[] count = new int[1];
    return searcher.processOccurrences(contents, __->++count[0] <= MAX_OCCURRENCES);
  }
}