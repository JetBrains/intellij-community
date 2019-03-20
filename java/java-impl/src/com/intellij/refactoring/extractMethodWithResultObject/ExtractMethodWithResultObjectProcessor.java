// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author Pavel.Dolgov
 */
public class ExtractMethodWithResultObjectProcessor {
  private static final Logger LOG = Logger.getInstance(ExtractMethodWithResultObjectProcessor.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiElement[] myElements;
  private final PsiExpression myExpression;
  private final PsiElement myCodeFragmentMember;
  private final Map<PsiReferenceExpression, PsiVariable> myInputs = new HashMap<>();
  private final Set<PsiVariable> myOutputVariables = new HashSet<>();
  private final Map<PsiStatement, Exit> myExits = new HashMap<>();

  private PsiClass myTargetClass;
  private PsiElement myAnchor;

  @NonNls static final String REFACTORING_NAME = "Extract Method With Result Object";

  private enum ExitType {
    EXPRESSION,
    RETURN,
    BREAK,
    CONTINUE,
    THROW, // todo
    SEQUENTIAL,
    UNDEFINED
  }

  private static class Exit {
    final ExitType myType;
    final PsiElement myExitedElement;

    private Exit(ExitType type, PsiElement element) {
      myType = type;
      myExitedElement = element;
    }

    @Override
    public String toString() {
      return myExitedElement != null ? myType + " " + myExitedElement : myType.toString();
    }
  }

  public ExtractMethodWithResultObjectProcessor(@NonNls Project project, @NonNls Editor editor, @NonNls PsiElement[] elements) {
    if (elements.length == 0) {
      throw new IllegalArgumentException("elements.length");
    }
    myProject = project;
    myEditor = editor;

    PsiExpression expression = null;
    if (elements[0] instanceof PsiExpression) {
      expression = (PsiExpression)elements[0];
      if (expression.getParent() instanceof PsiExpressionStatement) {
        elements = new PsiElement[]{expression.getParent()};
        expression = null;
      }
    }

    myElements = elements;
    myExpression = expression;

    PsiElement codeFragment = ControlFlowUtil.findCodeFragment(elements[0]);
    myCodeFragmentMember = getCodeFragmentMember(codeFragment);
  }

  Project getProject() {
    return myProject;
  }

  Editor getEditor() {
    return myEditor;
  }

  private static PsiElement getCodeFragmentMember(@NotNull PsiElement codeFragment) {
    PsiElement codeFragmentMember = codeFragment.getUserData(ElementToWorkOn.PARENT);
    if (codeFragmentMember == null) {
      codeFragmentMember = codeFragment.getParent();
    }
    if (codeFragmentMember == null) {
      PsiElement context = codeFragment.getContext();
      LOG.assertTrue(context != null, "code fragment context is null");
      codeFragmentMember = ControlFlowUtil.findCodeFragment(context).getParent();
    }
    return codeFragmentMember;
  }

  public boolean prepare() {
    LOG.info("prepare");
    if (myElements.length == 0) {
      return false;
    }

    collectInputsAndOutputs();
    collectDeclaredInsideUsedAfter();
    collectReturnedImmediatelyAfter();

    chooseTargetClass();
    chooseAnchor();

    return true;
  }

  private void collectInputsAndOutputs() {
    // todo take care of surrounding try-catch
    JavaRecursiveElementWalkingVisitor elementsVisitor = new JavaRecursiveElementWalkingVisitor() {
      private boolean myExpressionsOnly;

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);

        PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiQualifiedExpression) {
          PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiVariable) {
            PsiVariable variable = (PsiVariable)resolved;
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
        }
      }

      private PsiAssignmentExpression getAssignmentOf(PsiReferenceExpression expression) {
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

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        super.visitReturnStatement(statement);

        if (!myExpressionsOnly) {
          myExits.put(statement, new Exit(ExitType.RETURN, myCodeFragmentMember));
        }
      }

      @Override
      public void visitContinueStatement(PsiContinueStatement statement) {
        super.visitContinueStatement(statement);

        if (!myExpressionsOnly) {
          PsiStatement continuedStatement = statement.findContinuedStatement();
          if (continuedStatement != null && !isInside(continuedStatement)) {
            myExits.put(statement, new Exit(ExitType.CONTINUE, continuedStatement));
          }
        }
      }

      @Override
      public void visitBreakStatement(PsiBreakStatement statement) {
        super.visitBreakStatement(statement);

        if (!myExpressionsOnly) {
          PsiElement exitedElement = statement.findExitedElement();
          if (exitedElement != null && !isInside(exitedElement)) {
            myExits.put(statement, new Exit(ExitType.BREAK, exitedElement));
          }
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {
        myExpressionsOnly = true;
        try {
          super.visitClass(aClass);
        }
        finally {
          myExpressionsOnly = false;
        }
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        myExpressionsOnly = true;
        try {
          super.visitLambdaExpression(expression);
        }
        finally {
          myExpressionsOnly = false;
        }
      }
    };

    for (PsiElement element : myElements) {
      element.accept(elementsVisitor);
    }
    if (myExpression != null) {
      myExits.put(null, new Exit(ExitType.EXPRESSION, myExpression));
    }
  }

