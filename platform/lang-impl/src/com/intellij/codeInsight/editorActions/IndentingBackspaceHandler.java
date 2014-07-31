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
import com.intellij.formatting.*;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Makes Backspace action delete all whitespace till next valid indent position
 */
public class IndentingBackspaceHandler extends BackspaceHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(IndentingBackspaceHandler.class);

  private boolean caretWasAtLineStart;

  @Override
  public void beforeCharDeleted(char c, PsiFile file, Editor editor) {
    caretWasAtLineStart = editor.getCaretModel().getLogicalPosition().column == 0;
  }

  @Override
  public boolean charDeleted(char c, PsiFile file, Editor editor) {
    if (CodeInsightSettings.getInstance().SMART_BACKSPACE != CodeInsightSettings.AUTOINDENT || !StringUtil.isWhiteSpace(c)) {
      return false;
    }
    LanguageCodeStyleSettingsProvider codeStyleSettingsProvider = LanguageCodeStyleSettingsProvider.forLanguage(file.getLanguage());
    if (codeStyleSettingsProvider != null && codeStyleSettingsProvider.isIndentBasedLanguageSemantics()) {
      return false;
    }

    Project project = file.getProject();
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();

    int caretOffset = caretModel.getOffset();
    int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), caretOffset, " \t");
    int offsetInPsi = getOffsetInPsi(document, file, offset);
    int beforeWhitespaceOffset = CharArrayUtil.shiftBackward(document.getCharsSequence(), offset - 1, " \t") + 1;
    LogicalPosition logicalPosition = caretOffset < offset ? editor.offsetToLogicalPosition(offset) : caretModel.getLogicalPosition();
    int lineStartOffset = document.getLineStartOffset(logicalPosition.line);
    if (lineStartOffset < beforeWhitespaceOffset) {
      if (caretWasAtLineStart && beforeWhitespaceOffset <= offset) {
        String spacing = getSpacing(file, offsetInPsi);
        if (beforeWhitespaceOffset < offset || !spacing.isEmpty()) {
          document.replaceString(beforeWhitespaceOffset, offset, spacing);
          caretModel.moveToOffset(beforeWhitespaceOffset + spacing.length());
          return true;
        }
      }
      return false;
    }

    CodeStyleFacade codeStyleFacade = CodeStyleFacade.getInstance(project);
    // We should calculate indent at line containing the text after caret, cause that text might affect the result (e.g. closing brace in Java)
    String indent = codeStyleFacade.getLineIndent(document, offsetInPsi);
    if (indent == null) {
      return false;
    }

    int tabSize = getTabSize(codeStyleFacade, document);
    int targetColumn = getWidth(indent, tabSize);

    if (logicalPosition.column == targetColumn) {
      if (caretOffset < offset) {
        caretModel.moveToLogicalPosition(logicalPosition);
        return true;
      }
      return false;
    }

    if (caretWasAtLineStart || logicalPosition.column > targetColumn) {
      document.replaceString(lineStartOffset, offset, indent);
      caretModel.moveToLogicalPosition(new LogicalPosition(logicalPosition.line, targetColumn));
      return true;
    }

    if (logicalPosition.line == 0) {
      return false;
    }

    int prevLineStartOffset = document.getLineStartOffset(logicalPosition.line - 1);
    int prevLineEndOffset = document.getLineEndOffset(logicalPosition.line - 1);
    int targetOffset = CharArrayUtil.shiftBackward(document.getCharsSequence(), prevLineEndOffset - 1, " \t") + 1;

    if (prevLineStartOffset < targetOffset) {
      String spacing = getSpacing(file, offsetInPsi);
      document.replaceString(targetOffset, offset, spacing);
      caretModel.moveToOffset(targetOffset + spacing.length());
    }
    else {
      document.replaceString(prevLineStartOffset, offset, indent);
      caretModel.moveToLogicalPosition(new LogicalPosition(logicalPosition.line - 1, targetColumn));
    }
    return true;
  }

  private static int getOffsetInPsi(Document document, PsiFile file, int offset) {
    int psiLength = file.getTextLength();
    return Math.max(0, Math.min(psiLength, offset + psiLength - document.getTextLength()));
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

  private static String getSpacing(PsiFile file, int offset) {
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder == null) {
      return "";
    }
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(file.getProject());
    FormattingModel model = builder.createModel(file, settings);
    int spacing = FormatterEx.getInstance().getSpacingForBlockAtOffset(model, offset);
    return StringUtil.repeatSymbol(' ', spacing);
  }
}
