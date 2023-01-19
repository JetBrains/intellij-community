// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCopyPasteHelper.CopyPasteOptions;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.CopyAction;
import com.intellij.openapi.editor.impl.EditorCopyPasteHelperImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

public class CopyHandler extends EditorActionHandler implements CopyAction.TransferableProvider {
  private static final Logger LOG = Logger.getInstance(CopyHandler.class);

  private final EditorActionHandler myOriginalAction;

  public CopyHandler(final EditorActionHandler originalHandler) {
    myOriginalAction = originalHandler;
  }

  @Override
  public void doExecute(@NotNull final Editor editor, Caret caret, final DataContext dataContext) {
    assert caret == null : "Invocation of 'copy' operation for specific caret is not supported";
    Project project = editor.getProject();
    PsiFile file = project == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      if (myOriginalAction != null) {
        myOriginalAction.execute(editor, null, dataContext);
      }
      return;
    }

    CopyAction.copyToClipboard(editor, dataContext, this);
  }

  @Override
  public @Nullable Transferable getSelection(@NotNull Editor editor, @NotNull CopyPasteOptions options) {
    Project project = editor.getProject();
    if (project == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    return getSelection(editor, project, file, options);
  }

  /**
   * @return transferable, or null if copy action was cancelled by a user
   */
  private static @Nullable Transferable getSelection(@NotNull Editor editor, @NotNull Project project, @NotNull PsiFile file,
                                                     @NotNull CopyPasteOptions options) {
    TypingActionsExtension typingActionsExtension = TypingActionsExtension.findForContext(project, editor);
    try {
      typingActionsExtension.startCopy(project, editor);
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> ReadAction.compute(() -> getSelectionAction(editor, project, file, options)),
        ActionsBundle.message("action.EditorCopy.text"), true, project);
    }
    finally {
      typingActionsExtension.endCopy(project, editor);
    }
  }

  private static @NotNull Transferable getSelectionAction(@NotNull Editor editor, @NotNull Project project, @NotNull PsiFile file,
                                                          @NotNull CopyPasteOptions options) {
    SelectionModel selectionModel = editor.getSelectionModel();
    final int[] startOffsets = selectionModel.getBlockSelectionStarts();
    final int[] endOffsets = selectionModel.getBlockSelectionEnds();

    final List<TextBlockTransferableData> transferableDataList = new ArrayList<>();

    DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      for (CopyPastePostProcessor<? extends TextBlockTransferableData> processor : CopyPastePostProcessor.EP_NAME.getExtensionList()) {
        try {
          transferableDataList.addAll(processor.collectTransferableData(file, editor, startOffsets, endOffsets));
        }
        catch (ProcessCanceledException ex) {
          throw ex;
        }
        catch (IndexNotReadyException e) {
          LOG.debug(e);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    });

    String text = editor.getCaretModel().supportsMultipleCarets()
                  ? EditorCopyPasteHelperImpl.getSelectedTextForClipboard(editor, options, transferableDataList)
                  : selectionModel.getSelectedText();
    String rawText = TextBlockTransferable.convertLineSeparators(text, "\n", transferableDataList);
    String escapedText = null;
    for (CopyPastePreProcessor processor : CopyPastePreProcessor.EP_NAME.getExtensionList()) {
      try {
        escapedText = processor.preprocessOnCopy(file, startOffsets, endOffsets, rawText);
      }
      catch (ProcessCanceledException ex) {
        throw ex;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      if (escapedText != null) {
        break;
      }
    }
    return new TextBlockTransferable(escapedText != null ? escapedText : rawText,
                                     transferableDataList,
                                     escapedText != null ? new RawText(rawText) : null);
  }
}
