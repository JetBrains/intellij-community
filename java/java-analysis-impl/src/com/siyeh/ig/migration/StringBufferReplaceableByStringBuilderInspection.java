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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class StringBufferReplaceableByStringBuilderInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getID() {
    return "StringBufferMayBeStringBuilder";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.builder.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new StringBufferMayBeStringBuilderFix();
  }

  @Nullable
  private static PsiExpression getNewStringBuffer(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return null;
    }
    else if (expression instanceof PsiNewExpression || ExpressionUtils.isNullLiteral(expression)) {
      return expression;
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if ("append".equals(methodName) || "appendCodePoint".equals(methodName) || "insert".equals(methodName)) {
        return getNewStringBuffer(methodExpression.getQualifierExpression());
      }
    }
    return null;
  }

  private static class StringBufferMayBeStringBuilderFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "StringBuilder");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass stringBuilderClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_STRING_BUILDER, element.getResolveScope());
      if (stringBuilderClass == null) {
        return;
      }
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiJavaCodeReferenceElement stringBuilderClassReference = factory.createClassReferenceElement(stringBuilderClass);
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiDeclarationStatement declarationStatement)) {
        return;
      }
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiVariable variable)) {
          continue;
        }
        replaceWithStringBuilder(stringBuilderClassReference, variable);
        replaceAssignmentsWithStringBuilder(variable, stringBuilderClassReference);
      }
    }

    private static void replaceAssignmentsWithStringBuilder(PsiVariable variable, PsiJavaCodeReferenceElement stringBuilderClassReference) {
      final List<PsiReferenceExpression> references =
        VariableAccessUtils.getVariableReferences(variable, PsiUtil.getVariableCodeBlock(variable, null));
      for (PsiReference reference : references) {
        final PsiElement referenceElement = PsiUtil.skipParenthesizedExprUp(reference.getElement().getParent());
        if (referenceElement instanceof PsiAssignmentExpression assignmentExpression) {
          final PsiExpression rhs = assignmentExpression.getRExpression();
          final PsiExpression newExpression = getNewStringBuffer(rhs);
          if (!(newExpression instanceof PsiNewExpression)) {
            continue;
          }
          final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)newExpression).getClassReference();
          if (classReference == null) {
            continue;
          }
          classReference.replace(stringBuilderClassReference);
        }
      }
    }

    private static void replaceWithStringBuilder(PsiJavaCodeReferenceElement newClassReference, PsiVariable variable) {
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      if (!typeElement.isInferredType()) {
        final PsiJavaCodeReferenceElement oldReferenceElement = typeElement.getInnermostComponentReferenceElement();
        if (oldReferenceElement == null) {
          return;
        }
        else {
          oldReferenceElement.replace(newClassReference);
        }
      }
      final PsiExpression newExpression = getNewStringBuffer(variable.getInitializer());
      if (!(newExpression instanceof PsiNewExpression)) {
        return;
      }
      // no need to handle anonymous classes because StringBuffer is final
      final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)newExpression).getClassReference();
      if (classReference == null) {
        return;
      }
      classReference.replace(newClassReference);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferReplaceableByStringBuilderVisitor();
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  private static class StringBufferReplaceableByStringBuilderVisitor extends BaseInspectionVisitor {

    private static final Set<String> SAFE_CLASSES = ContainerUtil.newHashSet(CommonClassNames.JAVA_LANG_STRING_BUILDER,
                                                                             CommonClassNames.JAVA_LANG_STRING_BUFFER,
                                                                             CommonClassNames.JAVA_LANG_STRING);

    @Override
    public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      final PsiElement[] declaredElements = statement.getDeclaredElements();
      if (declaredElements.length == 0) {
        return;
      }
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable variable)) {
          return;
        }
        final PsiElement context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
        if (!isReplaceableStringBuffer(variable, context)) {
          return;
        }
      }
      final PsiLocalVariable firstVariable = (PsiLocalVariable)declaredElements[0];
      registerVariableError(firstVariable);
    }

    private static boolean isReplaceableStringBuffer(PsiVariable variable, PsiElement context) {
      if (context == null) {
        return false;
      }
      final PsiType type = variable.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type)) {
        return false;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null && getNewStringBuffer(initializer) == null) {
        return false;
      }
      final Predicate<PsiAssignmentExpression> skipFilter = e -> getNewStringBuffer(e.getRExpression()) != null;
      if (VariableAccessUtils.variableIsAssigned(variable, skipFilter, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsReturned(variable, context, true)) {
        return false;
      }
      if (VariableAccessUtils.variableIsUsedInInnerClass(variable, context)) {
        return false;
      }
      final Processor<PsiCall> processor = call -> {
        final PsiMethod method = call.resolveMethod();
        if (method == null) {
          return false;
        }
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
          return false;
        }
        final String fqName = aClass.getQualifiedName();
        if ("java.util.regex.Matcher".equals(fqName)) {
          if (!PsiUtil.isLanguageLevel9OrHigher(call)) {
            return false;
          }
          final String methodName = method.getName();
          if ("appendTail".equals(methodName)) {
            return call instanceof PsiExpression && isSafeStringBufferUsage((PsiExpression)call);
          }
          else if ("appendReplacement".equals(methodName)) {
            return true;
          }
        }
        return SAFE_CLASSES.contains(fqName);
      };
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context, true, processor)) {
        return false;
      }
      return true;
    }

    private static boolean isSafeStringBufferUsage(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (ExpressionUtils.isVoidContext(expression)) {
        return true;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiReferenceExpression) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiMethodCallExpression) {
          final String methodName = ((PsiReferenceExpression)parent).getReferenceName();
          if ("toString".equals(methodName)) {
            return true;
          }
          else if ("append".equals(methodName) || "appendCodePoint".equals(methodName) || "insert".equals(methodName)) {
            return isSafeStringBufferUsage((PsiExpression)grandParent);
          }
        }
      }
      return false;
    }
  }
}