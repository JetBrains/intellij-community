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
  private EditorActionHandler myOriginalAction;

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
      transferableDatas.add(processor.collectTransferableData(file, editor, startOffsets, endOffsets));
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
