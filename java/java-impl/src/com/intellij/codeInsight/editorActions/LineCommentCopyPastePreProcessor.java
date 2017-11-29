// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LineCommentCopyPastePreProcessor implements CopyPastePreProcessor {
  @Nullable
  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @NotNull
  @Override
  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (!(file instanceof PsiJavaFile)) return text;

    Document document = editor.getDocument();
    int offset = editor.getSelectionModel().getSelectionStart();
    int lineStartOffset = DocumentUtil.getLineStartOffset(offset, document);
    CharSequence chars = document.getImmutableCharSequence();
    int firstNonWsLineOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t");
    if (offset >= (firstNonWsLineOffset + 2) && 
        chars.charAt(firstNonWsLineOffset) == '/' && chars.charAt(firstNonWsLineOffset + 1) == '/') {
      CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
      String lineStartReplacement = "\n" + chars.subSequence(lineStartOffset, firstNonWsLineOffset + 2) +
                                    (codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE).LINE_COMMENT_ADD_SPACE ? " " : "");
      return StringUtil.trimTrailing(text, '\n').replace("\n", lineStartReplacement);
    }
    else {
      return text;
    }
  }
}
