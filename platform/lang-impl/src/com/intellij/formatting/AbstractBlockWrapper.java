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

/**
 * @author lesya
 */
public abstract class AbstractBlockWrapper {
  protected WhiteSpace myWhiteSpace;
  protected CompositeBlockWrapper myParent;
  protected int myStart;
  protected int myEnd;
  protected int myFlags;

  static int CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT = 1;
  static int INCOMPLETE = 2;

  protected IndentInfo myIndentFromParent = null;
  private IndentImpl myIndent = null;
  private AlignmentImpl myAlignment;
  private WrapImpl myWrap;

  public AbstractBlockWrapper(final Block block, final WhiteSpace whiteSpace, final CompositeBlockWrapper parent, final TextRange textRange) {
    myWhiteSpace = whiteSpace;
    myParent = parent;
    myStart = textRange.getStartOffset();
    myEnd = textRange.getEndOffset();

    myFlags = CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT | (block.isIncomplete() ? INCOMPLETE:0);
    myAlignment = (AlignmentImpl)block.getAlignment();
    myWrap = (WrapImpl)block.getWrap();
  }

  public WhiteSpace getWhiteSpace() {
    return myWhiteSpace;
  }

  public ArrayList<WrapImpl> getWraps() {
    final ArrayList<WrapImpl> result = new ArrayList<WrapImpl>(3);
    AbstractBlockWrapper current = this;
    while(current != null && current.getStartOffset() == getStartOffset()) {
      final WrapImpl wrap = current.getOwnWrap();
      if (wrap != null && !result.contains(wrap)) result.add(0, wrap);
      if (wrap != null && wrap.getIgnoreParentWraps()) break;
      current = current.myParent;
    }
    return result;
  }

  public int getStartOffset() {
    return myStart;
  }

  public int getEndOffset() {
    return myEnd;
  }

  public int getLength() {
    return myEnd - myStart;
  }

  /**
   * Applies given start offset to the current block wrapper and recursively calls this method on parent block wrapper
   * if it starts at the same place as the current one.
   *
   * @param startOffset     new start offset value to apply
   */
  protected void arrangeStartOffset(final int startOffset) {
    if (getStartOffset() == startOffset) return;
    boolean isFirst = getParent() != null && getStartOffset() == getParent().getStartOffset();
    myStart = startOffset;
    if (isFirst) {
      getParent().arrangeStartOffset(startOffset);
    }
  }

  public IndentImpl getIndent(){
    return myIndent;
  }

  public CompositeBlockWrapper getParent() {
    return myParent;
  }

  @Nullable
  public WrapImpl getWrap() {
    final ArrayList<WrapImpl> wraps = getWraps();
    if (wraps.size() == 0) return null;
    return wraps.get(0);
  }

  /**
   * @return    wrap object configured for the current block wrapper if any; <code>null</code> otherwise
   */
  public WrapImpl getOwnWrap() {
    return myWrap;
  }

  public void reset() {
    myFlags |= CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT;
    final AlignmentImpl alignment = myAlignment;
    if (alignment != null) alignment.reset();
    final WrapImpl wrap = myWrap;
    if (wrap != null) wrap.reset();
  }

  /**
   * Calculates indent for the given block and target start offset according to the given idnent options.
   *
   * @param options                 indent options to use
   * @param block                   target wrapped block
   * @param tokenBlockStartOffset   target wrapped block offset
   * @return                        indent to use for the given parameters
   */
  private static IndentData getIndent(CodeStyleSettings.IndentOptions options,
                                      AbstractBlockWrapper block,
                                      final int tokenBlockStartOffset) {
    final IndentImpl indent = block.getIndent();
    if (indent.getType() == IndentImpl.Type.CONTINUATION) {
      return new IndentData(options.CONTINUATION_INDENT_SIZE);
    }
    if (indent.getType() == IndentImpl.Type.CONTINUATION_WITHOUT_FIRST) {
      if (block.getStartOffset() != block.getParent().getStartOffset() && block.getStartOffset() == tokenBlockStartOffset) {
        return new IndentData(options.CONTINUATION_INDENT_SIZE);
      }
      else {
        return new IndentData(0);
      }
    }
    if (indent.getType() == IndentImpl.Type.LABEL) return new IndentData(options.LABEL_INDENT_SIZE);
    if (indent.getType() == IndentImpl.Type.NONE) return new IndentData(0);
    if (indent.getType() == IndentImpl.Type.SPACES) return new IndentData(0, indent.getSpaces());
    return new IndentData(options.INDENT_SIZE);

  }

