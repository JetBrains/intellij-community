/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Class EvaluatorBuilderImpl
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EvaluatorBuilderImpl implements EvaluatorBuilder {
  private static final EvaluatorBuilderImpl ourInstance = new EvaluatorBuilderImpl();

  private EvaluatorBuilderImpl() {
  }

  public static EvaluatorBuilder getInstance() {
    return ourInstance;
  }

  public static ExpressionEvaluator build(final TextWithImports text,
                                          @Nullable PsiElement contextElement,
                                          @Nullable final SourcePosition position,
                                          @NotNull Project project) throws EvaluateException {
    CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, contextElement);
    PsiCodeFragment codeFragment = factory.createCodeFragment(text, contextElement, project);
    if (codeFragment == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", text.getText()));
    }
    codeFragment.forceResolveScope(GlobalSearchScope.allScope(project));
    DebuggerUtils.checkSyntax(codeFragment);

    return factory.getEvaluatorBuilder().build(codeFragment, position);
  }

  @Override
  public ExpressionEvaluator build(final PsiElement codeFragment, final SourcePosition position) throws EvaluateException {
    return new Builder(position).buildElement(codeFragment);
  }

  private static class Builder extends JavaElementVisitor {
    private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl");
    private Evaluator myResult = null;
    private PsiClass myContextPsiClass;
    private CodeFragmentEvaluator myCurrentFragmentEvaluator;
    private final Set<JavaCodeFragment> myVisitedFragments = new HashSet<>();
    @Nullable
    private final SourcePosition myPosition;

    private Builder(@Nullable SourcePosition position) {
      myPosition = position;
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

        myCurrentFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[evaluators.size()]));
        myResult = myCurrentFragmentEvaluator;
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
    }

    @Override
    public void visitErrorElement(PsiErrorElement element) {
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
        throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", lExpression.getText()));
      }

      IElementType assignmentType = expression.getOperationTokenType();
      PsiType rType = rExpression.getType();
      if(!TypeConversionUtil.areTypesAssignmentCompatible(lType, rExpression) && rType != null) {
        throwEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", expression.getOperationSign().getText()));
      }
      lExpression.accept(this);
      Evaluator lEvaluator = myResult;

      rEvaluator = handleAssignmentBoxingAndPrimitiveTypeConversions(lType, rType, rEvaluator);

      if (assignmentType != JavaTokenType.EQ) {
        IElementType opType = TypeConversionUtil.convertEQtoOperation(assignmentType);
        final PsiType typeForBinOp = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opType, true);
        if (typeForBinOp == null || rType == null) {
          throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
        }
        rEvaluator = createBinaryEvaluator(lEvaluator, lType, rEvaluator, rType, opType, typeForBinOp);
      }
      myResult = new AssignmentEvaluator(lEvaluator, rEvaluator);
    }

    // returns rEvaluator possibly wrapped with boxing/unboxing and casting evaluators
    private static Evaluator handleAssignmentBoxingAndPrimitiveTypeConversions(PsiType lType, PsiType rType, Evaluator rEvaluator) {
      final PsiType unboxedLType = PsiPrimitiveType.getUnboxedType(lType);

      if (unboxedLType != null) {
        if (rType instanceof PsiPrimitiveType && !PsiType.NULL.equals(rType)) {
          if (!rType.equals(unboxedLType)) {
            rEvaluator = new TypeCastEvaluator(rEvaluator, unboxedLType.getCanonicalText(), true);
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
              rEvaluator = new TypeCastEvaluator(rEvaluator, lType.getCanonicalText(), true);
            }
          }
        }
      }
      return rEvaluator;
    }

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      throw new EvaluateRuntimeException(new UnsupportedExpressionException(statement.getText()));
    }

    @Override
    public void visitStatement(PsiStatement statement) {
      throwEvaluateException(DebuggerBundle.message("evaluation.error.statement.not.supported", statement.getText()));
    }

    private CodeFragmentEvaluator setNewCodeFragmentEvaluator() {
      CodeFragmentEvaluator old = myCurrentFragmentEvaluator;
      myCurrentFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
      return old;
    }

    @Override
    public void visitBlockStatement(PsiBlockStatement statement) {
      CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
      try {
        PsiStatement[] statements = statement.getCodeBlock().getStatements();
        Evaluator[] evaluators = new Evaluator[statements.length];
        for (int i = 0; i < statements.length; i++) {
          PsiStatement psiStatement = statements[i];
          psiStatement.accept(this);
          evaluators[i] = new DisableGC(myResult);
          myResult = null;
        }
        myResult = new BlockStatementEvaluator(evaluators);
      }
      finally {
        myCurrentFragmentEvaluator = oldFragmentEvaluator;
      }
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
      Evaluator bodyEvaluator = accept(statement.getBody());
      Evaluator conditionEvaluator = accept(statement.getCondition());
      if (conditionEvaluator != null) {
        myResult = new WhileStatementEvaluator(new UnBoxingEvaluator(conditionEvaluator), bodyEvaluator, getLabel(statement));
      }
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      Evaluator initializerEvaluator = accept(statement.getInitialization());
      Evaluator conditionEvaluator = accept(statement.getCondition());
      if (conditionEvaluator != null) {
        conditionEvaluator = new UnBoxingEvaluator(conditionEvaluator);
      }
      Evaluator updateEvaluator = accept(statement.getUpdate());
      Evaluator bodyEvaluator = accept(statement.getBody());
      if (bodyEvaluator != null) {
        myResult = new ForStatementEvaluator(initializerEvaluator, conditionEvaluator, updateEvaluator, bodyEvaluator, getLabel(statement));
      }
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      try {
        String iterationParameterName = statement.getIterationParameter().getName();
        myCurrentFragmentEvaluator.setInitialValue(iterationParameterName, null);
        SyntheticVariableEvaluator iterationParameterEvaluator = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, iterationParameterName);

        Evaluator iteratedValueEvaluator = accept(statement.getIteratedValue());
        Evaluator bodyEvaluator = accept(statement.getBody());
        if (bodyEvaluator != null) {
          myResult = new ForeachStatementEvaluator(iterationParameterEvaluator, iteratedValueEvaluator, bodyEvaluator, getLabel(statement));
        }
      }
      catch (EvaluateException e) {
        throw new EvaluateRuntimeException(e);
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
          throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
        }
        final PsiType typeForBinOp = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opType, true);
        if (typeForBinOp == null) {
          throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", wideExpression.getText()));
        }
        myResult = createBinaryEvaluator(result, lType, rResult, rType, opType, typeForBinOp);
        lType = typeForBinOp;
        result = myResult;
      }
    }

    // constructs binary evaluator handling unboxing and numeric promotion issues
    private static BinaryExpressionEvaluator createBinaryEvaluator(Evaluator lResult,
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
            rResult = new TypeCastEvaluator(rResult, PsiType.DOUBLE.getCanonicalText(), true);
          }
        }
        else if (PsiType.DOUBLE.equals(_rType)) {
          if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.DOUBLE)) {
            lResult = new TypeCastEvaluator(lResult, PsiType.DOUBLE.getCanonicalText(), true);
          }
        }
        else if (PsiType.FLOAT.equals(_lType)) {
          if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.FLOAT)) {
            rResult = new TypeCastEvaluator(rResult, PsiType.FLOAT.getCanonicalText(), true);
          }
        }
        else if (PsiType.FLOAT.equals(_rType)) {
          if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.FLOAT)) {
            lResult = new TypeCastEvaluator(lResult, PsiType.FLOAT.getCanonicalText(), true);
          }
        }
        else if (PsiType.LONG.equals(_lType)) {
          if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.LONG)) {
            rResult = new TypeCastEvaluator(rResult, PsiType.LONG.getCanonicalText(), true);
          }
        }
        else if (PsiType.LONG.equals(_rType)) {
          if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.LONG)) {
            lResult = new TypeCastEvaluator(lResult, PsiType.LONG.getCanonicalText(), true);
          }
        }
        else {
          if (!PsiType.INT.equals(_lType) && TypeConversionUtil.areTypesConvertible(_lType, PsiType.INT)) {
            lResult = new TypeCastEvaluator(lResult, PsiType.INT.getCanonicalText(), true);
          }
          if (!PsiType.INT.equals(_rType) && TypeConversionUtil.areTypesConvertible(_rType, PsiType.INT)) {
            rResult = new TypeCastEvaluator(rResult, PsiType.INT.getCanonicalText(), true);
          }
        }
      }

      return new BinaryExpressionEvaluator(lResult, rResult, operation, expressionExpectedType.getCanonicalText());
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

            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(localVariable.getProject()).getElementFactory();
            try {
              PsiExpression initialValue = elementFactory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(lType), null);
              Object value = JavaConstantExpressionEvaluator.computeConstantExpression(initialValue, true);
              myCurrentFragmentEvaluator.setInitialValue(localVariable.getName(), value);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
            catch (EvaluateException e) {
              throw new EvaluateRuntimeException(e);
            }

            PsiExpression initializer = localVariable.getInitializer();
            if (initializer != null) {
              try {
                if (!TypeConversionUtil.areTypesAssignmentCompatible(lType, initializer)) {
                  throwEvaluateException(
                    DebuggerBundle.message("evaluation.error.incompatible.variable.initializer.type", localVariable.getName()));
                }
                final PsiType rType = initializer.getType();
                initializer.accept(this);
                Evaluator rEvaluator = myResult;

                PsiExpression localVarReference = elementFactory.createExpressionFromText(localVariable.getName(), initializer);

                localVarReference.accept(this);
                Evaluator lEvaluator = myResult;
                rEvaluator = handleAssignmentBoxingAndPrimitiveTypeConversions(localVarReference.getType(), rType, rEvaluator);

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
              DebuggerBundle.message("evaluation.error.local.variable.declarations.not.supported"), null));
          }
        }
        else {
          throw new EvaluateRuntimeException(new EvaluateException(
            DebuggerBundle.message("evaluation.error.unsupported.declaration", declaredElement.getText()), null));
        }
      }

      if(!evaluators.isEmpty()) {
        CodeFragmentEvaluator codeFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
        codeFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[evaluators.size()]));
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
        //synthetic variable
        final PsiFile containingFile = element.getContainingFile();
        if(containingFile instanceof PsiCodeFragment && myCurrentFragmentEvaluator != null && myVisitedFragments.contains(containingFile)) {
          // psiVariable may live in PsiCodeFragment not only in debugger editors, for example Fabrique has such variables.
          // So treat it as synthetic var only when this code fragment is located in DebuggerEditor,
          // that's why we need to check that containing code fragment is the one we visited
          myResult = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, ((PsiVariable)element).getName());
          return;
        }
        // local variable
        final PsiVariable psiVar = (PsiVariable)element;
        final String localName = psiVar.getName();
        PsiClass variableClass = getContainingClass(psiVar);
        if (getContextPsiClass() == null || getContextPsiClass().equals(variableClass)) {
          PsiElement method = DebuggerUtilsEx.getContainingMethod(expression);
          boolean canScanFrames = method instanceof PsiLambdaExpression || ContextUtil.isJspImplicit(element);
          myResult = new LocalVariableEvaluator(localName, canScanFrames);
          return;
        }
        // the expression references final var outside the context's class (in some of the outer classes)
        int iterationCount = 0;
        PsiClass aClass = getOuterClass(getContextPsiClass());
        while (aClass != null && !aClass.equals(variableClass)) {
          iterationCount++;
          aClass = getOuterClass(aClass);
        }
        if (aClass != null) {
          PsiExpression initializer = psiVar.getInitializer();
          if(initializer != null) {
            Object value = JavaPsiFacade.getInstance(psiVar.getProject()).getConstantEvaluationHelper().computeConstantExpression(initializer);
            if(value != null) {
              PsiType type = resolveResult.getSubstitutor().substitute(psiVar.getType());
              myResult = new LiteralEvaluator(value, type.getCanonicalText());
              return;
            }
          }
          Evaluator objectEvaluator = new ThisEvaluator(iterationCount);
          //noinspection HardCodedStringLiteral
          final PsiClass classAt = myPosition != null? JVMNameUtil.getClassAt(myPosition) : null;
          FieldEvaluator.TargetClassFilter filter = FieldEvaluator.createClassFilter(classAt != null? classAt : getContextPsiClass());
          myResult = createFallbackEvaluator(new FieldEvaluator(objectEvaluator, filter, "val$" + localName),
                                             new LocalVariableEvaluator(localName, true));
          return;
        }
        throwEvaluateException(DebuggerBundle.message("evaluation.error.local.variable.missing.from.class.closure", localName));
      }
      else if (element instanceof PsiField) {
        final PsiField psiField = (PsiField)element;
        final PsiClass fieldClass = psiField.getContainingClass();
        if(fieldClass == null) {
          throwEvaluateException(DebuggerBundle.message("evaluation.error.cannot.resolve.field.class", psiField.getName())); return;
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
        else if (fieldClass.equals(getContextPsiClass()) ||
                 (getContextPsiClass() != null && getContextPsiClass().isInheritor(fieldClass, true))) {
            objectEvaluator = new ThisEvaluator();
        }
        else {  // myContextPsiClass != fieldClass && myContextPsiClass is not a subclass of fieldClass
          int iterationCount = 0;
          PsiClass aClass = getContextPsiClass();
          while (aClass != null && !(aClass.equals(fieldClass) || aClass.isInheritor(fieldClass, true))) {
            iterationCount++;
            aClass = getOuterClass(aClass);
          }
          if (aClass == null) {
            throwEvaluateException(DebuggerBundle.message("evaluation.error.cannot.sources.for.field.class", psiField.getName()));
          }
          objectEvaluator = new ThisEvaluator(iterationCount);
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
          throwEvaluateException(DebuggerBundle.message("evaluation.error.identifier.expected", elementDisplayString));
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
              throwEvaluateException(DebuggerBundle.message("evaluation.error.cannot.evaluate.qualifier", qualifier.getText()));
            }

            myResult = new FieldEvaluator(myResult, FieldEvaluator.createClassFilter(qualifier.getType()), name);
          }
        }
        else {
          myResult = new LocalVariableEvaluator(name, false);
        }
      }
    }

    private static Evaluator createFallbackEvaluator(final Evaluator primary, final Evaluator fallback) {
      return new Evaluator() {
        @Override
        public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
          try {
            return primary.evaluate(context);
          }
          catch (EvaluateException e) {
            try {
              return fallback.evaluate(context);
            }
            catch (EvaluateException e1) {
              throw e;
            }
          }
        }

        @Override
        public Modifier getModifier() {
          return primary.getModifier();
        }
      };
    }

    private static void throwExpressionInvalid(PsiElement expression) {
      throwEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", expression.getText()));
    }

    private static void throwEvaluateException(String message) throws EvaluateRuntimeException {
      throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(message));
    }

    @Override
    public void visitSuperExpression(PsiSuperExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitSuperExpression " + expression);
      }
      final int iterationCount = calcIterationCount(expression.getQualifier());
      myResult = new SuperEvaluator(iterationCount);
    }

    @Override
    public void visitThisExpression(PsiThisExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitThisExpression " + expression);
      }
      final int iterationCount = calcIterationCount(expression.getQualifier());
      myResult = new ThisEvaluator(iterationCount);
    }

    private int calcIterationCount(final PsiJavaCodeReferenceElement qualifier) {
      if (qualifier != null) {
        return calcIterationCount(qualifier.resolve(), qualifier.getText());
      }
      return 0;
    }

    private int calcIterationCount(PsiElement targetClass, String name) {
      int iterationCount = 0;
      if (targetClass == null || getContextPsiClass() == null) {
        throwEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", name));
      }
      try {
        PsiClass aClass = getContextPsiClass();
        while (aClass != null && !aClass.equals(targetClass)) {
          iterationCount++;
          aClass = getOuterClass(aClass);
        }
      }
      catch (Exception e) {
        //noinspection ThrowableResultOfMethodCallIgnored
        throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(e));
      }
      return iterationCount;
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("visitInstanceOfExpression " + expression);
      }
      PsiTypeElement checkType = expression.getCheckType();
      if(checkType == null) {
        throwExpressionInvalid(expression);
      }
      PsiType type = checkType.getType();
      expression.getOperand().accept(this);
