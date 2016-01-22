package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class IntentionHintComponentProviderImpl implements IntentionHintComponentProvider {
  @NotNull
  @Override
  public IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                  @NotNull PsiFile file,
                                                  @NotNull Editor editor,
                                                  @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                                  boolean showExpanded) {
    final Point position = IntentionHintComponentImpl.getHintPosition(editor);
    return showIntentionHint(project, file, editor, intentions, showExpanded, position);
  }

  @NotNull
  @Override
  public IntentionHintComponent showIntentionHint(@NotNull Project project,
                                                  @NotNull PsiFile file,
                                                  @NotNull final Editor editor,
                                                  @NotNull ShowIntentionsPass.IntentionsInfo intentions,
                                                  boolean showExpanded,
                                                  @NotNull Point position) {
    final IntentionHintComponentImpl component = new IntentionHintComponentImpl(project, file, editor, intentions);

    component.showIntentionHintImpl(!showExpanded, position);
    Disposer.register(project, component);
    if (showExpanded) {
      if (ApplicationManager.getApplication().isOnAir()) {
        component.showPopup(false);
      }
      else {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!editor.isDisposed() && editor.getComponent().isShowing()) {
              component.showPopup(false);
            }
          }
        }, project.getDisposed());
      }
    }

    return component;
  }
}
