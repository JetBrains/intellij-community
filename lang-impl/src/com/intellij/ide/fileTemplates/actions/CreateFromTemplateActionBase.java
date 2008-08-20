package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class CreateFromTemplateActionBase extends AnAction {

  public CreateFromTemplateActionBase(final String title, final String description, final Icon icon) {
    super (title,description,icon);
  }

  public final void actionPerformed(AnActionEvent e){
    DataContext dataContext = e.getDataContext();

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    PsiDirectory dir = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (dir == null) return;

    FileTemplate selectedTemplate = getTemplate(project, dir);
    if(selectedTemplate != null){
      AnAction action = getReplacedAction(selectedTemplate);
      if (action != null) {
        action.actionPerformed(e);
      }
      else {
        FileTemplateManager.getInstance().addRecentName(selectedTemplate.getName());
        PsiElement createdElement = new CreateFromTemplateDialog(project, dir, selectedTemplate).create();
        if (createdElement != null) {
          view.selectElement(createdElement);
        }
      }
    }
  }

  @Nullable
  protected abstract AnAction getReplacedAction(final FileTemplate selectedTemplate);

  protected abstract FileTemplate getTemplate(final Project project, final PsiDirectory dir);
}
