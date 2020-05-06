// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.execution.filters.ExceptionAnalysisProvider;
import com.intellij.execution.filters.ExceptionInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class DataflowExceptionAnalysisProvider implements ExceptionAnalysisProvider {
  private final Project myProject;

  public DataflowExceptionAnalysisProvider(Project project) {
    myProject = project;
  }

  @Override
  public @Nullable AnAction getAnalysisAction(@NotNull PsiElement anchor, @NotNull String exceptionName, @NotNull String exceptionMessage) {
    Analysis analysis = getAnalysis(anchor, exceptionName, exceptionMessage);
    return createAction(analysis);
  }

  private @Nullable Analysis getAnalysis(@NotNull PsiElement anchor,
                                         @NotNull String exceptionName,
                                         @NotNull String exceptionMessage) {
    if (anchor instanceof PsiKeyword && anchor.textMatches(PsiKeyword.NEW)) {
      PsiNewExpression exceptionConstructor = tryCast(anchor.getParent(), PsiNewExpression.class);
      if (exceptionConstructor == null) return null;
      PsiThrowStatement throwStatement =
        tryCast(ExpressionUtils.getTopLevelExpression(exceptionConstructor).getParent(), PsiThrowStatement.class);
      if (throwStatement == null) return null;
      return fromThrowStatement(throwStatement);
    }
    switch (exceptionName) {
      case CommonClassNames.JAVA_LANG_ASSERTION_ERROR:
        return fromAssertionError(anchor);
      case "java.lang.ArrayIndexOutOfBoundsException":
        return fromArrayIndexOutOfBoundsException(anchor, exceptionMessage);
      case "java.lang.ClassCastException":
        return fromClassCastException(anchor, exceptionMessage);
      case CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION:
        PsiExpression deref = extractAnchor(findDereferencedExpression(anchor));
        return deref == null ? null : new Analysis(DfTypes.NULL, deref);
      default:
        return null;
    }
  }

  private @Nullable static Analysis fromThrowStatement(PsiThrowStatement throwStatement) {
    PsiElement parent = throwStatement.getParent();
    if (parent instanceof PsiCodeBlock) {
      PsiElement statement = throwStatement.getPrevSibling();
      while (statement != null) {
        statement = statement.getPrevSibling();
        if (statement instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)statement;
          boolean thenExits = ifStatement.getThenBranch() != null && !ControlFlowUtils.statementMayCompleteNormally(ifStatement.getThenBranch());
          boolean elseExits =
            ifStatement.getElseBranch() != null && !ControlFlowUtils.statementMayCompleteNormally(ifStatement.getElseBranch());
          if (thenExits != elseExits) {
            Analysis analysis = fromCondition(ifStatement.getCondition());
            return thenExits ? tryNegate(analysis) : analysis;
          }
        }
        if (statement instanceof PsiSwitchLabelStatement) {
          PsiStatement prev = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
          // TODO: support multiple labels
          // TODO: support rule-based switch
          if (prev != null && ControlFlowUtils.statementMayCompleteNormally(prev)) return null;
          PsiExpressionList values = ((PsiSwitchLabelStatement)statement).getCaseValues();
          if (values == null) return null;
          PsiExpression[] expressions = values.getExpressions();
          if (expressions.length != 1) return null;
          DfType type = fromConstant(expressions[0]);
          if (type == null) return null;
          PsiSwitchBlock block = ((PsiSwitchLabelStatement)statement).getEnclosingSwitchBlock();
          if (block == null) return null;
          PsiExpression anchor = extractAnchor(block.getExpression());
          if (anchor == null) return null;
          return new Analysis(type, anchor);
        }
      }
      if (parent.getParent() instanceof PsiBlockStatement) {
        parent = parent.getParent().getParent();
      } else {
        return null;
      }
    }
    if (!(parent instanceof PsiIfStatement) || !PsiTreeUtil.isAncestor(((PsiIfStatement)parent).getThenBranch(), throwStatement, false)) {
      return null;
    }
    PsiExpression cond = PsiUtil.skipParenthesizedExprDown(((PsiIfStatement)parent).getCondition());
    return fromCondition(cond);
  }

  private @Nullable
  static PsiExpression findDereferencedExpression(PsiElement anchor) {
    if (anchor instanceof PsiKeyword && anchor.textMatches(PsiKeyword.THROW) && anchor.getParent() instanceof PsiThrowStatement) {
      return ((PsiThrowStatement)anchor.getParent()).getException();
    }
    if (anchor instanceof PsiKeyword &&
        anchor.textMatches(PsiKeyword.SYNCHRONIZED) &&
        anchor.getParent() instanceof PsiSynchronizedStatement) {
      return ((PsiSynchronizedStatement)anchor.getParent()).getLockExpression();
    }
    if (anchor instanceof PsiIdentifier && anchor.getParent() instanceof PsiReferenceExpression) {
      return ((PsiReferenceExpression)anchor.getParent()).getQualifierExpression();
    }
    if (anchor instanceof PsiJavaToken && ((PsiJavaToken)anchor).getTokenType().equals(JavaTokenType.LBRACKET) &&
        anchor.getParent() instanceof PsiArrayAccessExpression) {
      // Currently we don't report auto-unboxing of index, so it's surely array itself
      return ((PsiArrayAccessExpression)anchor.getParent()).getArrayExpression();
    }
    return null;
  }

  private @Nullable Analysis fromClassCastException(@NotNull PsiElement anchor, @NotNull String exceptionMessage) {
    if (anchor instanceof PsiJavaToken && ((PsiJavaToken)anchor).getTokenType().equals(JavaTokenType.LPARENTH)) {
      String actualClass = ExceptionInfo.getCastActualClassFromMessage(exceptionMessage);
      if (actualClass != null) {
        PsiTypeCastExpression castExpression = tryCast(anchor.getParent(), PsiTypeCastExpression.class);
        if (castExpression != null) {
          PsiExpression ref = extractAnchor(castExpression.getOperand());
          if (ref != null) {
            // TODO: support arrays, primitive arrays, inner classes
            PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(actualClass, GlobalSearchScope.allScope(myProject));
            if (classes.length == 1) {
              return new Analysis(
                DfTypes.typedObject(JavaPsiFacade.getElementFactory(myProject).createType(classes[0]), Nullability.NOT_NULL), ref);
            }
            else {
              PsiType castType = castExpression.getType();
              if (castType != null) {
                return tryNegate(new Analysis(DfTypes.typedObject(castType, Nullability.NULLABLE), ref));
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static Analysis fromArrayIndexOutOfBoundsException(@NotNull PsiElement anchor, @NotNull String exceptionMessage) {
    if (anchor instanceof PsiJavaToken &&
        ((PsiJavaToken)anchor).getTokenType().equals(JavaTokenType.LBRACKET)) {
      Integer index = ExceptionInfo.getArrayIndexFromMessage(exceptionMessage);
      if (index != null) {
        PsiArrayAccessExpression access = tryCast(anchor.getParent(), PsiArrayAccessExpression.class);
        if (access != null) {
          PsiExpression ref = extractAnchor(access.getIndexExpression());
          if (ref != null && PsiType.INT.equals(ref.getType())) {
            return new Analysis(DfTypes.intValue(index), ref);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static Analysis fromAssertionError(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiKeyword &&
        anchor.textMatches(PsiKeyword.ASSERT)) {
      PsiAssertStatement assertStatement = tryCast(anchor.getParent(), PsiAssertStatement.class);
      if (assertStatement != null) {
        return tryNegate(fromCondition(assertStatement.getAssertCondition()));
      }
    }
    return null;
  }

  private @Nullable AnAction createAction(@Nullable Analysis analysis) {
    if (analysis == null) return null;
    String text = getPresentationText(analysis.myDfType, analysis.myAnchor.getType());
    if (text.isEmpty()) return null;
    return new AnAction(null, JavaBundle.message("action.dfa.from.stacktrace.text", analysis.myAnchor.getText(), text), null) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        SliceAnalysisParams params = new SliceAnalysisParams();
        params.dataFlowToThis = true;
        params.scope = new AnalysisScope(myProject);
        params.valueFilter = new JavaDfaSliceValueFilter(analysis.myDfType);
        SliceManager.getInstance(myProject).createToolWindow(analysis.myAnchor, params);
      }
    };
  }

  private static String getPresentationText(DfType type, @Nullable PsiType psiType) {
    if (type instanceof DfIntegralType) {
      // chop 'int' or 'long' prefix
      return ((DfIntegralType)type).getRange().getPresentationText(psiType);
    }
    if (type instanceof DfConstantType) {
      return type.toString();
    }
    if (type instanceof DfReferenceType) {
      DfReferenceType stripped = ((DfReferenceType)type).dropNullability();
      DfaNullability nullability = ((DfReferenceType)type).getNullability();
      TypeConstraint constraint = ((DfReferenceType)type).getConstraint();
      if (constraint.getPresentationText(psiType).isEmpty()) {
        stripped = stripped.dropTypeConstraint();
      }
      String constraintText = stripped.toString();
      if (nullability == DfaNullability.NOT_NULL) {
        if (constraintText.isEmpty()) {
          return "not-null";
        }
        return constraintText + " (not-null)";
      }
      else if (nullability != DfaNullability.NULL) {
        if (constraintText.isEmpty()) {
          return "";
        }
        return "null or " + constraintText;
      }
    }
    return type.toString();
  }

  private static @Nullable Analysis fromCondition(@Nullable PsiExpression cond) {
    cond = PsiUtil.skipParenthesizedExprDown(cond);
    if (cond == null) return null;
    if (cond instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)cond).getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND)) {
        Analysis analysis = null;
        for (PsiExpression operand : ((PsiPolyadicExpression)cond).getOperands()) {
          Analysis next = fromCondition(operand);
          if (next == null) return null;
          if (analysis == null) {
            analysis = next;
          }
          else {
            analysis = analysis.tryMeet(next);
            if (analysis == null) return null;
          }
        }
        return analysis;
      }
      if (tokenType.equals(JavaTokenType.OROR)) {
        Analysis analysis = null;
        for (PsiExpression operand : ((PsiPolyadicExpression)cond).getOperands()) {
          Analysis next = fromCondition(operand);
          if (next == null) return null;
          if (analysis == null) {
            analysis = next;
          }
          else {
            analysis = analysis.tryJoin(next);
            if (analysis == null) return null;
          }
        }
        return analysis;
      }
    }
    if (cond instanceof PsiBinaryExpression) {
      PsiBinaryExpression binop = (PsiBinaryExpression)cond;
      PsiExpression left = PsiUtil.skipParenthesizedExprDown(binop.getLOperand());
      PsiExpression right = PsiUtil.skipParenthesizedExprDown(binop.getROperand());
      Analysis analysis = fromBinOp(left, binop.getOperationTokenType(), right);
      if (analysis != null) return analysis;
      return fromBinOp(right, binop.getOperationTokenType(), left);
    }
    if (cond instanceof PsiInstanceOfExpression) {
      PsiTypeElement checkType = ((PsiInstanceOfExpression)cond).getCheckType();
      if (checkType == null) return null;
      PsiExpression anchor = extractAnchor(((PsiInstanceOfExpression)cond).getOperand());
      if (anchor != null) {
        DfType typedObject = DfTypes.typedObject(checkType.getType(), Nullability.NOT_NULL);
        return new Analysis(typedObject, anchor);
      }
    }
    if (cond instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)cond;
      if (MethodCallUtils.isEqualsCall(call)) {
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
        PsiExpression argument = PsiUtil.skipParenthesizedExprDown(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
        if (qualifier != null && argument != null) {
          DfType type = fromConstant(qualifier);
          PsiExpression anchor = extractAnchor(argument);
          if (type == null) {
            type = fromConstant(argument);
            anchor = extractAnchor(qualifier);
          }
          if (type != null && anchor != null) {
            PsiType anchorType = anchor.getType();
            if (anchorType == null || DfTypes.typedObject(anchorType, Nullability.NOT_NULL).meet(type) == DfTypes.BOTTOM) return null;
            return new Analysis(type, anchor);
          }
        }
      }
    }
    if (BoolUtils.isNegation(cond)) {
      Analysis negatedAnalysis = fromCondition(BoolUtils.getNegated(cond));
      return tryNegate(negatedAnalysis);
    }
    PsiExpression anchor = extractAnchor(cond);
    if (anchor != null) {
      return new Analysis(DfTypes.TRUE, anchor);
    }
    return null;
  }

  private static @Nullable Analysis tryNegate(Analysis analysis) {
    if (analysis == null) return null;
    DfType type = analysis.myDfType.tryNegate();
    if (type == null) return null;
    NullabilityProblemKind.NullabilityProblem<?> problem = NullabilityProblemKind.fromContext(analysis.myAnchor, Collections.emptyMap());
    if (problem != null && CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION.equals(problem.thrownException())) {
      type = type.meet(DfTypes.NOT_NULL_OBJECT);
    }
    return new Analysis(type, analysis.myAnchor);
  }

  private static @Nullable DfType fromConstant(@NotNull PsiExpression constant) {
    if (constant instanceof PsiClassObjectAccessExpression) {
      PsiClassObjectAccessExpression classObject = (PsiClassObjectAccessExpression)constant;
      PsiTypeElement operand = classObject.getOperand();
      return DfTypes.constant(operand.getType(), classObject.getType());
    }
    if (constant instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)constant).resolve();
      if (target instanceof PsiEnumConstant) {
        return DfTypes.constant(target, Objects.requireNonNull(constant.getType()));
      }
    }
    if (ExpressionUtils.isNullLiteral(constant)) {
      return DfTypes.NULL;
    }
    Object value = ExpressionUtils.computeConstantExpression(constant);
    if (value != null) {
      return DfTypes.constant(value, Objects.requireNonNull(constant.getType()));
    }
    return null;
  }

  private static @Nullable Analysis fromBinOp(@Nullable PsiExpression target,
                                              @NotNull IElementType type,
                                              @Nullable PsiExpression constant) {
    if (constant == null) return null;
    DfType constantType = fromConstant(constant);
    if (constantType == null) {
      return null;
    }
    PsiExpression anchor = extractAnchor(target);
    if (anchor != null) {
      PsiType anchorType = anchor.getType();
      if (anchorType == null || TypeUtils.isJavaLangString(anchorType)) return null;
      if (anchorType.equals(PsiType.BYTE) || anchorType.equals(PsiType.CHAR) || anchorType.equals(PsiType.SHORT)) {
        anchorType = PsiType.INT;
      }
      if (constantType == DfTypes.NULL || DfTypes.typedObject(anchorType, Nullability.NOT_NULL).meet(constantType) != DfTypes.BOTTOM) {
        if (type.equals(JavaTokenType.EQEQ)) {
          return new Analysis(constantType, anchor);
        }
        if (type.equals(JavaTokenType.NE)) {
          return tryNegate(new Analysis(constantType, anchor));
        }
      }
    }
    RelationType relationType = RelationType.fromElementType(type);
    if (relationType != null) {
      LongRangeSet set = DfLongType.extractRange(constantType).fromRelation(relationType);
      if (anchor == null) {
        if (target instanceof PsiBinaryExpression) {
          PsiBinaryExpression binOp = (PsiBinaryExpression)target;
          IElementType tokenType = binOp.getOperationTokenType();
          if (tokenType.equals(JavaTokenType.PERC)) {
            anchor = extractAnchor(binOp.getLOperand());
            if (anchor != null) {
              Object divisor = ExpressionUtils.computeConstantExpression(binOp.getROperand());
              if (!(divisor instanceof Integer) && !(divisor instanceof Long)) return null;
              set = LongRangeSet.fromRemainder(((Number)divisor).longValue(), set);
            }
          }
        }
        if (anchor == null) return null;
      }
      PsiType anchorType = anchor.getType();
      if (PsiType.LONG.equals(anchorType)) {
        return new Analysis(DfTypes.longRange(set), anchor);
      }
      if (PsiType.INT.equals(anchorType) ||
          PsiType.SHORT.equals(anchorType) ||
          PsiType.BYTE.equals(anchorType) ||
          PsiType.CHAR.equals(anchorType)) {
        set = set.intersect(Objects.requireNonNull(LongRangeSet.fromType(anchorType)));
        return new Analysis(DfTypes.intRangeClamped(set), anchor);
      }
    }
    return null;
  }

  @Nullable
  private static PsiExpression extractAnchor(@Nullable PsiExpression target) {
    target = PsiUtil.skipParenthesizedExprDown(target);
    if (target instanceof PsiReferenceExpression || target instanceof PsiMethodCallExpression) {
      return target;
    }
    return null;
  }

  private static class Analysis {
    final DfType myDfType;
    final PsiExpression myAnchor;

    private Analysis(DfType type, PsiExpression anchor) {
      myDfType = type;
      myAnchor = anchor;
    }

    private @Nullable Analysis tryMeet(@NotNull Analysis next) {
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(this.myAnchor, next.myAnchor)) return null;
      DfType meet = this.myDfType.meet(next.myDfType);
      if (meet == DfTypes.BOTTOM) return null;
      return new Analysis(meet, this.myAnchor);
    }

    private @Nullable Analysis tryJoin(@NotNull Analysis next) {
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(this.myAnchor, next.myAnchor)) return null;
      DfType meet = this.myDfType.join(next.myDfType);
      if (meet == DfTypes.TOP) return null;
      return new Analysis(meet, this.myAnchor);
    }
  }
}
