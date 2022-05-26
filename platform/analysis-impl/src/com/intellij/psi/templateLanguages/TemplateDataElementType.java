// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementTypeBase;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * @author peter
 */
public class TemplateDataElementType extends IFileElementType implements ITemplateDataElementType {
  private static final Logger LOG = Logger.getInstance(TemplateDataElementType.class);
  private static final int CHECK_PROGRESS_AFTER_TOKENS = 1000;
  public static final LanguageExtension<TreePatcher> TREE_PATCHER =
    new LanguageExtension<>("com.intellij.lang.treePatcher", new SimpleTreePatcher());

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

    RangeCollectorImpl collector = new RangeCollectorImpl(this);
    final PsiFile templatePsiFile = createTemplateFile(psiFile, templateLanguage, sourceCode, viewProvider, collector);
    final FileElement templateFileElement = ((PsiFileImpl)templatePsiFile).calcTreeElement();

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
   * Creates psi tree without base language elements. The result PsiFile can contain additional elements.
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
    CharSequence templateSourceCode = createTemplateText(sourceCode, createBaseLexer(viewProvider), rangeCollector);
    if (rangeCollector instanceof RangeCollectorImpl) {
      ((RangeCollectorImpl)rangeCollector).prepareFileForParsing(templateLanguage, sourceCode, templateSourceCode);
    }
    return createPsiFileFromSource(templateLanguage, templateSourceCode, psiFile.getManager());
  }

  /**
   * Creates source code without template tokens. May add additional pieces of code.
   * Ranges of such additions should be added in rangeCollector using {@link RangeCollector#addRangeToRemove(TextRange)}
   * for later removal from the resulting tree.
   *
   * Consider overriding {@link #collectTemplateModifications(CharSequence, Lexer)} instead.
   *
   * @param sourceCode     source code with base and template languages
   * @param baseLexer      base language lexer
   * @param rangeCollector collector for ranges with non-template/additional symbols
   * @return template source code
   */
  protected CharSequence createTemplateText(@NotNull CharSequence sourceCode,
                                            @NotNull Lexer baseLexer,
                                            @NotNull TemplateDataElementType.RangeCollector rangeCollector) {
    if (REQUIRES_OLD_CREATE_TEMPLATE_TEXT.getValue()) {
      return oldCreateTemplateText(sourceCode, baseLexer, rangeCollector);
    }

    TemplateDataModifications modifications = collectTemplateModifications(sourceCode, baseLexer);
    return ((RangeCollectorImpl)rangeCollector).applyTemplateDataModifications(sourceCode, modifications);
  }

  private final NotNullLazyValue<Boolean> REQUIRES_OLD_CREATE_TEMPLATE_TEXT = NotNullLazyValue.volatileLazy(() -> {
    Class<?> implementationClass = ReflectionUtil.getMethodDeclaringClass(
      getClass(), "appendCurrentTemplateToken", StringBuilder.class, CharSequence.class, Lexer.class, RangeCollector.class);
    return implementationClass != TemplateDataElementType.class;
  });

  private CharSequence oldCreateTemplateText(@NotNull CharSequence sourceCode,
                                             @NotNull Lexer baseLexer,
                                             @NotNull RangeCollector rangeCollector) {
    StringBuilder result = new StringBuilder(sourceCode.length());
    baseLexer.start(sourceCode);

    TextRange currentRange = TextRange.EMPTY_RANGE;
    int tokenCounter = 0;
    while (baseLexer.getTokenType() != null) {
      if (++tokenCounter % CHECK_PROGRESS_AFTER_TOKENS == 0) {
        ProgressManager.checkCanceled();
      }
      TextRange newRange = TextRange.create(baseLexer.getTokenStart(), baseLexer.getTokenEnd());
      assert currentRange.getEndOffset() == newRange.getStartOffset() :
        "Inconsistent tokens stream from " + baseLexer +
        ": " + getRangeDump(currentRange, sourceCode) + " followed by " + getRangeDump(newRange, sourceCode);
      currentRange = newRange;
      if (baseLexer.getTokenType() == myTemplateElementType) {
        appendCurrentTemplateToken(result, sourceCode, baseLexer, rangeCollector);
      }
      else {
        rangeCollector.addOuterRange(currentRange);
      }
      baseLexer.advance();
    }

    return result;
  }

  /**
   * Collects changes to apply to template source code for later parsing by underlying language.
   *
   * @param sourceCode     source code with base and template languages
   * @param baseLexer      base language lexer
   */
  protected @NotNull TemplateDataModifications collectTemplateModifications(@NotNull CharSequence sourceCode, @NotNull Lexer baseLexer) {
    TemplateDataModifications modifications = new TemplateDataModifications();
    baseLexer.start(sourceCode);
    TextRange currentRange = TextRange.EMPTY_RANGE;
    int tokenCounter = 0;
    while (baseLexer.getTokenType() != null) {
      if (++tokenCounter % CHECK_PROGRESS_AFTER_TOKENS == 0) {
        ProgressManager.checkCanceled();
      }
      TextRange newRange = TextRange.create(baseLexer.getTokenStart(), baseLexer.getTokenEnd());
      assert currentRange.getEndOffset() == newRange.getStartOffset() :
        "Inconsistent tokens stream from " + baseLexer +
        ": " + getRangeDump(currentRange, sourceCode) + " followed by " + getRangeDump(newRange, sourceCode);
      currentRange = newRange;
      if (baseLexer.getTokenType() == myTemplateElementType) {
        TemplateDataModifications tokenModifications = appendCurrentTemplateToken(baseLexer.getTokenEnd(), baseLexer.getTokenSequence());
        modifications.addAll(tokenModifications);
      }
      else {
        modifications.addOuterRange(currentRange, isInsertionToken(baseLexer.getTokenType(), baseLexer.getTokenSequence()));
      }
      baseLexer.advance();
    }

    return modifications;
  }


  @NotNull
  private static String getRangeDump(@NotNull TextRange range, @NotNull CharSequence sequence) {
    return "'" + StringUtil.escapeLineBreak(range.subSequence(sequence).toString()) + "' " + range;
  }

  /**
   * @deprecated Override {@link #appendCurrentTemplateToken(int, CharSequence)} instead.
   */
  @Deprecated
  protected void appendCurrentTemplateToken(@NotNull StringBuilder result,
                                            @NotNull CharSequence buf,
                                            @NotNull Lexer lexer,
                                            @NotNull TemplateDataElementType.RangeCollector collector) {
    result.append(buf, lexer.getTokenStart(), lexer.getTokenEnd());
  }

  /**
   * Collects modifications for tokens having {@link #myTemplateElementType} type.
   *
   * @return modifications need to be applied for the current token
   */
  protected @NotNull TemplateDataModifications appendCurrentTemplateToken(int tokenEndOffset, @NotNull CharSequence tokenText) {
    return TemplateDataModifications.EMPTY;
  }

  /**
   * @deprecated Use {@link #isInsertionToken(IElementType, CharSequence)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected @NotNull TokenSet getTemplateDataInsertionTokens() {
    return TokenSet.EMPTY;
  }

  /**
   * Returns true if a string is expected to be inserted into resulting file in place of a current token.
   *
   * If insertion range contains several tokens, <code>true</code> may be returned only for the starting one. For example, if
   * <code><?=$myVar?></code> has three tokens <code><?=</code>, <code>$myVar</code> and <code>?></code>, only <code><?=</code>
   * may be an insertion token.
   *
   * Override this method when overriding {@link #collectTemplateModifications(CharSequence, Lexer)} is not required.
   *
   * @see RangeCollector#addOuterRange(TextRange, boolean)
   */
  protected boolean isInsertionToken(@Nullable IElementType tokenType, @NotNull CharSequence tokenSequence) {
    return false;
  }

  /**
   * @return instance of {@link OuterLanguageElementImpl} for outer element
   * @apiNote there are few ways to resolve error from this method:
   * <ul>
   * <li> Create your own {@link ASTFactory} and create proper element for your outer element</li>
   * <li> Use {@link com.intellij.psi.tree.OuterLanguageElementType} for your outer element type</li>
   * </ul>
   * @deprecated this method is going to be removed and com.intellij.lang.ASTFactory#leaf(com.intellij.psi.tree.IElementType, java.lang.CharSequence) going to be used instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected OuterLanguageElementImpl createOuterLanguageElement(@NotNull CharSequence internedTokenText,
                                                                @NotNull IElementType outerElementType) {
    var factoryCreatedElement = ASTFactory.leaf(outerElementType, internedTokenText);
    if (factoryCreatedElement instanceof OuterLanguageElementImpl) {
      return (OuterLanguageElementImpl)factoryCreatedElement;
    }
    LOG.error(
      "Wrong element created by ASTFactory. See method documentation for details. Here is what we have:" +
      " elementType: " + outerElementType +
      "; language: " + outerElementType.getLanguage() +
      "; element from factory: " + factoryCreatedElement);
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

  public static @NotNull ASTNode parseWithOuterAndRemoveRangesApplied(@NotNull ASTNode chameleon,
                                                                      @NotNull Language language,
                                                                      @NotNull Function<? super @NotNull CharSequence, ? extends @NotNull ASTNode> parser) {
    RangeCollectorImpl collector = chameleon.getUserData(RangeCollectorImpl.OUTER_ELEMENT_RANGES);
    return collector != null ? collector.applyRangeCollectorAndExpandChameleon(chameleon, language, parser)
                             : parser.apply(chameleon.getChars());
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
   *
   * @implNote Should be interface, but abstract class with empty method bodies for keeping binary compatibility with plugins.
   */
  public static abstract class RangeCollector {

    /**
     * Adds range corresponding to the outer element inside original source code.
     * After building the data template tree these ranges will be used for inserting outer language elements.
     * If it's known whether this template element adds some string to resulting text, consider using {@link #addOuterRange(TextRange, boolean)}.
     */
    public void addOuterRange(@NotNull TextRange newRange) {}

    /**
     * Adds range corresponding to the outer element inside original source code.
     * After building the data template tree these ranges will be used for inserting outer language elements.
     *
     * @param isInsertion <tt>true</tt> if element is expected to insert some text into template data fragment. For example, PHP's
     *                    <code><?= $myVar ?></code> are insertions, while <code><?php foo() ?></code> are not.
     */
    public abstract void addOuterRange(@NotNull TextRange newRange, boolean isInsertion);

    /**
     * Adds the fragment that must be removed from the tree on the stage inserting outer elements.
     * This method should be called after adding "fake" symbols inside the data language text for building syntax correct tree
     */
    public void addRangeToRemove(@NotNull TextRange rangeToRemove) {}
  }


  /**
   * Marker interface for element types which handle outer language elements themselves in
   * {@link ILazyParseableElementTypeBase#parseContents(ASTNode)} method.
   *
   * To parse lazy parseable element {@link TemplateDataElementType#parseWithOuterAndRemoveRangesApplied(ASTNode, Language, Function)}
   * should be used.
   */
  public interface TemplateAwareElementType extends ILazyParseableElementTypeBase {
    @NotNull TreeElement createTreeElement(@NotNull CharSequence text);
  }

  /**
   * Customizes template data language-specific parsing in templates.
   */
  public interface OuterLanguageRangePatcher {

    @ApiStatus.Internal
    LanguageExtension<OuterLanguageRangePatcher> EXTENSION = new LanguageExtension<>("com.intellij.outerLanguageRangePatcher");

    /**
     * @return Text to be inserted for parsing in outer element insertion ranges provided by
     * {@link RangeCollector#addOuterRange(TextRange, boolean)} where <tt>isInsertion == true</tt>
     */
    @Nullable String getTextForOuterLanguageInsertionRange(@NotNull TemplateDataElementType templateDataElementType,
                                                           @NotNull CharSequence outerElementText);
  }
}
