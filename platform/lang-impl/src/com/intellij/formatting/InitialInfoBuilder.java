/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.ReadOnlyBlockInformationProvider;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Allows to build {@link AbstractBlockWrapper formatting block wrappers} for the target {@link Block formatting blocks}.
 * The main idea of block wrapping is to associate information about {@link WhiteSpace white space before block} with the block itself.
 */
class InitialInfoBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.InitialInfoBuilder");

  private final Map<AbstractBlockWrapper, Block> myResult = new THashMap<AbstractBlockWrapper, Block>();

  private final FormattingDocumentModel               myModel;
  private final FormatTextRanges                      myAffectedRanges;
  private final int                                   myPositionOfInterest;
  @NotNull
  private final FormattingProgressCallback            myProgressCallback;
  private final FormatterTagHandler                   myFormatterTagHandler;

  private final CommonCodeStyleSettings.IndentOptions myOptions;

  private final Stack<State> myStates = new Stack<State>();
  private WhiteSpace                       myCurrentWhiteSpace;
  private CompositeBlockWrapper            myRootBlockWrapper;
  private LeafBlockWrapper                 myPreviousBlock;
  private LeafBlockWrapper                 myFirstTokenBlock;
  private LeafBlockWrapper                 myLastTokenBlock;
  private SpacingImpl                      myCurrentSpaceProperty;
  private ReadOnlyBlockInformationProvider myReadOnlyBlockInformationProvider;
  private boolean                          myInsideFormatRestrictingTag;

  private static final boolean INLINE_TABS_ENABLED = "true".equalsIgnoreCase(System.getProperty("inline.tabs.enabled"));

  private Set<Alignment> myAlignmentsInsideRangeToModify = ContainerUtil.newHashSet();
  private boolean myCollectAlignmentsInsideFormattingRange = false;

  private InitialInfoBuilder(final Block rootBlock,
                             final FormattingDocumentModel model,
                             @Nullable final FormatTextRanges affectedRanges,
                             @NotNull CodeStyleSettings settings,
                             final CommonCodeStyleSettings.IndentOptions options,
                             final int positionOfInterest,
                             @NotNull FormattingProgressCallback progressCallback)
  {
    myModel = model;
    myAffectedRanges = affectedRanges;
    myProgressCallback = progressCallback;
    myCurrentWhiteSpace = new WhiteSpace(getStartOffset(rootBlock), true);
    myOptions = options;
    myPositionOfInterest = positionOfInterest;
    myInsideFormatRestrictingTag = false;
    myFormatterTagHandler = new FormatterTagHandler(settings);
  }

  protected static InitialInfoBuilder prepareToBuildBlocksSequentially(Block root,
                                                                    FormattingDocumentModel model,
                                                                    @Nullable final FormatTextRanges affectedRanges,
                                                                    @NotNull CodeStyleSettings settings,
                                                                    final CommonCodeStyleSettings.IndentOptions options,
                                                                    int interestingOffset,
                                                                    @NotNull FormattingProgressCallback progressCallback)
  {
    InitialInfoBuilder builder = new InitialInfoBuilder(root, model, affectedRanges, settings, options, interestingOffset, progressCallback);
    builder.buildFrom(root, 0, null, null, null, true);
    return builder;
  }

  private int getStartOffset(@NotNull Block rootBlock) {
    int minOffset = rootBlock.getTextRange().getStartOffset();
    if (myAffectedRanges != null) {
      for (FormatTextRanges.FormatTextRange range : myAffectedRanges.getRanges()) {
        if (range.getStartOffset() < minOffset) minOffset = range.getStartOffset();
      }
    }
    return minOffset;
  }

  int getEndOffset() {
    int maxDocOffset = myModel.getTextLength();
    int maxOffset = myRootBlockWrapper != null ? myRootBlockWrapper.getEndOffset() : 0;
    if (myAffectedRanges != null) {
      for (FormatTextRanges.FormatTextRange range : myAffectedRanges.getRanges()) {
        if (range.getTextRange().getEndOffset() > maxOffset) maxOffset = range.getTextRange().getEndOffset();
      }
    }
    return   maxOffset < maxDocOffset ? maxOffset : maxDocOffset;
  }

  /**
   * Asks current builder to wrap one more remaining {@link Block code block} (if any).
   *
   * @return    <code>true</code> if all blocks are wrapped; <code>false</code> otherwise
   */
  public boolean iteration() {
    if (myStates.isEmpty()) {
      return true;
    }

    State state = myStates.peek();
    doIteration(state);
    return myStates.isEmpty();
  }
  
  /**
   * Wraps given root block and all of its descendants and returns root block wrapper.
   * <p/>
   * This method performs necessary infrastructure actions and delegates actual processing to
   * {@link #buildCompositeBlock(Block, CompositeBlockWrapper, int, WrapImpl, boolean)} and
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
                                         @Nullable final CompositeBlockWrapper parent,
                                         @Nullable WrapImpl currentWrapParent,
                                         @Nullable final Block parentBlock,
                                         boolean rootBlockIsRightBlock)
  {
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

    boolean isInsideFormattingRanges = isInsideFormattingRanges(rootBlock, rootBlockIsRightBlock);
    if (myCollectAlignmentsInsideFormattingRange && rootBlock.getAlignment() != null
        && isInsideFormattingRanges && !myInsideFormatRestrictingTag)
    {
      myAlignmentsInsideRangeToModify.add(rootBlock.getAlignment());
    }

    ReadOnlyBlockInformationProvider previousProvider = myReadOnlyBlockInformationProvider;
    try {
      if (rootBlock instanceof ReadOnlyBlockInformationProvider) {
        myReadOnlyBlockInformationProvider = (ReadOnlyBlockInformationProvider)rootBlock;
      }

      final List<Block> subBlocks = rootBlock.getSubBlocks();
      if (subBlocks.isEmpty() || myReadOnlyBlockInformationProvider != null
                                 && myReadOnlyBlockInformationProvider.isReadOnly(rootBlock)) {
        final AbstractBlockWrapper wrapper = processSimpleBlock(rootBlock, parent, false, index, parentBlock);
        if (!subBlocks.isEmpty()) {
          wrapper.setIndent((IndentImpl)subBlocks.get(0).getIndent());
        }
        return wrapper;
      }
      return buildCompositeBlock(rootBlock, parent, index, currentWrapParent, rootBlockIsRightBlock);
    }
    finally {
      myReadOnlyBlockInformationProvider = previousProvider;
    }
  }

  private CompositeBlockWrapper buildCompositeBlock(final Block rootBlock,
                                   @Nullable final CompositeBlockWrapper parent,
                                   final int index,
                                   @Nullable final WrapImpl currentWrapParent,
                                   boolean rootBlockIsRightBlock)
  {
    final CompositeBlockWrapper wrappedRootBlock = new CompositeBlockWrapper(rootBlock, myCurrentWhiteSpace, parent);
    if (index == 0) {
      wrappedRootBlock.arrangeParentTextRange();
    }

    if (myRootBlockWrapper == null) {
      myRootBlockWrapper = wrappedRootBlock;
      myRootBlockWrapper.setIndent((IndentImpl)Indent.getNoneIndent());
    }
    boolean blocksMayBeOfInterest = false;

    if (myPositionOfInterest != -1) {
      myResult.put(wrappedRootBlock, rootBlock);
      blocksMayBeOfInterest = true;
    }
    final boolean blocksAreReadOnly = rootBlock instanceof ReadOnlyBlockContainer || blocksMayBeOfInterest;
    
    State state = new State(rootBlock, wrappedRootBlock, currentWrapParent, blocksAreReadOnly, rootBlockIsRightBlock);
    myStates.push(state);
    return wrappedRootBlock;
  }

  private void doIteration(@NotNull State state) {
    List<Block> subBlocks = state.parentBlock.getSubBlocks();
    final int subBlocksCount = subBlocks.size();
    int childBlockIndex = state.getIndexOfChildBlockToProcess();
    final Block block = subBlocks.get(childBlockIndex);
    if (state.previousBlock != null || (myCurrentWhiteSpace != null && myCurrentWhiteSpace.isIsFirstWhiteSpace())) {
      myCurrentSpaceProperty = (SpacingImpl)state.parentBlock.getSpacing(state.previousBlock, block);
    }

    boolean childBlockIsRightBlock = false;

    if (childBlockIndex == subBlocksCount - 1 && state.parentBlockIsRightBlock) {
      childBlockIsRightBlock = true;
    }

    final AbstractBlockWrapper wrapper = buildFrom(
      block, childBlockIndex, state.wrappedBlock, state.parentBlockWrap, state.parentBlock, childBlockIsRightBlock
    );

    if (wrapper.getIndent() == null) {
      wrapper.setIndent((IndentImpl)block.getIndent());
    }
    if (!state.readOnly) {
      try {
        subBlocks.set(childBlockIndex, null); // to prevent extra strong refs during model building
      } catch (Throwable ex) {
        // read-only blocks
      }
    }
    
    if (state.childBlockProcessed(block, wrapper)) {
      while (!myStates.isEmpty() && myStates.peek().isProcessed()) {
        myStates.pop();
      }
    }
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
                                                  @Nullable final CompositeBlockWrapper parent,
                                                  final boolean readOnly,
                                                  final int index,
                                                  @Nullable Block parentBlock) 
  {
    LeafBlockWrapper result = doProcessSimpleBlock(rootBlock, parent, readOnly, index, parentBlock);
    myProgressCallback.afterWrappingBlock(result);
    return result;
  }

  private LeafBlockWrapper doProcessSimpleBlock(final Block rootBlock,
                                                @Nullable final CompositeBlockWrapper parent,
                                                final boolean readOnly,
                                                final int index,
                                                @Nullable Block parentBlock)
  {
    if (!INLINE_TABS_ENABLED && !myCurrentWhiteSpace.containsLineFeeds()) {
      myCurrentWhiteSpace.setForceSkipTabulationsUsage(true);
    }
    final LeafBlockWrapper info =
      new LeafBlockWrapper(rootBlock, parent, myCurrentWhiteSpace, myModel, myOptions, myPreviousBlock, readOnly);
    if (index == 0) {
      info.arrangeParentTextRange();
    }

    switch (myFormatterTagHandler.getFormatterTag(rootBlock)) {
      case ON:
        myInsideFormatRestrictingTag = false;
        break;
      case OFF:
        myInsideFormatRestrictingTag = true;
        break;
      case NONE:
        break;
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

    if (info.isEndOfCodeBlock()) {
      myCurrentWhiteSpace.setBeforeCodeBlockEnd(true);
    }

    info.setSpaceProperty(myCurrentSpaceProperty);
    myCurrentWhiteSpace = new WhiteSpace(textRange.getEndOffset(), false);
    if (myInsideFormatRestrictingTag) myCurrentWhiteSpace.setReadOnly(true);
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

  private boolean isInsideFormattingRanges(final Block block, boolean rootIsRightBlock) {
    if (myAffectedRanges == null) return true;
    return !myAffectedRanges.isReadOnly(block.getTextRange(), rootIsRightBlock);
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

    final StringBuilder messageBuffer =  new StringBuilder();
    messageBuffer.append("Invalid ranges during formatting");
    if (model instanceof FormattingDocumentModelImpl) {
      messageBuffer.append(" in ").append(((FormattingDocumentModelImpl)model).getFile().getLanguage());
    }

    buffer.append("File text:(").append(model.getTextLength()).append(")\n'");
    buffer.append(model.getText(new TextRange(0, model.getTextLength())).toString());
    buffer.append("'\n");
    buffer.append("model (").append(model.getClass()).append("): ").append(model);

    Throwable currentThrowable = new Throwable();
    if (model instanceof FormattingDocumentModelImpl) {
      final FormattingDocumentModelImpl modelImpl = (FormattingDocumentModelImpl)model;
      buffer.append("Psi Tree:\n");
      final PsiFile file = modelImpl.getFile();
      final List<PsiFile> roots = file.getViewProvider().getAllFiles();
      for (PsiFile root : roots) {
        buffer.append("Root ");
        DebugUtil.treeToBuffer(buffer, root.getNode(), 0, false, true, true, true);
      }
      buffer.append('\n');
      currentThrowable = makeLanguageStackTrace(currentThrowable, file);
    }

    LogMessageEx.error(LOG, messageBuffer.toString(), currentThrowable, buffer.toString());
  }
  
  private static Throwable makeLanguageStackTrace(@NotNull Throwable currentThrowable, @NotNull PsiFile file) {
    Throwable langThrowable = new Throwable();
    FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(file);
    if (builder == null) return currentThrowable;
    Class builderClass = builder.getClass();
    Class declaringClass = builderClass.getDeclaringClass();
    String guessedFileName = (declaringClass == null ? builderClass.getSimpleName() : declaringClass.getSimpleName())  + ".java";
    StackTraceElement ste = new StackTraceElement(builder.getClass().getName(), "createModel", guessedFileName, 1);
    StackTraceElement[] originalStackTrace = currentThrowable.getStackTrace();
    StackTraceElement[] modifiedStackTrace = new StackTraceElement[originalStackTrace.length + 1];
    System.arraycopy(originalStackTrace, 0, modifiedStackTrace, 1, originalStackTrace.length);
    modifiedStackTrace[0] = ste;
    langThrowable.setStackTrace(modifiedStackTrace);
    return langThrowable;
  }

  public Set<Alignment> getAlignmentsInsideRangeToModify() {
    return myAlignmentsInsideRangeToModify;
  }

  public void setCollectAlignmentsInsideFormattingRange(boolean value) {
    myCollectAlignmentsInsideFormattingRange = value;
  }

  /**
   * We want to wrap {@link Block code blocks} sequentially, hence, need to store a processing state and continue from the point
   * where we stopped the processing last time.
   * <p/>
   * Current class defines common contract for the state required for such a processing.
   */
  private class State {

    public final Block                 parentBlock;
    public final WrapImpl              parentBlockWrap;
    public final CompositeBlockWrapper wrappedBlock;
    public final boolean               readOnly;
    public final boolean               parentBlockIsRightBlock;
    
    public Block previousBlock;
    
    private final List<AbstractBlockWrapper> myWrappedChildren = new ArrayList<AbstractBlockWrapper>();

    State(@NotNull Block parentBlock, @NotNull CompositeBlockWrapper wrappedBlock, @Nullable WrapImpl parentBlockWrap,
          boolean readOnly, boolean parentBlockIsRightBlock)
    {
      this.parentBlock = parentBlock;
      this.wrappedBlock = wrappedBlock;
      this.parentBlockWrap = parentBlockWrap;
      this.readOnly = readOnly;
      this.parentBlockIsRightBlock = parentBlockIsRightBlock;
    }

    /**
     * @return    index of the first non-processed {@link Block#getSubBlocks() child block} of the {@link #parentBlock target block}
     */
    public int getIndexOfChildBlockToProcess() {
      return myWrappedChildren.size();
    }
    
    /**
     * Notifies current state that child block is processed.
     * 
     * @return    <code>true</code> if all child blocks of the block denoted by the current state are processed;
     *            <code>false</code> otherwise
     */
    public boolean childBlockProcessed(@NotNull Block child, @NotNull AbstractBlockWrapper wrappedChild) {
      myWrappedChildren.add(wrappedChild);
      previousBlock = child;
      
      int subBlocksNumber = parentBlock.getSubBlocks().size();
      if (myWrappedChildren.size() > subBlocksNumber) {
        return true;
      }
      else if (myWrappedChildren.size() == subBlocksNumber) {
        setDefaultIndents(myWrappedChildren);
        wrappedBlock.setChildren(myWrappedChildren);
        return true;
      }
      return false;
    }

    /**
     * @return    <code>true</code> if current state is processed (basically, if all {@link Block#getSubBlocks() child blocks})
     *            of the {@link #parentBlock target block} are processed; <code>false</code> otherwise
     */
    public boolean isProcessed() {
      return myWrappedChildren.size() == parentBlock.getSubBlocks().size();
    }
  }
}
