// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle.lineIndent;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Indent;
import com.intellij.formatting.IndentImpl;
import com.intellij.formatting.IndentInfo;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.SemanticEditorPosition;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.formatting.Indent.Type.*;

public class IndentCalculator {
  
  private final @NotNull Project myProject;
  private final @NotNull Editor myEditor;
  private final @NotNull BaseLineOffsetCalculator myBaseLineOffsetCalculator;
  private final @NotNull Indent myIndent;

  public IndentCalculator(@NotNull Project project,
                          @NotNull Editor editor,
                          @NotNull BaseLineOffsetCalculator baseLineOffsetCalculator,
                          @NotNull Indent indent) {
    myProject = project;
    myEditor = editor;
    myBaseLineOffsetCalculator = baseLineOffsetCalculator;
    myIndent = indent;
  }

  public static final BaseLineOffsetCalculator LINE_BEFORE = new BaseLineOffsetCalculator() {
    @Override
    public int getOffsetInBaseIndentLine(@NotNull SemanticEditorPosition currPosition) {
      return CharArrayUtil.shiftBackward(currPosition.getChars(), currPosition.getStartOffset(), " \t\n\r");
    }
  };

  public static final BaseLineOffsetCalculator LINE_AFTER = new BaseLineOffsetCalculator() {
    @Override
    public int getOffsetInBaseIndentLine(@NotNull SemanticEditorPosition currPosition) {
      return CharArrayUtil.shiftForward(currPosition.getChars(), currPosition.getStartOffset(), " \t\n\r");
    }
  };
  
  @Nullable
  String getIndentString(@Nullable Language language, @NotNull SemanticEditorPosition currPosition) {
    String baseIndent = getBaseIndent(currPosition);
    Document document = myEditor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file != null) {
      CommonCodeStyleSettings.IndentOptions fileOptions = CodeStyle.getIndentOptions(file);
      CommonCodeStyleSettings.IndentOptions options =
        !fileOptions.isOverrideLanguageOptions() && language != null && !(language.is(file.getLanguage()) || language.is(Language.ANY)) ?
        CodeStyle.getLanguageSettings(file, language).getIndentOptions() :
        fileOptions;
      if (options != null) {
        final int indentLength =
          baseIndent.replaceAll("\t", StringUtil.repeatSymbol(' ', options.TAB_SIZE)).length()
          + indentToSize(myIndent, options);
        return new IndentInfo(0, indentLength, 0, false).generateNewWhiteSpace(options);
      }
    }
    return null;
  }

  protected @NotNull String getBaseIndent(@NotNull SemanticEditorPosition currPosition) {
    CharSequence docChars = myEditor.getDocument().getCharsSequence();
    int offset = currPosition.getStartOffset();
    if (offset > 0) {
      int indentLineOffset = myBaseLineOffsetCalculator.getOffsetInBaseIndentLine(currPosition);
      if (indentLineOffset > 0) {
        int indentStart = CharArrayUtil.shiftBackwardUntil(docChars, indentLineOffset, "\n") + 1;
        if (indentStart >= 0) {
          int indentEnd = CharArrayUtil.shiftForward(docChars, indentStart, " \t");
          if (indentEnd > indentStart) {
            return docChars.subSequence(indentStart, indentEnd).toString();
          }
        }
      }
    }
    return "";
  }

  private static int indentToSize(@NotNull Indent indent, @NotNull CommonCodeStyleSettings.IndentOptions options) {
    if (indent.getType() == NORMAL) {
      return options.INDENT_SIZE;
    }
    else if (indent.getType() == CONTINUATION) {
      return options.CONTINUATION_INDENT_SIZE;
    }
    else if (indent.getType() == SPACES && indent instanceof IndentImpl) {
      return ((IndentImpl)indent).getSpaces();
    }
    return 0;
  }

  
  public interface BaseLineOffsetCalculator  {
    int getOffsetInBaseIndentLine(@NotNull SemanticEditorPosition position);
  }
}
