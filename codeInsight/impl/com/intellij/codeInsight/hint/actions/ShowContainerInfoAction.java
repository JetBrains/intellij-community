package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowContainerInfoHandler;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.Nullable;

public class ShowContainerInfoAction extends BaseCodeInsightAction{
  protected CodeInsightActionHandler getHandler() {
    return new ShowContainerInfoHandler();
  }

  @Nullable
  protected Editor getBaseEditor(final DataContext dataContext, final Project project) {
    return DataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext);
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file instanceof PsiJavaFile || file instanceof XmlFile ||
           file.getLanguage().getStructureViewBuilder(file) instanceof TreeBasedStructureViewBuilder;
  }
}