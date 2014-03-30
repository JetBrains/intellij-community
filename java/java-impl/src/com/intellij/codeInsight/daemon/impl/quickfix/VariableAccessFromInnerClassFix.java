/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class VariableAccessFromInnerClassFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassFix");
  private final PsiVariable myVariable;
  private final PsiClass myClass;
  private final int myFixType;
  private static final int MAKE_FINAL = 0;
  private static final int MAKE_ARRAY = 1;
  private static final int COPY_TO_FINAL = 2;
  private static final Key<Map<PsiVariable,Boolean>>[] VARS = new Key[] {Key.create("VARS_TO_MAKE_FINAL"), Key.create("VARS_TO_TRANSFORM"), Key.create("???")};

  public VariableAccessFromInnerClassFix(@NotNull PsiVariable variable, @NotNull PsiClass aClass) {
    myVariable = variable;
    myClass = aClass;
    myFixType = getQuickFixType(variable);
    if (myFixType == -1) return;

    getVariablesToFix().add(variable);
  }

  @Override
  @NotNull
  public String getText() {
    @NonNls String message;
    switch (myFixType) {
      case MAKE_FINAL:
        message = "make.final.text";
        break;
      case MAKE_ARRAY:
        message = "make.final.transform.to.one.element.array";
        break;
      case COPY_TO_FINAL:
        return QuickFixBundle.message("make.final.copy.to.temp", myVariable.getName());
      default:
        return "";
    }
    Collection<PsiVariable> vars = getVariablesToFix();
    String varNames = vars.size() == 1 ? "'"+myVariable.getName()+"'" : "variables";
    return QuickFixBundle.message(message, varNames);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("make.final.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myClass.isValid() &&
           myClass.getManager().isInProject(myClass) &&
           myVariable.isValid() &&
           myFixType != -1 &&
           !getVariablesToFix().isEmpty() &&
           !inOwnInitializer(myVariable, myClass);
  }

  private static boolean inOwnInitializer(PsiVariable variable, PsiClass aClass) {
    return PsiTreeUtil.isAncestor(variable, aClass, false);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(myClass, myVariable)) return;
    try {
      switch (myFixType) {
        case MAKE_FINAL:
          makeFinal();
          break;
        case MAKE_ARRAY:
          makeArray();
          break;
        case COPY_TO_FINAL:
          copyToFinal();
          break;
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      getVariablesToFix().clear();
    }
  }

  private void makeArray() {
    for (PsiVariable var : getVariablesToFix()) {
      makeArray(var);
    }
  }

  @NotNull
  private Collection<PsiVariable> getVariablesToFix() {
    Map<PsiVariable, Boolean> vars = myClass.getUserData(VARS[myFixType]);
    if (vars == null) myClass.putUserData(VARS[myFixType], vars = new ConcurrentWeakHashMap<PsiVariable, Boolean>(1));
    final Map<PsiVariable, Boolean> finalVars = vars;
    return new AbstractCollection<PsiVariable>() {
      @Override
      public boolean add(PsiVariable psiVariable) {
        return finalVars.put(psiVariable, Boolean.TRUE) == null;
      }

      @NotNull
      @Override
      public Iterator<PsiVariable> iterator() {
        return finalVars.keySet().iterator();
      }

      @Override
      public int size() {
        return finalVars.size();
      }
    };
  }

  private void makeFinal() {
    for (PsiVariable var : getVariablesToFix()) {
      if (var.isValid()) {
        PsiUtil.setModifierProperty(var, PsiModifier.FINAL, true);
      }
    }
  }

  private void makeArray(PsiVariable variable) throws IncorrectOperationException {
    PsiType type = variable.getType();

    PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    PsiType newType = type.createArrayType();

    PsiDeclarationStatement variableDeclarationStatement;
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      String expression = "[1]";
      while (type instanceof PsiArrayType) {
        expression += "[1]";
        type = ((PsiArrayType) type).getComponentType();
      }
      PsiExpression init = factory.createExpressionFromText("new " + type.getCanonicalText() + expression, variable);
      variableDeclarationStatement = factory.createVariableDeclarationStatement(variable.getName(), newType, init);
    }
    else {
      PsiExpression init = factory.createExpressionFromText("{ " + initializer.getText() + " }", variable);
      variableDeclarationStatement = factory.createVariableDeclarationStatement(variable.getName(), newType, init);
    }
    PsiVariable newVariable = (PsiVariable)variableDeclarationStatement.getDeclaredElements()[0];
    PsiUtil.setModifierProperty(newVariable, PsiModifier.FINAL, true);
    PsiElement newExpression = factory.createExpressionFromText(variable.getName() + "[0]", variable);

    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return;
    List<PsiReferenceExpression> outerReferences = new ArrayList<PsiReferenceExpression>();
    collectReferences(outerCodeBlock, variable, outerReferences);
    replaceReferences(outerReferences, newExpression);
    variable.replace(newVariable);
  }

  private void copyToFinal() throws IncorrectOperationException {
    PsiManager psiManager = myClass.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    PsiExpression initializer = factory.createExpressionFromText(myVariable.getName(), myClass);
    String newName = suggestNewName(psiManager.getProject(), myVariable);
    PsiType type = myVariable.getType();
    PsiDeclarationStatement copyDecl = factory.createVariableDeclarationStatement(newName, type, initializer);
    PsiVariable newVariable = (PsiVariable)copyDecl.getDeclaredElements()[0];
    PsiUtil.setModifierProperty(newVariable, PsiModifier.FINAL, true);
    PsiElement statement = getStatementToInsertBefore();
    if (statement == null) return;
    PsiExpression newExpression = factory.createExpressionFromText(newName, myVariable);
    replaceReferences(myClass, myVariable, newExpression);
    if (RefactoringUtil.isLoopOrIf(statement.getParent())) {
      RefactoringUtil.putStatementInLoopBody(copyDecl, statement.getParent(), statement);
    } else {
      statement.getParent().addBefore(copyDecl, statement);
    }
  }

  private PsiElement getStatementToInsertBefore() {
    PsiElement declarationScope = myVariable instanceof PsiParameter
                                  ? ((PsiParameter)myVariable).getDeclarationScope() : PsiUtil.getVariableCodeBlock(myVariable, null);
    if (declarationScope == null) return null;

    PsiElement statement = myClass;
    nextInnerClass:
    do {
      statement = RefactoringUtil.getParentStatement(statement, false);

      if (statement == null || statement.getParent() == null) {
        return null;
      }
      PsiElement element = statement;
      while (element != declarationScope && !(element instanceof PsiFile)) {
        if (element instanceof PsiClass) {
          statement = statement.getParent();
          continue nextInnerClass;
        }
        element = element.getParent();
      }
      return statement;
    }
    while (true);
  }

  private static String suggestNewName(Project project, PsiVariable variable) {
    // new name should not conflict with another variable at the variable declaration level and usage level
    String name = variable.getName();
    // trim last digit to suggest variable names like i1,i2, i3...
    if (name.length() > 1 && Character.isDigit(name.charAt(name.length()-1))) {
      name = name.substring(0,name.length()-1);
    }
    name = "final" + StringUtil.capitalize(StringUtil.trimStart(name, "final"));
    return JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, variable, true);
  }


  private static void replaceReferences(PsiElement context, final PsiVariable variable, final PsiElement newExpression) {
    context.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable)
          try {
            expression.replace(newExpression);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static void replaceReferences(List<PsiReferenceExpression> references, PsiElement newExpression) throws IncorrectOperationException {
    for (PsiReferenceExpression reference : references) {
      reference.replace(newExpression);
    }
  }

  private static void collectReferences(PsiElement context, final PsiVariable variable, final List<PsiReferenceExpression> references) {
    context.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  private static int getQuickFixType(PsiVariable variable) {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    if (outerCodeBlock == null) return -1;
    List<PsiReferenceExpression> outerReferences = new ArrayList<PsiReferenceExpression>();
    collectReferences(outerCodeBlock, variable, outerReferences);

    int type = MAKE_FINAL;
    for (PsiReferenceExpression expression : outerReferences) {
      // if it happens that variable referenced from another inner class, make sure it can be make final from there
      PsiClass innerClass = HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, expression);

      if (innerClass != null) {
        int thisType = MAKE_FINAL;
        if (writtenInside(variable, innerClass)) {
          // cannot make parameter array
          if (variable instanceof PsiParameter) return -1;
          thisType = MAKE_ARRAY;
        }
        if (thisType == MAKE_FINAL
            && !canBeFinal(variable, outerReferences)) {
          thisType = COPY_TO_FINAL;
        }
        type = Math.max(type, thisType);
      }
    }
    return type;
  }

  private static boolean canBeFinal(PsiVariable variable, List<PsiReferenceExpression> references) {
    // if there is at least one assignment to this variable, it cannot be final
    Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems = new THashMap<PsiElement, Collection<PsiReferenceExpression>>();
    Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems = new THashMap<PsiElement, Collection<ControlFlowUtil.VariableInfo>>();
    for (PsiReferenceExpression expression : references) {
      if (ControlFlowUtil.isVariableAssignedInLoop(expression, variable)) return false;
      HighlightInfo highlightInfo = HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems,
                                                                                                 variable.getContainingFile());
      if (highlightInfo != null) return false;
      highlightInfo = HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo(variable, expression, finalVarProblems);
      if (highlightInfo != null) return false;
      if (variable instanceof PsiParameter && PsiUtil.isAccessedForWriting(expression)) return false;
    }
    return true;
  }

  private static boolean writtenInside(PsiVariable variable, PsiElement element) {
    if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
      PsiExpression lExpression = assignmentExpression.getLExpression();
      if (lExpression instanceof PsiReferenceExpression
          && ((PsiReferenceExpression) lExpression).resolve() == variable)
        return true;
    }
    else if (PsiUtil.isIncrementDecrementOperation(element)) {
      PsiElement operand = element instanceof PsiPostfixExpression ?
                           ((PsiPostfixExpression) element).getOperand() :
                           ((PsiPrefixExpression) element).getOperand();
      if (operand instanceof PsiReferenceExpression
          && ((PsiReferenceExpression) operand).resolve() == variable)
        return true;
    }
    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      if (writtenInside(variable, child)) return true;
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
