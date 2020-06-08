// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementTypeBase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;

/**
 * @author peter
 */
public class TemplateDataElementType extends IFileElementType implements ITemplateDataElementType {
  public static final LanguageExtension<TreePatcher> TREE_PATCHER =
    new LanguageExtension<>("com.intellij.lang.treePatcher", new SimpleTreePatcher());

  public static final Key<RangeCollector> OUTER_ELEMENT_RANGES = Key.create("template.parser.outer.element.handler");

  @NotNull private final IElementType myTemplateElementType;
  @NotNull final IElementType myOuterElementType;

  public TemplateDataElementType(@NonNls String debugName,
                                 Language language,
                                 @NotNull IElementType templateElementType,
                                 @NotNull IElementType outerElementType) {
    super(debugName, language);
    myTemplateElementType = templateElementType;
    myOuterElementType = outerElementType;
  }

  protected Lexer createBaseLexer(TemplateLanguageFileViewProvider viewProvider) {
    return LanguageParserDefinitions.INSTANCE.forLanguage(viewProvider.getBaseLanguage())
                                             .createLexer(viewProvider.getManager().getProject());
  }

  protected LanguageFileType createTemplateFakeFileType(final Language language) {
    return new TemplateFileType(language);
  }

  @Override
  public ASTNode parseContents(@NotNull ASTNode chameleon) {
    final CharTable charTable = SharedImplUtil.findCharTableByTree(chameleon);
    final FileElement fileElement = TreeUtil.getFileElement((TreeElement)chameleon);
    final PsiFile psiFile = (PsiFile)fileElement.getPsi();
    PsiFile originalPsiFile = psiFile.getOriginalFile();

    final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)originalPsiFile.getViewProvider();

    final Language templateLanguage = getTemplateFileLanguage(viewProvider);
    final CharSequence sourceCode = chameleon.getChars();

    RangeCollector collector = new RangeCollector(this);
    final PsiFile templatePsiFile = createTemplateFile(psiFile, templateLanguage, sourceCode, viewProvider, collector);
    final FileElement templateFileElement = ((PsiFileImpl)templatePsiFile).calcTreeElement();
    collector.fillRangeToRemoveTexts(templatePsiFile);

