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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 * @author Sergey Malenkov
 */
public class FileEditorSelectInContext extends SmartSelectInContext {
  private final TextEditor editor;

  public FileEditorSelectInContext(@NotNull FileEditor fileEditor, @NotNull PsiFile psiFile) {
    super(psiFile, psiFile, () -> fileEditor);
    editor = fileEditor instanceof TextEditor ? (TextEditor)fileEditor : null;
  }

  @Nullable
  @Override
  public Object getSelectorInFile() {
    PsiFile file = getPsiFile();
    return file == null ? null : ObjectUtils.notNull(getElementAtCaret(file, false), file);
  }

  @Nullable
  public PsiElement getElementAtCaret(boolean tryInjected) {
    PsiFile file = getPsiFile();
    return file == null ? null : getElementAtCaret(file, tryInjected);
  }

  @Nullable
  private PsiElement getElementAtCaret(@NotNull PsiFile file, boolean tryInjected) {
    Editor editor = getEditor();
    if (editor == null) return null;
    int offset = editor.getCaretModel().getOffset();
    if (tryInjected) {
      InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
      PsiElement injectedElementAt = manager.findInjectedElementAt(file, offset);
      if (injectedElementAt != null) return injectedElementAt;
    }
    PsiElement elementAt = file.findElementAt(offset);
    if (offset > 0 && (elementAt == null || elementAt instanceof PsiWhiteSpace)) {
      elementAt = file.findElementAt(offset - 1);
    }
    return elementAt;
  }

  @Nullable
  public Editor getEditor() {
    return editor == null ? null : editor.getEditor();
  }
}
