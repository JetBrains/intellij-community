// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.intention;

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext;
import com.intellij.codeInsight.completion.command.CommandProvider;
import com.intellij.codeInsight.completion.command.CompletionCommand;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.intellij.lang.regexp.RegExpFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ExplainRegExpCommandCompletionProvider implements CommandProvider {
  @Override
  public @NotNull List<@NotNull CompletionCommand> getCommands(@NotNull CommandCompletionProviderContext context) {
    if (findInjectedRegexpFile(context.getPsiFile(), context.getOffset()) == null) return List.of();
    ExplainRegExpIntention intention = new ExplainRegExpIntention();
    return List.of(new CompletionCommand() {
      @Override
      public @Nls @NotNull String getPresentableName() {
        return intention.getFamilyName();
      }

      @Override
      public void execute(int offset, @NotNull PsiFile psiFile, @Nullable Editor editor) {
        ReadAction.nonBlocking(() -> findInjectedRegexpFile(psiFile, offset))
          .finishOnUiThread(ModalityState.nonModal(), regexpContext -> {
            if (regexpContext == null || editor == null) return;
            editor.getCaretModel()
              .moveToOffset(
                regexpContext.injectionHost.getTextRange().getStartOffset() + regexpContext.regexpInsideHostRange.getStartOffset());
            ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, intention, intention.getFamilyName());
            editor.getCaretModel()
              .moveToOffset(regexpContext.injectionHost.getTextRange().getEndOffset());
          })
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    });
  }

  private static @Nullable RegexpContext findInjectedRegexpFile(@NotNull PsiFile psiFile, int offset) {
    PsiElement element = psiFile.findElementAt(offset - 1);
    if (element == null) return null;
    PsiLanguageInjectionHost injectionHost = PsiTreeUtil.getParentOfType(element, PsiLanguageInjectionHost.class, false);
    if (injectionHost == null) return null;
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(psiFile.getProject());
    List<Pair<PsiElement, TextRange>> files = injectedLanguageManager.getInjectedPsiFiles(injectionHost);
    if (files == null) return null;
    if (files.size() != 1) return null;
    Pair<PsiElement, TextRange> pair = files.getFirst();
    PsiElement psiElement = pair.first;
    if (!(psiElement instanceof RegExpFile regExpFile)) return null;
    return new RegexpContext(regExpFile, injectionHost, pair.second);
  }

  private record RegexpContext(@NotNull RegExpFile regExpFile, @NotNull PsiLanguageInjectionHost injectionHost,
                               @NotNull TextRange regexpInsideHostRange) {
  }
}