  public IndentData getChildOffset(AbstractBlockWrapper child, CodeStyleSettings.IndentOptions options, final int tokenBlockStartOffset) {
    final boolean childOnNewLine = child.getWhiteSpace().containsLineFeeds();
    final IndentData childIndent;

    // Calculate child indent.
    if (childOnNewLine) {
      childIndent = getIndent(options, child, tokenBlockStartOffset);
    }
    else {
      IndentImpl.Type type = child.getIndent().getType();
      if (!getWhiteSpace().containsLineFeeds()
          && (type == IndentImpl.Type.NORMAL || type == IndentImpl.Type.CONTINUATION || type == IndentImpl.Type.CONTINUATION_WITHOUT_FIRST)
          && indentAlreadyUsedBefore(child))
      {
        childIndent = getIndent(options, child, tokenBlockStartOffset);
      }
      else {
        childIndent = new IndentData(0);
      }
    }

    // Use child indent if it's absolute and the child is contained on new line.
    if (childOnNewLine && child.getIndent().isAbsolute()) {
      myFlags &= ~CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT;
      AbstractBlockWrapper current = this;
      while (current != null && current.getStartOffset() == getStartOffset()) {
        current.myFlags &= ~CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT;
        current = current.myParent;
      }
      return childIndent;
    }

    if (child.getStartOffset() == getStartOffset()) {
      final boolean newValue = (myFlags & CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT) != 0 &&
                               (child.myFlags & CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT) != 0 && childIndent.isEmpty();
      setCanUseFirstChildIndentAsBlockIndent(newValue);
    }

    if (getStartOffset() == tokenBlockStartOffset) {
      if (myParent == null) {
        return childIndent;
      }
      else {
        return childIndent.add(myParent.getChildOffset(this, options, tokenBlockStartOffset));
      }
    } else if (!getWhiteSpace().containsLineFeeds()) {
      return childIndent.add(myParent.getChildOffset(this, options, tokenBlockStartOffset));
    } else {
      if (myParent == null) return  childIndent.add(getWhiteSpace());
      if (getIndent().isAbsolute()) {
        if (myParent.myParent != null) {
          return childIndent.add(myParent.myParent.getChildOffset(myParent, options, tokenBlockStartOffset));
        }
        else {
          return  childIndent.add(getWhiteSpace());
        }
      }
      if ((myFlags & CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT) != 0) {
        return childIndent.add(getWhiteSpace());
      }
      else {
        return childIndent.add(myParent.getChildOffset(this, options, tokenBlockStartOffset));
      }
    }
  }

  /**
   * Allows to answer if current wrapped block has a child block that is located before given block and has line feed.
   *
   * @param child   target child block to process
   * @return        <code>true</code> if current block has a child that is located before the given block and contains line feed;
   *                <code>false</code> otherwise
   */
  protected abstract boolean indentAlreadyUsedBefore(final AbstractBlockWrapper child);

  protected final void setCanUseFirstChildIndentAsBlockIndent(final boolean newValue) {
    if (newValue) myFlags |= CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT;
    else myFlags &= ~CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT;
  }

