/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point allows codeinsight subsystem to highlight and navigate braces in your language.
 * Brace matching is based on lexer (provided by {@link HighlighterIterator}) which supplies pairs of {@link IElementType} for corresponding braces.
 *
 * For example, {@link com.intellij.xml.impl.XmlBraceMatcher} defines several brace types,
 * e.g. {@link com.intellij.psi.xml.XmlTokenType#XML_CDATA_START} and {@link com.intellij.psi.xml.XmlTokenType#XML_CDATA_END} to match.
 */
public interface BraceMatcher {
  ExtensionPointName<FileTypeExtensionPoint<BraceMatcher>> EP_NAME = new ExtensionPointName<>("com.intellij.braceMatcher");

  int getBraceTokenGroupId(@NotNull IElementType tokenType);
  boolean isLBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType);
  boolean isRBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType);
  boolean isPairBraces(@NotNull IElementType tokenType, @NotNull IElementType tokenType2);
  boolean isStructuralBrace(@NotNull HighlighterIterator iterator, @NotNull CharSequence text, @NotNull FileType fileType);
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
  int getCodeConstructStart(@NotNull PsiFile file, int openingBraceOffset);
}
