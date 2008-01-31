package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.EditorDataProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

public class TextEditorPsiDataProvider implements EditorDataProvider {
  @Nullable
  public Object getData(final String dataId, final Editor e, final VirtualFile file) {
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.EDITOR))) {
      return InjectedLanguageUtil.getEditorForInjectedLanguage(e, getPsiFile(e, file));
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.PSI_ELEMENT))) {
      return getPsiElementIn((Editor)getData(AnActionEvent.injectedId(DataConstants.EDITOR), e, file), file);
    }
    if (DataConstants.PSI_ELEMENT.equals(dataId)){
      return getPsiElementIn(e, file);
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.LANGUAGE))) {
      PsiFile psiFile = (PsiFile)getData(AnActionEvent.injectedId(DataConstants.PSI_FILE), e, file);
      Editor editor = (Editor)getData(AnActionEvent.injectedId(DataConstants.EDITOR), e, file);
      if (psiFile == null || editor == null) return null;
      return getLanguageAtCurrentPositionInEditor(editor, psiFile);
    }
    if (DataConstants.LANGUAGE.equals(dataId)) {
      final PsiFile psiFile = getPsiFile(e, file);
      if (psiFile == null) return null;
      return getLanguageAtCurrentPositionInEditor(e, psiFile);
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.VIRTUAL_FILE))) {
      PsiFile psiFile = (PsiFile)getData(AnActionEvent.injectedId(DataConstants.PSI_FILE), e, file);
      if (psiFile == null) return null;
      return psiFile.getVirtualFile();
    }
    if (dataId.equals(AnActionEvent.injectedId(DataConstants.PSI_FILE))) {
      Editor editor = (Editor)getData(AnActionEvent.injectedId(DataConstants.EDITOR), e, file);
      if (editor == null) return null;
      return PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(editor.getDocument());
    }
    if (dataId.equals(DataConstants.PSI_FILE)) {
      return getPsiFile(e, file);
    }
    return null;
  }

  private static Language getLanguageAtCurrentPositionInEditor(final Editor editor, final PsiFile psiFile) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    int caretOffset = editor.getCaretModel().getOffset();
    int mostProbablyCorrectLanguageOffset = caretOffset == selectionModel.getSelectionStart() ||
                                            caretOffset == selectionModel.getSelectionEnd()
                                            ? selectionModel.getSelectionStart()
                                            : caretOffset;

    return PsiUtil.getLanguageAtOffset(psiFile, mostProbablyCorrectLanguageOffset);
  }

  private PsiElement getPsiElementIn(final Editor editor, VirtualFile file) {
    final PsiFile psiFile = getPsiFile(editor, file);
    if (psiFile == null) return null;

    return TargetElementUtilBase.findTargetElement(editor, TargetElementUtilBase
      .REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase
      .ELEMENT_NAME_ACCEPTED | TargetElementUtil
      .NEW_AS_CONSTRUCTOR | TargetElementUtilBase
      .LOOKUP_ITEM_ACCEPTED);
  }

  private static PsiFile getPsiFile(Editor e, VirtualFile file) {
    return file.isValid()? PsiManager.getInstance(e.getProject()).findFile(file) : null; // fix for SCR 40329
  }
}