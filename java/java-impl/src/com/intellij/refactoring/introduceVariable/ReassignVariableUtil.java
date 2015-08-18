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
package com.intellij.refactoring.introduceVariable;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * User: anna
 * Date: 11/8/10
 */
public class ReassignVariableUtil {
  static final Key<SmartPsiElementPointer<PsiDeclarationStatement>> DECLARATION_KEY = Key.create("var.type");
  static final Key<RangeMarker[]> OCCURRENCES_KEY = Key.create("occurrences");

  private ReassignVariableUtil() {
  }

  @VisibleForTesting
  public static boolean reassign(final Editor editor) {
    final SmartPsiElementPointer<PsiDeclarationStatement> pointer = editor.getUserData(DECLARATION_KEY);
    final PsiDeclarationStatement declaration = pointer != null ? pointer.getElement() : null;
    final PsiType type = getVariableType(declaration);
    if (type != null) {
      VariablesProcessor proc = findVariablesOfType(declaration, type);
      if (proc.size() > 0) {

        if (proc.size() == 1) {
          replaceWithAssignment(declaration, proc.getResult(0), editor);
          return true;
        }

        final DefaultListModel model = new DefaultListModel();
        for (int i = 0; i < proc.size(); i++) {
          model.addElement(proc.getResult(i));
        }
        final JList list = new JBList(model);
        list.setCellRenderer(new ListCellRendererWrapper() {
          @Override
          public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            if (value instanceof PsiVariable) {
              setText(((PsiVariable)value).getName());
              setIcon(((PsiVariable)value).getIcon(0));
            }
          }
        });


        final VisualPosition visualPosition = editor.getCaretModel().getVisualPosition();
        final Point point = editor.visualPositionToXY(new VisualPosition(visualPosition.line + 1, visualPosition.column));
        JBPopupFactory.getInstance().createListPopupBuilder(list)
          .setTitle("Choose variable to reassign")
          .setRequestFocus(true)
          .setItemChoosenCallback(new Runnable() {
            public void run() {
              replaceWithAssignment(declaration, (PsiVariable)list.getSelectedValue(), editor);
            }
          }).createPopup().show(new RelativePoint(editor.getContentComponent(), point));
      }

      return true;
    }
    return false;
  }

  @Nullable
  static PsiType getVariableType(@Nullable PsiDeclarationStatement declaration) {
    if (declaration != null) {
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length > 0 && declaredElements[0] instanceof PsiVariable) {
        return ((PsiVariable)declaredElements[0]).getType();
      }
    }
    return null;
  }

  static VariablesProcessor findVariablesOfType(final PsiDeclarationStatement declaration, final PsiType type) {
    VariablesProcessor proc = new VariablesProcessor(false) {
      @Override
      protected boolean check(PsiVariable var, ResolveState state) {
        for (PsiElement element : declaration.getDeclaredElements()) {
          if (element == var) return false;
        }
        return TypeConversionUtil.isAssignable(var.getType(), type);
      }
    };
    PsiElement scope = declaration;
    while (scope != null) {
      if (scope instanceof PsiFile || 
          scope instanceof PsiMethod || 
          scope instanceof PsiLambdaExpression ||
          scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return proc;
    PsiScopesUtil.treeWalkUp(proc, declaration, scope);
    return proc;
  }

  static void replaceWithAssignment(final PsiDeclarationStatement declaration, final PsiVariable variable, final Editor editor) {
    final PsiVariable var = (PsiVariable)declaration.getDeclaredElements()[0];
    final PsiExpression initializer = var.getInitializer();
    new WriteCommandAction(declaration.getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(variable.getProject());
        final String chosenVariableName = variable.getName();
        //would generate red code for final variables
        PsiElement newDeclaration = elementFactory.createStatementFromText(chosenVariableName + " = " + initializer.getText() + ";", declaration);
        final Collection<PsiReference> references = ReferencesSearch.search(var).findAll();
        newDeclaration = declaration.replace(newDeclaration);
        for (PsiReference reference : references) {
          final PsiElement element = reference.getElement();
          if (element instanceof PsiExpression) {
            element.replace(elementFactory.createExpressionFromText(chosenVariableName, newDeclaration));
          }
        }
      }
    }.execute();
    finishTemplate(editor);
  }

  private static void finishTemplate(Editor editor) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    final InplaceRefactoring renamer = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER);
    if (templateState != null && renamer != null) {
      templateState.gotoEnd(true);
      editor.putUserData(InplaceRefactoring.INPLACE_RENAMER, null);
    }
  }
}
