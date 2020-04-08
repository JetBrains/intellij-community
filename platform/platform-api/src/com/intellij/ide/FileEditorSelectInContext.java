// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
