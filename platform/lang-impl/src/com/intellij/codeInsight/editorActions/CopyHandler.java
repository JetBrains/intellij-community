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

package com.intellij.codeInsight.editorActions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.CopyAction;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorCopyPasteHelperImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

public class CopyHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalAction;

  public CopyHandler(final EditorActionHandler originalHandler) {
    myOriginalAction = originalHandler;
  }

  @Override
  public void doExecute(final Editor editor, Caret caret, final DataContext dataContext) {
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
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          selectionModel.selectLineAtCaret();
        }
      });
      if (!selectionModel.hasSelection(true)) return;
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          EditorActionUtil.moveCaretToLineStartIgnoringSoftWraps(editor);
        }
      });
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final int[] startOffsets = selectionModel.getBlockSelectionStarts();
    final int[] endOffsets = selectionModel.getBlockSelectionEnds();

    final List<TextBlockTransferableData> transferableDatas = new ArrayList<>();
    
    DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      for (CopyPastePostProcessor<? extends TextBlockTransferableData> processor : Extensions.getExtensions(CopyPastePostProcessor.EP_NAME)) {
        transferableDatas.addAll(processor.collectTransferableData(file, editor, startOffsets, endOffsets));
      }
    });

    String text = editor.getCaretModel().supportsMultipleCarets()
                  ? EditorCopyPasteHelperImpl.getSelectedTextForClipboard(editor, transferableDatas)
                  : selectionModel.getSelectedText();
    String rawText = TextBlockTransferable.convertLineSeparators(text, "\n", transferableDatas);
    String escapedText = null;
    for (CopyPastePreProcessor processor : Extensions.getExtensions(CopyPastePreProcessor.EP_NAME)) {
      escapedText = processor.preprocessOnCopy(file, startOffsets, endOffsets, rawText);
      if (escapedText != null) {
        break;
      }
    }
    final Transferable transferable = new TextBlockTransferable(escapedText != null ? escapedText : rawText,
                                                                transferableDatas,
                                                                escapedText != null ? new RawText(rawText) : null);
    CopyPasteManager.getInstance().setContents(transferable);
    if (editor instanceof EditorEx) {
      EditorEx ex = (EditorEx)editor;
      if (ex.isStickySelection()) {
        ex.setStickySelection(false);
      }
    }
  }
}
