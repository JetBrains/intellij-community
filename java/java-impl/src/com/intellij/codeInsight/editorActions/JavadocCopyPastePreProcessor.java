// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavadocCopyPastePreProcessor implements CopyPastePreProcessor {

  private static final String MARKDOWN_LINE_PREFIX = "///";
  private static final String LINE_COMMENT_PREFIX = "//";

  @Override
  public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @Override
  public @NotNull String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (!(file instanceof PsiJavaFile)) {
      return text;
    }

    int offset = editor.getSelectionModel().getSelectionStart();
    if (DocumentUtil.isAtLineEnd(offset, editor.getDocument()) && text.startsWith("\n")) return text;

    PsiElement element = file.findElementAt(offset - 1);
    PsiDocComment docComment = PsiTreeUtil.getParentOfType(element, PsiDocComment.class, false);
    if (docComment == null) return text;

    JavaCodeStyleSettings settings = CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class);
    if (!docComment.isMarkdownComment() && !settings.JD_LEADING_ASTERISKS_ARE_ENABLED) return text;

    if (docComment.isMarkdownComment()) {
      return preProcessMarkdownPaste(file, editor, offset, text);
    }

    return preProcessHtmlPaste(editor, offset, text);
  }

  private static String preProcessHtmlPaste(Editor editor, int offset, String text) {
    Document document = editor.getDocument();
    int lineStartOffset = DocumentUtil.getLineStartOffset(offset, document);
    CharSequence chars = document.getImmutableCharSequence();
    int firstNonWsLineOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t");
    if (firstNonWsLineOffset >= offset || chars.charAt(firstNonWsLineOffset) != '*') return text;

    String lineStartReplacement = "\n" + chars.subSequence(lineStartOffset, firstNonWsLineOffset + 1) + " ";
    return StringUtil.trimTrailing(text, '\n').replace("\n", lineStartReplacement);
  }

  /** @implNote Mostly a copy-paste from {@link LineCommentCopyPastePreProcessor#preprocessOnPaste(Project, PsiFile, Editor, String, RawText)} */
  private static String preProcessMarkdownPaste(PsiFile file, Editor editor, int offset, String text) {
    Language language = file.getLanguage();
    Document document = editor.getDocument();

    int lineStartOffset = DocumentUtil.getLineStartOffset(offset, document);
    CharSequence chars = document.getImmutableCharSequence();
    int firstNonWsLineOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t");
    if (offset < (firstNonWsLineOffset + MARKDOWN_LINE_PREFIX.length()) ||
        !CharArrayUtil.regionMatches(chars, firstNonWsLineOffset, MARKDOWN_LINE_PREFIX)) {
      return text;
    }

    /*
    This piece of code runs AFTER the LineCommentCopyPastePreProcessor.
    Since there is not system in place to stop a preprocessor chain, we have to use whatever has been produced beforehand.
    Meaning every line matches /^\s*?\/\/\s+?/
    There will probably be edge cases with specially crafted input.
    */

    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(file);
    String lineStartToReplace = "\n" +
                                chars.subSequence(lineStartOffset, firstNonWsLineOffset) +
                                LINE_COMMENT_PREFIX +
                                (codeStyleSettings.getCommonSettings(language).LINE_COMMENT_ADD_SPACE ? " " : "");
    String lineStartReplacement = "\n" + chars.subSequence(lineStartOffset, firstNonWsLineOffset) + MARKDOWN_LINE_PREFIX + " ";

    return text.replace(lineStartToReplace, lineStartReplacement);
  }
}