  private void collectDeclaredInsideUsedAfter() {
    if (myExpression != null) {
      return;
    }
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
                myOutputVariables.add((PsiLocalVariable)resolved);
              }
            }
          }
        });
      }
    }
  }

  private static PsiStatement findNextStatement(@Nullable PsiElement startLocation) {
    PsiElement location = startLocation;
    while (true) {
      if (location == null || location instanceof PsiParameterListOwner) {
        return null;
      }

      if (location instanceof PsiBreakStatement) {
        location = ((PsiBreakStatement)location).findExitedElement();
        continue;
      }
      if (location instanceof PsiContinueStatement) {
        PsiStatement continued = ((PsiContinueStatement)location).findContinuedStatement();
        if (continued instanceof PsiLoopStatement && !ControlFlowUtils.statementMayCompleteNormally(continued)) {
          return null;
        }
        location = continued;
        continue;
      }
      if (location instanceof PsiThrowStatement) {
        // todo find target
      }
      if (location instanceof PsiSwitchLabelStatementBase) {
        location = ((PsiSwitchLabelStatementBase)location).getEnclosingSwitchBlock();
        continue;
      }

      PsiElement parent = location.getParent();
      if (parent instanceof PsiLoopStatement && !ControlFlowUtils.statementMayCompleteNormally((PsiLoopStatement)parent)) {
        return null;
      }
      if (parent instanceof PsiCodeBlock) {
        PsiStatement next = PsiTreeUtil.getNextSiblingOfType(location, PsiStatement.class);
        if (next != null) {
          return next;
        }
      }
      location = parent;
    }
  }

  private void collectReturnedImmediatelyAfter() {
    if (myExpression != null) {
      return;
    }
    for (int i = myElements.length - 1; i >= 0; i--) {
      if (!(myElements[i] instanceof PsiStatement)) {
        continue; // skip comments and white spaces
      }
      PsiStatement statement = (PsiStatement)myElements[i];
      if (!ControlFlowUtils.statementMayCompleteNormally(statement)) {
        return; // don't need to look for the next statement
      }
      break;
    }

    PsiStatement nextStatement = findNextStatement(myElements[myElements.length - 1]);
    if (nextStatement != null) {
      PsiStatement exitedStatement = PsiTreeUtil.getPrevSiblingOfType(nextStatement, PsiStatement.class);
      myExits.put(null, new Exit(ExitType.SEQUENTIAL, exitedStatement));
    }
  }

  private void processPossibleOutput(PsiVariable variable) {
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
      if (PsiTreeUtil.isAncestor(myCodeFragmentMember, variable, true)) {
        myOutputVariables.add(variable);
      }
    }
    else if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.FINAL)) {
      if (myCodeFragmentMember.getParent() != null && myCodeFragmentMember.getParent() == variable.getParent()) {
        myOutputVariables.add(variable);
      }
    }
  }

  private void processPossibleInput(PsiReferenceExpression expression, PsiVariable variable) {
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
      if (!isInside(variable)) {
        myInputs.put(expression, variable);
      }
    }
  }

  private boolean isInside(@NotNull PsiElement element) {
    PsiElement context = PsiTreeUtil.findFirstContext(element, false,
                                                      e -> e == myCodeFragmentMember || ArrayUtil.find(myElements, e) >= 0);
    return context != myCodeFragmentMember;
  }

  boolean showDialog() {
    LOG.info("showDialog");
    return true;
  }

  void doRefactoring() {
    LOG.warn("doRefactoring");

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    dumpTexts(myExits.entrySet().stream().map(ExtractMethodWithResultObjectProcessor::getExitText), factory, "exit");
    dumpElements(myOutputVariables, factory, "out");
    dumpElements(new HashSet<>(myInputs.values()), factory, "in");
    dumpText(factory, "ins and outs");
  }

  private void dumpElements(Collection<? extends PsiElement> elements, PsiElementFactory factory, String prefix) {
    dumpTexts(elements.stream().map(PsiElement::toString), factory, prefix);
  }

  private void dumpTexts(Stream<String> elements, PsiElementFactory factory, String prefix) {
    elements
      .sorted(Comparator.reverseOrder())
      .forEach(text -> dumpText(factory, prefix + ": " + text));
  }

  private void dumpText(PsiElementFactory factory, String text) {
    PsiElement comment = factory.createCommentFromText("//" + text, null);
    myAnchor.getParent().addAfter(comment, myAnchor);
    LOG.warn(text);
  }

  private static String getExitText(Map.Entry<PsiStatement, Exit> e) {
    Exit exit = e.getValue();
    PsiStatement statement = e.getKey();
    if (statement instanceof PsiReturnStatement) {
      PsiExpression value = ((PsiReturnStatement)statement).getReturnValue();
      return exit + "<-" + (value != null ? value.toString() : PsiKeyword.VOID);
    }
    if (statement != null) {
      return exit + "<-" + statement;
    }
    return exit.toString();
  }

  private void chooseTargetClass() {
    myTargetClass = myCodeFragmentMember instanceof PsiMember
                    ? ((PsiMember)myCodeFragmentMember).getContainingClass()
                    : PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiClass.class);
  }

  private void chooseAnchor() {
    myAnchor = myCodeFragmentMember;
    while (!myAnchor.getParent().equals(myTargetClass)) {
      myAnchor = myAnchor.getParent();
    }
  }
}
