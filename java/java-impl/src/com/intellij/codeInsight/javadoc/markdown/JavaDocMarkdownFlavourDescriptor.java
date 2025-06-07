// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc.markdown;

import org.intellij.markdown.IElementType;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor;
import org.intellij.markdown.flavours.gfm.GFMTokenTypes;
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser;
import org.intellij.markdown.html.GeneratingProvider;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.html.TransparentInlineHolderProvider;
import org.intellij.markdown.html.TrimmingInlineHolderProvider;
import org.intellij.markdown.parser.LinkMap;
import org.intellij.markdown.parser.MarkerProcessorFactory;
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser;
import org.intellij.markdown.parser.sequentialparsers.SequentialParser;
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager;
import org.intellij.markdown.parser.sequentialparsers.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flavour descriptor which describes how to generate html text from markdown documentation
 */
public class JavaDocMarkdownFlavourDescriptor extends GFMFlavourDescriptor {
  public static final IElementType RAW_TYPE = new IElementType("RAW_TYPE");

  public JavaDocMarkdownFlavourDescriptor() {
    super(true, false, true);
  }

  @Override
  public @NotNull SequentialParserManager getSequentialParserManager() {
    return new SequentialParserManager() {
      @Override
      public @NotNull List<SequentialParser> getParserSequence() {
        return List.of(
          new AutolinkParser(List.of(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
          new BacktickParser(),
          new CodeTagParser(), // Custom parser excluding <code> blocks content to be processed as markdown
          new MathParser(),
          new ImageParser(),
          new InlineLinkParser(),
          new ReferenceLinkParser(),
          new EmphasisLikeParser(new EmphStrongDelimiterParser(), new StrikeThroughDelimiterParser())
        );
      }
    };
  }

  @Override
  public @NotNull Map<IElementType, GeneratingProvider> createHtmlGeneratingProviders(@NotNull LinkMap linkMap, @Nullable URI baseURI) {
    Map<IElementType, GeneratingProvider> result = new HashMap<>(super.createHtmlGeneratingProviders(linkMap, baseURI));

    // We don't generate a body
    result.remove(MarkdownElementTypes.MARKDOWN_FILE);

    // Paragraphs may need to be inlined
    result.put(MarkdownElementTypes.PARAGRAPH, new JavaDocParagraphProvider());

    // Custom type, no processing
    result.put(RAW_TYPE, new TransparentInlineHolderProvider());

    return result;
  }

  @Override
  public @NotNull MarkerProcessorFactory getMarkerProcessorFactory() {
    return new JavaDocMarkerProcessorFactory();
  }

  private static class JavaDocParagraphProvider extends TrimmingInlineHolderProvider {
    private static final CharSequence[] EMPTY_ARRAY = new CharSequence[0];

    /** @return Whether or no the paragraph is small and thus should be rendered inline */
    private static boolean isSmallParagraph(@NotNull ASTNode node) {
      for (ASTNode child : node.getChildren()) {
        if (child.getType() == MarkdownTokenTypes.EOL) {
          return false;
        }
      }

      ASTNode parent = node.getParent();
      if (parent != null && parent.getType() == MarkdownElementTypes.MARKDOWN_FILE) {
        // Check if the next thing is an EOL
        List<ASTNode> siblings = parent.getChildren();
        ASTNode nextSibling = siblings.get(Math.min(siblings.size() - 1, siblings.indexOf(node) + 1));
        if (nextSibling != null && nextSibling.getType() == MarkdownTokenTypes.EOL) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void closeTag(@NotNull HtmlGenerator.HtmlGeneratingVisitor visitor, @NotNull String text, @NotNull ASTNode node) {
      if (isSmallParagraph(node)) return;
      visitor.consumeTagClose("p");
    }

    @Override
    public void openTag(@NotNull HtmlGenerator.HtmlGeneratingVisitor visitor, @NotNull String text, @NotNull ASTNode node) {
      if (isSmallParagraph(node)) return;
      visitor.consumeTagOpen(node, "p", EMPTY_ARRAY, false);
    }
  }
}
