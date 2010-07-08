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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class FormatProcessor {

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatProcessor");

  private LeafBlockWrapper myCurrentBlock;

  private Map<AbstractBlockWrapper, Block> myInfos;
  private CompositeBlockWrapper myRootBlockWrapper;
  private TIntObjectHashMap<LeafBlockWrapper> myTextRangeToWrapper;

  private final CodeStyleSettings.IndentOptions myIndentOption;
  private final CodeStyleSettings mySettings;

  /**
   * Remembers mappings between backward-shifted aligned block and blocks that cause that shift in order to detected
   * infinite cycles that may occur when, for example following alignment is specified:
   * <p/>
   * <pre>
   *     int i1     = 1;
   *     int i2, i3 = 2;
   * </pre>
   * <p/>
   * There is a possible case that <code>'i1'</code>, <code>'i2'</code> and <code>'i3'</code> blocks re-use
   * the same alignment, hence, <code>'i1'</code> is shifted to right during <code>'i3'</code> processing but
   * that causes <code>'i2'</code> to be shifted right as wll because it's aligned to <code>'i1'</code> that
   * increases offset of <code>'i3'</code> that, in turn, causes backward shift of <code>'i1'</code> etc.
   * <p/>
   * This map remembers such backward shifts in order to be able to break such infinite cycles.
   */
  private final Map<LeafBlockWrapper, Set<LeafBlockWrapper>> myBackwardShiftedAlignedBlocks
    = new HashMap<LeafBlockWrapper, Set<LeafBlockWrapper>>();

  private LeafBlockWrapper myWrapCandidate = null;
  private LeafBlockWrapper myFirstWrappedBlockOnLine = null;

  private LeafBlockWrapper myFirstTokenBlock;
  private LeafBlockWrapper myLastTokenBlock;

  private SortedMap<TextRange,Pair<AbstractBlockWrapper, Boolean>> myPreviousDependencies =
    new TreeMap<TextRange, Pair<AbstractBlockWrapper, Boolean>>(new Comparator<TextRange>() {
      public int compare(final TextRange o1, final TextRange o2) {
        int offsetsDelta = o1.getEndOffset() - o2.getEndOffset();

        if (offsetsDelta == 0) {
          offsetsDelta = o2.getStartOffset() - o1.getStartOffset();     // starting earlier is greater
        }
        return offsetsDelta;
      }
    });

  private final HashSet<WhiteSpace> myAlignAgain = new HashSet<WhiteSpace>();
  private WhiteSpace myLastWhiteSpace;
  private boolean myDisposed;
  private CodeStyleSettings.IndentOptions myJavaIndentOptions;

  public FormatProcessor(final FormattingDocumentModel docModel,
                         Block rootBlock,
                         CodeStyleSettings settings,
                         CodeStyleSettings.IndentOptions indentOptions,
                         @Nullable FormatTextRanges affectedRanges) {
    this(docModel, rootBlock, settings, indentOptions, affectedRanges, -1);
  }

  public FormatProcessor(final FormattingDocumentModel docModel,
                         Block rootBlock,
                         CodeStyleSettings settings,
                         CodeStyleSettings.IndentOptions indentOptions,
                         @Nullable FormatTextRanges affectedRanges,
                         int interestingOffset) {
    myIndentOption = indentOptions;
    mySettings = settings;
    final InitialInfoBuilder builder = InitialInfoBuilder.buildBlocks(rootBlock,
                                                                      docModel,
                                                                      affectedRanges,
                                                                      indentOptions,
                                                                      interestingOffset);
    myInfos = builder.getBlockToInfoMap();
    myRootBlockWrapper = builder.getRootBlockWrapper();
    myFirstTokenBlock = builder.getFirstTokenBlock();
    myLastTokenBlock = builder.getLastTokenBlock();
    myCurrentBlock = myFirstTokenBlock;
    myTextRangeToWrapper = buildTextRangeToInfoMap(myFirstTokenBlock);
    myLastWhiteSpace = new WhiteSpace(getLastBlock().getEndOffset(), false);
    myLastWhiteSpace.append(docModel.getTextLength(), docModel, indentOptions);
  }

  private LeafBlockWrapper getLastBlock() {
    LeafBlockWrapper result = myFirstTokenBlock;
    while (result.getNextBlock() != null) {
      result = result.getNextBlock();
    }
    return result;
  }

  private static TIntObjectHashMap<LeafBlockWrapper> buildTextRangeToInfoMap(final LeafBlockWrapper first) {
    final TIntObjectHashMap<LeafBlockWrapper> result = new TIntObjectHashMap<LeafBlockWrapper>();
    LeafBlockWrapper current = first;
    while (current != null) {
      result.put(current.getStartOffset(), current);
      current = current.getNextBlock();
    }
    return result;
  }

  public void format(FormattingModel model) {
    formatWithoutRealModifications();
    performModifications(model);
  }

  @SuppressWarnings({"WhileLoopSpinsOnField"})
  public void formatWithoutRealModifications() {
    while (true) {
      myAlignAgain.clear();
      myCurrentBlock = myFirstTokenBlock;
      while (myCurrentBlock != null) {
        processToken();
      }
      if (myAlignAgain.isEmpty()) return;
      reset();
    }
  }

  private void reset() {
    myBackwardShiftedAlignedBlocks.clear();
    myPreviousDependencies.clear();
    myWrapCandidate = null;
    if (myRootBlockWrapper != null) {
      myRootBlockWrapper.reset();
    }
  }

  public void performModifications(FormattingModel model) {
    assert !myDisposed;
    final List<LeafBlockWrapper> blocksToModify = collectBlocksToModify();

    // call doModifications static method to ensure no access to state
    // thus we may clear formatting state
    reset();

    myInfos = null;
    myRootBlockWrapper = null;
    myTextRangeToWrapper = null;
    myPreviousDependencies = null;
    myLastWhiteSpace = null;
    myFirstTokenBlock = null;
    myLastTokenBlock = null;
    myDisposed = true;

    //for GeneralCodeFormatterTest
    if (myJavaIndentOptions == null) {
      myJavaIndentOptions = mySettings.getIndentOptions(StdFileTypes.JAVA);
    }

    doModify(blocksToModify, model,myIndentOption, myJavaIndentOptions);
  }

  public void setJavaIndentOptions(final CodeStyleSettings.IndentOptions javaIndentOptions) {
    myJavaIndentOptions = javaIndentOptions;
  }

  private static void doModify(final List<LeafBlockWrapper> blocksToModify, final FormattingModel model,
                               CodeStyleSettings.IndentOptions indentOption, CodeStyleSettings.IndentOptions javaOptions) {
    final int blocksToModifyCount = blocksToModify.size();
    final boolean bulkReformat = blocksToModifyCount > 50;
    final DocumentEx updatedDocument = bulkReformat ? getAffectedDocument(model) : null;
    if(updatedDocument != null) {
      updatedDocument.setInBulkUpdate(true);
    }
    try {
      int shift = 0;
      for (int i = 0; i < blocksToModifyCount; ++i) {
        final LeafBlockWrapper block = blocksToModify.get(i);
        shift = replaceWhiteSpace(model, block, shift, block.getWhiteSpace().generateWhiteSpace(indentOption),javaOptions);

        // block could be gc'd
        block.getParent().dispose();
        block.dispose();
        blocksToModify.set(i, null);
      }
    }
    finally {
      if (updatedDocument != null) {
        updatedDocument.setInBulkUpdate(false);
      }
      model.commitChanges();
    }
  }

  @Nullable
  private static DocumentEx getAffectedDocument(final FormattingModel model) {
    if (model instanceof DocumentBasedFormattingModel) {
      final Document document = ((DocumentBasedFormattingModel)model).getDocument();
      if (document instanceof DocumentEx) return (DocumentEx)document; 
    }/* else if (false) { // till issue with persistent range markers dropped fixed
      Document document = model.getDocumentModel().getDocument();
      if (document instanceof DocumentEx) return (DocumentEx)document;
    }*/
    return null;
  }

  private static int replaceWhiteSpace(final FormattingModel model,
                                  @NotNull final LeafBlockWrapper block,
                                  int shift,
                                  final CharSequence _newWhiteSpace,
                                  final CodeStyleSettings.IndentOptions options
                                  ) {
    final WhiteSpace whiteSpace = block.getWhiteSpace();
    final TextRange textRange = whiteSpace.getTextRange();
    final TextRange wsRange = shiftRange(textRange, shift);
    final String newWhiteSpace = _newWhiteSpace.toString();
    TextRange newWhiteSpaceRange = model.replaceWhiteSpace(wsRange, newWhiteSpace);

    shift += newWhiteSpaceRange.getLength() - textRange.getLength();

    if (block.isLeaf() && whiteSpace.containsLineFeeds() && block.containsLineFeeds()) {
      final TextRange currentBlockRange = shiftRange(block.getTextRange(), shift);

      IndentInside lastLineIndent = block.getLastLineIndent();
      IndentInside whiteSpaceIndent = IndentInside.createIndentOn(IndentInside.getLastLine(newWhiteSpace));
      final int shiftInside = calcShift(lastLineIndent, whiteSpaceIndent, options);

      final TextRange newBlockRange = model.shiftIndentInsideRange(currentBlockRange, shiftInside);
      shift += newBlockRange.getLength() - block.getLength();
    }
    return shift;
  }

  private List<LeafBlockWrapper> collectBlocksToModify() {
    List<LeafBlockWrapper> blocksToModify = new ArrayList<LeafBlockWrapper>();

    for (LeafBlockWrapper block = myFirstTokenBlock; block != null; block = block.getNextBlock()) {
      final WhiteSpace whiteSpace = block.getWhiteSpace();
      if (!whiteSpace.isReadOnly()) {
        final String newWhiteSpace = whiteSpace.generateWhiteSpace(myIndentOption);
        if (!whiteSpace.equalsToString(newWhiteSpace)) {
          blocksToModify.add(block);
        }
      }
    }
    return blocksToModify;
  }

  private static TextRange shiftRange(final TextRange textRange, final int shift) {
    return new TextRange(textRange.getStartOffset() + shift, textRange.getEndOffset() + shift);
  }

  private void processToken() {
    final SpacingImpl spaceProperty = myCurrentBlock.getSpaceProperty();
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    whiteSpace.arrangeLineFeeds(spaceProperty, this);

    if (!whiteSpace.containsLineFeeds()) {
      whiteSpace.arrangeSpaces(spaceProperty);
    }

    try {
      if (processWrap(spaceProperty)) {
        return;
      }
    }
    finally {
      if (whiteSpace.containsLineFeeds()) {
        onCurrentLineChanged();
      }
    }

    if (!adjustIndent()) {
       return;
    }

    defineAlignOffset(myCurrentBlock);

    if (myCurrentBlock.containsLineFeeds()) {
      onCurrentLineChanged();
    }

    if (shouldSaveDependency(spaceProperty, whiteSpace)) {
      saveDependency(spaceProperty);
    }

    if (!whiteSpace.isIsReadOnly() && shouldReformatBecauseOfBackwardDependency(whiteSpace.getTextRange())) {
      myAlignAgain.add(whiteSpace);
    }
    else if (!myAlignAgain.isEmpty()) {
      myAlignAgain.remove(whiteSpace);
    }

    myCurrentBlock = myCurrentBlock.getNextBlock();
  }

  private boolean shouldReformatBecauseOfBackwardDependency(TextRange changed) {
    final SortedMap<TextRange, Pair<AbstractBlockWrapper, Boolean>> sortedHeadMap = myPreviousDependencies.tailMap(changed);

    for (final Map.Entry<TextRange, Pair<AbstractBlockWrapper, Boolean>> entry : sortedHeadMap.entrySet()) {
      final TextRange textRange = entry.getKey();

      if (textRange.contains(changed)) {
        final Pair<AbstractBlockWrapper, Boolean> pair = entry.getValue();
        final boolean containedLineFeeds = pair.getSecond().booleanValue();
        final boolean containsLineFeeds = containsLineFeeds(textRange);

        if (containedLineFeeds != containsLineFeeds) {
          return true;
        }
      }
    }
    return false;
  }

  private void saveDependency(final SpacingImpl spaceProperty) {
    final DependantSpacingImpl dependantSpaceProperty = (DependantSpacingImpl)spaceProperty;
    final TextRange dependency = dependantSpaceProperty.getDependency();
    if (dependantSpaceProperty.wasLFUsed()) {
      myPreviousDependencies.put(dependency,new Pair<AbstractBlockWrapper, Boolean>(myCurrentBlock, Boolean.TRUE));
    }
    else {
      final boolean value = containsLineFeeds(dependency);
      if (value) {
        dependantSpaceProperty.setLFWasUsed(true);
      }
      myPreviousDependencies.put(dependency, new Pair<AbstractBlockWrapper, Boolean>(myCurrentBlock, value));
    }
  }

  private static boolean shouldSaveDependency(final SpacingImpl spaceProperty, WhiteSpace whiteSpace) {
    if (!(spaceProperty instanceof DependantSpacingImpl)) return false;

    if (whiteSpace.isReadOnly() || whiteSpace.isLineFeedsAreReadOnly()) return false;

    final TextRange dependency = ((DependantSpacingImpl)spaceProperty).getDependency();
    return whiteSpace.getStartOffset() < dependency.getEndOffset();
  }

  private boolean processWrap(SpacingImpl spacing) {
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    boolean wrapWasPresent = whiteSpace.containsLineFeeds();

    if (wrapWasPresent) {
      myFirstWrappedBlockOnLine = null;
    }

    if (whiteSpace.containsLineFeeds() && !whiteSpace.containsLineFeedsInitially()) {
      whiteSpace.removeLineFeeds(spacing, this);
    }

    boolean wrapIsPresent = whiteSpace.containsLineFeeds();

    final ArrayList<WrapImpl> wraps = myCurrentBlock.getWraps();
    final int wrapsCount = wraps.size();

    for (int i = 0; i < wrapsCount; ++i) {
      wraps.get(i).processNextEntry(myCurrentBlock.getStartOffset());
    }

    final WrapImpl wrap = getWrapToBeUsed(wraps);

    if (wrap != null || wrapIsPresent) {
      if (!wrapIsPresent && !canReplaceWrapCandidate(wrap)) {
        myCurrentBlock = myWrapCandidate;
        return true;
      }
      if (wrap != null && wrap.getFirstEntry() != null) {
        //myCurrentBlock = wrap.getFirstEntry();
        myCurrentBlock = getFirstBlockOnNewLine();
        wrap.markAsUsed();
        return true;
      }
      if (wrap != null && wrapCanBeUsedInTheFuture(wrap)) {
        wrap.markAsUsed();
      }

      if (!whiteSpace.containsLineFeeds()) {
        whiteSpace.ensureLineFeed();
        if (!wrapWasPresent && wrap != null) {
          if (myFirstWrappedBlockOnLine != null && wrap.isChildOf(myFirstWrappedBlockOnLine.getWrap(), myCurrentBlock)) {
            wrap.ignoreParentWrap(myFirstWrappedBlockOnLine.getWrap(), myCurrentBlock);
            myCurrentBlock = myFirstWrappedBlockOnLine;
            return true;
          }
          else {
            myFirstWrappedBlockOnLine = myCurrentBlock;
          }
        }
      }

      myWrapCandidate = null;
    }
    else {
      for (int i = 0; i < wrapsCount; ++i) {
        final WrapImpl wrap1 = wraps.get(i);
        if (isCandidateToBeWrapped(wrap1) && canReplaceWrapCandidate(wrap1)) {
          myWrapCandidate = myCurrentBlock;
        }
        if (wrapCanBeUsedInTheFuture(wrap1)) {
          wrap1.saveFirstEntry(myCurrentBlock);
        }
      }
    }

    if (!whiteSpace.containsLineFeeds() && myWrapCandidate != null && !whiteSpace.isReadOnly() && lineOver()) {
      myCurrentBlock = myWrapCandidate;
      return true;
    }

    return false;
  }

  @Nullable
  private LeafBlockWrapper getFirstBlockOnNewLine() {
    LeafBlockWrapper current = myCurrentBlock;
    while (current != null) {
      WhiteSpace whiteSpace = current.getWhiteSpace();
      if (whiteSpace.containsLineFeeds() && whiteSpace.containsLineFeedsInitially()) return current;
      if (current.getPreviousBlock() == null) return current;
      current = current.getPreviousBlock();
    }
    return null;
  }

  /**
   * Allows to answer if wrap of the {@link #myWrapCandidate} object (if any) may be replaced by the given wrap.
   *
   * @param wrap    wrap candidate to check
   * @return        <code>true</code> if wrap of the {@link #myWrapCandidate} object (if any) may be replaced by the given wrap;
   *                <code>false</code> otherwise
   */
  private boolean canReplaceWrapCandidate(WrapImpl wrap) {
    if (myWrapCandidate == null) return true;
    WrapImpl.Type type = wrap.getType();
    if (wrap.isIsActive() && (type == WrapImpl.Type.CHOP_IF_NEEDED || type == WrapImpl.Type.WRAP_ALWAYS)) return true;
    final WrapImpl currentWrap = myWrapCandidate.getWrap();
    return wrap == currentWrap || !wrap.isChildOf(currentWrap, myCurrentBlock);
  }

  private boolean isCandidateToBeWrapped(final WrapImpl wrap) {
    return isSuitableInTheCurrentPosition(wrap) &&
           (wrap.getType() == WrapImpl.Type.WRAP_AS_NEEDED || wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED) &&
           !myCurrentBlock.getWhiteSpace().isReadOnly();
  }

  private void onCurrentLineChanged() {
    myWrapCandidate = null;
  }

  /**
   * Adjusts indent of the current block.
   *
   * @return    <code>true</code> if current formatting iteration should be continued;
   *            <code>false</code> otherwise (e.g. if previously processed block is shifted inside this method for example
   *            because of specified alignment options)
   */
  private boolean adjustIndent() {
    IndentData alignOffset = getAlignOffset();
    WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    if (alignOffset == null) {
      if (whiteSpace.containsLineFeeds()) {
        adjustSpacingByIndentOffset();
      }
      else {
        whiteSpace.arrangeSpaces(myCurrentBlock.getSpaceProperty());
      }
      return true;
    }

    if (whiteSpace.containsLineFeeds()) {
      whiteSpace.setSpaces(alignOffset.getSpaces(), alignOffset.getIndentSpaces());
      return true;
    }

    IndentData indentBeforeBlock = myCurrentBlock.getNumberOfSymbolsBeforeBlock();
    int diff = alignOffset.getTotalSpaces() - indentBeforeBlock.getTotalSpaces();
    if (diff == 0) {
      return true;
    }

    if (diff > 0) {
      whiteSpace.setSpaces(whiteSpace.getSpaces(), whiteSpace.getIndentSpaces() + diff);
      return true;
    }
    AlignmentImpl alignment = myCurrentBlock.getAlignmentAtStartOffset();
    if (alignment == null) {
      // Never expect to be here.
      return true;
    }

    if (!alignment.isAllowBackwardShift()) {
      return true;
    }

    LeafBlockWrapper offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(myCurrentBlock);
    if (offsetResponsibleBlock == null) {
      return true;
    }

    // There is a possible case that alignment options are defined incorrectly. Consider the following example:
    //     int i1;
    //     int i2, i3;
    // There is a problem if all blocks above use the same alignment - block 'i1' is shifted to right in order to align
    // to block 'i3' and reformatting starts back after 'i1'. Now 'i2' is shifted to left as well in order to align to the
    // new 'i1' position. That changes 'i3' position as well that causes 'i1' to be shifted right one more time.
    // Hence, we have endless cycle here. We remember information about blocks that caused indentation change because of
    // alignment of blocks located before them and post error every time we detect endless cycle.
    Set<LeafBlockWrapper> blocksCausedRealignment = myBackwardShiftedAlignedBlocks.get(offsetResponsibleBlock);
    if (blocksCausedRealignment != null && blocksCausedRealignment.contains(myCurrentBlock)) {
      LOG.error(String.format("Formatting error - code block %s is set to be shifted right because of its alignment with "
                              + "block %s more than once. I.e. moving the former block because of alignment algo causes "
                              + "subsequent block to be shifted right as well - cyclic dependency",
                              offsetResponsibleBlock.getTextRange(), myCurrentBlock.getTextRange()));
      return true;
    }
    myBackwardShiftedAlignedBlocks.clear();
    myBackwardShiftedAlignedBlocks.put(offsetResponsibleBlock, blocksCausedRealignment = new HashSet<LeafBlockWrapper>());
    blocksCausedRealignment.add(myCurrentBlock);

    WhiteSpace previousWhiteSpace = offsetResponsibleBlock.getWhiteSpace();
    previousWhiteSpace.setSpaces(previousWhiteSpace.getSpaces(), previousWhiteSpace.getIndentOffset() - diff);

    myCurrentBlock = offsetResponsibleBlock.getNextBlock();
    onCurrentLineChanged();
    return false;
  }

  /**
   * Applies indent to the white space of {@link #myCurrentBlock currently processed wrapped block}. Both indentation
   * and alignment options are took into consideration here.
   */
  private void adjustLineIndent() {
    IndentData alignOffset = getAlignOffset();

    if (alignOffset == null) {
      adjustSpacingByIndentOffset();
    }
    else {
      myCurrentBlock.getWhiteSpace().setSpaces(alignOffset.getSpaces(), alignOffset.getIndentSpaces());
    }
  }

  private void adjustSpacingByIndentOffset() {
    IndentData offset = myCurrentBlock.calculateOffset(myIndentOption);
    myCurrentBlock.getWhiteSpace().setSpaces(offset.getSpaces(), offset.getIndentSpaces());
  }

  /**
   * Tries to find the closest block that starts before the {@link #myCurrentBlock currently processed block} and contains line feeds.
   *
   * @return    closest block to the currently processed block that contains line feeds if any; <code>null</code> otherwise
   */
  @Nullable
  private AbstractBlockWrapper getPreviousIndentedBlock() {
    AbstractBlockWrapper current = myCurrentBlock.getParent();
    while (current != null) {
      if (current.getStartOffset() != myCurrentBlock.getStartOffset() && current.getWhiteSpace().containsLineFeeds()) return current;
      if (current.getParent() != null) {
        AbstractBlockWrapper prevIndented = current.getParent().getPrevIndentedSibling(current);
        if (prevIndented != null) return prevIndented;

      }
      current = current.getParent();
    }
    return null;
  }

  private boolean wrapCanBeUsedInTheFuture(final WrapImpl wrap) {
    return wrap != null && wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED && isSuitableInTheCurrentPosition(wrap);
  }

  private boolean isSuitableInTheCurrentPosition(final WrapImpl wrap) {
    if (wrap.getFirstPosition() < myCurrentBlock.getStartOffset()) {
      return true;
    }

    if (wrap.isWrapFirstElement()) {
      return true;
    }

    if (wrap.getType() == WrapImpl.Type.WRAP_AS_NEEDED) {
      return positionAfterWrappingIsSuitable();
    }

    return wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED && lineOver() && positionAfterWrappingIsSuitable();
  }

  /**
   * Ensures that offset of the {@link #myCurrentBlock currently processed block} is not increased if we make a wrap on it.
   *
   * @return    <code>true</code> if it's ok to wrap at the currently processed block; <code>false</code> otherwise
   */
  private boolean positionAfterWrappingIsSuitable() {
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
    if (whiteSpace.containsLineFeeds()) return true;
    final int spaces = whiteSpace.getSpaces();
    int indentSpaces = whiteSpace.getIndentSpaces();
    try {
      final int offsetBefore = getOffsetBefore(myCurrentBlock);
      whiteSpace.ensureLineFeed();
      adjustLineIndent();
      final int offsetAfter = getOffsetBefore(myCurrentBlock);
      return offsetBefore > offsetAfter;
    }
    finally {
      whiteSpace.removeLineFeeds(myCurrentBlock.getSpaceProperty(), this);
      whiteSpace.setSpaces(spaces, indentSpaces);
    }
  }

  @Nullable
  private WrapImpl getWrapToBeUsed(final ArrayList<WrapImpl> wraps) {
    if (wraps.isEmpty()) {
      return null;
    }
    if (myWrapCandidate == myCurrentBlock) return wraps.get(0);

    final int wrapsCount = wraps.size();
    for (int i = 0; i < wrapsCount; ++i) {
      final WrapImpl wrap = wraps.get(i);
      if (!isSuitableInTheCurrentPosition(wrap)) continue;
      if (wrap.isIsActive()) return wrap;

      final WrapImpl.Type type = wrap.getType();
      if (type == WrapImpl.Type.WRAP_ALWAYS) return wrap;
      if (type == WrapImpl.Type.WRAP_AS_NEEDED || type == WrapImpl.Type.CHOP_IF_NEEDED) {
        if (lineOver()) {
          return wrap;
        }
      }
    }
    return null;
  }

  /**
   * @return    <code>true</code> if {@link #myCurrentBlock currently processed wrapped block} doesn't contain line feeds and
   *            exceeds right margin; <code>false</code> otherwise
   */
  private boolean lineOver() {
    return !myCurrentBlock.containsLineFeeds() &&
           getOffsetBefore(myCurrentBlock) + myCurrentBlock.getLength() > mySettings.RIGHT_MARGIN;
  }

  /**
   * Calculates number of non-line feed symbols before the given wrapped block.
   * <p/>
   * <b>Example:</b>
   * <pre>
   *      whitespace<sub>11</sub> block<sub>11</sub> whitespace<sub>12</sub> block<sub>12</sub>
   *      whitespace<sub>21</sub> block<sub>21</sub> whitespace<sub>22</sub> block<sub>22</sub>
   * </pre>
   * <p/>
   * Suppose this method is called with the wrapped <code>'block<sub>22</sub>'</code> and <code>'whitespace<sub>21</sub>'</code>
   * contains line feeds but <code>'whitespace<sub>22</sub>'</code> is not. This method returns number of symbols
   * from <code>'whitespace<sub>21</sub>'</code> after its last line feed symbol plus number of symbols at
   * <code>block<sub>21</sub></code> plus number of symbols at <code>whitespace<sub>22</sub></code>.
   *
   * @param info    target wrapped block to be used at a boundary during counting non-line feed symbols to the left of it
   * @return        non-line feed symbols to the left of the given wrapped block
   */
  private static int getOffsetBefore(LeafBlockWrapper info) {
    if (info != null) {
      int result = 0;
      while (true) {
        final WhiteSpace whiteSpace = info.getWhiteSpace();
        result += whiteSpace.getTotalSpaces();
        if (whiteSpace.containsLineFeeds()) {
          return result;
        }
        info = info.getPreviousBlock();
        if (info == null) return result;
        result += info.getSymbolsAtTheLastLine();
        if (info.containsLineFeeds()) return result;
      }
    }
    else {
      return -1;
    }
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

  /**
   * Tries to get align-implied indent of the current block.
   *
   * @return    indent of the current block if any; <code>null</code> otherwise
   */
  @Nullable
  private IndentData getAlignOffset() {
    AbstractBlockWrapper current = myCurrentBlock;
    while (true) {
      final AlignmentImpl alignment = current.getAlignment();
      LeafBlockWrapper offsetResponsibleBlock;
      if (alignment != null && (offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(myCurrentBlock)) != null) {
        final WhiteSpace whiteSpace = offsetResponsibleBlock.getWhiteSpace();
        if (whiteSpace.containsLineFeeds()) {
          return new IndentData(whiteSpace.getIndentSpaces(), whiteSpace.getSpaces());
        }
        else {
          final int offsetBeforeBlock = getOffsetBefore(offsetResponsibleBlock);
          final AbstractBlockWrapper prevIndentedBlock = getPreviousIndentedBlock();
          if (prevIndentedBlock == null) {
            return new IndentData(0, offsetBeforeBlock);
          }
          else {
            final int parentIndent = prevIndentedBlock.getWhiteSpace().getIndentOffset();
            if (parentIndent > offsetBeforeBlock) {
              return new IndentData(0, offsetBeforeBlock);
            }
            else {
              return new IndentData(parentIndent, offsetBeforeBlock - parentIndent);
            }
          }
        }

      }
      else {
        current = current.getParent();
        if (current == null) return null;
        if (current.getStartOffset() != myCurrentBlock.getStartOffset()) return null;
      }
    }
  }

  public boolean containsLineFeeds(final TextRange dependency) {
    LeafBlockWrapper child = myTextRangeToWrapper.get(dependency.getStartOffset());
    if (child == null) return false;
    if (child.containsLineFeeds()) return true;
    final int endOffset = dependency.getEndOffset();
    while (child.getEndOffset() < endOffset) {
      child = child.getNextBlock();
      if (child == null) return false;
      if (child.getWhiteSpace().containsLineFeeds()) return true;
      if (child.containsLineFeeds()) return true;
    }
    return false;
  }

  @Nullable
  public LeafBlockWrapper getBlockAfter(final int startOffset) {
    int current = startOffset;
    LeafBlockWrapper result = null;
    while (current < myLastWhiteSpace.getStartOffset()) {
      final LeafBlockWrapper currentValue = myTextRangeToWrapper.get(current);
      if (currentValue != null) {
        result = currentValue;
        break;
      }
      current++;
    }

    LeafBlockWrapper prevBlock = getPrevBlock(result);

    if (prevBlock != null && prevBlock.contains(startOffset)) {
      return prevBlock;
    }
    else {
      return result;
    }
  }

  @Nullable
  private LeafBlockWrapper getPrevBlock(final LeafBlockWrapper result) {
    if (result != null) {
      return result.getPreviousBlock();
    }
    else {
      return myLastTokenBlock;
    }
  }

  public void setAllWhiteSpacesAreReadOnly() {
    LeafBlockWrapper current = myFirstTokenBlock;
    while (current != null) {
      current.getWhiteSpace().setReadOnly(true);
      current = current.getNextBlock();
    }
  }

  static class ChildAttributesInfo {
    public final AbstractBlockWrapper parent;
    final ChildAttributes attributes;
    final int index;

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
      if (parent == null) return new IndentInfo(0,0,0);
    }
    int index = getNewChildPosition(parent, offset);
    final Block block = myInfos.get(parent);
    
    if (block == null) {
      return new IndentInfo(0, 0, 0);
    }

    ChildAttributesInfo info = getChildAttributesInfo(block, index, parent);

    return adjustLineIndent(info.parent, info.attributes, info.index);
  }

  private static ChildAttributesInfo getChildAttributesInfo(final Block block, final int index, AbstractBlockWrapper parent) {
    ChildAttributes childAttributes = block.getChildAttributes(index);

    if (childAttributes == ChildAttributes.DELEGATE_TO_PREV_CHILD) {
      final Block newBlock = block.getSubBlocks().get(index - 1);
      return getChildAttributesInfo(newBlock, newBlock.getSubBlocks().size(), ((CompositeBlockWrapper)parent).getChildren().get(index - 1));

    }

    else if (childAttributes == ChildAttributes.DELEGATE_TO_NEXT_CHILD) {
      return getChildAttributesInfo(block.getSubBlocks().get(index), 0, ((CompositeBlockWrapper)parent).getChildren().get(index));
    }

    else {
      return new ChildAttributesInfo(parent, childAttributes, index);
    }

  }

  private IndentInfo adjustLineIndent(final AbstractBlockWrapper parent, final ChildAttributes childAttributes, final int index) {
    int alignOffset = getAlignOffsetBefore(childAttributes.getAlignment(), null);
    if (alignOffset == -1) {
      return parent.calculateChildOffset(myIndentOption, childAttributes, index).createIndentInfo();
    }
    else {
      AbstractBlockWrapper previousIndentedBlock = getPreviousIndentedBlock();
      if (previousIndentedBlock == null) {
        return new IndentInfo(0, 0, alignOffset);
      }
      else {
        int indentOffset = previousIndentedBlock.getWhiteSpace().getIndentOffset();
        if (indentOffset > alignOffset) {
          return new IndentInfo(0, 0, alignOffset);
        }
        else {
          return new IndentInfo(0, indentOffset, alignOffset - indentOffset);
        }
      }
    }
  }

  private static int getAlignOffsetBefore(final Alignment alignment, @Nullable final LeafBlockWrapper blockAfter) {
    if (alignment == null) return -1;
    final LeafBlockWrapper alignRespBlock = ((AlignmentImpl)alignment).getOffsetRespBlockBefore(blockAfter);
    if (alignRespBlock != null) {
      return getOffsetBefore(alignRespBlock);
    }
    else {
      return -1;
    }
  }

  private static int getNewChildPosition(final AbstractBlockWrapper parent, final int offset) {
    if (!(parent instanceof CompositeBlockWrapper)) return 0;
    final List<AbstractBlockWrapper> subBlocks = ((CompositeBlockWrapper)parent).getChildren();
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
      return previous;
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
    if (!(currentResult instanceof CompositeBlockWrapper)) return null;
    final List<AbstractBlockWrapper> subBlocks = ((CompositeBlockWrapper)currentResult).getChildren();
    if (subBlocks.isEmpty()) return null;
    return subBlocks.get(subBlocks.size() - 1);
  }

  private void processBlocksBefore(final int offset) {
    while (true) {
      myAlignAgain.clear();
      myCurrentBlock = myFirstTokenBlock;
      while (myCurrentBlock != null && myCurrentBlock.getStartOffset() < offset) {
        processToken();
        if (myCurrentBlock == null) {
          myCurrentBlock = myLastTokenBlock;
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

  private static int calcShift(final IndentInside lastLineIndent, final IndentInside whiteSpaceIndent,
                               final CodeStyleSettings.IndentOptions options
                               ) {
    if (lastLineIndent.equals(whiteSpaceIndent)) return 0;
    if (options.USE_TAB_CHARACTER) {
      if (lastLineIndent.whiteSpaces > 0) {
        return whiteSpaceIndent.getSpacesCount(options);
      }
      else {
        return whiteSpaceIndent.tabs - lastLineIndent.tabs;
      }
    }
    else {
      if (lastLineIndent.tabs > 0) {
        return whiteSpaceIndent.getTabsCount(options);
      }
      else {
        return whiteSpaceIndent.whiteSpaces - lastLineIndent.whiteSpaces;
      }
    }
  }


}
