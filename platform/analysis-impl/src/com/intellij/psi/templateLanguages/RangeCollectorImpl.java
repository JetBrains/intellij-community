// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.templateLanguages.TemplateDataElementType.OuterLanguageRangePatcher;
import com.intellij.util.CharTable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;

public class RangeCollectorImpl extends TemplateDataElementType.RangeCollector {
  private final TemplateDataElementType myTemplateDataElementType;
  private final List<TextRange> myOuterAndRemoveRanges;

  static final Key<RangeCollectorImpl> OUTER_ELEMENT_RANGES = Key.create("template.parser.outer.element.handler");

  RangeCollectorImpl(@NotNull TemplateDataElementType templateDataElementType) {
    this(templateDataElementType, new ArrayList<>());
  }

  private RangeCollectorImpl(@NotNull TemplateDataElementType templateDataElementType, @NotNull List<TextRange> outerAndRemoveRanges) {
    myTemplateDataElementType = templateDataElementType;
    myOuterAndRemoveRanges = outerAndRemoveRanges;
  }

  @Override
  public void addOuterRange(@NotNull TextRange newRange) {
    addOuterRange(newRange, false);
  }

  @Override
  public void addOuterRange(@NotNull TextRange newRange, boolean isInsertion) {
    if (newRange.isEmpty()) {
      return;
    }
    assertRangeOrder(newRange);

    if (!myOuterAndRemoveRanges.isEmpty()) {
      int lastItemIndex = myOuterAndRemoveRanges.size() - 1;
      TextRange lastRange = myOuterAndRemoveRanges.get(lastItemIndex);
      if (lastRange.getEndOffset() == newRange.getStartOffset() && !(lastRange instanceof RangeToRemove)) {
        TextRange joinedRange =
          lastRange instanceof InsertionRange || isInsertion
          ? new InsertionRange(lastRange.getStartOffset(), newRange.getEndOffset())
          : TextRange.create(lastRange.getStartOffset(), newRange.getEndOffset());
        myOuterAndRemoveRanges.set(lastItemIndex, joinedRange);
        return;
      }
    }
    myOuterAndRemoveRanges.add(isInsertion ? new InsertionRange(newRange.getStartOffset(), newRange.getEndOffset()) : newRange);
  }

  @Override
  public void addRangeToRemove(@NotNull TextRange rangeToRemove) {
    if (rangeToRemove.isEmpty()) {
      return;
    }
    assertRangeOrder(rangeToRemove);

    myOuterAndRemoveRanges.add(new RangeToRemove(rangeToRemove.getStartOffset(), rangeToRemove.getEndOffset()));
  }

  private void assertRangeOrder(@NotNull TextRange newRange) {
    TextRange range = ContainerUtil.getLastItem(myOuterAndRemoveRanges);
    assert range == null || newRange.getStartOffset() >= range.getStartOffset();
  }

  void prepareFileForParsing(@NotNull Language templateLanguage,
                             @NotNull CharSequence originalSourceCode,
                             @NotNull CharSequence templateSourceCode) {
    addDummyStringsToRangesToRemove(templateSourceCode);
    OuterLanguageRangePatcher
      patcher = OuterLanguageRangePatcher.EXTENSION.forLanguage(templateLanguage);
    if (patcher != null) {
      StringBuilder builder =
        templateSourceCode instanceof StringBuilder ? (StringBuilder)templateSourceCode : new StringBuilder(templateSourceCode);
      insertDummyStringIntoInsertionRanges(patcher, originalSourceCode, builder);
    }
  }

  /**
   * Sets {@link RangeToRemove#myTextToRemove} in {@link #myOuterAndRemoveRanges} from generated file,
   * so lazy parseables implementing {@link TemplateDataElementType.TemplateAwareElementType} will have dummy strings when they are parsed.
   */
  private void addDummyStringsToRangesToRemove(@NotNull CharSequence generatedText) {
    int shift = 0;
    ListIterator<TextRange> iterator = myOuterAndRemoveRanges.listIterator();
    while (iterator.hasNext()) {
      TextRange range = iterator.next();
      if (range instanceof RangeToRemove) {
        CharSequence insertedString = generatedText.subSequence(range.getStartOffset() - shift, range.getEndOffset() - shift);
        iterator.set(new RangeToRemove(range.getStartOffset(), insertedString)); // only add insertedString
        shift -= range.getLength();
      }
      else {
        shift += range.getLength();
      }
    }
  }

