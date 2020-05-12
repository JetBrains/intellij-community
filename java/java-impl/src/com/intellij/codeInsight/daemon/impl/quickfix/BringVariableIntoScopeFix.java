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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author ven
 */
public final class BringVariableIntoScopeFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(BringVariableIntoScopeFix.class);
  private final @NotNull PsiReferenceExpression myUnresolvedReference;
  private final @NotNull PsiLocalVariable myOutOfScopeVariable;

  private BringVariableIntoScopeFix(@NotNull PsiReferenceExpression unresolvedReference, @NotNull PsiLocalVariable variable) {
    myUnresolvedReference = unresolvedReference;
    myOutOfScopeVariable = variable;
  }

  static @Nullable BringVariableIntoScopeFix fromReference(PsiReferenceExpression unresolvedReference) {
    if (unresolvedReference.isQualified()) return null;
    final String referenceName = unresolvedReference.getReferenceName();
    if (referenceName == null) return null;

    PsiElement container = PsiTreeUtil.getParentOfType(unresolvedReference, PsiCodeBlock.class, PsiClass.class);
    if (!(container instanceof PsiCodeBlock)) return null;
    while(container.getParent() instanceof PsiStatement || container.getParent() instanceof PsiCatchSection) container = container.getParent();

    class Visitor extends JavaRecursiveElementWalkingVisitor {
      int variableCount = 0;
      PsiLocalVariable myOutOfScopeVariable;

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {}

      @Override
      public void visitExpression(PsiExpression expression) {
        //Don't look inside expressions
      }

      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        if (referenceName.equals(variable.getName())) {
          myOutOfScopeVariable = variable;
          variableCount++;
          if (variableCount > 1) {
            stopWalking();
          }
        }
      }
    }
    Visitor visitor = new Visitor();
    container.accept(visitor);

    if (visitor.variableCount != 1 || visitor.myOutOfScopeVariable instanceof PsiResourceVariable) return null;
    return new BringVariableIntoScopeFix(unresolvedReference, visitor.myOutOfScopeVariable);
  }

  @Override
  @NotNull
  public String getText() {
    PsiLocalVariable variable = myOutOfScopeVariable;

    String varText = !variable.isValid()
                     ? "" : PsiFormatUtil.formatVariable(variable, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
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
    return myUnresolvedReference.isValid() && BaseIntentionAction.canModify(myUnresolvedReference) && myOutOfScopeVariable.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiFile file) throws IncorrectOperationException {
    PsiLocalVariable outOfScopeVariable = myOutOfScopeVariable;
    LOG.assertTrue(outOfScopeVariable != null);
    PsiManager manager = file.getManager();
    outOfScopeVariable.normalizeDeclaration();
    PsiUtil.setModifierProperty(outOfScopeVariable, PsiModifier.FINAL, false);
    PsiElement commonParent = PsiTreeUtil.findCommonParent(outOfScopeVariable, myUnresolvedReference);
    LOG.assertTrue(commonParent != null);
    PsiElement child = outOfScopeVariable.getTextRange().getStartOffset() < myUnresolvedReference.getTextRange().getStartOffset() ? 
                       outOfScopeVariable : myUnresolvedReference;

    while(child.getParent() != commonParent) child = child.getParent();
    PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)JavaPsiFacade.getElementFactory(manager.getProject()).createStatementFromText("int i = 0", null);
    PsiVariable variable = (PsiVariable)newDeclaration.getDeclaredElements()[0].replace(outOfScopeVariable);
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
    PsiExpression initializer = outOfScopeVariable.getInitializer();
    if (initializer != null) {
      PsiExpressionStatement assignment = (PsiExpressionStatement)JavaPsiFacade.getElementFactory(manager.getProject()).createStatementFromText(
        outOfScopeVariable.getName() + "= e;", null);
      Objects.requireNonNull(((PsiAssignmentExpression)assignment.getExpression()).getRExpression()).replace(initializer);
      assignment = (PsiExpressionStatement)CodeStyleManager.getInstance(manager.getProject()).reformat(assignment);
      PsiDeclarationStatement declStatement = PsiTreeUtil.getParentOfType(outOfScopeVariable, PsiDeclarationStatement.class);
      LOG.assertTrue(declStatement != null);
      PsiElement parent = declStatement.getParent();
      if (parent instanceof PsiForStatement) {
        declStatement.replace(assignment);
      }
      else {
        parent.addAfter(assignment, declStatement);
      }
    }

    if (outOfScopeVariable.isValid()) {
      outOfScopeVariable.delete();
    }

    if (HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(myUnresolvedReference, addedVar, new THashMap<>(), file) != null) {
      initialize(addedVar);
    }
  }

  private static void initialize(final PsiLocalVariable variable) throws IncorrectOperationException {
    PsiType type = variable.getType();
    String init = PsiTypesUtil.getDefaultValueOfType(type);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    PsiExpression initializer = factory.createExpressionFromText(init, variable);
    variable.setInitializer(initializer);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new BringVariableIntoScopeFix(PsiTreeUtil.findSameElementInCopy(myUnresolvedReference, target), 
                                         PsiTreeUtil.findSameElementInCopy(myOutOfScopeVariable, target));
  }
}
