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

import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils.selectorTopmost;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.JAVA_PSI_INFO;

public class TryStatementPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  public static Condition<PsiElement> HAS_TYPE = new Condition<PsiElement>() {
    @Override
    public boolean value(@Nullable PsiElement element) {
      return element instanceof PsiExpression && ((PsiExpression)element).getType() != null;
    }
  };

  protected TryStatementPostfixTemplate() {
    super("try", "try { exp } catch(Exception e)", JAVA_PSI_INFO, selectorTopmost(HAS_TYPE));
  }


  @Override
  public void expandForChooseExpression(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = (PsiExpression)context;
    PsiStatement statement = PsiTreeUtil.getParentOfType(expr, PsiStatement.class, false);
    assert statement != null;

    PsiFile file = statement.getContainingFile();
    Project project = context.getProject();

    JavaWithTryCatchSurrounder surrounder = new JavaWithTryCatchSurrounder();
    TextRange range = surrounder.surroundElements(project, editor, new PsiElement[]{statement});

    if (range == null) {
      PostfixTemplatesUtils.showErrorHint(project, editor);
      return;
    }

    PsiElement element = file.findElementAt(range.getStartOffset());
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    assert tryStatement != null;
    PsiCodeBlock block = tryStatement.getTryBlock();
    assert block != null;
    PsiStatement statementInTry = ArrayUtil.getFirstElement(block.getStatements());
    if (null != statementInTry) {
      editor.getCaretModel().moveToOffset(statementInTry.getTextRange().getEndOffset());
    }
  }
}
