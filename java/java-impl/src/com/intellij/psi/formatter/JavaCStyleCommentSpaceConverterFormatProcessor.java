// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JavaCStyleCommentSpaceConverterFormatProcessor implements PostFormatProcessor {
  @Override
  public @NotNull PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();

    if (indentOptions == null || indentOptions.USE_TAB_CHARACTER) {
      return source;
    }

    CStyleCommentSpaceConvertingVisitor visitor = new CStyleCommentSpaceConvertingVisitor(indentOptions, source.getTextRange(), indentOptions.TAB_SIZE);
    source.accept(visitor);
    for (ReplacementPair pair : visitor.myReplacementPairs) {
      pair.toReplace.replace(pair.replacement);
    }

    for (ReplacementPair myReplacementPair : visitor.myReplacementPairs) {
      if (myReplacementPair.toReplace == source) {
        PsiElement replacement = myReplacementPair.replacement;
        if (replacement != null) return replacement;
        return source;
      }
    }
    return source;
  }

  /**
   * @return true if replaced something
   */
  private static boolean traverseAndReplaceTabsWithSpaces(CommonCodeStyleSettings.IndentOptions indentOptions, TextRange range, @NotNull PsiElement source) {
    CStyleCommentSpaceConvertingVisitor visitor = new CStyleCommentSpaceConvertingVisitor(indentOptions, range, indentOptions.TAB_SIZE);
    source.accept(visitor);
    for (ReplacementPair pair : visitor.myReplacementPairs) {
      pair.toReplace.replace(pair.replacement);
    }
    return !visitor.myReplacementPairs.isEmpty();
  }

  @Override
  public @NotNull TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();

    if (indentOptions == null || indentOptions.USE_TAB_CHARACTER) {
      return rangeToReformat;
    }
    if (traverseAndReplaceTabsWithSpaces(indentOptions, rangeToReformat, source)) {
      return source.getTextRange();
    }
    return rangeToReformat;
  }

  private static @Nullable String getSpaceReplacement(@NotNull PsiElement element, int tabSize) {
    if (!element.textContains('\t')) {
      return null;
    }
    String text = element.getText();
    return text.replace("\t", " ".repeat(tabSize));
  }

  private static @Nullable String getCommentReplacement(@NotNull PsiElement element, int tabSize) {
    if (!element.textContains('\t')) {
      return null;
    }
    String text = element.getText();
    int length = text.length();
    boolean isInIndent = true;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      char ch = text.charAt(i);
      if (ch == '\t') {
        if (isInIndent) {
          sb.append(" ".repeat(tabSize));
          continue;
        }
      }
      else if (ch == '\n') {
        isInIndent = true;
      }
      else if (isInIndent && !Character.isWhitespace(ch)) {
        isInIndent = false;
      }
      sb.append(ch);
    }
    return sb.toString();
  }

  private record ReplacementPair(PsiElement toReplace, PsiElement replacement) {
  }

  private static class CStyleCommentSpaceConvertingVisitor extends JavaRecursiveElementVisitor {
    final @NotNull CommonCodeStyleSettings.IndentOptions myIndentOptions;
    final @NotNull TextRange rangeToReformat;
    private int myTabSize;
    final @NotNull List<ReplacementPair> myReplacementPairs = new ArrayList<>();

    private CStyleCommentSpaceConvertingVisitor(CommonCodeStyleSettings.@NotNull IndentOptions options,
                                                @NotNull TextRange rangeToReformat, int tabSize) {
      myIndentOptions = options;
      this.rangeToReformat = rangeToReformat;
      myTabSize = tabSize;
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {
      super.visitComment(comment);
      if (!rangeToReformat.contains(comment.getTextRange())) {
        return;
      }

      String replacement = getCommentReplacement(comment, myTabSize);
      if (replacement == null) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(comment.getProject());
      PsiComment newComment = factory.createCommentFromText(replacement, comment);
      myReplacementPairs.add(new ReplacementPair(comment, newComment));
    }


    @Override
    public void visitWhiteSpace(@NotNull PsiWhiteSpace space) {
      super.visitWhiteSpace(space);

      String replacement = getSpaceReplacement(space, myTabSize);
      if (replacement == null) return;
      PsiFileFactory fileFactory = PsiFileFactory.getInstance(space.getProject());
      PsiFile file = fileFactory.createFileFromText("__Dummy.java", JavaLanguage.INSTANCE, replacement);

      PsiElement newSpace = file.findElementAt(0);
      assert newSpace != null;
      myReplacementPairs.add(new ReplacementPair(space, newSpace));
    }
  }
}
