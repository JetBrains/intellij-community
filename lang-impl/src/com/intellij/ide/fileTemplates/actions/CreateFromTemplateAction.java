package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.Nullable;

public class CreateFromTemplateAction extends CreateFromTemplateActionBase {

  private FileTemplate myTemplate;

  public CreateFromTemplateAction(FileTemplate template){
    super(template.getName(), null, FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension()).getIcon());
    myTemplate = template;
  }

  @Nullable
  protected AnAction getReplacedAction(final FileTemplate template) {
    return null;
  }

  protected FileTemplate getTemplate(final Project project, final PsiDirectory dir) {
    return myTemplate;
  }

  public void update(AnActionEvent e){
    super.update(e);
    Presentation presentation = e.getPresentation();
    boolean isEnabled = CreateFromTemplateGroup.canCreateFromTemplate(e, myTemplate);
    presentation.setEnabled(isEnabled);
    presentation.setVisible(isEnabled);
  }
}
