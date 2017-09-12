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
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
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
      return text.replace("\n", lineStartReplacement);
    }
    else {
      return text;
    }
  }
}
