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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
  private final List<PsiExpression> myReturnValues = new ArrayList<>();

  private PsiClass myTargetClass;
  private PsiElement myAnchor;

  @NonNls static final String REFACTORING_NAME = "Extract Method With Result Object";

  public ExtractMethodWithResultObjectProcessor(@NonNls Project project, @NonNls Editor editor, @NonNls PsiElement[] elements) {
    myProject = project;
    myEditor = editor;

    PsiExpression expression = null;
    if (elements.length != 0 && elements[0] instanceof PsiExpression) {
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

    collectInputsAndOutputs();
    collectDeclaredInsideUsedAfter();

    chooseTargetClass();
    chooseAnchor();

    return true;
  }

  private void collectInputsAndOutputs() {
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
          PsiExpression returnValue = statement.getReturnValue();
          if (returnValue != null) {
            myReturnValues.add(returnValue);
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
      myReturnValues.add(myExpression);
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
                myOutputVariables.add((PsiLocalVariable)resolved);
              }
            }
          }
        });
      }
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
      PsiElement context = PsiTreeUtil.findFirstContext(variable, true,
                                                        e -> e == myCodeFragmentMember || ArrayUtil.find(myElements, e) >= 0);
      if (context == myCodeFragmentMember) {
        myInputs.put(expression, variable);
      }
    }
  }

  boolean showDialog() {
    LOG.info("showDialog");
    return true;
  }

  void doRefactoring() {
    LOG.warn("doRefactoring");

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    dumpElements(myReturnValues, factory, "out");
    dumpElements(myOutputVariables, factory, "out");
    dumpElements(new HashSet<>(myInputs.values()), factory, "in");
    dumpText(factory, "ins and outs");
  }

  private void dumpElements(Collection<? extends PsiElement> elements, PsiElementFactory factory, String prefix) {
    elements.stream()
      .map(PsiElement::toString)
      .sorted(Comparator.reverseOrder())
      .forEach(text -> dumpText(factory, prefix + ": " + text));
  }

  private void dumpText(PsiElementFactory factory, String text) {
    PsiElement comment = factory.createCommentFromText("//" + text, null);
    myAnchor.getParent().addAfter(comment, myAnchor);
    LOG.warn(text);
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
