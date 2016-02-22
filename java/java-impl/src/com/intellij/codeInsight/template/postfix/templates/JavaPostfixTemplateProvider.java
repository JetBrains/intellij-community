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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


public class JavaPostfixTemplateProvider implements PostfixTemplateProvider {
  private final Set<PostfixTemplate> templates;


  public JavaPostfixTemplateProvider() {
    templates = ContainerUtil.newHashSet(new AssertStatementPostfixTemplate(),
                                         new CastExpressionPostfixTemplate(),
                                         new ElseStatementPostfixTemplate(),
                                         new ForAscendingPostfixTemplate(),
                                         new ForDescendingPostfixTemplate(),
                                         new ForeachPostfixTemplate("iter"),
                                         new ForeachPostfixTemplate("for"),
                                         new FormatPostfixTemplate(),
                                         new IfStatementPostfixTemplate(),
                                         new InstanceofExpressionPostfixTemplate(),
                                         new InstanceofExpressionPostfixTemplate("inst"),
                                         new IntroduceFieldPostfixTemplate(),
                                         new IntroduceVariablePostfixTemplate(),
                                         new IsNullCheckPostfixTemplate(),
                                         new NotExpressionPostfixTemplate(),
                                         new NotExpressionPostfixTemplate("!"),
                                         new NotNullCheckPostfixTemplate(),
                                         new NotNullCheckPostfixTemplate("nn"),
                                         new ParenthesizedExpressionPostfixTemplate(),
                                         new ReturnStatementPostfixTemplate(),
                                         new SoutPostfixTemplate(),
                                         new SwitchStatementPostfixTemplate(),
                                         new SynchronizedStatementPostfixTemplate(),
                                         new ThrowExceptionPostfixTemplate(),
                                         new TryStatementPostfixTemplate(),
                                         new TryWithResourcesPostfixTemplate(),
                                         new WhileStatementPostfixTemplate(),
                                         new StreamPostfixTemplate(),
                                         new OptionalPostfixTemplate(),
                                         new LambdaPostfixTemplate());
  }

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return templates;
  }

  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return currentChar == '.' || currentChar == '!';
  }

  @Override
  public void preExpand(@NotNull final PsiFile file, @NotNull final Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (isSemicolonNeeded(file, editor)) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        CommandProcessor.getInstance().runUndoTransparentAction(
          () -> {
            EditorModificationUtil.insertStringAtCaret(editor, ";", false, false);
            PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
          });
      });
    }
  }

  @Override
  public void afterExpand(@NotNull final PsiFile file, @NotNull final Editor editor) {
  }

  @NotNull
  @Override
  public PsiFile preCheck(final @NotNull PsiFile copyFile, final @NotNull Editor realEditor, final int currentOffset) {
    Document document = copyFile.getViewProvider().getDocument();
    assert document != null;
    CharSequence sequence = document.getCharsSequence();
    StringBuilder fileContentWithSemicolon = new StringBuilder(sequence);
    if (isSemicolonNeeded(copyFile, realEditor)) {
      fileContentWithSemicolon.insert(currentOffset, ';');
      return PostfixLiveTemplate.copyFile(copyFile, fileContentWithSemicolon);
    }

    return copyFile;
  }

  private static boolean isSemicolonNeeded(@NotNull PsiFile file, @NotNull Editor editor) {
    return JavaCompletionContributor.semicolonNeeded(editor, file, CompletionInitializationContext.calcStartOffset(editor.getCaretModel().getCurrentCaret()));
  }
}
