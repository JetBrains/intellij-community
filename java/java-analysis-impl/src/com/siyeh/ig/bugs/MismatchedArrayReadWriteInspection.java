/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MismatchedArrayReadWriteInspection extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "MismatchedReadAndWriteOfArray";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean written = ((Boolean)infos[0]).booleanValue();
    if (written) {
      return InspectionGadgetsBundle.message(
        "mismatched.read.write.array.problem.descriptor.write.not.read");
    }
    else {
      return InspectionGadgetsBundle.message(
        "mismatched.read.write.array.problem.descriptor.read.not.write");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MismatchedArrayReadWriteVisitor();
  }

  private static class MismatchedArrayReadWriteVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (HighlightUtil.isSerializationImplicitlyUsedField(field)) {
        return;
      }
      final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
      if (!shouldCheckVariable(field, containingClass)) {
        return;
      }
      final ArrayReadWriteVisitor visitor = new ArrayReadWriteVisitor(field, !isZeroSizeArrayExpression(field.getInitializer()));
      containingClass.accept(visitor);
      final boolean written = visitor.isWritten();
      if (!visitor.isReferenced() || written == visitor.isRead() || UnusedSymbolUtil.isImplicitWrite(field)) {
        return;
      }
      registerFieldError(field, Boolean.valueOf(written));
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (!shouldCheckVariable(variable, codeBlock)) {
        return;
      }
      final ArrayReadWriteVisitor visitor = new ArrayReadWriteVisitor(variable, !isZeroSizeArrayExpression(variable.getInitializer()));
      codeBlock.accept(visitor);
      final boolean written = visitor.isWritten();
      if (!visitor.isReferenced() || written == visitor.isRead()) {
        return;
      }
      registerVariableError(variable, Boolean.valueOf(written));
    }

    private static boolean shouldCheckVariable(PsiVariable variable, PsiElement context) {
      return context != null && variable.getType().getArrayDimensions() != 0 && !mayBeAccessedElsewhere(variable.getInitializer());
    }

    static boolean mayBeAccessedElsewhere(PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiNewExpression newExpression) {
        final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
        return mayBeAccessedElsewhere(arrayInitializer);
      }
      else if (expression instanceof PsiArrayInitializerExpression arrayInitializerExpression) {
        for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
          if (mayBeAccessedElsewhere(initializer)) {
            return true;
          }
        }
        return false;
      }
      else if (expression instanceof PsiReferenceExpression) {
        return expression.getType() instanceof PsiArrayType || TypeUtils.isJavaLangObject(expression.getType());
      }
      else if (expression instanceof PsiArrayAccessExpression) {
        return true;
      }
      else if (expression instanceof PsiTypeCastExpression typeCastExpression) {
        return mayBeAccessedElsewhere(typeCastExpression.getOperand());
      }
      else if (expression instanceof PsiConditionalExpression conditionalExpression) {
        return mayBeAccessedElsewhere(conditionalExpression.getThenExpression()) ||
               mayBeAccessedElsewhere(conditionalExpression.getElseExpression());
      }
      else if (expression instanceof PsiMethodCallExpression methodCallExpression) {
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
          return true;
        }
        if (CloneUtils.isClone(method)) {
          return false;
        }
        @NonNls final String name = method.getName();
        if ("copyOf".equals(name) || "copyOfRange".equals(name)) {
          final PsiClass aClass = method.getContainingClass();
          if (aClass != null && CommonClassNames.JAVA_UTIL_ARRAYS.equals(aClass.getQualifiedName())) {
            return false;
          }
        }
        else if ("toArray".equals(name) && InheritanceUtil.isInheritor(method.getContainingClass(), "java.util.Collection")) {
          return false;
        }
        return true;
      }
      else if (expression instanceof PsiLiteralExpression) {
        return false;
      }
      return true;
    }

    static boolean isZeroSizeArrayExpression(PsiExpression initializer) {
      if (initializer == null) {
        return true;
      }
      if (initializer instanceof PsiNewExpression newExpression) {
        final PsiArrayInitializerExpression arrayInitializer = newExpression.getArrayInitializer();
        return arrayInitializer == null || isZeroSizeArrayExpression(arrayInitializer);
      }
      if (initializer instanceof PsiArrayInitializerExpression arrayInitializerExpression) {
        final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
        return initializers.length == 0;
      }
      return false;
    }

    private static class ArrayReadWriteVisitor extends JavaRecursiveElementWalkingVisitor {
      private final PsiVariable myVariable;
      private boolean myWritten;
      private boolean myRead = false;
      private boolean myIsReferenced = false;

      ArrayReadWriteVisitor(@NotNull PsiVariable variable, boolean written) {
        myVariable = variable;
        myWritten = written;
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (myWritten && myRead) {
          return;
        }
        super.visitReferenceExpression(expression);
        if (!expression.isReferenceTo(myVariable)) return;
        if (PsiUtil.isAccessedForWriting(expression)) {
          final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
          if (parent instanceof PsiAssignmentExpression assignmentExpression) {
            final PsiExpression rhs = assignmentExpression.getRExpression();
            if (mayBeAccessedElsewhere(rhs)) {
              myWritten = true;
              myRead = true;
            }
            else if (!isZeroSizeArrayExpression(rhs)) {
              myWritten = true;
            }
            if (!ExpressionUtils.isVoidContext(assignmentExpression)) {
              myWritten = true;
              myRead = true;
            }
          }
          return;
        }
        myIsReferenced = true;
        PsiElement parent = getParent(expression);
        if (parent instanceof PsiArrayAccessExpression arrayAccessExpression) {
          parent = getParent(parent);
          while (parent instanceof PsiArrayAccessExpression &&
                 ((PsiArrayAccessExpression)parent).getArrayExpression() == arrayAccessExpression) {
            arrayAccessExpression = (PsiArrayAccessExpression)parent;
            parent = getParent(parent);
          }
          final PsiType type = arrayAccessExpression.getType();
          if (type != null) {
            final int dimensions = type.getArrayDimensions();
            if (dimensions > 0 && dimensions != myVariable.getType().getArrayDimensions()) {
              myWritten = true;
            }
          }
          if (PsiUtil.isAccessedForWriting(arrayAccessExpression)) {
            myWritten = true;
          }
          if (PsiUtil.isAccessedForReading(arrayAccessExpression) && !goesToSameArray(arrayAccessExpression) &&
              // compound writes like += are not considered as reads, because result result goes to the same array as well
              (!PsiUtil.isAccessedForWriting(arrayAccessExpression) || 
               parent instanceof PsiExpression && !ExpressionUtils.isVoidContext((PsiExpression)parent))) {
            myRead = true;
          }
        }
        else if (parent instanceof PsiReferenceExpression referenceExpression) {
          final String name = referenceExpression.getReferenceName();
          if ("clone".equals(name) && referenceExpression.getParent() instanceof PsiMethodCallExpression) {
            myRead = true;
          }
        }
        else if (parent instanceof PsiForeachStatement foreachStatement) {
          final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
          if (PsiTreeUtil.isAncestor(iteratedValue, expression, false)) {
            myRead = true;
          }
        }
        else if (parent instanceof PsiExpressionList expressionList) {
          parent = parent.getParent();
          if (parent instanceof PsiMethodCallExpression methodCallExpression) {
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method != null) {
              final PsiClass aClass = method.getContainingClass();
              if (aClass != null) {
                final String methodName = method.getName();
                final String qualifiedName = aClass.getQualifiedName();
                if ("java.lang.System".equals(qualifiedName)) {
                  if ("arraycopy".equals(methodName)) {
                    final PsiExpression[] expressions = expressionList.getExpressions();
                    if (expressions.length == 5) {
                      if (PsiTreeUtil.isAncestor(expressions[0], expression, false)) {
                        myRead = true;
                        return;
                      }
                      else if (PsiTreeUtil.isAncestor(expressions[2], expression, false)) {
                        myWritten = true;
                        return;
                      }
                    }
                  }
                }
                else if (CommonClassNames.JAVA_UTIL_ARRAYS.equals(qualifiedName)) {
                  if (("fill".equals(methodName) || "parallelPrefix".equals(methodName) || "parallelSetAll".equals(methodName) ||
                      "parallelSort".equals(methodName) || "setAll".equals(methodName) || "sort".equals(methodName)) &&
                      PsiTreeUtil.isAncestor(expressionList.getExpressions()[0], expression, false)) {
                    myWritten = true;
                  }
                  else {
                    myRead = true;
                  }
                  return;
                }
              }
            }
          }
          myRead = true;
          myWritten = true;
        }
        else {
          myWritten = true;
          myRead = true;
        }
      }

      private boolean goesToSameArray(PsiExpression expression) {
        PsiExpression parent = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiExpression.class);
        if (parent == null) return false;
        if (parent instanceof PsiPolyadicExpression || parent instanceof PsiUnaryExpression || 
            parent instanceof PsiInstanceOfExpression || parent instanceof PsiConditionalExpression) {
          return goesToSameArray(parent);
        }
        if (parent instanceof PsiAssignmentExpression) {
          PsiExpression left = PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getLExpression());
          if (left instanceof PsiArrayAccessExpression && !PsiTreeUtil.isAncestor(left, expression, false)) {
            PsiExpression arrayExpression = ((PsiArrayAccessExpression)left).getArrayExpression();
            return ExpressionUtils.isReferenceTo(arrayExpression, myVariable);
          }
        }
        return false;
      }

      private static PsiElement getParent(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent instanceof PsiParenthesizedExpression ||
               parent instanceof PsiTypeCastExpression ||
               parent instanceof PsiConditionalExpression) {
          parent = parent.getParent();
        }
        return parent;
      }

      public boolean isRead() {
        return myRead;
      }

      public boolean isWritten() {
        return myWritten;
      }

      public boolean isReferenced() {
        return myIsReferenced;
      }
    }
  }
}