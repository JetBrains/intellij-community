// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.xml.util.BasicHtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaDocElementType.*;
import static com.intellij.util.text.CharArrayUtil.containsOnlyWhiteSpaces;

/**
 * Advises typing in javadoc if necessary.
 */
public final class JavadocTypedHandler extends TypedHandlerDelegate {

  private static final char START_TAG_SYMBOL = '<';
  private static final char CLOSE_TAG_SYMBOL = '>';
  private static final char SLASH = '/';
  private static final String COMMENT_PREFIX = "!--";

  @Override
  public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (isJavaFile(file)) {
      if (!insertClosingTagIfNecessary(c, project, editor, file)) {
        adjustStartIndent(c, editor, file);
      }
    }
    return Result.CONTINUE;
  }

  private static boolean isJavaFile(@Nullable PsiFile file) {
    return file instanceof AbstractBasicJavaFile;
  }

  private static void adjustStartIndent(char c, @NotNull Editor editor, @NotNull PsiFile file) {
    if (c == '@') {
      final int offset = editor.getCaretModel().getOffset();
      PsiElement currElement = file.findElementAt(offset);
      if (currElement instanceof PsiWhiteSpace) {
        PsiElement prev = currElement.getPrevSibling();
        if (prev != null && prev.getNode().getElementType() == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
          editor.getDocument().replaceString(currElement.getTextRange().getStartOffset(), offset - 1, " ");
        }
      }
      return;
    }

    if (c == '/') {
      // Insert a single space when adding the initial leading slashes
      final CaretModel caretModel = editor.getCaretModel();
      final int caretOffset = caretModel.getOffset();
      PsiElement currElement = file.findElementAt(caretOffset - 1);
      if (currElement instanceof PsiWhiteSpace) {
        PsiElement prev = currElement.getPrevSibling();
        if (prev != null && prev.getNode().getElementType() == JavaTokenType.END_OF_LINE_COMMENT && prev.getText().equals("//")) {
          editor.getDocument().insertString(prev.getTextRange().getEndOffset() + 1, " ");
          caretModel.moveToOffset(caretOffset + 1);
        }
      }
    }
  }

  /**
   * Checks if it's necessary to insert closing tag on typed character.
   *
   * @param c       typed symbol
   * @param project current project
   * @param editor  current editor
   * @param file    current file
   * @return {@code true} if closing tag is inserted; {@code false} otherwise
   */
  private static boolean insertClosingTagIfNecessary(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (c != CLOSE_TAG_SYMBOL || !CodeInsightSettings.getInstance().JAVADOC_GENERATE_CLOSING_TAG) {
      return false;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!isAppropriatePlace(editor, file)) {
      return false;
    }

    // Inspect symbols to the left of the current caret position, insert closing tag only if valid tag is just typed
    // (e.g. don't insert anything on single '>' symbol typing).
    int offset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    String tagName = getTagName(document.getText(), offset);
    if (tagName == null || BasicHtmlUtil.isSingleHtmlTag(tagName, false) || tagName.startsWith(COMMENT_PREFIX)) {
      return false;
    }

    document.insertString(offset, String.valueOf(START_TAG_SYMBOL) + SLASH + tagName + CLOSE_TAG_SYMBOL);
    return true;
  }

  /**
   * Tries to derive start tag name assuming that given offset points to position just after {@code '>'} symbol.
   * <p/>
   * Is expected to return {@code null} when offset is not located just after start tag, e.g. the following situations:
   * <pre>
   * <ul>
   *   <li>standalone {@code '>'} symbol (surrounded by white spaces);</li>
   *   <li>after end tag {@code <mytag><mytag>[caret]};</li>
   *   <li>after empty element tag {@code <p/>[caret]};</li>
   * </ul>
   * </pre>
   *
   * @param text           target text
   * @param afterTagOffset offset that points after
   * @return tag name if the one is parsed; {@code null} otherwise
   */
  public static @Nullable String getTagName(@NotNull CharSequence text, int afterTagOffset) {
    if (afterTagOffset > text.length()) {
      return null;
    }
    int endOffset = afterTagOffset - 1;

    // Check empty element like <p/>
    if (endOffset > 0 && text.charAt(endOffset - 1) == SLASH) {
      return null;
    }

    for (int i = endOffset - 1; i >= 0; i--) {
      char c = text.charAt(i);
      switch (c) {
        case '\n' -> {
          return null;
        }
        case CLOSE_TAG_SYMBOL -> {
          return null;
        }
        case START_TAG_SYMBOL -> {
          if (text.charAt(i + 1) == SLASH) {
            // Handle situation like <tag></tag>[offset].
            return null;
          }
          return text.subSequence(i + 1, endOffset).toString();
        }

        // There is a possible case that opening tag has attributes, e.g. <a href='bla-bla-bla'>[offset]. We want to extract
        // only tag name then.
        case ' ', '\t' -> endOffset = i;
      }
    }
    return null;
  }

  private static boolean isAppropriatePlace(Editor editor, PsiFile file) {
    FileViewProvider provider = file.getViewProvider();
    int offset = editor.getCaretModel().getOffset();

    final PsiElement elementAtCaret;
    if (offset < editor.getDocument().getTextLength()) {
      elementAtCaret = provider.findElementAt(offset);
    }
    else {
      elementAtCaret = provider.findElementAt(editor.getDocument().getTextLength() - 1);
    }

    PsiElement element = elementAtCaret;
    while (element instanceof PsiWhiteSpace || element != null && containsOnlyWhiteSpaces(element.getText())) {
      element = element.getPrevSibling();
    }

    if (element == null) {
      return false;
    }
    ASTNode astNode = BasicJavaAstTreeUtil.toNode(element);
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_DOC_PARAMETER_REF)) {
      astNode = astNode.getTreeParent();
    }

    if (BasicJavaAstTreeUtil.is(astNode, BASIC_DOC_TAG, BASIC_DOC_SNIPPET_TAG, BASIC_DOC_INLINE_TAG) &&
        "param".equals(BasicJavaAstTreeUtil.getTagName(astNode)) &&
        isTypeParamBracketClosedAfterParamTag(astNode, offset)) {
      return false;
    }


    // The contents of inline tags is not HTML, so the paired tag completion isn't appropriate there.
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_DOC_INLINE_TAG, BASIC_DOC_SNIPPET_TAG) ||
        BasicJavaAstTreeUtil.getParentOfType(astNode, ParentAwareTokenSet.create(BASIC_DOC_INLINE_TAG, BASIC_DOC_SNIPPET_TAG)) != null) {
      return false;
    }

    ASTNode node = element.getNode();
    return node != null
           && (JavaDocTokenType.ALL_JAVADOC_TOKENS.contains(node.getElementType())
               || BASIC_ALL_JAVADOC_ELEMENTS.contains(node.getElementType()));
  }

  private static boolean isTypeParamBracketClosedAfterParamTag(ASTNode tag, int bracketOffset) {
    ASTNode paramToDocument = getDocumentingParameter(tag);
    if (paramToDocument == null) return false;

    TextRange paramRange = paramToDocument.getTextRange();
    return paramRange.getEndOffset() == bracketOffset;
  }

  private static @Nullable ASTNode getDocumentingParameter(@NotNull ASTNode tag) {
    for (ASTNode element = tag.getFirstChildNode(); element != null; element = element.getTreeNext()) {
      if (BasicJavaAstTreeUtil.is(element, BASIC_DOC_PARAMETER_REF)) {
        return element;
      }
    }
    return null;
  }
}
