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
 * @author ignatov
 */
public abstract class ExpressionPostfixTemplateWithChooser extends PostfixTemplate {

  @NotNull
  protected final PostfixTemplatePsiInfoBase myInfo;

  protected ExpressionPostfixTemplateWithChooser(@NotNull String name, @NotNull String example, @NotNull PostfixTemplatePsiInfoBase info) {
    super(name, example);
    myInfo = info;
  }

  protected ExpressionPostfixTemplateWithChooser(@NotNull String name,
                                                 @NotNull String key,
                                                 @NotNull String example,
                                                 @NotNull PostfixTemplatePsiInfoBase info) {
    super(name, key, example);
    myInfo = info;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return !getExpressions(context, copyDocument, newOffset).isEmpty();
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull final Editor editor) {
    List<PsiElement> expressions = getExpressions(context, editor.getDocument(), editor.getCaretModel().getOffset());

    if (expressions.isEmpty()) {
      PostfixTemplatesUtils.showErrorHint(context.getProject(), editor);
    }
    else if (expressions.size() == 1) {
      doIt(editor, expressions.get(0));
    }
    else {

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        doIt(editor, expressions.get(expressions.size() - 1));
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
                    doIt(editor, e);
                  }
                }, "Expand postfix template", PostfixLiveTemplate.POSTFIX_TEMPLATE_ID);
              }
            });
          }
        },
        myInfo.getRenderer(),
        "Expressions", 0, ScopeHighlighter.NATURAL_RANGER
      );
    }
  }

  @NotNull
  protected List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, final int offset) {
    List<PsiElement> possibleExpressions = myInfo.getExpressions(context, document, offset);
    List<PsiElement> expressions = ContainerUtil.filter(possibleExpressions,
                                                        new Condition<PsiElement>() {
                                                          @Override
                                                          public boolean value(PsiElement expression) {
                                                            return expression.getTextRange().getEndOffset() == offset;
                                                          }
                                                        }
    );
    return ContainerUtil.filter(expressions.isEmpty() ? maybeTopmostExpression(context) : expressions, getTypeCondition());
  }

  @NotNull
  @SuppressWarnings("unchecked")
  protected Condition<PsiElement> getTypeCondition() {
    return Condition.TRUE;
  }

  @NotNull
  private List<PsiElement> maybeTopmostExpression(@NotNull PsiElement context) {
    return ContainerUtil.createMaybeSingletonList(myInfo.getTopmostExpression(context));
  }

  protected abstract void doIt(@NotNull Editor editor, @NotNull PsiElement expression);
}
