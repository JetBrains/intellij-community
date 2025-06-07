// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.execution.filters.*;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.tryCast;

public final class DataflowExceptionAnalysisProvider implements ExceptionAnalysisProvider {
  private final Project myProject;

  public DataflowExceptionAnalysisProvider(Project project) {
    myProject = project;
  }

  @Override
  public @Nullable AnAction getAnalysisAction(@NotNull PsiElement anchor,
                                              @NotNull ExceptionInfo info,
                                              @NotNull Supplier<? extends List<StackLine>> nextFrames) {
    AnalysisStartingPoint analysis = getAnalysis(anchor, info);
    return createAction(analysis, nextFrames);
  }

  @Override
  public @Nullable AnAction getIntermediateRowAnalysisAction(@NotNull PsiElement anchor,
                                                             @NotNull Supplier<? extends List<StackLine>> nextFrames) {
    AnalysisStartingPoint analysis = getIntermediateRowAnalysis(anchor);
    return createAction(analysis, nextFrames);
  }

  private static @Nullable AnalysisStartingPoint getIntermediateRowAnalysis(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiExpression) {
      return new AnalysisStartingPoint(DfTypes.NULL, (PsiExpression)anchor);
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
      return AnalysisStartingPoint.create(DfTypes.NULL, arg);
    }
    arg = getArgFromContract(args, condition, ContractValue.nullValue(), false);
    if (arg != null) {
      return AnalysisStartingPoint.create(DfTypes.NOT_NULL_OBJECT, arg);
    }
    arg = getArgFromContract(args, condition, ContractValue.booleanValue(true), true);
    if (arg != null) {
      return AnalysisStartingPoint.fromCondition(arg);
    }
    arg = getArgFromContract(args, condition, ContractValue.booleanValue(false), true);
    if (arg != null) {
      return AnalysisStartingPoint.tryNegate(AnalysisStartingPoint.fromCondition(arg));
    }
    return null;
  }

  private static @Nullable PsiExpression getArgFromContract(
    PsiExpression[] args, ContractValue condition, ContractValue expectedValue, boolean equal) {
    int pos = condition.getArgumentComparedTo(expectedValue, equal).orElse(-1);
    if (pos < 0 || pos >= args.length) return null;
    return args[pos];
  }

  private @Nullable AnalysisStartingPoint getAnalysis(@NotNull PsiElement anchor,
                                                      @NotNull ExceptionInfo info) {
    if (anchor instanceof PsiKeyword && anchor.textMatches(JavaKeywords.NEW)) {
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
        return AnalysisStartingPoint.create(DfTypes.intValue(index), (PsiExpression)anchor);
      }
    }
    else if (info instanceof ClassCastExceptionInfo) {
      return fromClassCastException(anchor, ((ClassCastExceptionInfo)info).getActualClass());
    }
    else if (info instanceof NullPointerExceptionInfo || info instanceof JetBrainsNotNullInstrumentationExceptionInfo) {
      return AnalysisStartingPoint.create(DfTypes.NULL, tryCast(anchor, PsiExpression.class));
    }
    else if (info instanceof NegativeArraySizeExceptionInfo) {
      Integer size = ((NegativeArraySizeExceptionInfo)info).getSuppliedSize();
      if (size != null && size < 0 && anchor instanceof PsiExpression) {
        return AnalysisStartingPoint.create(DfTypes.intValue(size), (PsiExpression)anchor);
      }
    }
    else if (info instanceof ArithmeticExceptionInfo) {
      return fromArithmeticException(anchor);
    }
    else if (info instanceof ArrayCopyIndexOutOfBoundsExceptionInfo) {
      if (anchor instanceof PsiExpression) {
        return AnalysisStartingPoint.create(DfTypes.intValue(((ArrayCopyIndexOutOfBoundsExceptionInfo)info).getValue()), (PsiExpression)anchor);
      }
    }
    return null;
  }

  private static AnalysisStartingPoint fromArithmeticException(PsiElement anchor) {
    if (anchor instanceof PsiExpression divisor) {
      PsiType type = divisor.getType();
      if (PsiTypes.longType().equals(type)) {
        return AnalysisStartingPoint.create(DfTypes.longValue(0), divisor);
      }
      else if (TypeConversionUtil.isIntegralNumberType(type)) {
        return AnalysisStartingPoint.create(DfTypes.intValue(0), divisor);
      }
    }
    return null;
  }

  private static @Nullable AnalysisStartingPoint fromThrowStatement(PsiThrowStatement throwStatement) {
    PsiElement parent = throwStatement.getParent();
    if (parent instanceof PsiCodeBlock) {
      PsiElement statement = throwStatement.getPrevSibling();
      while (statement != null) {
        statement = statement.getPrevSibling();
        if (statement instanceof PsiIfStatement ifStatement) {
          boolean thenExits = ifStatement.getThenBranch() != null && !ControlFlowUtils.statementMayCompleteNormally(ifStatement.getThenBranch());
          boolean elseExits =
            ifStatement.getElseBranch() != null && !ControlFlowUtils.statementMayCompleteNormally(ifStatement.getElseBranch());
          if (thenExits != elseExits) {
            AnalysisStartingPoint analysis = AnalysisStartingPoint.fromCondition(ifStatement.getCondition());
            return thenExits ? AnalysisStartingPoint.tryNegate(analysis) : analysis;
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
      return AnalysisStartingPoint.fromCondition(cond);
    }
    if (parent instanceof PsiSwitchLabeledRuleStatement) {
      return fromSwitchLabel((PsiSwitchLabeledRuleStatement)parent);
    }
    return null;
  }

  private static @Nullable AnalysisStartingPoint fromSwitchLabel(PsiSwitchLabelStatementBase label) {
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
        if (statement instanceof PsiSwitchLabelStatementBase labelStatement) {
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
    AnalysisStartingPoint result = null;
    for (PsiExpression labelValue : labels) {
      DfType type = AnalysisStartingPoint.fromConstant(labelValue);
      if (type == null) return null;
      AnalysisStartingPoint next = AnalysisStartingPoint.create(type, selector);
      if (hasDefault) {
        next = AnalysisStartingPoint.tryNegate(next);
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

  private @Nullable AnalysisStartingPoint fromClassCastException(@NotNull PsiElement anchor, @Nullable String actualClass) {
    if (!(anchor instanceof PsiTypeElement)) return null;
    PsiTypeCastExpression castExpression = tryCast(anchor.getParent(), PsiTypeCastExpression.class);
    if (castExpression == null) return null;
    PsiExpression ref = AnalysisStartingPoint.extractAnchor(castExpression.getOperand());
    if (ref == null) return null;
    if (actualClass != null) {
      PsiType psiType = getPsiType(actualClass);
      if (psiType != null) {
        return new AnalysisStartingPoint(TypeConstraints.exact(psiType).asDfType().meet(DfTypes.NOT_NULL_OBJECT), ref);
      }
    }
    PsiType castType = castExpression.getType();
    if (castType != null) {
      return AnalysisStartingPoint.tryNegate(new AnalysisStartingPoint(DfTypes.typedObject(castType, Nullability.NULLABLE), ref));
    }
    return null;
  }

  private @Nullable PsiType getPsiType(String classCastExceptionType) {
    int dim = 0;
    while (classCastExceptionType.startsWith("[", dim)) {
      dim++;
    }
    String className;
    if (dim > 0) {
      if (classCastExceptionType.startsWith("L", dim) && classCastExceptionType.endsWith(";")) {
        className = classCastExceptionType.substring(dim + 1, classCastExceptionType.length() - 1);
      } else {
        if (classCastExceptionType.length() == dim + 1){
          PsiType type = PsiPrimitiveType.fromJvmTypeDescriptor(classCastExceptionType.charAt(dim));
          if (type != null) {
            while (dim-- > 0) type = type.createArrayType();
            return type;
          }
        }
        return null;
      }
    } else {
      className = classCastExceptionType;
    }
    PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(myProject), className, null, true);
    if (psiClass != null) {
      PsiType type = JavaPsiFacade.getElementFactory(myProject).createType(psiClass);
      while (dim-- > 0) type = type.createArrayType();
      return type;
    }
    return null;
  }

  private static @Nullable AnalysisStartingPoint fromAssertionError(@NotNull PsiElement anchor) {
    if (anchor instanceof PsiAssertStatement) {
      return AnalysisStartingPoint.tryNegate(AnalysisStartingPoint.fromCondition(((PsiAssertStatement)anchor).getAssertCondition()));
    }
    return null;
  }

  private @Nullable AnAction createAction(@Nullable AnalysisStartingPoint analysis,
                                          @NotNull Supplier<? extends List<StackLine>> nextFramesSupplier) {
    if (analysis == null) return null;
    String text = DfaBasedFilter.getPresentationText(analysis.myDfType, analysis.myAnchor.getType());
    if (text.isEmpty()) return null;
    return new DfaFromStacktraceAction(analysis, text, nextFramesSupplier);
  }

  private final class DfaFromStacktraceAction extends AnAction {
    private final @NotNull AnalysisStartingPoint myAnalysis;
    private final @NotNull Supplier<? extends List<StackLine>> myNextFramesSupplier;

    private DfaFromStacktraceAction(@NotNull AnalysisStartingPoint analysis,
                                    String text,
                                    @NotNull Supplier<? extends List<StackLine>> nextFramesSupplier) {
      super(null, JavaBundle
        .message("action.dfa.from.stacktrace.text", analysis.myAnchor.getText(), text), null);
      myAnalysis = analysis;
      myNextFramesSupplier = nextFramesSupplier;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<StackLine> nextFrames = myNextFramesSupplier.get();
      StackFilter stackFilter = StackFilter.from(nextFrames);
      SliceAnalysisParams params = new SliceAnalysisParams();
      params.dataFlowToThis = true;
      params.scope = new AnalysisScope(GlobalSearchScope.allScope(myProject), myProject);
      params.scope.setSearchInLibraries(true);
      params.valueFilter = new JavaValueFilter(new DfaBasedFilter(myAnalysis.myDfType), stackFilter);
      SliceManager.getInstance(myProject).createToolWindow(myAnalysis.myAnchor, params);
    }
  }
}
