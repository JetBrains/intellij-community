package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;

import org.jetbrains.annotations.NonNls;

public class MoveFilesOrDirectoriesDialog extends DialogWrapper{
  public static interface Callback {
    void run(MoveFilesOrDirectoriesDialog dialog);
  }

  private JLabel myNameLabel;
  private TextFieldWithBrowseButton myTargetDirectoryField;
  private String myHelpID;
  private Project myProject;
  private final Callback myCallback;
  private PsiDirectory myTargetDirectory;

  public MoveFilesOrDirectoriesDialog(Project project, Callback callback) {
    super(project, true);
    myProject = project;
    myCallback = callback;
    setTitle(RefactoringBundle.message("move.title"));
    init();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myTargetDirectoryField.getTextField();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    panel.setBorder(IdeBorderFactory.createBorder());

    myNameLabel = new JLabel();
    panel.add(myNameLabel, new GridBagConstraints(0,0,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,8,4,8),0,0));

    panel.add(new JLabel(RefactoringBundle.message("move.files.to.directory.label")),
              new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,8,4,8),0,0));

    myTargetDirectoryField = new TextFieldWithBrowseButton();
    myTargetDirectoryField.addBrowseFolderListener(RefactoringBundle.message("select.target.directory"),
                                                   RefactoringBundle.message("the.file.will.be.moved.to.this.directory"),
                                                   null, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myTargetDirectoryField.setTextFieldPreferredWidth(60);
    panel.add(myTargetDirectoryField, new GridBagConstraints(1,1,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,0,4,8),0,0));

    myTargetDirectoryField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        validateOKButton();
      }
    });

    return panel;
  }

  public void setData(PsiElement[] psiElements, PsiDirectory initialTargetDirectory, @NonNls String helpID) {
    if (psiElements.length == 1) {
      String text;
      if (psiElements[0] instanceof PsiFile) {
        text = RefactoringBundle.message("move.file.0",
                                         ((PsiFile)psiElements[0]).getVirtualFile().getPresentableUrl());
      }
      else {
        text = RefactoringBundle.message("move.directory.0",
                                         ((PsiDirectory)psiElements[0]).getVirtualFile().getPresentableUrl());
      }
      myNameLabel.setText(text);
    }
    else {
      myNameLabel.setText((psiElements[0] instanceof PsiFile)?
                          RefactoringBundle.message("move.specified.files") :
                          RefactoringBundle.message("move.specified.directories"));
    }
    myTargetDirectoryField.setText(initialTargetDirectory == null ? "" : initialTargetDirectory.getVirtualFile().getPresentableUrl());

    validateOKButton();
    myHelpID = helpID;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  private void validateOKButton() {
    setOKActionEnabled(myTargetDirectoryField.getText().length() > 0);
  }

  protected void doOKAction() {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            String directoryName = myTargetDirectoryField.getText().replace(File.separatorChar, '/');
            try {
              myTargetDirectory = DirectoryUtil.mkdirs(PsiManager.getInstance(myProject), directoryName);
            }
            catch (IncorrectOperationException e) {
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, RefactoringBundle.message("create.directory"), null);
    if (myTargetDirectory == null){
      CommonRefactoringUtil.showErrorMessage(MoveFilesOrDirectoriesDialog.this.getTitle(),
                                              RefactoringBundle.message("cannot.create.directory"), myHelpID, myProject);
      return;
    }
    myCallback.run(this);
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }
}
