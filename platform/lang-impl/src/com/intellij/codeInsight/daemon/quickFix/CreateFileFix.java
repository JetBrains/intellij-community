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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
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
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class CreateFileFix implements IntentionAction, LocalQuickFix {
  private final boolean myIsDirectory;
  private final String myNewFileName;
  private final PsiDirectory myDirectory;
  private final String myText;
  private @NotNull String myKey;
  private boolean myIsAvailable;
  private long myIsAvailableTimeStamp;
  private static final int REFRESH_INTERVAL = 1000;

  public CreateFileFix(final boolean isDirectory,
                                   final String newFileName,
                                   final PsiDirectory directory,
                                   @Nullable String text,
                                   @NotNull String key) {
    myIsDirectory = isDirectory;
    myNewFileName = newFileName;
    myDirectory = directory;
    myText = text;
    myKey = key;
    myIsAvailable = isDirectory || !FileTypeManager.getInstance().getFileTypeByFileName(newFileName).isBinary();
    myIsAvailableTimeStamp = System.currentTimeMillis();
  }

  public CreateFileFix(final String newFileName,
                                   final PsiDirectory directory, String text) {
    this(false,newFileName,directory, text, "create.file.text" );
  }

  public CreateFileFix(final boolean isDirectory,
                                   final String newFileName,
                                   final PsiDirectory directory) {
    this(isDirectory,newFileName,directory,null, isDirectory ? "create.directory.text":"create.file.text" );
  }

  @Nullable
  protected String getFileText() {
    return myText;
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message(myKey, myNewFileName);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("create.file.family");
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
      if (myIsDirectory) {
        myDirectory.createSubdirectory(myNewFileName);
      }
      else {
        final PsiFile newFile = myDirectory.createFile(myNewFileName);
        String text = getFileText();

        if (text != null) {
          final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("_" + myNewFileName, text);
          final PsiElement psiElement = CodeStyleManager.getInstance(project).reformat(psiFile);
          text = psiElement.getText();
        }

        final FileEditorManager editorManager = FileEditorManager.getInstance(myDirectory.getProject());
        final FileEditor[] fileEditors = editorManager.openFile(newFile.getVirtualFile(), true);

        if (text != null) {
          for(FileEditor fileEditor: fileEditors) {
            if (fileEditor instanceof TextEditor) { // JSP is not safe to edit via Psi
              final Document document = ((TextEditor)fileEditor).getEditor().getDocument();
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
