// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocCommentBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CollapseExpandDocCommentsHandler implements CodeInsightActionHandler {

  private static final Key<Boolean> DOC_COMMENT_MARK = Key.create("explicit.fold.region.doc.comment.mark");

  public static void setDocCommentMark(@NotNull FoldRegion region, boolean value) {
    region.putUserData(DOC_COMMENT_MARK, value);
  }

  private final boolean myExpand;

  public CollapseExpandDocCommentsHandler(boolean isExpand) {
    myExpand = isExpand;
  }

  @Override
  public void invoke(@NotNull Project project, final @NotNull Editor editor, @NotNull PsiFile file){
    CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
    foldingManager.updateFoldRegions(editor);
    final FoldRegion[] allFoldRegions = editor.getFoldingModel().getAllFoldRegions();
    Runnable processor = () -> {
      for (FoldRegion region : allFoldRegions) {
        PsiElement element = EditorFoldingInfo.get(editor).getPsiElement(region);
        if (element instanceof PsiDocCommentBase
            || Boolean.TRUE.equals(region.getUserData(DOC_COMMENT_MARK))
            || hasAllowedTokenType(editor, region, element)) {
          region.setExpanded(myExpand);
        }
      }
    };
    editor.getFoldingModel().runBatchFoldingOperation(processor);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }

  /**
   * Check if specified psi element has allowed token type as documentation.
   * <p>
   * The check is needed for languages which differentiate comments and documentation
   * and cannot use PsiComment for docs (like Python).
   */
  private static boolean hasAllowedTokenType(@NotNull Editor editor, @NotNull FoldRegion region, @Nullable PsiElement element) {
    if (element == null) return false;
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(element.getLanguage());
    if (!(commenter instanceof CodeDocumentationAwareCommenter)) return false;
    final HighlighterIterator iterator = editor.getHighlighter().createIterator(region.getStartOffset());
    if (iterator.atEnd()) return false;
    return ((CodeDocumentationAwareCommenter)commenter).getDocumentationCommentTokenType() == iterator.getTokenType();
  }
}
