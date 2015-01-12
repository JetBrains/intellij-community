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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class PostfixTemplateWithExpressionSelector extends PostfixTemplate {
  @NotNull
  private final PostfixTemplateExpressionSelector mySelector;

  protected PostfixTemplateWithExpressionSelector(@NotNull String name,
                                                  @NotNull String key,
                                                  @NotNull String example,
                                                  @NotNull PostfixTemplateExpressionSelector selector) {
    super(name, key, example);
    mySelector = selector;
  }


  protected PostfixTemplateWithExpressionSelector(@NotNull String name,
                                                  @NotNull String example,
                                                  @NotNull PostfixTemplateExpressionSelector selector) {
    super(name, example);
    mySelector = selector;
  }


  @Override
  public final boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return mySelector.hasExpression(context, copyDocument, newOffset);
  }

  @Override
  public final void expand(@NotNull PsiElement context, @NotNull final Editor editor) {
    List<PsiElement> expressions = mySelector.getExpressions(context,
                                                             editor.getDocument(),
                                                             editor.getCaretModel().getOffset());

    if (expressions.isEmpty()) {
      PostfixTemplatesUtils.showErrorHint(context.getProject(), editor);
      return;
    }

    if (expressions.size() == 1) {
      expandForChooseExpression(expressions.get(0), editor);
      return;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      PsiElement item = ContainerUtil.getLastItem(expressions);
      assert item != null;
      expandForChooseExpression(item, editor);
      return;
    }

    IntroduceTargetChooser.showChooser(
      editor, expressions,
      new Pass<PsiElement>() {
        public void pass(@NotNull final PsiElement e) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              CommandProcessor.getInstance().executeCommand(e.getProject(), new Runnable() {
                public void run() {
                  expandForChooseExpression(e, editor);
                }
              }, "Expand postfix template", PostfixLiveTemplate.POSTFIX_TEMPLATE_ID);
            }
          });
        }
      },
      mySelector.getRenderer(),
      "Expressions", 0, ScopeHighlighter.NATURAL_RANGER
    );
  }

  protected abstract void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor);
}
