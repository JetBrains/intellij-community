/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle.lineIndent;

import com.intellij.formatting.Indent;
import com.intellij.formatting.IndentInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.SemanticEditorPosition;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.formatting.Indent.Type.CONTINUATION;
import static com.intellij.formatting.Indent.Type.NORMAL;

class IndentCalculator {
  
  private @NotNull final Project myProject;
  private @NotNull final Editor myEditor;
  private @NotNull BaseLineOffsetCalculator myBaseLineOffsetCalculator;
  private @NotNull final Indent.Type myIndentType;

  public IndentCalculator(@NotNull Project project,
                          @NotNull Editor editor,
                          @NotNull BaseLineOffsetCalculator baseLineOffsetCalculator,
                          @NotNull Indent.Type type) {
    myProject = project;
    myEditor = editor;
    myBaseLineOffsetCalculator = baseLineOffsetCalculator;
    myIndentType = type;
  }

  public final static BaseLineOffsetCalculator LINE_BEFORE = new BaseLineOffsetCalculator() {
    @Override
    public int getOffsetInBaseIndentLine(@NotNull SemanticEditorPosition currPosition) {
      return CharArrayUtil.shiftBackward(currPosition.getChars(), currPosition.getStartOffset(), " \t\n\r");
    }
  };
  
  
  @Nullable
  String getIndentString(@NotNull SemanticEditorPosition currPosition) {
    Document document = myEditor.getDocument();
    String baseIndent = getBaseIndent(currPosition);
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file != null) {
      CommonCodeStyleSettings.IndentOptions options = CodeStyleSettingsManager.getSettings(myProject).getIndentOptionsByFile(file);
      return
        baseIndent + new IndentInfo(0, indentTypeToSize(myIndentType, options), 0, false).generateNewWhiteSpace(options);
    }
    return null;
  }


  @NotNull
  private String getBaseIndent(@NotNull SemanticEditorPosition currPosition) {
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

  private static int indentTypeToSize(@NotNull Indent.Type indentType, @NotNull CommonCodeStyleSettings.IndentOptions options) {
    if (indentType == NORMAL) {
      return options.INDENT_SIZE;
    }
    else if (indentType == CONTINUATION) {
      return options.CONTINUATION_INDENT_SIZE;
    }
    return 0;
  }

  
  public interface BaseLineOffsetCalculator  {
    int getOffsetInBaseIndentLine(@NotNull SemanticEditorPosition position);
  }
}
