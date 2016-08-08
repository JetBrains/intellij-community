/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author ven
 */
public class BringVariableIntoScopeFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeFix");
  private final PsiReferenceExpression myUnresolvedReference;
  private PsiLocalVariable myOutOfScopeVariable;

  public BringVariableIntoScopeFix(@NotNull PsiReferenceExpression unresolvedReference) {
    myUnresolvedReference = unresolvedReference;
  }

  @Override
  @NotNull
  public String getText() {
    PsiLocalVariable variable = myOutOfScopeVariable;

    String varText = variable == null ? "" : PsiFormatUtil.formatVariable(variable, PsiFormatUtilBase.SHOW_NAME |
                                                                                    PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
    return QuickFixBundle.message("bring.variable.to.scope.text", varText);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("bring.variable.to.scope.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (myUnresolvedReference.isQualified()) return false;
    final String referenceName = myUnresolvedReference.getReferenceName();
    if (referenceName == null) return false;

    PsiManager manager = file.getManager();
    if (!myUnresolvedReference.isValid() || !manager.isInProject(myUnresolvedReference)) return false;

    PsiElement container = PsiTreeUtil.getParentOfType(myUnresolvedReference, PsiCodeBlock.class, PsiClass.class);
    if (!(container instanceof PsiCodeBlock)) return false;

    myOutOfScopeVariable = null;
    while(container.getParent() instanceof PsiStatement || container.getParent() instanceof PsiCatchSection) container = container.getParent();
    container.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {}

      @Override public void visitExpression(PsiExpression expression) {
        //Don't look inside expressions
      }

      @Override public void visitLocalVariable(PsiLocalVariable variable) {
        if (referenceName.equals(variable.getName())) {
          if (myOutOfScopeVariable == null) {
            myOutOfScopeVariable = variable;
          }
          else {
            myOutOfScopeVariable = null; //2 conflict variables
          }
        }
      }
    });

    return myOutOfScopeVariable != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
    LOG.assertTrue(myOutOfScopeVariable != null);
    PsiManager manager = file.getManager();
    myOutOfScopeVariable.normalizeDeclaration();
    PsiUtil.setModifierProperty(myOutOfScopeVariable, PsiModifier.FINAL, false);
    PsiElement commonParent = PsiTreeUtil.findCommonParent(myOutOfScopeVariable, myUnresolvedReference);
    LOG.assertTrue(commonParent != null);
    PsiElement child = myOutOfScopeVariable.getTextRange().getStartOffset() < myUnresolvedReference.getTextRange().getStartOffset() ? myOutOfScopeVariable
                       : myUnresolvedReference;

    while(child.getParent() != commonParent) child = child.getParent();
    PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createStatementFromText("int i = 0", null);
    PsiVariable variable = (PsiVariable)newDeclaration.getDeclaredElements()[0].replace(myOutOfScopeVariable);
    if (variable.getInitializer() != null) {
      variable.getInitializer().delete();
    }

    while(!(child instanceof PsiStatement) || !(child.getParent() instanceof PsiCodeBlock)) {
      child = child.getParent();
      commonParent = commonParent.getParent();
    }
    LOG.assertTrue(commonParent != null);
    PsiDeclarationStatement added = (PsiDeclarationStatement)commonParent.addBefore(newDeclaration, child);
    final PsiElement[] declaredElements = added.getDeclaredElements();
    LOG.assertTrue(declaredElements.length > 0, added.getText());
    PsiLocalVariable addedVar = (PsiLocalVariable)declaredElements[0];
    assert addedVar != null : added;
    CodeStyleManager.getInstance(manager.getProject()).reformat(commonParent);

    //Leave initializer assignment
    PsiExpression initializer = myOutOfScopeVariable.getInitializer();
    if (initializer != null) {
      PsiExpressionStatement assignment = (PsiExpressionStatement)JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createStatementFromText(myOutOfScopeVariable
        .getName() + "= e;", null);
      ((PsiAssignmentExpression)assignment.getExpression()).getRExpression().replace(initializer);
      assignment = (PsiExpressionStatement)CodeStyleManager.getInstance(manager.getProject()).reformat(assignment);
      PsiDeclarationStatement declStatement = PsiTreeUtil.getParentOfType(myOutOfScopeVariable, PsiDeclarationStatement.class);
      LOG.assertTrue(declStatement != null);
      PsiElement parent = declStatement.getParent();
      if (parent instanceof PsiForStatement) {
        declStatement.replace(assignment);
      }
      else {
        parent.addAfter(assignment, declStatement);
      }
    }

    if (myOutOfScopeVariable.isValid()) {
      myOutOfScopeVariable.delete();
    }

    if (HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(myUnresolvedReference, addedVar, new THashMap<>(), file) != null) {
      initialize(addedVar);
    }

    DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
  }

  private static void initialize(final PsiLocalVariable variable) throws IncorrectOperationException {
    PsiType type = variable.getType();
    String init = PsiTypesUtil.getDefaultValueOfType(type);
    PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
    PsiExpression initializer = factory.createExpressionFromText(init, variable);
    variable.setInitializer(initializer);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
