/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class CreateFileFix implements IntentionAction, LocalQuickFix {
  private final boolean myIsdirectory;
  private final String myNewFileName;
  private final PsiDirectory myDirectory;
  private final @Nullable String myText;
  private @NotNull String myKey;
  private boolean myIsAvailable;
  private long myIsAvailableTimeStamp;
  private static final int REFRESH_INTERVAL = 1000;

  public CreateFileFix(final boolean isdirectory,
                                   final String newFileName,
                                   final PsiDirectory directory,
                                   @Nullable String text,
                                   @NotNull String key) {
    myIsdirectory = isdirectory;
    myNewFileName = newFileName;
    myDirectory = directory;
    myText = text;
    myKey = key;
    myIsAvailable = isdirectory || !FileTypeManager.getInstance().getFileTypeByFileName(newFileName).isBinary();
    myIsAvailableTimeStamp = System.currentTimeMillis();
  }

  public CreateFileFix(final boolean isdirectory,
                                   final String newFileName,
                                   final PsiDirectory directory) {
    this(isdirectory,newFileName,directory,null,isdirectory ? "create.directory.text":"create.file.text" );
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message(myKey, myNewFileName);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.file.family");
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    if (isAvailable(project, null, null)) {
      new WriteCommandAction(project) {
        protected void run(Result result) throws Throwable {
          invoke(project, null, null);
        }
      }.execute();
    }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    long current = System.currentTimeMillis();

    if (current - myIsAvailableTimeStamp > REFRESH_INTERVAL) {
      myIsAvailable = myDirectory.getVirtualFile().findChild(myNewFileName) == null;
      myIsAvailableTimeStamp = current;
    }

    return myIsAvailable;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myIsAvailableTimeStamp = 0; // to revalidate applicability

    try {
      if (myIsdirectory) {
        myDirectory.createSubdirectory(myNewFileName);
      }
      else {
        final PsiFile newfile = myDirectory.createFile(myNewFileName);
        String text = null;

        if (myText != null) {
          final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("_" + myNewFileName, myText);
          final PsiElement psiElement = CodeStyleManager.getInstance(project).reformat(psiFile);

          text = psiElement.getText();
        }

        final FileEditorManager editorManager = FileEditorManager.getInstance(myDirectory.getProject());
        final FileEditor[] fileEditors = editorManager.openFile(newfile.getVirtualFile(), true);

        if (text != null) {
          for(FileEditor feditor:fileEditors) {
            if (feditor instanceof TextEditor) { // JSP is not safe to edit via Psi
              final Document document = ((TextEditor)feditor).getEditor().getDocument();
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
    catch (IncorrectOperationException e) {
      myIsAvailable = false;
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
