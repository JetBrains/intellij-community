// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.CodeStyleBundle;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;

public final class FormattingProgressTask extends SequentialModalProgressTask implements FormattingProgressCallback {

  private static final double MAX_PROGRESS_VALUE = 1;
  private static final double TOTAL_WEIGHT =
    Arrays.stream(FormattingStateId.values()).mapToDouble(FormattingStateId::getProgressWeight).sum();

  private final WeakReference<VirtualFile> myFile;
  private final WeakReference<Document>    myDocument;
  private final int                        myFileTextLength;

  private @NotNull FormattingStateId myLastState                       = FormattingStateId.WRAPPING_BLOCKS;
  private long              myDocumentModificationStampBefore = -1;

  private int myBlocksToModifyNumber;
  private int myModifiedBlocksNumber;

  public FormattingProgressTask(@Nullable Project project, @NotNull PsiFile file, @NotNull Document document) {
    super(project, getTitle(file));
    myFile = new WeakReference<>(file.getVirtualFile());
    myDocument = new WeakReference<>(document);
    myFileTextLength = file.getTextLength();
  }

  private static @NotNull @NlsContexts.DialogTitle String getTitle(@NotNull PsiFile file) {
    VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
    if (virtualFile == null) {
      return CodeStyleBundle.message("reformat.progress.common.text");
    }
    else {
      return CodeStyleBundle.message("reformat.progress.file.with.known.name.text", virtualFile.getName());
    }
  }

  @Override
  protected void prepare(final @NotNull SequentialTask task) {
    UIUtil.invokeAndWaitIfNeeded(() -> {
      Document document = myDocument.get();
      if (document != null) {
        myDocumentModificationStampBefore = document.getModificationStamp();
      }
      task.prepare();
    });
  }

  @Override
  public void onCancel() {
    cancelled();
  }

  @Override
  public void onThrowable(@NotNull Throwable error) {
    super.onThrowable(error);
    cancelled();
  }

  @Override
  public void afterWrappingBlock(@NotNull LeafBlockWrapper wrapped) {
    update(FormattingStateId.WRAPPING_BLOCKS, MAX_PROGRESS_VALUE * wrapped.getEndOffset() / myFileTextLength);
  }

  @Override
  public void afterProcessingBlock(@NotNull LeafBlockWrapper block) {
    update(FormattingStateId.PROCESSING_BLOCKS, MAX_PROGRESS_VALUE * block.getEndOffset() / myFileTextLength);
  }

  @Override
  public void beforeApplyingFormatChanges(@NotNull Collection<LeafBlockWrapper> modifiedBlocks) {
    myBlocksToModifyNumber = modifiedBlocks.size();
    updateTextIfNecessary(FormattingStateId.APPLYING_CHANGES);
    setCancelText(CodeStyleBundle.message("action.stop"));
  }

  @Override
  public void afterApplyingChange(@NotNull LeafBlockWrapper block) {
    if (myModifiedBlocksNumber++ >= myBlocksToModifyNumber) {
      return;
    }

    update(FormattingStateId.APPLYING_CHANGES, MAX_PROGRESS_VALUE * myModifiedBlocksNumber / myBlocksToModifyNumber);
  }

  private void update(@NotNull FormattingStateId state, double completionRate) {
    ProgressIndicator indicator = getIndicator();
    if (indicator == null) {
      return;
    }

    updateTextIfNecessary(state);

    myLastState = state;
    double newFraction = 0;
    for (FormattingStateId prevState : state.getPreviousStates()) {
      newFraction += MAX_PROGRESS_VALUE * prevState.getProgressWeight() / TOTAL_WEIGHT;
    }
    newFraction += completionRate * state.getProgressWeight() / TOTAL_WEIGHT;

    double currentFraction = indicator.getFraction();
    if (newFraction - currentFraction < MAX_PROGRESS_VALUE / 100) {
      return;
    }

    indicator.setFraction(newFraction);
  }

  private void updateTextIfNecessary(@NotNull FormattingStateId currentState) {
    ProgressIndicator indicator = getIndicator();
    if (myLastState != currentState && indicator != null) {
      indicator.setText(currentState.getDescription());
    }
  }

  @Override
  public void cancelled() {
    CodeFormatterFacade.FORMATTING_CANCELLED_FLAG.set(true);
    VirtualFile file = myFile.get();
    Document document = myDocument.get();
    if (file == null || document == null || myDocumentModificationStampBefore < 0) {
      return;
    }
    FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
    if (editor == null) {
      return;
    }

    UndoManager manager = UndoManager.getInstance(myProject);
    while (manager.isUndoAvailable(editor) && document.getModificationStamp() != myDocumentModificationStampBefore) {
      manager.undo(editor);
    }
  }
}
