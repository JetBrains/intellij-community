// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.richcopy;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.editor.richcopy.view.HtmlSyntaxInfoReader;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class HtmlSyntaxInfoUtil {
  @NotNull
  public static HtmlSyntaxInfoReader createHtmlReader(@NotNull PsiFile file,
                                                      @NotNull Document document,
                                                      @NotNull EditorColorsScheme schemeToUse,
                                                      int startOffset,
                                                      int endOffset) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, file.getProject(), true);
    CharSequence text = document.getText();
    SyntaxInfoBuilder.Context context = new SyntaxInfoBuilder.Context(text, schemeToUse, 0);
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(file.getViewProvider().getVirtualFile(),
                                                                         schemeToUse, file.getProject());
    highlighter.setText(text);
    SyntaxInfoBuilder.MyMarkupIterator
      markupIterator = SyntaxInfoBuilder.createMarkupIterator(highlighter, text, schemeToUse, markupModel, startOffset, endOffset);
    try {
      context.iterate(markupIterator, endOffset);
    }
    finally {
      markupIterator.dispose();
    }
    SyntaxInfo info = context.finish();
    HtmlSyntaxInfoReader data = new HtmlSyntaxInfoReader(info, 2) {
      @Override
      protected void appendCloseTags() {
        myResultBuffer.append("</pre>");
      }

      @Override
      protected void appendStartTags() {
        myResultBuffer.append("<pre>");
      }

      @Override
      protected void defineBackground(int id, @NotNull StringBuilder styleBuffer) {

      }

      @Override
      protected void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, int fontFamilyId) {
        
      }
    };
    data.setRawText(text.toString());
    return data;
  }
}
