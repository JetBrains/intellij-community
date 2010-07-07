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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.ReadOnlyBlockInformationProvider;
import com.intellij.psi.impl.DebugUtil;
import gnu.trove.THashMap;
import org.apache.commons.collections.list.UnmodifiableList;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Allows to build {@link AbstractBlockWrapper formatting block wrappers} for the target {@link Block formatting blocks}.
 * The main idea of block wrapping is to associate information about {@link WhiteSpace white space before block} with the block itself.
 */
class InitialInfoBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.InitialInfoBuilder");

  private WhiteSpace myCurrentWhiteSpace;
  private final FormattingDocumentModel myModel;
  private final FormatTextRanges myAffectedRanges;
  private final int myPositionOfInterest;
  private final Map<AbstractBlockWrapper, Block> myResult = new THashMap<AbstractBlockWrapper, Block>();
  private CompositeBlockWrapper myRootBlockWrapper;
  private LeafBlockWrapper myPreviousBlock;
  private LeafBlockWrapper myFirstTokenBlock;
  private LeafBlockWrapper myLastTokenBlock;
  private SpacingImpl myCurrentSpaceProperty;
  private final CodeStyleSettings.IndentOptions myOptions;
  private ReadOnlyBlockInformationProvider myReadOnlyBlockInformationProvider;

  private InitialInfoBuilder(final FormattingDocumentModel model,
                             final FormatTextRanges affectedRanges,
                             final CodeStyleSettings.IndentOptions options,
                             final int positionOfInterest
  ) {
    myModel = model;
    myAffectedRanges = affectedRanges;
    myCurrentWhiteSpace = new WhiteSpace(0, true);
    myOptions = options;
    myPositionOfInterest = positionOfInterest;
  }

  public static InitialInfoBuilder buildBlocks(Block root,
                                               FormattingDocumentModel model,
                                               final FormatTextRanges affectedRanges,
                                               final CodeStyleSettings.IndentOptions options,
                                               int interestingOffset) {
    final InitialInfoBuilder builder = new InitialInfoBuilder(model, affectedRanges, options, interestingOffset);
    final AbstractBlockWrapper wrapper = builder.buildFrom(root, 0, null, null, null, true);
    wrapper.setIndent((IndentImpl)Indent.getNoneIndent());
    return builder;
  }

  /**
   * Wraps given root block and all of its descendants and returns root block wrapper.
   * <p/>
   * This method performs necessary infrastructure actions and delegates actual processing to
   * {@link #processCompositeBlock(Block, CompositeBlockWrapper, int, WrapImpl, boolean)} and
   * {@link #processSimpleBlock(Block, CompositeBlockWrapper, boolean, int, Block)}.
   *
   * @param rootBlock             block to wrap
   * @param index                 index of the current block at its parent block. <code>-1</code> may be used here if we don't
   *                              have information about parent block
   * @param parent                parent block wrapper. <code>null</code> may be used here we no parent block wrapper exists
   * @param currentWrapParent     parent wrap if any; <code>null</code> otherwise
   * @param parentBlock           parent block of the block to wrap
   * @param rootBlockIsRightBlock flag that shows if target block is the right-most block
   * @return wrapper for the given <code>'rootBlock'</code>
   */
  private AbstractBlockWrapper buildFrom(final Block rootBlock,
                                         final int index,
                                         final CompositeBlockWrapper parent,
                                         WrapImpl currentWrapParent,
                                         final Block parentBlock,
                                         boolean rootBlockIsRightBlock
  ) {
    final WrapImpl wrap = (WrapImpl)rootBlock.getWrap();
    if (wrap != null) {
      wrap.registerParent(currentWrapParent);
      currentWrapParent = wrap;
    }
    TextRange textRange = rootBlock.getTextRange();
    final int blockStartOffset = textRange.getStartOffset();

    if (parent != null) {
      if (textRange.getStartOffset() < parent.getStartOffset()) {
        assertInvalidRanges(
          textRange.getStartOffset(),
          parent.getStartOffset(),
          myModel,
          "child block start is less than parent block start"
        );
      }

      if (textRange.getEndOffset() > parent.getEndOffset()) {
        assertInvalidRanges(
          textRange.getEndOffset(),
          parent.getEndOffset(),
          myModel,
          "child block end is after parent block end"
        );
      }
    }

    myCurrentWhiteSpace.append(blockStartOffset, myModel, myOptions);
    boolean isReadOnly = isReadOnly(textRange, rootBlockIsRightBlock);

    ReadOnlyBlockInformationProvider previousProvider = myReadOnlyBlockInformationProvider;
    try {
      if (rootBlock instanceof ReadOnlyBlockInformationProvider) {
        myReadOnlyBlockInformationProvider = (ReadOnlyBlockInformationProvider)rootBlock;
      }
      if (isReadOnly) {
        return processSimpleBlock(rootBlock, parent, isReadOnly, index, parentBlock);
      }

      final List<Block> subBlocks = rootBlock.getSubBlocks();
      if (subBlocks.isEmpty() || myReadOnlyBlockInformationProvider != null
                                 && myReadOnlyBlockInformationProvider.isReadOnly(rootBlock)) {
        final AbstractBlockWrapper wrapper = processSimpleBlock(rootBlock, parent, isReadOnly, index, parentBlock);
        if (!subBlocks.isEmpty()) {
          wrapper.setIndent((IndentImpl)subBlocks.get(0).getIndent());
        }
        return wrapper;
      }
      return processCompositeBlock(rootBlock, parent, index, currentWrapParent, rootBlockIsRightBlock);
    }
    finally {
      myReadOnlyBlockInformationProvider = previousProvider;
    }
  }

  private AbstractBlockWrapper processCompositeBlock(final Block rootBlock,
                                                     final CompositeBlockWrapper parent,
                                                     final int index,
                                                     final WrapImpl currentWrapParent,
                                                     boolean rootBlockIsRightBlock
  ) {
    final CompositeBlockWrapper info = new CompositeBlockWrapper(rootBlock, myCurrentWhiteSpace, parent);
    if (index == 0) {
      info.arrangeParentTextRange();
    }

    if (myRootBlockWrapper == null) myRootBlockWrapper = info;
    boolean blocksMayBeOfInterest = false;

    if (myPositionOfInterest != -1) {
      myResult.put(info, rootBlock);
      blocksMayBeOfInterest = true;
    }

    Block previous = null;
    List<Block> subBlocks = rootBlock.getSubBlocks();
    final int subBlocksCount = subBlocks.size();
    List<AbstractBlockWrapper> list = new ArrayList<AbstractBlockWrapper>(subBlocksCount);
    final boolean blocksAreReadOnly = rootBlock instanceof ReadOnlyBlockContainer || blocksMayBeOfInterest;

    for (int i = 0; i < subBlocksCount; i++) {
      final Block block = subBlocks.get(i);
      if (previous != null) {
        myCurrentSpaceProperty = (SpacingImpl)rootBlock.getSpacing(previous, block);
      }

      boolean childBlockIsRightBlock = false;

      if (i == subBlocksCount - 1 && rootBlockIsRightBlock) {
        childBlockIsRightBlock = true;
      }

      final AbstractBlockWrapper wrapper = buildFrom(block, i, info, currentWrapParent, rootBlock, childBlockIsRightBlock);
      list.add(wrapper);

      if (wrapper.getIndent() == null) {
        wrapper.setIndent((IndentImpl)block.getIndent());
      }
      previous = block;

      if (!blocksAreReadOnly && !(subBlocks instanceof UnmodifiableList)) {
        subBlocks.set(i, null); // to prevent extra strong refs during model building
      }
    }
    setDefaultIndents(list);
    info.setChildren(list);
    return info;
  }

  private void setDefaultIndents(final List<AbstractBlockWrapper> list) {
    if (!list.isEmpty()) {
      for (AbstractBlockWrapper wrapper : list) {
        if (wrapper.getIndent() == null) {
          wrapper.setIndent((IndentImpl)Indent.getContinuationWithoutFirstIndent(myOptions.USE_RELATIVE_INDENTS));
        }
      }
    }
  }

  private AbstractBlockWrapper processSimpleBlock(final Block rootBlock,
                                                  final CompositeBlockWrapper parent,
                                                  final boolean readOnly,
                                                  final int index,
                                                  Block parentBlock

  ) {
    final LeafBlockWrapper info =
      new LeafBlockWrapper(rootBlock, parent, myCurrentWhiteSpace, myModel, myOptions, myPreviousBlock, readOnly);
    if (index == 0) {
      info.arrangeParentTextRange();
    }

    TextRange textRange = rootBlock.getTextRange();
    if (textRange.getLength() == 0) {
      assertInvalidRanges(
        textRange.getStartOffset(),
        textRange.getEndOffset(),
        myModel,
        "empty block"
      );
    }
    if (myPreviousBlock != null) {
      myPreviousBlock.setNextBlock(info);
    }
    if (myFirstTokenBlock == null) {
      myFirstTokenBlock = info;
    }
    myLastTokenBlock = info;
    if (currentWhiteSpaceIsReadOnly()) {
      myCurrentWhiteSpace.setReadOnly(true);
    }
    if (myCurrentSpaceProperty != null) {
      myCurrentWhiteSpace.setIsSafe(myCurrentSpaceProperty.isSafe());
      myCurrentWhiteSpace.setKeepFirstColumn(myCurrentSpaceProperty.shouldKeepFirstColumn());
    }

    info.setSpaceProperty(myCurrentSpaceProperty);
    myCurrentWhiteSpace = new WhiteSpace(textRange.getEndOffset(), false);
    myPreviousBlock = info;

    if (myPositionOfInterest != -1 && (textRange.contains(myPositionOfInterest) || textRange.getEndOffset() == myPositionOfInterest)) {
      myResult.put(info, rootBlock);
      if (parent != null) myResult.put(parent, parentBlock);
    }
    return info;
  }

  private boolean currentWhiteSpaceIsReadOnly() {
    if (myCurrentSpaceProperty != null && myCurrentSpaceProperty.isReadOnly()) {
      return true;
    }
    else {
      if (myAffectedRanges == null) return false;
      return myAffectedRanges.isWhitespaceReadOnly(myCurrentWhiteSpace.getTextRange());
    }
  }

  private boolean isReadOnly(final TextRange textRange, boolean rootIsRightBlock) {
    if (myAffectedRanges == null) return false;
    return myAffectedRanges.isReadOnly(textRange, rootIsRightBlock);
  }

  public Map<AbstractBlockWrapper, Block> getBlockToInfoMap() {
    return myResult;
  }

  public CompositeBlockWrapper getRootBlockWrapper() {
    return myRootBlockWrapper;
  }

  public LeafBlockWrapper getFirstTokenBlock() {
    return myFirstTokenBlock;
  }

  public LeafBlockWrapper getLastTokenBlock() {
    return myLastTokenBlock;
  }

  public static void assertInvalidRanges(final int startOffset, final int newEndOffset, FormattingDocumentModel model, String message) {
    @NonNls final StringBuilder buffer = new StringBuilder();
    buffer.append("Invalid formatting blocks:").append(message).append("\n");
    buffer.append("Start offset:");
    buffer.append(startOffset);
    buffer.append(" end offset:");
    buffer.append(newEndOffset);
    buffer.append("\n");

    int minOffset = Math.max(Math.min(startOffset, newEndOffset) - 20, 0);
    int maxOffset = Math.min(Math.max(startOffset, newEndOffset) + 20, model.getTextLength());

    buffer.append("Affected text fragment:[").append(minOffset).append(",").append(maxOffset).append("] - '")
      .append(model.getText(new TextRange(minOffset, maxOffset))).append("'\n");

    if (model instanceof FormattingDocumentModelImpl) {
      buffer.append("in ").append(((FormattingDocumentModelImpl)model).getFile().getLanguage()).append("\n");
    }

    buffer.append("File text:(").append(model.getTextLength()).append(")\n'");
    buffer.append(model.getText(new TextRange(0, model.getTextLength())).toString());
    buffer.append("'\n");

    if (model instanceof FormattingDocumentModelImpl) {
      final FormattingDocumentModelImpl modelImpl = (FormattingDocumentModelImpl)model;
      buffer.append("Psi Tree:\n");
      final PsiFile file = modelImpl.getFile();
      final PsiFile[] roots = file.getPsiRoots();
      for (PsiFile root : roots) {
        buffer.append("Root ");
        DebugUtil.treeToBuffer(buffer, root.getNode(), 0, false, true, true, true);
      }
      buffer.append('\n');
    }

    LOG.error(buffer);
  }
}
