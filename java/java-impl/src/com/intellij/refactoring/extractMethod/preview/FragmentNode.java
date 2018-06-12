// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;

/**
 * @author Pavel.Dolgov
 */
abstract class FragmentNode extends DefaultMutableTreeNode implements Comparable<FragmentNode> {
  private final TextChunk[] myTextChunks;
  private final TextChunk myLineNumberChunk;
  private final int myOffset;
  private final ExtractableFragment myFragment;
  private boolean myValid = true;

  protected FragmentNode(@NotNull PsiElement start, @NotNull PsiElement end, @NotNull ExtractableFragment fragment) {
    myTextChunks = createTextChunks(start);
    myLineNumberChunk = createNumberChunk(start, end);
    myOffset = start.getTextRange().getStartOffset();
    myFragment = fragment;
    setAllowsChildren(false);
  }

  public TextChunk[] getTextChunks() {
    return myTextChunks;
  }

  public TextChunk getLineNumberChunk() {
    return myLineNumberChunk;
  }

  @NotNull
  protected TextChunk[] createTextChunks(@NotNull PsiElement element) {
    UsageInfo2UsageAdapter usageAdapter = new UsageInfo2UsageAdapter(new UsageInfo(element));
    PsiFile file = element.getContainingFile();
    TextRange range = element.getTextRange();
    return ChunkExtractor.getExtractor(file)
                  .createTextChunks(usageAdapter, file.getText(), range.getStartOffset(), range.getEndOffset(), false, new ArrayList<>());
  }

  private static TextChunk createNumberChunk(@NotNull PsiElement start, @NotNull PsiElement end) {
    Document document = PsiDocumentManager.getInstance(start.getProject()).getDocument(start.getContainingFile());
    if (document != null) {
      int startLine = getLineNumber(document, start.getTextRange().getStartOffset()) + 1;
      int endLine = getLineNumber(document, end.getTextRange().getEndOffset()) + 1;
      String lineText = startLine == endLine ? Integer.toString(startLine) : startLine + ".." + endLine;
      EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
      return new TextChunk(colorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION), lineText + "  ");
    }
    return null;
  }

  @Nullable
  public Navigatable getNavigatable() {
    return myFragment.getNavigatable();
  }

  public boolean isExcluded() {return false;}

  public synchronized boolean isValid() {
    return myValid;
  }

  public synchronized void setValid(boolean valid) {
    myValid = valid;
  }

  @Nullable
  public TextRange getTextRange() {
    return myFragment.getTextRange();
  }

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
