/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: angus
 * Date: 4/20/11
 * Time: 9:27 PM
 */
public class IntentionWrapper implements LocalQuickFix, IntentionAction, ActionClassHolder {
  private final IntentionAction myAction;
  private final PsiFile myFile;

  public IntentionWrapper(@NotNull IntentionAction action, @NotNull PsiFile file) {
    myAction = action;
    myFile = file;
  }

  @NotNull
  @Override
  public String getName() {
    return myAction.getText();
  }

  @NotNull
  @Override
  public String getText() {
    return myAction.getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myAction.isAvailable(project, editor, file);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myAction.invoke(project, editor, file);
  }

  @Override
  public boolean startInWriteAction() {
    return myAction.startInWriteAction();
  }

  @NotNull
  public IntentionAction getAction() {
    return myAction;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    VirtualFile virtualFile = myFile.getVirtualFile();

    if (virtualFile != null) {
      FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
      myAction.invoke(project, editor instanceof TextEditor ? ((TextEditor) editor).getEditor() : null, myFile);
    }
  }

  @NotNull
  @Override
  public Class getActionClass() {
    return getAction().getClass();
  }
}

