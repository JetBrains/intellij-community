/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class CreateHtmlAction extends CreateElementActionBase {
  private FileType myFileType;
  private String myTemplateName;

  public CreateHtmlAction() {
    this(StdFileTypes.HTML, FileTemplateManager.INTERNAL_HTML_TEMPLATE_NAME);
  }

  protected CreateHtmlAction(FileType fileType, String templateName) {
    super(IdeBundle.message("action.create.new.filetype", fileType.getName()),
          IdeBundle.message("action.description.create.new.file", fileType.getName()), fileType.getIcon());

    myFileType = fileType;
    myTemplateName = templateName;
  }

  @NotNull
  protected PsiElement[] invokeDialog(Project project, PsiDirectory directory) {
    CreateElementActionBase.MyInputValidator validator = new CreateElementActionBase.MyInputValidator(project, directory);
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.filetype.name", myFileType.getName()),
                             IdeBundle.message("title.new.filetype", myFileType.getName()), Messages.getQuestionIcon(), "", validator);
    return validator.getCreatedElements();

  }

  protected void checkBeforeCreate(String newName, PsiDirectory directory) throws IncorrectOperationException {
    newName = getName(newName);
    directory.checkCreateFile(newName);
  }

  private String getName(String oldName) {
    if (FileTypeManager.getInstance().getFileTypeByFileName(oldName) == myFileType) return oldName;
    return oldName + "." + myFileType.getDefaultExtension();
  }

  @NotNull
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    PsiFile file = directory.createFile(getName(newName));

    final FileTemplate fileTemplate = FileTemplateManager.getInstance().getInternalTemplate(myTemplateName);
    VfsUtil.saveText(file.getVirtualFile(), fileTemplate.getText(FileTemplateManager.getInstance().getDefaultProperties()));

    FileEditorManager.getInstance(directory.getProject()).openFile(file.getVirtualFile(), true);
    return new PsiElement[]{file};

  }

  protected String getErrorTitle() {
    return IdeBundle.message("title.cannot.create.filetype", myFileType.getName());
  }

  protected String getCommandName() {
    return IdeBundle.message("command.name.create.new.file", myFileType.getName());
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return IdeBundle.message("progress.creating.filetype.in.directory", myFileType.getName(), newName, directory.getName());
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    Presentation presentation = e.getPresentation();
    if (presentation.isEnabled()) {
      Module module = (Module)dataContext.getData(DataConstantsEx.MODULE);
      assert module != null;

      if (module.getModuleType() == ModuleType.WEB) {
        IdeView view = (IdeView)dataContext.getData(DataConstantsEx.IDE_VIEW);
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        PsiDirectory[] dirs = view.getDirectories();
        boolean inSourceRoot = false;
        for (PsiDirectory dir : dirs) {
          if (projectFileIndex.isInSourceContent(dir.getVirtualFile()) && dir.getPackage() != null) {
            inSourceRoot = true;
            break;
          }
        }

        if (!inSourceRoot) return;
      }

      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }
}
