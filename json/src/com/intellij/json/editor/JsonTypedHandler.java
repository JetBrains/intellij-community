// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class JsonTypedHandler extends TypedHandlerDelegate {

  private boolean myWhitespaceAdded;

  @NotNull
  @Override
  public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (file instanceof JsonFile) {
      processPairedBracesComma(c, editor, file);
      addWhiteSpaceAfterColonIfNeeded(c, editor, file);
      removeRedundantWhitespaceIfAfterColon(c, editor, file);
    }
    return Result.CONTINUE;
  }

  private void removeRedundantWhitespaceIfAfterColon(char c, Editor editor, PsiFile file) {
    if (!myWhitespaceAdded || c != ' ' || !JsonEditorOptions.getInstance().AUTO_WHITESPACE_AFTER_COLON) {
      if (c != ':') {
        myWhitespaceAdded = false;
      }
      return;
    }
    int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    final PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) {
      editor.getDocument().deleteString(offset - 1, offset);
    }
    myWhitespaceAdded = false;
  }

  @NotNull
  @Override
  public Result beforeCharTyped(char c,
                                @NotNull Project project,
                                @NotNull Editor editor,
                                @NotNull PsiFile file,
                                @NotNull FileType fileType) {
    if (file instanceof JsonFile) {
      addPropertyNameQuotesIfNeeded(c, editor, file);
    }
    return Result.CONTINUE;
  }

  private void addWhiteSpaceAfterColonIfNeeded(char c,
                                               @NotNull Editor editor,
                                               @NotNull PsiFile file) {
    if (c != ':' || !JsonEditorOptions.getInstance().AUTO_WHITESPACE_AFTER_COLON) {
      if (c != ' ') {
        myWhitespaceAdded = false;
      }
      return;
    }
    int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    PsiElement element = PsiTreeUtil.getParentOfType(PsiTreeUtil.skipWhitespacesBackward(file.findElementAt(offset)), JsonProperty.class, false);
    if (element == null) {
      myWhitespaceAdded = false;
      return;
    }
    final ASTNode[] children = element.getNode().getChildren(TokenSet.create(JsonElementTypes.COLON));
    if (children.length == 0) {
      myWhitespaceAdded = false;
      return;
    }
    final ASTNode colon = children[0];
    final ASTNode next = colon.getTreeNext();
    final String text = next.getText();
    if (text.length() == 0 || !StringUtil.isEmptyOrSpaces(text) || StringUtil.isLineBreak(text.charAt(0))) {
      final int insOffset = colon.getStartOffset() + 1;
      editor.getDocument().insertString(insOffset, " ");
      editor.getCaretModel().moveToOffset(insOffset + 1);
      myWhitespaceAdded = true;
    }
    else {
      myWhitespaceAdded = false;
    }
  }

  private static void addPropertyNameQuotesIfNeeded(char c,
                                                    @NotNull Editor editor,
                                                    @NotNull PsiFile file) {
    if (c != ':' || !JsonDialectUtil.isStandardJson(file) || !JsonEditorOptions.getInstance().AUTO_QUOTE_PROP_NAME) return;
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = PsiTreeUtil.skipWhitespacesBackward(file.findElementAt(offset));
    if (!(element instanceof JsonProperty)) return;
    final JsonValue nameElement = ((JsonProperty)element).getNameElement();
    if (nameElement instanceof JsonReferenceExpression) {
      ((JsonProperty)element).setName(nameElement.getText());
      PsiDocumentManager.getInstance(file.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    }
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
