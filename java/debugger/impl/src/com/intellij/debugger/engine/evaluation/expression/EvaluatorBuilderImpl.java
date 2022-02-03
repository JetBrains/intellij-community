// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class EvaluatorBuilderImpl
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class EvaluatorBuilderImpl implements EvaluatorBuilder {
  private static final EvaluatorBuilderImpl ourInstance = new EvaluatorBuilderImpl();

  private EvaluatorBuilderImpl() {
  }

  public static EvaluatorBuilder getInstance() {
    return ourInstance;
  }

  @NotNull
  public static ExpressionEvaluator build(final TextWithImports text,
                                          @Nullable PsiElement contextElement,
                                          @Nullable final SourcePosition position,
                                          @NotNull Project project) throws EvaluateException {
    CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, contextElement);
    PsiCodeFragment codeFragment = factory.createCodeFragment(text, contextElement, project);
    if (codeFragment == null) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.invalid.expression", text.getText()));
    }
    DebuggerUtils.checkSyntax(codeFragment);

    return factory.getEvaluatorBuilder().build(codeFragment, position);
  }

  @Override
  public @NotNull ExpressionEvaluator build(final PsiElement codeFragment, final SourcePosition position) throws EvaluateException {
    return new Builder(position).buildElement(codeFragment);
  }

  private static final class Builder extends JavaElementVisitor {
    private static final Logger LOG = Logger.getInstance(EvaluatorBuilderImpl.class);
    private Evaluator myResult = null;
    private PsiClass myContextPsiClass;
    private CodeFragmentEvaluator myCurrentFragmentEvaluator;
    private final Set<JavaCodeFragment> myVisitedFragments = new HashSet<>();
    @Nullable private final SourcePosition myPosition;
    @Nullable private final PsiClass myPositionPsiClass;

    private Builder(@Nullable SourcePosition position) {
      myPosition = position;
      myPositionPsiClass = JVMNameUtil.getClassAt(myPosition);
    }

    @Override
    public void visitCodeFragment(JavaCodeFragment codeFragment) {
      myVisitedFragments.add(codeFragment);
      ArrayList<Evaluator> evaluators = new ArrayList<>();

      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();

      try {
        for (PsiElement child = codeFragment.getFirstChild(); child != null; child = child.getNextSibling()) {
          child.accept(this);
          if (myResult != null) {
            evaluators.add(myResult);
          }
          myResult = null;
        }

        myCurrentFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[0]));
        myResult = myCurrentFragmentEvaluator;
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Override
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      throwExpressionInvalid(element);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      final PsiExpression rExpression = expression.getRExpression();
      if(rExpression == null) {
        throwExpressionInvalid(expression);
      }

      rExpression.accept(this);
      Evaluator rEvaluator = myResult;

      final PsiExpression lExpression = expression.getLExpression();
      final PsiType lType = lExpression.getType();
      if(lType == null) {
        throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", lExpression.getText()));
      }

      IElementType assignmentType = expression.getOperationTokenType();
      PsiType rType = rExpression.getType();
      if(!TypeConversionUtil.areTypesAssignmentCompatible(lType, rExpression) && rType != null) {
        throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.incompatible.types", expression.getOperationSign().getText()));
      }
      lExpression.accept(this);
      Evaluator lEvaluator = myResult;

      rEvaluator = handleAssignmentBoxingAndPrimitiveTypeConversions(lType, rType, rEvaluator, expression.getProject());

      if (assignmentType != JavaTokenType.EQ) {
        IElementType opType = TypeConversionUtil.convertEQtoOperation(assignmentType);
        final PsiType typeForBinOp = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opType, true);
        if (typeForBinOp == null || rType == null) {
          throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
        }
        rEvaluator = createBinaryEvaluator(lEvaluator, lType, rEvaluator, rType, opType, typeForBinOp);
      }
      myResult = new AssignmentEvaluator(lEvaluator, rEvaluator);
    }

    // returns rEvaluator possibly wrapped with boxing/unboxing and casting evaluators
    private static Evaluator handleAssignmentBoxingAndPrimitiveTypeConversions(PsiType lType,
                                                                               PsiType rType,
                                                                               Evaluator rEvaluator,
                                                                               Project project) {
      final PsiType unboxedLType = PsiPrimitiveType.getUnboxedType(lType);

      if (unboxedLType != null) {
        if (rType instanceof PsiPrimitiveType && !PsiType.NULL.equals(rType)) {
          if (!rType.equals(unboxedLType)) {
            rEvaluator = createTypeCastEvaluator(rEvaluator, unboxedLType);
          }
          rEvaluator = new BoxingEvaluator(rEvaluator);
        }
      }
      else {
        // either primitive type or not unboxable type
        if (lType instanceof PsiPrimitiveType) {
          if (rType instanceof PsiClassType) {
            rEvaluator = new UnBoxingEvaluator(rEvaluator);
          }
          final PsiPrimitiveType unboxedRType = PsiPrimitiveType.getUnboxedType(rType);
          final PsiType _rType = unboxedRType != null? unboxedRType : rType;
          if (_rType instanceof PsiPrimitiveType && !PsiType.NULL.equals(_rType)) {
            if (!lType.equals(_rType)) {
              rEvaluator = createTypeCastEvaluator(rEvaluator, lType);
            }
          }
        }
        else if (lType instanceof PsiClassType && rType instanceof PsiPrimitiveType && !PsiType.NULL.equals(rType)) {
          final PsiClassType rightBoxed =
            ((PsiPrimitiveType)rType).getBoxedType(PsiManager.getInstance(project), ((PsiClassType)lType).getResolveScope());
          if (rightBoxed != null && TypeConversionUtil.isAssignable(lType, rightBoxed)) {
            rEvaluator = new BoxingEvaluator(rEvaluator);
          }
        }
      }
      return rEvaluator;
    }

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      if (statement.getResourceList() != null) {
        throw new EvaluateRuntimeException(new UnsupportedExpressionException("Try with resources is not yet supported"));
      }
      Evaluator bodyEvaluator = accept(statement.getTryBlock());
      if (bodyEvaluator != null) {
        PsiCatchSection[] catchSections = statement.getCatchSections();
        List<CatchEvaluator> evaluators = new ArrayList<>();
        for (PsiCatchSection catchSection : catchSections) {
          PsiParameter parameter = catchSection.getParameter();
          PsiCodeBlock catchBlock = catchSection.getCatchBlock();
          if (parameter != null && catchBlock != null) {
            CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
            try {
              myCurrentFragmentEvaluator.setInitialValue(parameter.getName(), null);
              myCurrentFragmentEvaluator.setStatements(visitStatements(catchBlock.getStatements()));
              PsiType type = parameter.getType();
              List<PsiType> types =
                type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : Collections.singletonList(type);
              for (PsiType psiType : types) {
                evaluators.add(new CatchEvaluator(psiType.getCanonicalText(), parameter.getName(), myCurrentFragmentEvaluator));
              }
            }
            finally{
              myCurrentFragmentEvaluator = oldFragmentEvaluator;
            }
          }
        }
        myResult = new TryEvaluator(bodyEvaluator, evaluators, accept(statement.getFinallyBlock()));
      }
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      Evaluator accept = accept(statement.getException());
      if (accept != null) {
        myResult = new ThrowEvaluator(accept);
      }
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      myResult = new ReturnEvaluator(accept(statement.getReturnValue()));
    }

    @Override
    public void visitYieldStatement(PsiYieldStatement statement) {
      myResult = new SwitchEvaluator.YieldEvaluator(accept(statement.getExpression()));
    }

    @Override
    public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
      throw new EvaluateRuntimeException(new UnsupportedExpressionException("Synchronized is not yet supported"));
    }

    @Override
    public void visitStatement(PsiStatement statement) {
      LOG.error(JavaDebuggerBundle.message("evaluation.error.statement.not.supported", statement.getText()));
      throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.statement.not.supported", statement.getText()));
    }

    private CodeFragmentEvaluator setNewCodeFragmentEvaluator() {
      CodeFragmentEvaluator old = myCurrentFragmentEvaluator;
      myCurrentFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
      return old;
    }

    private Evaluator[] visitStatements(PsiStatement[] statements) {
      List<Evaluator> evaluators = new ArrayList<>();
      for (PsiStatement psiStatement : statements) {
        psiStatement.accept(this);
        if (myResult != null) { // for example declaration w/o initializer produces empty evaluator now
          evaluators.add(DisableGC.create(myResult));
        }
        myResult = null;
      }
      return evaluators.toArray(new Evaluator[0]);
    }

    @Override
    public void visitCodeBlock(PsiCodeBlock block) {
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        myResult = new BlockStatementEvaluator(visitStatements(block.getStatements()));
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Override
    public void visitBlockStatement(PsiBlockStatement statement) {
      visitCodeBlock(statement.getCodeBlock());
    }

    @Override
    public void visitLabeledStatement(PsiLabeledStatement labeledStatement) {
      PsiStatement statement = labeledStatement.getStatement();
      if (statement != null) {
        statement.accept(this);
      }
    }

    private static String getLabel(PsiElement element) {
      String label = null;
      if(element.getParent() instanceof PsiLabeledStatement) {
        label = ((PsiLabeledStatement)element.getParent()).getName();
      }
      return label;
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      Evaluator bodyEvaluator = accept(statement.getBody());
      Evaluator conditionEvaluator = accept(statement.getCondition());
      if (conditionEvaluator != null) {
        myResult = new DoWhileStatementEvaluator(new UnBoxingEvaluator(conditionEvaluator), bodyEvaluator, getLabel(statement));
      }
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        Evaluator bodyEvaluator = accept(statement.getBody());
        Evaluator conditionEvaluator = accept(statement.getCondition());
        if (conditionEvaluator != null) {
          myResult = new WhileStatementEvaluator(new UnBoxingEvaluator(conditionEvaluator), bodyEvaluator, getLabel(statement));
        }
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        Evaluator initializerEvaluator = accept(statement.getInitialization());
        Evaluator conditionEvaluator = accept(statement.getCondition());
        if (conditionEvaluator != null) {
          conditionEvaluator = new UnBoxingEvaluator(conditionEvaluator);
        }
        Evaluator updateEvaluator = accept(statement.getUpdate());
        Evaluator bodyEvaluator = accept(statement.getBody());
        if (bodyEvaluator != null) {
          myResult =
            new ForStatementEvaluator(initializerEvaluator, conditionEvaluator, updateEvaluator, bodyEvaluator, getLabel(statement));
        }
      } finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        PsiParameter parameter = statement.getIterationParameter();
        String iterationParameterName = parameter.getName();
        myCurrentFragmentEvaluator.setInitialValue(iterationParameterName, null);
        SyntheticVariableEvaluator iterationParameterEvaluator =
          new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, iterationParameterName, null);

        Evaluator iteratedValueEvaluator = accept(statement.getIteratedValue());
        Evaluator bodyEvaluator = accept(statement.getBody());
        if (bodyEvaluator != null) {
          myResult = new ForeachStatementEvaluator(iterationParameterEvaluator, iteratedValueEvaluator, bodyEvaluator, getLabel(statement));
        }
      } finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Nullable
    private Evaluator accept(@Nullable PsiElement element) {
      if (element == null || element instanceof PsiEmptyStatement) {
        return null;
      }
      element.accept(this);
      return myResult;
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        PsiStatement thenBranch = statement.getThenBranch();
        if(thenBranch == null) return;
        thenBranch.accept(this);
        Evaluator thenEvaluator = myResult;

        PsiStatement elseBranch = statement.getElseBranch();
        Evaluator elseEvaluator = null;
        if(elseBranch != null){
          elseBranch.accept(this);
          elseEvaluator = myResult;
        }

        PsiExpression condition = statement.getCondition();
        if(condition == null) return;
        condition.accept(this);

        myResult = new IfStatementEvaluator(new UnBoxingEvaluator(myResult), thenEvaluator, elseEvaluator);
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    private void visitSwitchBlock(PsiSwitchBlock statement) {
      PsiCodeBlock body = statement.getBody();
      if (body != null) {
        Evaluator expressionEvaluator = accept(statement.getExpression());
        if (expressionEvaluator != null) {
          myResult = new SwitchEvaluator(expressionEvaluator, visitStatements(body.getStatements()), getLabel(statement));
        }
      }
    }

    @Override
    public void visitSwitchStatement(PsiSwitchStatement statement) {
      visitSwitchBlock(statement);
    }

    @Override
    public void visitSwitchExpression(PsiSwitchExpression expression) {
      visitSwitchBlock(expression);
    }

    private void visitSwitchLabelStatementBase(PsiSwitchLabelStatementBase statement) {
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        List<Evaluator> evaluators = new SmartList<>();
        PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
        boolean defaultCase = statement.isDefaultCase();
        if (labelElementList != null) {
          for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
            if (labelElement instanceof PsiDefaultCaseLabelElement) {
              defaultCase = true;
              continue;
            } else if (labelElement instanceof PsiPattern) {
              PatternLabelEvaluator evaluator = getPatternLabelEvaluator((PsiPattern)labelElement);
              if (evaluator != null) {
                evaluators.add(evaluator);
              }
              continue;
            }
            Evaluator evaluator = accept(labelElement);
            if (evaluator != null) {
              evaluators.add(evaluator);
            }
          }
        }
        if (statement instanceof PsiSwitchLabeledRuleStatement) {
          myResult = new SwitchEvaluator.SwitchCaseRuleEvaluator(evaluators, defaultCase,
                                                                 accept(((PsiSwitchLabeledRuleStatement)statement).getBody()));
        }
        else {
          myResult = new SwitchEvaluator.SwitchCaseEvaluator(evaluators, defaultCase);
        }
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Nullable
    private PatternLabelEvaluator getPatternLabelEvaluator(@NotNull PsiPattern pattern) {
      PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(pattern, PsiSwitchBlock.class);
      if (switchBlock == null) return null;
      PsiExpression selector = switchBlock.getExpression();
      if (selector == null) return null;
      selector.accept(this);
      Evaluator selectorEvaluator = myResult;
      PsiPatternVariable patternVariable = JavaPsiPatternUtil.getPatternVariable(pattern);
      if (patternVariable == null) return null;
      PsiPattern naked = JavaPsiPatternUtil.skipParenthesizedPatternDown(pattern);
      Evaluator guardingEvaluator = null;
      if (naked instanceof PsiGuardedPattern) {
        PsiExpression guardingExpression = ((PsiGuardedPattern)naked).getGuardingExpression();
        if (guardingExpression != null) {
          guardingExpression.accept(this);
          guardingEvaluator = myResult != null ? new UnBoxingEvaluator(myResult) : null;
        }
      }
      String patternVariableName = patternVariable.getName();
      myCurrentFragmentEvaluator.setInitialValue(patternVariableName, null);
      Evaluator patternVariableEvaluator = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, patternVariableName, null);
      PsiType type = JavaPsiPatternUtil.getPatternType(pattern);
      if (type == null) return null;
      return new PatternLabelEvaluator(selectorEvaluator, new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)),
                                       patternVariableEvaluator, guardingEvaluator);
    }

    @Override
    public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
      visitSwitchLabelStatementBase(statement);
    }

    @Override
    public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
      visitSwitchLabelStatementBase(statement);
    }

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      myResult = BreakContinueStatementEvaluator.createBreakEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
    }

    @Override
    public void visitContinueStatement(PsiContinueStatement statement) {
      PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
      myResult = BreakContinueStatementEvaluator.createContinueEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
    }

    @Override
    public void visitExpressionListStatement(PsiExpressionListStatement statement) {
      myResult = new ExpressionListEvaluator(ContainerUtil.mapNotNull(statement.getExpressionList().getExpressions(), this::accept));
    }

    @Override
    public void visitEmptyStatement(PsiEmptyStatement statement) {
      // do nothing
    }

    @Override
    public void visitExpressionStatement(PsiExpressionStatement statement) {
      statement.getExpression().accept(this);
    }

    @Override
    public void visitExpression(PsiExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitExpression " + expression);
      }
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression wideExpression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitPolyadicExpression " + wideExpression);
      }
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        PsiExpression[] operands = wideExpression.getOperands();
        operands[0].accept(this);
        Evaluator result = myResult;
        PsiType lType = operands[0].getType();
        for (int i = 1; i < operands.length; i++) {
          PsiExpression expression = operands[i];
          if (expression == null) {
            throwExpressionInvalid(wideExpression);
          }
          expression.accept(this);
          Evaluator rResult = myResult;
          IElementType opType = wideExpression.getOperationTokenType();
          PsiType rType = expression.getType();
          if (rType == null) {
            throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
          }
          final PsiType typeForBinOp = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opType, true);
          if (typeForBinOp == null) {
            throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", wideExpression.getText()));
          }
          myResult = createBinaryEvaluator(result, lType, rResult, rType, opType, typeForBinOp);
          lType = typeForBinOp;
          result = myResult;
        }
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    // constructs binary evaluator handling unboxing and numeric promotion issues
    private static Evaluator createBinaryEvaluator(Evaluator lResult,
                                                                   PsiType lType,
                                                                   Evaluator rResult,
                                                                   @NotNull PsiType rType,
                                                                   @NotNull IElementType operation,
                                                                   @NotNull PsiType expressionExpectedType) {
      // handle unboxing if necessary
      if (isUnboxingInBinaryExpressionApplicable(lType, rType, operation)) {
        if (rType instanceof PsiClassType && UnBoxingEvaluator.isTypeUnboxable(rType.getCanonicalText())) {
          rResult = new UnBoxingEvaluator(rResult);
        }
        if (lType instanceof PsiClassType && UnBoxingEvaluator.isTypeUnboxable(lType.getCanonicalText())) {
          lResult = new UnBoxingEvaluator(lResult);
        }
      }
      if (isBinaryNumericPromotionApplicable(lType, rType, operation)) {
        PsiType _lType = lType;
        final PsiPrimitiveType unboxedLType = PsiPrimitiveType.getUnboxedType(lType);
        if (unboxedLType != null) {
          _lType = unboxedLType;
        }

        PsiType _rType = rType;
        final PsiPrimitiveType unboxedRType = PsiPrimitiveType.getUnboxedType(rType);
        if (unboxedRType != null) {
          _rType = unboxedRType;
        }

        // handle numeric promotion
        if (PsiType.DOUBLE.equals(_lType)) {
          if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.DOUBLE)) {
            rResult = createTypeCastEvaluator(rResult, PsiType.DOUBLE);
          }
        }
        else if (PsiType.DOUBLE.equals(_rType)) {
          if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.DOUBLE)) {
            lResult = createTypeCastEvaluator(lResult, PsiType.DOUBLE);
          }
        }
        else if (PsiType.FLOAT.equals(_lType)) {
          if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.FLOAT)) {
            rResult = createTypeCastEvaluator(rResult, PsiType.FLOAT);
          }
        }
        else if (PsiType.FLOAT.equals(_rType)) {
          if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.FLOAT)) {
            lResult = createTypeCastEvaluator(lResult, PsiType.FLOAT);
          }
        }
        else if (PsiType.LONG.equals(_lType)) {
          if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.LONG)) {
            rResult = createTypeCastEvaluator(rResult, PsiType.LONG);
          }
        }
        else if (PsiType.LONG.equals(_rType)) {
          if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.LONG)) {
            lResult = createTypeCastEvaluator(lResult, PsiType.LONG);
          }
        }
        else {
          if (!PsiType.INT.equals(_lType) && TypeConversionUtil.areTypesConvertible(_lType, PsiType.INT)) {
            lResult = createTypeCastEvaluator(lResult, PsiType.INT);
          }
          if (!PsiType.INT.equals(_rType) && TypeConversionUtil.areTypesConvertible(_rType, PsiType.INT)) {
            rResult = createTypeCastEvaluator(rResult, PsiType.INT);
          }
        }
      }
      // unary numeric promotion if applicable
      else if (ExpressionParser.SHIFT_OPS.contains(operation)) {
        lResult = handleUnaryNumericPromotion(lType, lResult);
        rResult = handleUnaryNumericPromotion(rType, rResult);
      }

      return DisableGC.create(new BinaryExpressionEvaluator(lResult, rResult, operation, expressionExpectedType.getCanonicalText()));
    }

    private static boolean isBinaryNumericPromotionApplicable(PsiType lType, PsiType rType, IElementType opType) {
      if (lType == null || rType == null) {
        return false;
      }
      if (!TypeConversionUtil.isNumericType(lType) || !TypeConversionUtil.isNumericType(rType)) {
        return false;
      }
      if (opType == JavaTokenType.EQEQ || opType == JavaTokenType.NE) {
        if (PsiType.NULL.equals(lType) || PsiType.NULL.equals(rType)) {
          return false;
        }
        if (lType instanceof PsiClassType && rType instanceof PsiClassType) {
          return false;
        }
        if (lType instanceof PsiClassType) {
          return PsiPrimitiveType.getUnboxedType(lType) != null; // should be unboxable
        }
        if (rType instanceof PsiClassType) {
          return PsiPrimitiveType.getUnboxedType(rType) != null; // should be unboxable
        }
        return true;
      }

      return opType == JavaTokenType.ASTERISK ||
          opType == JavaTokenType.DIV         ||
          opType == JavaTokenType.PERC        ||
          opType == JavaTokenType.PLUS        ||
          opType == JavaTokenType.MINUS       ||
          opType == JavaTokenType.LT          ||
          opType == JavaTokenType.LE          ||
          opType == JavaTokenType.GT          ||
          opType == JavaTokenType.GE          ||
          opType == JavaTokenType.AND         ||
          opType == JavaTokenType.XOR         ||
          opType == JavaTokenType.OR;

    }

    private static boolean isUnboxingInBinaryExpressionApplicable(PsiType lType, PsiType rType, IElementType opCode) {
      if (PsiType.NULL.equals(lType) || PsiType.NULL.equals(rType)) {
        return false;
      }
      // handle '==' and '!=' separately
      if (opCode == JavaTokenType.EQEQ || opCode == JavaTokenType.NE) {
        return lType instanceof PsiPrimitiveType && rType instanceof PsiClassType ||
               lType instanceof PsiClassType     && rType instanceof PsiPrimitiveType;
      }
      // concat with a String
      if (opCode == JavaTokenType.PLUS) {
        if ((lType instanceof PsiClassType && lType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) ||
            (rType instanceof PsiClassType && rType.equalsToText(CommonClassNames.JAVA_LANG_STRING))){
          return false;
        }
      }
      // all other operations at least one should be of class type
      return lType instanceof PsiClassType || rType instanceof PsiClassType;
    }

    /**
     * @param type
     * @return promotion type to cast to or null if no casting needed
     */
    @Nullable
    private static PsiType calcUnaryNumericPromotionType(PsiPrimitiveType type) {
      if (PsiType.BYTE.equals(type) || PsiType.SHORT.equals(type) || PsiType.CHAR.equals(type) || PsiType.INT.equals(type)) {
        return PsiType.INT;
      }
      return null;
    }

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      List<Evaluator> evaluators = new ArrayList<>();

      PsiElement[] declaredElements = statement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable) {
          if (myCurrentFragmentEvaluator != null) {
            final PsiLocalVariable localVariable = (PsiLocalVariable)declaredElement;

            final PsiType lType = localVariable.getType();

            PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(localVariable.getProject());
            try {
              PsiExpression initialValue = elementFactory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(lType), null);
              Object value = JavaConstantExpressionEvaluator.computeConstantExpression(initialValue, true);
              myCurrentFragmentEvaluator.setInitialValue(localVariable.getName(), value);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }

            PsiExpression initializer = localVariable.getInitializer();
            if (initializer != null) {
              try {
                if (!TypeConversionUtil.areTypesAssignmentCompatible(lType, initializer)) {
                  throwEvaluateException(
                    JavaDebuggerBundle.message("evaluation.error.incompatible.variable.initializer.type", localVariable.getName()));
                }
                final PsiType rType = initializer.getType();
                initializer.accept(this);
                Evaluator rEvaluator = myResult;

                PsiExpression localVarReference = elementFactory.createExpressionFromText(localVariable.getName(), initializer);

                localVarReference.accept(this);
                Evaluator lEvaluator = myResult;
                rEvaluator = handleAssignmentBoxingAndPrimitiveTypeConversions(localVarReference.getType(), rType, rEvaluator,
                                                                               statement.getProject());

                Evaluator assignment = new AssignmentEvaluator(lEvaluator, rEvaluator);
                evaluators.add(assignment);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
          else {
            throw new EvaluateRuntimeException(new EvaluateException(
              JavaDebuggerBundle.message("evaluation.error.local.variable.declarations.not.supported"), null));
          }
        }
        else {
          throw new EvaluateRuntimeException(new EvaluateException(
            JavaDebuggerBundle.message("evaluation.error.unsupported.declaration", declaredElement.getText()), null));
        }
      }

      if(!evaluators.isEmpty()) {
        CodeFragmentEvaluator codeFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
        codeFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[0]));
        myResult = codeFragmentEvaluator;
      } else {
        myResult = null;
      }
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitConditionalExpression " + expression);
      }
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      if (thenExpression == null || elseExpression == null){
        throwExpressionInvalid(expression);
      }
      PsiExpression condition = expression.getCondition();
      condition.accept(this);
      if (myResult == null) {
        throwExpressionInvalid(condition);
      }
      Evaluator conditionEvaluator = new UnBoxingEvaluator(myResult);
      thenExpression.accept(this);
      if (myResult == null) {
        throwExpressionInvalid(thenExpression);
      }
      Evaluator thenEvaluator = myResult;
      elseExpression.accept(this);
      if (myResult == null) {
        throwExpressionInvalid(elseExpression);
      }
      Evaluator elseEvaluator = myResult;
      myResult = new ConditionalExpressionEvaluator(conditionEvaluator, thenEvaluator, elseEvaluator);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitReferenceExpression " + expression);
      }
      PsiExpression qualifier = expression.getQualifierExpression();
      JavaResolveResult resolveResult = expression.advancedResolve(true);
      PsiElement element = resolveResult.getElement();

      if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
        final Value labeledValue = element.getUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY);
        if (labeledValue != null) {
          myResult = new IdentityEvaluator(labeledValue);
          return;
        }
        final PsiVariable psiVar = (PsiVariable)element;
        String localName = psiVar.getName();
        //synthetic variable
        final PsiFile containingFile = element.getContainingFile();
        if (myCurrentFragmentEvaluator != null &&
            ((containingFile instanceof PsiCodeFragment && myVisitedFragments.contains(containingFile)) ||
             myCurrentFragmentEvaluator.hasValue(localName))) {
          // psiVariable may live in PsiCodeFragment not only in debugger editors, for example Fabrique has such variables.
          // So treat it as synthetic var only when this code fragment is located in DebuggerEditor,
          // that's why we need to check that containing code fragment is the one we visited
          JVMName jvmName = JVMNameUtil.getJVMQualifiedName(CompilingEvaluatorTypesUtil.getVariableType((PsiVariable)element));
          myResult = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, localName, jvmName);
          return;
        }
        // local variable
        PsiClass variableClass = getContainingClass(psiVar);
        final PsiClass positionClass = getPositionClass();
        if (Objects.equals(positionClass, variableClass)) {
          PsiElement method = DebuggerUtilsEx.getContainingMethod(expression);
          boolean canScanFrames = method instanceof PsiLambdaExpression || ContextUtil.isJspImplicit(element);
          myResult = new LocalVariableEvaluator(localName, canScanFrames);
          return;
        }
        // the expression references final var outside the context's class (in some of the outer classes)
        // oneLevelLess() because val$ are located in the same class
        CaptureTraverser traverser = createTraverser(variableClass, "Base class not found for " + psiVar.getName(), false).oneLevelLess();
        if (traverser.isValid()) {
          PsiExpression initializer = psiVar.getInitializer();
          if(initializer != null) {
            Object value = JavaPsiFacade.getInstance(psiVar.getProject()).getConstantEvaluationHelper().computeConstantExpression(initializer);
            if(value != null) {
              PsiType type = resolveResult.getSubstitutor().substitute(psiVar.getType());
              myResult = new LiteralEvaluator(value, type.getCanonicalText());
              return;
            }
          }
          Evaluator objectEvaluator = new ThisEvaluator(traverser);
          myResult = createFallbackEvaluator(
            new FieldEvaluator(objectEvaluator, FieldEvaluator.createClassFilter(positionClass), "val$" + localName),
            new LocalVariableEvaluator(localName, true));
          return;
        }
        throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.local.variable.missing.from.class.closure", localName));
      }
      else if (element instanceof PsiField) {
        final PsiField psiField = (PsiField)element;
        final PsiClass fieldClass = psiField.getContainingClass();
        if(fieldClass == null) {
          throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.resolve.field.class", psiField.getName())); return;
        }
        Evaluator objectEvaluator;
        if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
          JVMName className = JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(psiField));
          if (className == null) {
            className = JVMNameUtil.getJVMQualifiedName(fieldClass);
          }
          objectEvaluator = new TypeEvaluator(className);
        }
        else if(qualifier != null) {
          qualifier.accept(this);
          objectEvaluator = myResult;
        }
        else {
          CaptureTraverser traverser = createTraverser(fieldClass, fieldClass.getName(), true);
          if (!traverser.isValid()) {
            throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.sources.for.field.class", psiField.getName()));
          }
          objectEvaluator = new ThisEvaluator(traverser);
        }
        myResult = new FieldEvaluator(objectEvaluator, FieldEvaluator.createClassFilter(fieldClass), psiField.getName());
      }
      else {
        //let's guess what this could be
        PsiElement nameElement = expression.getReferenceNameElement(); // get "b" part
        String name;
        if (nameElement instanceof PsiIdentifier) {
          name = nameElement.getText();
        }
        else {
          //noinspection HardCodedStringLiteral
          final String elementDisplayString = nameElement != null ? nameElement.getText() : "(null)";
          throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.identifier.expected", elementDisplayString));
          return;
        }

        if(qualifier != null) {
          final PsiElement qualifierTarget = qualifier instanceof PsiReferenceExpression
                                             ? ((PsiReferenceExpression)qualifier).resolve() : null;
          if (qualifierTarget instanceof PsiClass) {
            // this is a call to a 'static' field
            PsiClass psiClass = (PsiClass)qualifierTarget;
            final JVMName typeName = JVMNameUtil.getJVMQualifiedName(psiClass);
            myResult = new FieldEvaluator(new TypeEvaluator(typeName), FieldEvaluator.createClassFilter(psiClass), name);
          }
          else {
            qualifier.accept(this);
            if (myResult == null) {
              throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.qualifier", qualifier.getText()));
            }

            myResult = new FieldEvaluator(myResult, FieldEvaluator.createClassFilter(qualifier.getType()), name);
          }
        }
        else {
          myResult = createFallbackEvaluator(new LocalVariableEvaluator(name, false),
                                             new FieldEvaluator(new ThisEvaluator(), FieldEvaluator.TargetClassFilter.ALL, name));
        }
      }
    }

    private static Evaluator createFallbackEvaluator(final Evaluator primary, final Evaluator fallback) {
      return new Evaluator() {
        private boolean myIsFallback;
        @Override
        public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
          try {
            return primary.evaluate(context);
          }
          catch (EvaluateException e) {
            try {
              Object res = fallback.evaluate(context);
              myIsFallback = true;
              return res;
            }
            catch (EvaluateException e1) {
              throw e;
            }
          }
        }

        @Override
        public Modifier getModifier() {
          return myIsFallback ? fallback.getModifier() : primary.getModifier();
        }
      };
    }

    private static void throwExpressionInvalid(PsiElement expression) {
      throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.invalid.expression", expression.getText()));
    }

    private static void throwEvaluateException(String message) throws EvaluateRuntimeException {
      throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(message));
    }

    @Override
    public void visitSuperExpression(PsiSuperExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitSuperExpression " + expression);
      }
      myResult = new SuperEvaluator(createTraverser(expression.getQualifier()));
    }

    @Override
    public void visitThisExpression(PsiThisExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitThisExpression " + expression);
      }
      myResult = new ThisEvaluator(createTraverser(expression.getQualifier()));
    }

    private CaptureTraverser createTraverser(final PsiJavaCodeReferenceElement qualifier) {
      if (qualifier != null) {
        return createTraverser(qualifier.resolve(), qualifier.getText(), false);
      }
      return CaptureTraverser.direct();
    }

    private CaptureTraverser createTraverser(PsiElement targetClass, String name, boolean checkInheritance) {
      PsiClass fromClass = getPositionClass();
      if (!(targetClass instanceof PsiClass) || fromClass == null) {
        throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.invalid.expression", name));
      }
      try {
        CaptureTraverser traverser = CaptureTraverser.create((PsiClass)targetClass, fromClass, checkInheritance);
        if (!traverser.isValid() && !fromClass.equals(myContextPsiClass)) { // do not check twice
          traverser = CaptureTraverser.create((PsiClass)targetClass, myContextPsiClass, checkInheritance);
        }

        if (!traverser.isValid()) {
          LOG.warn("Unable create valid CaptureTraverser to access 'this'.");
          traverser = CaptureTraverser.direct();
        }

        return traverser;
      }
      catch (Exception e) {
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(e));
      }
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitInstanceOfExpression " + expression);
      }
      PsiElement parentOfType = PsiTreeUtil.getParentOfType(expression, PsiWhileStatement.class,
                                                            PsiIfStatement.class, PsiPolyadicExpression.class);
      CodeFragmentEvaluator oldFragmentEvaluator = parentOfType != null ?
                                                   myCurrentFragmentEvaluator : setNewCodeFragmentEvaluator();
      try {
        PsiTypeElement checkType = expression.getCheckType();
        if (checkType == null) {
          throwExpressionInvalid(expression);
        }
        PsiType type = checkType.getType();
        expression.getOperand().accept(this);
        //    ClassObjectEvaluator typeEvaluator = new ClassObjectEvaluator(type.getCanonicalText());
        Evaluator operandEvaluator = myResult;

        Evaluator patternVariable = null;
        PsiPattern pattern = expression.getPattern();
        if (pattern instanceof PsiTypeTestPattern) {
          PsiPatternVariable variable = ((PsiTypeTestPattern)pattern).getPatternVariable();
          if (variable != null) {
            String variableName = variable.getName();
            myCurrentFragmentEvaluator.setInitialValue(variableName, null);
            patternVariable = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, variableName, null);
          }
        }

        myResult = new InstanceofEvaluator(operandEvaluator, new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)), patternVariable);
      } finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitParenthesizedExpression " + expression);
      }
      PsiExpression expr = expression.getExpression();
      if (expr != null){
        expr.accept(this);
      }
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      if(expression.getType() == null) {
        throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
      }

      final PsiExpression operandExpression = expression.getOperand();
      operandExpression.accept(this);

      final Evaluator operandEvaluator = myResult;

      final IElementType operation = expression.getOperationTokenType();
      final PsiType operandType = operandExpression.getType();
      @Nullable final PsiType unboxedOperandType = PsiPrimitiveType.getUnboxedType(operandType);

      Evaluator incrementImpl = createBinaryEvaluator(
        operandEvaluator, operandType,
        new LiteralEvaluator(1, "int"), PsiType.INT,
        operation == JavaTokenType.PLUSPLUS ? JavaTokenType.PLUS : JavaTokenType.MINUS,
        unboxedOperandType!= null? unboxedOperandType : operandType
      );
      if (unboxedOperandType != null) {
        incrementImpl = new BoxingEvaluator(incrementImpl);
      }
      myResult = new PostfixOperationEvaluator(operandEvaluator, incrementImpl);
    }

    @Override
    public void visitPrefixExpression(final PsiPrefixExpression expression) {
      final PsiType expressionType = expression.getType();
      if(expressionType == null) {
        throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
      }

      final PsiExpression operandExpression = expression.getOperand();
      if (operandExpression == null) {
        throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.expression.operand", expression.getText()));
      }

      operandExpression.accept(this);
      Evaluator operandEvaluator = myResult;

      // handle unboxing issues
      final PsiType operandType = operandExpression.getType();
      @Nullable
      final PsiType unboxedOperandType = PsiPrimitiveType.getUnboxedType(operandType);

      final IElementType operation = expression.getOperationTokenType();

      if(operation == JavaTokenType.PLUSPLUS || operation == JavaTokenType.MINUSMINUS) {
        try {
          final Evaluator rightEval = createBinaryEvaluator(
            operandEvaluator, operandType,
            new LiteralEvaluator(Integer.valueOf(1), "int"), PsiType.INT,
            operation == JavaTokenType.PLUSPLUS ? JavaTokenType.PLUS : JavaTokenType.MINUS,
            unboxedOperandType!= null? unboxedOperandType : operandType
          );
          myResult = new AssignmentEvaluator(operandEvaluator, unboxedOperandType != null? new BoxingEvaluator(rightEval) : rightEval);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      else {
        if (JavaTokenType.PLUS.equals(operation) || JavaTokenType.MINUS.equals(operation)|| JavaTokenType.TILDE.equals(operation)) {
          operandEvaluator = handleUnaryNumericPromotion(operandType, operandEvaluator);
        }
        else {
          if (unboxedOperandType != null) {
            operandEvaluator = new UnBoxingEvaluator(operandEvaluator);
          }
        }
        myResult = new UnaryExpressionEvaluator(operation, expressionType.getCanonicalText(), operandEvaluator, expression.getOperationSign().getText());
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitMethodCallExpression " + expression);
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] argExpressions = argumentList.getExpressions();
      Evaluator[] argumentEvaluators = new Evaluator[argExpressions.length];
      // evaluate arguments
      for (int idx = 0; idx < argExpressions.length; idx++) {
        final PsiExpression psiExpression = argExpressions[idx];
        psiExpression.accept(this);
        if (myResult == null) {
          // cannot build evaluator
          throwExpressionInvalid(psiExpression);
        }
        argumentEvaluators[idx] = DisableGC.create(myResult);
      }
      PsiReferenceExpression methodExpr = expression.getMethodExpression();

      final JavaResolveResult resolveResult = methodExpr.advancedResolve(false);
      final PsiMethod psiMethod = CompilingEvaluatorTypesUtil.getReferencedMethod(resolveResult);

      PsiExpression qualifier = methodExpr.getQualifierExpression();
      Evaluator objectEvaluator;
      JVMName contextClass = null;

      if(psiMethod != null) {
        PsiClass methodPsiClass = psiMethod.getContainingClass();
        contextClass =  JVMNameUtil.getJVMQualifiedName(methodPsiClass);
        if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
          objectEvaluator = new TypeEvaluator(contextClass);
        }
        else if (qualifier != null ) {
          qualifier.accept(this);
          objectEvaluator = myResult;
        }
        else {
          CaptureTraverser traverser = CaptureTraverser.direct();
          PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
          if (currentFileResolveScope instanceof PsiClass) {
            traverser = createTraverser(currentFileResolveScope, ((PsiClass)currentFileResolveScope).getName(), false);
          }
          objectEvaluator = new ThisEvaluator(traverser);
        }
      }
      else {
        //trying to guess
        if (qualifier != null) {
          PsiType type = qualifier.getType();

          if (type != null) {
            contextClass = JVMNameUtil.getJVMQualifiedName(type);
          }

          if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass) {
            // this is a call to a 'static' method but class is not available, try to evaluate by qname
            if (contextClass == null) {
              contextClass = JVMNameUtil.getJVMRawText(((PsiReferenceExpression)qualifier).getQualifiedName());
            }
            objectEvaluator = new TypeEvaluator(contextClass);
          }
          else {
            qualifier.accept(this);
            objectEvaluator = myResult;
          }
        }
        else {
          objectEvaluator = new ThisEvaluator();
          PsiClass positionClass = getPositionClass();
          if (positionClass != null) {
            contextClass = JVMNameUtil.getJVMQualifiedName(positionClass);
          }
          //else {
          //  throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
          //    JavaDebuggerBundle.message("evaluation.error.method.not.found", methodExpr.getReferenceName()))
          //  );
          //}
        }
      }

      if (objectEvaluator == null) {
        throwExpressionInvalid(expression);
      }

      if (psiMethod != null && !psiMethod.isConstructor()) {
        if (psiMethod.getReturnType() == null) {
          throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.unknown.method.return.type", psiMethod.getText()));
        }
      }

      boolean mustBeVararg = false;

      if (psiMethod != null) {
        processBoxingConversions(psiMethod.getParameterList().getParameters(), argExpressions, resolveResult.getSubstitutor(), argumentEvaluators);
        mustBeVararg = psiMethod.isVarArgs();
      }

      myResult = new MethodEvaluator(objectEvaluator, contextClass, methodExpr.getReferenceName(),
                                     psiMethod != null ? JVMNameUtil.getJVMSignature(psiMethod) : null, argumentEvaluators,
                                     mustBeVararg);
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      final HighlightInfo parsingError = HighlightUtil.checkLiteralExpressionParsingError(expression, PsiUtil.getLanguageLevel(expression), null);
      if (parsingError != null) {
        throwEvaluateException(parsingError.getDescription());
        return;
      }

      final PsiType type = expression.getType();
      if (type == null) {
        throwEvaluateException(expression + ": null type");
        return;
      }

      myResult = new LiteralEvaluator(expression.getValue(), type.getCanonicalText());
    }

    @Override
    public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
      final PsiExpression indexExpression = expression.getIndexExpression();
      if(indexExpression == null) {
        throwExpressionInvalid(expression);
      }
      indexExpression.accept(this);
      final Evaluator indexEvaluator = handleUnaryNumericPromotion(indexExpression.getType(), myResult);

      expression.getArrayExpression().accept(this);
      Evaluator arrayEvaluator = myResult;
      myResult = new ArrayAccessEvaluator(arrayEvaluator, indexEvaluator);
    }


    /**
     * Handles unboxing and numeric promotion issues for
     * - array dimension expressions
     * - array index expression
     * - unary +, -, and ~ operations
     * @param operandExpressionType
     * @param operandEvaluator  @return operandEvaluator possibly 'wrapped' with necessary unboxing and type-casting evaluators to make returning value
     * suitable for mentioned contexts
     */
    private static Evaluator handleUnaryNumericPromotion(final PsiType operandExpressionType, Evaluator operandEvaluator) {
      final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(operandExpressionType);
      if (unboxedType != null && !PsiType.BOOLEAN.equals(unboxedType)) {
        operandEvaluator = new UnBoxingEvaluator(operandEvaluator);
      }

      // handle numeric promotion
      final PsiType _unboxedIndexType = unboxedType != null? unboxedType : operandExpressionType;
      if (_unboxedIndexType instanceof PsiPrimitiveType) {
        final PsiType promotionType = calcUnaryNumericPromotionType((PsiPrimitiveType)_unboxedIndexType);
        if (promotionType != null) {
          operandEvaluator = createTypeCastEvaluator(operandEvaluator, promotionType);
        }
      }
      return operandEvaluator;
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      PsiExpression operandExpr = expression.getOperand();
      if (operandExpr == null) {
        throwExpressionInvalid(expression);
      }
      operandExpr.accept(this);
      Evaluator operandEvaluator = myResult;
      PsiTypeElement castTypeElem = expression.getCastType();
      if (castTypeElem == null) {
        throwExpressionInvalid(expression);
      }
      PsiType castType = castTypeElem.getType();
      PsiType operandType = operandExpr.getType();

      // if operand type can not be resolved in current context - leave it for runtime checks
      if (operandType != null &&
          !TypeConversionUtil.areTypesConvertible(operandType, castType) &&
          PsiUtil.resolveClassInType(operandType) != null) {
        throw new EvaluateRuntimeException(
          new EvaluateException(
            JavaErrorBundle.message("inconvertible.type.cast", JavaHighlightUtil.formatType(operandType), JavaHighlightUtil
            .formatType(castType)))
        );
      }

      boolean shouldPerformBoxingConversion = operandType != null && TypeConversionUtil.boxingConversionApplicable(castType, operandType);
      final boolean castingToPrimitive = castType instanceof PsiPrimitiveType;
      if (shouldPerformBoxingConversion && castingToPrimitive) {
        operandEvaluator = new UnBoxingEvaluator(operandEvaluator);
      }

      final boolean performCastToWrapperClass = shouldPerformBoxingConversion && !castingToPrimitive;

      if (!(PsiUtil.resolveClassInClassTypeOnly(castType) instanceof PsiTypeParameter)) {
        if (performCastToWrapperClass) {
          castType = ObjectUtils.notNull(PsiPrimitiveType.getUnboxedType(castType), operandType);
        }

        myResult = createTypeCastEvaluator(operandEvaluator, castType);
      }

      if (performCastToWrapperClass) {
        myResult = new BoxingEvaluator(myResult);
      }
    }

    private static TypeCastEvaluator createTypeCastEvaluator(Evaluator operandEvaluator, PsiType castType) {
      if (castType instanceof PsiPrimitiveType) {
        return new TypeCastEvaluator(operandEvaluator, castType.getCanonicalText());
      }
      else {
        return new TypeCastEvaluator(operandEvaluator, new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(castType)));
      }
    }


    @Override
    public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
      PsiType type = expression.getOperand().getType();

      if (type instanceof PsiPrimitiveType) {
        final JVMName typeName = JVMNameUtil.getJVMRawText(((PsiPrimitiveType)type).getBoxedTypeName());
        myResult = new FieldEvaluator(new TypeEvaluator(typeName), FieldEvaluator.TargetClassFilter.ALL, "TYPE");
      }
      else {
        myResult = new ClassObjectEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)));
      }
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      throw new EvaluateRuntimeException(new UnsupportedExpressionException(
        JavaDebuggerBundle.message("evaluation.error.lambda.evaluation.not.supported")));
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      PsiElement qualifier = expression.getQualifier();
      PsiType interfaceType = expression.getFunctionalInterfaceType();
      if (!Registry.is("debugger.compiling.evaluator.method.refs") && interfaceType != null && qualifier != null) {
        String code = null;
        try {
          PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiMethod) {
            PsiMethod method = (PsiMethod)resolved;
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
              String find;
              boolean bind = false;
              if (method.isConstructor()) {
                find = "findConstructor(" + containingClass.getQualifiedName() + ".class, mt)";
              }
              else if (qualifier instanceof PsiSuperExpression) {
                find = "in(" + containingClass.getQualifiedName() + ".class).findSpecial(" +
                       containingClass.getQualifiedName() + ".class, \"" + method.getName() + "\", mt, " +
                       containingClass.getQualifiedName() + ".class)";
                bind = true;
              }
              else {
                find = containingClass.getQualifiedName() + ".class, \"" + method.getName() + "\", mt)";
                if (method.hasModifier(JvmModifier.STATIC)) {
                  find = "findStatic(" + find;
                }
                else {
                  find = "findVirtual(" + find;
                  if (qualifier instanceof PsiReference) {
                    PsiElement resolve = ((PsiReference)qualifier).resolve();
                    if (!(resolve instanceof PsiClass)) {
                      bind = true;
                    }
                  }
                  else {
                    bind = true;
                  }
                }
              }
              String bidStr = bind ? "mh = mh.bindTo(" + qualifier.getText() + ");\n" : "";
              code =
                "MethodType mt = MethodType.fromMethodDescriptorString(\"" + JVMNameUtil.getJVMSignature(method) + "\", null);\n" +
                "MethodHandle mh = MethodHandles.lookup()." + find + ";\n" +
                bidStr +
                "MethodHandleProxies.asInterfaceInstance(" + interfaceType.getCanonicalText() + ".class, mh);";
            }
          } else if (PsiUtil.isArrayClass(resolved)) {
            code =
              "MethodType mt = MethodType.methodType(Object.class, Class.class, int.class);\n" +
              "MethodHandle mh = MethodHandles.publicLookup().findStatic(Array.class, \"newInstance\", mt);\n" +
              "mh = mh.bindTo(" + StringUtil.substringBeforeLast(qualifier.getText(), "[]") + ".class)\n" +
              "MethodHandleProxies.asInterfaceInstance(" + interfaceType.getCanonicalText() + ".class, mh);";
          }
          if (code != null) {
            myResult = buildFromJavaCode(code,
                                         "java.lang.invoke.MethodHandle," +
                                         "java.lang.invoke.MethodHandleProxies," +
                                         "java.lang.invoke.MethodHandles," +
                                         "java.lang.invoke.MethodType," +
                                         "java.lang.reflect.Array",
                                         expression);
            return;
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      throw new EvaluateRuntimeException(
        new UnsupportedExpressionException(JavaDebuggerBundle.message("evaluation.error.method.reference.evaluation.not.supported")));
    }

    private Evaluator buildFromJavaCode(String code, String imports, @NotNull PsiElement context) {
      TextWithImportsImpl text = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, code, imports, JavaFileType.INSTANCE);
      JavaCodeFragment codeFragment = DefaultCodeFragmentFactory.getInstance().createCodeFragment(text, context, context.getProject());
      return accept(codeFragment);
    }

    @Override
    public void visitNewExpression(final PsiNewExpression expression) {
      PsiType expressionPsiType = expression.getType();
      if (expressionPsiType instanceof PsiArrayType) {
        Evaluator dimensionEvaluator = null;
        PsiExpression[] dimensions = expression.getArrayDimensions();
        if (dimensions.length == 1){
          PsiExpression dimensionExpression = dimensions[0];
          dimensionExpression.accept(this);
          if (myResult != null) {
            dimensionEvaluator = handleUnaryNumericPromotion(dimensionExpression.getType(), myResult);
          }
          else {
            throwEvaluateException(
              JavaDebuggerBundle.message("evaluation.error.invalid.array.dimension.expression", dimensionExpression.getText()));
          }
        }
        else if (dimensions.length > 1){
          throwEvaluateException(JavaDebuggerBundle.message("evaluation.error.multi.dimensional.arrays.creation.not.supported"));
        }

        Evaluator initializerEvaluator = null;
        PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
        if (arrayInitializer != null) {
          if (dimensionEvaluator != null) { // initializer already exists
            throwExpressionInvalid(expression);
          }
          arrayInitializer.accept(this);
          if (myResult != null) {
            initializerEvaluator = handleUnaryNumericPromotion(arrayInitializer.getType(), myResult);
          }
          else {
            throwExpressionInvalid(arrayInitializer);
          }
          /*
          PsiExpression[] initializers = arrayInitializer.getInitializers();
          initializerEvaluators = new Evaluator[initializers.length];
          for (int idx = 0; idx < initializers.length; idx++) {
            PsiExpression initializer = initializers[idx];
            initializer.accept(this);
            if (myResult instanceof Evaluator) {
              initializerEvaluators[idx] = myResult;
            }
            else {
              throw new EvaluateException("Invalid expression for array initializer: " + initializer.getText(), true);
            }
          }
          */
        }
        if (dimensionEvaluator == null && initializerEvaluator == null) {
          throwExpressionInvalid(expression);
        }
        myResult = new NewArrayInstanceEvaluator(
          new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
          dimensionEvaluator,
          initializerEvaluator
        );
      }
      else if (expressionPsiType instanceof PsiClassType){ // must be a class ref
        PsiClass aClass = CompilingEvaluatorTypesUtil.getClass((PsiClassType)expressionPsiType);
        if(aClass instanceof PsiAnonymousClass) {
          throw new EvaluateRuntimeException(new UnsupportedExpressionException(
            JavaDebuggerBundle.message("evaluation.error.anonymous.class.evaluation.not.supported")));
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList == null) {
          throwExpressionInvalid(expression);
        }
        final PsiExpression[] argExpressions = argumentList.getExpressions();
        final JavaResolveResult constructorResolveResult = expression.resolveMethodGenerics();
        PsiMethod constructor = CompilingEvaluatorTypesUtil.getReferencedConstructor((PsiMethod)constructorResolveResult.getElement());
        if (constructor == null && argExpressions.length > 0) {
          throw new EvaluateRuntimeException(new EvaluateException(
            JavaDebuggerBundle.message("evaluation.error.cannot.resolve.constructor", expression.getText()), null));
        }
        Evaluator[] argumentEvaluators = new Evaluator[argExpressions.length];
        // evaluate arguments
        for (int idx = 0; idx < argExpressions.length; idx++) {
          PsiExpression argExpression = argExpressions[idx];
          argExpression.accept(this);
          if (myResult != null) {
            argumentEvaluators[idx] = DisableGC.create(myResult);
          }
          else {
            throwExpressionInvalid(argExpression);
          }
        }

        if (constructor != null) {
          processBoxingConversions(constructor.getParameterList().getParameters(), argExpressions, constructorResolveResult.getSubstitutor(), argumentEvaluators);
        }

        if (aClass != null) {
          PsiClass containingClass = aClass.getContainingClass();
          if (containingClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
            PsiExpression qualifier = expression.getQualifier();
            if (qualifier != null) {
              qualifier.accept(this);
              if (myResult != null) {
                argumentEvaluators = ArrayUtil.prepend(myResult, argumentEvaluators);
              }
            }
            else {
              argumentEvaluators = ArrayUtil.prepend(
                new ThisEvaluator(createTraverser(containingClass, "this", false)),
                argumentEvaluators);
            }
          }
        }

        JVMName signature = JVMNameUtil.getJVMConstructorSignature(constructor, aClass);
        PsiType instanceType = CompilingEvaluatorTypesUtil.getClassType((PsiClassType)expressionPsiType);
        myResult = new NewClassInstanceEvaluator(
          new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(instanceType)),
          signature,
          argumentEvaluators
        );
      }
      else {
        if (expressionPsiType != null) {
          throwEvaluateException("Unsupported expression type: " + expressionPsiType.getPresentableText());
        }
        else {
          throwEvaluateException("Unknown type for expression: " + expression.getText());
        }
      }
    }

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      PsiExpression[] initializers = expression.getInitializers();
      Evaluator[] evaluators = new Evaluator[initializers.length];
      final PsiType type = expression.getType();
      boolean primitive = type instanceof PsiArrayType && ((PsiArrayType)type).getComponentType() instanceof PsiPrimitiveType;
      for (int idx = 0; idx < initializers.length; idx++) {
        PsiExpression initializer = initializers[idx];
        initializer.accept(this);
        if (myResult != null) {
          final Evaluator coerced =
            primitive ? handleUnaryNumericPromotion(initializer.getType(), myResult) : new BoxingEvaluator(myResult);
          evaluators[idx] = DisableGC.create(coerced);
        }
        else {
          throwExpressionInvalid(initializer);
        }
      }
      myResult = new ArrayInitializerEvaluator(evaluators);
      if (type != null && !(expression.getParent() instanceof PsiNewExpression)) {
        myResult = new NewArrayInstanceEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)),
                                                 null,
                                                 myResult);
      }
    }

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      PsiExpression condition = statement.getAssertCondition();
      if (condition == null) {
        throwEvaluateException("Assert condition expected in: " + statement.getText());
      }
      PsiExpression description = statement.getAssertDescription();
      String descriptionText = description != null ? description.getText() : "";
      myResult = new AssertStatementEvaluator(buildFromJavaCode("if (!(" + condition.getText() + ")) { " +
                                                                "throw new java.lang.AssertionError(" + descriptionText + ");}",
                                                                "", statement));
    }

    @Nullable
    private PsiClass getContainingClass(@NotNull PsiVariable variable) {
      PsiClass element = PsiTreeUtil.getParentOfType(variable.getParent(), PsiClass.class, false);
      return element == null ? myContextPsiClass : element;
    }

    @Nullable
    private PsiClass getPositionClass() {
      return myPositionPsiClass != null ? myPositionPsiClass : myContextPsiClass;
    }

    private ExpressionEvaluator buildElement(final PsiElement element) throws EvaluateException {
      LOG.assertTrue(element.isValid());

      setNewCodeFragmentEvaluator(); // in case element is not a code fragment
      myContextPsiClass = PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
      try {
        element.accept(this);
      }
      catch (EvaluateRuntimeException e) {
        throw e.getCause();
      }
      if (myResult == null) {
        throw EvaluateExceptionUtil
          .createEvaluateException(JavaDebuggerBundle.message("evaluation.error.invalid.expression", element.toString()));
      }
      return new ExpressionEvaluatorImpl(myResult);
    }
  }

  private static void processBoxingConversions(final PsiParameter[] declaredParams,
                                               final PsiExpression[] actualArgumentExpressions,
                                               final PsiSubstitutor methodResolveSubstitutor,
                                               final Evaluator[] argumentEvaluators) {
    if (declaredParams.length > 0) {
      final int paramCount = Math.max(declaredParams.length, actualArgumentExpressions.length);
      PsiType varargType = null;
      for (int idx = 0; idx < paramCount; idx++) {
        if (idx >= actualArgumentExpressions.length) {
          break; // actual arguments count is less than number of declared params
        }
        PsiType declaredParamType;
        if (idx < declaredParams.length) {
          declaredParamType = methodResolveSubstitutor.substitute(declaredParams[idx].getType());
          if (declaredParamType instanceof PsiEllipsisType) {
            declaredParamType = varargType = ((PsiEllipsisType)declaredParamType).getComponentType();
          }
        }
        else if (varargType != null) {
          declaredParamType = varargType;
        }
        else {
          break;
        }
        final PsiType actualArgType = actualArgumentExpressions[idx].getType();
        if (TypeConversionUtil.boxingConversionApplicable(declaredParamType, actualArgType) ||
            (declaredParamType != null && actualArgType == null)) {
          final Evaluator argEval = argumentEvaluators[idx];
          argumentEvaluators[idx] = declaredParamType instanceof PsiPrimitiveType ? new UnBoxingEvaluator(argEval) : new BoxingEvaluator(argEval);
        }
      }
    }
  }

  /**
   * Contains a set of methods to ensure access to extracted generated class in compiling evaluator
   */
  private static class CompilingEvaluatorTypesUtil {
    @NotNull
    private static PsiType getVariableType(@NotNull PsiVariable variable) {
      PsiType type = variable.getType();
      PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
      if (psiClass != null) {
        PsiType typeToUse = psiClass.getUserData(LightMethodObjectExtractedData.REFERENCED_TYPE);
        if (typeToUse != null) {
          type = typeToUse;
        }
      }

      return type;
    }

    @Nullable
    private static PsiMethod getReferencedMethod(@NotNull JavaResolveResult resolveResult) {
      PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();
      PsiMethod methodToUseInstead = psiMethod == null ? null : psiMethod.getUserData(LightMethodObjectExtractedData.REFERENCE_METHOD);
      if (methodToUseInstead != null) {
        psiMethod = methodToUseInstead;
      }

      return psiMethod;
    }

    @Nullable
    private static PsiClass getClass(@NotNull PsiClassType classType) {
      PsiClass aClass = classType.resolve();
      PsiType type = aClass == null ? null : aClass.getUserData(LightMethodObjectExtractedData.REFERENCED_TYPE);
      if (type != null) {
        return PsiTypesUtil.getPsiClass(type);
      }

      return aClass;
    }

    @Nullable
    @Contract("null -> null")
    private static PsiMethod getReferencedConstructor(@Nullable PsiMethod originalConstructor) {
      if (originalConstructor == null) return null;
      PsiMethod methodToUseInstead = originalConstructor.getUserData(LightMethodObjectExtractedData.REFERENCE_METHOD);
      return methodToUseInstead == null ? originalConstructor : methodToUseInstead;
    }

    @NotNull
    private static PsiType getClassType(@NotNull PsiClassType expressionPsiType) {
      PsiClass aClass = expressionPsiType.resolve();
      PsiType type = aClass == null ? null : aClass.getUserData(LightMethodObjectExtractedData.REFERENCED_TYPE);
      return type != null ? type : expressionPsiType;
    }
  }
}
