// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

/**
 * @deprecated Use {@link CreateDirectoryPathFix} or {@link CreateFilePathFix} instead.
*/
@Deprecated
public class CreateFileFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final int REFRESH_INTERVAL = 1000;

  private final boolean myIsDirectory;
  private final String myNewFileName;
  private final String myText;
  private final @PropertyKey(resourceBundle = CodeInsightBundle.BUNDLE) @NotNull String myKey;
  private boolean myIsAvailable;
  private long myIsAvailableTimeStamp;

  // invoked from another module
  @SuppressWarnings("WeakerAccess")
  public CreateFileFix(boolean isDirectory,
                       @NotNull String newFileName,
                       @NotNull PsiDirectory directory,
                       @Nullable String text,
                       @PropertyKey(resourceBundle = CodeInsightBundle.BUNDLE) @NotNull String key) {
    super(directory);

    myIsDirectory = isDirectory;
    myNewFileName = newFileName;
    myText = text;
    myKey = key;
    myIsAvailable = isDirectory || !FileTypeManager.getInstance().getFileTypeByFileName(newFileName).isBinary();
    myIsAvailableTimeStamp = System.currentTimeMillis();
  }

  public CreateFileFix(@NotNull String newFileName, @NotNull PsiDirectory directory, String text) {
    this(false,newFileName,directory, text, "create.file.text");
  }

  public CreateFileFix(final boolean isDirectory, @NotNull String newFileName, @NotNull PsiDirectory directory) {
    this(isDirectory,newFileName,directory,null, isDirectory ? "create.directory.text":"create.file.text" );
  }

  protected @Nullable String getFileText() {
    return myText;
  }

  @Override
  public @NotNull String getText() {
    return CodeInsightBundle.message(myKey, myNewFileName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("create.file.family");
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return null;
  }

  @Override
  public void invoke(final @NotNull Project project,
                     @NotNull PsiFile psiFile,
                     Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (isAvailable(project, null, psiFile)) {
      invoke(project, (PsiDirectory)startElement);
    }
  }

  @Override
  public void applyFix() {
    invoke(myStartElement.getProject(), (PsiDirectory)myStartElement.getElement());
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiDirectory myDirectory = (PsiDirectory)startElement;
    long current = System.currentTimeMillis();

    if (ApplicationManager.getApplication().isUnitTestMode() || current - myIsAvailableTimeStamp > REFRESH_INTERVAL) {
      myIsAvailable &= myDirectory.getVirtualFile().findChild(myNewFileName) == null;
      myIsAvailableTimeStamp = current;
    }

    return myIsAvailable;
  }

  private void invoke(@NotNull Project project, PsiDirectory myDirectory) throws IncorrectOperationException {
    myIsAvailableTimeStamp = 0; // to revalidate applicability

    try {
      if (myIsDirectory) {
        myDirectory.createSubdirectory(myNewFileName);
      }
      else {
        var targetFile = CreateFilePathFix.createFileForFix(project, myDirectory, myNewFileName, getFileText());
        if (targetFile !=null) {
          openFile(project, targetFile.directory(), targetFile.newFile(), targetFile.text());
        }
      }
    }
    catch (IncorrectOperationException e) {
      myIsAvailable = false;
    }
  }

  protected void openFile(@NotNull Project project, PsiDirectory directory, PsiFile newFile, String text) {
    final FileEditorManager editorManager = FileEditorManager.getInstance(directory.getProject());
    final FileEditor[] fileEditors = editorManager.openFile(newFile.getVirtualFile(), true);

    if (text != null) {
      for(FileEditor fileEditor: fileEditors) {
        if (fileEditor instanceof TextEditor textEditor) { // JSP is not safe to edit via Psi
          final Document document = textEditor.getEditor().getDocument();
          document.setText(text);

          if (ApplicationManager.getApplication().isUnitTestMode()) {
            FileDocumentManager.getInstance().saveDocument(document);
          }
          PsiDocumentManager.getInstance(project).commitDocument(document);
          break;
        }
      }
    }
  }
}
