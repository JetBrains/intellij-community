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

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DeferFinalAssignmentFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.DeferFinalAssignmentFix");

  private final PsiVariable variable;
  private final PsiReferenceExpression expression;

  public DeferFinalAssignmentFix(@NotNull PsiVariable variable, @NotNull PsiReferenceExpression expression) {
    this.variable = variable;
    this.expression = expression;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("defer.final.assignment.with.temp.family");
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("defer.final.assignment.with.temp.text", variable.getName());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(variable.getContainingFile())) return;

    if (variable instanceof PsiField) {
      deferField((PsiField)variable);
    }
    else {
      deferLocalVariable((PsiLocalVariable)variable);
    }
  }

  private void deferField(PsiField field) throws IncorrectOperationException {
    PsiCodeBlock codeBlock = getEnclosingCodeBlock(field, expression);
    if (codeBlock == null) return;
    deferVariable(codeBlock, field, null);
  }

  private static PsiCodeBlock getEnclosingCodeBlock(PsiField field, PsiElement element) {
    PsiClass aClass = field.getContainingClass();
    if (aClass == null) return null;
    PsiMethod[] constructors = aClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      if (PsiTreeUtil.isAncestor(body, element, true)) return body;
    }

    //maybe inside class initalizer ?
    PsiClassInitializer[] initializers = aClass.getInitializers();
    for (PsiClassInitializer initializer : initializers) {
      PsiCodeBlock body = initializer.getBody();
      if (PsiTreeUtil.isAncestor(body, element, true)) return body;
    }
    return null;
  }

  private void deferLocalVariable(PsiLocalVariable variable) throws IncorrectOperationException {
    PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
    deferVariable(outerCodeBlock, variable, variable.getParent());
  }

  private void deferVariable(PsiElement outerCodeBlock, PsiVariable variable, PsiElement tempDeclarationAnchor) throws IncorrectOperationException {
    if (outerCodeBlock == null) return;
    List<PsiReferenceExpression> outerReferences = new ArrayList<>();
    collectReferences(outerCodeBlock, variable, outerReferences);

    PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
    Project project = variable.getProject();
    String tempName = suggestNewName(project, variable);
    PsiDeclarationStatement tempVariableDeclaration = factory.createVariableDeclarationStatement(tempName, variable.getType(), null);

    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(outerCodeBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
    }
    catch (AnalysisCanceledException e) {
      return;
    }
    int minOffset = 0;
    boolean writeReferenceOccurred = false;
    PsiReferenceExpression writeReference = null;
    for (int i = outerReferences.size()-1; i>=0; i--) {
      PsiReferenceExpression reference = outerReferences.get(i);
      if (!writeReferenceOccurred && !PsiUtil.isAccessedForWriting(reference)) {
        // trailing read references need not be converted to temp var references
        outerReferences.remove(i);
        continue;
      }
      writeReferenceOccurred = true;
      writeReference = reference;
      PsiElement element = PsiUtil.getEnclosingStatement(reference);
      int endOffset = element == null ? -1 : controlFlow.getEndOffset(element);
      minOffset = Math.max(minOffset, endOffset);
    }
    LOG.assertTrue(writeReference != null);
    PsiStatement finalAssignment = factory.createStatementFromText(writeReference.getText()+" = "+tempName+";", outerCodeBlock);
    if (!insertToDefinitelyReachedPlace(outerCodeBlock, finalAssignment, controlFlow, minOffset, outerReferences)) return;

    outerCodeBlock.addAfter(tempVariableDeclaration, tempDeclarationAnchor);

    replaceReferences(outerReferences, factory.createExpressionFromText(tempName, outerCodeBlock));
  }


  private static boolean insertToDefinitelyReachedPlace(PsiElement codeBlock,
                                                        PsiStatement finalAssignment,
                                                        ControlFlow controlFlow,
                                                        int minOffset,
                                                        List references) throws IncorrectOperationException {
    int offset = ControlFlowUtil.getMinDefinitelyReachedOffset(controlFlow, minOffset, references);
    if (offset == controlFlow.getSize()) {
      codeBlock.add(finalAssignment);
      return true;
    }
    PsiElement element = null; //controlFlow.getEndOffset(codeBlock) == offset ? getEnclosingStatement(controlFlow.getElement(offset)) : null;
    while (offset < controlFlow.getSize()) {
      element = controlFlow.getElement(offset);
      if (element != null) element = PsiUtil.getEnclosingStatement(element);
      int startOffset = controlFlow.getStartOffset(element);
      if (startOffset != -1 && startOffset >= minOffset && element instanceof PsiStatement) break;
      offset++;
    }
    if (!(offset < controlFlow.getSize())) return false;
    // inside loop
    if (ControlFlowUtil.isInstructionReachable(controlFlow, offset, offset)) return false;
    codeBlock.addBefore(finalAssignment, element);
    return true;
  }

  private static void replaceReferences(List references, PsiElement newExpression) throws IncorrectOperationException {
    for (Object reference1 : references) {
      PsiElement reference = (PsiElement)reference1;
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

  private static String suggestNewName(Project project, PsiVariable variable) {
    // new name should not conflict with another variable at the variable declaration level and usage level
    String name = variable.getName();
    // trim last digit to suggest variable names like i1,i2, i3...
    if (name.length() > 1 && Character.isDigit(name.charAt(name.length()-1))) {
      name = name.substring(0,name.length()-1);
    }
    return JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(name, variable, true);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      variable.isValid() &&
      !(variable instanceof PsiParameter) &&
      !(variable instanceof ImplicitVariable) &&
      expression.isValid() &&
      variable.getManager().isInProject(variable)
        ;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
