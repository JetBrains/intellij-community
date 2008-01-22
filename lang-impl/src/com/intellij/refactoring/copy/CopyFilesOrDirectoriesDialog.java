package com.intellij.refactoring.copy;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;

class CopyFilesOrDirectoriesDialog extends DialogWrapper{
  private JLabel myInformationLabel;
  private TextFieldWithBrowseButton myTargetDirectoryField;
  private JTextField myNewNameField;
  private Project myProject;
  private boolean myShowDirectoryField;
  private boolean myShowNewNameField;

  private PsiDirectory myTargetDirectory;

  public CopyFilesOrDirectoriesDialog(PsiElement[] elements, PsiDirectory defaultTargetDirectory, Project project, boolean doClone) {
    super(project, true);
    myProject = project;
    myShowDirectoryField = !doClone;
    myShowNewNameField = elements.length == 1;

    if (doClone && elements.length != 1) {
      throw new IllegalArgumentException("wrong number of elements to clone: " + elements.length);
    }

    setTitle(doClone ?
             RefactoringBundle.message("copy.files.clone.title") :
             RefactoringBundle.message("copy.files.copy.title"));
    init();

    if (elements.length == 1) {
      String text;
      if (elements[0] instanceof PsiFile) {
        PsiFile file = (PsiFile)elements[0];
        text = doClone ?
               RefactoringBundle.message("copy.files.clone.file.0", file.getVirtualFile().getPresentableUrl()) :
               RefactoringBundle.message("copy.files.copy.file.0", file.getVirtualFile().getPresentableUrl());
        myNewNameField.setText(file.getName());
      }
      else {
        PsiDirectory directory = (PsiDirectory)elements[0];
        text = doClone ?
               RefactoringBundle.message("copy.files.clone.directory.0", directory.getVirtualFile().getPresentableUrl()) :
               RefactoringBundle.message("copy.files.copy.directory.0", directory.getVirtualFile().getPresentableUrl());
        myNewNameField.setText(directory.getName());
      }
      myInformationLabel.setText(text);
    }
    else {
      myInformationLabel.setText((elements[0] instanceof PsiFile)?
                                 RefactoringBundle.message("copy.files.copy.specified.files.label") :
                                 RefactoringBundle.message("copy.files.copy.specified.directories.label"));
    }

    if (myShowDirectoryField) {
      myTargetDirectoryField.setText(defaultTargetDirectory == null ? "" : defaultTargetDirectory.getVirtualFile().getPresentableUrl());
    }
    validateOKButton();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myShowDirectoryField ? myTargetDirectoryField.getTextField() : myNewNameField;
  }

  protected JComponent createCenterPanel() {
    return new JPanel(new BorderLayout());
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    panel.setBorder(IdeBorderFactory.createBorder());

    myInformationLabel = new JLabel();

    panel.add(myInformationLabel, new GridBagConstraints(0,0,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,8,4,8),0,0));

    DocumentListener documentListener = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        validateOKButton();
      }
    };

    if (myShowDirectoryField) {
      panel.add(new JLabel(RefactoringBundle.message("copy.files.to.directory.label")), new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,8,4,8),0,0));

      myTargetDirectoryField = new TextFieldWithBrowseButton();
      myTargetDirectoryField.addBrowseFolderListener(RefactoringBundle.message("select.target.directory"),
                                                     RefactoringBundle.message("the.file.will.be.copied.to.this.directory"),
                                                     null,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor());
      myTargetDirectoryField.setTextFieldPreferredWidth(60);
      panel.add(myTargetDirectoryField, new GridBagConstraints(1,1,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,0,4,8),0,0));

      myTargetDirectoryField.getTextField().getDocument().addDocumentListener(documentListener);
    }

    if (myShowNewNameField) {
      myNewNameField = new JTextField();
      Dimension size = myNewNameField.getPreferredSize();
      FontMetrics fontMetrics = myNewNameField.getFontMetrics(myNewNameField.getFont());
      size.width = fontMetrics.charWidth('a') * 60;
      myNewNameField.setPreferredSize(size);

      panel.add(new JLabel(RefactoringBundle.message("copy.files.new.name.label")), new GridBagConstraints(0,2,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,8,4,8),0,0));

      panel.add(myNewNameField, new GridBagConstraints(1,2,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,0,4,8),0,0));

      myNewNameField.getDocument().addDocumentListener(documentListener);
    }

    return panel;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  public String getNewName() {
    return myNewNameField != null ? myNewNameField.getText().trim() : null;
  }

  protected void doOKAction(){
    if (myShowNewNameField) {
      String newName = getNewName();

      if (newName.length() == 0) {
        Messages.showMessageDialog(myProject, RefactoringBundle.message("no.new.name.specified"), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
        return;
      }
    }

    if (myShowDirectoryField) {
      final String targetDirectoryName = myTargetDirectoryField.getText();

      if (targetDirectoryName.length() == 0) {
        Messages.showMessageDialog(myProject, RefactoringBundle.message("no.target.directory.specified"), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
        return;
      }

      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                myTargetDirectory = DirectoryUtil.mkdirs(PsiManager.getInstance(myProject), targetDirectoryName.replace(File.separatorChar, '/'));
              }
              catch (IncorrectOperationException e) {
              }
            }
          });
        }
      }, RefactoringBundle.message("create.directory"), null);

      if (myTargetDirectory == null) {
        Messages.showMessageDialog(myProject, RefactoringBundle.message("cannot.create.directory"), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
        return;
      }
    }

    super.doOKAction();
  }

  private void validateOKButton() {
    if (myShowDirectoryField) {
      if (myTargetDirectoryField.getText().length() == 0) {
        setOKActionEnabled(false);
        return;
      }
    }
    if (myShowNewNameField) {
      if (getNewName().length() == 0) {
        setOKActionEnabled(false);
        return;
      }
    }
    setOKActionEnabled(true);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("refactoring.copyClass");
  }
}