//    ClassObjectEvaluator typeEvaluator = new ClassObjectEvaluator(type.getCanonicalText());
      Evaluator operandEvaluator = myResult;
      myResult = new InstanceofEvaluator(operandEvaluator, new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)));
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
        throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
      }

      final PsiExpression operandExpression = expression.getOperand();
      operandExpression.accept(this);

      final Evaluator operandEvaluator = myResult;

      final IElementType operation = expression.getOperationTokenType();
      final PsiType operandType = operandExpression.getType();
      @Nullable final PsiType unboxedOperandType = PsiPrimitiveType.getUnboxedType(operandType);

      Evaluator incrementImpl = createBinaryEvaluator(
        operandEvaluator, operandType,
        new LiteralEvaluator(Integer.valueOf(1), "int"), PsiType.INT,
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
        throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.type", expression.getText()));
      }

      final PsiExpression operandExpression = expression.getOperand();
      if (operandExpression == null) {
        throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.expression.operand", expression.getText()));
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
          final BinaryExpressionEvaluator rightEval = createBinaryEvaluator(
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
        argumentEvaluators[idx] = new DisableGC(myResult);
      }
      PsiReferenceExpression methodExpr = expression.getMethodExpression();

      final JavaResolveResult resolveResult = methodExpr.advancedResolve(false);
      final PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();

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
          int iterationCount = 0;
          final PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
          if (currentFileResolveScope instanceof PsiClass) {
            PsiClass aClass = getContextPsiClass();
            while(aClass != null && !aClass.equals(currentFileResolveScope)) {
              aClass = getOuterClass(aClass);
              iterationCount++;
            }
          }
          objectEvaluator = new ThisEvaluator(iterationCount);
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
          contextClass = JVMNameUtil.getContextClassJVMQualifiedName(myPosition);
          if(contextClass == null && myContextPsiClass != null) {
            contextClass = JVMNameUtil.getJVMQualifiedName(myContextPsiClass);
          }
          //else {
          //  throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
          //    DebuggerBundle.message("evaluation.error.method.not.found", methodExpr.getReferenceName()))
          //  );
          //}
        }
      }

      if (objectEvaluator == null) {
        throwExpressionInvalid(expression);
      }

      if (psiMethod != null && !psiMethod.isConstructor()) {
        if (psiMethod.getReturnType() == null) {
          throwEvaluateException(DebuggerBundle.message("evaluation.error.unknown.method.return.type", psiMethod.getText()));
        }
      }

      boolean defaultInterfaceMethod = false;
      boolean mustBeVararg = false;

      if (psiMethod != null) {
        processBoxingConversions(psiMethod.getParameterList().getParameters(), argExpressions, resolveResult.getSubstitutor(), argumentEvaluators);
        argumentEvaluators = wrapVarargs(psiMethod.getParameterList().getParameters(), argExpressions, resolveResult.getSubstitutor(), argumentEvaluators);
        defaultInterfaceMethod = psiMethod.hasModifierProperty(PsiModifier.DEFAULT);
        mustBeVararg = psiMethod.isVarArgs();
      }

      myResult = new MethodEvaluator(objectEvaluator, contextClass, methodExpr.getReferenceName(),
                                     psiMethod != null ? JVMNameUtil.getJVMSignature(psiMethod) : null, argumentEvaluators,
                                     defaultInterfaceMethod, mustBeVararg);
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      final HighlightInfo parsingError = HighlightUtil.checkLiteralExpressionParsingError(expression, null, null);
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
          operandEvaluator = new TypeCastEvaluator(operandEvaluator, promotionType.getCanonicalText(), true);
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
          new EvaluateException(JavaErrorMessages.message("inconvertible.type.cast", JavaHighlightUtil.formatType(operandType), JavaHighlightUtil
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
        String castTypeName = castType.getCanonicalText();
        if (performCastToWrapperClass) {
          final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(castType);
          if (unboxedType != null) {
            castTypeName = unboxedType.getCanonicalText();
          }
        }

        myResult = new TypeCastEvaluator(operandEvaluator, castTypeName, castingToPrimitive);
      }

      if (performCastToWrapperClass) {
        myResult = new BoxingEvaluator(myResult);
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
      throw new EvaluateRuntimeException(new UnsupportedExpressionException(DebuggerBundle.message("evaluation.error.lambda.evaluation.not.supported")));
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      throw new EvaluateRuntimeException(new UnsupportedExpressionException(DebuggerBundle.message("evaluation.error.method.reference.evaluation.not.supported")));
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
              DebuggerBundle.message("evaluation.error.invalid.array.dimension.expression", dimensionExpression.getText()));
          }
        }
        else if (dimensions.length > 1){
          throwEvaluateException(DebuggerBundle.message("evaluation.error.multi.dimensional.arrays.creation.not.supported"));
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
        PsiClass aClass = ((PsiClassType)expressionPsiType).resolve();
        if(aClass instanceof PsiAnonymousClass) {
          throw new EvaluateRuntimeException(new UnsupportedExpressionException(DebuggerBundle.message("evaluation.error.anonymous.class.evaluation.not.supported")));
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList == null) {
          throwExpressionInvalid(expression);
        }
        final PsiExpression[] argExpressions = argumentList.getExpressions();
        final JavaResolveResult constructorResolveResult = expression.resolveMethodGenerics();
        final PsiMethod constructor = (PsiMethod)constructorResolveResult.getElement();
        if (constructor == null && argExpressions.length > 0) {
          throw new EvaluateRuntimeException(new EvaluateException(
            DebuggerBundle.message("evaluation.error.cannot.resolve.constructor", expression.getText()), null));
        }
        Evaluator[] argumentEvaluators = new Evaluator[argExpressions.length];
        // evaluate arguments
        for (int idx = 0; idx < argExpressions.length; idx++) {
          PsiExpression argExpression = argExpressions[idx];
          argExpression.accept(this);
          if (myResult != null) {
            argumentEvaluators[idx] = new DisableGC(myResult);
          }
          else {
            throwExpressionInvalid(argExpression);
          }
        }

        if (constructor != null) {
          processBoxingConversions(constructor.getParameterList().getParameters(), argExpressions, constructorResolveResult.getSubstitutor(), argumentEvaluators);
          argumentEvaluators = wrapVarargs(constructor.getParameterList().getParameters(), argExpressions, constructorResolveResult.getSubstitutor(), argumentEvaluators);
        }

        if (aClass != null && aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
          argumentEvaluators = addThisEvaluator(argumentEvaluators, aClass.getContainingClass());
        }

        JVMName signature = JVMNameUtil.getJVMConstructorSignature(constructor, aClass);
        myResult = new NewClassInstanceEvaluator(
          new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
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

    private Evaluator[] addThisEvaluator(Evaluator[] argumentEvaluators, PsiClass cls) {
      Evaluator[] res = new Evaluator[argumentEvaluators.length+1];
      int depth = calcIterationCount(cls, "this");
      res[0] = new ThisEvaluator(depth);
      System.arraycopy(argumentEvaluators, 0, res, 1, argumentEvaluators.length);
      return res;
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
          evaluators[idx] = new DisableGC(coerced);
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

    @Nullable
    private static PsiClass getOuterClass(PsiClass aClass) {
      return aClass == null ? null : PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
    }

    private PsiClass getContainingClass(PsiVariable variable) {
      PsiElement element = PsiTreeUtil.getParentOfType(variable.getParent(), PsiClass.class, false);
      return element == null ? getContextPsiClass() : (PsiClass)element;
    }

    @Nullable
    public PsiClass getContextPsiClass() {
      return myContextPsiClass;
    }

    protected ExpressionEvaluator buildElement(final PsiElement element) throws EvaluateException {
      LOG.assertTrue(element.isValid());

      myContextPsiClass = PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
      try {
        element.accept(this);
      }
      catch (EvaluateRuntimeException e) {
        throw e.getCause();
      }
      if (myResult == null) {
        throw EvaluateExceptionUtil
          .createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", element.toString()));
      }
      return new ExpressionEvaluatorImpl(myResult);
    }
  }

  private static Evaluator[] wrapVarargs(final PsiParameter[] declaredParams,
                                         final PsiExpression[] actualArgumentExpressions,
                                         final PsiSubstitutor methodResolveSubstitutor,
                                         final Evaluator[] argumentEvaluators) {
    int lastParam = declaredParams.length - 1;
    if (lastParam >= 0 && declaredParams[lastParam].isVarArgs() && argumentEvaluators.length > lastParam) {
      // only wrap if the first varargs parameter is null for now
      if (!TypeConversionUtil.isNullType(actualArgumentExpressions[lastParam].getType())) {
        return argumentEvaluators;
      }
      // do not wrap arrays twice
      if (argumentEvaluators.length - lastParam == 1 && actualArgumentExpressions[lastParam].getType() instanceof PsiArrayType) {
        return argumentEvaluators;
      }
      PsiEllipsisType declaredParamType = (PsiEllipsisType)methodResolveSubstitutor.substitute(declaredParams[lastParam].getType());
      ArrayInitializerEvaluator varargArrayEvaluator =
        new ArrayInitializerEvaluator(Arrays.copyOfRange(argumentEvaluators, lastParam, argumentEvaluators.length));
      NewArrayInstanceEvaluator evaluator =
        new NewArrayInstanceEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(declaredParamType.toArrayType())), null,
                                      varargArrayEvaluator);
      Evaluator[] res = new Evaluator[declaredParams.length];
      System.arraycopy(argumentEvaluators, 0, res, 0, lastParam);
      res[lastParam] = new DisableGC(evaluator);
      return res;
    }
    return argumentEvaluators;
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
        if (TypeConversionUtil.boxingConversionApplicable(declaredParamType, actualArgType)) {
          final Evaluator argEval = argumentEvaluators[idx];
          argumentEvaluators[idx] = declaredParamType instanceof PsiPrimitiveType ? new UnBoxingEvaluator(argEval) : new BoxingEvaluator(argEval);
        }
      }
    }
  }
}
