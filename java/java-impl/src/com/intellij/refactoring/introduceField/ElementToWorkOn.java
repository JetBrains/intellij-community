/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import java.util.Iterator;
import java.util.List;

/**
 * @author dsl
 */
public class ElementToWorkOn {
  public static final Key<PsiElement> PARENT = Key.create("PARENT");
  private final PsiExpression myExpression;
  private final PsiLocalVariable myLocalVariable;
  public static final Key<String> PREFIX = Key.create("prefix");
  public static final Key<String> SUFFIX = Key.create("suffix");
  public static final Key<RangeMarker> TEXT_RANGE = Key.create("range");
  public static final Key<Boolean> REPLACE_NON_PHYSICAL = Key.create("replace_non_physical");
  public static final Key<Boolean> OUT_OF_CODE_BLOCK= Key.create("out_of_code_block");

  private ElementToWorkOn(PsiLocalVariable localVariable, PsiExpression expr) {
    myLocalVariable = localVariable;
    myExpression = expr;
  }

  public PsiExpression getExpression() {
    return myExpression;
  }

  public PsiLocalVariable getLocalVariable() {
    return myLocalVariable;
  }

  public boolean isInvokedOnDeclaration() {
    return myExpression == null;
  }

  public static void processElementToWorkOn(final Editor editor, final PsiFile file, final String refactoringName, final String helpId, final Project project, final ElementsProcessor<ElementToWorkOn> processor) {
    PsiLocalVariable localVar = null;
    PsiExpression expr = null;

    if (!editor.getSelectionModel().hasSelection()) {
      PsiElement element = TargetElementUtil.findTargetElement(editor, TargetElementUtil
                                                                         .ELEMENT_NAME_ACCEPTED | TargetElementUtil
                                                                         .REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil
                                                                         .LOOKUP_ITEM_ACCEPTED);
      if (element instanceof PsiLocalVariable) {
        localVar = (PsiLocalVariable) element;
        PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
        if (elementAt instanceof PsiIdentifier && elementAt.getParent() instanceof PsiReferenceExpression) {
          expr = (PsiExpression) elementAt.getParent();
        } else {
          final PsiReference reference = TargetElementUtil.findReference(editor);
          if (reference != null) {
            final PsiElement refElement = reference.getElement();
            if (refElement instanceof PsiReferenceExpression) {
              expr = (PsiReferenceExpression)refElement;
            }
          }
        }
      } else {
        final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiLocalVariable.class);

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement[] statementsInRange = IntroduceVariableBase.findStatementsAtOffset(editor, file, offset);

        if (statementsInRange.length == 1 && IntroduceVariableBase.selectLineAtCaret(offset, statementsInRange)) {
          editor.getSelectionModel().selectLineAtCaret();
          final ElementToWorkOn elementToWorkOn = getElementToWorkOn(editor, file, refactoringName, helpId, project, localVar, expr);
          if (elementToWorkOn == null || elementToWorkOn.getLocalVariable() == null && elementToWorkOn.getExpression() == null || !processor.accept(elementToWorkOn)) {
            editor.getSelectionModel().removeSelection();
          }
        }

        if (!editor.getSelectionModel().hasSelection()){
          final List<PsiExpression> expressions = IntroduceVariableBase.collectExpressions(file, editor, offset);
          for (Iterator<PsiExpression> iterator = expressions.iterator(); iterator.hasNext(); ) {
            PsiExpression expression = iterator.next();
            if (!processor.accept(new ElementToWorkOn(null, expression))) {
              iterator.remove();
            }
          }

          if (expressions.isEmpty()) {
            editor.getSelectionModel().selectLineAtCaret();
          }
          else if (!IntroduceVariableBase.isChooserNeeded(expressions)) {
            expr = expressions.get(0);
          }
          else {
            final int selection = IntroduceVariableBase.preferredSelection(statementsInRange, expressions);
            IntroduceTargetChooser.showChooser(editor, expressions, new Pass<PsiExpression>() {
              @Override
              public void pass(final PsiExpression selectedValue) {
                PsiLocalVariable var = null; //replace var if selected expression == var initializer
                if (variable != null && variable.getInitializer() == selectedValue) {
                  var = variable;
                }
                processor.pass(getElementToWorkOn(editor, file, refactoringName, helpId, project, var, selectedValue));
              }
            }, new PsiExpressionTrimRenderer.RenderFunction(), "Expressions", selection, ScopeHighlighter.NATURAL_RANGER);
            return;
          }
        }
      }
    }


    processor.pass(getElementToWorkOn(editor, file, refactoringName, helpId, project, localVar, expr));
  }

  private static ElementToWorkOn getElementToWorkOn(final Editor editor, final PsiFile file,
                                                    final String refactoringName,
                                                    final String helpId,
                                                    final Project project, PsiLocalVariable localVar, PsiExpression expr) {
    int startOffset = 0;
    int endOffset = 0;
    if (localVar == null && expr == null) {
      startOffset = editor.getSelectionModel().getSelectionStart();
      endOffset = editor.getSelectionModel().getSelectionEnd();
      expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
      if (expr == null) {
        PsiIdentifier ident = CodeInsightUtil.findElementInRange(file, startOffset, endOffset, PsiIdentifier.class);
        if (ident != null) {
          localVar = PsiTreeUtil.getParentOfType(ident, PsiLocalVariable.class);
        }
      }
    }

    if (expr == null && localVar == null) {
      PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        expr = ((PsiExpressionStatement)statements[0]).getExpression();
      }
      else if (statements.length == 1 && statements[0] instanceof PsiDeclarationStatement) {
        PsiDeclarationStatement decl = (PsiDeclarationStatement)statements[0];
        PsiElement[] declaredElements = decl.getDeclaredElements();
        if (declaredElements.length == 1 && declaredElements[0] instanceof PsiLocalVariable) {
          localVar = (PsiLocalVariable)declaredElements[0];
        }
      }
    }
    if (localVar == null && expr == null) {
      expr = IntroduceVariableBase.getSelectedExpression(project, file, startOffset, endOffset);
    }

    if (localVar == null && expr != null) {
      final String errorMessage = IntroduceVariableBase.getErrorMessage(expr);
      if (errorMessage != null) {
        CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, refactoringName, helpId);
        return null;
      }
    }
    return new ElementToWorkOn(localVar, expr);
  }

  public static void showNothingSelectedErrorMessage(final Editor editor,
                                                     final String refactoringName,
                                                     final String helpId,
                                                     final Project project) {
    String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.local.or.expression.name"));
    CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId);
  }
  
  public interface ElementsProcessor<T> {
    boolean accept(ElementToWorkOn el);
    void pass(T t);
  }
}
