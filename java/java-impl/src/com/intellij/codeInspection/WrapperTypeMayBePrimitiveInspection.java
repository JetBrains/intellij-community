// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.NullnessUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public class WrapperTypeMayBePrimitiveInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        if (!isBoxedType(variable.getType())) return;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null && !(initializer.getType() instanceof PsiPrimitiveType)) {
          return;
        }
        if (ExpressionUtils.isNullLiteral(variable.getInitializer())) return;
        PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
        if (block == null) return;
        BoxingVisitor visitor = new BoxingVisitor(variable);
        block.accept(visitor);
        if (visitor.myBoxingRequired || !visitor.myHasReferences) return;
        holder.registerProblem(variable.getTypeElement(), InspectionsBundle.message("inspection.wrapper.type.may.be.primitive.name"),
                               new ConvertWrapperTypeToPrimitive());
      }
    };
  }

  private static class BoxingVisitor extends JavaRecursiveElementWalkingVisitor {
    private final PsiLocalVariable myVariable;
    boolean myBoxingRequired = false;
    boolean myHasReferences = false;

    public BoxingVisitor(PsiLocalVariable variable) {
      myVariable = variable;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (!ExpressionUtils.isReferenceTo(expression, myVariable)) return;
      myHasReferences = true;
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiConditionalExpression) {
        boxingRequired();
      }
      else if (parent instanceof PsiExpressionList) {
        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiCallExpression)) return;
        PsiExpression[] arguments = ((PsiExpressionList)parent).getExpressions();
        int argumentsIndex = ArrayUtil.indexOf(arguments, expression);
        if (argumentsIndex == -1) return;
        PsiMethod method = ((PsiCallExpression)grandParent).resolveMethod();
        if (method == null) return;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        int parameterIndex = parameters.length > argumentsIndex ? parameters.length - 1 : argumentsIndex;
        PsiParameter parameter = parameters[parameterIndex];
        if (parameter.getType() instanceof PsiPrimitiveType) return;
        boxingRequired();
      }
      else if (parent instanceof PsiAssignmentExpression) {
        PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
        if (rExpression == null) return;
        if (isNotNull(rExpression)) return;
        boxingRequired();
      }
    }

    private void boxingRequired() {
      myBoxingRequired = true;
      stopWalking();
    }
  }

  private static boolean isNotNull(PsiExpression rExpression) {
    return NullnessUtil.getExpressionNullness(rExpression) == Nullness.NOT_NULL;
  }

  private static boolean isBoxedType(@NotNull PsiType type) {
    return type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_LONG) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_DOUBLE) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_FLOAT) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER);
  }

  private static class ConvertWrapperTypeToPrimitive implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.wrapper.type.may.be.primitive.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      PsiTypeElement typeElement = tryCast(element, PsiTypeElement.class);
      if (typeElement == null) return;
      PsiLocalVariable variable = tryCast(typeElement.getParent(), PsiLocalVariable.class);
      if (variable == null) return;
      PsiType type = variable.getType();
      String boxedType = type.getCanonicalText();
      String unboxedType = PsiTypesUtil.unboxIfPossible(boxedType);
      if (unboxedType.equals(boxedType)) return;
      new CommentTracker().replaceAndRestoreComments(typeElement, unboxedType);
    }
  }
}
