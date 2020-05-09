// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.execution.filters.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class DataflowExceptionAnalysisProvider implements ExceptionAnalysisProvider {
  private final Project myProject;

  public DataflowExceptionAnalysisProvider(Project project) {
    myProject = project;
  }

  @Override
  public @Nullable AnAction getAnalysisAction(@NotNull PsiElement anchor,
                                              @NotNull ExceptionInfo info) {
    Analysis analysis = getAnalysis(anchor, info);
    return createAction(analysis);
  }

  @Override
  public @Nullable AnAction getIntermediateRowAnalysisAction(@NotNull PsiElement anchor) {
    Analysis analysis = getIntermediateRowAnalysis(anchor);
    return createAction(analysis);
  }

  private static @Nullable Analysis getIntermediateRowAnalysis(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiExpression) {
      return new Analysis(DfTypes.NULL, (PsiExpression)anchor);
    }
    if (!(anchor instanceof PsiIdentifier)) return null;
    PsiReferenceExpression ref = tryCast(anchor.getParent(), PsiReferenceExpression.class);
    if (ref == null) return null;
    PsiMethodCallExpression call = tryCast(ref.getParent(), PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    if (!JavaMethodContractUtil.isPure(method)) return null;
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
    if (contracts.isEmpty() || contracts.size() > 2) return null;
    MethodContract failContract = contracts.get(0);
    if (failContract.getReturnValue() != ContractReturnValue.fail()) return null;
    if (contracts.size() == 2 && !(contracts.get(1).getReturnValue() instanceof ContractReturnValue.ParameterReturnValue)) return null;
    ContractValue condition = ContainerUtil.getOnlyItem(failContract.getConditions());
    if (condition == null) return null;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    PsiExpression arg = getArgFromContract(args, condition, ContractValue.nullValue(), true);
    if (arg != null) {
      return Analysis.create(DfTypes.NULL, arg);
    }
    arg = getArgFromContract(args, condition, ContractValue.nullValue(), false);
    if (arg != null) {
      return Analysis.create(DfTypes.NOT_NULL_OBJECT, arg);
    }
    arg = getArgFromContract(args, condition, ContractValue.booleanValue(true), true);
    if (arg != null) {
      return fromCondition(arg);
    }
    arg = getArgFromContract(args, condition, ContractValue.booleanValue(false), true);
    if (arg != null) {
      return tryNegate(fromCondition(arg));
    }
    return null;
  }
  
  private static @Nullable PsiExpression getArgFromContract(
    PsiExpression[] args, ContractValue condition, ContractValue expectedValue, boolean equal) {
    int pos = condition.getArgumentComparedTo(expectedValue, equal).orElse(-1);
    if (pos < 0 || pos >= args.length) return null;
    return args[pos];
  }

  private @Nullable Analysis getAnalysis(@NotNull PsiElement anchor,
                                         @NotNull ExceptionInfo info) {
    if (anchor instanceof PsiKeyword && anchor.textMatches(PsiKeyword.NEW)) {
      PsiNewExpression exceptionConstructor = tryCast(anchor.getParent(), PsiNewExpression.class);
      if (exceptionConstructor != null && !exceptionConstructor.isArrayCreation()) {
        PsiThrowStatement throwStatement =
          tryCast(ExpressionUtils.getTopLevelExpression(exceptionConstructor).getParent(), PsiThrowStatement.class);
        if (throwStatement == null) return null;
        return fromThrowStatement(throwStatement);
      }
    }
    if (info instanceof AssertionErrorInfo) {
      return fromAssertionError(anchor);
    }
    else if (info instanceof ArrayIndexOutOfBoundsExceptionInfo) {
      Integer index = ((ArrayIndexOutOfBoundsExceptionInfo)info).getIndex();
      if (index != null && anchor instanceof PsiExpression) {
        return Analysis.create(DfTypes.intValue(index), (PsiExpression)anchor);
      }
    }
    else if (info instanceof ClassCastExceptionInfo) {
      return fromClassCastException(anchor, ((ClassCastExceptionInfo)info).getActualClass());
    }
    else if (info instanceof NullPointerExceptionInfo || info instanceof JetBrainsNotNullInstrumentationExceptionInfo) {
      return Analysis.create(DfTypes.NULL, tryCast(anchor, PsiExpression.class));
    }
    else if (info instanceof NegativeArraySizeExceptionInfo) {
      Integer size = ((NegativeArraySizeExceptionInfo)info).getSuppliedSize();
      if (size != null && size < 0 && anchor instanceof PsiExpression) {
        return Analysis.create(DfTypes.intValue(size), (PsiExpression)anchor);
      }
    }
    else if (info instanceof ArithmeticExceptionInfo) {
      return fromArithmeticException(anchor);
    }
    return null;
  }

  private static Analysis fromArithmeticException(PsiElement anchor) {
    if (anchor instanceof PsiExpression) {
      PsiExpression divisor = (PsiExpression)anchor;
      PsiType type = divisor.getType();
      if (PsiType.LONG.equals(type)) {
        return Analysis.create(DfTypes.longValue(0), divisor);
      }
      else if (TypeConversionUtil.isIntegralNumberType(type)) {
        return Analysis.create(DfTypes.intValue(0), divisor);
      }
    }
    return null;
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
          return fromSwitchLabel((PsiSwitchLabelStatement)statement);
        }
      }
      if (parent.getParent() instanceof PsiBlockStatement) {
        parent = parent.getParent().getParent();
      } else {
        return null;
      }
    }
    if (parent instanceof PsiIfStatement && PsiTreeUtil.isAncestor(((PsiIfStatement)parent).getThenBranch(), throwStatement, false)) {
      PsiExpression cond = PsiUtil.skipParenthesizedExprDown(((PsiIfStatement)parent).getCondition());
      return fromCondition(cond);
    }
    if (parent instanceof PsiSwitchLabeledRuleStatement) {
      return fromSwitchLabel((PsiSwitchLabeledRuleStatement)parent);
    }
    return null;
  }

  private static @Nullable Analysis fromSwitchLabel(PsiSwitchLabelStatementBase label) {
    PsiSwitchBlock block = label.getEnclosingSwitchBlock();
    if (block == null) return null;
    boolean hasDefault = false;
    List<PsiExpression> labels = new ArrayList<>();
    if (label.isDefaultCase()) {
      hasDefault = true;
    } else {
      PsiExpressionList values = label.getCaseValues();
      if (values == null) return null;
      labels.addAll(Arrays.asList(values.getExpressions()));
    }
    if (label instanceof PsiSwitchLabelStatement) {
      PsiStatement prev = label;
      while (true) {
        prev = PsiTreeUtil.getPrevSiblingOfType(prev, PsiStatement.class);
        if (!(prev instanceof PsiSwitchLabelStatement)) {
          if (prev != null && ControlFlowUtils.statementMayCompleteNormally(prev)) return null;
          break;
        }
        if (((PsiSwitchLabelStatement)prev).isDefaultCase()) {
          hasDefault = true;
        } else {
          PsiExpressionList prevValues = ((PsiSwitchLabelStatement)prev).getCaseValues();
          if (prevValues == null) return null;
          labels.addAll(Arrays.asList(prevValues.getExpressions()));
        }
      }
    }
    if (hasDefault) {
      List<PsiExpression> allLabels = new ArrayList<>();
      for (PsiStatement statement : Objects.requireNonNull(block.getBody()).getStatements()) {
        if (statement instanceof PsiSwitchLabelStatementBase) {
          PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase)statement;
          if (labelStatement.isDefaultCase()) continue;
          PsiExpressionList caseValues = labelStatement.getCaseValues();
          if (caseValues == null) return null;
          allLabels.addAll(Arrays.asList(caseValues.getExpressions()));
        }
      }
      allLabels.removeAll(labels);
      labels = allLabels;
    }
    PsiExpression selector = block.getExpression();
    Analysis result = null;
    for (PsiExpression labelValue : labels) {
      DfType type = fromConstant(labelValue);
      if (type == null) return null;
      Analysis next = Analysis.create(type, selector);
      if (hasDefault) {
        next = tryNegate(next);
      }
      if (next == null) return null;
      if (result == null) {
        result = next;
      } else {
        result = hasDefault ? result.tryMeet(next) : result.tryJoin(next);
        if (result == null) return null;
      }
    }
    return result; 
  }

  private @Nullable Analysis fromClassCastException(@NotNull PsiElement anchor, @Nullable String actualClass) {
    if (!(anchor instanceof PsiTypeElement)) return null;
    PsiTypeCastExpression castExpression = tryCast(anchor.getParent(), PsiTypeCastExpression.class);
    if (castExpression == null) return null;
    PsiExpression ref = extractAnchor(castExpression.getOperand());
    if (ref == null) return null;
    if (actualClass != null) {
      // TODO: support arrays, primitive arrays, inner classes
      PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(actualClass, GlobalSearchScope.allScope(myProject));
      if (classes.length == 1) {
        return new Analysis(
          DfTypes.typedObject(JavaPsiFacade.getElementFactory(myProject).createType(classes[0]), Nullability.NOT_NULL), ref);
      }
    }
    PsiType castType = castExpression.getType();
    if (castType != null) {
      return tryNegate(new Analysis(DfTypes.typedObject(castType, Nullability.NULLABLE), ref));
    }
    return null;
  }

  @Nullable
  private static Analysis fromAssertionError(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiAssertStatement) {
      return tryNegate(fromCondition(((PsiAssertStatement)anchor).getAssertCondition()));
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
        params.scope.setSearchInLibraries(true);
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

    private static @Nullable Analysis create(@NotNull DfType type, @Nullable PsiExpression anchor) {
      anchor = extractAnchor(anchor);
      if (anchor == null) return null;
      if (DfTypes.typedObject(anchor.getType(), Nullability.UNKNOWN).meet(type) == DfTypes.BOTTOM) return null;
      return new Analysis(type, anchor);
    }
  }
}