  /**
   * Applies start offset of the current block wrapper to the parent block wrapper if the one is defined.
   */
  public void arrangeParentTextRange() {
    if (myParent != null) {
      myParent.arrangeStartOffset(getStartOffset());
    }
  }
  public IndentData calculateChildOffset(final CodeStyleSettings.IndentOptions indentOption, final ChildAttributes childAttributes,
                                         int index) {
    IndentImpl childIndent = (IndentImpl)childAttributes.getChildIndent();

    if (childIndent == null) childIndent = (IndentImpl)Indent.getContinuationWithoutFirstIndent();

    IndentData indent = getIndent(indentOption, index, childIndent);
    if (myParent == null) {
      return indent.add(getWhiteSpace());
    } else if ((myFlags & CAN_USE_FIRST_CHILD_INDENT_AS_BLOCK_INDENT) != 0 && getWhiteSpace().containsLineFeeds()) {
      return indent.add(getWhiteSpace());
    }
    else {
      ArrayList<IndentData> ignored = new ArrayList<IndentData>();
      IndentData offsetFromParent = myParent.getChildOffset(this, indentOption, -1);
      IndentData result = indent.add(offsetFromParent);
      if (!ignored.isEmpty()) {
        result = result.add(ignored.get(ignored.size() - 1));
      }
      return result;
    }

  }

  private IndentData getIndent(final CodeStyleSettings.IndentOptions options, final int index, IndentImpl indent) {
    if (indent.getType() == IndentImpl.Type.CONTINUATION) {
      return new IndentData(options.CONTINUATION_INDENT_SIZE);
    }
    if (indent.getType() == IndentImpl.Type.CONTINUATION_WITHOUT_FIRST) {
      if (index != 0) {
        return new IndentData(options.CONTINUATION_INDENT_SIZE);
      }
      else {
        return new IndentData(0);
      }
    }
    if (indent.getType() == IndentImpl.Type.LABEL) return new IndentData(options.LABEL_INDENT_SIZE);
    if (indent.getType() == IndentImpl.Type.NONE) return new IndentData(0);
    if (indent.getType() == IndentImpl.Type.SPACES) return new IndentData(0, indent.getSpaces());
    return new IndentData(options.INDENT_SIZE);

  }

  /**
   * Applies given indent value to '<code>indentFromParent'</code> property of the current wrapped block.
   * <p/>
   * Given value is also applied to '<code>indentFromParent'</code> properties of all parents of the current wrapped block if the
   * value is defined (not <code>null</code>).
   * <p/>
   * This property is used later during
   * {@link LeafBlockWrapper#calculateOffset(CodeStyleSettings.IndentOptions) leaf block offset calculation}.
   *
   * @param indentFromParent    indent value to apply
   */
  public void setIndentFromParent(final IndentInfo indentFromParent) {
    myIndentFromParent = indentFromParent;
    if (myIndentFromParent != null) {
      AbstractBlockWrapper parent = myParent;
      if (myParent != null && myParent.getStartOffset() == myStart) {
        parent.setIndentFromParent(myIndentFromParent);
      }
    }    
  }

  /**
   * Tries to find first parent block of the current block that starts before the current block and which
   * {@link WhiteSpace white space} contains line feeds.
   *
   * @return    first parent block that starts before the current block and which white space contains line feeds if any;
   *            <code>null</code> otherwise
   */
  @Nullable
  protected AbstractBlockWrapper findFirstIndentedParent() {
    if (myParent == null) return null;
    if (myStart != myParent.getStartOffset() && myParent.getWhiteSpace().containsLineFeeds()) return myParent;
    return myParent.findFirstIndentedParent();
  }

  public void setIndent(final IndentImpl indent) {
    myIndent = indent;
  }

  public AlignmentImpl getAlignment() {
    return myAlignment;
  }

  public boolean isIncomplete() {
    return (myFlags & INCOMPLETE) != 0;
  }

  public void dispose() {
    myAlignment = null;
    myWrap = null;
    myIndent = null;
    myIndentFromParent = null;
    myParent = null;
    myWhiteSpace = null;
  }
}