  private void insertDummyStringIntoInsertionRanges(@NotNull OuterLanguageRangePatcher patcher,
                                                    @NotNull CharSequence originalSourceCode,
                                                    @NotNull StringBuilder modifiedText) {
    if (myOuterAndRemoveRanges.isEmpty()) return;

    int shift = 0;
    ListIterator<TextRange> iterator = myOuterAndRemoveRanges.listIterator();
    while (iterator.hasNext()) {
      TextRange outerElementRange = iterator.next();
      if (outerElementRange instanceof RangeToRemove) {
        shift += outerElementRange.getLength();
      }
      else {
        if (outerElementRange instanceof InsertionRange) {
          String dummyString = patcher.getTextForOuterLanguageInsertionRange(
            myTemplateDataElementType,
            outerElementRange.subSequence(originalSourceCode));
          if (dummyString != null) {
            // Don't set RangeToRemove#myTextToRemove so it won't be applied to nested lazy parseables.
            // Nested lazy parseables may add dummy string themselves.
            iterator.add(new RangeToRemove(outerElementRange.getEndOffset(), outerElementRange.getEndOffset() + dummyString.length()));
            modifiedText.insert(outerElementRange.getStartOffset() + shift, dummyString);
            shift += dummyString.length();
          }
        }
        shift -= outerElementRange.getLength();
      }
    }
  }

