// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class EnterInJavadocParamDescriptionHandler implements EnterHandlerDelegate {
  @Override
  public Result postProcessEnter(final @NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
    if (!(file instanceof PsiJavaFile)
        || !CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER
        || !CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class).JD_ALIGN_PARAM_COMMENTS) {
      return Result.Continue;
    }
    final CaretModel caretModel = editor.getCaretModel();
    final int caretOffset = caretModel.getOffset();
    if (!isInJavaDoc(editor, caretOffset)) {
      return Result.Continue;
    }
    
    final Pair<JavadocHelper.JavadocParameterInfo,List<JavadocHelper.JavadocParameterInfo>> pair
      = JavadocHelper.parse(file, editor, caretOffset);
    if (pair.first == null || pair.first.parameterDescriptionStartPosition == null) {
      return Result.Continue;
    }

    final LogicalPosition caretPosition = caretModel.getLogicalPosition();
    final LogicalPosition nameEndPosition = pair.first.parameterNameEndPosition;
    if (nameEndPosition.line == caretPosition.line && caretPosition.column <= nameEndPosition.column) {
      return Result.Continue;
    }
    
    final int descriptionStartColumn = pair.first.parameterDescriptionStartPosition.column;
    final LogicalPosition desiredPosition = new LogicalPosition(caretPosition.line, descriptionStartColumn);
    final Document document = editor.getDocument();
    final CharSequence text = document.getCharsSequence();
    final int offsetAfterLastWs = CharArrayUtil.shiftForward(text, caretOffset, " \t");
    if (editor.offsetToLogicalPosition(offsetAfterLastWs).column < desiredPosition.column) {
      final int lineStartOffset = document.getLineStartOffset(desiredPosition.line);
      final String toInsert = StringUtil.repeat(" ", desiredPosition.column - (offsetAfterLastWs - lineStartOffset));
      ApplicationManager.getApplication().runWriteAction(() -> {
        document.insertString(caretOffset, toInsert);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
      });
    } 

    JavadocHelper.navigate(desiredPosition, editor, file.getProject());
    return Result.Stop;
  }
  
  private static boolean isInJavaDoc(@NotNull Editor editor, int offset) {
    Document document = editor.getDocument();
    CharSequence docChars = document.getCharsSequence();
    int i = CharArrayUtil.lastIndexOf(docChars, "/**", offset);
    if (i >= 0) {
      i = CharArrayUtil.indexOf(docChars, "*/", i);
      return i > offset;
    }
    return false;
  }
}
