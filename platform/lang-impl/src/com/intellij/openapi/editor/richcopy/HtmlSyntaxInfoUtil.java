// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.richcopy;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.lang.documentation.QuickDocHighlightingHelper;
import com.intellij.lang.documentation.QuickDocSyntaxHighlightingHandler;
import com.intellij.lang.documentation.QuickDocSyntaxHighlightingHandlerFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.richcopy.model.SyntaxInfo;
import com.intellij.openapi.editor.richcopy.view.HtmlSyntaxInfoReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.ColorUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;


public final class HtmlSyntaxInfoUtil {

  private HtmlSyntaxInfoUtil() { }

  /**
   * @deprecated Use {@link QuickDocHighlightingHelper} for adding highlighted HTML to documentation
   */
  @Deprecated(forRemoval = true)
  public static @NotNull String getStyledSpan(@NotNull TextAttributesKey attributesKey, @Nullable String value, float saturationFactor) {
    return appendStyledSpan(new StringBuilder(), attributesKey, value, saturationFactor).toString();
  }

  /**
   * @deprecated Use {@link QuickDocHighlightingHelper} for adding highlighted HTML to documentation
   */
  @Deprecated(forRemoval = true)
  public static @NotNull String getStyledSpan(@NotNull TextAttributes attributes, @Nullable String value, float saturationFactor) {
    return appendStyledSpan(new StringBuilder(), attributes, value, saturationFactor).toString();
  }

  public static @NotNull StringBuilder appendStyledSpan(
    @NotNull StringBuilder buffer,
    @Nullable String value,
    String @NotNull ... properties
  ) {
    HtmlChunk.span().style(StringUtil.join(properties, ";"))
      .addRaw(StringUtil.notNullize(value)) //NON-NLS
      .appendTo(buffer);
    return buffer;
  }

