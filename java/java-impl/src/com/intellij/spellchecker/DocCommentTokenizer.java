// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.spellchecker.inspections.CommentSplitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author shkate@jetbrains.com
 */
public class DocCommentTokenizer extends Tokenizer<PsiDocComment> {
  private static final Set<String> excludedTags = Set.of("author", "see", "by", "link");

  @Override
  public void tokenize(@NotNull PsiDocComment comment, @NotNull TokenConsumer consumer) {
    final CommentSplitter splitter = CommentSplitter.getInstance();

    for (PsiElement el = comment.getFirstChild(); el != null; el = el.getNextSibling()) {
      if (el instanceof PsiDocTag tag) {
        if (!excludedTags.contains(tag.getName())) {
          for (PsiElement data : tag.getDataElements()) {
            consumer.consumeToken(data, splitter);
          }
        }
      }
      else {
        consumer.consumeToken(el, splitter);
      }
    }
  }
}
