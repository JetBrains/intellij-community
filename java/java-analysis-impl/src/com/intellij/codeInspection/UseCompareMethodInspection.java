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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Tagir Valeev
 */
public class UseCompareMethodInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_1_4)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        CompareInfo info = fromCall(call);
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (info != null && nameElement != null) {
          register(info, nameElement);
        }
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        CompareInfo info = fromIf(statement);
        PsiElement keyword = statement.getFirstChild();
        if (info != null && keyword != null) {
          register(info, keyword);
        }
      }

      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        CompareInfo info = fromTernary(expression);
        if (info != null) {
          register(info, expression);
        }
      }

      private void register(CompareInfo info, PsiElement nameElement) {
        holder.registerProblem(nameElement, "Can be replaced with '" + info.myClass.getClassName() + ".compare'",
                               new ReplaceWithPrimitiveCompareFix(info.myClass.getCanonicalText()));
      }
    };
  }

  private static CompareInfo fromIf(PsiIfStatement ifStatement) {
    PsiExpression firstCondition = ifStatement.getCondition();
    if (firstCondition == null) return null;
    PsiIfStatement elseIfStatement = tryCast(getElse(ifStatement), PsiIfStatement.class);
    if (elseIfStatement == null) return null;
    PsiExpression secondCondition = elseIfStatement.getCondition();
    if (secondCondition == null) return null;
    PsiStatement firstStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    if (firstStatement == null) return null;
    PsiStatement secondStatement = ControlFlowUtils.stripBraces(elseIfStatement.getThenBranch());
    if (secondStatement == null) return null;
    PsiStatement thirdStatement = getElse(elseIfStatement);
    if (thirdStatement == null) return null;

    Map<Integer, PsiExpression> result = new HashMap<>(3);
    // like if(...) return 1; else if(...) return -1; return 0;
    if (firstStatement instanceof PsiReturnStatement) {
      if (!(secondStatement instanceof PsiReturnStatement) || !(thirdStatement instanceof PsiReturnStatement)) return null;
      PsiExpression firstValue = ((PsiReturnStatement)firstStatement).getReturnValue();
      if (!storeCondition(result, firstCondition, firstValue)) return null;
      if (!storeCondition(result, secondCondition, ((PsiReturnStatement)secondStatement).getReturnValue())) return null;
      if (!storeCondition(result, null, ((PsiReturnStatement)thirdStatement).getReturnValue())) return null;
      return fromMap(result, firstValue, firstStatement);
    }
    // like if(...) x = 1; else if(...) x = -1; else x = 0;
    PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(firstStatement);
    if (assignment == null) return null;
    PsiReferenceExpression ref = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
    if (ref == null) return null;
    PsiVariable variable = tryCast(ref.resolve(), PsiVariable.class);
    if (variable == null) return null;
    PsiExpression firstExpression = assignment.getRExpression();
    if (!storeCondition(result, firstCondition, firstExpression)) return null;
    if (!storeCondition(result, secondCondition, ExpressionUtils.getAssignmentTo(secondStatement, variable))) return null;
    if (!storeCondition(result, null, ExpressionUtils.getAssignmentTo(thirdStatement, variable))) return null;
    return fromMap(result, firstExpression, assignment);
  }

  private static PsiStatement getElse(PsiIfStatement ifStatement) {
    PsiStatement branch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
    if (branch != null) return branch;
    PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    if (!(thenBranch instanceof PsiReturnStatement)) return null;
    PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement);
    return tryCast(next, PsiStatement.class);
  }

  @Nullable
  private static Map<Integer, PsiExpression> extractConditions(PsiConditionalExpression ternary) {
    Map<Integer, PsiExpression> result = new HashMap<>(3);
    if (!storeCondition(result, ternary.getCondition(), ternary.getThenExpression())) return null;
    PsiExpression elseExpression = PsiUtil.skipParenthesizedExprDown(ternary.getElseExpression());
    if (elseExpression instanceof PsiConditionalExpression) {
      Map<Integer, PsiExpression> m = extractConditions((PsiConditionalExpression)elseExpression);
      if (m == null) return null;
      result.putAll(m);
      return result;
    }
    return storeCondition(result, null, elseExpression) ? result : null;
  }

  @Contract("_, _, null -> false")
  private static boolean storeCondition(@NotNull Map<Integer, PsiExpression> result,
                                        @Nullable PsiExpression condition,
                                        @Nullable PsiExpression expression) {
    if (expression == null) return false;
    Object thenValue = ExpressionUtils.computeConstantExpression(expression);
    if (!(thenValue instanceof Integer) || Math.abs((Integer)thenValue) > 1) return false;
    result.put((Integer)thenValue, condition);
    return true;
  }

  private static CompareInfo fromTernary(PsiConditionalExpression ternary) {
    if (!PsiType.INT.equals(ternary.getType())) return null;
    Map<Integer, PsiExpression> map = extractConditions(ternary);
    return fromMap(map, ternary, ternary);
  }

  private static CompareInfo fromMap(@Nullable Map<Integer, PsiExpression> map,
                                     @NotNull PsiExpression expression,
                                     @NotNull PsiElement template) {
    if (map == null || map.size() != 3) {
      return null;
    }
    PsiExpression lt = map.get(-1);
    Pair<PsiExpression, PsiExpression> ltPair = getOperands(lt, JavaTokenType.LT);
    if (lt != null && ltPair == null) return null;

    PsiExpression gt = map.get(1);
    Pair<PsiExpression, PsiExpression> gtPair = getOperands(gt, JavaTokenType.GT);
    if ((gt != null || ltPair == null) && gtPair == null) return null;

    if (ltPair != null && gtPair != null) {
      if (!PsiEquivalenceUtil.areElementsEquivalent(ltPair.getFirst(), gtPair.getFirst())) return null;
      if (!PsiEquivalenceUtil.areElementsEquivalent(ltPair.getSecond(), gtPair.getSecond())) return null;
    }
    Pair<PsiExpression, PsiExpression> canonicalPair = ltPair == null ? gtPair : ltPair;
    PsiType leftType = canonicalPair.getFirst().getType();
    PsiType rightType = canonicalPair.getSecond().getType();
    if (!isTypeConvertible(leftType, expression) || !leftType.equals(rightType)) return null;

    PsiExpression eq = map.get(0);
    Pair<PsiExpression, PsiExpression> eqPair = getOperands(eq, JavaTokenType.EQEQ);
    if (eq != null && eqPair == null) return null;
    if (eqPair != null) {
      if ((!PsiEquivalenceUtil.areElementsEquivalent(canonicalPair.getFirst(), eqPair.getFirst()) ||
           !PsiEquivalenceUtil.areElementsEquivalent(canonicalPair.getSecond(), eqPair.getSecond())) &&
          (!PsiEquivalenceUtil.areElementsEquivalent(canonicalPair.getFirst(), eqPair.getSecond()) ||
           !PsiEquivalenceUtil.areElementsEquivalent(canonicalPair.getSecond(), eqPair.getFirst()))) {
        return null;
      }
    }
    PsiClassType boxedType = ((PsiPrimitiveType)leftType).getBoxedType(expression);
    if (boxedType == null) return null;
    return new CompareInfo(template, expression, canonicalPair.getFirst(), canonicalPair.getSecond(), boxedType);
  }

  private static Pair<PsiExpression, PsiExpression> getOperands(PsiExpression expression, IElementType expectedToken) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiBinaryExpression)) return null;
    PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
    PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
    PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
    if (left == null || right == null) return null;
    if (binOp.getOperationTokenType().equals(expectedToken)) {
      return Pair.create(left, right);
    }
    if (expectedToken.equals(JavaTokenType.GT) && binOp.getOperationTokenType().equals(JavaTokenType.LT) ||
        expectedToken.equals(JavaTokenType.LT) && binOp.getOperationTokenType().equals(JavaTokenType.GT)) {
      return Pair.create(right, left);
    }
    return null;
  }

  @Contract("null -> null")
  private static CompareInfo fromCall(PsiMethodCallExpression call) {
    if (call == null) return null;
    PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
    if (nameElement == null) return null;
    String name = nameElement.getText();
    if (!"compareTo".equals(name)) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length != 1) return null;
    PsiExpression arg = args[0];
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return null;
    PsiClassType boxedType = getBoxedType(call);
    if (boxedType == null) return null;
    PsiPrimitiveType primitiveType = PsiPrimitiveType.getUnboxedType(boxedType);
    if (!isTypeConvertible(primitiveType, call)) return null;
    PsiExpression left = extractPrimitive(boxedType, primitiveType, qualifier);
    if (left == null) return null;
    PsiExpression right = extractPrimitive(boxedType, primitiveType, arg);
    if (right == null) return null;
    return new CompareInfo(call, call, left, right, boxedType);
  }

  @Nullable
  static PsiClassType getBoxedType(PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    return JavaPsiFacade.getElementFactory(call.getProject()).createType(aClass);
  }

  @Nullable
  static PsiExpression extractPrimitive(PsiClassType type, PsiPrimitiveType primitiveType, PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return null;
    if (primitiveType.equals(expression.getType())) {
      return expression;
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (!"valueOf".equals(call.getMethodExpression().getReferenceName())) return null;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1) return null;
      PsiMethod method = call.resolveMethod();
      if (method == null || type.resolve() != method.getContainingClass()) return null;
      return checkPrimitive(args[0]);
    }
    if (expression instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression cast = (PsiTypeCastExpression)expression;
      if (!type.equals(cast.getType())) return null;
      return checkPrimitive(cast.getOperand());
    }
    if (expression instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)expression;
      if (!type.equals(newExpression.getType())) return null;
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) return null;
      PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 1) return null;
      if (!(args[0].getType() instanceof PsiPrimitiveType)) return null;
      return checkPrimitive(args[0]);
    }
    return null;
  }

  private static PsiExpression checkPrimitive(PsiExpression expression) {
    return expression != null && expression.getType() instanceof PsiPrimitiveType ? expression : null;
  }

  @Contract("null, _ -> false")
  private static boolean isTypeConvertible(PsiType type, PsiElement context) {
    return type instanceof PsiPrimitiveType && (PsiType.DOUBLE.equals(type) ||
                                                PsiType.FLOAT.equals(type) ||
                                                PsiUtil.isLanguageLevel7OrHigher(context));
  }

  static class CompareInfo {
    final @NotNull PsiElement myTemplate;
    final @NotNull PsiExpression myToReplace;
    final @NotNull PsiExpression myLeft;
    final @NotNull PsiExpression myRight;
    final @NotNull PsiClassType myClass;

    CompareInfo(@NotNull PsiElement template,
                @NotNull PsiExpression toReplace,
                @NotNull PsiExpression left,
                @NotNull PsiExpression right,
                @NotNull PsiClassType aClass) {
      myTemplate = template;
      myToReplace = toReplace;
      myLeft = left;
      myRight = right;
      myClass = aClass;
    }

    private void replace(PsiElement toReplace, CommentTracker ct) {
      String replacement = this.myClass.getCanonicalText() + ".compare(" + ct.text(this.myLeft) + "," + ct.text(this.myRight) + ")";
      if(toReplace == myTemplate) {
        ct.replaceAndRestoreComments(myToReplace, replacement);
      } else {
        ct.replace(myToReplace, replacement);
        ct.replaceAndRestoreComments(toReplace, myTemplate);
      }
    }
  }

  private static class ReplaceWithPrimitiveCompareFix implements LocalQuickFix {
    private String myClassName;

    public ReplaceWithPrimitiveCompareFix(String className) {
      myClassName = className;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with '" + StringUtil.getShortName(myClassName) + ".compare'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with static 'compare' method";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      PsiElement toReplace;
      List<PsiElement> toDelete = new ArrayList<>();
      CompareInfo info;
      if (element instanceof PsiConditionalExpression) {
        toReplace = element;
        info = fromTernary((PsiConditionalExpression)element);
      }
      else {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiIfStatement) {
          toReplace = parent;
          info = fromIf((PsiIfStatement)parent);
          PsiStatement elseIf = getElse((PsiIfStatement)parent);
          toDelete.add(elseIf);
          if(elseIf instanceof PsiIfStatement) {
            toDelete.add(getElse((PsiIfStatement)elseIf));
          }
        } else {
          PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
          info = fromCall(call);
          toReplace = call;
        }
      }
      if (info == null) return;
      CommentTracker ct = new CommentTracker();
      info.replace(toReplace, ct);
      StreamEx.of(toDelete).nonNull().filter(PsiElement::isValid).forEach(e -> new CommentTracker().deleteAndRestoreComments(e));
    }
  }
}
