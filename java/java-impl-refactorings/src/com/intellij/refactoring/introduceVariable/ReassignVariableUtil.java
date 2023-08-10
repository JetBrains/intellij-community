// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ReassignVariableUtil {
  static final Key<SmartPsiElementPointer<PsiDeclarationStatement>> DECLARATION_KEY = Key.create("var.type");
  static final Key<RangeMarker[]> OCCURRENCES_KEY = Key.create("occurrences");

  private ReassignVariableUtil() {
  }

  @TestOnly
  public static void registerDeclaration(Editor editor, PsiDeclarationStatement declarationStatement, Disposable parentDisposable) {
    editor.putUserData(DECLARATION_KEY, SmartPointerManager.createPointer(declarationStatement));
    Disposer.register(parentDisposable, () -> editor.putUserData(DECLARATION_KEY, null));
  }
  
  @VisibleForTesting
  public static boolean reassign(final Editor editor) {
    final SmartPsiElementPointer<PsiDeclarationStatement> pointer = editor.getUserData(DECLARATION_KEY);
    final PsiDeclarationStatement declaration = pointer != null ? pointer.getElement() : null;
    final PsiType type = getVariableType(declaration);
    if (type != null) {
      VariablesProcessor proc = findVariablesOfType(declaration, type);
      if (proc.size() > 0) {

        List<PsiVariable> vars = new ArrayList<>();
        for (int i = 0; i < proc.size(); i++) {
          final PsiVariable variable = proc.getResult(i);
          PsiElement outerCodeBlock = PsiUtil.getVariableCodeBlock(variable, null);
          if (outerCodeBlock == null) continue;
          if (ReferencesSearch.search(variable, new LocalSearchScope(outerCodeBlock))
            .allMatch(reference -> HighlightControlFlowUtil.getElementVariableReferencedFrom(variable, reference.getElement()) == null)) {
            vars.add(variable);
          }
        }

        if (vars.isEmpty()) {
          return true;
        }

        if (vars.size() == 1) {
          replaceWithAssignment(declaration, proc.getResult(0), editor);
          return true;
        }

        JBPopup popup = JBPopupFactory.getInstance()
          .createPopupChooserBuilder(vars)
          .setTitle(JavaRefactoringBundle.message("introduce.local.variable.to.reassign.title"))
          .setRequestFocus(true)
          .setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
            if (value != null) {
              label.setText(value.getName());
              label.setIcon(value.getIcon(0));
            }
          }))
          .setItemChosenCallback((selectedValue) -> replaceWithAssignment(declaration, selectedValue, editor))
          .createPopup();
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
          popup.showInBestPositionFor(editor);
        } else {
          final VisualPosition visualPosition = editor.getCaretModel().getVisualPosition();
          final Point point = editor.visualPositionToXY(new VisualPosition(visualPosition.line + 1, visualPosition.column));
          popup.show(new RelativePoint(editor.getContentComponent(), point));
        }
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
          if (element == var || var instanceof LightElement) return false;
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
    WriteCommandAction.writeCommandAction(declaration.getProject()).run(() -> {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(variable.getProject());
      final String chosenVariableName = variable.getName();
      //would generate red code for final variables
      PsiElement newDeclaration =
        elementFactory.createStatementFromText(chosenVariableName + " = " + initializer.getText() + ";", declaration);
      final Collection<PsiReference> references = ReferencesSearch.search(var).findAll();
      newDeclaration = declaration.replace(newDeclaration);
      for (PsiReference reference : references) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiExpression) {
          element.replace(elementFactory.createExpressionFromText(chosenVariableName, newDeclaration));
        }
      }
      final PsiModifierList modifierList = variable.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
      }
    });
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
