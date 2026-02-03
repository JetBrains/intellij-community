// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.editorActions.BackspaceHandler.getLanguageAtCursorPosition;

abstract class AbstractIndentingBackspaceHandler extends BackspaceHandlerDelegate {
  private final SmartBackspaceMode myMode;
  private boolean myEnabled;

  AbstractIndentingBackspaceHandler(SmartBackspaceMode mode) {
    myMode = mode;
  }

  @Override
  public void beforeCharDeleted(char c, @NotNull PsiFile file, Editor editor) {
    myEnabled = false;
    if (editor.isColumnMode() || !StringUtil.isWhiteSpace(c)) {
      return;
    }
    Language languageAtCursorPosition = getLanguageAtCursorPosition(file, editor);
    SmartBackspaceMode mode = getBackspaceMode(file, languageAtCursorPosition);
    if (mode != myMode) {
      return;
    }
    doBeforeCharDeleted(c, file, editor);
    myEnabled = true;
  }

  @Override
  public boolean charDeleted(char c, @NotNull PsiFile file, @NotNull Editor editor) {
    if (!myEnabled) {
      return false;
    }
    return doCharDeleted(c, file, editor);
  }

  protected abstract void doBeforeCharDeleted(char c, PsiFile file, Editor editor);

  protected abstract boolean doCharDeleted(char c, PsiFile file, Editor editor);

  private static @NotNull SmartBackspaceMode getBackspaceMode(@NotNull PsiFile file, @NotNull Language language) {
    SmartBackspaceMode mode = CodeInsightSettings.getInstance().getBackspaceMode();
    BackspaceModeOverride override = LanguageBackspaceModeOverride.INSTANCE.forLanguage(language);
    if (override != null) {
      mode = override.getBackspaceMode(file, mode);
    }
    return mode;
  }
}
