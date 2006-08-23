
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.AutoIndentLinesHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class AutoIndentLinesAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler() {
    return new AutoIndentLinesHandler();
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    final FileType fileType = file.getFileType();
    return fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage().getEffectiveFormattingModelBuilder(file) != null;
  }
}