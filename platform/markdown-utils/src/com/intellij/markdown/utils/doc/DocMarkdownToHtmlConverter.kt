// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.markdown.IElementType;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor;
import org.intellij.markdown.flavours.gfm.GFMConstraints;
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor;
import org.intellij.markdown.html.GeneratingProvider;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.parser.LinkMap;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.markdown.parser.MarkerProcessorFactory;
import org.intellij.markdown.parser.ProductionHolder;
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints;
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider;
import org.intellij.markdown.parser.markerblocks.providers.HtmlBlockProvider;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.intellij.markdown.ast.ASTUtilKt.getTextInNode;

/**
 * TODO convert to Kotlin
 */
public final class DocMarkdownToHtmlConverter {

  private static final Logger LOG = Logger.getInstance(DocMarkdownToHtmlConverter.class);

  private static final Pattern TAG_START_OR_CLOSE_PATTERN = Pattern.compile("(<)/?(\\w+)[> ]");
  private static final Pattern SPLIT_BY_LINE_PATTERN = Pattern.compile("\n|\r|\r\n");
  private static final String FENCED_CODE_BLOCK = "```";

  private static final Map<String, String> HTML_DOC_SUBSTITUTIONS = new LinkedHashMap<>();

  static {
    HTML_DOC_SUBSTITUTIONS.put("<pre><code>", "<pre>");
    HTML_DOC_SUBSTITUTIONS.put("</code></pre>", "</pre>");
    HTML_DOC_SUBSTITUTIONS.put("<em>", "<i>");
    HTML_DOC_SUBSTITUTIONS.put("</em>", "</i>");
    HTML_DOC_SUBSTITUTIONS.put("<strong>", "<b>");
    HTML_DOC_SUBSTITUTIONS.put("</strong>", "</b>");
    HTML_DOC_SUBSTITUTIONS.put(": //", "://"); // Fix URL
    HTML_DOC_SUBSTITUTIONS.put("<p></p><pre>", "<pre>");
    HTML_DOC_SUBSTITUTIONS.put("</p><pre>", "<pre>");
    HTML_DOC_SUBSTITUTIONS.put("</p>", "");
    HTML_DOC_SUBSTITUTIONS.put("<br  />", "");
  }

  private static final Set<CharSequence> ACCEPTABLE_TAGS = CollectionFactory.createCharSequenceSet(false);
  static final Set<CharSequence> ACCEPTABLE_BLOCK_TAGS = CollectionFactory.createCharSequenceSet(false);

  static {
    ACCEPTABLE_BLOCK_TAGS.addAll(Arrays.asList(
      // Text content
      "blockquote", "dd", "dl", "dt",
      "hr", "li", "ol", "ul", "pre", "p",

      // Table,
      "caption", "col", "colgroup", "table", "tbody", "td", "tfoot", "th", "thead", "tr"
    ));
    ACCEPTABLE_TAGS.addAll(ACCEPTABLE_BLOCK_TAGS);
    ACCEPTABLE_TAGS.addAll(Arrays.asList(
      // Content sectioning
      "h1", "h2", "h3", "h4", "h5", "h6",
      // Inline text semantic
      "a", "b", "br", "code", "em", "i", "s", "span", "strong", "u", "wbr",
      // Image and multimedia
      "img",
      // Svg and math
      "svg",
      // Obsolete
      "tt"
    ));
  }

  private DocMarkdownToHtmlConverter() {
  }

