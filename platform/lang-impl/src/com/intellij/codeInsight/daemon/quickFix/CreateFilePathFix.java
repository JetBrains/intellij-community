// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Quick fix that creates a new file in one of the target directories. Automatically creates all intermediate directories of
 * {@link TargetDirectory#getPathToCreate()} and {@link NewFileLocation#getSubPath()}. If there are multiple target directories, it shows
 * a popup where users can select the desired target directory.
 */
public class CreateFilePathFix extends AbstractCreateFileFix {
  private final String myText;
  private @Nullable Supplier<String> myFileTextSupplier;

  // invoked from another module
  @SuppressWarnings("WeakerAccess")
  public CreateFilePathFix(@NotNull PsiElement psiElement,
                           @NotNull NewFileLocation newFileLocation,
                           @Nullable String fileText,
                           @NotNull String fixLocaleKey) {
    super(psiElement, newFileLocation, fixLocaleKey);

    myText = fileText;
    myIsAvailable = !FileTypeManager.getInstance().getFileTypeByFileName(myNewFileName).isBinary();
    myIsAvailableTimeStamp = System.currentTimeMillis();
  }

  public CreateFilePathFix(@NotNull PsiElement psiElement,
                           @NotNull NewFileLocation newFileLocation) {
    this(psiElement, newFileLocation, null, "create.file.text");
  }

  public CreateFilePathFix(@NotNull PsiElement psiElement,
                           @NotNull NewFileLocation newFileLocation,
                           @NotNull Supplier<String> fileTextSupplier) {
    this(psiElement, newFileLocation, null, "create.file.text");

    myFileTextSupplier = fileTextSupplier;
  }

  private void createFile(@NotNull Project project,
                          @Nullable Editor editor, @NotNull Supplier<? extends @Nullable PsiDirectory> currentDirectory,
                          @NotNull String fileName) throws IncorrectOperationException {
    CreatedFile target;
    try {
      target = WriteCommandAction.writeCommandAction(project)
        .withName(CodeInsightBundle.message(myKey, myNewFileName))
        .compute(() -> {
          PsiDirectory toDirectory = currentDirectory.get();
          if (toDirectory == null) return null;

          return createFileForFix(project, toDirectory, fileName, getFileText());
        });
    }
    catch (IncorrectCreateFilePathException e) {
      if (editor != null) {
        HintManager.getInstance().showErrorHint(editor, e.getLocalizedMessage());
      }
      return;
    }

    if (target != null) {
      openFile(project, target.directory(), target.newFile(), target.text());
    }
  }

  @RequiresWriteLock
  public static @Nullable CreateFilePathFix.CreatedFile createFileForFix(@NotNull Project project,
                                                                         @NotNull PsiDirectory currentDirectory,
                                                                         @NotNull String fileName,
                                                                         @Nullable String fileContent) {
    String newFileName = fileName;
    String newDirectories = null;
    if (fileName.contains("/")) {
      int pos = fileName.lastIndexOf('/');
      newFileName = fileName.substring(pos + 1);
      newDirectories = fileName.substring(0, pos);
    }
    PsiDirectory directory = currentDirectory;
    if (newDirectories != null) {
      try {
        VfsUtil.createDirectoryIfMissing(currentDirectory.getVirtualFile(), newDirectories);
        VirtualFile vfsDir = VfsUtil.findRelativeFile(currentDirectory.getVirtualFile(),
                                                      ArrayUtilRt.toStringArray(StringUtil.split(newDirectories, "/")));

        if (vfsDir == null) {
          Logger.getInstance(CreateFilePathFix.class)
            .warn("Unable to find relative file" + currentDirectory.getVirtualFile().getPath());
          return null;
        }

        directory = currentDirectory.getManager().findDirectory(vfsDir);
        if (directory == null) throw new IOException("Couldn't create directory '" + newDirectories + "'");
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e.getMessage());
      }
    }
    PsiFile newFile = directory.createFile(newFileName);
    String text = fileContent;

    if (text != null) {
      FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(newFileName);
      PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("_" + newFileName, type, text);
      PsiElement psiElement = CodeStyleManager.getInstance(project).reformat(psiFile);
      text = psiElement.getText();
    }

    return new CreatedFile(directory, newFile, text);
  }

  public record CreatedFile(
    PsiDirectory directory,
    PsiFile newFile,
    String text
  ) {
  }

  protected void openFile(@NotNull Project project, PsiDirectory directory, PsiFile newFile, String text) {
    FileEditorManager editorManager = FileEditorManager.getInstance(directory.getProject());
    FileEditor[] fileEditors = editorManager.openFile(newFile.getVirtualFile(), true);

    WriteAction.run(() -> {
      if (text != null) {
        for (FileEditor fileEditor : fileEditors) {
          if (fileEditor instanceof TextEditor textEditor) { // JSP is not safe to edit via Psi
            Document document = textEditor.getEditor().getDocument();
            document.setText(text);

            if (ApplicationManager.getApplication().isUnitTestMode()) {
              FileDocumentManager.getInstance().saveDocument(document);
            }
            PsiDocumentManager.getInstance(project).commitDocument(document);
            break;
          }
        }
      }
    });
  }

  protected @Nullable String getFileText() {
    if (myFileTextSupplier != null) {
      return myFileTextSupplier.get();
    }
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
  protected void apply(@NotNull Project project,
                       @NotNull Supplier<? extends @Nullable PsiDirectory> targetDirectory,
                       @Nullable Editor editor)
    throws IncorrectOperationException {

    createFile(project, editor, targetDirectory, myNewFileName);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    String extension = StringUtil.substringAfterLast(myNewFileName, ".");
    Icon icon =
      extension == null ? AllIcons.FileTypes.Any_type : FileTypeRegistry.getInstance().getFileTypeByExtension(extension).getIcon();
    HtmlChunk description = getDescription(icon);
    return description == null ? IntentionPreviewInfo.EMPTY : new IntentionPreviewInfo.Html(description);
  }
}
