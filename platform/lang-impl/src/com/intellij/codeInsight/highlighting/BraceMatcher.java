package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BraceMatcher {
  ExtensionPointName<FileTypeExtensionPoint<BraceMatcher>> EP_NAME = new ExtensionPointName<FileTypeExtensionPoint<BraceMatcher>>("com.intellij.braceMatcher");

  int getBraceTokenGroupId(IElementType tokenType);
  boolean isLBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
  boolean isRBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
  boolean isPairBraces(IElementType tokenType,IElementType tokenType2);
  boolean isStructuralBrace(HighlighterIterator iterator,CharSequence text, FileType fileType);
  @Nullable IElementType getOppositeBraceTokenType(@NotNull IElementType type);
  boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType);

  /**
   * Returns the start offset of the code construct which owns the opening structural brace at the specified offset. For example,
   * if the opening brace belongs to an 'if' statement, returns the start offset of the 'if' statement.
   *
   * @param file the file in which brace matching is performed.
   * @param openingBraceOffset the offset of an opening structural brace.
   * @return the offset of corresponding code construct, or the same offset if not defined.
   */
  int getCodeConstructStart(final PsiFile file, int openingBraceOffset);
}