  @Contract(pure = true)
  public static @NotNull String convert(@NotNull String markdownText) {
    String[] lines = SPLIT_BY_LINE_PATTERN.split(markdownText);
    List<String> processedLines = new ArrayList<>(lines.length);
    boolean isInCode = false;
    boolean isInTable = false;
    List<String> tableFormats = null;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String processedLine = StringUtil.trimTrailing(line);
      if (processedLine.matches("\\s+```.*")) {
        processedLine = processedLine.trim();
      }

      int count = StringUtil.getOccurrenceCount(processedLine, FENCED_CODE_BLOCK);
      if (count > 0) {
        //noinspection SimplifiableConditionalExpression
        isInCode = count % 2 == 0 ? isInCode : !isInCode;
      }
      else {
        // TODO merge custom table generation code with Markdown parser
        int tableDelimiterIndex = processedLine.indexOf('|');
        if (tableDelimiterIndex != -1) {
          if (!isInTable) {
            if (i + 1 < lines.length) {
              tableFormats = parseTableFormats(splitTableCols(lines[i + 1]));
            }
          }
          // create table only if we've successfully read the formats line
          if (!ContainerUtil.isEmpty(tableFormats)) {
            List<String> parts = splitTableCols(processedLine);
            if (isTableHeaderSeparator(parts)) continue;
            processedLine = getProcessedRow(isInTable, parts, tableFormats);
            if (!isInTable) processedLine = "<table style=\"border: 0px;\" cellspacing=\"0\">" + processedLine;
            isInTable = true;
          }
        }
        else {
          if (isInTable) processedLine += "</table>";
          isInTable = false;
          tableFormats = null;
        }
        processedLine = isInCode ? processedLine : StringUtil.trimLeading(processedLine);
      }
      processedLines.add(processedLine);
    }
    String normalizedMarkdown = StringUtil.join(processedLines, "\n");
    if (isInTable) normalizedMarkdown += "</table>"; //NON-NLS
    String html = performConversion(normalizedMarkdown);
    if (html == null) {
      html = replaceProhibitedTags(convertNewLinePlaceholdersToTags(markdownText), ContainerUtil.emptyList());
    }
    return adjustHtml(html);
  }

  @NotNull
  private static String convertNewLinePlaceholdersToTags(@NotNull String generatedDoc) {
    return StringUtil.replace(generatedDoc, "\n", "\n<p>");
  }

  private static @Nullable List<String> parseTableFormats(@NotNull List<String> cols) {
    List<String> formats = new ArrayList<>();
    for (String col : cols) {
      if (!isHeaderSeparator(col)) return null;
      formats.add(parseFormat(col.trim()));
    }
    return formats;
  }

  private static boolean isTableHeaderSeparator(@NotNull List<String> parts) {
    return ContainerUtil.and(parts, DocMarkdownToHtmlConverter::isHeaderSeparator);
  }

  private static boolean isHeaderSeparator(@NotNull String s) {
    return StringUtil.trimEnd(StringUtil.trimStart(s.trim(), ":"), ":").chars().allMatch(sx -> sx == '-');
  }

  private static @NotNull List<String> splitTableCols(@NotNull String processedLine) {
    List<String> parts = new ArrayList<>(StringUtil.split(processedLine, "|"));
    if (parts.isEmpty()) return parts;
    if (StringUtil.isEmptyOrSpaces(parts.get(0))) parts.remove(0);
    if (!parts.isEmpty() && StringUtil.isEmptyOrSpaces(parts.get(parts.size() - 1))) parts.remove(parts.size() - 1);
    return parts;
  }

  private static @NotNull String getProcessedRow(boolean isInTable,
                                                 @NotNull List<String> parts,
                                                 @Nullable List<String> tableFormats) {
    String openingTagStart = isInTable
                             ? "<td style=\"" + getBorder() + "\" "
                             : "<th style=\"" + getBorder() + "\" ";
    String closingTag = isInTable ? "</td>" : "</th>";
    StringBuilder resultBuilder = new StringBuilder("<tr style=\"" + getBorder() + "\">" + openingTagStart);
    resultBuilder.append("align=\"").append(getAlign(0, tableFormats)).append("\">");
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        resultBuilder.append(closingTag).append(openingTagStart).append("align=\"").append(getAlign(i, tableFormats)).append("\">");
      }
      resultBuilder.append(performConversion(parts.get(i).trim()));
    }
    resultBuilder.append(closingTag).append("</tr>");
    return resultBuilder.toString();
  }

  private static @NotNull String getAlign(int index, @Nullable List<String> formats) {
    return formats == null || index >= formats.size() ? "left" : formats.get(index);
  }

  private static @NotNull String parseFormat(@NotNull String format) {
    if (format.length() <= 1) return "left";
    char c0 = format.charAt(0);
    char cE = format.charAt(format.length() - 1);
    return c0 == ':' && cE == ':' ? "center" : cE == ':' ? "right" : "left";
  }

  private static final IElementType embeddedHtmlType = new IElementType("ROOT");

  private static @Nullable @NlsSafe String performConversion(@NotNull @Nls String text) {
    try {
      var flavour = new DocumentationFlavourDescriptor();
      var parsedTree = new MarkdownParser(flavour).parse(embeddedHtmlType, text, true);
      return new HtmlGenerator(text, parsedTree, flavour, false)
        .generateHtml(new DocumentationTagRenderer(text));
    }
    catch (Exception e) {
      LOG.warn(e.getMessage(), e);
      return null;
    }
  }

  private static @NotNull String replaceProhibitedTags(@NotNull String line, @NotNull List<TextRange> skipRanges) {
    Matcher matcher = TAG_START_OR_CLOSE_PATTERN.matcher(line);
    StringBuilder builder = new StringBuilder(line);

    int diff = 0;
    l:
    while (matcher.find()) {
      final String tagName = matcher.group(2);

      if (ACCEPTABLE_TAGS.contains(tagName)) continue;

      int startOfTag = matcher.start(2);
      for (TextRange range : skipRanges) {
        if (range.contains(startOfTag)) {
          continue l;
        }
      }

      var start = matcher.start(1) + diff;
      if (StringUtil.toLowerCase(tagName).equals("div")) {
        boolean isOpenTag = !matcher.group(0).contains("/");
        var end = start + (isOpenTag ? 5 : 6);
        String replacement = isOpenTag ? "<span>" : "</span>";
        builder.replace(start, end, replacement);
        diff += 1;
      }
      else {
        builder.replace(start, start + 1, "&lt;");
        diff += 3;
      }
    }
    return builder.toString();
  }

  @Contract(pure = true)
  private static @NotNull String adjustHtml(@NotNull String html) {
    String str = html;
    for (Map.Entry<String, String> entry : HTML_DOC_SUBSTITUTIONS.entrySet()) {
      str = str.replace(entry.getKey(), entry.getValue());
    }
    return str.trim();
  }

  private static @NotNull String getBorder() {
    return "margin: 0; border: 1px solid; border-color: #" + ColorUtil
      .toHex(UIUtil.getTooltipSeparatorColor()) + "; border-spacing: 0; border-collapse: collapse;vertical-align: baseline;";
  }

  private static class DocumentationMarkerProcessor extends CommonMarkMarkerProcessor {

    DocumentationMarkerProcessor(@NotNull ProductionHolder productionHolder,
                                 @NotNull CommonMarkdownConstraints constraintsBase) {
      super(productionHolder, constraintsBase);
    }

    @NotNull
    @Override
    protected List<MarkerBlockProvider<StateInfo>> getMarkerBlockProviders() {
      return ContainerUtil.concat(ContainerUtil.filter(super.getMarkerBlockProviders(), it -> !(it instanceof HtmlBlockProvider)),
                                  Collections.singletonList(new DocHtmlBlockProvider()));
    }
  }

  private static class DocumentationFlavourDescriptor extends GFMFlavourDescriptor {
    @NotNull
    @Override
    public MarkerProcessorFactory getMarkerProcessorFactory() {
      return (productionHolder) -> new DocumentationMarkerProcessor(productionHolder, GFMConstraints.Companion.getBASE());
    }

    @NotNull
    @Override
    public Map<IElementType, GeneratingProvider> createHtmlGeneratingProviders(@NotNull LinkMap linkMap, @Nullable URI baseURI) {
      var result = new HashMap<>(super.createHtmlGeneratingProviders(linkMap, baseURI));
      result.put(MarkdownTokenTypes.HTML_TAG, new SanitizingTagGeneratingProvider());
      return result;
    }
  }

  private static class SanitizingTagGeneratingProvider implements GeneratingProvider {

    private static final Pattern TAG_PATTERN = Pattern.compile("^</?([a-z][a-z-_0-9]*)[^>]*>$", Pattern.CASE_INSENSITIVE);

    @Override
    public void processNode(@NotNull HtmlGenerator.HtmlGeneratingVisitor visitor, @NotNull String wholeText, @NotNull ASTNode node) {
      var text = getTextInNode(node, wholeText);
      var matcher = TAG_PATTERN.matcher(text);
      if (matcher.matches()) {
        var tagName = matcher.group(1);
        if (StringUtil.equalsIgnoreCase(tagName, "div")) {
          visitor.consumeHtml(text.subSequence(0, matcher.start(1)));
          visitor.consumeHtml("span");
          visitor.consumeHtml(text.subSequence(matcher.end(1), text.length()));
          return;
        }
        if (ACCEPTABLE_TAGS.contains(tagName)) {
          visitor.consumeHtml(text);
          return;
        }
      }
      visitor.consumeHtml(StringUtil.escapeXmlEntities(text.toString()));
    }
  }

  private static class DocumentationTagRenderer extends HtmlGenerator.DefaultTagRenderer {

    private final String wholeText;

    private DocumentationTagRenderer(@NotNull String wholeText) {
      super((a, b, c) -> c, false);
      this.wholeText = wholeText;
    }

    @NotNull
    @Override
    public CharSequence openTag(@NotNull ASTNode node,
                                @NotNull CharSequence tagName,
                                CharSequence @NotNull [] attributes,
                                boolean autoClose) {
      if (StringUtil.equalsIgnoreCase(tagName, "p")) {
        ASTNode first = ContainerUtil.getFirstItem(node.getChildren());
        if (first != null && first.getType() == MarkdownTokenTypes.HTML_TAG) {
          var text = getTextInNode(first, wholeText);
          var matcher = SanitizingTagGeneratingProvider.TAG_PATTERN.matcher(text);
          if (matcher.matches()) {
            var nestedTag = matcher.group(1);
            if (ACCEPTABLE_BLOCK_TAGS.contains(nestedTag)) {
              return "";
            }
          }
        }
      }
      if (StringUtil.equalsIgnoreCase(tagName, "code") && node.getType() == MarkdownTokenTypes.CODE_FENCE_CONTENT) {
        return "";
      }
      return super.openTag(node, convertTag(tagName), attributes, autoClose);
    }

    @NotNull
    @Override
    public CharSequence closeTag(@NotNull CharSequence tagName) {
      if (StringUtil.equalsIgnoreCase(tagName, "p")) return "";
      return super.closeTag(convertTag(tagName));
    }

    private static @NotNull CharSequence convertTag(@NotNull CharSequence tagName) {
      if (StringUtil.equalsIgnoreCase(tagName, "strong")) {
        return "b";
      }
      else if (StringUtil.equalsIgnoreCase(tagName, "em")) {
        return "i";
      }
      return tagName;
    }
  }
}
