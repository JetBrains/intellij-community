/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.ChangeToAppendUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Predicates.nonNull;

public final class StringConcatenationInLoopsInspection extends BaseInspection {

  @org.intellij.lang.annotations.Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "StringConcatenationInLoop";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInLoopsVisitor();
  }

  private static PsiLoopStatement getOutermostCommonLoop(@NotNull PsiExpression expression, @NotNull PsiVariable variable) {
    PsiElement stopAt = null;
    PsiCodeBlock block = StringConcatenationInLoopsVisitor.getSurroundingBlock(expression);
    if (block != null) {
      PsiElement ref;
      if (expression instanceof PsiAssignmentExpression) {
        ref = expression;
      }
      else {
        PsiReference reference = ReferencesSearch.search(variable, new LocalSearchScope(expression)).findFirst();
        ref = reference != null ? reference.getElement() : null;
      }
      if (ref != null) {
        PsiElement[] elements = StreamEx.of(DefUseUtil.getDefs(block, variable, expression)).prepend(expression).toArray(PsiElement[]::new);
        stopAt = PsiTreeUtil.findCommonParent(elements);
      }
    }
    PsiElement parent = expression.getParent();
    PsiLoopStatement commonLoop = null;
    while (parent != null && parent != stopAt && !(parent instanceof PsiMethod)
           && !(parent instanceof PsiClass) && !(parent instanceof PsiLambdaExpression)) {
      if (parent instanceof PsiLoopStatement) {
        commonLoop = (PsiLoopStatement)parent;
      }
      parent = parent.getParent();
    }
    return commonLoop;
  }

  private static class StringConcatenationInLoopsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length <= 1) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUS)) return;

      if (!checkExpression(expression)) return;

      if (ExpressionUtils.isEvaluatedAtCompileTime(expression)) return;

      if (!isAppendedRepeatedly(expression)) return;
      final PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
      assert sign != null;
      registerError(sign, expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null) return;

      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();

      if (!tokenType.equals(JavaTokenType.PLUSEQ)) return;

      if (!checkExpression(expression)) return;

      PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLExpression());

      if (!(lhs instanceof PsiReferenceExpression)) return;
      registerError(sign, expression);
    }

    private static boolean checkExpression(PsiExpression expression) {
      if (!TypeUtils.isJavaLangString(expression.getType()) || ControlFlowUtils.isInExitStatement(expression) ||
          !ControlFlowUtils.isInLoop(expression)) return false;

      PsiElement parent = expression;
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
        parent = parent.getParent();
      }
      if (parent != expression && parent instanceof PsiAssignmentExpression &&
          ((PsiAssignmentExpression)parent).getOperationTokenType().equals(JavaTokenType.PLUSEQ)) {
        // Will be reported for parent +=, no need to report twice
        return false;
      }

      if (parent instanceof PsiAssignmentExpression) {
        expression = (PsiExpression)parent;

        PsiReferenceExpression lhs = getAppendedExpression(expression);
        if (lhs == null) return false;
        PsiVariable variable = ObjectUtils.tryCast(lhs.resolve(), PsiVariable.class);

        if (variable != null) {
          PsiLoopStatement commonLoop = getOutermostCommonLoop(expression, variable);
          return commonLoop != null &&
                 !ControlFlowUtils.isExecutedOnceInLoop(PsiTreeUtil.getParentOfType(expression, PsiStatement.class), commonLoop) &&
                 !isUsedCompletely(variable, commonLoop) && checkQualifier(lhs, commonLoop);
        }
      }
      return false;
    }

    private static boolean checkQualifier(PsiReferenceExpression lhs, PsiLoopStatement commonLoop) {
      while (true) {
        PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(lhs.getQualifierExpression());
        if (qualifierExpression == null || qualifierExpression instanceof PsiQualifiedExpression) return true;
        PsiReferenceExpression ref = ObjectUtils.tryCast(qualifierExpression, PsiReferenceExpression.class);
        if (ref == null) return false;
        PsiVariable varRef = ObjectUtils.tryCast(ref.resolve(), PsiVariable.class);
        if (varRef == null) return false;
        if (PsiTreeUtil.isAncestor(commonLoop, varRef, true) || VariableAccessUtils.variableIsAssigned(varRef, commonLoop)) return false;
        lhs = ref;
      }
    }

    private static boolean isUsedCompletely(PsiVariable variable, PsiLoopStatement loop) {
      return ContainerUtil.exists(VariableAccessUtils.getVariableReferences(variable, loop), expression -> {
        PsiElement parent = ExpressionUtils.getPassThroughParent(expression);
        if (parent instanceof PsiExpressionList ||
            parent instanceof PsiAssignmentExpression &&
            PsiTreeUtil.isAncestor(((PsiAssignmentExpression)parent).getRExpression(), expression, false)) {
          PsiStatement statement = PsiTreeUtil.getParentOfType(parent, PsiStatement.class);
          return !ControlFlowUtils.isExecutedOnceInLoop(statement, loop) && !ControlFlowUtils.isVariableReassigned(statement, variable);
        }
        return false;
      });
    }

    @Nullable
    private static PsiCodeBlock getSurroundingBlock(PsiElement expression) {
      PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiClassInitializer.class, PsiLambdaExpression.class);
      if(parent instanceof PsiMethod) {
        return ((PsiMethod)parent).getBody();
      }
      if(parent instanceof PsiClassInitializer) {
        return ((PsiClassInitializer)parent).getBody();
      }
      if(parent instanceof PsiLambdaExpression) {
        PsiElement body = ((PsiLambdaExpression)parent).getBody();
        if(body instanceof PsiCodeBlock) {
          return (PsiCodeBlock)body;
        }
      }
      return null;
    }

    private static boolean isAppendedRepeatedly(PsiExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
        return false;
      }
      PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
        return false;
      }
      if (assignmentExpression.getOperationTokenType() == JavaTokenType.PLUSEQ) {
        return true;
      }
      final PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      final PsiExpression rhs = assignmentExpression.getRExpression();
      return isAppended(referenceExpression, rhs);
    }

    private static boolean isAppended(PsiReferenceExpression otherRef, PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if(expression instanceof PsiPolyadicExpression polyadicExpression) {
        if (polyadicExpression.getOperationTokenType().equals(JavaTokenType.PLUS)) {
          for (PsiExpression operand : polyadicExpression.getOperands()) {
            if (isSameReference(operand, otherRef) || isAppended(otherRef, operand)) return true;
          }
        }
      }
      return false;
    }

    private static boolean isSameReference(PsiExpression operand, PsiReferenceExpression ref) {
      PsiReferenceExpression other = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(operand), PsiReferenceExpression.class);
      if (other == null) {
        return false;
      }
      String name = other.getReferenceName();
      if (name == null || !name.equals(ref.getReferenceName())) return false;
      PsiExpression qualifier = ref.getQualifierExpression();
      PsiExpression otherQualifier = other.getQualifierExpression();
      if (qualifier == null && otherQualifier == null) return true;
      if (qualifier == null && ref.resolve() instanceof PsiField) {
        qualifier = ExpressionUtils.getEffectiveQualifier(ref);
      }
      if (otherQualifier == null && other.resolve() instanceof PsiField) {
        otherQualifier = ExpressionUtils.getEffectiveQualifier(other);
      }
      if (qualifier == null || otherQualifier == null) return false;
      if (qualifier instanceof PsiReferenceExpression) {
        return isSameReference(otherQualifier, (PsiReferenceExpression)qualifier);
      }
      return PsiEquivalenceUtil.areElementsEquivalent(qualifier, otherQualifier);
    }
  }

  @Contract("null -> null")
  @Nullable
  private static PsiVariable getAppendedVariable(PsiExpression expression) {
    PsiReferenceExpression lhs = getAppendedExpression(expression);
    if (lhs == null) return null;
    return ObjectUtils.tryCast(lhs.resolve(), PsiVariable.class);
  }

  @Contract("null -> null")
  @Nullable
  private static PsiReferenceExpression getAppendedExpression(PsiExpression expression) {
    PsiElement parent = expression;
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
      parent = parent.getParent();
    }
    if (!(parent instanceof PsiAssignmentExpression)) return null;
    return ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(((PsiAssignmentExpression)parent).getLExpression()),
                               PsiReferenceExpression.class);
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    PsiExpression expression = ObjectUtils.tryCast(ArrayUtil.getFirstElement(infos), PsiExpression.class);
    PsiVariable var = getAppendedVariable(expression);
    if (var == null) return InspectionGadgetsFix.EMPTY_ARRAY;
    boolean needNullSafe = canBeNull(var);
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (var instanceof PsiLocalVariable) {
      fixes.add(new ReplaceWithStringBuilderFix(var, false));
      if (needNullSafe) {
        fixes.add(new ReplaceWithStringBuilderFix(var, true));
      }
      PsiLoopStatement loop = getOutermostCommonLoop(expression, var);
      // Do not add IntroduceStringBuilderFix if there's only 0 or 1 reference to the variable outside loop:
      // in this case the result is usually similar to ReplaceWithStringBuilderFix or worse
      if (ReferencesSearch.search(var).findAll().stream()
            .map(PsiReference::getElement).filter(e -> !PsiTreeUtil.isAncestor(loop, e, true))
            .limit(2).count() > 1) {
        fixes.add(new IntroduceStringBuilderFix(var, false));
        if (needNullSafe) {
          fixes.add(new IntroduceStringBuilderFix(var, true));
        }
      }
    }
    else if (var instanceof PsiParameter) {
      fixes.add(new IntroduceStringBuilderFix(var, false));
      if (needNullSafe) {
        fixes.add(new IntroduceStringBuilderFix(var, true));
      }
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static boolean canBeNull(PsiVariable var) {
    PsiExpression initializer = var.getInitializer();
    if (initializer != null && NullabilityUtil.getExpressionNullability(initializer, true) != Nullability.NOT_NULL) {
      return true;
    }
    Predicate<PsiReference> isPossiblyNullableWrite = ref -> {
      if (!(ref instanceof PsiExpression expression)) return false;
      if (!PsiUtil.isOnAssignmentLeftHand(expression)) return false;
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class);
      if (assignment == null || assignment.getOperationTokenType() != JavaTokenType.EQ) return false;
      PsiExpression rExpression = assignment.getRExpression();
      return rExpression != null && NullabilityUtil.getExpressionNullability(rExpression, true) != Nullability.NOT_NULL;
    };
    return ReferencesSearch.search(var).anyMatch(isPossiblyNullableWrite);
  }

  private static class StringBuilderReplacer {
    static final Pattern PRINT_OR_PRINTLN = Pattern.compile("print|println");

    Set<PsiExpression> myNullables = Collections.emptySet();
    final String myTargetType;
    final boolean myNullSafe;

    private StringBuilderReplacer(String type, boolean safe) {
      myTargetType = type;
      myNullSafe = safe;
    }

    @NonNls @NotNull String generateNewStringBuilder(PsiExpression initializer, CommentTracker ct) {
      if (ExpressionUtils.isNullLiteral(initializer)) {
        return ct.text(initializer);
      }
      String text = initializer == null || ExpressionUtils.isLiteral(initializer, "") ? "" : ct.text(initializer);
      String stringBuilderText = "new " + myTargetType + "(" + text + ")";
      if (myNullables.contains(initializer)) {
        if (ExpressionUtils.isSafelyRecomputableExpression(initializer)) {
          return initializer.getText() + "==null?null:" + stringBuilderText;
        }
        if (PsiUtil.isLanguageLevel8OrHigher(initializer)) {
          return CommonClassNames.JAVA_UTIL_OPTIONAL +
                 ".ofNullable(" +
                 initializer.getText() +
                 ").map(" +
                 myTargetType +
                 "::new).orElse(null)";
        }
      }
      return stringBuilderText;
    }

    void replaceAll(PsiVariable variable,
                    PsiVariable builderVariable,
                    PsiElement scope,
                    CommentTracker ct) {
      replaceAll(variable, builderVariable, scope, ct, ref -> false);
    }

    void replaceAll(PsiVariable variable,
                    PsiVariable builderVariable,
                    PsiElement scope,
                    CommentTracker ct,
                    Predicate<? super PsiReferenceExpression> skip) {
      if (scope == null) {
        scope = PsiUtil.getVariableCodeBlock(variable, null);
      }
      List<PsiReferenceExpression> refs = VariableAccessUtils.getVariableReferences(variable, scope);
      if (myNullSafe) {
        fillNullables(variable, refs);
      }
      for(PsiReferenceExpression target : refs) {
        if(target.isValid() && !skip.test(target)) {
          replace(variable, builderVariable, target, ct);
        }
      }
    }

    private void fillNullables(PsiVariable variable, Collection<PsiReferenceExpression> refs) {
      if (myNullables instanceof HashSet) return; // already filled
      myNullables = new HashSet<>();
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null && NullabilityUtil.getExpressionNullability(initializer, true) != Nullability.NOT_NULL) {
        myNullables.add(initializer);
      }
      for (PsiReferenceExpression refExpr : refs) {
        if (NullabilityUtil.getExpressionNullability(refExpr, true) != Nullability.NOT_NULL) {
          myNullables.add(refExpr);
        }
        if(PsiUtil.isOnAssignmentLeftHand(refExpr)) {
          PsiExpression rExpr =
            ExpressionUtils.getAssignmentTo(PsiTreeUtil.getParentOfType(refExpr, PsiAssignmentExpression.class), variable);
          if (rExpr != null && NullabilityUtil.getExpressionNullability(rExpr, true) != Nullability.NOT_NULL) {
            myNullables.add(rExpr);
          }
        }
      }
    }

    private void replace(PsiVariable variable,
                         PsiVariable builderVariable,
                         PsiReferenceExpression ref,
                         CommentTracker ct) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
      if(parent instanceof PsiAssignmentExpression assignment) {
        if(PsiUtil.skipParenthesizedExprDown(assignment.getLExpression()) == ref) {
          replaceInAssignment(variable, builderVariable, assignment, ct);
          return;
        } else {
          // ref is r-value
          if(assignment.getOperationTokenType().equals(JavaTokenType.PLUSEQ)) return;
        }
      }
      if (variable != builderVariable) {
        ExpressionUtils.bindReferenceTo(ref, Objects.requireNonNull(builderVariable.getName()));
      }
      PsiMethodCallExpression methodCallExpression = ExpressionUtils.getCallForQualifier(ref);
      if(methodCallExpression != null) {
        replaceInCallQualifier(builderVariable, methodCallExpression, ct);
        return;
      }
      if(parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression) {
        PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
        if(expressions.length == 1 && expressions[0] == ref) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)parent.getParent();
          if(canAcceptBuilderInsteadOfString(call)) {
            return;
          }
        }
      }
      if(parent instanceof PsiBinaryExpression binOp) {
        if(ExpressionUtils.getValueComparedWithNull(binOp) != null) {
          return;
        }
      }
      if(parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType().equals(JavaTokenType.PLUS)) {
        PsiExpression[] operands = ((PsiPolyadicExpression)parent).getOperands();
        for (PsiExpression operand : operands) {
          if (operand == ref) break;
          if (TypeUtils.isJavaLangString(operand.getType())) return;
        }
        if (operands.length > 1 && operands[0] == ref && TypeUtils.isJavaLangString(operands[1].getType())) return;
      }
      @NonNls String text = builderVariable.getName() + ".toString()";
      if (myNullables.contains(ref) && !isNotNullContext(ref)) {
        text = builderVariable.getName() + "==null?null:" + text;
        if (parent instanceof PsiExpression) {
          text = "(" + text + ")";
        }
      }
      ct.replace(ref, text);
    }

    private static boolean isNotNullContext(PsiExpression ref) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
      if (!(parent instanceof PsiExpressionList) || !(parent.getParent() instanceof PsiMethodCallExpression)) return false;
      int argIndex = IntStreamEx.ofIndices(((PsiExpressionList)parent).getExpressions(), arg -> PsiTreeUtil.isAncestor(arg, ref, false))
        .findFirst().orElse(-1);
      if (argIndex < 0) return false;
      PsiMethod method = ((PsiMethodCallExpression)parent.getParent()).resolveMethod();
      if (method == null) return false;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      return parameters.length > argIndex && NullableNotNullManager.isNotNull(parameters[argIndex]);
    }

    private static boolean canAcceptBuilderInsteadOfString(PsiMethodCallExpression call) {
      return MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_LANG_STRING_BUILDER, null, "append",
                                            (PsiType[])null) ||
             MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_LANG_STRING_BUFFER, null, "append",
                                            (PsiType[])null) ||
             MethodCallUtils.isCallToMethod(call, "java.io.PrintStream", null, PRINT_OR_PRINTLN,
                                            (PsiType[])null) ||
             MethodCallUtils.isCallToMethod(call, "java.io.PrintWriter", null, PRINT_OR_PRINTLN,
                                            (PsiType[])null);
    }

    private static void replaceInCallQualifier(PsiVariable variable, PsiMethodCallExpression call, CommentTracker ct) {
      PsiMethod method = call.resolveMethod();
      if(method != null) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        @NonNls String name = method.getName();
        switch (name) {
          case "length", "chars", "codePoints", "charAt", "codePointAt", "codePointBefore", "codePointAfter", "codePointCount",
            "offsetByCodePoints", "substring", "subSequence" -> {
            return;
          }
          case "getChars" -> {
            if (args.length == 4) return;
          }
          case "indexOf", "lastIndexOf" -> {
            if (args.length >= 1 && args.length <= 2 && TypeUtils.isJavaLangString(args[0].getType())) return;
          }
          case "isEmpty" -> {
            String sign = "==";
            PsiExpression negation = BoolUtils.findNegation(call);
            PsiElement toReplace = call;
            if (negation != null) {
              sign = ">";
              toReplace = negation;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
            PsiExpression emptyCheck = factory.createExpressionFromText(variable.getName() + ".length()" + sign + "0", call);
            PsiElement callParent = toReplace.getParent();
            if (callParent instanceof PsiExpression &&
                ParenthesesUtils.areParenthesesNeeded(emptyCheck, (PsiExpression)callParent, true)) {
              emptyCheck = factory.createExpressionFromText("(" + emptyCheck.getText() + ")", call);
            }
            ct.replace(toReplace, emptyCheck);
            return;
          }
          default -> {
          }
        }
      }
      PsiExpression qualifier = Objects.requireNonNull(call.getMethodExpression().getQualifierExpression());
      ct.replace(qualifier, variable.getName() + ".toString()");
    }

    private void replaceInAssignment(PsiVariable variable,
                                     PsiVariable builderVariable,
                                     PsiAssignmentExpression assignment,
                                     CommentTracker ct) {
      PsiExpression rValue = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
      String builderName = Objects.requireNonNull(builderVariable.getName());
      if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        if (rValue instanceof PsiPolyadicExpression concat &&
            ((PsiPolyadicExpression)rValue).getOperationTokenType().equals(JavaTokenType.PLUS)) {
          PsiExpression[] operands = concat.getOperands();
          if (operands.length > 1) {
            // s = s + ...;
            if (ExpressionUtils.isReferenceTo(operands[0], variable)) {
              StreamEx.iterate(operands[1], nonNull(), PsiElement::getNextSibling).forEach(ct::markUnchanged);
              replaceAll(variable, builderVariable, rValue, ct, operands[0]::equals);
              StringBuilder replacement =
                ChangeToAppendUtil.buildAppendExpression(rValue, false, new StringBuilder(builderName));
              if (replacement != null) {
                PsiMethodCallExpression result = (PsiMethodCallExpression)ct.replace(assignment, replacement.toString());
                PsiMethodCallExpression append = getDeepestQualifierCall(result);
                PsiExpression qualifier = append.getMethodExpression().getQualifierExpression();
                if (qualifier != null) {
                  append.replace(qualifier);
                }
                makeNullSafe(operands[0], result);
              }
              return;
            }
            // s = ... + s;
            PsiExpression lastOp = operands[operands.length - 1];
            if (ExpressionUtils.isReferenceTo(lastOp, variable)) {
              ct.delete(concat.getTokenBeforeOperand(lastOp), lastOp);
              replaceAll(variable, builderVariable, rValue, ct);
              makeNullSafe(lastOp, (PsiMethodCallExpression)ct.replace(assignment, builderName + ".insert(0," + ct.text(rValue) + ")"));
              return;
            }
          }
        }
      }
      if(rValue != null) {
        replaceAll(variable, builderVariable, rValue, ct);
        rValue = assignment.getRExpression();
      }
      if(assignment.getOperationTokenType().equals(JavaTokenType.PLUSEQ)) {
        // s += ...;
        String replacement = "";
        if (rValue != null) {
          StringBuilder sb =
            ChangeToAppendUtil.buildAppendExpression(ct.markUnchanged(rValue), false, new StringBuilder(builderName));
          if (sb != null) {
            replacement = sb.toString();
          }
        }
        makeNullSafe(assignment.getLExpression(), (PsiMethodCallExpression)ct.replace(assignment, replacement));
      } else if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
        JavaCodeStyleManager.getInstance(variable.getProject())
          .shortenClassReferences(ct.replace(assignment, builderName + "=" + generateNewStringBuilder(rValue, ct)));
      }
    }

    @NotNull
    private static PsiMethodCallExpression getDeepestQualifierCall(PsiMethodCallExpression result) {
      PsiMethodCallExpression append = result;
      while (true) {
        PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(append);
        if (qualifierCall == null) break;
        append = qualifierCall;
      }
      return append;
    }

    private void makeNullSafe(PsiExpression expression, PsiMethodCallExpression result) {
      if (!myNullables.contains(expression)) return;
      PsiExpression qualifier = getDeepestQualifierCall(result).getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      String builder = qualifier.getText();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(result.getProject());
      qualifier
        .replace(factory.createExpressionFromText("(" + builder + "==null?new " + myTargetType + "(\"null\"):" + builder + ")", qualifier));
      result.replace(factory.createExpressionFromText(builder + "=" + result.getText(), result));
    }
  }

  static class IntroduceStringBuilderFix extends PsiUpdateModCommandQuickFix {
    final String myName;
    final String myTargetType;
    final boolean myNullSafe;

    IntroduceStringBuilderFix(@NotNull PsiVariable variable, boolean nullSafe) {
      myName = variable.getName();
      myTargetType = PsiUtil.isLanguageLevel5OrHigher(variable) ?
                     CommonClassNames.JAVA_LANG_STRING_BUILDER : CommonClassNames.JAVA_LANG_STRING_BUFFER;
      myNullSafe = nullSafe;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiExpression expression = PsiTreeUtil.getParentOfType(startElement, PsiExpression.class);
      if (expression == null) return;
      PsiVariable variable = getAppendedVariable(expression);
      if (variable == null) return;
      PsiLoopStatement loop = getOutermostCommonLoop(expression, variable);
      if (loop == null) return;
      ControlFlowUtils.InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(variable, loop);
      JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
      String newName = javaCodeStyleManager.suggestUniqueVariableName(variable.getName() + "Builder", loop, true);
      String newStringBuilder =
        myTargetType + " " + newName + "=new " + myTargetType + "(" + variable.getName() + ");";
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      Object marker = new Object();
      PsiTreeUtil.mark(loop, marker);
      PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)BlockUtils.addBefore(loop, factory.createStatementFromText(newStringBuilder, loop));
      if (!loop.isValid()) {
        loop = (PsiLoopStatement)PsiTreeUtil.releaseMark(declaration.getParent(), marker);
        if (loop == null) return;
      }
      PsiVariable builderVariable = (PsiVariable)declaration.getDeclaredElements()[0];
      PsiExpression builderInitializer = Objects.requireNonNull(builderVariable.getInitializer());
      CommentTracker ct = new CommentTracker();
      StringBuilderReplacer replacer = new StringBuilderReplacer(myTargetType, myNullSafe);
      replacer.replaceAll(variable, builderVariable, loop, ct);
      String convertToString = myNullSafe ? CommonClassNames.JAVA_LANG_STRING + ".valueOf(" + newName + ")" : newName + ".toString()";
      @NonNls String toString = variable.getName() + " = " + convertToString + ";";

      PsiExpression initializer = variable.getInitializer();
      switch (status) {
        case DECLARED_JUST_BEFORE -> {
          // Put original variable declaration after the loop and use its original initializer in StringBuilder constructor
          PsiTypeElement typeElement = variable.getTypeElement();
          if (typeElement != null && initializer != null) {
            javaCodeStyleManager.shortenClassReferences(ct.replace(builderInitializer, replacer.generateNewStringBuilder(initializer, ct)));
            ct.replace(initializer, convertToString);
            toString = variable.getText();
            ct.delete(variable);
          }
        }
        case AT_WANTED_PLACE_ONLY -> {
          // Move original initializer to the StringBuilder constructor
          if (initializer != null) {
            javaCodeStyleManager.shortenClassReferences(ct.replace(builderInitializer, replacer.generateNewStringBuilder(initializer, ct)));
            initializer.delete();
          }
        }
        case AT_WANTED_PLACE -> {
          // Copy original initializer to the StringBuilder constructor if possible
          if (ExpressionUtils.isSafelyRecomputableExpression(initializer)) {
            javaCodeStyleManager.shortenClassReferences(ct.replace(builderInitializer, replacer.generateNewStringBuilder(initializer, ct)));
          }
        }
        case UNKNOWN -> {
          PsiElement prevStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(declaration);
          PsiExpression prevAssignment = ExpressionUtils.getAssignmentTo(prevStatement, variable);
          if (prevAssignment != null) {
            javaCodeStyleManager
              .shortenClassReferences(ct.replace(builderInitializer, replacer.generateNewStringBuilder(prevAssignment, ct)));
            ct.delete(prevStatement);
          }
        }
      }
      BlockUtils.addAfter(loop, factory.createStatementFromText(toString, loop));
      ct.insertCommentsBefore(loop);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      final String introduce = StringUtil.getShortName(myTargetType);
      if (myNullSafe) {
        return InspectionGadgetsBundle.message("string.concatenation.introduce.fix.name.null.safe", myName, introduce);
      } else {
        return InspectionGadgetsBundle.message("string.concatenation.introduce.fix.name", myName, introduce);
      }
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.introduce.fix");
    }
  }

  static class ReplaceWithStringBuilderFix extends PsiUpdateModCommandQuickFix {
    final String myName;
    final String myTargetType;
    final boolean myNullSafe;

    ReplaceWithStringBuilderFix(@NotNull PsiVariable variable, boolean nullSafe) {
      myName = variable.getName();
      myTargetType = PsiUtil.isLanguageLevel5OrHigher(variable) ?
                     CommonClassNames.JAVA_LANG_STRING_BUILDER : CommonClassNames.JAVA_LANG_STRING_BUFFER;
      myNullSafe = nullSafe;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiExpression expression = PsiTreeUtil.getParentOfType(startElement, PsiExpression.class);
      if (expression == null) return;
      if (!(getAppendedVariable(expression) instanceof PsiLocalVariable variable)) return;
      variable.normalizeDeclaration();
      PsiTypeElement typeElement = variable.getTypeElement();
      CommentTracker ct = new CommentTracker();
      StringBuilderReplacer replacer = new StringBuilderReplacer(myTargetType, myNullSafe);
      replacer.replaceAll(variable, variable, null, ct);
      ct.replace(typeElement, myTargetType);
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        JavaCodeStyleManager.getInstance(project)
          .shortenClassReferences(ct.replace(initializer, replacer.generateNewStringBuilder(initializer, ct)));
      }
      PsiStatement commentPlace = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
      ct.insertCommentsBefore(commentPlace == null ? variable : commentPlace);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      final String introduce = StringUtil.getShortName(myTargetType);
      if (myNullSafe) {
        return InspectionGadgetsBundle.message("string.concatenation.replace.fix.name.null.safe", myName, introduce);
      } else {
        return InspectionGadgetsBundle.message("string.concatenation.replace.fix.name", myName, introduce);
      }
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.concatenation.replace.fix");
    }
  }
}
