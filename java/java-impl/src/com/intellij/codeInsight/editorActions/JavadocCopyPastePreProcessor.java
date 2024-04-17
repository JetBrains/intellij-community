// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavadocCopyPastePreProcessor implements CopyPastePreProcessor {
  @Override
  public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @Override
  public @NotNull String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (!(file instanceof PsiJavaFile)) {
      return text;
    }

    JavaCodeStyleSettings settings = CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class);
    if (!settings.JD_LEADING_ASTERISKS_ARE_ENABLED) return text;
    
    int offset = editor.getSelectionModel().getSelectionStart();
    if (DocumentUtil.isAtLineEnd(offset, editor.getDocument()) && text.startsWith("\n")) return text;

    PsiElement element = file.findElementAt(offset);
    PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class, false);
    if (docComment == null) return text;

    Document document = editor.getDocument();
    int lineStartOffset = DocumentUtil.getLineStartOffset(offset, document);
    CharSequence chars = document.getImmutableCharSequence();
    int firstNonWsLineOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t");
    if (firstNonWsLineOffset >= offset || chars.charAt(firstNonWsLineOffset) != '*') return text;

    String lineStartReplacement = "\n" + chars.subSequence(lineStartOffset, firstNonWsLineOffset + 1) + " ";
    return StringUtil.trimTrailing(text, '\n').replace("\n", lineStartReplacement);
  }
}
