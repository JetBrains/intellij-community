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

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.TextChange;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.BulkChangesMerger;
import com.intellij.openapi.editor.impl.TextChangeImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class FormatProcessor {

  private static final Map<Alignment.Anchor, BlockAlignmentProcessor> ALIGNMENT_PROCESSORS =
    new EnumMap<Alignment.Anchor, BlockAlignmentProcessor>(Alignment.Anchor.class);
  static {
    ALIGNMENT_PROCESSORS.put(Alignment.Anchor.LEFT, new LeftEdgeAlignmentProcessor());
    ALIGNMENT_PROCESSORS.put(Alignment.Anchor.RIGHT, new RightEdgeAlignmentProcessor());
  }

  /**
   * There is a possible case that formatting introduced big number of changes to the underlying document. That number may be
   * big enough for that their subsequent appliance is much slower than direct replacing of the whole document text.
   * <p/>
   * Current constant holds minimum number of changes that should trigger such <code>'replace whole text'</code> optimization.
   */
  private static final int BULK_REPLACE_OPTIMIZATION_CRITERIA = 3000;

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatProcessor");

  private LeafBlockWrapper myCurrentBlock;

  private Map<AbstractBlockWrapper, Block>    myInfos;
  private CompositeBlockWrapper               myRootBlockWrapper;
  private TIntObjectHashMap<LeafBlockWrapper> myTextRangeToWrapper;

  private final CommonCodeStyleSettings.IndentOptions myDefaultIndentOption;
  private final CodeStyleSettings                     mySettings;
  private final Document                              myDocument;

  /**
   * Remembers mappings between backward-shifted aligned block and blocks that cause that shift in order to detect
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

  private final Map<AbstractBlockWrapper, Set<AbstractBlockWrapper>> myAlignmentMappings
    = new HashMap<AbstractBlockWrapper, Set<AbstractBlockWrapper>>();

  /**
   * There is a possible case that we detect a 'cycled alignment' rules (see {@link #myBackwardShiftedAlignedBlocks}). We want
   * just to skip processing for such alignments then.
   * <p/>
   * This container holds 'bad alignment' objects that should not be processed.
   */
  private final Set<Alignment> myAlignmentsToSkip = new HashSet<Alignment>();

  private LeafBlockWrapper myWrapCandidate           = null;
  private LeafBlockWrapper myFirstWrappedBlockOnLine = null;

  private LeafBlockWrapper myFirstTokenBlock;
  private LeafBlockWrapper myLastTokenBlock;

  /**
   * Formatter provides a notion of {@link DependantSpacingImpl dependent spacing}, i.e. spacing that insist on line feed if target
   * dependent region contains line feed.
   * <p/>
   * Example:
   * <pre>
   *       int[] data = {1, 2, 3};
   * </pre>
   * We want to keep that in one line if possible but place curly braces on separate lines if the width is not enough:
   * <pre>
   *      int[] data = {    | &lt; right margin
   *          1, 2, 3       |
   *      }                 |
   * </pre>
   * There is a possible case that particular block has dependent spacing property that targets region that lays beyond the
   * current block. E.g. consider example above - <code>'1'</code> block has dependent spacing that targets the whole
   * <code>'{1, 2, 3}'</code> block. So, it's not possible to answer whether line feed should be used during processing block
   * <code>'1'</code>.
   * <p/>
   * We store such 'forward dependencies' at the current collection where the key is the range of the target 'dependent forward
   * region' and value is dependent spacing object.
   * <p/>
   * Every time we detect that formatter changes 'has line feeds' status of such dependent region, we
   * {@link DependantSpacingImpl#setDependentRegionChanged() mark} the dependent spacing as changed and schedule one more
   * formatting iteration.
   */
  private SortedMap<TextRange, DependantSpacingImpl> myPreviousDependencies =
    new TreeMap<TextRange, DependantSpacingImpl>(new Comparator<TextRange>() {
      @Override
      public int compare(final TextRange o1, final TextRange o2) {
        int offsetsDelta = o1.getEndOffset() - o2.getEndOffset();

        if (offsetsDelta == 0) {
          offsetsDelta = o2.getStartOffset() - o1.getStartOffset();     // starting earlier is greater
        }
        return offsetsDelta;
      }
    });

  private final HashSet<WhiteSpace> myAlignAgain = new HashSet<WhiteSpace>();
  @NotNull
  private final FormattingProgressCallback myProgressCallback;

  private WhiteSpace                      myLastWhiteSpace;
  private boolean                         myDisposed;
  private CommonCodeStyleSettings.IndentOptions myJavaIndentOptions;

  @NotNull
  private State myCurrentState;

  public FormatProcessor(final FormattingDocumentModel docModel,
                         Block rootBlock,
                         CodeStyleSettings settings,
                         CommonCodeStyleSettings.IndentOptions indentOptions,
                         @Nullable FormatTextRanges affectedRanges,
                         @NotNull FormattingProgressCallback progressCallback)
  {
    this(docModel, rootBlock, settings, indentOptions, affectedRanges, -1, progressCallback);
  }

  public FormatProcessor(final FormattingDocumentModel docModel,
                         Block rootBlock,
                         CodeStyleSettings settings,
                         CommonCodeStyleSettings.IndentOptions indentOptions,
                         @Nullable FormatTextRanges affectedRanges,
                         int interestingOffset,
                         @NotNull FormattingProgressCallback progressCallback)
  {
    myProgressCallback = progressCallback;
    myDefaultIndentOption = indentOptions;
    mySettings = settings;
    myDocument = docModel.getDocument();
    myCurrentState = new WrapBlocksState(rootBlock, docModel, affectedRanges, interestingOffset);
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
      AdjustWhiteSpacesState adjustState = new AdjustWhiteSpacesState();
      adjustState.setNext(new ApplyChangesState(model));
      myCurrentState.setNext(adjustState);
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
    if (myCurrentState.isDone()) {
      return true;
    }
    myCurrentState.iteration();
    return myCurrentState.isDone();
  }

  /**
   * Asks current processor to stop any active sequential processing if any.
   */
  public void stopSequentialProcessing() {
    myCurrentState.stop();
  }

  public void formatWithoutRealModifications() {
    formatWithoutRealModifications(false);
  }

  @SuppressWarnings({"WhileLoopSpinsOnField"})
  public void formatWithoutRealModifications(boolean sequentially) {
    myCurrentState.setNext(new AdjustWhiteSpacesState());

    if (sequentially) {
      return;
    }

    doIterationsSynchronously(FormattingStateId.PROCESSING_BLOCKS);
  }

  private void reset() {
    myBackwardShiftedAlignedBlocks.clear();
    myAlignmentMappings.clear();
    myPreviousDependencies.clear();
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
    myCurrentState.setNext(new ApplyChangesState(model));

    if (sequentially) {
      return;
    }

    doIterationsSynchronously(FormattingStateId.APPLYING_CHANGES);
  }

  /**
   * Perform iterations against the {@link #myCurrentState current state} until it's {@link FormattingStateId type}
   * is {@link FormattingStateId#getPreviousStates() less} or equal to the given state.
   *
   * @param state   target state to process
   */
  private void doIterationsSynchronously(@NotNull FormattingStateId state) {
    while ((myCurrentState.getStateId() == state || state.getPreviousStates().contains(myCurrentState.getStateId()))
           && !myCurrentState.isDone())
    {
      myCurrentState.iteration();
    }
  }

  public void setJavaIndentOptions(final CommonCodeStyleSettings.IndentOptions javaIndentOptions) {
    myJavaIndentOptions = javaIndentOptions;
  }

  /**
   * Decides whether applying formatter changes should be applied incrementally one-by-one or merge result should be
   * constructed locally and the whole document text should be replaced. Performs such single bulk change if necessary.
   *
   * @param blocksToModify        changes introduced by formatter
   * @param model                 current formatting model
   * @param indentOption          indent options to use
   * @return                      <code>true</code> if given changes are applied to the document (i.e. no further processing is required);
   *                              <code>false</code> otherwise
   */
  @SuppressWarnings({"deprecation"})
  private boolean applyChangesAtRewriteMode(@NotNull final List<LeafBlockWrapper> blocksToModify,
                                            @NotNull final FormattingModel model,
                                            @NotNull CommonCodeStyleSettings.IndentOptions indentOption)
  {
    FormattingDocumentModel documentModel = model.getDocumentModel();
    Document document = documentModel.getDocument();
    if (document == null) {
      return false;
    }

    List<TextChange> changes = new ArrayList<TextChange>();
    int shift = 0;
    int currentIterationShift = 0;
    for (LeafBlockWrapper block : blocksToModify) {
      WhiteSpace whiteSpace = block.getWhiteSpace();
      CharSequence newWs = documentModel.adjustWhiteSpaceIfNecessary(
        whiteSpace.generateWhiteSpace(getIndentOptionsToUse(block, indentOption)), whiteSpace.getStartOffset(),
        whiteSpace.getEndOffset(), block.getNode(), false
      );
      if (changes.size() > 10000) {
        CharSequence mergeResult = BulkChangesMerger.INSTANCE.mergeToCharSequence(document.getChars(), document.getTextLength(), changes);
        document.replaceString(0, document.getTextLength(), mergeResult);
        shift += currentIterationShift;
        currentIterationShift = 0;
        changes.clear();
      }
      TextChangeImpl change = new TextChangeImpl(newWs, whiteSpace.getStartOffset() + shift, whiteSpace.getEndOffset() + shift);
      currentIterationShift += change.getDiff();
      changes.add(change);
    }
    CharSequence mergeResult = BulkChangesMerger.INSTANCE.mergeToCharSequence(document.getChars(), document.getTextLength(), changes);
    document.replaceString(0, document.getTextLength(), mergeResult);
    cleanupBlocks(blocksToModify);
    return true;
  }

  private static void cleanupBlocks(List<LeafBlockWrapper> blocks) {
    for (LeafBlockWrapper block : blocks) {
      block.getParent().dispose();
      block.dispose();
    }
    blocks.clear();
  }

  @Nullable
  private static DocumentEx getAffectedDocument(final FormattingModel model) {
    final Document document = model.getDocumentModel().getDocument();
    if (document instanceof DocumentEx) {
      return (DocumentEx)document;
    }
    else {
      return null;
    }
  }

  private static int replaceWhiteSpace(final FormattingModel model,
                                       @NotNull final LeafBlockWrapper block,
                                       int shift,
                                       final CharSequence _newWhiteSpace,
                                       final CommonCodeStyleSettings.IndentOptions options
  ) {
    final WhiteSpace whiteSpace = block.getWhiteSpace();
    final TextRange textRange = whiteSpace.getTextRange();
    final TextRange wsRange = shiftRange(textRange, shift);
    final String newWhiteSpace = _newWhiteSpace.toString();
    TextRange newWhiteSpaceRange = model instanceof FormattingModelEx
                                   ? ((FormattingModelEx) model).replaceWhiteSpace(wsRange, block.getNode(), newWhiteSpace)
                                   : model.replaceWhiteSpace(wsRange, newWhiteSpace);

    shift += newWhiteSpaceRange.getLength() - textRange.getLength();

    if (block.isLeaf() && whiteSpace.containsLineFeeds() && block.containsLineFeeds()) {
      final TextRange currentBlockRange = shiftRange(block.getTextRange(), shift);

      IndentInside oldBlockIndent = whiteSpace.getInitialLastLineIndent();
      IndentInside whiteSpaceIndent = IndentInside.createIndentOn(IndentInside.getLastLine(newWhiteSpace));
      final int shiftInside = calcShift(oldBlockIndent, whiteSpaceIndent, options);

      if (shiftInside != 0 || !oldBlockIndent.equals(whiteSpaceIndent)) {
        final TextRange newBlockRange = model.shiftIndentInsideRange(currentBlockRange, shiftInside);
        shift += newBlockRange.getLength() - block.getLength();
      }
    }
    return shift;
  }

  @NotNull
  private List<LeafBlockWrapper> collectBlocksToModify() {
    List<LeafBlockWrapper> blocksToModify = new ArrayList<LeafBlockWrapper>();

    for (LeafBlockWrapper block = myFirstTokenBlock; block != null; block = block.getNextBlock()) {
      final WhiteSpace whiteSpace = block.getWhiteSpace();
      if (!whiteSpace.isReadOnly()) {
        final String newWhiteSpace = whiteSpace.generateWhiteSpace(getIndentOptionsToUse(block, myDefaultIndentOption));
        if (!whiteSpace.equalsToString(newWhiteSpace)) {
          blocksToModify.add(block);
        }
      }
    }
    return blocksToModify;
  }

  @NotNull
  private CommonCodeStyleSettings.IndentOptions getIndentOptionsToUse(@NotNull AbstractBlockWrapper block,
                                                                      @NotNull CommonCodeStyleSettings.IndentOptions fallbackIndentOptions)
  {
    final Language language = block.getLanguage();
    if (language == null) {
      return fallbackIndentOptions;
    }
    final CommonCodeStyleSettings commonSettings = mySettings.getCommonSettings(language);
    if (commonSettings == null) {
      return fallbackIndentOptions;
    }
    final CommonCodeStyleSettings.IndentOptions result = commonSettings.getIndentOptions();
    return result == null ? fallbackIndentOptions : result;
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
      if (processWrap()) {
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
    final SortedMap<TextRange, DependantSpacingImpl> sortedHeadMap = myPreviousDependencies.tailMap(changed);

    boolean result = false;
    for (final Map.Entry<TextRange, DependantSpacingImpl> entry : sortedHeadMap.entrySet()) {
      final TextRange textRange = entry.getKey();

      if (textRange.contains(changed)) {
        final DependantSpacingImpl dependentSpacing = entry.getValue();
        final boolean containedLineFeeds = dependentSpacing.getMinLineFeeds() > 0;
        final boolean containsLineFeeds = containsLineFeeds(textRange);

        if (containedLineFeeds != containsLineFeeds) {
          dependentSpacing.setDependentRegionChanged();
          result = true;
        }
      }
    }
    return result;
  }

  private void saveDependency(final SpacingImpl spaceProperty) {
    final DependantSpacingImpl dependantSpaceProperty = (DependantSpacingImpl)spaceProperty;
    final TextRange dependency = dependantSpaceProperty.getDependency();
    if (dependantSpaceProperty.isDependentRegionChanged()) {
      return;
    }
    myPreviousDependencies.put(dependency, dependantSpaceProperty);
  }

  private static boolean shouldSaveDependency(final SpacingImpl spaceProperty, WhiteSpace whiteSpace) {
    if (!(spaceProperty instanceof DependantSpacingImpl)) return false;

    if (whiteSpace.isReadOnly() || whiteSpace.isLineFeedsAreReadOnly()) return false;

    final TextRange dependency = ((DependantSpacingImpl)spaceProperty).getDependency();
    return whiteSpace.getStartOffset() < dependency.getEndOffset();
  }

  /**
   * Processes the wrap of the current block.
   *
   * @return true if we have changed myCurrentBlock and need to restart its processing; false if myCurrentBlock is unchanged and we can
   * continue processing
   */
  private boolean processWrap() {
    final SpacingImpl spacing = myCurrentBlock.getSpaceProperty();
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    final boolean wrapWasPresent = whiteSpace.containsLineFeeds();

    if (wrapWasPresent) {
      myFirstWrappedBlockOnLine = null;

      if (!whiteSpace.containsLineFeedsInitially()) {
        whiteSpace.removeLineFeeds(spacing, this);
      }
    }

    final boolean wrapIsPresent = whiteSpace.containsLineFeeds();

    final ArrayList<WrapImpl> wraps = myCurrentBlock.getWraps();
    for (WrapImpl wrap : wraps) {
      wrap.setWrapOffset(myCurrentBlock.getStartOffset());
    }

    final WrapImpl wrap = getWrapToBeUsed(wraps);

    if (wrap != null || wrapIsPresent) {
      if (!wrapIsPresent && !canReplaceWrapCandidate(wrap)) {
        myCurrentBlock = myWrapCandidate;
        return true;
      }
      if (wrap != null && wrap.getChopStartBlock() != null) {
        // getWrapToBeUsed() returns the block only if it actually exceeds the right margin. In this case, we need to go back to the
        // first block that has the CHOP_IF_NEEDED wrap type and start wrapping from there.
        myCurrentBlock = wrap.getChopStartBlock();
        wrap.setActive();
        return true;
      }
      if (wrap != null && isChopNeeded(wrap)) {
        wrap.setActive();
      }

      if (!wrapIsPresent) {
        whiteSpace.ensureLineFeed();
        if (!wrapWasPresent) {
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
      for (final WrapImpl wrap1 : wraps) {
        if (isCandidateToBeWrapped(wrap1) && canReplaceWrapCandidate(wrap1)) {
          myWrapCandidate = myCurrentBlock;
        }
        if (isChopNeeded(wrap1)) {
          wrap1.saveChopBlock(myCurrentBlock);
        }
      }
    }

    if (!whiteSpace.containsLineFeeds() && myWrapCandidate != null && !whiteSpace.isReadOnly() && lineOver()) {
      myCurrentBlock = myWrapCandidate;
      return true;
    }

    return false;
  }

  /**
   * Allows to answer if wrap of the {@link #myWrapCandidate} object (if any) may be replaced by the given wrap.
   *
   * @param wrap wrap candidate to check
   * @return <code>true</code> if wrap of the {@link #myWrapCandidate} object (if any) may be replaced by the given wrap;
   *         <code>false</code> otherwise
   */
  private boolean canReplaceWrapCandidate(WrapImpl wrap) {
    if (myWrapCandidate == null) return true;
    WrapImpl.Type type = wrap.getType();
    if (wrap.isActive() && (type == WrapImpl.Type.CHOP_IF_NEEDED || type == WrapImpl.Type.WRAP_ALWAYS)) return true;
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
   * @return <code>true</code> if current formatting iteration should be continued;
   *         <code>false</code> otherwise (e.g. if previously processed block is shifted inside this method for example
   *         because of specified alignment options)
   */
  private boolean adjustIndent() {
    AlignmentImpl alignment = CoreFormatterUtil.getAlignment(myCurrentBlock);
    WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();

    if (alignment == null || myAlignmentsToSkip.contains(alignment)) {
      if (whiteSpace.containsLineFeeds()) {
        adjustSpacingByIndentOffset();
      }
      else {
        whiteSpace.arrangeSpaces(myCurrentBlock.getSpaceProperty());
      }
      return true;
    }

    BlockAlignmentProcessor alignmentProcessor = ALIGNMENT_PROCESSORS.get(alignment.getAnchor());
    if (alignmentProcessor == null) {
      LOG.error(String.format("Can't find alignment processor for alignment anchor %s", alignment.getAnchor()));
      return true;
    }

    BlockAlignmentProcessor.Context context = new BlockAlignmentProcessor.Context(
      myDocument, alignment, myCurrentBlock, myAlignmentMappings, myBackwardShiftedAlignedBlocks,
      getIndentOptionsToUse(myCurrentBlock, myDefaultIndentOption)
    );
    BlockAlignmentProcessor.Result result = alignmentProcessor.applyAlignment(context);
    final LeafBlockWrapper offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(myCurrentBlock);
    switch (result) {
      case TARGET_BLOCK_PROCESSED_NOT_ALIGNED: return true;
      case TARGET_BLOCK_ALIGNED: storeAlignmentMapping(); return true;
      case BACKWARD_BLOCK_ALIGNED:
        if (offsetResponsibleBlock == null) {
          return true;
        }
        Set<LeafBlockWrapper> blocksCausedRealignment = new HashSet<LeafBlockWrapper>();
        myBackwardShiftedAlignedBlocks.clear();
        myBackwardShiftedAlignedBlocks.put(offsetResponsibleBlock, blocksCausedRealignment);
        blocksCausedRealignment.add(myCurrentBlock);
        storeAlignmentMapping(myCurrentBlock, offsetResponsibleBlock);
        myCurrentBlock = offsetResponsibleBlock.getNextBlock();
        onCurrentLineChanged();
        return false;
      case RECURSION_DETECTED:
        myCurrentBlock = offsetResponsibleBlock; // Fall through to the 'register alignment to skip'.
      case UNABLE_TO_ALIGN_BACKWARD_BLOCK:
        myAlignmentsToSkip.add(alignment);
        return false;
      default: return true;
    }
  }

  /**
   * We need to track blocks which white spaces are modified because of alignment rules.
   * <p/>
   * This method encapsulates the logic of storing such information.
   */
  private void storeAlignmentMapping() {
    AlignmentImpl alignment = null;
    AbstractBlockWrapper block = myCurrentBlock;
    while (alignment == null && block != null) {
      alignment = block.getAlignment();
      block = block.getParent();
    }
    if (alignment != null) {
      block = alignment.getOffsetRespBlockBefore(myCurrentBlock);
      if (block != null) {
        storeAlignmentMapping(myCurrentBlock, block);
      }
    }
  }

  private void storeAlignmentMapping(AbstractBlockWrapper block1, AbstractBlockWrapper block2) {
    doStoreAlignmentMapping(block1, block2);
    doStoreAlignmentMapping(block2, block1);
  }

  private void doStoreAlignmentMapping(AbstractBlockWrapper key, AbstractBlockWrapper value) {
    Set<AbstractBlockWrapper> wrappers = myAlignmentMappings.get(key);
    if (wrappers == null) {
      myAlignmentMappings.put(key, wrappers = new HashSet<AbstractBlockWrapper>());
    }
    wrappers.add(value);
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
    IndentData offset = myCurrentBlock.calculateOffset(getIndentOptionsToUse(myCurrentBlock, myDefaultIndentOption));
    myCurrentBlock.getWhiteSpace().setSpaces(offset.getSpaces(), offset.getIndentSpaces());
  }

  private boolean isChopNeeded(final WrapImpl wrap) {
    return wrap != null && wrap.getType() == WrapImpl.Type.CHOP_IF_NEEDED && isSuitableInTheCurrentPosition(wrap);
  }

  private boolean isSuitableInTheCurrentPosition(final WrapImpl wrap) {
    if (wrap.getWrapOffset() < myCurrentBlock.getStartOffset()) {
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
   * @return <code>true</code> if it's ok to wrap at the currently processed block; <code>false</code> otherwise
   */
  private boolean positionAfterWrappingIsSuitable() {
    final WhiteSpace whiteSpace = myCurrentBlock.getWhiteSpace();
    if (whiteSpace.containsLineFeeds()) return true;
    final int spaces = whiteSpace.getSpaces();
    int indentSpaces = whiteSpace.getIndentSpaces();
    try {
      final int startColumnNow = CoreFormatterUtil.getStartColumn(myCurrentBlock);
      whiteSpace.ensureLineFeed();
      adjustLineIndent();
      final int startColumnAfterWrap = CoreFormatterUtil.getStartColumn(myCurrentBlock);
      return startColumnNow > startColumnAfterWrap;
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

    for (final WrapImpl wrap : wraps) {
      if (!isSuitableInTheCurrentPosition(wrap)) continue;
      if (wrap.isActive()) return wrap;

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
   * @return <code>true</code> if {@link #myCurrentBlock currently processed wrapped block} doesn't contain line feeds and
   *         exceeds right margin; <code>false</code> otherwise
   */
  private boolean lineOver() {
    return !myCurrentBlock.containsLineFeeds() &&
           CoreFormatterUtil.getStartColumn(myCurrentBlock) + myCurrentBlock.getLength() > mySettings.RIGHT_MARGIN;
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
   * @return indent of the current block if any; <code>null</code> otherwise
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
          final int offsetBeforeBlock = CoreFormatterUtil.getStartColumn(offsetResponsibleBlock);
          final AbstractBlockWrapper indentedParentBlock = CoreFormatterUtil.getIndentedParentBlock(myCurrentBlock);
          if (indentedParentBlock == null) {
            return new IndentData(0, offsetBeforeBlock);
          }
          else {
            final int parentIndent = indentedParentBlock.getWhiteSpace().getIndentOffset();
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
        if (current == null || current.getStartOffset() != myCurrentBlock.getStartOffset()) return null;
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
  private LeafBlockWrapper getPrevBlock(@Nullable final LeafBlockWrapper result) {
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
    final        ChildAttributes      attributes;
    final        int                  index;

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

    return adjustLineIndent(info.parent, info.attributes, info.index);
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

  private IndentInfo adjustLineIndent(final AbstractBlockWrapper parent, final ChildAttributes childAttributes, final int index) {
    int alignOffset = getAlignOffsetBefore(childAttributes.getAlignment(), null);
    if (alignOffset == -1) {
      return parent.calculateChildOffset(getIndentOptionsToUse(parent, myDefaultIndentOption), childAttributes, index).createIndentInfo();
    }
    else {
      AbstractBlockWrapper indentedParentBlock = CoreFormatterUtil.getIndentedParentBlock(myCurrentBlock);
      if (indentedParentBlock == null) {
        return new IndentInfo(0, 0, alignOffset);
      }
      else {
        int indentOffset = indentedParentBlock.getWhiteSpace().getIndentOffset();
        if (indentOffset > alignOffset) {
          return new IndentInfo(0, 0, alignOffset);
        }
        else {
          return new IndentInfo(0, indentOffset, alignOffset - indentOffset);
        }
      }
    }
  }

  private static int getAlignOffsetBefore(@Nullable final Alignment alignment, @Nullable final LeafBlockWrapper blockAfter) {
    if (alignment == null) return -1;
    final LeafBlockWrapper alignRespBlock = ((AlignmentImpl)alignment).getOffsetRespBlockBefore(blockAfter);
    if (alignRespBlock != null) {
      return CoreFormatterUtil.getStartColumn(alignRespBlock);
    }
    else {
      return -1;
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

  /**
   * Calculates difference in visual columns between the given indents.
   *
   * @param oldIndent  old indent
   * @param newIndent  new indent
   * @param options    indent options to use
   * @return           difference in visual columns between the given indents
   */
  private static int calcShift(@NotNull final IndentInside oldIndent,
                               @NotNull final IndentInside newIndent,
                               @NotNull final CommonCodeStyleSettings.IndentOptions options)
  {
    if (oldIndent.equals(newIndent)) return 0;
    if (options.USE_TAB_CHARACTER) {
      return (newIndent.tabs - oldIndent.getTabsCount(options)) * options.TAB_SIZE;
    }
    else {
      return newIndent.whiteSpaces - oldIndent.getSpacesCount(options);
    }
  }

  /**
   * Utility method to use during debugging formatter processing.
   *
   * @return    text that contains intermediate formatter-introduced changes (even not committed yet)
   */
  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  private String getCurrentText() {
    StringBuilder result = new StringBuilder();
    for (LeafBlockWrapper block = myFirstTokenBlock; block != null; block = block.getNextBlock()) {
      result.append(block.getWhiteSpace().generateWhiteSpace(getIndentOptionsToUse(block, myDefaultIndentOption)));
      result.append(myDocument.getCharsSequence().subSequence(block.getStartOffset(), block.getEndOffset()));
    }
    return result.toString();
  }

  private abstract class State {

    private final FormattingStateId myStateId;

    private State   myNextState;
    private boolean myDone;

    protected State(FormattingStateId stateId) {
      myStateId = stateId;
    }

    public void iteration() {
      if (!isDone()) {
        doIteration();
      }
      shiftStateIfNecessary();
    }

    public boolean isDone() {
      return myDone;
    }

    protected void setDone(boolean done) {
      myDone = done;
    }

    public void setNext(@NotNull State state) {
      if (getStateId() == state.getStateId() || (myNextState != null && myNextState.getStateId() == state.getStateId())) {
        return;
      }
      myNextState = state;
      shiftStateIfNecessary();
    }

    public FormattingStateId getStateId() {
      return myStateId;
    }

    public void stop() {
    }

    protected abstract void doIteration();
    protected abstract void prepare();

    private void shiftStateIfNecessary() {
      if (isDone() && myNextState != null) {
        myCurrentState = myNextState;
        myNextState = null;
        myCurrentState.prepare();
      }
    }
  }

  private class WrapBlocksState extends State {

    private final InitialInfoBuilder      myWrapper;
    private final FormattingDocumentModel myModel;

    WrapBlocksState(@NotNull Block root,
                    @NotNull FormattingDocumentModel model,
                    @Nullable final FormatTextRanges affectedRanges,
                    int interestingOffset)
    {
      super(FormattingStateId.WRAPPING_BLOCKS);
      myModel = model;
      myWrapper = InitialInfoBuilder.prepareToBuildBlocksSequentially(
        root, model, affectedRanges, mySettings, myDefaultIndentOption, interestingOffset, myProgressCallback
      );
    }

    @Override
    protected void prepare() {
    }

    @Override
    public void doIteration() {
      if (isDone()) {
        return;
      }

      setDone(myWrapper.iteration());
      if (!isDone()) {
        return;
      }

      myInfos = myWrapper.getBlockToInfoMap();
      myRootBlockWrapper = myWrapper.getRootBlockWrapper();
      myFirstTokenBlock = myWrapper.getFirstTokenBlock();
      myLastTokenBlock = myWrapper.getLastTokenBlock();
      myCurrentBlock = myFirstTokenBlock;
      myTextRangeToWrapper = buildTextRangeToInfoMap(myFirstTokenBlock);
      myLastWhiteSpace = new WhiteSpace(getLastBlock().getEndOffset(), false);
      myLastWhiteSpace.append(myModel.getTextLength(), myModel, myDefaultIndentOption);
    }
  }

  private class AdjustWhiteSpacesState extends State {

    AdjustWhiteSpacesState() {
      super(FormattingStateId.PROCESSING_BLOCKS);
    }

    @Override
    protected void prepare() {
    }

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
        myPreviousDependencies.clear();
        myCurrentBlock = myFirstTokenBlock;
      }
    }
  }

  private class ApplyChangesState extends State {

    private final FormattingModel        myModel;
    private       List<LeafBlockWrapper> myBlocksToModify;
    private       int                    myShift;
    private       int                    myIndex;
    private       boolean                myResetBulkUpdateState;

    private ApplyChangesState(FormattingModel model) {
      super(FormattingStateId.APPLYING_CHANGES);
      myModel = model;
    }

    @Override
    protected void prepare() {
      myBlocksToModify = collectBlocksToModify();
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

      if (myBlocksToModify.isEmpty()) {
        setDone(true);
        return;
      }

      //for GeneralCodeFormatterTest
      if (myJavaIndentOptions == null) {
        myJavaIndentOptions = mySettings.getIndentOptions(StdFileTypes.JAVA);
      }

      myProgressCallback.beforeApplyingFormatChanges(myBlocksToModify);

      final int blocksToModifyCount = myBlocksToModify.size();
      final boolean bulkReformat = blocksToModifyCount > 50;
      DocumentEx updatedDocument = bulkReformat ? getAffectedDocument(myModel) : null;
      if (updatedDocument != null) {
        updatedDocument.setInBulkUpdate(true);
        myResetBulkUpdateState = true;
      }
      if (blocksToModifyCount > BULK_REPLACE_OPTIMIZATION_CRITERIA
          && applyChangesAtRewriteMode(myBlocksToModify, myModel, myDefaultIndentOption))
      {
        setDone(true);
      }
    }

    @Override
    protected void doIteration() {
      LeafBlockWrapper blockWrapper = myBlocksToModify.get(myIndex);
      myShift = replaceWhiteSpace(
        myModel,
        blockWrapper,
        myShift,
        blockWrapper.getWhiteSpace().generateWhiteSpace(getIndentOptionsToUse(blockWrapper, myDefaultIndentOption)),
        myDefaultIndentOption
      );
      myProgressCallback.afterApplyingChange(blockWrapper);
      // block could be gc'd
      blockWrapper.getParent().dispose();
      blockWrapper.dispose();
      myBlocksToModify.set(myIndex, null);
      myIndex++;

      if (myIndex >= myBlocksToModify.size()) {
        setDone(true);
      }
    }

    @Override
    protected void setDone(boolean done) {
      super.setDone(done);

      if (myResetBulkUpdateState) {
        DocumentEx document = getAffectedDocument(myModel);
        if (document != null) {
          document.setInBulkUpdate(false);
          myResetBulkUpdateState = false;
        }
      }

      if (done) {
        myModel.commitChanges();
      }
    }

    @Override
    public void stop() {
      if (myIndex > 0) {
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            myModel.commitChanges();
          }
        });
      }
    }
  }
}
