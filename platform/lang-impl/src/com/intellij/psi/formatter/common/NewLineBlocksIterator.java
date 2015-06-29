/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.formatter.common;

import com.intellij.formatting.Block;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

import java.util.*;


public class NewLineBlocksIterator implements Iterator<Block> {
  private final Document myDocument;
  private final int myTotalLines;

  private int myCurrentLineStartOffset;
  private int myCurrentDocumentLine;
  private Stack<Block> myStack = new Stack<Block>();

  public NewLineBlocksIterator(Block root, Document document) {
    myStack.add(root);
    myDocument = document;
    myTotalLines = myDocument.getLineCount();

    myCurrentDocumentLine = 0;
    myCurrentLineStartOffset = 0;
  }

  @Override
  public boolean hasNext() {
    if (myCurrentDocumentLine < myTotalLines) {
      popUntilTopBlockStartOffsetGreaterOrEqual(myCurrentLineStartOffset);
      return !myStack.isEmpty();
    }
    return false;
  }

  @Override
  public Block next() {
    popUntilTopBlockStartOffsetGreaterOrEqual(myCurrentLineStartOffset);

    Block current = myStack.peek();
    TextRange currentBlockRange = current.getTextRange();

    myCurrentDocumentLine = myDocument.getLineNumber(currentBlockRange.getStartOffset());
    myCurrentDocumentLine++;
    if (myCurrentDocumentLine < myTotalLines) {
      myCurrentLineStartOffset = myDocument.getLineStartOffset(myCurrentDocumentLine);
      if (currentBlockRange.getEndOffset() < myCurrentLineStartOffset) {
        myStack.pop();
      }
      else {
        pushAll(current);
      }
    }

    return current;
  }

  private void popUntilTopBlockStartOffsetGreaterOrEqual(final int lineStartOffset) {
    while (!myStack.isEmpty()) {
      Block current = myStack.peek();
      TextRange range = current.getTextRange();
      if (range.getStartOffset() < lineStartOffset) {
        myStack.pop();
        if (range.getEndOffset() > lineStartOffset) {
          pushAll(current);
        }
      }
      else {
        break;
      }
    }
  }

  private void pushAll(Block current) {
    if (current instanceof AbstractBlock) {
      //building blocks as fast as possible
      ((AbstractBlock)current).setBuildInjectedBlocks(false);
    }

    List<Block> blocks = current.getSubBlocks();
    ListIterator<Block> iterator = blocks.listIterator(blocks.size());
    while (iterator.hasPrevious()) {
      myStack.push(iterator.previous());
    }
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
