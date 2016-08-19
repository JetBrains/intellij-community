/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ControlFlowWrapper {
  private static final Logger LOG = Logger.getInstance("#" + ControlFlowWrapper.class.getName());

  private final ControlFlow myControlFlow;
  private final int myFlowStart;

  private final int myFlowEnd;
  private boolean myGenerateConditionalExit;
  private Collection<PsiStatement> myExitStatements;
  private PsiStatement myFirstExitStatementCopy;
  private IntArrayList myExitPoints;

  public ControlFlowWrapper(Project project, PsiElement codeFragment, PsiElement[] elements) throws PrepareFailedException {
    try {
      myControlFlow =
        ControlFlowFactory.getInstance(project).getControlFlow(codeFragment, new LocalsControlFlowPolicy(codeFragment), false, false);
    }
    catch (AnalysisCanceledException e) {
      throw new PrepareFailedException(RefactoringBundle.message("extract.method.control.flow.analysis.failed"), e.getErrorElement());
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(myControlFlow.toString());
    }

    int flowStart = -1;
    int index = 0;
    while (index < elements.length) {
      flowStart = myControlFlow.getStartOffset(elements[index]);
      if (flowStart >= 0) break;
      index++;
    }
    int flowEnd;
    if (flowStart < 0) {
      // no executable code
      flowStart = 0;
      flowEnd = 0;
    }
    else {
      index = elements.length - 1;
      while (true) {
        flowEnd = myControlFlow.getEndOffset(elements[index]);
        if (flowEnd >= 0) break;
        index--;
      }
    }
    myFlowStart = flowStart;
    myFlowEnd = flowEnd;
    if (LOG.isDebugEnabled()) {
      LOG.debug("start offset:" + myFlowStart);
      LOG.debug("end offset:" + myFlowEnd);
    }
  }

  public PsiStatement getFirstExitStatementCopy() {
    return myFirstExitStatementCopy;
  }

  public Collection<PsiStatement> prepareExitStatements(final PsiElement[] elements) throws ExitStatementsNotSameException {
    myExitPoints = new IntArrayList();
    myExitStatements = ControlFlowUtil
      .findExitPointsAndStatements(myControlFlow, myFlowStart, myFlowEnd, myExitPoints, ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
    if (LOG.isDebugEnabled()) {
      LOG.debug("exit points:");
      for (int i = 0; i < myExitPoints.size(); i++) {
        LOG.debug("  " + myExitPoints.get(i));
      }
      LOG.debug("exit statements:");
      for (PsiStatement exitStatement : myExitStatements) {
        LOG.debug("  " + exitStatement);
      }
    }
    if (myExitPoints.isEmpty()) {
      // if the fragment never exits assume as if it exits in the end
      myExitPoints.add(myControlFlow.getEndOffset(elements[elements.length - 1]));
    }

    if (myExitPoints.size() != 1) {
      myGenerateConditionalExit = true;
      areExitStatementsTheSame();
    }
    return myExitStatements;
  }


  private void areExitStatementsTheSame() throws ExitStatementsNotSameException {
    if (myExitStatements.isEmpty()) {
      throw new ExitStatementsNotSameException();
    }
    PsiStatement first = null;
    for (PsiStatement statement : myExitStatements) {
      if (first == null) {
        first = statement;
        continue;
      }
      if (!PsiEquivalenceUtil.areElementsEquivalent(first, statement)) {
        throw new ExitStatementsNotSameException();
      }
    }

    myFirstExitStatementCopy = (PsiStatement)first.copy();
  }

  public boolean isGenerateConditionalExit() {
    return myGenerateConditionalExit;
  }

  public Collection<PsiStatement> getExitStatements() {
    return myExitStatements;
  }

  public static class ExitStatementsNotSameException extends Exception {}


  @NotNull
  public PsiVariable[] getOutputVariables() {
    return getOutputVariables(myGenerateConditionalExit);
  }

  @NotNull
  public PsiVariable[] getOutputVariables(boolean collectVariablesAtExitPoints) {
    PsiVariable[] myOutputVariables = ControlFlowUtil.getOutputVariables(myControlFlow, myFlowStart, myFlowEnd, myExitPoints.toArray());
    if (collectVariablesAtExitPoints) {
      //variables declared in selected block used in return statements are to be considered output variables when extracting guard methods
      final Set<PsiVariable> outputVariables = new HashSet<>(Arrays.asList(myOutputVariables));
      for (PsiStatement statement : myExitStatements) {
        statement.accept(new JavaRecursiveElementVisitor() {

          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiVariable) {
              final PsiVariable variable = (PsiVariable)resolved;
              if (isWrittenInside(variable)) {
                outputVariables.add(variable);
              }
            }
          }

          private boolean isWrittenInside(final PsiVariable variable) {
            final List<Instruction> instructions = myControlFlow.getInstructions();
            for (int i = myFlowStart; i < myFlowEnd; i++) {
              Instruction instruction = instructions.get(i);
              if (instruction instanceof WriteVariableInstruction && variable.equals(((WriteVariableInstruction)instruction).variable)) {
                return true;
              }
            }

            return false;
          }
        });
      }

      myOutputVariables = outputVariables.toArray(new PsiVariable[outputVariables.size()]);
    }
    Arrays.sort(myOutputVariables, PsiUtil.BY_POSITION);
    return myOutputVariables;
  }

  public boolean isReturnPresentBetween() {
    return ControlFlowUtil.returnPresentBetween(myControlFlow, myFlowStart, myFlowEnd);
  }

  private void removeParametersUsedInExitsOnly(PsiElement codeFragment, List<PsiVariable> inputVariables) {
    LocalSearchScope scope = new LocalSearchScope(codeFragment);
    Variables:
    for (Iterator<PsiVariable> iterator = inputVariables.iterator(); iterator.hasNext();) {
      PsiVariable variable = iterator.next();
      for (PsiReference ref : ReferencesSearch.search(variable, scope)) {
        PsiElement element = ref.getElement();
        int elementOffset = myControlFlow.getStartOffset(element);
        if (elementOffset >= myFlowStart && elementOffset <= myFlowEnd) {
          if (!isInExitStatements(element, myExitStatements)) continue Variables;
        }
        if (elementOffset == -1) { //references in local/anonymous classes should not be skipped
          final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
          if (psiClass != null) {
            final TextRange textRange = psiClass.getTextRange();
            if (myControlFlow.getElement(myFlowStart).getTextOffset() <= textRange.getStartOffset() &&
                textRange.getEndOffset() <= myControlFlow.getElement(myFlowEnd).getTextRange().getEndOffset()) {
              continue Variables;
            }
          }
        }
      }
      iterator.remove();
    }
  }


  private static boolean isInExitStatements(PsiElement element, Collection<PsiStatement> exitStatements) {
    for (PsiStatement exitStatement : exitStatements) {
      if (PsiTreeUtil.isAncestor(exitStatement, element, false)) return true;
    }
    return false;
  }

  private boolean needExitStatement(final PsiStatement exitStatement) {
    if (exitStatement instanceof PsiContinueStatement) {
      //IDEADEV-11748
      PsiStatement statement = ((PsiContinueStatement)exitStatement).findContinuedStatement();
      if (statement == null) return true;
      if (statement instanceof PsiLoopStatement) statement = ((PsiLoopStatement)statement).getBody();
      int endOffset = myControlFlow.getEndOffset(statement);
      return endOffset > myFlowEnd;
    }
    return true;
  }

  public List<PsiVariable> getInputVariables(final PsiElement codeFragment, PsiElement[] elements, PsiVariable[] outputVariables) {
    final List<PsiVariable> inputVariables = ControlFlowUtil.getInputVariables(myControlFlow, myFlowStart, myFlowEnd);
    List<PsiVariable> myInputVariables;
    if (skipVariablesFromExitStatements(outputVariables)) {
      List<PsiVariable> inputVariableList = new ArrayList<>(inputVariables);
      removeParametersUsedInExitsOnly(codeFragment, inputVariableList);
      myInputVariables = inputVariableList;
    }
    else {
      List<PsiVariable> inputVariableList = new ArrayList<>(inputVariables);
      for (Iterator<PsiVariable> iterator = inputVariableList.iterator(); iterator.hasNext(); ) {
        PsiVariable variable = iterator.next();
        for (PsiElement element : elements) {
          if (PsiTreeUtil.isAncestor(element, variable, false)) {
            iterator.remove();
            break;
          }
        }
      }
      myInputVariables = inputVariableList;
    }
    //varargs variables go last, otherwise order is induced by original ordering
    Collections.sort(myInputVariables, (v1, v2) -> {
      if (v1.getType() instanceof PsiEllipsisType) {
        return 1;
      }
      if (v2.getType() instanceof PsiEllipsisType) {
        return -1;
      }
      return v1.getTextOffset() - v2.getTextOffset();
    });
    return myInputVariables;
  }

  public PsiStatement getExitStatementCopy(PsiElement returnStatement,
                                           final PsiElement[] elements) {
    PsiStatement exitStatementCopy = null;
    // replace all exit-statements such as break's or continue's with appropriate return
    for (PsiStatement exitStatement : myExitStatements) {
      if (exitStatement instanceof PsiReturnStatement) {
        if (!myGenerateConditionalExit) continue;
      }
      else if (exitStatement instanceof PsiBreakStatement) {
        PsiStatement statement = ((PsiBreakStatement)exitStatement).findExitedStatement();
        if (statement == null) continue;
        int startOffset = myControlFlow.getStartOffset(statement);
        int endOffset = myControlFlow.getEndOffset(statement);
        if (myFlowStart <= startOffset && endOffset <= myFlowEnd) continue;
      }
      else if (exitStatement instanceof PsiContinueStatement) {
        PsiStatement statement = ((PsiContinueStatement)exitStatement).findContinuedStatement();
        if (statement == null) continue;
        int startOffset = myControlFlow.getStartOffset(statement);
        int endOffset = myControlFlow.getEndOffset(statement);
        if (myFlowStart <= startOffset && endOffset <= myFlowEnd) continue;
      }
      else {
        LOG.error(String.valueOf(exitStatement));
        continue;
      }

      int index = -1;
      for (int j = 0; j < elements.length; j++) {
        if (exitStatement.equals(elements[j])) {
          index = j;
          break;
        }
      }
      if (exitStatementCopy == null) {
        if (needExitStatement(exitStatement)) {
          exitStatementCopy = (PsiStatement)exitStatement.copy();
        }
      }
      PsiElement result = exitStatement.replace(returnStatement);
      if (index >= 0) {
        elements[index] = result;
      }
    }
    return exitStatementCopy;
  }

  public List<PsiVariable> getUsedVariables(int start) {
    return getUsedVariables(start, myControlFlow.getSize());
  }

  public List<PsiVariable> getUsedVariables(int start, int end) {
    return ControlFlowUtil.getUsedVariables(myControlFlow, start, end);
  }

  public Collection<ControlFlowUtil.VariableInfo> getInitializedTwice(int start) {
    return ControlFlowUtil.getInitializedTwice(myControlFlow, start, myControlFlow.getSize());
  }

  public List<PsiVariable> getUsedVariables() {
    return getUsedVariables(myFlowEnd);
  }

  public List<PsiVariable> getUsedVariablesInBody(PsiElement codeFragment, PsiVariable[] outputVariables) {
    final List<PsiVariable> variables = getUsedVariables(myFlowStart, myFlowEnd);
    if (skipVariablesFromExitStatements(outputVariables)) {
      removeParametersUsedInExitsOnly(codeFragment, variables);
    }
    return variables;
  }

  private boolean skipVariablesFromExitStatements(PsiVariable[] outputVariables) {
    return myGenerateConditionalExit && outputVariables.length == 0;
  }

  public Collection<ControlFlowUtil.VariableInfo> getInitializedTwice() {
    return getInitializedTwice(myFlowEnd);
  }

  public void setGenerateConditionalExit(boolean generateConditionalExit) {
    myGenerateConditionalExit = generateConditionalExit;
  }
}
