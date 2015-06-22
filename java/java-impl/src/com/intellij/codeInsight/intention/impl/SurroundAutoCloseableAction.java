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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SurroundAutoCloseableAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
    if (!PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7)) return false;

    final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (variable == null) return false;
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return false;
    final PsiElement declaration = variable.getParent();
    if (!(declaration instanceof PsiDeclarationStatement)) return false;
    final PsiElement codeBlock = declaration.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return false;

    return InheritanceUtil.isInheritor(variable.getType(), CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) {
      return;
    }

    final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (variable == null) return;
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return;
    final PsiElement declaration = variable.getParent();
    if (!(declaration instanceof PsiDeclarationStatement)) return;
    final PsiElement codeBlock = declaration.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return;

    final LocalSearchScope scope = new LocalSearchScope(codeBlock);
    PsiElement last = null;
    for (PsiReference reference : ReferencesSearch.search(variable, scope).findAll()) {
      final PsiElement usage = PsiTreeUtil.findPrevParent(codeBlock, reference.getElement());
      if ((last == null || usage.getTextOffset() > last.getTextOffset())) {
        last = usage;
      }
    }

    final String text = "try (" + variable.getTypeElement().getText() + " " + variable.getName() + " = " + initializer.getText() + ") {}";
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiTryStatement armStatement = (PsiTryStatement)declaration.replace(factory.createStatementFromText(text, codeBlock));

    List<PsiElement> toFormat = null;
    if (last != null) {
      toFormat = moveStatements(last, armStatement);
    }

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final PsiElement formattedElement = codeStyleManager.reformat(armStatement);
    if (toFormat != null) {
      for (PsiElement psiElement : toFormat) {
        codeStyleManager.reformat(psiElement);
      }
    }

    if (last == null) {
      final PsiCodeBlock tryBlock = ((PsiTryStatement)formattedElement).getTryBlock();
      if (tryBlock != null) {
        final PsiJavaToken brace = tryBlock.getLBrace();
        if (brace != null) {
          editor.getCaretModel().moveToOffset(brace.getTextOffset() + 1);
        }
      }
    }
  }

  private static List<PsiElement> moveStatements(PsiElement last, PsiTryStatement statement) {
    PsiCodeBlock tryBlock = statement.getTryBlock();
    assert tryBlock != null : statement.getText();
    PsiElement parent = statement.getParent();

    List<PsiElement> toFormat = new SmartList<PsiElement>();
    PsiElement stopAt = last.getNextSibling();

    PsiElement i = statement.getNextSibling();
    while (i != null && i != stopAt) {
      PsiElement child = i;
      i = PsiTreeUtil.skipSiblingsForward(i, PsiWhiteSpace.class, PsiComment.class);

      if (!(child instanceof PsiDeclarationStatement)) continue;

      PsiElement anchor = child;
      for (PsiElement declared : ((PsiDeclarationStatement)child).getDeclaredElements()) {
        if (!(declared instanceof PsiLocalVariable)) continue;

        final int endOffset = last.getTextRange().getEndOffset();
        boolean contained = ReferencesSearch.search(declared, new LocalSearchScope(parent)).forEach(new Processor<PsiReference>() {
          @Override
          public boolean process(PsiReference ref) {
            return ref.getElement().getTextOffset() <= endOffset;
          }
        });

        if (!contained) {
          PsiLocalVariable var = (PsiLocalVariable)declared;
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());

          String name = var.getName();
          assert name != null : child.getText();
          toFormat.add(parent.addBefore(factory.createVariableDeclarationStatement(name, var.getType(), null), statement));

          PsiExpression varInit = var.getInitializer();
          if (varInit != null) {
            String varAssignText = name + " = " + varInit.getText() + ";";
            anchor = parent.addAfter(factory.createStatementFromText(varAssignText, parent), anchor);
          }

          var.delete();
        }
      }

      if (child == last && !child.isValid()) {
        last = anchor;
      }
    }

    PsiElement first = statement.getNextSibling();
    tryBlock.addRangeBefore(first, last, tryBlock.getRBrace());
    parent.deleteChildRange(first, last);

    return toFormat;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.surround.resource.with.ARM.block");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
