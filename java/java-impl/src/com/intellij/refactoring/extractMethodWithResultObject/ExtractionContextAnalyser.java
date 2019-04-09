// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author Pavel.Dolgov
 */
class ExtractionContextAnalyser extends JavaRecursiveElementWalkingVisitor {
  private final PsiElement[] myElements;
  private final PsiElement myCodeFragmentMember;
  private final PsiExpression myExpression;
  private final ControlFlowWrapper myControlFlow;
  private final ExtractionContext myContext;

  ExtractionContextAnalyser(@NotNull PsiElement[] elements,
                            @NotNull PsiElement codeFragmentMember,
                            @Nullable PsiExpression expression,
                            @NotNull ControlFlowWrapper controlFlow) {
    myElements = elements;
    myCodeFragmentMember = codeFragmentMember;
    myExpression = expression;
    myControlFlow = controlFlow;
    myContext = new ExtractionContext(controlFlow.getInputVariables());
  }

  @NotNull
  ExtractionContext createContext() {
    collectInputsAndOutputs();
    collectDeclaredInsideUsedAfter();
    myContext.myThrownCheckedExceptions.addAll(ExceptionUtil.getThrownCheckedExceptions(myElements));

    if (myExpression != null) {
      myContext.addExit(null, ExitType.EXPRESSION, myExpression);
    }
    else {
      addSequentialExit();
    }
    collectDistinctExits();
    collectResultItems();
    collectOutputVariableFlags();

    return myContext;
  }

  private void collectInputsAndOutputs() {
    // todo take care of surrounding try-catch
    ExtractionContextVisitor visitor = new ExtractionContextVisitor(myCodeFragmentMember);
    for (PsiElement element : myElements) {
      element.accept(visitor);
    }
  }

