// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.json.psi.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class JsonTypedHandler extends TypedHandlerDelegate {
  @NotNull
  @Override
  public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (file instanceof JsonFile) {
      processPairedBracesComma(c, editor, file);
    }
    return Result.CONTINUE;
  }

  public static void processPairedBracesComma(char c,
                                               @NotNull Editor editor,
                                               @NotNull PsiFile file) {
    if (!JsonEditorOptions.getInstance().COMMA_ON_MATCHING_BRACES) return;
    if (c != '[' && c != '{' && c != '"') return;
    SmartEnterProcessor.commitDocument(editor);
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return;
    PsiElement parent = element.getParent();
    if (c == '[' && parent instanceof JsonArray
        || c == '{' && parent instanceof JsonObject
        || c == '"' && parent instanceof JsonStringLiteral) {
      if (shouldAddCommaInParentContainer((JsonValue)parent)) {
        editor.getDocument().insertString(parent.getTextRange().getEndOffset(), ",");
      }
    }
  }

  private static boolean shouldAddCommaInParentContainer(@NotNull JsonValue item) {
    PsiElement parent = item.getParent();
    if (parent instanceof JsonArray || parent instanceof JsonProperty) {
      PsiElement nextElement = PsiTreeUtil.skipWhitespacesForward(parent instanceof JsonProperty ? parent : item);
      if (nextElement instanceof PsiErrorElement) {
        PsiElement forward = PsiTreeUtil.skipWhitespacesForward(nextElement);
        return parent instanceof JsonProperty ? forward instanceof JsonProperty : forward instanceof JsonValue;
      }
    }
    return false;
  }
}
