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

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class FormatterImpl extends FormatterEx
  implements ApplicationComponent,
             IndentFactory,
             WrapFactory,
             AlignmentFactory,
             SpacingFactory,
             FormattingModelFactory
{
  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.FormatterImpl");

  private int myIsDisabledCount = 0;
  private final IndentImpl NONE_INDENT = new IndentImpl(IndentImpl.Type.NONE, false);
  private final IndentImpl myAbsoluteNoneIndent = new IndentImpl(IndentImpl.Type.NONE, true);
  private final IndentImpl myLabelIndent = new IndentImpl(IndentImpl.Type.LABEL, false);
  private final IndentImpl myContinuationIndent = new IndentImpl(IndentImpl.Type.CONTINUATION, false);
  private final IndentImpl myContinutationWithoutFirstIndent = new IndentImpl(IndentImpl.Type.CONTINUATION_WITHOUT_FIRST, false);
  private final IndentImpl myAbsoluteLabelIndent = new IndentImpl(IndentImpl.Type.LABEL, true);
  private final IndentImpl myNormalIndent = new IndentImpl(IndentImpl.Type.NORMAL, false);
  private final SpacingImpl myReadOnlySpacing = new SpacingImpl(0, 0, 0, true, false, true, 0, false, 0);

  public FormatterImpl() {
    Indent.setFactory(this);
    Wrap.setFactory(this);
    Alignment.setFactory(this);
    Spacing.setFactory(this);
    FormattingModelProvider.setFactory(this);
  }

  public Alignment createAlignment(boolean applyToNonFirstBlocksOnLine) {
    return new AlignmentImpl(applyToNonFirstBlocksOnLine);
  }

  public Alignment createChildAlignment(final Alignment base) {
    AlignmentImpl result = new AlignmentImpl();
    result.setParent(base);
    return result;
  }

  public Indent getNormalIndent() {
    return myNormalIndent;
  }

  public Indent getNoneIndent() {
    return NONE_INDENT;
  }
  public void format(final FormattingModel model, final CodeStyleSettings settings,
                     final CodeStyleSettings.IndentOptions indentOptions,
                     final CodeStyleSettings.IndentOptions javaIndentOptions,
                     final FormatTextRanges affectedRanges) throws IncorrectOperationException {
    disableFormatting();
    try {
      FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions,
                                                      affectedRanges);
      processor.setJavaIndentOptions(javaIndentOptions);
      processor.format(model);
    } finally {
      enableFormatting();
    }

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

  public void format(FormattingModel model,
                     CodeStyleSettings settings,
                     CodeStyleSettings.IndentOptions indentOptions,
                     FormatTextRanges affectedRanges) throws IncorrectOperationException {
    disableFormatting();
    try {
      new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions, affectedRanges).format(model);
    } finally {
      enableFormatting();
    }

  }

  public void formatWithoutModifications(FormattingDocumentModel model,
                                         Block rootBlock,
                                         CodeStyleSettings settings,
                                         CodeStyleSettings.IndentOptions indentOptions,
                                         TextRange affectedRange) throws IncorrectOperationException {
    disableFormatting();
    try {
      new FormatProcessor(model, rootBlock, settings, indentOptions, new FormatTextRanges(affectedRange, true)).formatWithoutRealModifications();
    } finally {
      enableFormatting();
    }

  }

  public IndentInfo getWhiteSpaceBefore(final FormattingDocumentModel model,
                                        final Block block,
                                        final CodeStyleSettings settings,
                                        final CodeStyleSettings.IndentOptions indentOptions,
                                        final TextRange affectedRange, final boolean mayChangeLineFeeds) {
    disableFormatting();
    try {
      final FormatProcessor processor = new FormatProcessor(model, block, settings, indentOptions, new FormatTextRanges(affectedRange, true));
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
    } finally {
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
      final FormatProcessor processor = new FormatProcessor(documentModel, block, settings, indentOptions,
                                                            new FormatTextRanges(rangeToAdjust, true));
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
      final FormatProcessor processor = new FormatProcessor(documentModel, block, settings,
                                                            settings.getIndentOptions(fileType), null);
      LeafBlockWrapper tokenBlock = processor.getFirstTokenBlock();
      while (tokenBlock != null) {
        final WhiteSpace whiteSpace = tokenBlock.getWhiteSpace();

        if (whiteSpace.getEndOffset() < textRange.getStartOffset()) {
          whiteSpace.setIsReadOnly(true);
        } else if (whiteSpace.getStartOffset() > textRange.getStartOffset() &&
                   whiteSpace.getEndOffset() < textRange.getEndOffset()){
          if (whiteSpace.containsLineFeeds()) {
            whiteSpace.setLineFeedsAreReadOnly(true);
          } else {
            whiteSpace.setIsReadOnly(true);
          }
        } else if (whiteSpace.getEndOffset() > textRange.getEndOffset() + 1) {
          whiteSpace.setIsReadOnly(true);
        }

        tokenBlock = tokenBlock.getNextBlock();
      }
      processor.formatWithoutRealModifications();
      processor.performModifications(model);

    } finally{
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
      final FormatProcessor processor = new FormatProcessor(documentModel, block, settings, indentOptions,
                                                            new FormatTextRanges(affectedRange, true),
                                                            offset);

      final LeafBlockWrapper blockAfterOffset = processor.getBlockAfter(offset);

      if (blockAfterOffset != null && blockAfterOffset.contains(offset)) {
        return offset;
      }

      if (blockAfterOffset != null) {
        return adjustLineIndent(offset, documentModel, processor, indentOptions, model, blockAfterOffset.getWhiteSpace());
      } else {
        return adjustLineIndent(offset, documentModel, processor, indentOptions, model, processor.getLastWhiteSpace());
      }
    } finally {
      enableFormatting();
    }
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
      final int ws = whiteSpace.getStartOffset() + CharArrayUtil.shiftForward(newWS, lineStartOffset - whiteSpace.getStartOffset(), " \t");
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
    final FormatProcessor processor = new FormatProcessor(documentModel, block, settings, indentOptions,
                                                          new FormatTextRanges(affectedRange, true), offset);

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
      final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions,
                                                            new FormatTextRanges(affectedRange, true));
      LeafBlockWrapper current = processor.getFirstTokenBlock();
      while (current != null) {
        WhiteSpace whiteSpace = current.getWhiteSpace();

        if (!whiteSpace.isReadOnly()) {
          if (whiteSpace.getStartOffset() > affectedRange.getStartOffset()) {
            if (whiteSpace.containsLineFeeds() && indentInfoStorage != null) {
              whiteSpace.setLineFeedsAreReadOnly(true);
              current.setIndentFromParent(indentInfoStorage.getIndentInfo(current.getStartOffset()));
            } else {
              whiteSpace.setReadOnly(true);
            }
          } else {
            if (!changeWSBeforeFirstElement) {
              whiteSpace.setReadOnly(true);
            } else {
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
                      spaceProperty.getMinSpaces(), spaceProperty.getMaxSpaces(), spaceProperty.getMinLineFeeds(), spaceProperty.isReadOnly(),
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
    } finally {
      enableFormatting();
    }

  }

  public void adjustTextRange(final FormattingModel model,
                              final CodeStyleSettings settings,
                              final CodeStyleSettings.IndentOptions indentOptions,
                              final TextRange affectedRange) {
    disableFormatting();
    try {
      final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), model.getRootBlock(), settings, indentOptions,
                                                            new FormatTextRanges(affectedRange, true));
      LeafBlockWrapper current = processor.getFirstTokenBlock();
      while (current != null) {
        WhiteSpace whiteSpace = current.getWhiteSpace();

        if (!whiteSpace.isReadOnly()) {
          if (whiteSpace.getStartOffset() > affectedRange.getStartOffset()) {
            whiteSpace.setReadOnly(true);
          } else {
            whiteSpace.setReadOnly(false);
          }
        }
        current = current.getNextBlock();
      }
      processor.format(model);
    } finally {
      enableFormatting();
    }

  }

  public void saveIndents(final FormattingModel model, final TextRange affectedRange,
                          IndentInfoStorage storage,
                          final CodeStyleSettings settings,
                          final CodeStyleSettings.IndentOptions indentOptions) {
    final Block block = model.getRootBlock();
    final FormatProcessor processor = new FormatProcessor(model.getDocumentModel(), block, settings, indentOptions,
                                                          new FormatTextRanges(affectedRange, true));
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

  public Indent getSpaceIndent(final int spaces) {
    return new IndentImpl(IndentImpl.Type.SPACES, false, spaces);
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

  public Indent getContinuationIndent() {
    return myContinuationIndent;
  }

  public Indent getContinuationWithoutFirstIndent()//is default
  {
    return myContinutationWithoutFirstIndent;
  }

  private final Object DISABLING_LOCK = new Object();

  public boolean isDisabled() {
    synchronized (DISABLING_LOCK) {
      return myIsDisabledCount > 0;
    }
  }

  public void disableFormatting() {
    synchronized (DISABLING_LOCK) {
      myIsDisabledCount++;
    }
  }

  public void enableFormatting() {
    synchronized (DISABLING_LOCK) {
      if (myIsDisabledCount <= 0) {
        LOG.error("enableFormatting()/disableFormatting() not paired. DisabledLevel = " + myIsDisabledCount);
      }
      myIsDisabledCount--;
    }
  }
}
