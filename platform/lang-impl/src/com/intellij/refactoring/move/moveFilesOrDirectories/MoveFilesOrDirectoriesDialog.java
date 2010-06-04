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

package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.util.DirectoryUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;

public class MoveFilesOrDirectoriesDialog extends DialogWrapper{
  @NonNls private static final String RECENT_KEYS = "MoveFile.RECENT_KEYS";

  public interface Callback {
    void run(MoveFilesOrDirectoriesDialog dialog);
  }

  private JLabel myNameLabel;
  private ComponentWithBrowseButton<TextFieldWithStoredHistory> myTargetDirectoryField;
  private String myHelpID;
  private final Project myProject;
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
    return myTargetDirectoryField.getChildComponent();
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

    myTargetDirectoryField = new ComponentWithBrowseButton<TextFieldWithStoredHistory>(new TextFieldWithStoredHistory(RECENT_KEYS), null);
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myTargetDirectoryField.addBrowseFolderListener(RefactoringBundle.message("select.target.directory"),
                                                   RefactoringBundle.message("the.file.will.be.moved.to.this.directory"),
                                                   myProject,
                                                   descriptor,
                                                   TextComponentAccessor.TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT);
    final TextFieldWithStoredHistory textFieldWithStoredHistory = myTargetDirectoryField.getChildComponent();
    FileChooserFactory.getInstance().installFileCompletion(textFieldWithStoredHistory.getTextEditor(), descriptor, true, getDisposable());
    myTargetDirectoryField.setTextFieldPreferredWidth(60);
    panel.add(myTargetDirectoryField, new GridBagConstraints(1,1,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(4,0,4,8),0,0));

    textFieldWithStoredHistory.addDocumentListener(new DocumentAdapter(){
      @Override
      protected void textChanged(DocumentEvent e) {
        validateOKButton();
      }
    });
    Disposer.register(getDisposable(), myTargetDirectoryField);

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
      boolean isFile = true;
      boolean isDirectory = true;
      for (PsiElement psiElement : psiElements) {
        isFile &= psiElement instanceof PsiFile;
        isDirectory &= psiElement instanceof PsiDirectory;
      }
      myNameLabel.setText(isFile ?
                          RefactoringBundle.message("move.specified.files") :
                          isDirectory ?
                          RefactoringBundle.message("move.specified.directories") :
                          RefactoringBundle.message("move.specified.elements"));
    }

    myTargetDirectoryField.getChildComponent().setText(initialTargetDirectory == null ? "" : initialTargetDirectory.getVirtualFile().getPresentableUrl());

    validateOKButton();
    myHelpID = helpID;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  private void validateOKButton() {
    setOKActionEnabled(myTargetDirectoryField.getChildComponent().getText().length() > 0);
  }

  protected void doOKAction() {
    myTargetDirectoryField.getChildComponent().addCurrentTextToHistory();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            String directoryName = myTargetDirectoryField.getChildComponent().getText().replace(File.separatorChar, '/');
            try {
              myTargetDirectory = DirectoryUtil.mkdirs(PsiManager.getInstance(myProject), directoryName);
            }
            catch (IncorrectOperationException e) {
              // ignore
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, RefactoringBundle.message("create.directory"), null);
    if (myTargetDirectory == null){
      CommonRefactoringUtil.showErrorMessage(getTitle(),
                                              RefactoringBundle.message("cannot.create.directory"), myHelpID, myProject);
      return;
    }
    myCallback.run(this);
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }
}
