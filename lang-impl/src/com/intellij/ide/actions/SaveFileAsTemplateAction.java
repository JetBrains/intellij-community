package com.intellij.ide.actions;

import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.ide.fileTemplates.ui.ConfigureTemplatesDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

public class SaveFileAsTemplateAction extends AnAction{
  public void actionPerformed(AnActionEvent e){
    Project project = e.getData(PlatformDataKeys.PROJECT);
    String fileText = e.getData(PlatformDataKeys.FILE_TEXT);
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    String extension = file.getExtension();
    String nameWithoutExtension = file.getNameWithoutExtension();
    AllFileTemplatesConfigurable fileTemplateOptions = new AllFileTemplatesConfigurable();
    ConfigureTemplatesDialog dialog = new ConfigureTemplatesDialog(project, fileTemplateOptions);
    PsiFile psiFile = e.getData(DataKeys.PSI_FILE);
    for(SaveFileAsTemplateHandler handler: Extensions.getExtensions(SaveFileAsTemplateHandler.EP_NAME)) {
      String textFromHandler = handler.getTemplateText(psiFile, fileText, nameWithoutExtension);
      if (textFromHandler != null) {
        fileText = textFromHandler;
        break;
      }
    }
    fileTemplateOptions.createNewTemplate(nameWithoutExtension, extension, fileText);
    dialog.show();
  }

  public void update(AnActionEvent e) {
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    String fileText = e.getData(PlatformDataKeys.FILE_TEXT);
    e.getPresentation().setEnabled((fileText != null) && (file != null));
  }
}