  public static @NotNull StringBuilder appendStyledSpan(
    @NotNull StringBuilder buffer,
    @NotNull TextAttributesKey attributesKey,
    @Nullable String value,
    float saturationFactor
  ) {
    appendStyledSpan(
      buffer,
      Objects.requireNonNull(EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey)),
      value,
      saturationFactor);
    return buffer;
  }

  public static @NotNull StringBuilder appendStyledSpan(
    @NotNull StringBuilder buffer,
    @NotNull TextAttributes attributes,
    @Nullable String value,
    float saturationFactor
  ) {
    createHtmlSpanBlockStyledAsTextAttributes(attributes, saturationFactor)
      .addRaw(StringUtil.notNullize(value)) //NON-NLS
      .appendTo(buffer);
    return buffer;
  }

  public static @NotNull StringBuilder appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
    @NotNull StringBuilder buffer,
    @NotNull Project project,
    @NotNull Language language,
    @Nullable String codeSnippet,
    float saturationFactor
  ) {
    return appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(buffer, project, language, codeSnippet, true, saturationFactor);
  }


  @RequiresReadLock
  public static @NotNull StringBuilder appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
    @NotNull StringBuilder buffer,
    @NotNull Project project,
    @NotNull Language language,
    @Nullable String codeSnippet,
    boolean doTrimIndent,
    float saturationFactor
  ) {
    return appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(buffer, project, language, codeSnippet, doTrimIndent, saturationFactor, null,
                                                               HtmlGeneratorProperties.createDefault());
  }

  /**
   * This class represents a builder for generating HTML code snippets.
   */
  @ApiStatus.Experimental
  public static class HtmlCodeSnippetBuilder {
    @NotNull
    private final StringBuilder buffer;
    @NotNull
    private final Project project;
    @NotNull
    private final Language language;
    @Nullable
    private String codeSnippet;
    private boolean doTrimIndent;
    private float saturationFactor = 1.0f;
    @Nullable
    private SyntaxInfoBuilder.RangeIterator additionalIterator;
    @NotNull
    private HtmlGeneratorProperties properties = HtmlGeneratorProperties.createDefault();

    public HtmlCodeSnippetBuilder(@NotNull StringBuilder buffer, @NotNull Project project, @NotNull Language language) {
      this.buffer = buffer;
      this.project = project;
      this.language = language;
    }

    public HtmlCodeSnippetBuilder codeSnippet(@Nullable String codeSnippet) {
      this.codeSnippet = codeSnippet;
      return this;
    }

    public HtmlCodeSnippetBuilder doTrimIndent(boolean doTrimIndent) {
      this.doTrimIndent = doTrimIndent;
      return this;
    }

    public HtmlCodeSnippetBuilder saturationFactor(float saturationFactor) {
      this.saturationFactor = saturationFactor;
      return this;
    }

    public HtmlCodeSnippetBuilder additionalIterator(@Nullable SyntaxInfoBuilder.RangeIterator additionalIterator) {
      this.additionalIterator = additionalIterator;
      return this;
    }

    public HtmlCodeSnippetBuilder properties(@NotNull HtmlGeneratorProperties properties) {
      this.properties = properties;
      return this;
    }

    @RequiresReadLock
    public StringBuilder build() {
      return appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(this.buffer,
                                                                 this.project,
                                                                 this.language,
                                                                 this.codeSnippet,
                                                                 this.doTrimIndent,
                                                                 this.saturationFactor,
                                                                 this.additionalIterator,
                                                                 this.properties);
    }
  }

  @RequiresReadLock
  private static @NotNull StringBuilder appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
    @NotNull StringBuilder buffer,
    @NotNull Project project,
    @NotNull Language language,
    @Nullable String codeSnippet,
    boolean doTrimIndent,
    float saturationFactor,
    @Nullable SyntaxInfoBuilder.RangeIterator additionalIterator,
    @NotNull HtmlGeneratorProperties properties
  ) {
    codeSnippet = StringUtil.notNullize(codeSnippet);
    String trimmed = doTrimIndent ? StringsKt.trimIndent(codeSnippet) : codeSnippet;
    String zeroIndentCode = trimmed.replace("\t", "    ");
    if (!zeroIndentCode.isEmpty()) {
      var factory = QuickDocSyntaxHighlightingHandlerFactory.Companion.getEXTENSION$intellij_platform_lang_impl().forLanguage(language);
      var handler = factory != null ? factory.createHandler() : null;
      String preprocessedCode = handler != null ? handler.preprocessCode(zeroIndentCode) : zeroIndentCode;

      PsiFile fakePsiFile = PsiFileFactory.getInstance(project).createFileFromText(language, preprocessedCode);
      EditorColorsScheme scheme =
        new ColorsSchemeWithChangedSaturation(EditorColorsManager.getInstance().getGlobalScheme(), saturationFactor);

      List<QuickDocSyntaxHighlightingHandler.QuickDocHighlightInfo> semanticHighlighting =
        handler != null ? handler.performSemanticHighlighting(fakePsiFile) : Collections.emptyList();
      SyntaxInfoBuilder.RangeIterator rangeIterator =
        semanticHighlighting.isEmpty() ? null : new HighlightInfoIterator(semanticHighlighting, scheme);
      if (additionalIterator != null && rangeIterator != null) {
        rangeIterator = new SyntaxInfoBuilder.CompositeRangeIterator(scheme, rangeIterator, additionalIterator);
      }
      else if (additionalIterator != null) {
        rangeIterator = additionalIterator;
      }
      var html = getHtmlContent(fakePsiFile, preprocessedCode, rangeIterator, scheme, 0, preprocessedCode.length(), properties);
      var postProcessedHtml = handler != null && html != null ? handler.postProcessHtml(html.toString()) : html;
      buffer.append(postProcessedHtml);
    }
    return buffer;
  }

  public static @Nullable CharSequence getHtmlContent(
    @NotNull PsiFile file,
    @NotNull CharSequence text,
    @Nullable SyntaxInfoBuilder.RangeIterator ownRangeIterator,
    @NotNull EditorColorsScheme schemeToUse,
    int startOffset,
    int endOffset
  ) {
    return getHtmlContent(file, text, ownRangeIterator, schemeToUse, startOffset, endOffset, HtmlGeneratorProperties.createDefault());
  }

  private static @Nullable CharSequence getHtmlContent(
    @NotNull PsiFile file,
    @NotNull CharSequence text,
    @Nullable SyntaxInfoBuilder.RangeIterator ownRangeIterator,
    @NotNull EditorColorsScheme schemeToUse,
    int startOffset,
    int endOffset,
    @NotNull HtmlGeneratorProperties properties
  ) {
    EditorHighlighter highlighter =
      HighlighterFactory.createHighlighter(file.getViewProvider().getVirtualFile(), schemeToUse, file.getProject());
    highlighter.setText(text);

    SyntaxInfoBuilder.HighlighterRangeIterator highlighterRangeIterator =
      new SyntaxInfoBuilder.HighlighterRangeIterator(highlighter, startOffset, endOffset);
    ownRangeIterator = ownRangeIterator == null
                       ? highlighterRangeIterator
                       : new SyntaxInfoBuilder.CompositeRangeIterator(schemeToUse, highlighterRangeIterator, ownRangeIterator);

    return getHtmlContent(text, ownRangeIterator, schemeToUse, endOffset, properties);
  }

  public static @Nullable CharSequence getHtmlContent(
    @NotNull CharSequence text,
    @NotNull SyntaxInfoBuilder.RangeIterator ownRangeIterator,
    @NotNull EditorColorsScheme schemeToUse,
    int stopOffset
  ) {
    return getHtmlContent(text, ownRangeIterator, schemeToUse, stopOffset, HtmlGeneratorProperties.createDefault());
  }

  private static @Nullable CharSequence getHtmlContent(
    @NotNull CharSequence text,
    @NotNull SyntaxInfoBuilder.RangeIterator ownRangeIterator,
    @NotNull EditorColorsScheme schemeToUse,
    int stopOffset,
    @NotNull HtmlGeneratorProperties properties
  ) {
    SyntaxInfoBuilder.Context context = new SyntaxInfoBuilder.Context(text, schemeToUse, 0);
    SyntaxInfoBuilder.MyMarkupIterator iterator = new SyntaxInfoBuilder.MyMarkupIterator(text, ownRangeIterator, schemeToUse);

    try {
      context.iterate(iterator, stopOffset);
    }
    finally {
      iterator.dispose();
    }
    SyntaxInfo info = context.finish();
    try (HtmlSyntaxInfoReader data = new CustomHtmlSyntaxInfoReader(info, properties)) {
      data.setRawText(text.toString());
      return data.getBuffer();
    }
    catch (IOException e) {
      Logger.getInstance(HtmlSyntaxInfoUtil.class).error(e);
    }
    return null;
  }

  private static @NotNull HtmlChunk.Element createHtmlSpanBlockStyledAsTextAttributes(
    @NotNull TextAttributes attributes,
    float saturationFactor
  ) {
    StringBuilder style = new StringBuilder();

    Color foregroundColor = attributes.getForegroundColor();
    Color backgroundColor = attributes.getBackgroundColor();
    Color effectTypeColor = attributes.getEffectColor();

    if (foregroundColor != null) foregroundColor = tuneSaturationEspeciallyGrey(foregroundColor, 1, saturationFactor);
    if (backgroundColor != null) backgroundColor = tuneSaturationEspeciallyGrey(backgroundColor, 1, saturationFactor);
    if (effectTypeColor != null) effectTypeColor = tuneSaturationEspeciallyGrey(effectTypeColor, 1, saturationFactor);

    if (foregroundColor != null) appendProperty(style, "color", ColorUtil.toHtmlColor(foregroundColor));
    if (backgroundColor != null) appendProperty(style, "background-color", ColorUtil.toHtmlColor(backgroundColor));

    switch (attributes.getFontType()) {
      case Font.BOLD -> appendProperty(style, "font-weight", "bold");
      case Font.ITALIC -> appendProperty(style, "font-style", "italic");
    }

    EffectType effectType = attributes.getEffectType();
    if (attributes.hasEffects() && effectType != null) {
      switch (effectType) {
        case LINE_UNDERSCORE -> appendProperty(style, "text-decoration-line", "underline");
        case WAVE_UNDERSCORE -> {
          appendProperty(style, "text-decoration-line", "underline");
          appendProperty(style, "text-decoration-style", "wavy");
        }
        case BOLD_LINE_UNDERSCORE -> {
          appendProperty(style, "text-decoration-line", "underline");
          appendProperty(style, "text-decoration-thickness", "2px");
        }
        case BOLD_DOTTED_LINE -> {
          appendProperty(style, "text-decoration-line", "underline");
          appendProperty(style, "text-decoration-thickness", "2px");
          appendProperty(style, "text-decoration-style", "dotted");
        }
        case STRIKEOUT -> appendProperty(style, "text-decoration-line", "line-through");
        case BOXED, SLIGHTLY_WIDER_BOX, SEARCH_MATCH -> appendProperty(style, "border", "1px solid");
        case ROUNDED_BOX -> {
          appendProperty(style, "border", "1px solid");
          appendProperty(style, "border-radius", "2px");
        }
      }
    }

    if (attributes.hasEffects() && effectType != null && effectTypeColor != null) {
      switch (effectType) {
        case LINE_UNDERSCORE, WAVE_UNDERSCORE, BOLD_LINE_UNDERSCORE, BOLD_DOTTED_LINE, STRIKEOUT ->
          appendProperty(style, "text-decoration-color", ColorUtil.toHtmlColor(effectTypeColor));
        case BOXED, ROUNDED_BOX, SEARCH_MATCH, SLIGHTLY_WIDER_BOX ->
          appendProperty(style, "border-color", ColorUtil.toHtmlColor(effectTypeColor));
      }
    }

    return HtmlChunk.span().style(style.toString());
  }

  private static void appendProperty(@NotNull StringBuilder builder, @NotNull String name, @NotNull String value) {
    builder.append(name);
    builder.append(":");
    builder.append(value);
    builder.append(";");
  }

  private static @NotNull Color tuneSaturationEspeciallyGrey(@NotNull Color color, int howMuch, float saturationFactor) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return color;
    }
    return ColorUtil.tuneSaturationEspeciallyGrey(color, howMuch, saturationFactor);
  }


  private static final class ColorsSchemeWithChangedSaturation extends DelegateColorScheme {

    private final float mySaturationFactor;

    private ColorsSchemeWithChangedSaturation(@NotNull EditorColorsScheme delegate, float saturationFactor) {
      super((EditorColorsScheme)delegate.clone());
      mySaturationFactor = saturationFactor;
    }

    @Override
    public @NotNull Color getDefaultBackground() {
      return tuneColor(super.getDefaultBackground());
    }

    @Override
    public @NotNull Color getDefaultForeground() {
      return tuneColor(super.getDefaultForeground());
    }

    @Override
    public @Nullable Color getColor(ColorKey key) {
      Color color = super.getColor(key);
      return color != null ? tuneColor(color) : null;
    }

    @Override
    public void setColor(ColorKey key, @Nullable Color color) {
      super.setColor(key, color != null ? tuneColor(color) : null);
    }

    @Override
    public TextAttributes getAttributes(TextAttributesKey key) {
      return tuneAttributes(super.getAttributes(key));
    }

    @Override
    public void setAttributes(@NotNull TextAttributesKey key, TextAttributes attributes) {
      super.setAttributes(key, tuneAttributes(attributes));
    }

    private Color tuneColor(Color color) {
      return tuneSaturationEspeciallyGrey(color, 1, mySaturationFactor);
    }

    private TextAttributes tuneAttributes(TextAttributes attributes) {
      if (attributes != null) {
        attributes = attributes.clone();
        Color foregroundColor = attributes.getForegroundColor();
        Color backgroundColor = attributes.getBackgroundColor();
        if (foregroundColor != null) attributes.setForegroundColor(tuneColor(foregroundColor));
        if (backgroundColor != null) attributes.setBackgroundColor(tuneColor(backgroundColor));
      }
      return attributes;
    }
  }


  private static final class CustomHtmlSyntaxInfoReader extends HtmlSyntaxInfoReader {
    private final HtmlGeneratorProperties myProperties;

    private CustomHtmlSyntaxInfoReader(SyntaxInfo info, @NotNull HtmlGeneratorProperties properties) {
      super(info, 2);
      myProperties = properties;
    }

    @Override
    protected void generateCloseBodyHtmlTags() {
      if (myProperties.generateHtmlTags) {
        super.generateCloseBodyHtmlTags();
      }
    }

    @Override
    protected void appendOpenBodyHtmlTags() {
      if (myProperties.generateHtmlTags) {
        super.appendOpenBodyHtmlTags();
      }
    }

    @Override
    protected void appendFontSizeRule() {
      if (!myProperties.defaultFontSize) {
        super.appendFontSizeRule();
      }
    }

    @Override
    protected void appendCloseTags() {
      if (myProperties.generateWrappedDivTags) {
        super.appendCloseTags();
      }
    }


    @Override
    protected void appendStartTags() {
      if (myProperties.generateWrappedDivTags) {
        super.appendStartTags();
      }
    }

    @Override
    protected void defineBackground(int id, @NotNull StringBuilder styleBuffer) {
      if (myProperties.generateBackground) {
        super.defineBackground(id, styleBuffer);
      }
    }

    @Override
    protected void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, int fontFamilyId) {
      if (myProperties.generateFontFamily) {
        super.appendFontFamilyRule(styleBuffer, fontFamilyId);
      }
    }

    @Override
    public void handleText(int startOffset, int endOffset) {
      if (myProperties.textHandler != null) {
        myProperties.textHandler.handleText(startOffset, endOffset, myResultBuffer, () -> super.handleText(startOffset, endOffset));
      }
      else {
        super.handleText(startOffset, endOffset);
      }
    }
  }

  private static class HighlightInfoIterator implements SyntaxInfoBuilder.RangeIterator {

    private final Iterator<QuickDocSyntaxHighlightingHandler.QuickDocHighlightInfo> myIterator;
    private final EditorColorsScheme myScheme;
    private QuickDocSyntaxHighlightingHandler.QuickDocHighlightInfo myCurrentInfo = null;

    private HighlightInfoIterator(List<QuickDocSyntaxHighlightingHandler.QuickDocHighlightInfo> highlightInfos,
                                  EditorColorsScheme scheme) {
      myIterator = highlightInfos.stream()
        .sorted(Comparator.comparing(el -> el.getStartOffset()))
        .iterator();
      myScheme = scheme;
    }

    @Override
    public void advance() {
      if (myIterator.hasNext()) {
        myCurrentInfo = myIterator.next();
      }
    }

    @Override
    public boolean atEnd() {
      return !myIterator.hasNext();
    }

    @Override
    public int getRangeStart() {
      return myCurrentInfo.getStartOffset();
    }

    @Override
    public int getRangeEnd() {
      return myCurrentInfo.getEndOffset();
    }

    @Override
    public TextAttributes getTextAttributes() {
      return myCurrentInfo.getTextAttributes(myScheme);
    }

    @Override
    public void dispose() { }
  }

  /**
   * Represents properties for configuring the HTML generation process.
   * This class is a record with parameters for toggling various features such as generating background, font family, main tags, and HTML tags.
   * Additionally, it allows specifying the default font size and a custom text handler for handling text within specified ranges.
   * This class provides a factory method {@link #createDefault()} to create default properties.
   */

  @ApiStatus.Experimental
  public static class HtmlGeneratorProperties {
    private boolean generateBackground = false;
    private boolean generateFontFamily = false;
    private boolean generateWrappedDivTags = false;
    private boolean generateHtmlTags = false;
    private boolean defaultFontSize = false;
    @Nullable
    private TextHandler textHandler = null;

    private HtmlGeneratorProperties() { }

    public static HtmlGeneratorProperties createDefault() {
      return new HtmlGeneratorProperties();
    }

    /**
     * Enables the generation of background colors for all texts, which are different from default editor background color
     * during the HTML generation process.
     *
     * @return the updated instance of {@code HtmlGeneratorProperties} with the background generation enabled
     */
    public HtmlGeneratorProperties generateBackground() {
      this.generateBackground = true;
      return this;
    }

    /**
     * Enables the generation of the current IDEA font family definitions during the HTML generation process.
     * Usually, default browser fonts are used.
     * If this text is used inside IDEA, it is supposed to use IDEA's font families as well
     *
     * @return the updated instance of {@code HtmlGeneratorProperties} with font family generation enabled
     */
    public HtmlGeneratorProperties generateFontFamily() {
      this.generateFontFamily = true;
      return this;
    }

    /**
     * Enables the generation of wrapped HTML tags, including enabling the wrapping of content
     * into a standard set of tags such as div, pre during the HTML generation process.
     * Should be used together with {@link HtmlGeneratorProperties#generateHtmlTags()}
     *
     * @return the updated instance of {@code HtmlGeneratorProperties} with the wrapped tags generation enabled
     */
    public HtmlGeneratorProperties generateWrappedTags() {
      this.generateWrappedDivTags = true;
      return this;
    }

    /**
     * Wrap text into the next tags: `html`, `head`, `body`.
     * Part of {@link HtmlGeneratorProperties#generateWrappedTags()}
     *
     * @return the updated instance of {@code HtmlGeneratorProperties} with HTML tag generation enabled
     */
    public HtmlGeneratorProperties generateHtmlTags() {
      this.generateHtmlTags = true;
      return this;
    }

    /**
     * Enables the generation of font size definitions during the HTML generation process.
     * Usually, the default browser font size is used.
     * If this text is used inside IDEA, it is supposed to use IDEA's font size as well
     *
     * @return the updated instance of {@code HtmlGeneratorProperties} with font size generation enabled
     */
    public HtmlGeneratorProperties generateFontSize() {
      this.defaultFontSize = true;
      return this;
    }

    /**
     * Sets the custom text handler to be used during HTML generation.
     *
     *
     * @param textHandler the {@code TextHandler} implementation that defines how text will be processed and modified
     *                    during the HTML generation process; must not be null
     * @return the updated instance of {@code HtmlGeneratorProperties} with the specified text handler set
     * @see TextHandler
     */
    public HtmlGeneratorProperties textHandler(@NotNull TextHandler textHandler) {
      this.textHandler = textHandler;
      return this;
    }

    /**
     * Interface for handling text within a specified range and appending the result to a StringBuilder.
     * Implementations of this interface should define the behavior when text is handled, such as formatting or modifying the text.
     */
    @ApiStatus.Experimental
    public interface TextHandler {
      /**
       * Handles the text within the specified range and appends the result to a StringBuilder.
       *
       * @param startOffset  the starting offset of the text to handle
       * @param endOffset    the ending offset of the text to handle
       * @param resultBuffer the StringBuilder to which the handled text will be appended
       * @param superHandler a Runnable representing the super handler to invoke and update resultBuffer from original handler
       */
      void handleText(int startOffset, int endOffset, @NotNull StringBuilder resultBuffer, @Nullable Runnable superHandler);
    }
  }
}
