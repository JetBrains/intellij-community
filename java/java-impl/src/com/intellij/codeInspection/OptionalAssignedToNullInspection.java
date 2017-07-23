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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
public class OptionalAssignedToNullInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        checkNulls(expression.getType(), expression.getRExpression(),
                   InspectionsBundle.message("inspection.null.value.for.optional.context.assignment"));
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) return;
        PsiMethod method = call.resolveMethod();
        if (method == null) return;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > args.length) return;
        boolean varArgCall = MethodCallUtils.isVarArgCall(call);
        if (!varArgCall && parameters.length < args.length) return;
        for (int i = 0; i < args.length; i++) {
          PsiParameter parameter = parameters[Math.min(parameters.length - 1, i)];
          PsiType type = parameter.getType();
          if (varArgCall && i >= parameters.length - 1 && type instanceof PsiEllipsisType) {
            type = ((PsiEllipsisType)type).getComponentType();
          }
          checkNulls(type, args[i], InspectionsBundle.message("inspection.null.value.for.optional.context.parameter"));
        }
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        PsiElement body = lambda.getBody();
        if (body instanceof PsiExpression) {
          checkNulls(LambdaUtil.getFunctionalInterfaceReturnType(lambda), (PsiExpression)body,
                     InspectionsBundle.message("inspection.null.value.for.optional.context.lambda"));
        }
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        checkNulls(PsiTypesUtil.getMethodReturnType(statement), statement.getReturnValue(),
                   InspectionsBundle.message("inspection.null.value.for.optional.context.return"));
      }

      @Override
      public void visitVariable(PsiVariable variable) {
        checkNulls(variable.getType(), variable.getInitializer(),
                   InspectionsBundle.message("inspection.null.value.for.optional.context.declaration"));
      }

      private void checkNulls(PsiType type, PsiExpression expression, String declaration) {
        if (expression != null && TypeUtils.isOptional(type)) {
          ExpressionUtils.nonStructuralChildren(expression).filter(ExpressionUtils::isNullLiteral)
            .forEach(nullLiteral -> register(nullLiteral, (PsiClassType)type, declaration));
        }
      }

      private void register(PsiExpression expression, PsiClassType type, String contextName) {
        holder.registerProblem(expression,
                               InspectionsBundle.message("inspection.null.value.for.optional.message", contextName),
                               new ReplaceWithEmptyOptionalFix(type));
      }
    };
  }

  private static class ReplaceWithEmptyOptionalFix implements LocalQuickFix {
    private final String myTypeName;
    private final String myTypeParameter;
    private final String myMethodName;

    public ReplaceWithEmptyOptionalFix(PsiClassType type) {
      myTypeName = type.rawType().getCanonicalText();
      PsiType[] parameters = type.getParameters();
      myTypeParameter =
        parameters.length == 1 ? "<" + GenericsUtil.getVariableTypeByExpressionType(parameters[0]).getCanonicalText() + ">" : "";
      myMethodName = myTypeName.equals("com.google.common.base.Optional") ? "absent" : "empty";
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.null.value.for.optional.fix.name",
                                       StringUtil.getShortName(myTypeName) + "." + myMethodName + "()");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.null.value.for.optional.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiExpression)) return;
      String emptyCall = myTypeName + "." + myTypeParameter + myMethodName + "()";
      PsiElement result = element.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(emptyCall, element));
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
    }
  }
}
