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

package com.intellij.formatting;

import com.intellij.formatting.engine.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.formatting.InitialInfoBuilder.prepareToBuildBlocksSequentially;

public class FormatProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatProcessor");
  private Set<Alignment> myAlignmentsInsideRangesToModify = null;
  private boolean myReformatContext;

  private LeafBlockWrapper myCurrentBlock;

  private Map<AbstractBlockWrapper, Block> myInfos;
  private CompositeBlockWrapper myRootBlockWrapper;

  
  private DependentSpacingEngine myDependentSpacingEngine;
  private AlignmentHelper myAlignmentHelper;
  private IndentAdjuster myIndentAdjuster;

  private final BlockIndentOptions myBlockIndentOptions;
  private final CommonCodeStyleSettings.IndentOptions myDefaultIndentOption;
  private final CodeStyleSettings mySettings;
  private final Document myDocument;
  
  private BlockMapperHelper myBlockMapperHelper;
  

  private LeafBlockWrapper myFirstTokenBlock;
  private Ref<LeafBlockWrapper> myFirstTokenBlockRef = Ref.create(); 
  private LeafBlockWrapper myLastTokenBlock;


  private final HashSet<WhiteSpace> myAlignAgain = new HashSet<WhiteSpace>();
  @NotNull
  private final FormattingProgressCallback myProgressCallback;

  private WhiteSpace                      myLastWhiteSpace;
  private boolean                         myDisposed;
  private final int myRightMargin;

  @NotNull
  private StateProcessor myStateProcessor;
  private Ref<MultiMap<ExpandableIndent, AbstractBlockWrapper>> myExpandableIndentsRef = Ref.create();
  private Ref<IndentAdjuster> myIndentAdjusterRef = Ref.create();
  private WrapProcessor myWrapProcessor;
  private LeafBlockWrapper myWrapCandidate;

  public FormatProcessor(final FormattingDocumentModel docModel,
                         Block rootBlock,
                         CodeStyleSettings settings,
                         CommonCodeStyleSettings.IndentOptions indentOptions,
                         @Nullable FormatTextRanges affectedRanges,
                         @NotNull FormattingProgressCallback progressCallback)
  {
    this(docModel, rootBlock, new FormatOptions(settings, indentOptions, affectedRanges, false), progressCallback);
  }

  public FormatProcessor(final FormattingDocumentModel model,
                         Block block,
                         FormatOptions options,
                         @NotNull FormattingProgressCallback callback)
  {
    myProgressCallback = callback;
    myDefaultIndentOption = options.myIndentOptions;
    mySettings = options.mySettings;
    myBlockIndentOptions = new BlockIndentOptions(mySettings, myDefaultIndentOption);
    myDocument = model.getDocument();
    myReformatContext = options.myReformatContext;
    myRightMargin = getRightMargin(block);
    
    final InitialInfoBuilder builder = prepareToBuildBlocksSequentially(block, model, options, mySettings, myDefaultIndentOption, myProgressCallback);
    final WrapBlocksState wrapState = new WrapBlocksState(builder);
    wrapState.setOnDone(new Runnable() {
      @Override
      public void run() {
        myInfos = builder.getBlockToInfoMap();
        myRootBlockWrapper = builder.getRootBlockWrapper();
        myFirstTokenBlock = builder.getFirstTokenBlock();
        myFirstTokenBlockRef.set(myFirstTokenBlock);
        myLastTokenBlock = builder.getLastTokenBlock();
        myCurrentBlock = myFirstTokenBlock;
        int lastBlockOffset = myLastTokenBlock.getEndOffset();
        myLastWhiteSpace = new WhiteSpace(lastBlockOffset, false);
        myLastWhiteSpace.append(Math.max(lastBlockOffset, builder.getEndOffset()), model, myDefaultIndentOption);
        myBlockMapperHelper = new BlockMapperHelper(myFirstTokenBlock, myLastTokenBlock);
        myDependentSpacingEngine = new DependentSpacingEngine(myBlockMapperHelper);
        myAlignmentsInsideRangesToModify = builder.getAlignmentsInsideRangeToModify();
        myAlignmentHelper = new AlignmentHelper(myDocument, builder.getBlocksToAlign(), myBlockIndentOptions);
        myIndentAdjuster = new IndentAdjuster(myBlockIndentOptions, myAlignmentHelper);
        myIndentAdjusterRef.set(myIndentAdjuster);
        myExpandableIndentsRef.set(builder.getExpandableIndentsBlocks());
        myWrapProcessor = new WrapProcessor(myBlockMapperHelper, myIndentAdjuster, myRightMargin);
      } 
    });
    myStateProcessor = new StateProcessor(wrapState);
  }
  
  public BlockMapperHelper getBlockMapperHelper() {
    return myBlockMapperHelper;
  }

  private int getRightMargin(Block rootBlock) {
    Language language = null;
    if (rootBlock instanceof ASTBlock) {
      ASTNode node = ((ASTBlock)rootBlock).getNode();
      if (node != null) {
        PsiElement psiElement = node.getPsi();
        if (psiElement.isValid()) {
          PsiFile psiFile = psiElement.getContainingFile();
          if (psiFile != null) {
            language = psiFile.getViewProvider().getBaseLanguage();
          }
        }
      }
    }
    return mySettings.getRightMargin(language);
  }

  public void format(FormattingModel model) {
    format(model, false);
  }

  /**
   * Asks current processor to perform formatting.
   * <p/>
   * There are two processing approaches at the moment:
   * <pre>
   * <ul>
   *   <li>perform formatting during the current method call;</li>
   *   <li>
   *     split the whole formatting process to the set of fine-grained tasks and execute them sequentially during
   *     subsequent {@link #iteration()} calls;
   *   </li>
   * </ul>
   * </pre>
   * <p/>
   * Here is rationale for the second approach - formatting may introduce changes to the underlying document and IntelliJ IDEA
   * is designed in a way that write access is allowed from EDT only. That means that every time we execute particular action
   * from EDT we have no chance of performing any other actions from EDT simultaneously (e.g. we may want to show progress bar
   * that reflects current formatting state but the progress bar can' bet updated if formatting is performed during a single long
   * method call). So, we can interleave formatting iterations with GUI state updates.
   *
   * @param model         target formatting model
   * @param sequentially  flag that indicates what kind of processing should be used
   */
  public void format(FormattingModel model, boolean sequentially) {
    if (sequentially) {
      myStateProcessor.setNextState(new AdjustWhiteSpacesState());
      myStateProcessor.setNextState(new ExpandChildrenIndent(myDocument, myIndentAdjusterRef, myExpandableIndentsRef));
      myStateProcessor.setNextState(new ApplyChangesState(myFirstTokenBlockRef, model, myBlockIndentOptions, myProgressCallback));
    }
    else {
      formatWithoutRealModifications(false);
      performModifications(model, false);
    }
  }

  /**
   * Asks current processor to perform processing iteration
   *
   * @return    <code>true</code> if the processing is finished; <code>false</code> otherwise
   * @see #format(FormattingModel, boolean)
   */
  public boolean iteration() {
    if (myStateProcessor.isDone()) {
      return true;
    }
    myStateProcessor.iteration();
    return myStateProcessor.isDone();
  }

  /**
   * Asks current processor to stop any active sequential processing if any.
   */
  public void stopSequentialProcessing() {
    myStateProcessor.stop();
  }

  public void formatWithoutRealModifications() {
    formatWithoutRealModifications(false);
  }

  @SuppressWarnings({"WhileLoopSpinsOnField"})
  public void formatWithoutRealModifications(boolean sequentially) {
    myStateProcessor.setNextState(new AdjustWhiteSpacesState());
    myStateProcessor.setNextState(new ExpandChildrenIndent(myDocument, myIndentAdjusterRef, myExpandableIndentsRef));
    if (sequentially) {
      return;
    }
    doIterationsSynchronously();
  }

  private void reset() {
    myAlignmentHelper.reset();
    myDependentSpacingEngine.clear();
    myWrapCandidate = null;
    if (myRootBlockWrapper != null) {
      myRootBlockWrapper.reset();
    }
  }

  public void performModifications(FormattingModel model) {
    performModifications(model, false);
  }

  public void performModifications(FormattingModel model, boolean sequentially) {
    assert !myDisposed;
    myStateProcessor.setNextState(new ApplyChangesState(myFirstTokenBlockRef, model, myBlockIndentOptions, myProgressCallback));

    if (sequentially) {
      return;
    }

    doIterationsSynchronously();
  }

  private void doIterationsSynchronously() {
    while (!myStateProcessor.isDone()) {
      myStateProcessor.iteration();
    }
  }

  private void processToken() {
    final SpacingImpl spaceProperty = myCurrentBlock.getSpaceProperty();
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    if (isReformatSelectedRangesContext()) {
      if (isCurrentBlockAlignmentUsedInRangesToModify() && whiteSpace.isReadOnly() && spaceProperty != null && !spaceProperty.isReadOnly()) {
        whiteSpace.setReadOnly(false);
        whiteSpace.setLineFeedsAreReadOnly(true);
      }
    }

    whiteSpace.arrangeLineFeeds(spaceProperty, myBlockMapperHelper);

    if (!whiteSpace.containsLineFeeds()) {
      whiteSpace.arrangeSpaces(spaceProperty);
    }

    try {
      LeafBlockWrapper newBlock = myWrapProcessor.processWrap(myCurrentBlock);
      if (newBlock != null) {
        myCurrentBlock = newBlock;
        return;
      }
    }
    finally {
      if (whiteSpace.containsLineFeeds()) {
        onCurrentLineChanged();
      }
    }

    LeafBlockWrapper newCurrentBlock = myIndentAdjuster.adjustIndent(myCurrentBlock);
    if (newCurrentBlock != null) {
      myCurrentBlock = newCurrentBlock;
      onCurrentLineChanged();
      return;
    }

    defineAlignOffset(myCurrentBlock);

    if (myCurrentBlock.containsLineFeeds()) {
      onCurrentLineChanged();
    }


    final List<TextRange> ranges = getDependentRegionRangesAfterCurrentWhiteSpace(spaceProperty, whiteSpace);
    if (!ranges.isEmpty()) {
      myDependentSpacingEngine.registerUnresolvedDependentSpacingRanges(spaceProperty, ranges);
    }

    if (!whiteSpace.isIsReadOnly() && myDependentSpacingEngine.shouldReformatPreviouslyLocatedDependentSpacing(whiteSpace)) {
      myAlignAgain.add(whiteSpace);
    }
    else if (!myAlignAgain.isEmpty()) {
      myAlignAgain.remove(whiteSpace);
    }

    myCurrentBlock = myCurrentBlock.getNextBlock();
  }

  private void onCurrentLineChanged() {
    myWrapCandidate = null;
  }

  private boolean isReformatSelectedRangesContext() {
    return myReformatContext && !ContainerUtil.isEmpty(myAlignmentsInsideRangesToModify);
  }

  private boolean isCurrentBlockAlignmentUsedInRangesToModify() {
    AbstractBlockWrapper block = myCurrentBlock;
    AlignmentImpl alignment = myCurrentBlock.getAlignment();

    while (alignment == null) {
      block = block.getParent();
      if (block == null || block.getStartOffset() != myCurrentBlock.getStartOffset()) {
        return false;
      }
      alignment = block.getAlignment();
    }

    return myAlignmentsInsideRangesToModify.contains(alignment);
  }

  private static List<TextRange> getDependentRegionRangesAfterCurrentWhiteSpace(final SpacingImpl spaceProperty,
                                                                                final WhiteSpace whiteSpace)
  {
    if (!(spaceProperty instanceof DependantSpacingImpl)) return ContainerUtil.emptyList();

    if (whiteSpace.isReadOnly() || whiteSpace.isLineFeedsAreReadOnly()) return ContainerUtil.emptyList();

    DependantSpacingImpl spacing = (DependantSpacingImpl)spaceProperty;
    return ContainerUtil.filter(spacing.getDependentRegionRanges(), new Condition<TextRange>() {
      @Override
      public boolean value(TextRange dependencyRange) {
        return whiteSpace.getStartOffset() < dependencyRange.getEndOffset();
      }
    });
  }

  
  private void defineAlignOffset(final LeafBlockWrapper block) {
    AbstractBlockWrapper current = myCurrentBlock;
    while (true) {
      final AlignmentImpl alignment = current.getAlignment();
      if (alignment != null) {
        alignment.setOffsetRespBlock(block);
      }
      current = current.getParent();
      if (current == null) return;
      if (current.getStartOffset() != myCurrentBlock.getStartOffset()) return;

    }
  }

  public void setAllWhiteSpacesAreReadOnly() {
    LeafBlockWrapper current = myFirstTokenBlock;
    while (current != null) {
      current.getWhiteSpace().setReadOnly(true);
      current = current.getNextBlock();
    }
  }

  public static class ChildAttributesInfo {
    public final AbstractBlockWrapper parent;
    public final ChildAttributes attributes;
    public final int index;

    public ChildAttributesInfo(final AbstractBlockWrapper parent, final ChildAttributes attributes, final int index) {
      this.parent = parent;
      this.attributes = attributes;
      this.index = index;
    }
  }

  public IndentInfo getIndentAt(final int offset) {
    processBlocksBefore(offset);
    AbstractBlockWrapper parent = getParentFor(offset, myCurrentBlock);
    if (parent == null) {
      final LeafBlockWrapper previousBlock = myCurrentBlock.getPreviousBlock();
      if (previousBlock != null) parent = getParentFor(offset, previousBlock);
      if (parent == null) return new IndentInfo(0, 0, 0);
    }
    int index = getNewChildPosition(parent, offset);
    final Block block = myInfos.get(parent);

    if (block == null) {
      return new IndentInfo(0, 0, 0);
    }

    ChildAttributesInfo info = getChildAttributesInfo(block, index, parent);
    if (info == null) {
      return new IndentInfo(0, 0, 0);
    }

    return myIndentAdjuster.adjustLineIndent(myCurrentBlock, info);
  }

  @Nullable
  private static ChildAttributesInfo getChildAttributesInfo(@NotNull final Block block,
                                                            final int index,
                                                            @Nullable AbstractBlockWrapper parent) {
    if (parent == null) {
      return null;
    }
    ChildAttributes childAttributes = block.getChildAttributes(index);

    if (childAttributes == ChildAttributes.DELEGATE_TO_PREV_CHILD) {
      final Block newBlock = block.getSubBlocks().get(index - 1);
      AbstractBlockWrapper prevWrappedBlock;
      if (parent instanceof CompositeBlockWrapper) {
        prevWrappedBlock = ((CompositeBlockWrapper)parent).getChildren().get(index - 1);
      }
      else {
        prevWrappedBlock = parent.getPreviousBlock();
      }
      return getChildAttributesInfo(newBlock, newBlock.getSubBlocks().size(), prevWrappedBlock);
    }

    else if (childAttributes == ChildAttributes.DELEGATE_TO_NEXT_CHILD) {
      AbstractBlockWrapper nextWrappedBlock;
      if (parent instanceof CompositeBlockWrapper) {
        List<AbstractBlockWrapper> children = ((CompositeBlockWrapper)parent).getChildren();
        if (children != null && index < children.size()) {
          nextWrappedBlock = children.get(index);
        }
        else {
          return null;
        }
      }
      else {
        nextWrappedBlock = ((LeafBlockWrapper)parent).getNextBlock();
      }
      return getChildAttributesInfo(block.getSubBlocks().get(index), 0, nextWrappedBlock);
    }

    else {
      return new ChildAttributesInfo(parent, childAttributes, index);
    }
  }
  
  private static int getNewChildPosition(final AbstractBlockWrapper parent, final int offset) {
    AbstractBlockWrapper parentBlockToUse = getLastNestedCompositeBlockForSameRange(parent);
    if (!(parentBlockToUse instanceof CompositeBlockWrapper)) return 0;
    final List<AbstractBlockWrapper> subBlocks = ((CompositeBlockWrapper)parentBlockToUse).getChildren();
    //noinspection ConstantConditions
    if (subBlocks != null) {
      for (int i = 0; i < subBlocks.size(); i++) {
        AbstractBlockWrapper block = subBlocks.get(i);
        if (block.getStartOffset() >= offset) return i;
      }
      return subBlocks.size();
    }
    else {
      return 0;
    }
  }

  @Nullable
  private static AbstractBlockWrapper getParentFor(final int offset, AbstractBlockWrapper block) {
    AbstractBlockWrapper current = block;
    while (current != null) {
      if (current.getStartOffset() < offset && current.getEndOffset() >= offset) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  @Nullable
  private AbstractBlockWrapper getParentFor(final int offset, LeafBlockWrapper block) {
    AbstractBlockWrapper previous = getPreviousIncompleteBlock(block, offset);
    if (previous != null) {
      return getLastNestedCompositeBlockForSameRange(previous);
    }
    else {
      return getParentFor(offset, (AbstractBlockWrapper)block);
    }
  }

  @Nullable
  private AbstractBlockWrapper getPreviousIncompleteBlock(final LeafBlockWrapper block, final int offset) {
    if (block == null) {
      if (myLastTokenBlock.isIncomplete()) {
        return myLastTokenBlock;
      }
      else {
        return null;
      }
    }

    AbstractBlockWrapper current = block;
    while (current.getParent() != null && current.getParent().getStartOffset() > offset) {
      current = current.getParent();
    }

    if (current.getParent() == null) return null;

    if (current.getEndOffset() <= offset) {
      while (!current.isIncomplete() &&
             current.getParent() != null &&
             current.getParent().getEndOffset() <= offset) {
        current = current.getParent();
      }
      if (current.isIncomplete()) return current;
    }

    if (current.getParent() == null) return null;

    final List<AbstractBlockWrapper> subBlocks = current.getParent().getChildren();
    final int index = subBlocks.indexOf(current);
    if (index < 0) {
      LOG.assertTrue(false);
    }
    if (index == 0) return null;

    AbstractBlockWrapper currentResult = subBlocks.get(index - 1);
    if (!currentResult.isIncomplete()) return null;

    AbstractBlockWrapper lastChild = getLastChildOf(currentResult);
    while (lastChild != null && lastChild.isIncomplete()) {
      currentResult = lastChild;
      lastChild = getLastChildOf(currentResult);
    }
    return currentResult;
  }

  @Nullable
  private static AbstractBlockWrapper getLastChildOf(final AbstractBlockWrapper currentResult) {
    AbstractBlockWrapper parentBlockToUse = getLastNestedCompositeBlockForSameRange(currentResult);
    if (!(parentBlockToUse instanceof CompositeBlockWrapper)) return null;
    final List<AbstractBlockWrapper> subBlocks = ((CompositeBlockWrapper)parentBlockToUse).getChildren();
    if (subBlocks.isEmpty()) return null;
    return subBlocks.get(subBlocks.size() - 1);
  }

  /**
   * There is a possible case that particular block is a composite block that contains number of nested composite blocks
   * that all target the same text range. This method allows to derive the most nested block that shares the same range (if any).
   *
   * @param block   block to check
   * @return        the most nested block of the given one that shares the same text range if any; given block otherwise
   */
  @NotNull
  private static AbstractBlockWrapper getLastNestedCompositeBlockForSameRange(@NotNull final AbstractBlockWrapper block) {
    if (!(block instanceof CompositeBlockWrapper)) {
      return block;
    }

    AbstractBlockWrapper result = block;
    AbstractBlockWrapper candidate = block;
    while (true) {
      List<AbstractBlockWrapper> subBlocks = ((CompositeBlockWrapper)candidate).getChildren();
      if (subBlocks == null || subBlocks.size() != 1) {
        break;
      }

      candidate = subBlocks.get(0);
      if (candidate.getStartOffset() == block.getStartOffset() && candidate.getEndOffset() == block.getEndOffset()
          && candidate instanceof CompositeBlockWrapper)
      {
        result = candidate;
      }
      else {
        break;
      }
    }
    return result;
  }

  private void processBlocksBefore(final int offset) {
    while (true) {
      myAlignAgain.clear();
      myCurrentBlock = myFirstTokenBlock;
      while (myCurrentBlock != null && myCurrentBlock.getStartOffset() < offset) {
        processToken();
        if (myCurrentBlock == null) {
          myCurrentBlock = myLastTokenBlock;
          if (myCurrentBlock != null) {
            myProgressCallback.afterProcessingBlock(myCurrentBlock);
          }
          break;
        }
      }
      if (myAlignAgain.isEmpty()) return;
      reset();
    }
  }

  public LeafBlockWrapper getFirstTokenBlock() {
    return myFirstTokenBlock;
  }

  public WhiteSpace getLastWhiteSpace() {
    return myLastWhiteSpace;
  }

  private class AdjustWhiteSpacesState extends State {
    
    @Override
    protected void doIteration() {
      LeafBlockWrapper blockToProcess = myCurrentBlock;
      processToken();
      if (blockToProcess != null) {
        myProgressCallback.afterProcessingBlock(blockToProcess);
      }

      if (myCurrentBlock != null) {
        return;
      }

      if (myAlignAgain.isEmpty()) {
        setDone(true);
      }
      else {
        myAlignAgain.clear();
        myDependentSpacingEngine.clear();
        myCurrentBlock = myFirstTokenBlock;
      }
    }
  }
  
  public static class FormatOptions {
    public CodeStyleSettings mySettings;
    public CommonCodeStyleSettings.IndentOptions myIndentOptions;

    public FormatTextRanges myAffectedRanges;
    public boolean myReformatContext;

    public int myInterestingOffset;

    public FormatOptions(CodeStyleSettings settings,
                         CommonCodeStyleSettings.IndentOptions options,
                         FormatTextRanges ranges,
                         boolean reformatContext) {
      this(settings, options, ranges, reformatContext, -1);
    }

    public FormatOptions(CodeStyleSettings settings,
                         CommonCodeStyleSettings.IndentOptions options,
                         FormatTextRanges ranges,
                         boolean reformatContext,
                         int interestingOffset) {
      mySettings = settings;
      myIndentOptions = options;
      myAffectedRanges = ranges;
      myReformatContext = reformatContext;
      myInterestingOffset = interestingOffset;
    }
  }
}
