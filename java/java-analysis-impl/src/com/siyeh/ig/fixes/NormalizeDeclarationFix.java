/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NormalizeDeclarationFix extends PsiUpdateModCommandQuickFix {

  private final boolean myCStyleDeclaration;

  public NormalizeDeclarationFix(boolean cStyleDeclaration) {
    myCStyleDeclaration = cStyleDeclaration;
  }

  @Override
  public @NotNull String getFamilyName() {
    return myCStyleDeclaration
           ? InspectionGadgetsBundle.message("c.style.array.declaration.replace.quickfix")
           : InspectionGadgetsBundle.message("normalize.declaration.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiVariable) && !(element instanceof PsiMethod) && !(element instanceof PsiDeclarationStatement)) {
      element = element.getParent();
    }
    if (element instanceof PsiParameter || element instanceof PsiRecordComponent) {
      JavaSharedImplUtil.normalizeBrackets((PsiVariable)element);
      return;
    }
    if (element instanceof PsiLocalVariable) {
      element = element.getParent();
      if (!(element instanceof PsiDeclarationStatement)) {
        return;
      }
    }
    if (element instanceof PsiDeclarationStatement declarationStatement) {
      final PsiElement grandParent = element.getParent();
      if (grandParent instanceof PsiForStatement statement) {
        splitMultipleDeclarationInForStatementInitialization(statement);
      }
      else {
        final PsiElement[] elements = declarationStatement.getDeclaredElements();
        List<PsiVariable> psiVariables = ContainerUtil.filterIsInstance(elements, PsiVariable.class);
        if (!myCStyleDeclaration || elements.length != psiVariables.size() || !new SingleDeclarationNormalizer(psiVariables).normalize()) {
          final PsiVariable variable = (PsiVariable)elements[0];
          variable.normalizeDeclaration();
          for (int i = 1; i < elements.length; i++) {
            declarationStatement = PsiTreeUtil.getNextSiblingOfType(declarationStatement, PsiDeclarationStatement.class);
            assert declarationStatement != null;
            JavaSharedImplUtil.normalizeBrackets((PsiVariable)declarationStatement.getDeclaredElements()[0]);
          }
        }
      }
    }
    else if (element instanceof PsiField f) {
      PsiField field = DeclarationSearchUtils.findFirstFieldInDeclaration(f);
      assert field != null;
      PsiField nextField = field;
      int count = 0;
      List<PsiVariable> psiFields = new ArrayList<>();
      while (nextField != null) {
        count++;
        psiFields.add(nextField);
        nextField = DeclarationSearchUtils.findNextFieldInDeclaration(nextField);
      }
      if (!myCStyleDeclaration || !new SingleDeclarationNormalizer(psiFields).normalize()) {
        field.normalizeDeclaration();
        for (int i = 1; i < count; i++) {
          field = PsiTreeUtil.getNextSiblingOfType(field, PsiField.class);
          assert field != null;
          JavaSharedImplUtil.normalizeBrackets(field);
        }
      }
    }
    else if (element instanceof PsiMethod method) {
      final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement == null) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      final PsiTypeElement typeElement = JavaPsiFacade.getElementFactory(project).createTypeElement(returnType);
      CommentTracker ct = new CommentTracker();
      final PsiElement replacement = ct.replaceAndRestoreComments(returnTypeElement, typeElement);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replacement);

      PsiElement child = method.getParameterList();
      while (child != null && !(child instanceof PsiCodeBlock)) {
        final PsiElement elementToDelete = child;
        child = PsiTreeUtil.skipWhitespacesAndCommentsForward(child);
        if (elementToDelete instanceof PsiJavaToken token) {
          final IElementType tokenType = token.getTokenType();
          if (JavaTokenType.LBRACKET.equals(tokenType) || JavaTokenType.RBRACKET.equals(tokenType)) {
            elementToDelete.delete();
          }
        }
        else if (elementToDelete instanceof PsiAnnotation) {
          elementToDelete.delete();
        }
      }
    }
  }

  private static void splitMultipleDeclarationInForStatementInitialization(PsiForStatement forStatement) {
    if (!(forStatement.getParent() instanceof PsiCodeBlock)) {
      forStatement = BlockUtils.expandSingleStatementToBlockStatement(forStatement);
    }
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement declarationStatement)) {
      return;
    }
    final List<PsiLocalVariable> variables =
      ContainerUtil.filterIsInstance(declarationStatement.getDeclaredElements(), PsiLocalVariable.class);
    final int min, max;
    final boolean dependentVariables = containsDependentVariables(variables);
    if (dependentVariables) {
      min = 0;
      max = variables.size() - 1;
    }
    else {
      min = 1;
      max = variables.size();
    }
    final CommentTracker ct = new CommentTracker();
    for (int i = min; i < max; i++) {
      final PsiVariable variable = variables.get(i);
      final PsiDeclarationStatement newStatement = createNewDeclaration(variable, ct);
      forStatement.getParent().addBefore(newStatement, forStatement);
    }

    final PsiVariable remainingVariable = variables.get(dependentVariables ? variables.size() - 1 : 0);
    final PsiDeclarationStatement replacementStatement = createNewDeclaration(remainingVariable, ct);
    ct.replaceAndRestoreComments(declarationStatement, replacementStatement);
  }

  private static @NotNull PsiDeclarationStatement createNewDeclaration(PsiVariable variable, CommentTracker ct) {
    final String name = variable.getName();
    assert name != null;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    final PsiDeclarationStatement replacementStatement =
      factory.createVariableDeclarationStatement(name, variable.getType(), ct.markUnchanged(variable.getInitializer()), variable);
    if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
      PsiUtil.setModifierProperty((PsiLocalVariable)replacementStatement.getDeclaredElements()[0], PsiModifier.FINAL, false);
    }
    return replacementStatement;
  }

  private static boolean containsDependentVariables(List<PsiLocalVariable> variables) {
    if (variables.isEmpty()) return false;
    final Set<PsiLocalVariable> visited = ContainerUtil.newHashSet(variables.getFirst());
    for (int i = 1; i < variables.size(); i++) {
      final PsiLocalVariable variable = variables.get(i);
      if (!PsiTreeUtil.processElements(variable.getInitializer(), element -> !visited.contains(tryResolveLocalVariable(element)))) {
        return true;
      }
      visited.add(variable);
    }
    return false;
  }

  private static PsiLocalVariable tryResolveLocalVariable(PsiElement element) {
    if (element instanceof PsiReferenceExpression referenceExpression) {
      if (referenceExpression.getQualifierExpression() == null) {
        return referenceExpression.resolve() instanceof PsiLocalVariable var ? var : null;
      }
    }
    return null;
  }
}