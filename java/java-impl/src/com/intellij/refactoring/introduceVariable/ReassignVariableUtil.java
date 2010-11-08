/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * User: anna
 * Date: 11/8/10
 */
public class ReassignVariableUtil {
  static final Key<PsiDeclarationStatement> DECLARATION_KEY = Key.create("var.type");

  static boolean reassign(final Editor editor) {
    final PsiDeclarationStatement declaration = editor.getUserData(DECLARATION_KEY);
    final PsiType type = getVariableType(declaration);
    if (type != null) {
      VariablesProcessor proc = findVariablesOfType(editor, declaration, type);
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
        list.setCellRenderer(new ListCellRendererWrapper(new DefaultListCellRenderer()) {
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
  private static PsiType getVariableType(@Nullable PsiDeclarationStatement declaration) {
    if (declaration != null) {
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length > 0 && declaredElements[0] instanceof PsiVariable) {
        return ((PsiVariable)declaredElements[0]).getType();
      }
    }
    return null;
  }

  private static VariablesProcessor findVariablesOfType(Editor editor, final PsiDeclarationStatement declaration, final PsiType type) {
    VariablesProcessor proc = new VariablesProcessor(false) {
      @Override
      protected boolean check(PsiVariable var, ResolveState state) {
        for (PsiElement element : declaration.getDeclaredElements()) {
          if (element == var) return false;
        }
        return TypeConversionUtil.isAssignable(var.getType(), type);
      }
    };
    PsiElement scope = editor.getUserData(DECLARATION_KEY);
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return proc;
    PsiScopesUtil.treeWalkUp(proc, declaration, scope);
    return proc;
  }

  static void replaceWithAssignment(final PsiDeclarationStatement declaration, final PsiVariable variable, Editor editor) {
    final PsiVariable var = (PsiVariable)declaration.getDeclaredElements()[0];
    final PsiExpression initializer = var.getInitializer();
    new WriteCommandAction(declaration.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        //would generate red code for final variables
        declaration.replace(JavaPsiFacade.getElementFactory(variable.getProject())
                              .createStatementFromText(variable.getName() + " = " + initializer.getText() + ";", declaration));
      }
    }.execute();
    finishTemplate(editor);
  }

  private static void finishTemplate(Editor editor) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    final VariableInplaceRenamer renamer = editor.getUserData(VariableInplaceRenamer.INPLACE_RENAMER);
    if (templateState != null && renamer != null) {
      templateState.gotoEnd(true);
      editor.putUserData(VariableInplaceRenamer.INPLACE_RENAMER, null);
    }
  }

  static Expression createExpression(final TypeSelectorManagerImpl typeSelectorManager, final String defaultText) {
    final PsiType[] types = typeSelectorManager.getTypesForAll();
    return new Expression() {
      @Override
      public com.intellij.codeInsight.template.Result calculateResult(ExpressionContext context) {
        return new TextResult(defaultText);
      }

      @Override
      public com.intellij.codeInsight.template.Result calculateQuickResult(ExpressionContext context) {
        return new TextResult(defaultText);
      }

      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        LookupElement[] result = new LookupElement[types.length];
        for (int i = 0, typesLength = types.length; i < typesLength; i++) {
          result[i] = LookupElementBuilder.create(types[i], types[i].getPresentableText());
        }
        return result;
      }
    };
  }

  static String getAdvertisementText(Editor editor, PsiDeclarationStatement declaration, PsiType type) {
    final VariablesProcessor processor = findVariablesOfType(editor, declaration, type);
    if (processor.size() > 0) {
      final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
      final Shortcut[] shortcuts = keymap.getShortcuts("IntroduceVariable");
      if (shortcuts.length > 0) {
        return "Press " + shortcuts[0] + " to reassign existing variable";
      }
    }
    return "Press Shift Tab to change type";
  }
}
