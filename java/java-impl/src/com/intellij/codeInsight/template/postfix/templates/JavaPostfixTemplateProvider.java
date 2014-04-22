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
import com.intellij.codeInsight.template.postfix.util.Aliases;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;


public class JavaPostfixTemplateProvider implements PostfixTemplateProvider {

  private static final Logger LOG = Logger.getInstance(JavaPostfixTemplateProvider.class);

  private final Map<String, PostfixTemplate> myMapTemplates;

  public JavaPostfixTemplateProvider() {
    myMapTemplates = ContainerUtil.newHashMap();
    for (PostfixTemplate template : getInitializeTemplateSet()) {
      register(template.getKey(), template);
      Aliases aliases = template.getClass().getAnnotation(Aliases.class);
      if (aliases != null) {
        for (String key : aliases.value()) {
          register(key, template);
        }
      }
    }
  }

  @NotNull
  protected Set<PostfixTemplate> getInitializeTemplateSet() {
    return ContainerUtil.newHashSet(new AssertStatementPostfixTemplate(),
                                    new CastExpressionPostfixTemplate(),
                                    new ElseStatementPostfixTemplate(),
                                    new ForAscendingPostfixTemplate(),
                                    new ForDescendingPostfixTemplate(),
                                    new ForeachPostfixTemplate(),
                                    new FormatPostfixTemplate(),
                                    new IfStatementPostfixTemplate(),
                                    new InstanceofExpressionPostfixTemplate(),
                                    new IntroduceFieldPostfixTemplate(),
                                    new IntroduceVariablePostfixTemplate(),
                                    new IsNullCheckPostfixTemplate(),
                                    new NotExpressionPostfixTemplate(),
                                    new NotNullCheckPostfixTemplate(),
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
    return ContainerUtil.newHashSet(myMapTemplates.values());
  }

  @NotNull
  @Override
  public Set<String> getKeys() {
    return myMapTemplates.keySet();
  }


  @Nullable
  @Override
  public PostfixTemplate get(@Nullable String key) {
    return myMapTemplates.get(key);
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
  public PsiFile preCheck(@NotNull Editor editor, @NotNull PsiFile file, int currentOffset) {
    Document document = file.getViewProvider().getDocument();
    assert document != null;
    CharSequence sequence = document.getCharsSequence();
    StringBuilder fileContentWithoutKey = new StringBuilder(sequence);
    if (isSemicolonNeeded(file, editor)) {
      fileContentWithoutKey.insert(currentOffset, ';');
      file = PostfixLiveTemplate.copyFile(file, fileContentWithoutKey);
    }

    return file;
  }

  private void register(@NotNull String key, @NotNull PostfixTemplate template) {
    PostfixTemplate registered = myMapTemplates.put(key, template);
    if (registered != null) {
      LOG.error("Can't register postfix template. Duplicated key: " + template.getKey());
    }
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
