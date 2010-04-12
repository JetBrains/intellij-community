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

package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

class LeafBlockWrapper extends AbstractBlockWrapper {
  private static final int CONTAIN_LINE_FEEDS = 4;
  private static final int READ_ONLY = 8;
  private static final int LEAF = 16;

  private final int mySymbolsAtTheLastLine;
  private LeafBlockWrapper myPreviousBlock;
  private LeafBlockWrapper myNextBlock;
  private SpacingImpl mySpaceProperty;
  private IndentInside myLastLineIndent;

  public LeafBlockWrapper(final Block block,
                          CompositeBlockWrapper parent,
                          WhiteSpace whiteSpaceBefore,
                          FormattingDocumentModel model,
                          LeafBlockWrapper previousTokenBlock,
                          boolean isReadOnly,
                          final TextRange textRange) {
    super(block, whiteSpaceBefore, parent, textRange);
    myPreviousBlock = previousTokenBlock;
    final int lastLineNumber = model.getLineNumber(textRange.getEndOffset());

    int flagsValue = myFlags;
    final boolean containsLineFeeds = model.getLineNumber(textRange.getStartOffset()) != lastLineNumber;
    flagsValue |= containsLineFeeds ? CONTAIN_LINE_FEEDS:0;
    mySymbolsAtTheLastLine = containsLineFeeds ? textRange.getEndOffset() - model.getLineStartOffset(lastLineNumber) : textRange.getLength();
    flagsValue |= isReadOnly ? READ_ONLY:0;
    final boolean isLeaf = block.isLeaf();
    flagsValue |= isLeaf ? LEAF : 0;

    if (isLeaf && containsLineFeeds) {
      myLastLineIndent = IndentInside.getLastLineIndent(model.getText(textRange).toString());
    } else {
      myLastLineIndent = null;
    }

    myFlags = flagsValue;
  }

  public final boolean containsLineFeeds() {
    return (myFlags & CONTAIN_LINE_FEEDS) != 0;
  }

  public int getSymbolsAtTheLastLine() {
    return mySymbolsAtTheLastLine;
  }

  public LeafBlockWrapper getPreviousBlock() {
    return myPreviousBlock;
  }

  public LeafBlockWrapper getNextBlock() {
    return myNextBlock;
  }

  public void setNextBlock(final LeafBlockWrapper nextBlock) {
    myNextBlock = nextBlock;
  }

  protected boolean indentAlreadyUsedBefore(final AbstractBlockWrapper child) {
    return false;
  }

  public void dispose() {
    super.dispose();
    myPreviousBlock = null;
    myNextBlock = null;
    mySpaceProperty = null;
    myLastLineIndent = null;
  }

  /**
   * @return    spacing between current block and its left sibling
   */
  public SpacingImpl getSpaceProperty() {
    return mySpaceProperty;
  }

  public IndentData calculateOffset(final CodeStyleSettings.IndentOptions options) {

    if (myIndentFromParent != null) {
      final AbstractBlockWrapper firstIndentedParent = findFirstIndentedParent();
      final IndentData indentData = new IndentData(myIndentFromParent.getIndentSpaces(), myIndentFromParent.getSpaces());
      if (firstIndentedParent == null) {
        return indentData;
      } else {
        final WhiteSpace whiteSpace = firstIndentedParent.getWhiteSpace();
        return new IndentData(whiteSpace.getIndentOffset(), whiteSpace.getSpaces()).add(indentData);
      }
    }

    if (myParent == null) return new IndentData(0);
    if (getIndent().isAbsolute()) {
      setCanUseFirstChildIndentAsBlockIndent(false);
      AbstractBlockWrapper current = this;
      while (current != null && current.getStartOffset() == getStartOffset()) {
        current.setCanUseFirstChildIndentAsBlockIndent(false);
        current = current.myParent;
      }
    }

    ArrayList<IndentData> ignored = new ArrayList<IndentData>();
    IndentData result = myParent.getChildOffset(this, options, this.getStartOffset());
    if (!ignored.isEmpty()) {
      result = result.add(ignored.get(ignored.size() - 1));
    }
    return result;
  }

  public void setSpaceProperty(final SpacingImpl currentSpaceProperty) {
    mySpaceProperty = currentSpaceProperty;
  }

  @Nullable
  public IndentInfo calcIndentFromParent() {
    AbstractBlockWrapper firstIndentedParent = findFirstIndentedParent();
    final WhiteSpace mySpace = getWhiteSpace();
    if (firstIndentedParent != null) {
      final WhiteSpace parentSpace = firstIndentedParent.getWhiteSpace();
      return new IndentInfo(0,
                            mySpace.getIndentOffset() - parentSpace.getIndentOffset(),
                            mySpace.getSpaces() - parentSpace.getSpaces());
    } else {
      return null;
    }
  }

  public final boolean isLeaf() {
    return (myFlags & LEAF) != 0;
  }

  public IndentInside getLastLineIndent() {
    return myLastLineIndent;
  }

  public boolean contains(final int offset) {
    return myStart < offset && myEnd > offset;
  }

  public TextRange getTextRange() {
    return new TextRange(myStart, myEnd);
  }

  @Override
  public String toString() {
    return "LeafBlockWrapper(" + myStart + "-" + myEnd + ")";
  }
}
