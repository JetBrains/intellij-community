// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JavaCStyleCommentSpaceConverterFormatProcessor implements PostFormatProcessor {
  @Override
  public @NotNull PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    if (source.getContainingFile().getLanguage() != JavaLanguage.INSTANCE) {
      return source;
    }
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();

    if (indentOptions == null || indentOptions.USE_TAB_CHARACTER) {
      return source;
    }

    CStyleCommentSpaceConvertingVisitor visitor = traverseAndReplaceTabs(indentOptions, source.getTextRange(), source);

    for (ReplacementPair myReplacementPair : visitor.myReplacementPairs) {
      if (myReplacementPair.toReplace == source) {
        PsiElement replacement = myReplacementPair.replacement;
        if (replacement != null) return replacement;
        return source;
      }
    }
    return source;
  }

  @NotNull
  private static CStyleCommentSpaceConvertingVisitor traverseAndReplaceTabs(CommonCodeStyleSettings.IndentOptions indentOptions,
                                                                            TextRange range,
                                                                            @NotNull PsiElement source) {
    CStyleCommentSpaceConvertingVisitor visitor = new CStyleCommentSpaceConvertingVisitor(indentOptions, range, indentOptions.TAB_SIZE);
    Document document = source.getContainingFile().getViewProvider().getDocument();
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(source.getProject());
    source.accept(visitor);
    if (visitor.myReplacementPairs.isEmpty()) {
      return visitor;
    }
    DocumentUtil.executeInBulk(document, () -> {
      for (ReplacementPair pair : visitor.myReplacementPairs) {
        pair.toReplace.replace(pair.replacement);
      }
      psiDocumentManager.commitDocument(document);
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
    });
    return visitor;
  }

  @Override
  public @NotNull TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    if (source.getContainingFile().getLanguage() != JavaLanguage.INSTANCE) {
      return rangeToReformat;
    }
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();

    if (indentOptions == null || indentOptions.USE_TAB_CHARACTER) {
      return rangeToReformat;
    }
    boolean replacedSomething = !traverseAndReplaceTabs(indentOptions, rangeToReformat, source).myReplacementPairs.isEmpty();
    if (replacedSomething) {
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
