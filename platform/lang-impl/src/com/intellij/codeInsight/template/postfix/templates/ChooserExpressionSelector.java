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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * See {@link PostfixTemplateExpressionSelector} for description
 */
public class ChooserExpressionSelector implements PostfixTemplateExpressionSelector {

  @NotNull
  private final Condition<PsiElement> myCondition;

  public ChooserExpressionSelector(@NotNull Condition<PsiElement> condition) {
    myCondition = condition;
  }


  public boolean hasExpression(@NotNull final PostfixTemplateWithExpressionSelector postfixTemplate,
                               @NotNull PsiElement context,
                               @NotNull Document copyDocument,
                               int newOffset) {
    return !getExpressions(postfixTemplate, context, copyDocument, newOffset).isEmpty();
  }

  public void expandTemplate(@NotNull final PostfixTemplateWithExpressionSelector postfixTemplate,
                             @NotNull PsiElement context,
                             @NotNull final Editor editor) {
    List<PsiElement> expressions =
      getExpressions(postfixTemplate, context, editor.getDocument(), editor.getCaretModel().getOffset());

    if (expressions.isEmpty()) {
      PostfixTemplatesUtils.showErrorHint(context.getProject(), editor);
    }
    else if (expressions.size() == 1) {
      postfixTemplate.expandForChooseExpression(expressions.get(0), editor);
    }
    else {

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        PsiElement item = ContainerUtil.getLastItem(expressions);
        assert item != null;
        postfixTemplate.expandForChooseExpression(item, editor);
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
                    postfixTemplate.expandForChooseExpression(e, editor);
                  }
                }, "Expand postfix template", PostfixLiveTemplate.POSTFIX_TEMPLATE_ID);
              }
            });
          }
        },
        postfixTemplate.getPsiInfo().getRenderer(),
        "Expressions", 0, ScopeHighlighter.NATURAL_RANGER
      );
    }
  }

  @NotNull
  private List<PsiElement> getExpressions(@NotNull PostfixTemplateWithExpressionSelector postfixTemplate,
                                          @NotNull PsiElement context,
                                          @NotNull Document document,
                                          final int offset) {
    List<PsiElement> possibleExpressions = postfixTemplate.getPsiInfo().getExpressions(context, document, offset);
    List<PsiElement> expressions = ContainerUtil.filter(possibleExpressions,
                                                        new Condition<PsiElement>() {
                                                          @Override
                                                          public boolean value(PsiElement expression) {
                                                            return expression.getTextRange().getEndOffset() == offset;
                                                          }
                                                        }
    );
    return ContainerUtil
      .filter(expressions.isEmpty() ? maybeTopmostExpression(postfixTemplate, context) : expressions, myCondition);
  }


  @NotNull
  private static List<PsiElement> maybeTopmostExpression(@NotNull PostfixTemplateWithExpressionSelector postfixTemplate, @NotNull PsiElement context) {
    return ContainerUtil.createMaybeSingletonList(postfixTemplate.getPsiInfo().getTopmostExpression(context));
  }
}
