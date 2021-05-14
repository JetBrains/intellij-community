// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.CopyAction;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorCopyPasteHelperImpl;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
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
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
    if (project == null){
      if (myOriginalAction != null){
        myOriginalAction.execute(editor, null, dataContext);
      }
      return;
    }
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      if (myOriginalAction != null) {
        myOriginalAction.execute(editor, null, dataContext);
      }
      return;
    }

    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection(true)) {
      if (Registry.is(CopyAction.SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY)) {
        return;
      }
      editor.getCaretModel().runForEachCaret(__ -> selectionModel.selectLineAtCaret());
      if (!selectionModel.hasSelection(true)) return;
      editor.getCaretModel().runForEachCaret(__ -> EditorActionUtil.moveCaretToLineStartIgnoringSoftWraps(editor));
    }

    Transferable transferable = getSelection(editor, project, file);

    CopyPasteManager.getInstance().setContents(transferable);
    if (editor instanceof EditorEx) {
      EditorEx ex = (EditorEx)editor;
      if (ex.isStickySelection()) {
        ex.setStickySelection(false);
      }
    }
  }

  @Override
  public @Nullable Transferable getSelection(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project == null) return null;
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    return getSelection(editor, project, file);
  }

  private static @NotNull Transferable getSelection(@NotNull Editor editor, @NotNull Project project, @NotNull PsiFile file) {
    commitDocuments(editor, project);

    SelectionModel selectionModel = editor.getSelectionModel();
    final int[] startOffsets = selectionModel.getBlockSelectionStarts();
    final int[] endOffsets = selectionModel.getBlockSelectionEnds();

    final List<TextBlockTransferableData> transferableDataList = new ArrayList<>();

    DumbService.getInstance(project).withAlternativeResolveEnabled(() -> SlowOperations.allowSlowOperations(() -> {
      for (CopyPastePostProcessor<? extends TextBlockTransferableData> processor : CopyPastePostProcessor.EP_NAME.getExtensionList()) {
        try {
          transferableDataList.addAll(processor.collectTransferableData(file, editor, startOffsets, endOffsets));
        }
        catch (IndexNotReadyException e) {
          LOG.debug(e);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }));

    String text = editor.getCaretModel().supportsMultipleCarets()
                  ? EditorCopyPasteHelperImpl.getSelectedTextForClipboard(editor, transferableDataList)
                  : selectionModel.getSelectedText();
    String rawText = TextBlockTransferable.convertLineSeparators(text, "\n", transferableDataList);
    String escapedText = null;
    for (CopyPastePreProcessor processor : CopyPastePreProcessor.EP_NAME.getExtensionList()) {
      try {
        escapedText = processor.preprocessOnCopy(file, startOffsets, endOffsets, rawText);
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

  private static void commitDocuments(@NotNull Editor editor, @NotNull Project project) {
    final List<CopyPastePostProcessor<? extends TextBlockTransferableData>> postProcessors =
      ContainerUtil.filter(CopyPastePostProcessor.EP_NAME.getExtensionList(), p -> p.requiresAllDocumentsToBeCommitted(editor, project));
    final List<CopyPastePreProcessor> preProcessors =
      ContainerUtil.filter(CopyPastePreProcessor.EP_NAME.getExtensionList(), p -> p.requiresAllDocumentsToBeCommitted(editor, project));
    final boolean commitAllDocuments = !preProcessors.isEmpty() || !postProcessors.isEmpty();
    if (LOG.isDebugEnabled()) {
      LOG.debug("CommitAllDocuments: " + commitAllDocuments);
      if (commitAllDocuments) {
        final String processorNames = StringUtil.join(preProcessors, ",") + "," + StringUtil.join(postProcessors, ",");
        LOG.debug("Processors with commitAllDocuments requirement: [" + processorNames + "]");
      }
    }
    if (commitAllDocuments) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
    else {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }
  }
}
