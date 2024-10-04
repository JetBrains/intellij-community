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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;


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
      var rangeIterator = semanticHighlighting.isEmpty() ? null : new HighlightInfoIterator(semanticHighlighting, scheme);
      var html = getHtmlContent(fakePsiFile, preprocessedCode, rangeIterator, scheme, 0, preprocessedCode.length());
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
    EditorHighlighter highlighter =
      HighlighterFactory.createHighlighter(file.getViewProvider().getVirtualFile(), schemeToUse, file.getProject());
    highlighter.setText(text);

    SyntaxInfoBuilder.HighlighterRangeIterator highlighterRangeIterator =
      new SyntaxInfoBuilder.HighlighterRangeIterator(highlighter, startOffset, endOffset);
    ownRangeIterator = ownRangeIterator == null
                       ? highlighterRangeIterator
                       : new SyntaxInfoBuilder.CompositeRangeIterator(schemeToUse, highlighterRangeIterator, ownRangeIterator);

    return getHtmlContent(text, ownRangeIterator, schemeToUse, endOffset);
  }

  public static @Nullable CharSequence getHtmlContent(
    @NotNull CharSequence text,
    @NotNull SyntaxInfoBuilder.RangeIterator ownRangeIterator,
    @NotNull EditorColorsScheme schemeToUse,
    int stopOffset
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
    try (HtmlSyntaxInfoReader data = new SimpleHtmlSyntaxInfoReader(info)) {
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


  private static final class SimpleHtmlSyntaxInfoReader extends HtmlSyntaxInfoReader {

    private SimpleHtmlSyntaxInfoReader(SyntaxInfo info) {
      super(info, 2);
    }

    @Override
    protected void appendCloseTags() {

    }

    @Override
    protected void appendStartTags() {

    }

    @Override
    protected void defineBackground(int id, @NotNull StringBuilder styleBuffer) {

    }

    @Override
    protected void appendFontFamilyRule(@NotNull StringBuilder styleBuffer, int fontFamilyId) {

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
      return !myIterator.hasNext(); }

    @Override
    public int getRangeStart() {
      return myCurrentInfo.getStartOffset(); }

    @Override
    public int getRangeEnd() {
      return myCurrentInfo.getEndOffset(); }

    @Override
    public TextAttributes getTextAttributes() {
      return myCurrentInfo.getTextAttributes(myScheme);
    }

    @Override
    public void dispose() { }
  }


}
