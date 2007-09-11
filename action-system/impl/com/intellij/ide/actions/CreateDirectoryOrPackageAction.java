package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;

import java.io.File;
import java.util.StringTokenizer;

public class CreateDirectoryOrPackageAction extends AnAction {
  public CreateDirectoryOrPackageAction() {
    super(IdeBundle.message("action.create.new.directory.or.package"), IdeBundle.message("action.create.new.directory.or.package"), null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    IdeView view = DataKeys.IDE_VIEW.getData(dataContext);
    Project project = DataKeys.PROJECT.getData(dataContext);

    PsiDirectory directory = PackageUtil.getOrChooseDirectory(view);

    if (directory == null) return;
    boolean isDirectory = directory.getPackage() == null;

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

    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    IdeView view = DataKeys.IDE_VIEW.getData(dataContext);
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
    for (PsiDirectory directory : directories) {
      if (directory.getPackage() != null) {
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
    private Project myProject;
    private PsiDirectory myDirectory;
    private boolean myIsDirectory;
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

      if (!myIsDirectory) {
        PsiNameHelper helper = PsiManager.getInstance(myProject).getNameHelper();
        if (!helper.isQualifiedName(subDirName)) {
          Messages.showMessageDialog(myProject, IdeBundle.message("a.valid.package.name.should.be.specified"),
                                     IdeBundle.message("title.cannot.create.package"), Messages.getErrorIcon());
          return false;
        }
      }

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
                if (myIsDirectory) {
                  String dirPath = myDirectory.getVirtualFile().getPresentableUrl();
                  actionName = IdeBundle.message("progress.creating.directory", dirPath, File.separator, subDirName);
                }
                else {
                  String packagePath = myDirectory.getPackage().getQualifiedName();
                  actionName = IdeBundle.message("progress.creating.package", packagePath, subDirName);
                }
                action = LocalHistory.startAction(myProject, actionName);

                final PsiDirectory createdDir;
                if (myIsDirectory) {
                  createdDir = myDirectory.createSubdirectory(subDirName);
                }
                else {
                  StringTokenizer tokenizer = new StringTokenizer(subDirName, ".");
                  PsiDirectory dir = myDirectory;
                  while (tokenizer.hasMoreTokens()) {
                    String packName = tokenizer.nextToken();
                    if (tokenizer.hasMoreTokens()) {
                      PsiDirectory existingDir = dir.findSubdirectory(packName);
                      if (existingDir != null) {
                        dir = existingDir;
                        continue;
                      }
                    }
                    dir = dir.createSubdirectory(packName);
                  }
                  createdDir = dir;
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
