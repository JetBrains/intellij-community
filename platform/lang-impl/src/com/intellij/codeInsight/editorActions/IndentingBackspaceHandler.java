/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Makes Backspace action delete all whitespace till next valid indent position
 */
public class IndentingBackspaceHandler extends BackspaceHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(IndentingBackspaceHandler.class);

  @Override
  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, Editor editor) {
    if (!CodeInsightSettings.getInstance().INDENTING_BACKSPACE || " \n\t".indexOf(c) == -1) {
      return false;
    }
    LanguageCodeStyleSettingsProvider codeStyleSettingsProvider = LanguageCodeStyleSettingsProvider.forLanguage(file.getLanguage());
    if (codeStyleSettingsProvider != null && codeStyleSettingsProvider.isIndentBasedLanguageSemantics()) {
      return false;
    }

    Document document = editor.getDocument();

    int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), editor.getCaretModel().getOffset(), " \t");
    int beforeWhitespaceOffset = CharArrayUtil.shiftBackward(document.getCharsSequence(), offset - 1, " \t") + 1;
    LogicalPosition logicalPosition = editor.offsetToLogicalPosition(offset);
    int lineStartOffset = document.getLineStartOffset(logicalPosition.line);
    if (lineStartOffset < beforeWhitespaceOffset) {
      if (c == '\n' && beforeWhitespaceOffset < offset) {
        document.deleteString(beforeWhitespaceOffset, offset);
        return true;
      }
      else {
        return false;
      }
    }

    CodeStyleFacade codeStyleFacade = CodeStyleFacade.getInstance(editor.getProject());
    String indent = codeStyleFacade.getLineIndent(document, lineStartOffset);
    if (indent == null) {
      return false;
    }

    int tabSize = getTabSize(codeStyleFacade, document);
    int targetColumn = getWidth(indent, tabSize);

    if (logicalPosition.column == targetColumn) {
      return false;
    }

    if (c == '\n' || logicalPosition.column > targetColumn) {
      smartReplace(document, lineStartOffset, offset, indent);
      return true;
    }

    if (logicalPosition.line == 0) {
      return false;
    }

    int prevLineStartOffset = document.getLineStartOffset(logicalPosition.line - 1);
    int prevLineEndOffset = document.getLineEndOffset(logicalPosition.line - 1);
    int targetOffset = CharArrayUtil.shiftBackward(document.getCharsSequence(), prevLineEndOffset - 1, " \t") + 1;

    if (prevLineStartOffset < targetOffset) {
      document.deleteString(targetOffset, offset);
    }
    else {
      smartReplace(document, prevLineStartOffset, offset, indent);
    }
    return true;
  }

  private static void smartReplace(Document document, int from, int to, String indent) {
    CharSequence text = document.getCharsSequence();
    int prefixLength = 0;
    while ((prefixLength + from) < to && prefixLength < indent.length() && text.charAt(prefixLength + from) == indent.charAt(prefixLength)) {
      prefixLength++;
    }
    document.replaceString(from + prefixLength, to, indent.substring(prefixLength));
  }

  private static int getTabSize(@NotNull CodeStyleFacade codeStyleFacade, @NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    FileType fileType = file == null ? null : file.getFileType();
    return codeStyleFacade.getTabSize(fileType);
  }

  private static int getWidth(@NotNull String indent, int tabSize) {
    int width = 0;
    for (int i = 0; i < indent.length(); i++) {
      char c = indent.charAt(i);
      switch (c) {
        case '\t':
          width = tabSize * (width / tabSize + 1);
          break;
        default:
          LOG.error("Unexpected whitespace character: " + ((int)c));
        case ' ':
          width++;
      }
    }
    return width;
  }
}
