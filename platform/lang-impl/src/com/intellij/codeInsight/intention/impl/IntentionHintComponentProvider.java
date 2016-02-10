package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface IntentionHintComponentProvider {
  class SERVICE {
    public static IntentionHintComponentProvider getInstance() {
      return ServiceManager.getService(IntentionHintComponentProvider.class);
    }
  }

  void hideLastIntentionHint(@NotNull Editor editor);

  @Nullable
  IntentionHintComponent getLastIntentionHint(@NotNull Editor editor);

  @NotNull
  IntentionHintComponent showIntentionHint(@NotNull Project project,
                                           @NotNull PsiFile file,
                                           @NotNull Editor editor,
                                           @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                           boolean showExpanded);

  @NotNull
  IntentionHintComponent showIntentionHint(@NotNull final Project project,
                                           @NotNull PsiFile file,
                                           @NotNull final Editor editor,
                                           @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                           boolean showExpanded,
                                           @NotNull Point position);
}
