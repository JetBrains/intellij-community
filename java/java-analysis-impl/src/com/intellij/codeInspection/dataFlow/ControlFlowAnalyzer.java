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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.dataFlow.inliner.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.siyeh.ig.numeric.UnnecessaryExplicitNumericCastInspection;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;

public class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();
  private final PsiElement myCodeFragment;
  private final boolean myIgnoreAssertions;
  private final boolean myInlining;
  private final Project myProject;

  private static class CannotAnalyzeException extends RuntimeException { }

  private final DfaValueFactory myFactory;
  private ControlFlow myCurrentFlow;
  private FList<Trap> myTrapStack = FList.emptyList();
  private final ExceptionTransfer myRuntimeException;
  private final ExceptionTransfer myError;
  private final PsiType myAssertionError;
  private InlinedBlockContext myInlinedBlockContext;
  private final boolean myThisReadOnly;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory, @NotNull PsiElement codeFragment, boolean ignoreAssertions, boolean inlining) {
    myInlining = inlining;
    myFactory = valueFactory;
    myCodeFragment = codeFragment;
    myProject = codeFragment.getProject();
    myIgnoreAssertions = ignoreAssertions;
    GlobalSearchScope scope = codeFragment.getResolveScope();
    myRuntimeException = new ExceptionTransfer(myFactory.createDfaType(createClassType(scope, JAVA_LANG_RUNTIME_EXCEPTION)));
    myError = new ExceptionTransfer(myFactory.createDfaType(createClassType(scope, JAVA_LANG_ERROR)));
    myAssertionError = createClassType(scope, JAVA_LANG_ASSERTION_ERROR);
    PsiElement member = PsiTreeUtil.getParentOfType(codeFragment, PsiMember.class, PsiLambdaExpression.class);
    myThisReadOnly = member instanceof PsiMethod && MutationSignature.fromMethod((PsiMethod)member).preservesThis();
  }

  private void buildClassInitializerFlow(PsiClass psiClass, boolean isStatic) {
    pushUnknown();
    ConditionalGotoInstruction conditionalGoto = new ConditionalGotoInstruction(null, false, null);
    addInstruction(conditionalGoto);
    for (PsiElement element : psiClass.getChildren()) {
      if ((element instanceof PsiField || element instanceof PsiClassInitializer) &&
          ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        element.accept(this);
      }
    }
    addInstruction(new FlushVariableInstruction(null));
    conditionalGoto.setOffset(getInstructionCount());
  }

  @Nullable
  public ControlFlow buildControlFlow() {
    myCurrentFlow = new ControlFlow(myFactory);
    try {
      if(myCodeFragment instanceof PsiClass) {
        // if(unknown) { staticInitializer(); } if(unknown) { instanceInitializer(); }
        buildClassInitializerFlow((PsiClass)myCodeFragment, true);
        buildClassInitializerFlow((PsiClass)myCodeFragment, false);
      } else {
        myCodeFragment.accept(this);
      }
    }
    catch (CannotAnalyzeException e) {
      return null;
    }

    PsiElement parent = myCodeFragment.getParent();
    if (parent instanceof PsiLambdaExpression && myCodeFragment instanceof PsiExpression) {
      generateBoxingUnboxingInstructionFor((PsiExpression)myCodeFragment,
                                           LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent));
      addInstruction(new CheckReturnValueInstruction((PsiExpression)myCodeFragment));
    }

    addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, FList.emptyList()), null));

    if (Registry.is("idea.dfa.live.variables.analysis")) {
      new LiveVariablesAnalyzer(myCurrentFlow, myFactory).flushDeadVariablesOnStatementFinish();
    }

    return myCurrentFlow;
  }

  DfaValueFactory getFactory() {
    return myFactory;
  }

  PsiElement getContext() {
    return myCodeFragment;
  }

  private PsiClassType createClassType(GlobalSearchScope scope, String fqn) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(fqn, scope);
    if (aClass != null) return JavaPsiFacade.getElementFactory(myProject).createType(aClass);
    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(fqn, scope);
  }

  <T extends Instruction> T addInstruction(T i) {
    myCurrentFlow.addInstruction(i);
    return i;
  }

  int getInstructionCount() {
    return myCurrentFlow.getInstructionCount();
  }

  private ControlFlow.ControlFlowOffset getEndOffset(PsiElement element) {
    return myCurrentFlow.getEndOffset(element);
  }

  private ControlFlow.ControlFlowOffset getStartOffset(PsiElement element) {
    return myCurrentFlow.getStartOffset(element);
  }

  private void startElement(PsiElement element) {
    myCurrentFlow.startElement(element);
  }

  private void finishElement(PsiElement element) {
    myCurrentFlow.finishElement(element);
    if (element instanceof PsiStatement && !(element instanceof PsiReturnStatement)) {
      addInstruction(new FinishElementInstruction(element));
    }
  }

  @Override
  public void visitErrorElement(PsiErrorElement element) {
    throw new CannotAnalyzeException();
  }

  @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    PsiExpression lExpr = expression.getLExpression();
    PsiExpression rExpr = expression.getRExpression();

    startElement(expression);
    if (rExpr == null) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    IElementType op = expression.getOperationTokenType();
    PsiType type = expression.getType();
    boolean isBoolean = PsiType.BOOLEAN.equals(type);
    if (op == JavaTokenType.EQ) {
      lExpr.accept(this);
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, type);
    }
    else if (op == JavaTokenType.ANDEQ && isBoolean) {
      generateBooleanAssignmentExpression(true, lExpr, rExpr, type);
    }
    else if (op == JavaTokenType.OREQ && isBoolean) {
      generateBooleanAssignmentExpression(false, lExpr, rExpr, type);
    }
    else if (op == JavaTokenType.XOREQ && isBoolean) {
      generateXorExpression(expression, new PsiExpression[]{lExpr, rExpr}, type, true);
    }
    else if (op == JavaTokenType.PLUSEQ && type != null && type.equalsToText(JAVA_LANG_STRING)) {
      lExpr.accept(this);
      addInstruction(new DupInstruction());
      rExpr.accept(this);
      addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, myProject));
    }
    else if (isAssignmentDivision(op) && type != null && PsiType.LONG.isAssignableFrom(type)) {
      lExpr.accept(this);
      generateBoxingUnboxingInstructionFor(lExpr, type);
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, type);
      checkZeroDivisor();
      addInstruction(new PopInstruction());
      pushUnknown();
    }
    else {
      generateDefaultAssignmentBinOp(lExpr, rExpr, type);
    }

    addInstruction(new AssignInstruction(rExpr, myFactory.createValue(lExpr)));

    finishElement(expression);
  }

  private void generateDefaultAssignmentBinOp(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    addInstruction(new DupInstruction());
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr, exprType);
    addInstruction(new BinopInstruction(null, null, myProject));
  }

  @Override public void visitAssertStatement(PsiAssertStatement statement) {
    if (myIgnoreAssertions) {
      return;
    }

    startElement(statement);
    final PsiExpression condition = statement.getAssertCondition();
    final PsiExpression description = statement.getAssertDescription();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), false, condition));
      if (description != null) {
        description.accept(this);
      }

      throwException(myAssertionError, statement);
    }
    finishElement(statement);
  }

  @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    startElement(statement);

    PsiElement[] elements = statement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        addInstruction(new EmptyInstruction(element));
      }
      else if (element instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable)element;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          initializeVariable(variable, initializer);
        }
      }
    }

    finishElement(statement);
  }

  @Override
  public void visitField(PsiField field) {
    PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      initializeVariable(field, initializer);
    }
    else if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      // initialize with default value
      DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(field, false);
      addInstruction(new PushInstruction(dfaVariable, null, true));
      addInstruction(new PushInstruction(
        myFactory.getConstFactory().createFromValue(PsiTypesUtil.getDefaultValue(field.getType()), field.getType(), null), null));
      addInstruction(new AssignInstruction(null, dfaVariable));
      addInstruction(new PopInstruction());
    }
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    visitCodeBlock(initializer.getBody());
  }

  private void initializeVariable(PsiVariable variable, PsiExpression initializer) {
    if (DfaUtil.ignoreInitializer(variable)) return;
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(variable, false);
    addInstruction(new PushInstruction(dfaVariable, initializer, true));
    initializer.accept(this);
    generateBoxingUnboxingInstructionFor(initializer, variable.getType());
    addInstruction(new AssignInstruction(initializer, dfaVariable));
    addInstruction(new PopInstruction());
  }

  @Override
  public void visitCodeFragment(JavaCodeFragment codeFragment) {
    startElement(codeFragment);
    if (codeFragment instanceof PsiExpressionCodeFragment) {
      PsiExpression expression = ((PsiExpressionCodeFragment)codeFragment).getExpression();
      if (expression != null) {
        expression.accept(this);
      }
    }
    finishElement(codeFragment);
  }

  @Override public void visitCodeBlock(PsiCodeBlock block) {
    startElement(block);

    for (PsiStatement statement : block.getStatements()) {
      statement.accept(this);
    }

    flushCodeBlockVariables(block);

    finishElement(block);
  }

  private void flushCodeBlockVariables(PsiCodeBlock block) {
    for (PsiStatement statement : block.getStatements()) {
      if (statement instanceof PsiDeclarationStatement) {
        for (PsiElement declaration : ((PsiDeclarationStatement)statement).getDeclaredElements()) {
          if (declaration instanceof PsiVariable) {
            myCurrentFlow.removeVariable((PsiVariable)declaration);
          }
        }
      }
    }
    PsiElement parent = block.getParent();
    if (parent instanceof PsiCatchSection) {
      myCurrentFlow.removeVariable(((PsiCatchSection)parent).getParameter());
    }
    else if (parent instanceof PsiForeachStatement) {
      myCurrentFlow.removeVariable(((PsiForeachStatement)parent).getIterationParameter());
    }
    else if (parent instanceof PsiForStatement) {
      PsiStatement statement = ((PsiForStatement)parent).getInitialization();
      if (statement instanceof PsiDeclarationStatement) {
        for (PsiElement declaration : ((PsiDeclarationStatement)statement).getDeclaredElements()) {
          if (declaration instanceof PsiVariable) {
            myCurrentFlow.removeVariable((PsiVariable)declaration);
          }
        }
      }
    }
    else if (parent instanceof PsiTryStatement) {
      PsiResourceList list = ((PsiTryStatement)parent).getResourceList();
      if (list != null) {
        for (PsiResourceListElement resource : list) {
          if (resource instanceof PsiResourceVariable) {
            myCurrentFlow.removeVariable((PsiVariable)resource);
          }
        }
      }
    }
  }

  @Override public void visitBlockStatement(PsiBlockStatement statement) {
    startElement(statement);
    statement.getCodeBlock().accept(this);
    finishElement(statement);
  }

  @Override public void visitBreakStatement(PsiBreakStatement statement) {
    startElement(statement);

    PsiStatement exitedStatement = statement.findExitedStatement();

    if (exitedStatement != null && PsiTreeUtil.isAncestor(myCodeFragment, exitedStatement, false)) {
      controlTransfer(new InstructionTransfer(getEndOffset(exitedStatement), getVariablesInside(exitedStatement)),
                      getTrapsInsideElement(exitedStatement));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(ReturnTransfer.INSTANCE, getTrapsInsideElement(myCodeFragment));
    }

    finishElement(statement);
  }

  private void controlTransfer(@NotNull TransferTarget target, FList<Trap> traps) {
    addInstruction(new ControlTransferInstruction(myFactory.controlTransfer(target, traps)));
  }

  @NotNull
  private FList<Trap> getTrapsInsideElement(PsiElement element) {
    return FList.createFromReversed(ContainerUtil.reverse(
      ContainerUtil.findAll(myTrapStack, cd -> PsiTreeUtil.isAncestor(element, cd.getAnchor(), true))));
  }

  @NotNull
  private List<DfaVariableValue> getVariablesInside(PsiElement exitedStatement) {
    return ContainerUtil.map(PsiTreeUtil.findChildrenOfType(exitedStatement, PsiVariable.class),
                             var -> myFactory.getVarFactory().createVariableValue(var, false));
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement instanceof PsiLoopStatement && PsiTreeUtil.isAncestor(myCodeFragment, continuedStatement, false)) {
      PsiStatement body = ((PsiLoopStatement)continuedStatement).getBody();
      controlTransfer(new InstructionTransfer(getEndOffset(body), getVariablesInside(body)), getTrapsInsideElement(body));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(ReturnTransfer.INSTANCE, getTrapsInsideElement(myCodeFragment));
    }
    finishElement(statement);
  }

  @Override public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    startElement(statement);

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
      PsiExpression condition = statement.getCondition();
      if (condition != null) {
        condition.accept(this);
        generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
        addInstruction(new ConditionalGotoInstruction(getStartOffset(statement), false, condition));
      }
    }

    finishElement(statement);
  }

  @Override public void visitEmptyStatement(PsiEmptyStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitExpressionStatement(PsiExpressionStatement statement) {
    startElement(statement);
    final PsiExpression expr = statement.getExpression();
    expr.accept(this);
    addInstruction(new PopInstruction());
    finishElement(statement);
  }

  @Override public void visitExpressionListStatement(PsiExpressionListStatement statement) {
    startElement(statement);
    PsiExpression[] expressions = statement.getExpressionList().getExpressions();
    for (PsiExpression expr : expressions) {
      expr.accept(this);
      addInstruction(new PopInstruction());
    }
    finishElement(statement);
  }

  @Override public void visitForeachStatement(PsiForeachStatement statement) {
    startElement(statement);
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression iteratedValue = statement.getIteratedValue();

    ControlFlow.ControlFlowOffset loopEndOffset = getEndOffset(statement);
    boolean hasSizeCheck = false;

    if (iteratedValue != null) {
      iteratedValue.accept(this);
      addInstruction(new DereferenceInstruction(iteratedValue));
      DfaValue qualifier = myFactory.createValue(iteratedValue);

      if (qualifier instanceof DfaVariableValue) {
        PsiType type = iteratedValue.getType();
        SpecialField length = null;
        if (type instanceof PsiArrayType) {
          length = SpecialField.ARRAY_LENGTH;
        }
        else if (InheritanceUtil.isInheritor(type, JAVA_UTIL_COLLECTION)) {
          length = SpecialField.COLLECTION_SIZE;
        }
        if (length != null) {
          addInstruction(new PushInstruction(length.createValue(myFactory, qualifier), null));
          addInstruction(new PushInstruction(myFactory.getInt(0), null));
          addInstruction(new BinopInstruction(JavaTokenType.EQEQ, iteratedValue, myProject));
          addInstruction(new ConditionalGotoInstruction(loopEndOffset, false, null));
          hasSizeCheck = true;
        }
      }
    }

    ControlFlow.ControlFlowOffset offset = myCurrentFlow.getNextOffset();
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(parameter, false);
    addInstruction(new FlushVariableInstruction(dfaVariable));

    if (!hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, true, null));
    }

    final PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, true, null));
    }

    addInstruction(new GotoInstruction(offset));

    finishElement(statement);
    myCurrentFlow.removeVariable(parameter);
  }

  @Override public void visitForStatement(PsiForStatement statement) {
    startElement(statement);
    final ArrayList<PsiElement> declaredVariables = new ArrayList<>();

    PsiStatement initialization = statement.getInitialization();
    if (initialization != null) {
      initialization.accept(this);
      initialization.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        @Override
        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
          PsiElement[] declaredElements = statement.getDeclaredElements();
          for (PsiElement element : declaredElements) {
            if (element instanceof PsiVariable) {
              declaredVariables.add(element);
            }
          }
        }
      });
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
    }
    else {
      addInstruction(new PushInstruction(statement.getRParenth() == null ? null : myFactory.getConstFactory().getTrue(), null));
    }
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), true, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    PsiStatement update = statement.getUpdate();
    if (update != null) {
      update.accept(this);
    }

    addCountingLoopBound(statement);

    ControlFlow.ControlFlowOffset offset = initialization != null
                 ? getEndOffset(initialization)
                 : getStartOffset(statement);

    addInstruction(new GotoInstruction(offset));
    finishElement(statement);

    for (PsiElement declaredVariable : declaredVariables) {
      PsiVariable psiVariable = (PsiVariable)declaredVariable;
      myCurrentFlow.removeVariable(psiVariable);
    }
  }

  /**
   * Add known-to-be-true condition inside counting loop, effectively converting
   * {@code for(int i=origin; i<bound; i++)} to
   * {@code int i = origin; while(i < bound) {... i++; if(i <= origin) break;}}.
   * This adds a range knowledge to data flow analysis.
   * <p>
   * Does nothing if the statement is not a counting loop.
   *
   * @param statement counting loop candidate.
   */
  private void addCountingLoopBound(PsiForStatement statement) {
    CountingLoop loop = CountingLoop.from(statement);
    if (loop == null) return;
    PsiLocalVariable counter = loop.getCounter();
    if (loop.isIncluding() && !(PsiType.LONG.equals(counter.getType()) && PsiType.INT.equals(loop.getBound().getType()))) {
      Object bound = ExpressionUtils.computeConstantExpression(loop.getBound());
      // could be for(int i=0; i<=Integer.MAX_VALUE; i++) which will overflow: conservatively skip this
      if (!(bound instanceof Number)) return;
      if (bound.equals(Long.MAX_VALUE) || bound.equals(Integer.MAX_VALUE)) return;
    }
    PsiExpression initializer = loop.getInitializer();
    if (!PsiType.INT.equals(initializer.getType()) && !PsiType.LONG.equals(initializer.getType())) return;
    DfaValue origin = null;
    Object initialValue = ExpressionUtils.computeConstantExpression(initializer);
    if (initialValue instanceof Number) {
      origin = myFactory.getConstFactory().createFromValue(initialValue, initializer.getType(), null);
    }
    else if (initializer instanceof PsiReferenceExpression) {
      PsiVariable initialVariable = ObjectUtils.tryCast(((PsiReferenceExpression)initializer).resolve(), PsiVariable.class);
      if ((initialVariable instanceof PsiLocalVariable || initialVariable instanceof PsiParameter)
        && !VariableAccessUtils.variableIsAssigned(initialVariable, statement.getBody())) {
        origin = myFactory.getVarFactory().createVariableValue(initialVariable, false);
      }
    }
    if (origin == null || VariableAccessUtils.variableIsAssigned(counter, statement.getBody())) return;
    addInstruction(new PushInstruction(myFactory.getVarFactory().createVariableValue(counter, false), null));
    addInstruction(new PushInstruction(origin, null));
    addInstruction(new BinopInstruction(JavaTokenType.LE, null, myProject));
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), false, null));
  }

  @Override public void visitIfStatement(PsiIfStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    PsiStatement thenStatement = statement.getThenBranch();
    PsiStatement elseStatement = statement.getElseBranch();

    ControlFlow.ControlFlowOffset offset = elseStatement != null
                 ? getStartOffset(elseStatement)
                 : getEndOffset(statement);

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(new ConditionalGotoInstruction(offset, true, condition));
    }

    if (thenStatement != null) {
      thenStatement.accept(this);
    }

    if (elseStatement != null) {
      offset = getEndOffset(statement);
      Instruction instruction = new GotoInstruction(offset);
      addInstruction(instruction);
      elseStatement.accept(this);
    }

    finishElement(statement);
  }

  // in case of JspTemplateStatement
  @Override public void visitStatement(PsiStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitLabeledStatement(PsiLabeledStatement statement) {
    startElement(statement);
    PsiStatement childStatement = statement.getStatement();
    if (childStatement != null) {
      childStatement.accept(this);
    }
    finishElement(statement);
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    startElement(expression);
    DfaValue dfaValue = myFactory.createValue(expression);
    addInstruction(new PushInstruction(dfaValue, expression));
    addInstruction(new LambdaInstruction(expression));
    finishElement(expression);
  }

  @Override public void visitReturnStatement(PsiReturnStatement statement) {
    startElement(statement);

    PsiExpression returnValue = statement.getReturnValue();

    if (myInlinedBlockContext != null) {
      if (returnValue != null) {
        DfaVariableValue var = myFactory.getVarFactory().createVariableValue(myInlinedBlockContext.myTarget, false);
        addInstruction(new PushInstruction(var, null, true));
        returnValue.accept(this);
        generateBoxingUnboxingInstructionFor(returnValue, var.getVariableType());
        if (myInlinedBlockContext.myForceNonNullBlockResult) {
          addInstruction(new CheckNotNullInstruction(NullabilityProblemKind.nullableFunctionReturn.problem(returnValue)));
        }
        addInstruction(new AssignInstruction(returnValue, null));
        addInstruction(new PopInstruction());
      }

      controlTransfer(new InstructionTransfer(getEndOffset(myInlinedBlockContext.myCodeBlock), getVariablesInside(
        myInlinedBlockContext.myCodeBlock)), myTrapStack);
    } else {

      if (returnValue != null) {
        returnValue.accept(this);
        PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiMember.class, PsiLambdaExpression.class);
        if (method != null) {
          generateBoxingUnboxingInstructionFor(returnValue, method.getReturnType());
        }
        else {
          final PsiLambdaExpression lambdaExpression =
            PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression.class, true, PsiMember.class);
          if (lambdaExpression != null) {
            generateBoxingUnboxingInstructionFor(returnValue, LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression));
          }
        }
        addInstruction(new CheckReturnValueInstruction(returnValue));
      }

      addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, myTrapStack), statement));
    }
    finishElement(statement);
  }

  @Override public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitSwitchStatement(PsiSwitchStatement switchStmt) {
    startElement(switchStmt);
    PsiExpression caseExpression = switchStmt.getExpression();
    Set<PsiEnumConstant> enumValues = null;
    if (caseExpression != null /*&& !(caseExpression instanceof PsiReferenceExpression)*/) {
      caseExpression.accept(this);

      generateBoxingUnboxingInstructionFor(caseExpression, PsiType.INT);
      final PsiClass psiClass = PsiUtil.resolveClassInType(caseExpression.getType());
      if (psiClass != null) {
        addInstruction(new DereferenceInstruction(caseExpression));
        if (psiClass.isEnum()) {
          enumValues = new HashSet<>();
          for (PsiField f : psiClass.getFields()) {
            if (f instanceof PsiEnumConstant) {
              enumValues.add((PsiEnumConstant)f);
            }
          }
        }
      } else {
        addInstruction(new PopInstruction());
      }

    }

    PsiCodeBlock body = switchStmt.getBody();

    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      PsiSwitchLabelStatement defaultLabel = null;
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiSwitchLabelStatement) {
          PsiSwitchLabelStatement psiLabelStatement = (PsiSwitchLabelStatement)statement;
          if (psiLabelStatement.isDefaultCase()) {
            defaultLabel = psiLabelStatement;
          }
          else {
            try {
              ControlFlow.ControlFlowOffset offset = getStartOffset(statement);
              PsiExpression caseValue = psiLabelStatement.getCaseValue();

              if (enumValues != null && caseValue instanceof PsiReferenceExpression) {
                //noinspection SuspiciousMethodCalls
                enumValues.remove(((PsiReferenceExpression)caseValue).resolve());
              }

              boolean alwaysTrue = enumValues != null && enumValues.isEmpty();
              if (alwaysTrue) {
                addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
              }
              else if (caseValue != null &&
                  caseExpression instanceof PsiReferenceExpression &&
                  ((PsiReferenceExpression)caseExpression).getQualifierExpression() == null) {
                
                addInstruction(new PushInstruction(myFactory.createValue(caseExpression), caseExpression));
                caseValue.accept(this);
                addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, myProject));
              }
              else {
                pushUnknown();
              }

              addInstruction(new ConditionalGotoInstruction(offset, false, statement));

            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      if (enumValues == null || !enumValues.isEmpty()) {
        ControlFlow.ControlFlowOffset offset = defaultLabel != null ? getStartOffset(defaultLabel) : getEndOffset(body);
        addInstruction(new GotoInstruction(offset));
      }

      body.accept(this);
    }

    finishElement(switchStmt);
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    startElement(expression);

    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null) {
      qualifier.accept(this);
      addInstruction(new DereferenceInstruction(qualifier));
    }

    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getFunctionalInterfaceType(), Nullness.NOT_NULL), expression));

    finishElement(expression);
  }

  @Override public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
      addInstruction(new DereferenceInstruction(lock));
    }

    addInstruction(new FlushVariableInstruction(null));

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    finishElement(statement);
  }

  @Override public void visitThrowStatement(PsiThrowStatement statement) {
    startElement(statement);

    PsiExpression exception = statement.getException();

    if (exception != null) {
      exception.accept(this);

      addInstruction(new DereferenceInstruction(exception));
      throwException(exception.getType(), statement);
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    if (myTrapStack.isEmpty()) {
      return;
    }
    
    pushUnknown();
    final ConditionalGotoInstruction ifNoException = addInstruction(new ConditionalGotoInstruction(null, false, null));

    pushUnknown();
    final ConditionalGotoInstruction ifError = addInstruction(new ConditionalGotoInstruction(null, false, null));
    throwException(myRuntimeException, null);
    ifError.setOffset(myCurrentFlow.getInstructionCount());
    throwException(myError, null);

    ifNoException.setOffset(myCurrentFlow.getInstructionCount());
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);

    PsiResourceList resourceList = statement.getResourceList();
    PsiCodeBlock tryBlock = statement.getTryBlock();
    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    Trap.TryFinally finallyDescriptor = finallyBlock != null ? new Trap.TryFinally(finallyBlock, getStartOffset(finallyBlock)) : null;
    if (finallyDescriptor != null) {
      myTrapStack = myTrapStack.prepend(finallyDescriptor);
    }

    PsiCatchSection[] sections = statement.getCatchSections();
    if (sections.length > 0) {
      LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset> clauses = new LinkedHashMap<>();
      for (PsiCatchSection section : sections) {
        PsiCodeBlock catchBlock = section.getCatchBlock();
        if (catchBlock != null) {
          clauses.put(section, getStartOffset(catchBlock));
        }
      }
      myTrapStack = myTrapStack.prepend(new Trap.TryCatch(statement, clauses));
    }

    processTryWithResources(resourceList, tryBlock);

    InstructionTransfer gotoEnd = new InstructionTransfer(getEndOffset(statement), getVariablesInside(tryBlock));
    FList<Trap> singleFinally = FList.createFromReversed(ContainerUtil.createMaybeSingletonList(finallyDescriptor));
    controlTransfer(gotoEnd, singleFinally);

    if (sections.length > 0) {
      assert myTrapStack.getHead() instanceof Trap.TryCatch;
      myTrapStack = myTrapStack.getTail();
    }

    for (PsiCatchSection section : sections) {
      PsiCodeBlock catchBlock = section.getCatchBlock();
      if (catchBlock != null) {
        visitCodeBlock(catchBlock);
      }
      controlTransfer(gotoEnd, singleFinally);
    }

    if (finallyBlock != null) {
      assert myTrapStack.getHead() instanceof Trap.TryFinally;
      myTrapStack = myTrapStack.getTail().prepend(new Trap.InsideFinally(finallyBlock));

      finallyBlock.accept(this);
      addInstruction(new ControlTransferInstruction(null)); // DfaControlTransferValue is on stack

      assert myTrapStack.getHead() instanceof Trap.InsideFinally;
      myTrapStack = myTrapStack.getTail();
    }

    finishElement(statement);
  }

  private void processTryWithResources(@Nullable PsiResourceList resourceList, @Nullable PsiCodeBlock tryBlock) {
    Set<PsiClassType> closerExceptions = Collections.emptySet();
    Trap.TwrFinally twrFinallyDescriptor = null;
    if (resourceList != null) {
      resourceList.accept(this);

      closerExceptions = StreamEx.of(resourceList.iterator()).flatCollection(ExceptionUtil::getCloserExceptions).toSet();
      if (!closerExceptions.isEmpty()) {
        twrFinallyDescriptor = new Trap.TwrFinally(resourceList, getStartOffset(resourceList));
        myTrapStack = myTrapStack.prepend(twrFinallyDescriptor);
      }
    }

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    if (twrFinallyDescriptor != null) {
      assert myTrapStack.getHead() instanceof Trap.TwrFinally;
      InstructionTransfer gotoEnd = new InstructionTransfer(getEndOffset(resourceList), getVariablesInside(tryBlock));
      controlTransfer(gotoEnd, FList.createFromReversed(ContainerUtil.createMaybeSingletonList(twrFinallyDescriptor)));
      myTrapStack = myTrapStack.getTail().prepend(new Trap.InsideFinally(resourceList));
      startElement(resourceList);
      addThrows(null, closerExceptions.toArray(PsiClassType.EMPTY_ARRAY));
      addInstruction(new ControlTransferInstruction(null)); // DfaControlTransferValue is on stack
      finishElement(resourceList);
      assert myTrapStack.getHead() instanceof Trap.InsideFinally;
      myTrapStack = myTrapStack.getTail();
    }
  }

  @Override
  public void visitResourceList(PsiResourceList resourceList) {
    for (PsiResourceListElement resource : resourceList) {
      if (resource instanceof PsiResourceVariable) {
        PsiResourceVariable variable = (PsiResourceVariable)resource;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          initializeVariable(variable, initializer);
        }
      }
      else if (resource instanceof PsiResourceExpression) {
        ((PsiResourceExpression)resource).getExpression().accept(this);
        addInstruction(new PopInstruction());
      }
    }
  }

  @Override public void visitWhileStatement(PsiWhileStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
    } else {
      pushUnknown();
    }
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), true, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    addInstruction(new GotoInstruction(getStartOffset(statement)));

    finishElement(statement);
  }

  @Override public void visitExpressionList(PsiExpressionList list) {
    startElement(list);

    PsiExpression[] expressions = list.getExpressions();
    for (PsiExpression expression : expressions) {
      expression.accept(this);
    }

    finishElement(list);
  }

  @Override public void visitExpression(PsiExpression expression) {
    startElement(expression);
    DfaValue dfaValue = myFactory.createValue(expression);
    addInstruction(new PushInstruction(dfaValue, expression));
    finishElement(expression);
  }

  @Override
  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    startElement(expression);
    PsiExpression arrayExpression = expression.getArrayExpression();
    arrayExpression.accept(this);

    PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
      generateBoxingUnboxingInstructionFor(indexExpression, PsiType.INT);
    } else {
      addInstruction(new PushInstruction(DfaUnknownValue.getInstance(), null));
    }

    DfaValue toPush = myFactory.createValue(expression);
    if (toPush == null) {
      toPush = myFactory.createTypeValue(expression.getType(), Nullness.UNKNOWN);
    }
    addInstruction(new ArrayAccessInstruction(toPush, expression));
    finishElement(expression);
  }

  @Nullable
  private DfaVariableValue getTargetVariable(PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiVariable) {
      // initialization
      return getFactory().getVarFactory().createVariableValue((PsiVariable)parent, false);
    }
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (assignmentExpression.getOperationTokenType().equals(JavaTokenType.EQ) &&
          PsiTreeUtil.isAncestor(assignmentExpression.getRExpression(), expression, false)) {
        DfaValue value = getFactory().createValue(assignmentExpression.getLExpression());
        if (value instanceof DfaVariableValue) {
          return (DfaVariableValue)value;
        }
      }
    }
    return null;
  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    startElement(expression);
    PsiType type = expression.getType();
    PsiType componentType = type instanceof PsiArrayType ? ((PsiArrayType)type).getComponentType() : null;
    DfaVariableValue var = getTargetVariable(expression);
    processArrayInitializers(expression, componentType, DfaPsiUtil.getTypeNullability(componentType));
    if (var != null) {
      // Declaration: write array length
      addInstruction(new PushInstruction(var, null, true));
      addInstruction(new PushInstruction(getFactory().createTypeValue(type, Nullness.NOT_NULL), expression));
      addInstruction(new AssignInstruction(expression, var));
      addInstruction(new PushInstruction(SpecialField.ARRAY_LENGTH.createValue(getFactory(), var), null, true));
      addInstruction(new PushInstruction(getFactory().getInt(expression.getInitializers().length), null));
      addInstruction(new AssignInstruction(null, null));
      addInstruction(new PopInstruction());
    }
    else {
      pushUnknown();
    }
    finishElement(expression);
  }

  private void processArrayInitializers(@NotNull PsiArrayInitializerExpression expression,
                                        @Nullable PsiType componentType,
                                        @NotNull Nullness componentNullability) {
    PsiExpression[] initializers = expression.getInitializers();
    for (PsiExpression initializer : initializers) {
      initializer.accept(this);
      if (componentType != null) {
        generateBoxingUnboxingInstructionFor(initializer, componentType);
        if (componentNullability == Nullness.NOT_NULL) {
          addInstruction(new CheckNotNullInstruction(NullabilityProblemKind.storingToNotNullArray.problem(initializer)));
        }
      }
      addInstruction(new PopInstruction());
    }
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.createValue(expression);
    if (dfaValue != null) {
      addInstruction(new PushInstruction(dfaValue, expression));
      finishElement(expression);
      return;
    }
    IElementType op = expression.getOperationTokenType();

    PsiExpression[] operands = expression.getOperands();
    if (operands.length <= 1) {
      pushUnknown();
      finishElement(expression);
      return;
    }
    PsiType type = expression.getType();
    if (op == JavaTokenType.ANDAND) {
      generateAndExpression(operands, type, true);
    }
    else if (op == JavaTokenType.OROR) {
      generateOrExpression(operands, type, true);
    }
    else if (op == JavaTokenType.XOR && PsiType.BOOLEAN.equals(type)) {
      generateXorExpression(expression, operands, type, false);
    }
    else if (op == JavaTokenType.AND && PsiType.BOOLEAN.equals(type)) {
      generateAndExpression(operands, type, false);
    }
    else if (op == JavaTokenType.OR && PsiType.BOOLEAN.equals(type)) {
      generateOrExpression(operands, type, false);
    }
    else if (isBinaryDivision(op) && operands.length == 2 &&
             type != null && PsiType.LONG.isAssignableFrom(type)) {
      generateDivMod(expression, type, operands[0], operands[1]);
    }
    else {
      generateOther(expression, op, operands, type);
    }
    finishElement(expression);
  }

  static boolean isBinaryDivision(IElementType binaryOp) {
    return binaryOp == JavaTokenType.DIV || binaryOp == JavaTokenType.PERC;
  }

  static boolean isAssignmentDivision(IElementType op) {
    return op == JavaTokenType.PERCEQ || op == JavaTokenType.DIVEQ;
  }

  private void generateDivMod(PsiPolyadicExpression expression, PsiType type, PsiExpression left, PsiExpression right) {
    left.accept(this);
    generateBoxingUnboxingInstructionFor(left, type);
    right.accept(this);
    generateBoxingUnboxingInstructionFor(right, type);
    checkZeroDivisor();
    addInstruction(new BinopInstruction(expression.getOperationTokenType(), expression.isPhysical() ? expression : null, myProject));
  }

  private void checkZeroDivisor() {
    addInstruction(new DupInstruction());
    addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(0, PsiType.LONG, null), null));
    addInstruction(new BinopInstruction(JavaTokenType.NE, null, myProject));
    ConditionalGotoInstruction ifNonZero = new ConditionalGotoInstruction(null, false, null);
    addInstruction(ifNonZero);
    throwException(JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(ArithmeticException.class.getName()), null);
    ifNonZero.setOffset(myCurrentFlow.getInstructionCount());
  }

  private void generateOther(PsiPolyadicExpression expression, IElementType op, PsiExpression[] operands, PsiType type) {
    op = substituteBinaryOperation(expression, op);

    PsiExpression lExpr = operands[0];
    lExpr.accept(this);
    PsiType lType = lExpr.getType();

    for (int i = 1; i < operands.length; i++) {
      PsiExpression rExpr = operands[i];
      PsiType rType = rExpr.getType();

      acceptBinaryRightOperand(op, type, lExpr, lType, rExpr, rType);
      addInstruction(new BinopInstruction(op, expression.isPhysical() ? expression : null, myProject));

      lExpr = rExpr;
      lType = rType;
    }
  }

  @Nullable
  private static IElementType substituteBinaryOperation(PsiPolyadicExpression expression, IElementType op) {
    if (JavaTokenType.PLUS == op) {
      if (TypeUtils.isJavaLangString(expression.getType()) || isAcceptableContextForMathOperation(expression)) return op;
      return null;
    }
    if (JavaTokenType.MINUS == op && !isAcceptableContextForMathOperation(expression)) return null;
    return op;
  }

  private static boolean isAcceptableContextForMathOperation(PsiExpression expression) {
    PsiType type = expression.getType();
    PsiElement parent = expression.getParent();
    while (parent != null && !(parent instanceof PsiAssignmentExpression) && !(parent instanceof PsiStatement) && !(parent instanceof PsiLambdaExpression)) {
      if (parent instanceof PsiExpressionList) return true;
      if (parent instanceof PsiBinaryExpression && DfaRelationValue.RelationType.fromElementType(((PsiBinaryExpression)parent).getOperationTokenType()) != null) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  private void acceptBinaryRightOperand(@Nullable IElementType op, PsiType type,
                                        PsiExpression lExpr, PsiType lType,
                                        PsiExpression rExpr, PsiType rType) {
    boolean comparing = op == JavaTokenType.EQEQ || op == JavaTokenType.NE;
    boolean comparingRef = comparing
                           && !TypeConversionUtil.isPrimitiveAndNotNull(lType)
                           && !TypeConversionUtil.isPrimitiveAndNotNull(rType);

    boolean comparingPrimitiveNumeric = comparing &&
                                        TypeConversionUtil.isPrimitiveAndNotNull(lType) &&
                                        TypeConversionUtil.isPrimitiveAndNotNull(rType) &&
                                        TypeConversionUtil.isNumericType(lType) &&
                                        TypeConversionUtil.isNumericType(rType);

    PsiType castType = comparingPrimitiveNumeric ? TypeConversionUtil.isFloatOrDoubleType(lType) ? PsiType.DOUBLE : PsiType.LONG : type;

    if (!comparingRef) {
      generateBoxingUnboxingInstructionFor(lExpr,castType);
    }

    rExpr.accept(this);
    if (!comparingRef) {
      generateBoxingUnboxingInstructionFor(rExpr, castType);
    }
  }

  void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression expression, PsiType expectedType) {
    generateBoxingUnboxingInstructionFor(expression, expression.getType(), expectedType);
  }

  void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression context, PsiType actualType, PsiType expectedType) {
    if (PsiType.VOID.equals(expectedType)) return;

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(actualType)) {
      addInstruction(new MethodCallInstruction(context, MethodCallInstruction.MethodType.UNBOXING, expectedType));
    }
    else if (TypeConversionUtil.isPrimitiveAndNotNull(actualType) && TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType)) {
      addConditionalRuntimeThrow();
      addInstruction(new MethodCallInstruction(context, MethodCallInstruction.MethodType.BOXING, expectedType));
    }
    else if (actualType != expectedType &&
             TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(actualType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      addInstruction(new MethodCallInstruction(context, MethodCallInstruction.MethodType.CAST, expectedType) {
        @Override
        public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
          return visitor.visitCast(this, runner, stateBefore);
        }
      });
    }
  }

  private void generateXorExpression(PsiExpression expression, PsiExpression[] operands, final PsiType exprType, boolean forAssignment) {
    PsiExpression operand = operands[0];
    operand.accept(this);
    if (forAssignment) {
      addInstruction(new DupInstruction());
    }
    generateBoxingUnboxingInstructionFor(operand, exprType);
    for (int i = 1; i < operands.length; i++) {
      operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);
      PsiElement psiAnchor = i == operands.length - 1 && expression.isPhysical() ? expression : null;
      addInstruction(new BinopInstruction(JavaTokenType.NE, psiAnchor, myProject));
    }
  }

  private void generateOrExpression(PsiExpression[] operands, final PsiType exprType, boolean shortCircuit) {
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);
      if (!shortCircuit) {
        if (i > 0) {
          combineStackBooleans(false, operand);
        }
        continue;
      }

      PsiExpression nextOperand = i == operands.length - 1 ? null : operands[i + 1];
      if (nextOperand != null) {
        addInstruction(new ConditionalGotoInstruction(getStartOffset(nextOperand), true, operand));
        addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
        addInstruction(new GotoInstruction(getEndOffset(operands[operands.length - 1])));
      }
    }
  }

  private void generateBooleanAssignmentExpression(boolean and, PsiExpression lExpression, PsiExpression rExpression, PsiType exprType) {
    lExpression.accept(this);
    generateBoxingUnboxingInstructionFor(lExpression, exprType);
    addInstruction(new DupInstruction());

    rExpression.accept(this);
    generateBoxingUnboxingInstructionFor(rExpression, exprType);
    addInstruction(new SwapInstruction());

    combineStackBooleans(and, lExpression);
  }

  private void combineStackBooleans(boolean and, PsiExpression anchor) {
    ConditionalGotoInstruction toPopAndPushSuccess = new ConditionalGotoInstruction(null, and, anchor);
    addInstruction(toPopAndPushSuccess);
    GotoInstruction overPushSuccess = new GotoInstruction(null);
    addInstruction(overPushSuccess);

    PopInstruction pop = new PopInstruction();
    addInstruction(pop);
    DfaConstValue constValue = myFactory.getBoolean(!and);
    PushInstruction pushSuccess = new PushInstruction(constValue, null);
    addInstruction(pushSuccess);

    toPopAndPushSuccess.setOffset(pop.getIndex());
    overPushSuccess.setOffset(pushSuccess.getIndex() + 1);
  }

  private void generateAndExpression(PsiExpression[] operands, final PsiType exprType, boolean shortCircuit) {
    List<ConditionalGotoInstruction> branchToFail = new ArrayList<>();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);

      if (!shortCircuit) {
        if (i > 0) {
          combineStackBooleans(true, operand);
        }
        continue;
      }

      ConditionalGotoInstruction onFail = new ConditionalGotoInstruction(null, true, operand);
      branchToFail.add(onFail);
      addInstruction(onFail);
    }

    if (!shortCircuit) {
      return;
    }

    addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
    GotoInstruction toSuccess = new GotoInstruction(null);
    addInstruction(toSuccess);
    PushInstruction pushFalse = new PushInstruction(myFactory.getConstFactory().getFalse(), null);
    addInstruction(pushFalse);
    for (ConditionalGotoInstruction toFail : branchToFail) {
      toFail.setOffset(pushFalse.getIndex());
    }
    toSuccess.setOffset(pushFalse.getIndex()+1);

  }

  @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    startElement(expression);
    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL), expression));
    finishElement(expression);
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    PsiExpression condition = expression.getCondition();

    PsiExpression thenExpression = expression.getThenExpression();
    PsiExpression elseExpression = expression.getElseExpression();

    final ControlFlow.ControlFlowOffset elseOffset = elseExpression == null ? ControlFlow.deltaOffset(getEndOffset(expression), -1) : getStartOffset(elseExpression);
    if (thenExpression != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      PsiType type = expression.getType();
      addInstruction(new ConditionalGotoInstruction(elseOffset, true, PsiUtil.skipParenthesizedExprDown(condition)));
      thenExpression.accept(this);
      generateBoxingUnboxingInstructionFor(thenExpression,type);

      addInstruction(new GotoInstruction(getEndOffset(expression)));

      if (elseExpression != null) {
        elseExpression.accept(this);
        generateBoxingUnboxingInstructionFor(elseExpression,type);
      }
      else {
        pushUnknown();
      }
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  void pushUnknown() {
    addInstruction(new PushInstruction(DfaUnknownValue.getInstance(), null));
  }

  @Override public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    startElement(expression);
    PsiExpression operand = expression.getOperand();
    PsiTypeElement checkType = expression.getCheckType();
    if (checkType != null) {
      operand.accept(this);
      PsiType type = checkType.getType();
      if (type instanceof PsiClassType) {
        type = ((PsiClassType)type).rawType();
      }
      addInstruction(new PushInstruction(myFactory.createTypeValue(type, Nullness.NOT_NULL), null));
      addInstruction(new InstanceofInstruction(expression, myProject, operand, type));
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private void addMethodThrows(PsiMethod method, @Nullable PsiElement explicitCall) {
    if (method != null) {
      addThrows(explicitCall, method.getThrowsList().getReferencedTypes());
    }
  }

  private void addThrows(@Nullable PsiElement explicitCall, PsiClassType[] refs) {
    for (PsiClassType ref : refs) {
      pushUnknown();
      ConditionalGotoInstruction cond = new ConditionalGotoInstruction(null, false, null);
      addInstruction(cond);
      throwException(ref, explicitCall);
      cond.setOffset(myCurrentFlow.getInstructionCount());
    }
  }

  private void throwException(@Nullable PsiType ref, @Nullable PsiElement anchor) {
    if (ref != null) {
      throwException(new ExceptionTransfer(myFactory.createDfaType(ref)), anchor);
    }
  }

  private void throwException(ExceptionTransfer kind, @Nullable PsiElement anchor) {
    addInstruction(new EmptyStackInstruction());
    addInstruction(new ReturnInstruction(myFactory.controlTransfer(kind, myTrapStack), anchor));
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    startElement(expression);

    if (myInlining) {
      for (CallInliner inliner : INLINERS) {
        if (inliner.tryInlineCall(new CFGBuilder(this), expression)) {
          finishElement(expression);
          return;
        }
      }
    }

    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
    }
    else if (myThisReadOnly) {
      addInstruction(new PushInstruction(myFactory.getFactValue(DfaFactType.MUTABILITY, Mutability.UNMODIFIABLE), null));
    } else {
      pushUnknown();
    }

    PsiExpression[] expressions = expression.getArgumentList().getExpressions();
    JavaResolveResult result = methodExpression.advancedResolve(false);
    PsiElement method = result.getElement();
    PsiParameter[] parameters = method instanceof PsiMethod ? ((PsiMethod)method).getParameterList().getParameters() : null;

    for (int i = 0; i < expressions.length; i++) {
      PsiExpression paramExpr = expressions[i];
      paramExpr.accept(this);
      if (parameters != null && i < parameters.length) {
        generateBoxingUnboxingInstructionFor(paramExpr, result.getSubstitutor().substitute(parameters[i].getType()));
      }
    }

    addBareCall(expression, expression.getMethodExpression());
    finishElement(expression);
  }

  void addBareCall(@Nullable PsiMethodCallExpression expression, @NotNull PsiReferenceExpression reference) {
    addConditionalRuntimeThrow();
    PsiMethod method = ObjectUtils.tryCast(reference.resolve(), PsiMethod.class);
    List<? extends MethodContract> contracts = method == null ? Collections.emptyList() : getMethodCallContracts(method, expression);
    MethodCallInstruction instruction;
    PsiExpression anchor;
    if (expression == null) {
      assert reference instanceof PsiMethodReferenceExpression;
      instruction = new MethodCallInstruction((PsiMethodReferenceExpression)reference, contracts);
      anchor = reference;
    }
    else {
      instruction = new MethodCallInstruction(expression, myFactory.createValue(expression), contracts);
      anchor = expression;
    }
    addInstruction(instruction);
    if (contracts.stream().anyMatch(c -> c.getReturnValue() == MethodContract.ValueConstraint.THROW_EXCEPTION)) {
      // if a contract resulted in 'fail', handle it
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getContractFail(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, myProject));
      ConditionalGotoInstruction ifNotFail = new ConditionalGotoInstruction(null, true, null);
      addInstruction(ifNotFail);
      addInstruction(new EmptyStackInstruction());
      addInstruction(
        new ReturnInstruction(myFactory.controlTransfer(new ExceptionTransfer(null), myTrapStack), anchor));

      ifNotFail.setOffset(myCurrentFlow.getInstructionCount());
    }

    if (!myTrapStack.isEmpty()) {
      addMethodThrows(method, anchor);
    }
  }

  public static List<? extends MethodContract> getMethodCallContracts(@NotNull final PsiMethod method,
                                                                      @Nullable PsiMethodCallExpression call) {
    List<MethodContract> contracts = HardcodedContracts.getHardcodedContracts(method, call);
    return !contracts.isEmpty() ? contracts : getMethodContracts(method);
  }

  public static List<StandardMethodContract> getMethodContracts(@NotNull final PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> {
      final PsiAnnotation contractAnno = findContractAnnotation(method);
      if (contractAnno != null) {
        String text = AnnotationUtil.getStringAttributeValue(contractAnno, null);
        if (text != null) {
          try {
            final int paramCount = method.getParameterList().getParametersCount();
            List<StandardMethodContract> applicable = ContainerUtil.filter(StandardMethodContract.parseContract(text),
                                                                           contract -> contract.arguments.length == paramCount);
            return CachedValueProvider.Result.create(applicable, contractAnno, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
          catch (Exception ignored) {
          }
        }
      }
      return CachedValueProvider.Result
        .create(Collections.emptyList(), method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  @Nullable
  public static PsiAnnotation findContractAnnotation(@NotNull PsiMethod method) {
    return AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(ORG_JETBRAINS_ANNOTATIONS_CONTRACT));
  }

  public static boolean isPure(@NotNull PsiMethod method) {
    PsiAnnotation anno = findContractAnnotation(method);
    return anno != null && Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(anno, "pure"));
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    if (enumConstant.getArgumentList() == null) return;

    pushUnknown();
    pushConstructorArguments(enumConstant);
    addInstruction(new MethodCallInstruction(enumConstant, null, Collections.emptyList()));
    addInstruction(new PopInstruction());
  }

  @Override public void visitNewExpression(PsiNewExpression expression) {
    startElement(expression);

    PsiExpression qualifier = expression.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
      addInstruction(new CheckNotNullInstruction(NullabilityProblemKind.innerClassNPE.problem(expression)));
      addInstruction(new PopInstruction());
    }

    PsiType type = expression.getType();
    if (type instanceof PsiArrayType) {
      DfaVariableValue var = getTargetVariable(expression);
      if (var == null) {
        var = getFactory().getVarFactory().createVariableValue(createTempVariable(type), false);
      }
      DfaValue length = SpecialField.ARRAY_LENGTH.createValue(getFactory(), var);
      addInstruction(new PushInstruction(length, null, true));
      // stack: ... var.length
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      boolean sizeOnStack = false;
      for (final PsiExpression dimension : dimensions) {
        dimension.accept(this);
        if (sizeOnStack) {
          addInstruction(new PopInstruction());
        }
        sizeOnStack = true;
      }
      final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
      if (arrayInitializer != null) {
        Nullness nullability = DfaPsiUtil.getTypeNullability(((PsiArrayType)type).getComponentType());
        if(nullability == Nullness.UNKNOWN) {
          PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
          if(expectedType instanceof PsiArrayType) {
            nullability = DfaPsiUtil.getTypeNullability(((PsiArrayType)expectedType).getComponentType());
          }
        }
        processArrayInitializers(arrayInitializer, ((PsiArrayType)type).getComponentType(), nullability);
        if (!sizeOnStack) {
          sizeOnStack = true;
          addInstruction(new PushInstruction(getFactory().getInt(arrayInitializer.getInitializers().length), null));
        }
      }
      if (!sizeOnStack) {
        pushUnknown();
      }
      // stack: ... var.length actual_size
      addInstruction(new PushInstruction(var, null, true));
      addInstruction(new PushInstruction(getFactory().createTypeValue(type, Nullness.NOT_NULL), expression));
      addInstruction(new AssignInstruction(expression, var));
      // stack: ... var.length actual_size var
      addInstruction(new SpliceInstruction(3, 0, 2, 1));
      // stack: ... var var.length actual_size
      addInstruction(new AssignInstruction(null, length));
      addInstruction(new PopInstruction());
      // stack: ... var
    }
    else {
      pushUnknown(); // qualifier
      PsiMethod constructor = pushConstructorArguments(expression);

      addConditionalRuntimeThrow();
      addInstruction(new MethodCallInstruction(expression, null, constructor == null ? Collections.emptyList() : getMethodContracts(constructor)));

      if (!myTrapStack.isEmpty()) {
        addMethodThrows(constructor, expression);
      }

    }

    finishElement(expression);
  }

  @Nullable
  private PsiMethod pushConstructorArguments(PsiConstructorCall call) {
    PsiExpressionList args = call.getArgumentList();
    PsiMethod ctr = call.resolveConstructor();
    if (args != null) {
      PsiExpression[] params = args.getExpressions();
      PsiParameter[] parameters = ctr == null ? null : ctr.getParameterList().getParameters();
      for (int i = 0; i < params.length; i++) {
        PsiExpression param = params[i];
        param.accept(this);
        if (parameters != null && i < parameters.length) {
          generateBoxingUnboxingInstructionFor(param, parameters[i].getType());
        }
      }
    }
    return ctr;
  }

  @Override public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    startElement(expression);
    PsiExpression inner = expression.getExpression();
    if (inner != null) {
      inner.accept(this);
    }
    else {
      pushUnknown();
    }
    finishElement(expression);
  }

  @Override public void visitPostfixExpression(PsiPostfixExpression expression) {
    startElement(expression);

    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, PsiType.INT);
    } else {
      pushUnknown();
    }

    addInstruction(new PopInstruction());
    pushUnknown();

    flushIncrementedValue(operand);

    finishElement(expression);
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.createValue(expression);
    if (dfaValue == null) {
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());

      if (operand == null) {
        pushUnknown();
      }
      else {
        operand.accept(this);
        PsiType type = expression.getType();
        PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
        generateBoxingUnboxingInstructionFor(operand, unboxed == null ? type : unboxed);
        if (expression.getOperationTokenType() == JavaTokenType.EXCL) {
          addInstruction(new NotInstruction());
        }
        else if (expression.getOperationTokenType() == JavaTokenType.MINUS && (PsiType.INT.equals(type) || PsiType.LONG.equals(type))) {
          addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(PsiTypesUtil.getDefaultValue(type), type, null), null));
          addInstruction(new SwapInstruction());
          addInstruction(new BinopInstruction(expression.getOperationTokenType(), expression, myProject));
        }
        else {
          addInstruction(new PopInstruction());
          pushUnknown();

          flushIncrementedValue(operand);
        }
      }
    }
    else {
      addInstruction(new PushInstruction(dfaValue, expression));
    }

    finishElement(expression);
  }

  private void flushIncrementedValue(@Nullable PsiExpression operand) {
    DfaValue dfaVariable = operand == null ? null : myFactory.createValue(operand);
    if (dfaVariable instanceof DfaVariableValue && PsiUtil.isAccessedForWriting(operand)) {
      addInstruction(new FlushVariableInstruction((DfaVariableValue)dfaVariable));
      if (((DfaVariableValue)dfaVariable).getPsiVariable() instanceof PsiField) {
        addInstruction(new FlushVariableInstruction(null));
      }
    }
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    startElement(expression);

    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
      addInstruction(expression.resolve() instanceof PsiField ? new DereferenceInstruction(qualifierExpression) : new PopInstruction());
    }

    // complex assignments (e.g. "|=") are both reading and writing
    boolean writing = PsiUtil.isAccessedForWriting(expression) && !PsiUtil.isAccessedForReading(expression);
    addInstruction(new PushInstruction(myFactory.createValue(expression), expression, writing));

    finishElement(expression);
  }

  @Override public void visitSuperExpression(PsiSuperExpression expression) {
    startElement(expression);
    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL), expression));
    finishElement(expression);
  }

  @Override public void visitThisExpression(PsiThisExpression expression) {
    startElement(expression);
    DfaValue value = myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL);
    if (myThisReadOnly) {
      value = myFactory.withFact(value, DfaFactType.MUTABILITY, Mutability.UNMODIFIABLE);
    }
    addInstruction(new PushInstruction(value, expression));
    finishElement(expression);
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.createLiteralValue(expression);
    addInstruction(new PushInstruction(dfaValue, expression));

    finishElement(expression);
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression castExpression) {
    startElement(castExpression);
    PsiExpression operand = castExpression.getOperand();

    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, castExpression.getType());
    }
    else {
      addInstruction(new PushInstruction(myFactory.createTypeValue(castExpression.getType(), Nullness.UNKNOWN), null));
    }

    final PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null && operand.getType() != null) {
      if (typeElement.getType() instanceof PsiPrimitiveType &&
          !UnnecessaryExplicitNumericCastInspection.isUnnecessaryPrimitiveNumericCast(castExpression)) {
        addInstruction(new PopInstruction());
        pushUnknown();
      } else {
        addInstruction(new TypeCastInstruction(castExpression, operand, typeElement.getType()));
      }
    }
    finishElement(castExpression);
  }

  @Override public void visitClass(PsiClass aClass) {
  }

  /**
   * Inline code block (lambda or method body) into this CFG. Incoming parameters are assumed to be handled already (if necessary)
   *
   * @param block block to inline
   * @param resultNullness desired nullness returned by block return statement
   * @param target a variable to store the block result (returned via {@code return} statement)
   */
  void inlineBlock(@NotNull PsiCodeBlock block, @NotNull Nullness resultNullness, @NotNull PsiVariable target) {
    InlinedBlockContext oldBlock = myInlinedBlockContext;
    // Transfer value is pushed to avoid emptying stack beyond this point
    addInstruction(new PushInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, this.myTrapStack), null));
    myInlinedBlockContext = new InlinedBlockContext(block, resultNullness == Nullness.NOT_NULL, target);
    startElement(block);
    try {
      block.accept(this);
    }
    finally {
      finishElement(block);
      myInlinedBlockContext = oldBlock;
      // Pop transfer value
      addInstruction(new PopInstruction());
    }
  }

  /**
   * Create a temporary {@link PsiVariable} (not declared in the original code) to be used within this control flow.
   *
   * @param type a type of variable to create
   * @return newly created variable
   */
  @NotNull
  PsiVariable createTempVariable(@Nullable PsiType type) {
    if(type == null) {
      type = PsiType.VOID;
    }
    return new TempVariable(getInstructionCount(), type, getContext());
  }

  /**
   * Checks whether supplied variable is a temporary variable created previously via {@link #createTempVariable(PsiType)}
   *
   * @param variable to check
   * @return true if supplied variable is a temp variable.
   */
  public static boolean isTempVariable(PsiModifierListOwner variable) {
    return variable instanceof TempVariable;
  }

  private static class TempVariable extends LightVariableBuilder<TempVariable> {
    TempVariable(int index, @NotNull PsiType type, @NotNull PsiElement navigationElement) {
      super("tmp$" + index, type, navigationElement);
    }
  }

  public static class InlinedBlockContext {
    final PsiCodeBlock myCodeBlock;
    final boolean myForceNonNullBlockResult;
    final PsiVariable myTarget;

    public InlinedBlockContext(PsiCodeBlock codeBlock, boolean forceNonNullBlockResult, PsiVariable target) {
      myCodeBlock = codeBlock;
      myForceNonNullBlockResult = forceNonNullBlockResult;
      myTarget = target;
    }
  }

  static final CallInliner[] INLINERS = {new OptionalChainInliner(), new LambdaInliner(), new CollectionFactoryInliner(),
    new StreamChainInliner(), new MapUpdateInliner()};
}