  /**
   * Builds the merged tree with inserting outer language elements and removing additional elements according to the ranges from rangeCollector.
   * Chameleons which implement {@link TemplateDataElementType.TemplateAwareElementType} are treated as leaves, i.e. they are not expanded
   * but their contents are modified considering outer elements and RangeCollector is put to their user data to be applied when this
   * chameleon is expanded.
   *
   * @param templateFileElement parsed template data language file without outer elements and with possible custom additions
   * @param sourceCode          original source code (include template data language and template language)
   */
  void insertOuterElementsAndRemoveRanges(@NotNull TreeElement templateFileElement,
                                          @NotNull CharSequence sourceCode,
                                          @NotNull CharTable charTable,
                                          @NotNull Language language) {
    TreePatcher templateTreePatcher = TemplateDataElementType.TREE_PATCHER.forLanguage(language);

    TreeElement currentLeafOrLazyParseable = findFirstSuitableElement(templateFileElement);

    // we use manual offset counter because node.getStartOffset() is expensive here
    // offset in original text
    int currentLeafOffset = 0;

    for (TextRange rangeToProcess : myOuterAndRemoveRanges) {
      int rangeStartOffset = rangeToProcess.getStartOffset();

      // search for leaf following or intersecting range
      while (currentLeafOrLazyParseable != null &&
             currentLeafOffset + currentLeafOrLazyParseable.getTextLength() <= rangeStartOffset) {
        currentLeafOffset += currentLeafOrLazyParseable.getTextLength();
        currentLeafOrLazyParseable = findNextSuitableElement(currentLeafOrLazyParseable);
      }

      if (rangeToProcess instanceof RangeToRemove) {
        if (currentLeafOrLazyParseable == null) {
          Logger.getInstance(RangeCollectorImpl.class).error(
            "RangeToRemove's range is out of original text bound",
            new Attachment("myOuterAndRemoveRanges", StringUtil.join(myOuterAndRemoveRanges, Object::toString, ", ")),
            new Attachment("rangeToProcess", rangeToProcess.toString()),
            new Attachment("sourceCode", sourceCode.toString()));
          continue;
        }

        if (currentLeafOffset > rangeToProcess.getStartOffset() ||
            currentLeafOffset + currentLeafOrLazyParseable.getTextLength() < rangeToProcess.getStartOffset()) {
          Logger.getInstance(RangeCollectorImpl.class).error("startLeaf doesn't contain rangeToRemove start offset");
          continue;
        }

        // don't modify currentLeafOffset as it stays the same as in original file because we are removing artificial insertion
        // currentLeafOrLazyParseable changes because leaf is changed and old one is invalidated
        currentLeafOrLazyParseable = removeElementsForRange(currentLeafOrLazyParseable, currentLeafOffset, rangeToProcess, charTable);
      }
      else {
        if (currentLeafOrLazyParseable instanceof LeafElement && currentLeafOffset < rangeStartOffset) {
          int splitOffset = rangeStartOffset - currentLeafOffset;
          currentLeafOrLazyParseable = templateTreePatcher.split((LeafElement)currentLeafOrLazyParseable, splitOffset, charTable);
          currentLeafOffset = rangeStartOffset;
        }
        if (currentLeafOrLazyParseable == null) {
          insertLastOuterElementForRange((CompositeElement)templateFileElement, rangeToProcess, sourceCode, charTable);
        }
        else {
          currentLeafOrLazyParseable =
            insertOuterElementFromRange(currentLeafOrLazyParseable, currentLeafOffset, rangeToProcess, sourceCode, templateTreePatcher,
                                        charTable);
          addRangeToLazyParseableCollector(currentLeafOrLazyParseable, rangeToProcess.shiftLeft(currentLeafOffset));
        }
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String after = templateFileElement.getText();
      assert after.contentEquals(sourceCode) :
        "Text presentation for the new tree must be the same: \nbefore: " + sourceCode + "\nafter: " + after;
    }
  }

  private void addRangeToLazyParseableCollector(@NotNull TreeElement leafOrLazyParseable, @NotNull TextRange rangeWithinLazyParseable) {
    if (rangeWithinLazyParseable instanceof RangeToRemove && ((RangeToRemove)rangeWithinLazyParseable).myTextToRemove == null) return;
    RangeCollectorImpl lazyParseableCollector = leafOrLazyParseable.getUserData(OUTER_ELEMENT_RANGES);
    if (lazyParseableCollector != null) {
      assert lazyParseableCollector != this;
      lazyParseableCollector.myOuterAndRemoveRanges.add(rangeWithinLazyParseable);
    }
  }

  /**
   * Similar to {@link TreeUtil#findFirstLeaf(ASTNode)}, but also treats collapsed lazy parseable elements as leaves and returns them.
   */
  private static @Nullable TreeElement findFirstSuitableElement(@NotNull ASTNode element) {
    if (isSuitableElement(element)) {
      return (TreeElement)element;
    }
    else {
      for (ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        TreeElement leaf = findFirstSuitableElement(child);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  /**
   * Similar to {@link TreeUtil#nextLeaf(ASTNode)}, but also treats collapsed lazy parseable elements as leaves and returns them.
   */
  private static @Nullable TreeElement findNextSuitableElement(@NotNull TreeElement start) {
    TreeElement element = start;
    while (element != null) {
      TreeElement nextTree = element;
      TreeElement next = null;
      while (next == null && (nextTree = nextTree.getTreeNext()) != null) {
        next = findFirstSuitableElement(nextTree);
      }
      if (next != null) {
        return next;
      }
      element = element.getTreeParent();
    }
    return null;
  }

  private static boolean isSuitableElement(@NotNull ASTNode element) {
    return element instanceof LeafElement ||
           TreeUtil.isCollapsedChameleon(element) &&
           element.getElementType() instanceof TemplateDataElementType.TemplateAwareElementType;
  }

  private void insertLastOuterElementForRange(@NotNull CompositeElement templateFileElement,
                                              @NotNull TextRange outerElementRange,
                                              @NotNull CharSequence sourceCode,
                                              @NotNull CharTable charTable) {
    assert isLastRange(myOuterAndRemoveRanges, outerElementRange) :
      "This should only happen for the last inserted range. Got " + myOuterAndRemoveRanges.lastIndexOf(outerElementRange) +
      " of " + (myOuterAndRemoveRanges.size() - 1);
    OuterLanguageElementImpl outerLanguageElement = myTemplateDataElementType
      .createOuterLanguageElement(charTable.intern(outerElementRange.subSequence(sourceCode)),
                                  myTemplateDataElementType.myOuterElementType);
    templateFileElement.rawAddChildren(outerLanguageElement);
  }

  private static boolean isLastRange(@NotNull List<TextRange> outerElementsRanges, @NotNull TextRange outerElementRange) {
    return outerElementsRanges.get(outerElementsRanges.size() - 1) == outerElementRange;
  }

  private @NotNull TreeElement insertOuterElementFromRange(@NotNull TreeElement currentLeaf,
                                                           int currentLeafOffset,
                                                           @NotNull TextRange outerElementRange,
                                                           @NotNull CharSequence sourceCode,
                                                           @NotNull TreePatcher templateTreePatcher,
                                                           @NotNull CharTable charTable) {
    CharSequence outerElementText = outerElementRange.subSequence(sourceCode);
    if (currentLeaf instanceof LazyParseableElement) {
      StringBuilder builder = new StringBuilder(currentLeaf.getText());
      builder.insert(outerElementRange.getStartOffset() - currentLeafOffset, outerElementText);
      TreeElement newElement = newLazyParseable(currentLeaf, builder.toString());
      currentLeaf.rawInsertAfterMe(newElement);
      currentLeaf.rawRemove();
      return newElement;
    }

    final OuterLanguageElementImpl newLeaf =
      myTemplateDataElementType.createOuterLanguageElement(charTable.intern(outerElementText), myTemplateDataElementType.myOuterElementType);
    templateTreePatcher.insert(currentLeaf.getTreeParent(), currentLeaf, newLeaf);
    return newLeaf;
  }

  @NotNull
  private TreeElement newLazyParseable(@NotNull TreeElement currentLeaf, @NotNull CharSequence text) {
    TemplateDataElementType.TemplateAwareElementType elementType =
      (TemplateDataElementType.TemplateAwareElementType)currentLeaf.getElementType();
    TreeElement newElement = elementType.createTreeElement(text);
    RangeCollectorImpl collector = currentLeaf.getUserData(OUTER_ELEMENT_RANGES);
    if (collector == null) collector = new RangeCollectorImpl(myTemplateDataElementType);
    newElement.putUserData(OUTER_ELEMENT_RANGES, collector);
    return newElement;
  }

  /**
   * @return null if all elements up to the end are removed, or a leaf which is in place of startLeaf after removal
   */
  @Nullable
  private TreeElement removeElementsForRange(@NotNull TreeElement startLeaf,
                                             int startLeafOffset,
                                             @NotNull TextRange rangeToRemove,
                                             @NotNull CharTable charTable) {
    TreeElement updatedStartLeaf = null;
    TreeElement currentLeaf = startLeaf;
    // leaf offset is in terms of RangeToRemove, i.e. it's start is position in original code and length is a length of artificial text
    int leafOffset = startLeafOffset;
    if (rangeToRemove.getEndOffset() > startLeafOffset + startLeaf.getTextLength()) {
      leafOffset += startLeaf.getTextLength();
      updatedStartLeaf = cutPartOfLeaf(startLeaf, startLeafOffset, rangeToRemove, charTable);
      currentLeaf = findNextSuitableElement(updatedStartLeaf);
    }

    while (currentLeaf != null && rangeToRemove.containsRange(leafOffset, leafOffset + currentLeaf.getTextLength())) {
      TreeElement leafToRemove = currentLeaf;
      leafOffset += currentLeaf.getTextLength();
      currentLeaf = findNextSuitableElement(currentLeaf);
      leafToRemove.rawRemove();
    }
    TreeElement leafAfterCompletelyRemoved = currentLeaf;

    TreeElement updatedLastLeaf = null;
    if (currentLeaf != null && leafOffset < rangeToRemove.getEndOffset()) {
      updatedLastLeaf = cutPartOfLeaf(currentLeaf, leafOffset, rangeToRemove, charTable);
    }

    return updatedStartLeaf != null ? updatedStartLeaf :
           updatedLastLeaf != null ? updatedLastLeaf :
           leafAfterCompletelyRemoved;
  }

  private @NotNull TreeElement cutPartOfLeaf(@NotNull TreeElement currentLeafOrLazyParseable,
                                             int currentLeafOffset,
                                             @NotNull TextRange rangeToProcess,
                                             @NotNull CharTable charTable) {
    TextRange intersection =
      rangeToProcess.intersection(TextRange.from(currentLeafOffset, currentLeafOrLazyParseable.getTextLength()));
    assert intersection != null;
    TextRange rangeWithinLeaf = intersection.shiftLeft(currentLeafOffset);
    currentLeafOrLazyParseable = removeRange(currentLeafOrLazyParseable, rangeWithinLeaf, charTable);
    addRangeToLazyParseableCollector(currentLeafOrLazyParseable, rangeWithinLeaf);
    return currentLeafOrLazyParseable;
  }

  /**
   * Like {@link TemplateDataElementType#parseContents} builds the tree considering outer language elements, but for inner lazy parseables.
   */
  @NotNull ASTNode applyRangeCollectorAndExpandChameleon(@NotNull ASTNode chameleon,
                                                         @NotNull Language language,
                                                         @NotNull Function<? super @NotNull CharSequence, ? extends @NotNull ASTNode> parser) {
    CharSequence chars = chameleon.getChars();
    if (myOuterAndRemoveRanges.isEmpty()) return parser.apply(chars);

    StringBuilder stringBuilder = applyOuterAndRemoveRanges(chars);

    OuterLanguageRangePatcher outerLanguageRangePatcher = OuterLanguageRangePatcher.EXTENSION.forLanguage(language);
    if (outerLanguageRangePatcher != null) {
      insertDummyStringIntoInsertionRanges(outerLanguageRangePatcher, chars, stringBuilder);
    }

    ASTNode root = parser.apply(stringBuilder.toString());
    DebugUtil.performPsiModification("lazy parseable outer elements insertion", () ->
      insertOuterElementsAndRemoveRanges((TreeElement)root, chars, SharedImplUtil.findCharTableByTree(chameleon), language));

    return root;
  }

  @NotNull
  private StringBuilder applyOuterAndRemoveRanges(CharSequence chars) {
    StringBuilder stringBuilder = new StringBuilder(chars);
    int shift = 0;
    for (TextRange outerElementRange : myOuterAndRemoveRanges) {
      if (outerElementRange instanceof RangeToRemove) {
        CharSequence textToRemove = ((RangeToRemove)outerElementRange).myTextToRemove;
        if (textToRemove != null) {
          stringBuilder.insert(outerElementRange.getStartOffset() + shift, textToRemove);
          shift += textToRemove.length();
        }
      }
      else {
        stringBuilder.delete(outerElementRange.getStartOffset() + shift,
                             outerElementRange.getEndOffset() + shift);
        shift -= outerElementRange.getLength();
      }
    }
    return stringBuilder;
  }

  /**
   * Removes "middle" part of the leaf and returns the new leaf with content of the right and left parts
   * e.g. if we process whitespace leaf " \n " and range "1, 2" the result will be new leaf with content "  "
   */
  @NotNull
  private TreeElement removeRange(@NotNull TreeElement leaf,
                                  @NotNull TextRange rangeToRemove,
                                  @NotNull CharTable table) {
    CharSequence chars = leaf.getChars();
    String res = rangeToRemove.replace(chars.toString(), "");
    TreeElement newLeaf =
      leaf instanceof LeafElement ? ASTFactory.leaf(leaf.getElementType(), table.intern(res)) : newLazyParseable(leaf, res);
    leaf.rawInsertBeforeMe(newLeaf);
    leaf.rawRemove();
    return newLeaf;
  }

  @NotNull CharSequence applyTemplateDataModifications(@NotNull CharSequence sourceCode, @NotNull TemplateDataModifications modifications) {
    assert myOuterAndRemoveRanges.isEmpty();
    List<TextRange> ranges = modifications.myOuterAndRemoveRanges;
    if (ranges.isEmpty()) return sourceCode;
    for (TextRange range : ranges) {
      if (range instanceof RangeToRemove) {
        if (range.isEmpty()) continue;
        assertRangeOrder(range);
        CharSequence textToRemove = ((RangeToRemove)range).myTextToRemove;
        assert textToRemove != null;
        myOuterAndRemoveRanges.add(new RangeToRemove(range.getStartOffset(), textToRemove));
      }
      else {
        addOuterRange(range, range instanceof InsertionRange);
      }
    }

    return applyOuterAndRemoveRanges(sourceCode);
  }


  final static class RangeToRemove extends TextRange {
    /**
     * We need this text to propagate dummy strings through lazy parseables. If this text is null, dummy identifier won't be propagated.
     */
    public final @Nullable CharSequence myTextToRemove;

    RangeToRemove(int startOffset, @NotNull CharSequence text) {
      super(startOffset, startOffset + text.length());
      myTextToRemove = text;
    }

    RangeToRemove(int startOffset, int endOffset) {
      super(startOffset, endOffset);
      myTextToRemove = null;
    }

    @Override
    public @NotNull TextRange shiftLeft(int delta) {
      if (delta == 0) return this;
      return myTextToRemove != null
             ? new RangeToRemove(getStartOffset() - delta, myTextToRemove)
             : new RangeToRemove(getStartOffset() - delta, getEndOffset() - delta);
    }

    @Override
    public @NotNull TextRange intersection(@NotNull TextRange range) {
      int newStart = Math.max(getStartOffset(), range.getStartOffset());
      int newEnd = Math.min(getEndOffset(), range.getEndOffset());
      assertProperRange(newStart, newEnd, "Invalid range");
      return myTextToRemove != null
             ? new RangeToRemove(newStart, myTextToRemove.subSequence(newStart - getStartOffset(), newEnd - getStartOffset()))
             : new RangeToRemove(newStart, newEnd);
    }

    @Override
    public String toString() {
      return "RangeToRemove" + super.toString();
    }
  }

  static final class InsertionRange extends TextRange {

    InsertionRange(int startOffset, int endOffset) {
      super(startOffset, endOffset);
    }

    @Override
    public @NotNull TextRange shiftLeft(int delta) {
      if (delta == 0) return this;
      return new InsertionRange(getStartOffset() - delta, getEndOffset() - delta);
    }

    @Override
    public @NotNull TextRange intersection(@NotNull TextRange range) {
      int newStart = Math.max(getStartOffset(), range.getStartOffset());
      int newEnd = Math.min(getEndOffset(), range.getEndOffset());
      assertProperRange(newStart, newEnd, "Invalid range");
      return new InsertionRange(newStart, newEnd);
    }

    @Override
    public String toString() {
      return "InsertionRange" + super.toString();
    }
  }
}