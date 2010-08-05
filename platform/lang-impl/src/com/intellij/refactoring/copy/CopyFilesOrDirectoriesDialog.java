/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.refactoring.copy;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;

class CopyFilesOrDirectoriesDialog extends DialogWrapper{
  private JLabel myInformationLabel;
  private EditorComboWithBrowseButton myTargetDirectoryField;
  private JTextField myNewNameField;
  private final Project myProject;
  private final boolean myShowDirectoryField;
  private final boolean myShowNewNameField;

  private PsiDirectory myTargetDirectory;
  @NonNls private static final String RECENT_KEYS = "CopyFile.RECENT_KEYS";

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
      setMultipleElementCopyLabel(elements);
    }

    if (myShowDirectoryField) {
      myTargetDirectoryField.prependItem(defaultTargetDirectory == null ? "" : defaultTargetDirectory.getVirtualFile().getPresentableUrl());
    }
    validateOKButton();
  }

  private void setMultipleElementCopyLabel(PsiElement[] elements) {
    boolean allFiles = true;
    boolean allDirectories = true;
    for (PsiElement element : elements) {
      if (element instanceof PsiDirectory) {
        allFiles = false;
      }
      else {
        allDirectories = false;
      }
    }
    if (allFiles) {
      myInformationLabel.setText(RefactoringBundle.message("copy.files.copy.specified.files.label"));
    }
    else if (allDirectories) {
      myInformationLabel.setText(RefactoringBundle.message("copy.files.copy.specified.directories.label"));
    }
    else {
      myInformationLabel.setText(RefactoringBundle.message("copy.files.copy.specified.mixed.label"));
    }
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myShowDirectoryField ? myTargetDirectoryField.getChildComponent() : myNewNameField;
  }

  protected JComponent createCenterPanel() {
    return new JPanel(new BorderLayout());
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    panel.setBorder(IdeBorderFactory.createRoundedBorder());

    myInformationLabel = new JLabel();

    panel.add(myInformationLabel, new GridBagConstraints(0,0,2,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,8,4,8),0,0));

    DocumentListener documentListener = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        validateOKButton();
      }
    };

    if (myShowDirectoryField) {
      panel.add(new JLabel(RefactoringBundle.message("copy.files.to.directory.label")), new GridBagConstraints(0,1,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,8,4,8),0,0));

      myTargetDirectoryField = new EditorComboWithBrowseButton(null, "", myProject,
                                                               RECENT_KEYS);
      myTargetDirectoryField.addBrowseFolderListener(RefactoringBundle.message("select.target.directory"),
                                                                            RefactoringBundle.message("the.file.will.be.copied.to.this.directory"),
                                                                            myProject, FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                                            EditorComboBox.COMPONENT_ACCESSOR);
      myTargetDirectoryField.setTextFieldPreferredWidth(60);
      panel.add(myTargetDirectoryField, new GridBagConstraints(1,1,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,0,4,8),0,0));

      myTargetDirectoryField.getChildComponent().getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
        @Override
        public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
          validateOKButton();
        }
      });
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

      RecentsManager.getInstance(myProject).registerRecentEntry(RECENT_KEYS, targetDirectoryName);

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
