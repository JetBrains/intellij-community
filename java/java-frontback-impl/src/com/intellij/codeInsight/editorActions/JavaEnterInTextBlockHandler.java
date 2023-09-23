// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.enter.EnterInStringLiteralHandler;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_LITERAL_EXPRESSION;

public class JavaEnterInTextBlockHandler extends EnterInStringLiteralHandler {

  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffsetRef,
                                @NotNull Ref<Integer> caretAdvanceRef,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement textBlock = getTextBlockAt(file, offset);
    if (textBlock == null) return Result.Continue;
    int textBlockOffset = textBlock.getTextOffset();
    String text = textBlock.getText();
    int offsetInTextBlock = offset - textBlockOffset;
    boolean isAtFirstLine = !text.substring(0, offsetInTextBlock).contains("\n");
    if (!isAtFirstLine) return Result.Continue;
    Document document = editor.getDocument();
    Project project = textBlock.getProject();
    int secondLineStart = text.indexOf('\n');
    if (secondLineStart == -1) {
      document.insertString(offset, "\n");
      PsiDocumentManager.getInstance(project).commitDocument(document);
      CodeStyleManager.getInstance(project).reformat(textBlock);
      text = textBlock.getText();
      int indent = getIndent(text, offsetInTextBlock + 1);
      if (indent == -1) return Result.Continue;
      editor.getCaretModel().moveToOffset(offset + 1 + indent);
    }
    else {
      int indent = getIndent(text, secondLineStart + 1);
      if (indent == -1) return Result.Continue;
      String newLine = '\n' + StringUtil.repeatSymbol(' ', indent);
      document.insertString(offset, newLine);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      editor.getCaretModel().moveToOffset(offset + newLine.length());
    }
    return Result.Stop;
  }

  @Contract("null, _ -> null")
  private static PsiElement getTextBlockAt(PsiFile file, int offset) {
    if (!isJavaFile(file)) return null;
    PsiElement token = file.findElementAt(offset);
    if (token == null || token.getNode() == null || !BasicJavaAstTreeUtil.is(token.getNode(), JavaTokenType.TEXT_BLOCK_LITERAL)) return null;
    PsiElement parent = token.getParent();
    if (!BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(parent), BASIC_LITERAL_EXPRESSION)) {
      return null;
    }
    return parent;
  }

  private static boolean isJavaFile(@Nullable PsiFile file) {
    return file != null && file.getLanguage() == JavaLanguage.INSTANCE;
  }
  private static int getIndent(@NotNull String text, int start) {
    int indent = 0;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') {
        indent = 0;
        continue;
      }
      if (Character.isWhitespace(c)) {
        indent++;
        continue;
      }
      return indent;
    }
    return -1;
  }
}
