/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.ui.components.JBLabelDecorator;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.File;
import java.util.List;

public class MoveFilesOrDirectoriesDialog extends DialogWrapper {
  @NonNls private static final String RECENT_KEYS = "MoveFile.RECENT_KEYS";
  @NonNls private static final String MOVE_FILES_OPEN_IN_EDITOR = "MoveFile.OpenInEditor";
  

  public interface Callback {
    void run(MoveFilesOrDirectoriesDialog dialog);
  }

  private JLabel myNameLabel;
  private TextFieldWithHistoryWithBrowseButton myTargetDirectoryField;
  private String myHelpID;
  private final Project myProject;
  private final Callback myCallback;
  private PsiDirectory myTargetDirectory;
  private JCheckBox myCbSearchForReferences;
  private JCheckBox myOpenInEditorCb;

  public MoveFilesOrDirectoriesDialog(Project project, Callback callback) {
    super(project, true);
    myProject = project;
    myCallback = callback;
    setTitle(RefactoringBundle.message("move.title"));
    init();
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTargetDirectoryField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    myNameLabel = JBLabelDecorator.createJBLabelDecorator().setBold(true);

    myTargetDirectoryField = new TextFieldWithHistoryWithBrowseButton();
    final List<String> recentEntries = RecentsManager.getInstance(myProject).getRecentEntries(RECENT_KEYS);
    if (recentEntries != null) {
      myTargetDirectoryField.getChildComponent().setHistory(recentEntries);
    }
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myTargetDirectoryField.addBrowseFolderListener(RefactoringBundle.message("select.target.directory"),
                                                   RefactoringBundle.message("the.file.will.be.moved.to.this.directory"),
                                                   myProject,
                                                   descriptor,
                                                   TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
    final JTextField textField = myTargetDirectoryField.getChildComponent().getTextEditor();
    FileChooserFactory.getInstance().installFileCompletion(textField, descriptor, true, getDisposable());
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateOKButton();
      }
    });
    myTargetDirectoryField.setTextFieldPreferredWidth(CopyFilesOrDirectoriesDialog.MAX_PATH_LENGTH);
    Disposer.register(getDisposable(), myTargetDirectoryField);

    String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));

    myCbSearchForReferences = new NonFocusableCheckBox(RefactoringBundle.message("search.for.references"));
    myCbSearchForReferences.setSelected(RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE);

    myOpenInEditorCb = new NonFocusableCheckBox("Open moved files in editor");
    myOpenInEditorCb.setSelected(isOpenInEditor());
    
    return FormBuilder.createFormBuilder().addComponent(myNameLabel)
      .addLabeledComponent(RefactoringBundle.message("move.files.to.directory.label"), myTargetDirectoryField, UIUtil.LARGE_VGAP)
      .addTooltip(RefactoringBundle.message("path.completion.shortcut", shortcutText))
      .addComponentToRightColumn(myCbSearchForReferences, UIUtil.LARGE_VGAP)
      .addComponentToRightColumn(myOpenInEditorCb, UIUtil.LARGE_VGAP)
      .getPanel();
  }

  public void setData(PsiElement[] psiElements, PsiDirectory initialTargetDirectory, @NonNls String helpID) {
    if (psiElements.length == 1) {
      String text;
      if (psiElements[0] instanceof PsiFile) {
        text = RefactoringBundle.message("move.file.0",
                                         CopyFilesOrDirectoriesDialog.shortenPath(((PsiFile)psiElements[0]).getVirtualFile()));
      }
      else {
        text = RefactoringBundle.message("move.directory.0",
                                         CopyFilesOrDirectoriesDialog.shortenPath(((PsiDirectory)psiElements[0]).getVirtualFile()));
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

    final String initialTargetPath = initialTargetDirectory == null ? "" : initialTargetDirectory.getVirtualFile().getPresentableUrl();
    myTargetDirectoryField.getChildComponent().setText(initialTargetPath);
    final int lastDirectoryIdx = initialTargetPath.lastIndexOf(File.separator);
    final int textLength = initialTargetPath.length();
    if (lastDirectoryIdx > 0 && lastDirectoryIdx + 1 < textLength) {
      myTargetDirectoryField.getChildComponent().getTextEditor().select(lastDirectoryIdx + 1, textLength);
    }

    validateOKButton();
    myHelpID = helpID;
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }

  public static boolean isOpenInEditor() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return false;
    }
    return PropertiesComponent.getInstance().getBoolean(MOVE_FILES_OPEN_IN_EDITOR, false);
  } 

  private void validateOKButton() {
    setOKActionEnabled(myTargetDirectoryField.getChildComponent().getText().length() > 0);
  }

  @Override
  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(MOVE_FILES_OPEN_IN_EDITOR, myOpenInEditorCb.isSelected(), false);
    //myTargetDirectoryField.getChildComponent().addCurrentTextToHistory();
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENT_KEYS, myTargetDirectoryField.getChildComponent().getText());
    RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE = myCbSearchForReferences.isSelected();

    if (DumbService.isDumb(myProject)) {
      Messages.showMessageDialog(myProject, "Move refactoring is not available while indexing is in progress", "Indexing", null);
      return;
    }
    
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      final Runnable action = () -> {
        String directoryName = myTargetDirectoryField.getChildComponent().getText().replace(File.separatorChar, '/');
        try {
          myTargetDirectory = DirectoryUtil.mkdirs(PsiManager.getInstance(myProject), directoryName);
        }
        catch (IncorrectOperationException e) {
          // ignore
        }
      };

      DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_MODAL, () -> {
        ApplicationManager.getApplication().runWriteAction(action);
        if (myTargetDirectory == null) {
          CommonRefactoringUtil.showErrorMessage(getTitle(),
                                                 RefactoringBundle.message("cannot.create.directory"), myHelpID, myProject);
          return;
        }
        myCallback.run(this);
      });
    }, RefactoringBundle.message("move.title"), null);
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  @Override
  public void show() {
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, () -> super.show());
  }

}
