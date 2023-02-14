// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesDialog;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.ui.components.JBLabelDecorator;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.File;
import java.util.Collections;
import java.util.List;

public abstract class MoveFilesOrDirectoriesDialog extends RefactoringDialog {
  private static final String MOVE_FILE = "MoveFile";
  private static final String RECENT_KEYS = "MoveFile.RECENT_KEYS";
  private static final String MOVE_FILES_OPEN_IN_EDITOR = "MoveFile.OpenInEditor";

  private JLabel myNameLabel;
  private TextFieldWithHistoryWithBrowseButton myTargetDirectoryField;
  private JCheckBox myCbSearchForReferences;
  private boolean isDumb;

  public MoveFilesOrDirectoriesDialog(@NotNull Project project, PsiElement @NotNull [] psiElements, PsiDirectory initialTargetDirectory) {
    super(project, true, true);
    setTitle(RefactoringBundle.message("move.title"));
    init();

    if (psiElements.length == 1) {
      PsiFileSystemItem element = (PsiFileSystemItem)psiElements[0];
      String path = CopyFilesOrDirectoriesDialog.shortenPath(element.getVirtualFile());
      String text = RefactoringBundle.message(element instanceof PsiFile ? "move.file.0" : "move.directory.0", path);
      myNameLabel.setText(text);
    }
    else {
      boolean isFile = true;
      boolean isDirectory = true;
      for (PsiElement psiElement : psiElements) {
        isFile &= psiElement instanceof PsiFile;
        isDirectory &= psiElement instanceof PsiDirectory;
      }
      myNameLabel.setText(isFile ? RefactoringBundle.message("move.specified.files") :
                          isDirectory ? RefactoringBundle.message("move.specified.directories")
                                      : RefactoringBundle.message("move.specified.elements"));
    }

    String initialTargetPath = initialTargetDirectory == null ? "" : initialTargetDirectory.getVirtualFile().getPresentableUrl();
    myTargetDirectoryField.getChildComponent().setText(initialTargetPath);
    int lastDirectoryIdx = initialTargetPath.lastIndexOf(File.separator);
    int textLength = initialTargetPath.length();
    if (lastDirectoryIdx > 0 && lastDirectoryIdx + 1 < textLength) {
      myTargetDirectoryField.getChildComponent().getTextEditor().select(lastDirectoryIdx + 1, textLength);
    }

    validateButtons();
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
                                                   TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
    final JTextField textField = myTargetDirectoryField.getChildComponent().getTextEditor();
    FileChooserFactory.getInstance().installFileCompletion(textField, descriptor, true, getDisposable());
    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        validateButtons();
      }
    });
    myTargetDirectoryField.setTextFieldPreferredWidth(CopyFilesOrDirectoriesDialog.MAX_PATH_LENGTH);
    Disposer.register(getDisposable(), myTargetDirectoryField);

    String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));

    isDumb = DumbService.isDumb(myProject);
    myCbSearchForReferences = new NonFocusableCheckBox(isDumb ?
                                                       RefactoringBundle.message("search.for.references.dumb.mode") :
                                                       RefactoringBundle.message("search.for.references"));
    myCbSearchForReferences.setSelected(!isDumb && RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE);
    myCbSearchForReferences.setEnabled(!isDumb);

    return FormBuilder.createFormBuilder().addComponent(myNameLabel)
      .addLabeledComponent(RefactoringBundle.message("move.files.to.directory.label"), myTargetDirectoryField, UIUtil.LARGE_VGAP)
      .addTooltip(RefactoringBundle.message("path.completion.shortcut", shortcutText))
      .addComponentToRightColumn(myCbSearchForReferences, UIUtil.LARGE_VGAP)
      .getPanel();
  }

  @Override
  protected @NotNull String getRefactoringId() {
    return MOVE_FILE;
  }

  @Override
  protected String getHelpId() {
    return "refactoring.moveFile";
  }

  public static boolean isOpenInEditorProperty() {
    return !ApplicationManager.getApplication().isUnitTestMode() &&
           PropertiesComponent.getInstance().getBoolean(MOVE_FILES_OPEN_IN_EDITOR, false);
  }

  @Override
  protected boolean isOpenInEditorEnabledByDefault() {
    return false;
  }

  @Override
  protected boolean hasPreviewButton() {
    return false;
  }

  @Override
  protected boolean areButtonsValid() {
    return myTargetDirectoryField.getChildComponent().getText().length() > 0;
  }

  @Override
  protected void doAction() {
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENT_KEYS, myTargetDirectoryField.getChildComponent().getText());
    if (!isDumb) {
      RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE = myCbSearchForReferences.isSelected();
    }

    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      String directoryName = myTargetDirectoryField.getChildComponent().getText().replace(File.separatorChar, '/');
      PsiDirectory directory = DirectoryUtil.findLongestExistingDirectory(PsiManager.getInstance(myProject), directoryName);
      PsiDirectory targetDirectory =
        directory == null || !CommonRefactoringUtil.checkReadOnlyStatus(myProject, Collections.singletonList(directory), false)
        ? null
        : ApplicationManager.getApplication().runWriteAction((Computable<PsiDirectory>)() -> {
        try {
          return DirectoryUtil.mkdirs(PsiManager.getInstance(myProject), directoryName);
        }
        catch (IncorrectOperationException ignored) {
          return null;
        }
      });

      if (targetDirectory == null) {
        CommonRefactoringUtil.showErrorMessage(getTitle(), RefactoringBundle.message("cannot.create.directory"), getHelpId(), myProject);
      }
      else {
        performMove(targetDirectory);
      }
    }, RefactoringBundle.message("move.title"), null);
  }

  protected abstract void performMove(@NotNull PsiDirectory targetDirectory);
}
