// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionPhase.EmptyAutoPopup;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class CompletionAutoPopupHandler extends TypedHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(CompletionAutoPopupHandler.class);
  public static final Key<Boolean> ourTestingAutopopup = Key.create("TestingAutopopup");

  @Override
  public @NotNull Result checkAutoPopup(char charTyped, final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);

    CompletionPhase phase = CompletionServiceImpl.getCompletionPhase();
    if (LOG.isDebugEnabled()) {
      LOG.debug("checkAutoPopup: character=" + charTyped + ";");
      LOG.debug("phase=" + phase);
      LOG.debug("lookup=" + lookup);
      LOG.debug("currentCompletion=" + CompletionServiceImpl.getCompletionService().getCurrentCompletion());
    }

    if (lookup != null) {
      if (editor.getSelectionModel().hasSelection()) {
        lookup.performGuardedChange(() -> EditorModificationUtil.deleteSelectedText(editor));
      }
      return Result.STOP;
    }

    if (Character.isLetterOrDigit(charTyped) || charTyped == '_') {
      if (phase instanceof EmptyAutoPopup && ((EmptyAutoPopup)phase).allowsSkippingNewAutoPopup(editor, charTyped)) {
        return Result.CONTINUE;
      }

      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
      return Result.STOP;
    }

    return Result.CONTINUE;
  }

  /**
   * @deprecated can be emulated with {@link AppUIExecutor}
   */
  @Deprecated(forRemoval = true)
  public static void runLaterWithCommitted(@NotNull Project project, Document document, @NotNull Runnable runnable) {
    AppUIExecutor.onUiThread().later().withDocumentsCommitted(project).execute(runnable);
  }
}
