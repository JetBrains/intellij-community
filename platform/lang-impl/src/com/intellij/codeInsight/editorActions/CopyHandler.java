/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

public class CopyHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalAction;

  public CopyHandler(final EditorActionHandler originalHandler) {
    myOriginalAction = originalHandler;
  }

  public void execute(final Editor editor, final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
    if (project == null){
      if (myOriginalAction != null){
        myOriginalAction.execute(editor, dataContext);
      }
      return;
    }
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (file == null || settings.ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO){
      if (myOriginalAction != null){
        myOriginalAction.execute(editor, dataContext);
      }
      return;
    }

    final SelectionModel selectionModel = editor.getSelectionModel();
    if(!selectionModel.hasSelection() && !selectionModel.hasBlockSelection()) {
      selectionModel.selectLineAtCaret();
      if (!selectionModel.hasSelection()) return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final int[] startOffsets = selectionModel.getBlockSelectionStarts();
    final int[] endOffsets = selectionModel.getBlockSelectionEnds();

    List<TextBlockTransferableData> transferableDatas = new ArrayList<TextBlockTransferableData>();
    for(CopyPastePostProcessor processor: Extensions.getExtensions(CopyPastePostProcessor.EP_NAME)) {
      final TextBlockTransferableData e = processor.collectTransferableData(file, editor, startOffsets, endOffsets);
      if (e != null) {
        transferableDatas.add(e);
      }
    }

    String rawText = TextBlockTransferable.convertLineSeparators(selectionModel.getSelectedText(), "\n", transferableDatas);
    String escapedText = null;
    for(CopyPastePreProcessor processor: Extensions.getExtensions(CopyPastePreProcessor.EP_NAME)) {
      escapedText = processor.preprocessOnCopy(file, startOffsets, endOffsets, rawText);
      if (escapedText != null) {
        break;
      }
    }
    final Transferable transferable = new TextBlockTransferable(escapedText != null ? escapedText : rawText,
                                                                transferableDatas,
                                                                escapedText != null ? new RawText(rawText) : null);
    CopyPasteManager.getInstance().setContents(transferable);
  }
}
