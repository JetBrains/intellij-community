package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;

import java.io.File;

public class CreateDirectoryOrPackageAction extends AnAction {
  public CreateDirectoryOrPackageAction() {
    super(IdeBundle.message("action.create.new.directory.or.package"), IdeBundle.message("action.create.new.directory.or.package"), null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    boolean isDirectory = fileIndex.getPackageNameByDirectory(directory.getVirtualFile()) == null;

    MyInputValidator validator = new MyInputValidator(project, directory, isDirectory);
    Messages.showInputDialog(project, isDirectory
                                      ? IdeBundle.message("prompt.enter.new.directory.name")
                                      : IdeBundle.message("prompt.enter.new.package.name"),
                                      isDirectory ? IdeBundle.message("title.new.directory") : IdeBundle.message("title.new.package"),
                                      Messages.getQuestionIcon(), "", validator);

    if (validator.myCreatedElement == null) return;

    view.selectElement(validator.myCreatedElement);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setEnabled(true);

    boolean isPackage = false;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiDirectory directory : directories) {
      if (fileIndex.getPackageNameByDirectory(directory.getVirtualFile()) != null) {
        isPackage = true;
        break;
      }
    }

    if (isPackage) {
      presentation.setText(IdeBundle.message("action.package"));
      presentation.setIcon(Icons.PACKAGE_ICON);
    }
    else {
      presentation.setText(IdeBundle.message("action.directory"));
      presentation.setIcon(Icons.DIRECTORY_OPEN_ICON);
    }
  }

  protected class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final PsiDirectory myDirectory;
    private final boolean myIsDirectory;
    private PsiElement myCreatedElement = null;

    public MyInputValidator(Project project, PsiDirectory directory, boolean isDirectory) {
      myProject = project;
      myDirectory = directory;
      myIsDirectory = isDirectory;
    }

    public boolean checkInput(String inputString) {
      return true;
    }

    public boolean canClose(String inputString) {
      final String subDirName = inputString;

      if (subDirName.length() == 0) {
        Messages.showMessageDialog(myProject, IdeBundle.message("error.name.should.be.specified"), CommonBundle.getErrorTitle(),
                                   Messages.getErrorIcon());
        return false;
      }

      //[ven] valentin thinks this is too restrictive
      /*if (!myIsDirectory) {
        PsiNameHelper helper = PsiManager.getInstance(myProject).getNameHelper();
        if (!helper.isQualifiedName(subDirName)) {
          Messages.showMessageDialog(myProject, "A valid package name should be specified", "Error", Messages.getErrorIcon());
          return false;
        }
      }*/

      final boolean multiCreation = !myIsDirectory && subDirName.indexOf('.') != -1;
      if (!multiCreation) {
        try {
          myDirectory.checkCreateSubdirectory(subDirName);
        }
        catch (IncorrectOperationException ex) {
          Messages.showMessageDialog(myProject, CreateElementActionBase.filterMessage(ex.getMessage()), CommonBundle.getErrorTitle(),
                                     Messages.getErrorIcon());
          return false;
        }
      }

      Runnable command = new Runnable() {
        public void run() {
          final Runnable run = new Runnable() {
            public void run() {
              LocalHistoryAction action = LocalHistoryAction.NULL;
              try {
                String actionName;
                String dirPath = myDirectory.getVirtualFile().getPresentableUrl();
                actionName = IdeBundle.message("progress.creating.directory", dirPath, File.separator, subDirName);
                action = LocalHistory.startAction(myProject, actionName);

                final PsiDirectory createdDir;
                if (myIsDirectory) {
                  createdDir = myDirectory.createSubdirectory(subDirName);
                }
                else {
                  createdDir = DirectoryUtil.createSubdirectories(subDirName, MyInputValidator.this.myDirectory);
                }


                myCreatedElement = createdDir;

              }
              catch (final IncorrectOperationException ex) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    Messages.showMessageDialog(myProject, CreateElementActionBase.filterMessage(ex.getMessage()),
                                               CommonBundle.getErrorTitle(), Messages.getErrorIcon());
                  }
                });
              }
              finally {
                action.finish();
              }
            }
          };
          ApplicationManager.getApplication().runWriteAction(run);
        }
      };
      CommandProcessor.getInstance().executeCommand(myProject, command, myIsDirectory
                                                                        ? IdeBundle.message("command.create.directory")
                                                                        : IdeBundle.message("command.create.package"), null);

      return myCreatedElement != null;
    }
  }
}
