/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public class FileEditorSelectInContext implements SelectInContext {
  private final FileEditor myFileEditor;
  private final PsiFile myPsiFile;

  public FileEditorSelectInContext(@NotNull FileEditor fileEditor, @NotNull PsiFile psiFile) {
    myFileEditor = fileEditor;
    myPsiFile = psiFile;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myPsiFile.getProject();
  }

  @NotNull
  @Override
  public VirtualFile getVirtualFile() {
    return myPsiFile.getViewProvider().getVirtualFile();
  }

  @NotNull
  public FileEditor getFileEditor() {
    return myFileEditor;
  }

  @NotNull
  public PsiFile getPsiFile() {
    return myPsiFile;
  }

  @Nullable
  @Override
  public Object getSelectorInFile() {
    return ObjectUtils.notNull(getElementAtCaret(false), myPsiFile);
  }

  @Nullable
  public PsiElement getElementAtCaret(boolean tryInjected) {
    if (!(myFileEditor instanceof TextEditor)) return null;
    Editor editor = ((TextEditor)myFileEditor).getEditor();
    int offset = editor.getCaretModel().getOffset();
    if (tryInjected) {
      InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
      PsiElement injectedElementAt = manager.findInjectedElementAt(myPsiFile, offset);
      if (injectedElementAt != null) return injectedElementAt;
    }
    return myPsiFile.findElementAt(offset);
  }

  @Nullable
  @Override
  public FileEditorProvider getFileEditorProvider() {
    return new FileEditorProvider() {
      @Override
      public FileEditor openFileEditor() {
        return myFileEditor;
      }
    };
  }
}
