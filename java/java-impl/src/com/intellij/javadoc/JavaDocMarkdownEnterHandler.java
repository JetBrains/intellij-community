// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javadoc;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiMarkdownCodeBlock;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class JavaDocMarkdownEnterHandler extends EnterHandlerDelegateAdapter {
  private static final String JAVADOC_MARKDOWN_PREFIX = "/// ";

  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    if (!(file instanceof PsiJavaFile) || !file.isValid()) return Result.Continue;

    return preProcessEnterImpl(file, editor, caretOffset, caretAdvance, dataContext, originalHandler);
  }

  @ApiStatus.Internal
  static Result preProcessEnterImpl(@NotNull PsiFile file,
                                    @NotNull Editor editor,
                                    @NotNull Ref<Integer> caretOffset,
                                    @NotNull Ref<Integer> caretAdvance,
                                    @NotNull DataContext dataContext,
                                    EditorActionHandler originalHandler) {
    PsiElement caretElement = file.findElementAt(caretOffset.get());
    if (caretElement == null) return Result.Continue;

    if (caretElement.getNode().getElementType() == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
      TextRange textRange = caretElement.getTextRange();

      if (textRange.getStartOffset() == caretOffset.get() &&
          Objects.requireNonNull(PsiTreeUtil.getParentOfType(caretElement, PsiDocComment.class)).getTextOffset() == textRange.getStartOffset()) {
        // Bail out if the caret is placed are before the start of the comment
        return Result.Continue;
      }

      // Avoid breaking a comment by placing the caret beyond the leading token, taking into account a potential space
      int newOffset = textRange.getEndOffset();
      caretOffset.set(editor.getDocument().getCharsSequence().charAt(newOffset) == ' ' ? newOffset + 1 : newOffset);
    }

    // EOL whitespace is not useful, we only need the tokens behind it
    if (caretElement instanceof PsiWhiteSpace) {
      // In multiline whitespaces, check cursor position to check whether the handler should trigger
      String whitespaces = caretElement.getText();
      int end = caretOffset.get() - caretElement.getTextOffset();
      if (StringUtil.countChars(whitespaces, '\n', 0, end, false) > 0) {
        return Result.Continue;
      }
      caretElement = caretElement.getPrevSibling();
    }

    if (!shouldInsertLeadingTokens(caretElement)) {
      return Result.Continue;
    }
    Document document = editor.getDocument();

    insertEndOfCodeBlock(file, document, caretOffset);

    document.insertString(caretOffset.get(), JAVADOC_MARKDOWN_PREFIX);
    caretAdvance.set(4);

    return Result.DefaultForceIndent;
  }

  /**
   * Verifies whether we should automatically add the leading slashes
   *
   * @param element a doc element found at the caret offset
   * @return If the javadoc is tied to a method/a class it should return true otherwise false
   */
  private static boolean shouldInsertLeadingTokens(PsiElement element) {
    PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class, false, PsiMember.class);
    if (docComment == null || !docComment.isMarkdownComment()) return false;

    return !JavaDocUtil.isDanglingDocComment(docComment, true);
  }

  /// Insert the end fence for a code block if none exists.
  private static void insertEndOfCodeBlock(@NotNull PsiFile file,
                                           @NotNull Document document,
                                           @NotNull Ref<Integer> caretOffset) {
    PsiElement caretElement = file.findElementAt(caretOffset.get() - 1);
    if (caretElement == null) return;

    PsiMarkdownCodeBlock codeBlock = PsiTreeUtil.getParentOfType(caretElement, PsiMarkdownCodeBlock.class);
    if (codeBlock == null || codeBlock.isInline()) return;

    // if there is only one fence, add another one into the element
    PsiElement firstChild = codeBlock.getFirstChild();
    if(firstChild == null) return;
    PsiElement lastChild = codeBlock.getLastChild();
    if (lastChild == null) return;
    if (firstChild == lastChild || lastChild.getNode().getElementType() != JavaDocTokenType.DOC_CODE_FENCE) {

      TextRange textRange = firstChild.getTextRange();
      if (textRange.containsOffset(caretOffset.get())) {
        // Move the offset to the end of the fence to avoid breaking it
        caretOffset.set(textRange.getEndOffset());
      }

      // compute the indent and insert the fence
      final int lineStartOffset = DocumentUtil.getLineStartOffset(caretOffset.get(), document);
      int firstNonWsLineOffset = CharArrayUtil.shiftForward(document.getCharsSequence(), lineStartOffset, " \t");
      document.insertString(caretOffset.get(),
                            String.format("\n%s%s%s", StringUtil.repeatSymbol(' ', firstNonWsLineOffset - lineStartOffset),
                                          JAVADOC_MARKDOWN_PREFIX, firstChild.getText()));
    }
  }
}