    return DebugUtil.performPsiModification("template language parsing", () -> {
      collector.insertOuterElementsAndRemoveRanges(templateFileElement, sourceCode, charTable, templateFileElement.getPsi().getLanguage());

      TreeElement childNode = templateFileElement.getFirstChildNode();

      DebugUtil.checkTreeStructure(templateFileElement);
      DebugUtil.checkTreeStructure(chameleon);
      if (fileElement != chameleon) {
        DebugUtil.checkTreeStructure(psiFile.getNode());
        DebugUtil.checkTreeStructure(originalPsiFile.getNode());
      }

      return childNode;
    });
  }

  protected Language getTemplateFileLanguage(TemplateLanguageFileViewProvider viewProvider) {
    return viewProvider.getTemplateDataLanguage();
  }

  /**
   * Creates psi tree without template tokens. The result PsiFile can contain additional elements.
   * Ranges of the removed tokens/additional elements should be stored in the rangeCollector
   *
   * @param psiFile          chameleon's psi file
   * @param templateLanguage template language to parse
   * @param sourceCode       source code: base language with template language
   * @param rangeCollector   collector for ranges with non-template/additional elements
   * @return template psiFile
   */
  protected PsiFile createTemplateFile(final PsiFile psiFile,
                                       final Language templateLanguage,
                                       final CharSequence sourceCode,
                                       final TemplateLanguageFileViewProvider viewProvider,
                                       @NotNull TemplateDataElementType.RangeCollector rangeCollector) {
    final CharSequence templateSourceCode = createTemplateText(sourceCode, createBaseLexer(viewProvider), rangeCollector);
    return createPsiFileFromSource(templateLanguage, templateSourceCode, psiFile.getManager());
  }

  /**
   * Creates source code without template tokens. May add additional pieces of code.
   * Ranges of such additions should be added in rangeCollector using {@link RangeCollector#addRangeToRemove(TextRange)}for later removal from the resulting tree
   *
   * @param sourceCode     source code with base and template languages
   * @param baseLexer      base language lexer
   * @param rangeCollector collector for ranges with non-template/additional symbols
   * @return template source code
   */
  protected CharSequence createTemplateText(@NotNull CharSequence sourceCode,
                                            @NotNull Lexer baseLexer,
                                            @NotNull TemplateDataElementType.RangeCollector rangeCollector) {
    StringBuilder result = new StringBuilder(sourceCode.length());
    baseLexer.start(sourceCode);

    TextRange currentRange = TextRange.EMPTY_RANGE;
    while (baseLexer.getTokenType() != null) {
      TextRange newRange = TextRange.create(baseLexer.getTokenStart(), baseLexer.getTokenEnd());
      assert currentRange.getEndOffset() == newRange.getStartOffset() :
        "Inconsistent tokens stream from " + baseLexer +
        ": " + getRangeDump(currentRange, sourceCode) + " followed by " + getRangeDump(newRange, sourceCode);
      currentRange = newRange;
      if (baseLexer.getTokenType() == myTemplateElementType) {
        appendCurrentTemplateToken(result, sourceCode, baseLexer, rangeCollector);
      }
      else {
        rangeCollector.addOuterRange(currentRange, false);
      }
      baseLexer.advance();
    }

    return result;
  }

  @NotNull
  private static String getRangeDump(@NotNull TextRange range, @NotNull CharSequence sequence) {
    return "'" + StringUtil.escapeLineBreak(range.subSequence(sequence).toString()) + "' " + range;
  }

  protected void appendCurrentTemplateToken(@NotNull StringBuilder result,
                                            @NotNull CharSequence buf,
                                            @NotNull Lexer lexer,
                                            @NotNull TemplateDataElementType.RangeCollector collector) {
    result.append(buf, lexer.getTokenStart(), lexer.getTokenEnd());
  }

  protected OuterLanguageElementImpl createOuterLanguageElement(@NotNull CharSequence internedTokenText,
                                                                @NotNull IElementType outerElementType) {
    return new OuterLanguageElementImpl(outerElementType, internedTokenText);
  }

  protected PsiFile createPsiFileFromSource(final Language language, CharSequence sourceCode, PsiManager manager) {
    @NonNls final LightVirtualFile virtualFile =
      new LightVirtualFile("foo", createTemplateFakeFileType(language), sourceCode, LocalTimeCounter.currentTime());

    FileViewProvider viewProvider = new SingleRootFileViewProvider(manager, virtualFile, false) {
      @Override
      @NotNull
      public Language getBaseLanguage() {
        return language;
      }
    };

    // Since we're already inside a template language PSI that was built regardless of the file size (for whatever reason),
    // there should also be no file size checks for template data files.
    SingleRootFileViewProvider.doNotCheckFileSizeLimit(virtualFile);

    return viewProvider.getPsi(language);
  }

  protected static class TemplateFileType extends LanguageFileType {
    private final Language myLanguage;

    protected TemplateFileType(final Language language) {
      super(language);
      myLanguage = language;
    }

    @Override
    @NotNull
    public String getDefaultExtension() {
      return "";
    }

    @Override
    @NotNull
    @NonNls
    public String getDescription() {
      return "fake for language" + myLanguage.getID();
    }

    @Override
    @Nullable
    public Icon getIcon() {
      return null;
    }

    @Override
    @NotNull
    @NonNls
    public String getName() {
      return myLanguage.getID();
    }
  }

  /**
   * This collector is used for storing ranges of outer elements and ranges of artificial elements, that should be stripped from the resulting tree
   * At the time of creating source code for the data language we need to memorize positions with template language elements.
   * For such positions we use {@link RangeCollector#addOuterRange}
   * Sometimes to build a correct tree we need to insert additional symbols into resulting source:
   * e.g. put an identifier instead of the base language fragment: {@code something={% $var %}} => {@code something=dummyidentifier}
   * that must be removed after building the tree.
   * For such additional symbols {@link RangeCollector#addRangeToRemove} must be used
   *
   * @apiNote Please note that all start offsets for the ranges must be in terms of "original source code". So, outer ranges are ranges
   * of outer elements in original source code. Ranges to remove don't correspond to any text range neither in original nor in modified text.
   * But their start offset is the offset in original text, and length is the length of inserted dummy identifier.
   */
  public static class RangeCollector {
    private final TemplateDataElementType myTemplateDataElementType;
    private final List<TextRange> myOuterAndRemoveRanges;

    public RangeCollector(@NotNull TemplateDataElementType templateDataElementType) {
      this(templateDataElementType, new ArrayList<>());
    }

    private RangeCollector(@NotNull TemplateDataElementType templateDataElementType, @NotNull List<TextRange> outerAndRemoveRanges) {
      myTemplateDataElementType = templateDataElementType;
      myOuterAndRemoveRanges = outerAndRemoveRanges;
    }

    /**
     * @deprecated Use {@link #addOuterRange(TextRange, boolean)} instead.
     */
    @Deprecated
    public void addOuterRange(@NotNull TextRange newRange) {
      addOuterRange(newRange, false);
    }

    /**
     * Adds range corresponding to the outer element inside original source code.
     * After building the data template tree these ranges will be used for inserting outer language elements
     *
     * @param isInsertion <tt>true</tt> if element is expected to insert some text into template data fragment. For example, PHP's
     *                    <code><?= $myVar ?></code> are insertions, while <code><?php foo() ?></code> are not.
     */
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

    /**
     * Adds the fragment that must be removed from the tree on the stage inserting outer elements.
     * This method should be called after adding "fake" symbols inside the data language text for building syntax correct tree
     */
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

    private void fillRangeToRemoveTexts(@NotNull PsiFile file) {
      boolean hasRangeToRemove = false;
      for (TextRange range : myOuterAndRemoveRanges) {
        if (range instanceof RangeToRemove) {
          hasRangeToRemove = true;
          break;
        }
      }
      if (!hasRangeToRemove) return;

      String text = file.getText();
      int shift = 0;
      ListIterator<TextRange> iterator = myOuterAndRemoveRanges.listIterator();
      while (iterator.hasNext()) {
        TextRange range = iterator.next();
        if (range instanceof RangeToRemove) {
          CharSequence insertedString = text.subSequence(range.getStartOffset() - shift, range.getEndOffset() - shift);
          iterator.set(new RangeToRemove(range.getStartOffset(), insertedString));
          shift -= range.getLength();
        }
        else {
          shift += range.getLength();
        }
      }
    }

    /**
     * Builds the merged tree with inserting outer language elements and removing additional elements according to the ranges from rangeCollector
     *
     * @param language
     * @param templateFileElement parsed template data language file without outer elements and with possible custom additions
     * @param sourceCode          original source code (include template data language and template language)
     */
    public void insertOuterElementsAndRemoveRanges(@NotNull TreeElement templateFileElement,
                                                   @NotNull CharSequence sourceCode,
                                                   @NotNull CharTable charTable,
                                                   @NotNull Language language) {
      TreePatcher templateTreePatcher = TREE_PATCHER.forLanguage(language);

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

        boolean addRangeToLazyParseableCollector = false;
        if (rangeToProcess instanceof RangeToRemove) {
          assert currentLeafOrLazyParseable != null;
          currentLeafOrLazyParseable =
            removeElementsForRange(currentLeafOrLazyParseable, currentLeafOffset, rangeToProcess, templateTreePatcher, charTable);
          if (currentLeafOrLazyParseable != null &&
              !(currentLeafOrLazyParseable instanceof LeafElement) &&
              ((RangeToRemove)rangeToProcess).myTextToRemove != null) {
            addRangeToLazyParseableCollector = true;
          }
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
            if (!(currentLeafOrLazyParseable instanceof LeafElement)) {
              addRangeToLazyParseableCollector = true;
            }
          }
        }
        if (addRangeToLazyParseableCollector) {
          RangeCollector lazyParseableCollector =
            currentLeafOrLazyParseable.getUserData(OUTER_ELEMENT_RANGES);
          assert lazyParseableCollector != null && lazyParseableCollector != this;
          lazyParseableCollector.myOuterAndRemoveRanges.add(rangeToProcess.shiftLeft(currentLeafOffset));
        }
      }

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        String after = templateFileElement.getText();
        assert after.contentEquals(sourceCode) :
          "Text presentation for the new tree must be the same: \nbefore: " + sourceCode + "\nafter: " + after;
      }
    }

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
             element.getElementType() instanceof TemplateAwareElementType;
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

    protected @NotNull TreeElement insertOuterElementFromRange(@NotNull TreeElement currentLeaf,
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
      TemplateAwareElementType elementType =
        (TemplateAwareElementType)currentLeaf.getElementType();
      TreeElement newElement = elementType.createTreeElement(text);
      RangeCollector collector = currentLeaf.getUserData(OUTER_ELEMENT_RANGES);
      if (collector == null) collector = new RangeCollector(myTemplateDataElementType);
      newElement.putUserData(OUTER_ELEMENT_RANGES, collector);
      return newElement;
    }

    @Nullable
    public TreeElement removeElementsForRange(@NotNull TreeElement startLeaf,
                                              int startLeafOffset,
                                              @NotNull TextRange rangeToRemove,
                                              @NotNull TreePatcher templateTreePatcher,
                                              @NotNull CharTable charTable) {
      @Nullable TreeElement nextLeaf = startLeaf;
      int nextLeafStartOffset = startLeafOffset;
      Collection<TreeElement> leavesToRemove = new ArrayList<>();
      while (nextLeaf != null && rangeToRemove.containsRange(nextLeafStartOffset, nextLeafStartOffset + nextLeaf.getTextLength())) {
        leavesToRemove.add(nextLeaf);
        nextLeafStartOffset += nextLeaf.getTextLength();
        nextLeaf = findNextSuitableElement(nextLeaf);
      }

      nextLeaf = splitOrRemoveRangeInsideLeafIfOverlap(nextLeaf, nextLeafStartOffset, rangeToRemove, templateTreePatcher, charTable);

      for (TreeElement element : leavesToRemove) {
        element.rawRemove();
      }
      return nextLeaf;
    }


    /**
     * Removes part the nextLeaf that intersects rangeToRemove.
     * If nextLeaf doesn't intersect rangeToRemove the method returns the nextLeaf without changes
     *
     * @return new leaf after removing the range or original nextLeaf if nothing changed
     */
    @Nullable
    private TreeElement splitOrRemoveRangeInsideLeafIfOverlap(@Nullable TreeElement nextLeaf,
                                                              int nextLeafStartOffset,
                                                              @NotNull TextRange rangeToRemove,
                                                              @NotNull TreePatcher templateTreePatcher,
                                                              @NotNull CharTable charTable) {
      if (nextLeaf == null) return null;
      if (nextLeafStartOffset >= rangeToRemove.getEndOffset()) return nextLeaf;

      if (rangeToRemove.getStartOffset() > nextLeafStartOffset) {
        return removeRange(nextLeaf, rangeToRemove.shiftLeft(nextLeafStartOffset), charTable);
      }

      int offsetToSplit = rangeToRemove.getEndOffset() - nextLeafStartOffset;
      return removeLeftPartOfLeaf(nextLeaf, offsetToSplit, templateTreePatcher, charTable);
    }

    /**
     * Splits the node according to the offsetToSplit and remove left leaf
     *
     * @return right part of the split node
     */
    @NotNull
    private TreeElement removeLeftPartOfLeaf(@NotNull TreeElement nextLeaf,
                                             int offsetToSplit,
                                             @NotNull TreePatcher templateTreePatcher,
                                             @NotNull CharTable charTable) {
      if (offsetToSplit == 0) return nextLeaf;
      if (!(nextLeaf instanceof LeafElement)) {
        return removeRange(nextLeaf, TextRange.from(0, offsetToSplit), charTable);
      }
      LeafElement rLeaf = templateTreePatcher.split((LeafElement)nextLeaf, offsetToSplit, charTable);
      LeafElement lLeaf = (LeafElement)TreeUtil.prevLeaf(rLeaf);
      assert lLeaf != null;
      lLeaf.rawRemove();
      return rLeaf;
    }

    public @NotNull ASTNode replaceOuterElementsInLazyParseable(@NotNull ASTNode chameleon,
                                                                @NotNull String dummyString,
                                                                @NotNull Language language,
                                                                @NotNull Function<@NotNull CharSequence, @NotNull ASTNode> parser) {
      CharSequence chars = chameleon.getChars();
      if (myOuterAndRemoveRanges.isEmpty()) return parser.apply(chars);

      StringBuilder stringBuilder = new StringBuilder(chars);
      int shift = 0;
      int dummyStringLength = dummyString.length();
      // copy to prevent ConcurrentModificationException
      LinkedList<TextRange> copiedRanges = new LinkedList<>(myOuterAndRemoveRanges);
      ListIterator<TextRange> iterator = copiedRanges.listIterator();
      while (iterator.hasNext()) {
        TextRange outerElementRange = iterator.next();
        if (outerElementRange instanceof InsertionRange) {
          // don't pass dummyString to RangeToRemove's constructor so it won't be applied to nested lazy parseables
          iterator.add(new RangeToRemove(outerElementRange.getEndOffset(), outerElementRange.getEndOffset() + dummyStringLength));
          stringBuilder.replace(outerElementRange.getStartOffset() + shift,
                                outerElementRange.getEndOffset() + shift,
                                dummyString);
          shift += dummyStringLength - outerElementRange.getLength();
        }
        else if (outerElementRange instanceof RangeToRemove) {
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

      ASTNode root = parser.apply(stringBuilder.toString());
      RangeCollector copiedCollector = new RangeCollector(myTemplateDataElementType, copiedRanges);
      DebugUtil.performPsiModification("lazy parseable outer elements insertion", () -> {
        copiedCollector.insertOuterElementsAndRemoveRanges((TreeElement)root, chars, SharedImplUtil.findCharTableByTree(chameleon), language);
      });

      return root;
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

    private final static class RangeToRemove extends TextRange {
      /**
       * We need this text to propagate dummy strings through lazy parseables. If this text is null, dummy identifier won't be propagated.
       */
      public final @Nullable CharSequence myTextToRemove;

      private RangeToRemove(int startOffset, @NotNull CharSequence text) {
        super(startOffset, startOffset + text.length());
        myTextToRemove = text;
      }

      private RangeToRemove(int startOffset, int endOffset) {
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
    }

    private static final class InsertionRange extends TextRange {

      private InsertionRange(int startOffset, int endOffset) {
        super(startOffset, endOffset);
      }

      @Override
      public @NotNull TextRange shiftLeft(int delta) {
        if (delta == 0) return this;
        return new InsertionRange(getStartOffset() - delta, getEndOffset() - delta);
      }
    }
  }


  /**
   * Marker interface for element types which handle outer language elements themselves in
   * {@link ILazyParseableElementTypeBase#parseContents(ASTNode)} method.
   *
   * {@link RangeCollector#replaceOuterElementsInLazyParseable(ASTNode, String, Language, Function)} may be used for this.
   * Ranges are stored in element's user data by {@link #OUTER_ELEMENT_RANGES} key.
   */
  public interface TemplateAwareElementType extends ILazyParseableElementTypeBase {
    @NotNull TreeElement createTreeElement(@NotNull CharSequence text);
  }
}