  private void collectDeclaredInsideUsedAfter() {
    Set<PsiLocalVariable> declaredInside = new HashSet<>();
    for (PsiElement element : myElements) {
      if (element instanceof PsiDeclarationStatement) {
        PsiElement[] declaredElements = ((PsiDeclarationStatement)element).getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) {
            declaredInside.add((PsiLocalVariable)declaredElement);
          }
        }
      }
    }

    if (!declaredInside.isEmpty()) {
      for (PsiElement next = myElements[myElements.length - 1].getNextSibling(); next != null; next = next.getNextSibling()) {
        next.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);

            if (expression.getQualifier() == null) {
              PsiElement resolved = expression.resolve();
              if (resolved instanceof PsiLocalVariable && declaredInside.contains(resolved)) {
                myContext.myOutputVariables.add((PsiLocalVariable)resolved);
              }
            }
          }
        });
      }
    }
  }

  private void addSequentialExit() {
    for (int i = myElements.length - 1; i >= 0; i--) {
      if (!(myElements[i] instanceof PsiStatement)) {
        continue; // skip comments and white spaces
      }
      PsiStatement statement = (PsiStatement)myElements[i];
      if (!ControlFlowUtils.statementMayCompleteNormally(statement)) {
        return; // there's no sequential exit
      }
      break;
    }

    PsiElement exitedElement = ExtractUtil.findOutermostExitedElement(myElements[myElements.length - 1], myCodeFragmentMember);
    if (exitedElement != null) {
      myContext.addExit(null, ExitType.SEQUENTIAL, exitedElement);
    }
    else {
      myContext.addExit(null, ExitType.UNDEFINED, null);
    }
  }

  void collectDistinctExits() {
    EntryStream.of(myContext.myExits)
      .mapKeys(s -> s != null ? s.getTextOffset() : 0)
      .sorted(Comparator.comparing((Function<Map.Entry<Integer, Exit>, Integer>)Map.Entry::getKey)
                .thenComparing(e -> e.getValue().getType()))
      .forKeyValue((offset, exit) -> {
        if (!myContext.myDistinctExits.containsKey(exit)) {
          myContext.myDistinctExits.put(exit, myContext.myDistinctExits.size());
        }
      });
  }

  private void collectResultItems() {
    if (myExpression != null) {
      assert myContext.myExits.size() == 1 : "one exit with expression";
      PsiType expressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(myExpression);
      if (expressionType == null) {
        expressionType = PsiType.getJavaLangObject(myExpression.getManager(),
                                                   GlobalSearchScope.allScope(myExpression.getProject()));
      }
      myContext.myResultItems.add(new ResultItem.Expression(myExpression, expressionType));
      return;
    }

    if (myContext.myDistinctExits.size() > 1) {
      myContext.myResultItems.add(new ResultItem.ExitKey());
    }

    List<Exit> returnExits = ContainerUtil.filter(myContext.myExits.values(), exit -> ExitType.RETURN.equals(exit.getType()));
    if (!returnExits.isEmpty()) {
      PsiType returnType = null;
      PsiTypeElement returnTypeElement = null;
      if (myCodeFragmentMember instanceof PsiMethod) {
        returnType = ((PsiMethod)myCodeFragmentMember).getReturnType();
        returnTypeElement = ((PsiMethod)myCodeFragmentMember).getReturnTypeElement();
      }
      else if (myCodeFragmentMember instanceof PsiLambdaExpression) {
        returnType = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)myCodeFragmentMember);
      }
      if (returnType == null) {
        returnType = PsiType.getJavaLangObject(myCodeFragmentMember.getManager(),
                                               GlobalSearchScope.allScope(myCodeFragmentMember.getProject()));
      }
      if (!PsiType.VOID.equals(returnType)) {
        myContext.myResultItems.add(new ResultItem.Return(returnType, returnTypeElement));
      }
    }

    StreamEx.of(myContext.myOutputVariables)
      .mapToEntry(PsiNamedElement::getName, v -> v)
      .filterKeys(Objects::nonNull)
      .sorted(Comparator.comparing(
        (Map.Entry<String, PsiVariable> e) ->
          (e.getValue() instanceof PsiLocalVariable || e.getValue() instanceof PsiParameter) ? e.getValue().getTextOffset() : 0)
                .thenComparing(e -> e.getKey()))
      .forKeyValue((name, variable) -> myContext.myResultItems.add(new ResultItem.Variable(variable, name)));
  }

  private void processReferenceToVariable(@NotNull PsiReferenceExpression expression, @NotNull PsiVariable variable) {
    PsiAssignmentExpression assignment = getAssignmentOf(expression);
    if (assignment != null) {
      if (assignment.getOperationTokenType() != JavaTokenType.EQ) {
        processPossibleInput(expression, variable);
      }
      processPossibleOutput(variable);
    }
    else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      processPossibleInput(expression, variable);
      processPossibleOutput(variable);
    }
    else {
      processPossibleInput(expression, variable);
    }
  }

  private void processPossibleInput(PsiReferenceExpression expression, PsiVariable variable) {
    Input input = myContext.myInputs.get(variable);
    if (input != null) {
      input.addOccurrence(expression);
    }
  }

  private void processPossibleOutput(PsiVariable variable) {
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
      if (PsiTreeUtil.isAncestor(myCodeFragmentMember, variable, true)) {
        myContext.myOutputVariables.add(variable);
        if (isOutside(variable)) {
          myContext.myWrittenOuterVariables.add(variable);
        }
      }
    }
    else if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.FINAL)) {
      if (myCodeFragmentMember.getParent() != null && myCodeFragmentMember.getParent() == variable.getParent()) {
        myContext.myOutputVariables.add(variable);
      }
    }
  }

  private boolean isOutside(@NotNull PsiElement element) {
    PsiElement context = ExtractUtil.findContext(element, myCodeFragmentMember, myElements);
    return context == myCodeFragmentMember;
  }

  @Nullable
  private static PsiAssignmentExpression getAssignmentOf(@NotNull PsiReferenceExpression expression) {
    PsiElement element = expression;
    while (element.getParent() instanceof PsiParenthesizedExpression) {
      element = element.getParent();
    }
    PsiElement parent = element.getParent();
    if (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getLExpression() == element) {
      return (PsiAssignmentExpression)parent;
    }
    return null;
  }


  private void collectOutputVariableFlags() {
    myContext.myExits.forEach((statement, exit) -> {
      Output output = new Output();
      myContext.myOutputs.put(statement, output);
      PsiElement exitElement = translateExit(statement);
      Set<PsiVariable> definitelyWrittenVariables = myControlFlow.getDefinitelyWrittenVariables(exitElement, exitElement == statement);
      for (PsiVariable variable : myContext.myOutputVariables) {
        Output.Flags flags = new Output.Flags();
        output.myFlags.put(variable, flags);
        flags.isVisibleAtExit = isVisibleAt(variable, statement);
        flags.isValueUsedAfterExit = definitelyWrittenVariables.contains(variable);
      }
    });
  }

  @NotNull
  private PsiElement translateExit(@Nullable PsiElement exitElement) {
    if (exitElement == null) { // sequential or expression exit
      return myElements[myElements.length - 1];
    }
    PsiExpression expression = null;
    if (exitElement instanceof PsiReturnStatement) {
      expression = ((PsiReturnStatement)exitElement).getReturnValue();
    }
    else if (exitElement instanceof PsiBreakStatement) {
      expression = ((PsiBreakStatement)exitElement).getValueExpression();
    }
    return expression != null ? expression : exitElement;
  }

  private boolean isVisibleAt(PsiVariable variable, PsiElement exitStatement) {
    if (exitStatement == null) {
      return true;
    }
    PsiElement variableContext = ExtractUtil.findContext(variable, myCodeFragmentMember, myElements);
    if (variableContext == null || variableContext == myCodeFragmentMember) {
      return true;
    }
    PsiElement exitContext = ExtractUtil.findContext(exitStatement, myCodeFragmentMember, myElements);
    assert exitContext != null && exitContext != myCodeFragmentMember : "exitContext";
    for (PsiElement element = exitContext; element != null; element = element.getPrevSibling()) {
      if (element == variableContext) {
        return true;
      }
    }
    return false;
  }

  private class ExtractionContextVisitor extends ExitStatementsVisitor {
    ExtractionContextVisitor(PsiElement topmostElement) {
      super(topmostElement);
    }

    @Override
    protected void processReferenceExpression(PsiReferenceExpression expression) {
      PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiQualifiedExpression) {
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiVariable) {
          processReferenceToVariable(expression, (PsiVariable)resolved);
        }
      }
    }

    @Override
    protected void processReturnExit(PsiReturnStatement statement) {
      myContext.addExit(statement, ExitType.RETURN, myCodeFragmentMember);
    }

    @Override
    protected void processContinueExit(PsiContinueStatement statement) {
      PsiStatement continuedStatement = statement.findContinuedStatement();
      if (continuedStatement instanceof PsiLoopStatement && isOutside(continuedStatement)) {
        myContext.addExit(statement, ExitType.CONTINUE, ((PsiLoopStatement)continuedStatement).getBody());
      }
    }

    @Override
    protected void processBreakExit(PsiBreakStatement statement) {
      PsiElement exitedElement = statement.findExitedElement();
      if (exitedElement != null && isOutside(exitedElement)) {
        PsiElement outermostExited = ExtractUtil.findOutermostExitedElement(statement, myCodeFragmentMember);
        myContext.addExit(statement, ExitType.BREAK, outermostExited != null ? outermostExited : exitedElement);
      }
    }

    @Override
    protected void processThrowExit(PsiThrowStatement statement) {
      PsiTryStatement throwTarget = ExtractUtil.findThrowTarget(statement, myCodeFragmentMember);
      if (throwTarget == null) {
        myContext.addExit(statement, ExitType.THROW, myCodeFragmentMember);
      }
      else if (isOutside(throwTarget)) {
        myContext.addExit(statement, ExitType.THROW, throwTarget);
      }
    }
  }
}
