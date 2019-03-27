// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.richcopy;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.editor.richcopy.view.HtmlSyntaxInfoReader;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HtmlSyntaxInfoUtil {
  
  @NotNull
  public static HtmlSyntaxInfoReader createHighlighterHtmlReader(@NotNull PsiFile file,
                                                                 @NotNull CharSequence text,
                                                                 @Nullable SyntaxInfoBuilder.RangeIterator ownRangeIterator,
                                                                 @NotNull EditorColorsScheme schemeToUse,
                                                                 int startOffset,
                                                                 int endOffset) {
    EditorHighlighter highlighter =
      HighlighterFactory.createHighlighter(file.getViewProvider().getVirtualFile(), schemeToUse, file.getProject());
    highlighter.setText(text);

    SyntaxInfoBuilder.HighlighterRangeIterator highlighterRangeIterator =
      new SyntaxInfoBuilder.HighlighterRangeIterator(highlighter, startOffset, endOffset);
    ownRangeIterator = ownRangeIterator == null
                       ? highlighterRangeIterator
                       : new SyntaxInfoBuilder.CompositeRangeIterator(schemeToUse, highlighterRangeIterator, ownRangeIterator);

    return createHighlighterHtmlReader(text, ownRangeIterator, schemeToUse, endOffset);
  }

  private static HtmlSyntaxInfoReader createHighlighterHtmlReader(@NotNull CharSequence text,
                                                                  @NotNull SyntaxInfoBuilder.RangeIterator ownRangeIterator,
                                                                  @NotNull EditorColorsScheme schemeToUse,
                                                                  int endOffset) {
    SyntaxInfoBuilder.Context context = new SyntaxInfoBuilder.Context(text, schemeToUse, 0);
    SyntaxInfoBuilder.MyMarkupIterator iterator = new SyntaxInfoBuilder.MyMarkupIterator(text, ownRangeIterator, schemeToUse);

    try {
      context.iterate(iterator, endOffset);
    }
    finally {
      iterator.dispose();
    }
    SyntaxInfo info = context.finish();
    HtmlSyntaxInfoReader data = new SimpleHtmlSyntaxInfoReader(info);
    data.setRawText(text.toString());
    return data;
  }

  private static class SimpleHtmlSyntaxInfoReader extends HtmlSyntaxInfoReader {
    
    public SimpleHtmlSyntaxInfoReader(SyntaxInfo info) {
      super(info, 2);
    }

    @Override
    protected void appendCloseTags() {
     
    }

    @Override
    protected void appendStartTags() {
      
    }

    @Override
    protected void defineBackground(int id, @NotNull StringBuilder styleBuffer) {

    }

    @Override
    protected void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, int fontFamilyId) {

    }
  }
}
