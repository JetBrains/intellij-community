// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;

/**
 * @author Pavel.Dolgov
 */
abstract class FragmentNode extends DefaultMutableTreeNode implements Comparable<FragmentNode> {
  private final TextChunk[] myTextChunks;
  private final int myOffset;

  protected FragmentNode(PsiElement start, PsiElement end) {
    myTextChunks = createTextChunks(start, end);
    myOffset = start.getTextRange().getStartOffset();
    setAllowsChildren(false);
  }

  public TextChunk[] getTextChunks() {
    return myTextChunks;
  }

  protected TextChunk[] createTextChunks(@NotNull PsiElement start, @NotNull PsiElement end) {
    UsageInfo2UsageAdapter usageStartAdapter = new UsageInfo2UsageAdapter(new UsageInfo(start));
    String text = start.getText();
    ArrayList<TextChunk> chunks = new ArrayList<>();

    Document document = PsiDocumentManager.getInstance(start.getProject()).getDocument(start.getContainingFile());
    if (document != null) {
      int startLine = getLineNumber(document, start.getTextRange().getStartOffset()) + 1;
      int endLine = getLineNumber(document, end.getTextRange().getEndOffset()) + 1;
      String lineText = startLine == endLine ? Integer.toString(startLine) : startLine + ".." + endLine;
      EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
      chunks.add(new TextChunk(colorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION), lineText));
    }

    return ChunkExtractor.getExtractor(start.getContainingFile())
                         .createTextChunks(usageStartAdapter, text, 0, text.length(), true, chunks);
  }

  protected Navigatable getNavigatable() {return null;}

  public boolean isExcluded() {return false;}

  public boolean isValid() {return true;}

  private static int getLineNumber(@NotNull Document document, int offset) {
    if (document.getTextLength() == 0) return 0;
    if (offset >= document.getTextLength()) return document.getLineCount();
    return document.getLineNumber(offset);
  }

  @Override
  public int compareTo(@NotNull FragmentNode o) {
    return myOffset - o.myOffset;
  }
}
