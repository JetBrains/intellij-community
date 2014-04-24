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

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
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
                                         new ForeachPostfixTemplate(),
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
                                         new WhileStatementPostfixTemplate());
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

  @NotNull
  @Override
  public PsiElement preExpand(@NotNull Editor editor, @NotNull PsiElement context, int offset, @NotNull final String key) {

    return addSemicolonIfNeeded(editor, editor.getDocument(), context, offset - key.length());
  }

  @NotNull
  @Override
  public PsiFile preCheck(@NotNull Editor editor, @NotNull PsiFile copyFile, int currentOffset) {
    Document document = copyFile.getViewProvider().getDocument();
    assert document != null;
    CharSequence sequence = document.getCharsSequence();
    StringBuilder fileContentWithoutKey = new StringBuilder(sequence);
    if (isSemicolonNeeded(copyFile, editor)) {
      fileContentWithoutKey.insert(currentOffset, ';');
      copyFile = PostfixLiveTemplate.copyFile(copyFile, fileContentWithoutKey);
    }

    return copyFile;
  }

  @NotNull
  private static PsiElement addSemicolonIfNeeded(@NotNull final Editor editor,
                                                 @NotNull final Document document,
                                                 @NotNull final PsiElement context,
                                                 final int offset) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Ref<PsiElement> newContext = Ref.create(context);
    final PsiFile file = context.getContainingFile();
    if (isSemicolonNeeded(file, editor)) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            public void run() {
              document.insertString(offset, ";");
              PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
              newContext.set(CustomTemplateCallback.getContext(file, offset - 1));
            }
          });
        }
      });
    }
    return newContext.get();
  }

  private static boolean isSemicolonNeeded(@NotNull PsiFile file, @NotNull Editor editor) {
    return JavaCompletionContributor.semicolonNeeded(editor, file, CompletionInitializationContext.calcStartOffset(editor));
  }
}
