// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Pavel.Dolgov
 */
abstract class FragmentNode extends DefaultMutableTreeNode implements Comparable<FragmentNode> {
  private final @NotNull TextChunk @NotNull [] myTextChunks;
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

  @NotNull TextChunk @NotNull [] getTextChunks() {
    return myTextChunks;
  }

  TextChunk getLineNumberChunk() {
    return myLineNumberChunk;
  }

  protected @NotNull TextChunk @NotNull [] createTextChunks(@NotNull PsiElement element) {
    UsageInfo2UsageAdapter usageAdapter = new UsageInfo2UsageAdapter(new UsageInfo(element));
    PsiFile file = element.getContainingFile();
    TextRange range = element.getTextRange();
    List<TextChunk> result = new ArrayList<>();
    ChunkExtractor.getExtractor(file).appendTextChunks(usageAdapter, file.getText(), range.getStartOffset(), range.getEndOffset(), false, result);
    return result.toArray(TextChunk.EMPTY_ARRAY);
  }

  private static TextChunk createNumberChunk(@NotNull PsiElement start, @NotNull PsiElement end) {
    Document document = PsiDocumentManager.getInstance(start.getProject()).getDocument(start.getContainingFile());
    if (document != null) {
      int startLine = getLineNumber(document, start.getTextRange().getStartOffset()) + 1;
      int endLine = getLineNumber(document, end.getTextRange().getEndOffset()) + 1;
      String lineText = startLine == endLine ? Integer.toString(startLine) : startLine + ".." + endLine;
      return new TextChunk(UsageTreeColors.NUMBER_OF_USAGES_ATTRIBUTES.toTextAttributes(), lineText + "  ");
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

  @Nullable ElementsRange getElementsRange() {
    return myFragment.getElementsRange();
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

  @Override
  public String toString() {
    String lineNumber = myLineNumberChunk != null ? myLineNumberChunk.getText().trim() + ":" : "";
    return Stream.of(myTextChunks).map(TextChunk::getText).collect(Collectors.joining("", lineNumber, ""));
  }
}
