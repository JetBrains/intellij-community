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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SequentialTask;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FormatterImpl extends FormatterEx
  implements ApplicationComponent,
             IndentFactory,
             WrapFactory,
             AlignmentFactory,
             SpacingFactory,
             FormattingModelFactory
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatterImpl");

  private FormattingProgressIndicatorImpl myProgressIndicator;
  
  private final AtomicInteger myIsDisabledCount = new AtomicInteger();
  private final IndentImpl NONE_INDENT = new IndentImpl(Indent.Type.NONE, false, false);
  private final IndentImpl myAbsoluteNoneIndent = new IndentImpl(Indent.Type.NONE, true, false);
  private final IndentImpl myLabelIndent = new IndentImpl(Indent.Type.LABEL, false, false);
  private final IndentImpl myContinuationIndentRelativeToDirectParent = new IndentImpl(Indent.Type.CONTINUATION, false, true);
  private final IndentImpl myContinuationIndentNotRelativeToDirectParent = new IndentImpl(Indent.Type.CONTINUATION, false, false);
  private final IndentImpl myContinuationWithoutFirstIndentRelativeToDirectParent
    = new IndentImpl(Indent.Type.CONTINUATION_WITHOUT_FIRST, false, true);
  private final IndentImpl myContinuationWithoutFirstIndentNotRelativeToDirectParent
    = new IndentImpl(Indent.Type.CONTINUATION_WITHOUT_FIRST, false, false);
  private final IndentImpl myAbsoluteLabelIndent = new IndentImpl(Indent.Type.LABEL, true, false);
  private final IndentImpl myNormalIndentRelativeToDirectParent = new IndentImpl(Indent.Type.NORMAL, false, true);
  private final IndentImpl myNormalIndentNotRelativeToDirectParent = new IndentImpl(Indent.Type.NORMAL, false, false);
  private final SpacingImpl myReadOnlySpacing = new SpacingImpl(0, 0, 0, true, false, true, 0, false, 0);

  public FormatterImpl() {
    Indent.setFactory(this);
    Wrap.setFactory(this);
    Alignment.setFactory(this);
    Spacing.setFactory(this);
    FormattingModelProvider.setFactory(this);
  }

  public Alignment createAlignment(boolean applyToNonFirstBlocksOnLine, @NotNull Alignment.Anchor anchor) {
    return new AlignmentImpl(applyToNonFirstBlocksOnLine, anchor);
  }

  public Alignment createChildAlignment(final Alignment base) {
    AlignmentImpl result = new AlignmentImpl();
    result.setParent(base);
    return result;
  }

  public Indent getNormalIndent(boolean relative) {
    return relative ? myNormalIndentRelativeToDirectParent : myNormalIndentNotRelativeToDirectParent;
  }

  public Indent getNoneIndent() {
    return NONE_INDENT;
  }

  @Override
  public void setProgressIndicator(@NotNull FormattingProgressIndicatorImpl progressIndicator) {
    if (!FormatterUtil.FORMATTER_ACTION_NAMES.contains(CommandProcessor.getInstance().getCurrentCommandName())) {
      return;
    }
    myProgressIndicator = progressIndicator;
  }

  public void format(final FormattingModel model, final CodeStyleSettings settings,
                     final CodeStyleSettings.IndentOptions indentOptions,
                     final CodeStyleSettings.IndentOptions javaIndentOptions,
                     final FormatTextRanges affectedRanges) throws IncorrectOperationException
  {
    SequentialTask task = new MyFormattingTask() {
      @NotNull
      @Override
      protected FormatProcessor buildProcessor() {
        FormatProcessor processor = new FormatProcessor(
          model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, affectedRanges, FormattingProgressIndicator.EMPTY
        );
        processor.setJavaIndentOptions(javaIndentOptions);

        processor.format(model);
        return processor;
      }
    };
    execute(task);
  }

  public Wrap createWrap(WrapType type, boolean wrapFirstElement) {
    return new WrapImpl(type, wrapFirstElement);
  }

  public Wrap createChildWrap(final Wrap parentWrap, final WrapType wrapType, final boolean wrapFirstElement) {
    final WrapImpl result = new WrapImpl(wrapType, wrapFirstElement);
    result.registerParent((WrapImpl)parentWrap);
    return result;
  }

  public Spacing createSpacing(int minOffset,
                               int maxOffset,
                               int minLineFeeds,
                               final boolean keepLineBreaks,
                               final int keepBlankLines) {
    return getSpacingImpl(minOffset, maxOffset, minLineFeeds, false, false, keepLineBreaks, keepBlankLines,false, 0);
  }

  public Spacing getReadOnlySpacing() {
    return myReadOnlySpacing;
  }

  public Spacing createDependentLFSpacing(int minOffset, int maxOffset, TextRange dependence, boolean keepLineBreaks,
                                          int keepBlankLines) {
    return new DependantSpacingImpl(minOffset, maxOffset, dependence, keepLineBreaks, keepBlankLines);
  }

  @NotNull
  private FormattingProgressIndicator getProgressIndicator() {
    FormattingProgressIndicator result = myProgressIndicator;
    return result == null ? FormattingProgressIndicator.EMPTY : result;
  }
  
  public void format(final FormattingModel model,
                     final CodeStyleSettings settings,
                     final CodeStyleSettings.IndentOptions indentOptions,
                     final FormatTextRanges affectedRanges) throws IncorrectOperationException {
    SequentialTask task = new MyFormattingTask() {
      @NotNull
      @Override
      protected FormatProcessor buildProcessor() {
        FormatProcessor processor = new FormatProcessor(
          model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, affectedRanges, getProgressIndicator()
        );
        processor.format(model, true);
        return processor;
      }
    };
    execute(task);
  }

  public void formatWithoutModifications(final FormattingDocumentModel model,
                                         final Block rootBlock,
                                         final CodeStyleSettings settings,
                                         final CodeStyleSettings.IndentOptions indentOptions,
                                         final TextRange affectedRange) throws IncorrectOperationException
  {
    SequentialTask task = new MyFormattingTask() {
      @NotNull
      @Override
      protected FormatProcessor buildProcessor() {
        FormatProcessor result = new FormatProcessor(
          model, rootBlock, settings, indentOptions, new FormatTextRanges(affectedRange, true), FormattingProgressIndicator.EMPTY
        );
        result.formatWithoutRealModifications();
        return result;
      }
    };
    execute(task);
  }

  /**
   * Execute given sequential formatting task. Two approaches are possible:
   * <pre>
   * <ul>
   *   <li>
   *      <b>synchronous</b> - the task is completely executed during the current method processing;
   *   </li>
   *   <li>
   *       <b>asynchronous</b> - the task is executed at background thread under the progress dialog;
   *   </li>
   * </ul>
   * </pre>
   * 
   * @param task    task to execute
   */
  private void execute(@NotNull SequentialTask task) {
    disableFormatting();
    Application application = ApplicationManager.getApplication();
    if (myProgressIndicator == null || !application.isDispatchThread() || application.isUnitTestMode()) {
      try {
        task.prepare();
        while (!task.isDone()) {
          task.iteration();
        }
      }
      finally {
        enableFormatting();
        myProgressIndicator = null;
      }
    }
    else {
      myProgressIndicator.setTask(task);
      myProgressIndicator.addCallback(FormattingProgressIndicator.EventType.SUCCESS, new Runnable() {
        @Override
        public void run() {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              // Reset current progress indicator.
              myProgressIndicator = null;
              enableFormatting();
            }
          });
        }
      });
      ProgressManager.getInstance().run(myProgressIndicator);
    }
  }

  public IndentInfo getWhiteSpaceBefore(final FormattingDocumentModel model,
                                        final Block block,
                                        final CodeStyleSettings settings,
                                        final CodeStyleSettings.IndentOptions indentOptions,
                                        final TextRange affectedRange, final boolean mayChangeLineFeeds)
  {
    disableFormatting();
    try {
      final FormatProcessor processor = buildProcessorAndWrapBlocks(
        model, block, settings, indentOptions, new FormatTextRanges(affectedRange, true)
      );
      final LeafBlockWrapper blockBefore = processor.getBlockAfter(affectedRange.getStartOffset());
      LOG.assertTrue(blockBefore != null);
      WhiteSpace whiteSpace = blockBefore.getWhiteSpace();
      LOG.assertTrue(whiteSpace != null);
      if (!mayChangeLineFeeds) {
        whiteSpace.setLineFeedsAreReadOnly();
      }
      processor.setAllWhiteSpacesAreReadOnly();
      whiteSpace.setReadOnly(false);
      processor.formatWithoutRealModifications();
      return new IndentInfo(whiteSpace.getLineFeeds(), whiteSpace.getIndentOffset(), whiteSpace.getSpaces());
    }
    finally {
      enableFormatting();
    }
  }

  public void adjustLineIndentsForRange(final FormattingModel model,
                                        final CodeStyleSettings settings,
                                        final CodeStyleSettings.IndentOptions indentOptions,
                                        final TextRange rangeToAdjust) {
    disableFormatting();
    try {
      final FormattingDocumentModel documentModel = model.getDocumentModel();
      final Block block = model.getRootBlock();
      final FormatProcessor processor = buildProcessorAndWrapBlocks(
        documentModel, block, settings, indentOptions, new FormatTextRanges(rangeToAdjust, true)
      );
      LeafBlockWrapper tokenBlock = processor.getFirstTokenBlock();
      while (tokenBlock != null) {
        final WhiteSpace whiteSpace = tokenBlock.getWhiteSpace();
        whiteSpace.setLineFeedsAreReadOnly(true);
        if (!whiteSpace.containsLineFeeds()) {
          whiteSpace.setIsReadOnly(true);
        }
        tokenBlock = tokenBlock.getNextBlock();
      }
      processor.formatWithoutRealModifications();
      processor.performModifications(model);
    }
    finally {
      enableFormatting();
    }
  }

  public void formatAroundRange(final FormattingModel model,
                                final CodeStyleSettings settings,
                                final TextRange textRange,
                                final FileType fileType) {
    disableFormatting();
    try {
      final FormattingDocumentModel documentModel = model.getDocumentModel();
      final Block block = model.getRootBlock();
      final FormatProcessor processor = buildProcessorAndWrapBlocks(
        documentModel, block, settings, settings.getIndentOptions(fileType), null
      );
      LeafBlockWrapper tokenBlock = processor.getFirstTokenBlock();
      while (tokenBlock != null) {
        final WhiteSpace whiteSpace = tokenBlock.getWhiteSpace();

        if (whiteSpace.getEndOffset() < textRange.getStartOffset() || whiteSpace.getEndOffset() > textRange.getEndOffset() + 1) {
          whiteSpace.setIsReadOnly(true);
        } else if (whiteSpace.getStartOffset() > textRange.getStartOffset() &&
                   whiteSpace.getEndOffset() < textRange.getEndOffset())
        {
          if (whiteSpace.containsLineFeeds()) {
            whiteSpace.setLineFeedsAreReadOnly(true);
          } else {
            whiteSpace.setIsReadOnly(true);
          }
        }

        tokenBlock = tokenBlock.getNextBlock();
      }
      processor.formatWithoutRealModifications();
      processor.performModifications(model);
    }
    finally{
      enableFormatting();
    }
  }

  public int adjustLineIndent(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final int offset,
                              final TextRange affectedRange) throws IncorrectOperationException {
    disableFormatting();
    if (model instanceof PsiBasedFormattingModel) {
      ((PsiBasedFormattingModel)model).canModifyAllWhiteSpaces();
    }
    try {
      final FormattingDocumentModel documentModel = model.getDocumentModel();
      final Block block = model.getRootBlock();
      final FormatProcessor processor = buildProcessorAndWrapBlocks(
        documentModel, block, settings, indentOptions, new FormatTextRanges(affectedRange, true), offset
      );

      final LeafBlockWrapper blockAfterOffset = processor.getBlockAfter(offset);

      if (blockAfterOffset != null && blockAfterOffset.contains(offset)) {
        return offset;
      }

      WhiteSpace whiteSpace = blockAfterOffset != null ? blockAfterOffset.getWhiteSpace() : processor.getLastWhiteSpace();
      return adjustLineIndent(offset, documentModel, processor, indentOptions, model, whiteSpace);
    }
    finally {
      enableFormatting();
    }
  }

  /**
   * Delegates to
   * {@link #buildProcessorAndWrapBlocks(FormattingDocumentModel, Block, CodeStyleSettings, CodeStyleSettings.IndentOptions, FormatTextRanges, int)}
   * with '-1' as an interested offset.
   * 
   * @param docModel
   * @param rootBlock
   * @param settings
   * @param indentOptions
   * @param affectedRanges
   * @return
   */
  private static FormatProcessor buildProcessorAndWrapBlocks(final FormattingDocumentModel docModel,
                                                             Block rootBlock,
                                                             CodeStyleSettings settings,
                                                             CodeStyleSettings.IndentOptions indentOptions,
                                                             @Nullable FormatTextRanges affectedRanges)
  {
    return buildProcessorAndWrapBlocks(docModel, rootBlock, settings, indentOptions, affectedRanges, -1);
  }

  /**
   * Builds {@link FormatProcessor} instance and asks it to wrap all {@link Block code blocks}
   * {@link FormattingModel#getRootBlock() derived from the given model}. 
   * 
   * @param docModel            target model
   * @param rootBlock           root block to process
   * @param settings            code style settings to use
   * @param indentOptions       indent options to use
   * @param affectedRanges      ranges to reformat
   * @param interestingOffset   interesting offset; <code>'-1'</code> if no particular offset has a special interest
   * @return                    format processor instance with wrapped {@link Block code blocks}
   */
  @SuppressWarnings({"StatementWithEmptyBody"})
  private static FormatProcessor buildProcessorAndWrapBlocks(final FormattingDocumentModel docModel,
                                                             Block rootBlock,
                                                             CodeStyleSettings settings,
                                                             CodeStyleSettings.IndentOptions indentOptions,
                                                             @Nullable FormatTextRanges affectedRanges,
                                                             int interestingOffset)
  {
    FormatProcessor processor = new FormatProcessor(
      docModel, rootBlock, settings, indentOptions, affectedRanges, interestingOffset, FormattingProgressIndicator.EMPTY
    );
    while (!processor.iteration()) ;
    return processor;
  }
  
  private static int adjustLineIndent(
    final int offset,
    final FormattingDocumentModel documentModel,
    final FormatProcessor processor,
    final CodeStyleSettings.IndentOptions indentOptions,
    final FormattingModel model,
    final WhiteSpace whiteSpace)
  {
    boolean wsContainsCaret = whiteSpace.getStartOffset() <= offset && offset < whiteSpace.getEndOffset();

    int lineStartOffset = getLineStartOffset(offset, whiteSpace, documentModel);

    final IndentInfo indent = calcIndent(offset, documentModel, processor, whiteSpace);

    final String newWS = whiteSpace.generateWhiteSpace(indentOptions, lineStartOffset, indent).toString();
    if (!whiteSpace.equalsToString(newWS)) {
      try {
        model.replaceWhiteSpace(whiteSpace.getTextRange(), newWS);
      }
      finally {
        model.commitChanges();
      }
    }

    final int defaultOffset = offset - whiteSpace.getLength() + newWS.length();

    if (wsContainsCaret) {
      final int ws = whiteSpace.getStartOffset()
                     + CharArrayUtil.shiftForward(newWS, Math.max(0, lineStartOffset - whiteSpace.getStartOffset()), " \t");
      return Math.max(defaultOffset, ws);
    } else {
      return defaultOffset;
    }
  }

  private static boolean hasContentAfterLineBreak(final FormattingDocumentModel documentModel, final int offset, final WhiteSpace whiteSpace) {
    return documentModel.getLineNumber(offset) == documentModel.getLineNumber(whiteSpace.getEndOffset()) &&
           documentModel.getTextLength() != offset;
  }

  public String getLineIndent(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final int offset,
                              final TextRange affectedRange) {
    final FormattingDocumentModel documentModel = model.getDocumentModel();
    final Block block = model.getRootBlock();
    final FormatProcessor processor = buildProcessorAndWrapBlocks(
      documentModel, block, settings, indentOptions, new FormatTextRanges(affectedRange, true), offset
    );
    final LeafBlockWrapper blockAfterOffset = processor.getBlockAfter(offset);

    if (blockAfterOffset != null) {
      final WhiteSpace whiteSpace = blockAfterOffset.getWhiteSpace();
      final IndentInfo indent = calcIndent(offset, documentModel, processor, whiteSpace);

      return indent.generateNewWhiteSpace(indentOptions);
    }
    return null;
  }

  private static IndentInfo calcIndent(int offset, FormattingDocumentModel documentModel, FormatProcessor processor, WhiteSpace whiteSpace) {
    processor.setAllWhiteSpacesAreReadOnly();
    whiteSpace.setLineFeedsAreReadOnly(true);
    final IndentInfo indent;
    if (hasContentAfterLineBreak(documentModel, offset, whiteSpace)) {
      whiteSpace.setReadOnly(false);
      processor.formatWithoutRealModifications();
      indent = new IndentInfo(0, whiteSpace.getIndentOffset(), whiteSpace.getSpaces());
    }
    else {
      indent = processor.getIndentAt(offset);
    }
    return indent;
  }

  public static String getText(final FormattingDocumentModel documentModel) {
    return getCharSequence(documentModel).toString();
  }

  private static CharSequence getCharSequence(final FormattingDocumentModel documentModel) {
    return documentModel.getText(new TextRange(0, documentModel.getTextLength()));
  }

  private static int getLineStartOffset(final int offset,
                                        final WhiteSpace whiteSpace,
                                        final FormattingDocumentModel documentModel) {
    int lineStartOffset = offset;

    CharSequence text = getCharSequence(documentModel);
    lineStartOffset = CharArrayUtil.shiftBackwardUntil(text, lineStartOffset, " \t\n");
    if (lineStartOffset > whiteSpace.getStartOffset()) {
      if (lineStartOffset >= text.length()) lineStartOffset = text.length() - 1;
      final int wsStart = whiteSpace.getStartOffset();
      int prevEnd;

      if (text.charAt(lineStartOffset) == '\n'
          && wsStart <= (prevEnd = documentModel.getLineStartOffset(documentModel.getLineNumber(lineStartOffset - 1))) &&
          documentModel.getText(new TextRange(prevEnd, lineStartOffset)).toString().trim().length() == 0 // ws consists of space only, it is not true for <![CDATA[
         ) {
        lineStartOffset--;
      }
      lineStartOffset = CharArrayUtil.shiftBackward(text, lineStartOffset, "\t ");
      if (lineStartOffset < 0) lineStartOffset = 0;
      if (lineStartOffset != offset && text.charAt(lineStartOffset) == '\n') {
        lineStartOffset++;
      }
    }
    return lineStartOffset;
  }

  public void adjustTextRange(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final TextRange affectedRange,
                              final boolean keepBlankLines,
                              final boolean keepLineBreaks,
                              final boolean changeWSBeforeFirstElement,
                              final boolean changeLineFeedsBeforeFirstElement,
                              @Nullable final IndentInfoStorage indentInfoStorage) {
    disableFormatting();
    try {
      final FormatProcessor processor = buildProcessorAndWrapBlocks(
        model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, new FormatTextRanges(affectedRange, true)
      );
      LeafBlockWrapper current = processor.getFirstTokenBlock();
      while (current != null) {
        WhiteSpace whiteSpace = current.getWhiteSpace();

        if (!whiteSpace.isReadOnly()) {
          if (whiteSpace.getStartOffset() > affectedRange.getStartOffset()) {
            if (whiteSpace.containsLineFeeds() && indentInfoStorage != null) {
              whiteSpace.setLineFeedsAreReadOnly(true);
              current.setIndentFromParent(indentInfoStorage.getIndentInfo(current.getStartOffset()));
            }
            else {
              whiteSpace.setReadOnly(true);
            }
          }
          else {
            if (!changeWSBeforeFirstElement) {
              whiteSpace.setReadOnly(true);
            }
            else {
              if (!changeLineFeedsBeforeFirstElement) {
                whiteSpace.setLineFeedsAreReadOnly(true);
              }
              final SpacingImpl spaceProperty = current.getSpaceProperty();
              if (spaceProperty != null) {
                boolean needChange = false;
                int newKeepLineBreaks = spaceProperty.getKeepBlankLines();
                boolean newKeepLineBreaksFlag = spaceProperty.shouldKeepLineFeeds();

                if (!keepLineBreaks) {
                  needChange = true;
                  newKeepLineBreaksFlag = false;
                }
                if (!keepBlankLines) {
                  needChange = true;
                  newKeepLineBreaks = 0;
                }

                if (needChange) {
                  assert !(spaceProperty instanceof DependantSpacingImpl);
                  current.setSpaceProperty(
                    getSpacingImpl(
                      spaceProperty.getMinSpaces(), spaceProperty.getMaxSpaces(), spaceProperty.getMinLineFeeds(),
                      spaceProperty.isReadOnly(),
                      spaceProperty.isSafe(), newKeepLineBreaksFlag, newKeepLineBreaks, false, spaceProperty.getPrefLineFeeds()
                    )
                  );
                }
              }
            }
          }
        }
        current = current.getNextBlock();
      }
      processor.format(model);
    }
    finally {
      enableFormatting();
    }
  }

  public void adjustTextRange(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final TextRange affectedRange) {
    disableFormatting();
    try {
      final FormatProcessor processor = buildProcessorAndWrapBlocks(
        model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, new FormatTextRanges(affectedRange, true)
      );
      LeafBlockWrapper current = processor.getFirstTokenBlock();
      while (current != null) {
        WhiteSpace whiteSpace = current.getWhiteSpace();

        if (!whiteSpace.isReadOnly()) {
          if (whiteSpace.getStartOffset() > affectedRange.getStartOffset()) {
            whiteSpace.setReadOnly(true);
          }
          else {
            whiteSpace.setReadOnly(false);
          }
        }
        current = current.getNextBlock();
      }
      processor.format(model);
    }
    finally {
      enableFormatting();
    }
  }

  public void saveIndents(final FormattingModel model, final TextRange affectedRange,
                          IndentInfoStorage storage,
                          final CodeStyleSettings settings,
                          final CodeStyleSettings.IndentOptions indentOptions) {
    final Block block = model.getRootBlock();
    
    final FormatProcessor processor = buildProcessorAndWrapBlocks(
      model.getDocumentModel(), block, settings, indentOptions, new FormatTextRanges(affectedRange, true)
    );
    LeafBlockWrapper current = processor.getFirstTokenBlock();
    while (current != null) {
      WhiteSpace whiteSpace = current.getWhiteSpace();

      if (!whiteSpace.isReadOnly() && whiteSpace.containsLineFeeds()) {
        storage.saveIndentInfo(current.calcIndentFromParent(), current.getStartOffset());
      }
      current = current.getNextBlock();
    }
  }

  public FormattingModel createFormattingModelForPsiFile(final PsiFile file,
                                                         @NotNull final Block rootBlock,
                                                         final CodeStyleSettings settings) {
    return new PsiBasedFormattingModel(file, rootBlock, FormattingDocumentModelImpl.createOn(file));
  }

  public Indent getSpaceIndent(final int spaces, final boolean relative) {
    return new IndentImpl(Indent.Type.SPACES, false, spaces, relative, false);
  }

  @Override
  public Indent getIndent(@NotNull Indent.Type type, boolean relativeToDirectParent, boolean enforceIndent) {
    return new IndentImpl(type, false, 0, relativeToDirectParent, enforceIndent);
  }

  public Indent getAbsoluteLabelIndent() {
    return myAbsoluteLabelIndent;
  }

  public Spacing createSafeSpacing(final boolean shouldKeepLineBreaks, final int keepBlankLines) {
    return getSpacingImpl(0, 0, 0, false, true, shouldKeepLineBreaks, keepBlankLines, false, 0);
  }

  public Spacing createKeepingFirstColumnSpacing(final int minSpace,
                                                 final int maxSpace,
                                                 final boolean keepLineBreaks,
                                                 final int keepBlankLines) {
    return getSpacingImpl(minSpace, maxSpace, -1, false, false, keepLineBreaks, keepBlankLines, true, 0);
  }

  public Spacing createSpacing(final int minSpaces, final int maxSpaces, final int minLineFeeds, final boolean keepLineBreaks, final int keepBlankLines,
                               final int prefLineFeeds) {
    return getSpacingImpl(minSpaces, maxSpaces, -1, false, false, keepLineBreaks, keepBlankLines, false, prefLineFeeds);
  }

  private final Map<SpacingImpl,SpacingImpl> ourSharedProperties = new HashMap<SpacingImpl,SpacingImpl>();
  private final SpacingImpl ourSharedSpacing = new SpacingImpl(-1,-1,-1,false,false,false,-1,false,0);

  private SpacingImpl getSpacingImpl(final int minSpaces, final int maxSpaces, final int minLineFeeds, final boolean readOnly, final boolean safe,
                                     final boolean keepLineBreaksFlag,
                                     final int keepLineBreaks,
                                     final boolean keepFirstColumn, int prefLineFeeds) {
    synchronized(this) {
      ourSharedSpacing.init(minSpaces, maxSpaces, minLineFeeds, readOnly, safe, keepLineBreaksFlag, keepLineBreaks, keepFirstColumn, prefLineFeeds);
      SpacingImpl spacing = ourSharedProperties.get(ourSharedSpacing);

      if (spacing == null) {
        spacing = new SpacingImpl(minSpaces, maxSpaces, minLineFeeds, readOnly, safe, keepLineBreaksFlag, keepLineBreaks, keepFirstColumn, prefLineFeeds);
        ourSharedProperties.put(spacing, spacing);
      }
      return spacing;
    }
  }

  @NotNull
  public String getComponentName() {
    return "FormatterEx";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Indent getAbsoluteNoneIndent() {
    return myAbsoluteNoneIndent;
  }

  public Indent getLabelIndent() {
    return myLabelIndent;
  }

  public Indent getContinuationIndent(boolean relative) {
    return relative ? myContinuationIndentRelativeToDirectParent : myContinuationIndentNotRelativeToDirectParent;
  }

  //is default
  public Indent getContinuationWithoutFirstIndent(boolean relative) {
    return relative ? myContinuationWithoutFirstIndentRelativeToDirectParent : myContinuationWithoutFirstIndentNotRelativeToDirectParent;
  }

  public boolean isDisabled() {
    return myIsDisabledCount.get() > 0;
  }

  private void disableFormatting() {
    myIsDisabledCount.incrementAndGet();
  }

  private void enableFormatting() {
    int old = myIsDisabledCount.getAndDecrement();
    if (old <= 0) {
      LOG.error("enableFormatting()/disableFormatting() not paired. DisabledLevel = " + old);
    }
  }

  @Nullable
  public <T> T runWithFormattingDisabled(@NotNull Computable<T> runnable) {
    disableFormatting();
    try {
      return runnable.compute();
    }
    finally {
      enableFormatting();
    }
  }
  
  private abstract static class MyFormattingTask implements SequentialTask {
    private FormatProcessor myProcessor;
    private boolean         myDone;
    
    @Override
    public void prepare() {
      myProcessor = buildProcessor();
    }

    @Override
    public boolean isDone() {
      return myDone;
    }

    @Override
    public boolean iteration() {
      return myDone = myProcessor.iteration();
    }

    @Override
    public void stop() {
      myProcessor.stopSequentialProcessing();
      myDone = true;
    }

    @NotNull
    protected abstract FormatProcessor buildProcessor();
  }
}
