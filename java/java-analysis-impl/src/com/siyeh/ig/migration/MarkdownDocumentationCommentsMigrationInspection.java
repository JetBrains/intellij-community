// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.NodeFilter;

import java.util.ArrayDeque;
import java.util.regex.Pattern;

import static com.intellij.psi.javadoc.PsiDocToken.isDocToken;

/**
 * @author Bas Leijdekkers
 */
@ApiStatus.Internal
public final class MarkdownDocumentationCommentsMigrationInspection extends BaseInspection implements DumbAware {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("markdown.documentation.comments.migration.display.name");
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new MarkdownDocumentationCommentsMigrationFix();
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new MarkdownDocumentationCommentsMigrationVisitor();
  }

  private static class MarkdownDocumentationCommentsMigrationVisitor extends BaseInspectionVisitor {
    @Override
    public void visitDocComment(@NotNull PsiDocComment comment) {
      super.visitDocComment(comment);
      if (comment.isMarkdownComment()) {
        return;
      }
      registerError(isVisibleHighlight(comment) ? comment.getFirstChild() : comment);
    }
  }

  private static class MarkdownDocumentationCommentsMigrationFix extends PsiUpdateModCommandQuickFix implements DumbAware {

    private static final TokenSet SKIP_TOKENS = TokenSet.create(JavaDocTokenType.DOC_COMMENT_START,
                                                                JavaDocTokenType.DOC_COMMENT_END,
                                                                JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS);
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("markdown.documentation.comments.migration.fix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiDocToken) element = element.getParent();
      if (!(element instanceof PsiDocComment)) return;
      String markdown = convertToMarkdown(appendElementText(element, new StringBuilder()).toString());
      String indent = getElementIndent(element);
      String[] lines = markdown.split("\n");
      StringBuilder result = new StringBuilder(markdown.length() + (indent.length() + 4) * lines.length);
      for (String line : lines) {
        if (!result.isEmpty()) {
          result.append(indent);
        }
        boolean isBlank = line.isBlank();
        result.append((line.startsWith(" ") || isBlank) ? "///" : "/// ");
          if (!isBlank) {
            result.append(line);
          }
        result.append('\n');
      }
      result.append(indent);

      Document document = element.getContainingFile().getFileDocument();
      int startOffset = element.getTextOffset();
      int endOffset = element.getNextSibling() instanceof PsiWhiteSpace whiteSpace
                      ? whiteSpace.getTextOffset() + whiteSpace.getTextLength()
                      : startOffset + element.getTextLength();
      document.replaceString(startOffset, endOffset, result);
    }

    private static StringBuilder appendElementText(@NotNull PsiElement element, StringBuilder result) {
      for (@NotNull PsiElement child : element.getChildren()) {
        if (isDocToken(child, SKIP_TOKENS)) continue;

        if (child instanceof PsiInlineDocTag inlineDocTag) {
          PsiElement next = inlineDocTag.getNameElement().getNextSibling();
          if (next instanceof PsiWhiteSpace && next.getText().contains("\n") && !Strings.endsWith(result, "<pre>")) {
            result.append("\n ");
          }
          String name = inlineDocTag.getName();
          if ("code".equals(name)) handleCode(inlineDocTag, result);
          else if ("link".equals(name) || "linkplain".equals(name)) handleLink(inlineDocTag, result);
          else handleInlineDocTag(inlineDocTag, result);
        }
        else if (child instanceof PsiDocParamRef) {
          result.append(escapeInline(child.getText()));
        }
        else if (child instanceof PsiDocTag) {
          result.append("<%s>".formatted(HtmlToMarkdownVisitor.INTERNAL_TAG_JDOC_TAG));
          appendElementText(child, result);
          result.append("</%s>".formatted(HtmlToMarkdownVisitor.INTERNAL_TAG_JDOC_TAG));
        }
        else if (child instanceof PsiDocTagValue) {
          appendElementText(child, result);
        }
        else if (child instanceof PsiWhiteSpace) {
          if (!isDocToken(child.getNextSibling(), JavaDocTokenType.DOC_COMMENT_END)) {
            String text = child.getText();
            if (text.contains("\n")) {
              if (!result.isEmpty()) result.append("\n");
            }
            else {
              result.append(text);
            }
          }
        }
        else {
          result.append(child.getText());
        }
      }
      return result;
    }

    /// Escape and wrap the `text` with a custom html tag for jsoup processing 
    private static String escapeInline(String text) {
      return "<%s>%s</%s>".formatted(
        HtmlToMarkdownVisitor.INTERNAL_TAG_INLINE_RAW,
        StringUtil.escapeXmlEntities(text),
        HtmlToMarkdownVisitor.INTERNAL_TAG_INLINE_RAW
      );
    }

    private static String convertToMarkdown(@NlsSafe String html) {
      @NonNls String escape = "\\nbsp;";
      Element body = Jsoup.parse(html.replace("&nbsp;", escape)).outputSettings(new OutputSettings().prettyPrint(false)).body();
      HtmlToMarkdownVisitor visitor = new HtmlToMarkdownVisitor(html.length());
      body.filter(visitor);

      return visitor.getResult().replace(escape, "&nbsp;");
    }

    private static String getElementIndent(PsiElement element) {
      PsiElement leaf = PsiTreeUtil.prevLeaf(element);
      if (!(leaf instanceof PsiWhiteSpace)) {
        return "";
      }
      String text = leaf.getText();
      final int lineBreak = text.lastIndexOf('\n');
      return text.substring(lineBreak + 1);
    }

    private static void handleInlineDocTag(PsiElement element, StringBuilder result) {
      PsiElement[] children = element.getChildren();
      if (children.length > 0) {
        for (@NotNull PsiElement child : children) {
          handleInlineDocTag(child, result);
        }
      }
      else if (element instanceof PsiWhiteSpace) {
        String text = element.getText();
        if (text.contains("\n")) {
          result.append("\n ");
        }
        else {
          result.append(text);
        }
      }
      else if (!isDocToken(element, JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) {
        result.append(element.getText());
      }
    }

    private static void handleCode(PsiInlineDocTag inlineDocTag, StringBuilder result) {
      StringBuilder codeBuilder = new StringBuilder();

      for (PsiElement dataElement : inlineDocTag.getDataElements()) {
        if (dataElement instanceof PsiDocToken) {
          codeBuilder.append(dataElement.getText());
          if (dataElement.getNextSibling() instanceof PsiWhiteSpace whiteSpace && whiteSpace.getText().contains("\n")) {
            codeBuilder.append("\n");
          }
        }
      }

      result
        .append("<code>")
        .append(escapeInline(codeBuilder.toString().trim().replace("\\","\\\\")))
        .append("</code>");
    }

    private static void handleLink(PsiInlineDocTag inlineDocTag, StringBuilder result) {
      boolean isPlain = inlineDocTag.getName().equals("linkplain");
      PsiElement[] dataElements = inlineDocTag.getDataElements();
      StringBuilder referenceBuilder = new StringBuilder();
      StringBuilder labelBuilder = null;

      // Extract label (if available)
      for (PsiElement dataElement : dataElements) {
        if (dataElement instanceof PsiDocToken) {
          String labelText = dataElement.getText();
          // Remove the mandatory space
          if (labelBuilder == null) labelText = labelText.stripLeading();
          if (!labelText.isBlank()) {
            if (labelBuilder == null) labelBuilder = new StringBuilder();

            labelBuilder.append(labelText);
          }
        }
      }

      // Extract references
      for (PsiElement dataElement : dataElements) {
        if (dataElement instanceof PsiDocMethodOrFieldRef) {
          for (@NotNull PsiElement refChild : dataElement.getChildren()) {
            if (refChild instanceof PsiDocToken) {
              referenceBuilder.append(refChild.getText());
            }
            else if (refChild instanceof PsiDocTagValue) {
              for (@NotNull PsiElement valueChild : refChild.getChildren()) {
                if (valueChild instanceof PsiWhiteSpace) {
                  referenceBuilder.append(valueChild.getText().contains("\n") ? '\n' : valueChild.getText());
                }
                else if (!isDocToken(valueChild, JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) {
                  referenceBuilder.append(valueChild.getText());
                }
              }
            }
            else {
              referenceBuilder.append(refChild.getText());
            }
          }
        }
        else if (!(dataElement instanceof PsiDocToken) && !(dataElement instanceof PsiWhiteSpace)) {
          referenceBuilder.append(dataElement.getText());
        }
      }

      // When a @linkplain tag is without a label, use the reference as one, otherwise the Markdown version mimics the @link tag
      if(isPlain && labelBuilder == null && !referenceBuilder.isEmpty()) {
        labelBuilder = referenceBuilder;
      }

      if (labelBuilder != null) {
        result.append(!isPlain ? "[<code>" : '[').append(labelBuilder).append(!isPlain ? "</code>]" : ']');
      }
      if (!referenceBuilder.isEmpty()) {
        result.append('[')
          .append(referenceBuilder.toString()
                    .replace("[", "\\[")
                    .replace("]", "\\]"))
          .append(']');
      }
    }
  }
  

  /// Visitor implementation for [Element#filter(NodeFilter)].
  /// 
  /// This implementation aims at converting most of the simple HTML features into their Markdown equivalent.
  /// Due to the complexity of trying to convert everything into Markdown, the code is pretty close to just handwritten heuristics  
  private static class HtmlToMarkdownVisitor implements NodeFilter {
    /// Used to mark *inline* content as raw data, that should not be converted
    static final String INTERNAL_TAG_INLINE_RAW = "jbr-internal-inline";
    /// Used to mark *non*-inline tags, that must start on a new line
    static final String INTERNAL_TAG_JDOC_TAG = "jbr-internal-jdoc";
    
    /// Detect multiple line breaks which are not at the end of the string
    private static final Pattern MULTI_LINE_BREAK = Pattern.compile("\n\\s*(?=\n)");
    /// Detect Markdown like features: titles, blockquotes  
    private static final Pattern MARKDOWN_LINE_STRUCT = Pattern.compile("(?m)(^ *)(#{1,6} |> )");
    /// Detect Markdown like features: (un)ordered lists
    private static final Pattern MARKDOWN_BLOCK_STRUCT = Pattern.compile("(?m)(^ {2,})(- |\\d[.)] )");
    
    /// Denotes a line break in [#appendBreaks(int)]
    private static final int LINE_BREAK = 1;
    /// Denotes a paragraph break in [#appendBreaks(int)]
    private static final int PARAGRAPH_BREAK = 2;

    /// Current buffer of Markdown converted input 
    private final StringBuilder result = new StringBuilder();
    /// Indicates the item count of each list and the number of sublists
    private final ArrayDeque<Integer> listContext = new ArrayDeque<>(1);

    HtmlToMarkdownVisitor(){}
    
    HtmlToMarkdownVisitor(int size) {
      result.ensureCapacity(size);
    }

    @Override
    public @NotNull FilterResult head(@NotNull Node node, int depth) {
      String nodeName = node.nodeName();
      if (nodeName.equals("body")) return FilterResult.CONTINUE;

      if(shouldSkipConversion(node)) {
        appendWithoutConversion(node);
        return FilterResult.SKIP_ENTIRELY;
      }

      switch (nodeName) {
        case "a" -> {
          if (!shouldTransformHtmlLink()) {
            appendWithoutConversion(node);
            return FilterResult.SKIP_ENTIRELY;
          }
          result.append("[");
        }
        case "i", "em" -> result.append('_');
        case "b", "strong" -> result.append("**");
        case "hr" -> appendWithNewLineIfNeeded("___\n");
        case "p", "br" -> appendBreaks(PARAGRAPH_BREAK);
        case "img" -> result.append("![").append(node.attr("alt")).append("](").append(node.attr("src")).append(')');
        case INTERNAL_TAG_JDOC_TAG -> {
          // Special handling for Javadoc tags, respect user adding an empty line, even if meaningless once rendered.
          Node previousSibling = node.previousSibling();
          if(previousSibling != null && hasEnoughLineBreaks(previousSibling.nodeValue(), PARAGRAPH_BREAK, true)) {
            appendBreaks(PARAGRAPH_BREAK);  
          } else {
            appendBreaks(LINE_BREAK); 
          }
        }

        case "h1", "h2", "h3", "h4", "h5", "h6" -> {
          // Titles are a single-line construct, we post process the output in hopes of matching the original rendering
          // There are "impossible cases" like when there are subtags that are a block construct.
          // For now, we bury our heads in the sand.
          HtmlToMarkdownVisitor subVisitor = new HtmlToMarkdownVisitor();
          node.childNodes().forEach(child -> child.filter(subVisitor));

          appendWithNewLineIfNeeded("#".repeat(Integer.parseInt(nodeName.substring(1))));
          result.append(' ').append(subVisitor.getResult().replace("\n", " ").replace("  ", " "));
          return FilterResult.SKIP_CHILDREN;
        }
        
        case "blockquote" -> {
          if (isBlockquoteTagCodeBlock(node)) {
            // Combining blockquote and <pre> tag gives the same behavior as a code block
            appendCodeBlock(getSingleRelevantChild(node));
            return FilterResult.SKIP_ENTIRELY;
          }
          else {
            appendBreaks(LINE_BREAK);
            // The blockquote a funny one, as it needs to pad every new line with its starting construct
            HtmlToMarkdownVisitor subVisitor = new HtmlToMarkdownVisitor();
            node.childNodes().forEach(child -> {
              child.filter(subVisitor);
            });
            appendWithNewLineIfNeeded("> " + subVisitor.getResult().strip().replace("\n", "\n> "));
            return FilterResult.SKIP_CHILDREN;
          }
        }

        case "pre" -> {
          if (!isPreTagCodeBlock(node)) {
            appendWithNewLineIfNeeded("<pre>\n");
            node.html(result);
            appendWithNewLineIfNeeded("</pre>\n");
            return FilterResult.SKIP_ENTIRELY;
          }
        }

        case INTERNAL_TAG_INLINE_RAW -> {
          appendRaw(node);
          return FilterResult.SKIP_ENTIRELY;
        }

        /*
         * This case is slightly tricky since the code tag will show up in:
         * [<pre>] [...] <code> [<jbr-internal-inline>] ... [</jbr-internal-inline>] </code> [</pre>]
         *
         * depending on the setup:
         * - inline code block, interpret sub tags
         * - inline code block, raw sub tags
         * - code block, raw sub tags
         */
        case "code" -> {
          if (isCodeTagCodeBlock(node)) {
            appendCodeBlock(node);
            return FilterResult.SKIP_ENTIRELY;
          }
          else {
            result.append("`");
          }
        }

        case "ul", "ol" -> {
          if(listContext.isEmpty()) {
            appendBreaks(PARAGRAPH_BREAK);
          }
          listContext.push(0);
        }
        case "li" -> {
          // This line handles list items without a proper ul/ol parent tag
          appendBreaks(listContext.isEmpty() ? PARAGRAPH_BREAK : LINE_BREAK);

          int newItemListAmount = listContext.isEmpty() ? 1 : listContext.peek() + 1;
          appendWithIndent(Math.max(4 * listContext.size() - 1, 3), 
                                     node.parentNameIs("ol") ? newItemListAmount + ". " :  "- " );
          listContext.pollLast();
          listContext.push(newItemListAmount);
        }

        case "#text" -> {
          if (node.nodeValue().isBlank()) {
            if (node.nodeValue().contains("\n")) {
              appendBreaks(LINE_BREAK);
            }
            break;
          }

          // Remove consecutive newlines and space which is before the last line
          String cleanedText = MULTI_LINE_BREAK.matcher(node.nodeValue()).replaceAll("");
          cleanedText = cleanedText
            .replace("~", "\\~")
            .replace("`", "\\`")
            .replace("_", "\\_")
            .replace("*", "\\*");
          cleanedText = MARKDOWN_LINE_STRUCT.matcher(cleanedText).replaceAll("$1\\\\$2");
          cleanedText = MARKDOWN_BLOCK_STRUCT.matcher(cleanedText).replaceAll("$1\\\\$2");

          if(!listContext.isEmpty()) {
            if(node.siblingIndex() == 0) {
              // Force the first text node to be in front of the list item struct
              cleanedText = cleanedText.stripLeading();
            }
            // Indent the list text to be aligned with the first line of the item list
            appendWithIndent(Math.max(4 * listContext.size() + 1, 5), cleanedText);
            break;
          }

          appendWithLineBreakLimits(cleanedText);
        }
        
        default -> {
          appendWithoutConversion(node);
          return FilterResult.SKIP_ENTIRELY;
        }
      }

      return FilterResult.CONTINUE;
    }

    @Override
    public @NotNull FilterResult tail(Node node, int depth) {
      String nodeName = node.nodeName();
      if (nodeName.equals("body")) return FilterResult.CONTINUE;

      switch (nodeName) {
        case "i", "em" -> result.append('_');
        case "b", "strong" -> result.append("**");
        case "h1", "h2", "h3", "h4", "h5", "h6" -> appendBreaks(LINE_BREAK);
        case "p", "blockquote" -> appendBreaks(PARAGRAPH_BREAK);
        case "a" -> result.append("](").append(node.attr("href")).append(')');
        case "#text", "pre", "hr", "br", "li", INTERNAL_TAG_JDOC_TAG -> {} // No op, handled in head method

        case "code" -> {
          if(!isCodeTagCodeBlock(node)) {
            result.append("`");
          }
        }

        case "ol", "ul" -> {
          if (!listContext.isEmpty()) {
            listContext.pop();
          }
          if(listContext.isEmpty()) {
            appendBreaks(PARAGRAPH_BREAK);
          }
        }
      }
      return FilterResult.CONTINUE;
    }

    /// The `<blockquote>` mimics a codeblock when paired with the `<pre>` tag
    /// @return Whether the `<blockquote>` tag should be considered a code block
    private static boolean isBlockquoteTagCodeBlock(Node node) {
      Node child = getSingleRelevantChild(node);
      return child != null && child.nameIs("pre");
    }

    /// The `<pre>` tag has a different translation if it can be considered a code block
    /// @return Whether the `<pre>` tag should be considered a code block
    private static boolean isPreTagCodeBlock(Node node) {
      return (node.childNodeSize() == 1 && node.childNode(0).nodeName().equals("code"))
             || (node.parentNameIs("blockquote") && isBlockquoteTagCodeBlock(node.parentNode()));
    }

    /// Same as [#isPreTagCodeBlock] but from the `<code>` tag perspective
    private static boolean isCodeTagCodeBlock(Node node) {
      return node.parentNameIs("pre") && isPreTagCodeBlock(node.parentNode());
    }

    /// A significant child is characterized by a unique node that has a non-blank text value, or a tag element
    ///
    /// @return The single significant child if found
    private static @Nullable Node getSingleRelevantChild(Node node) {
      Node singleRelevantChild = null;
      for (Node child : node.childNodes()) {
        if (!child.nodeName().equals("#text") || !child.nodeValue().isBlank()) {
          if (singleRelevantChild != null) return null;
          singleRelevantChild = child;
        }
      }
      return singleRelevantChild;
    }

    /// @see <a href="https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html#see"> To see why `a` tags shouldn't be converted</a>
    private boolean shouldTransformHtmlLink() {
      return !StringUtil.endsWithIgnoreWhitespaces(result, "@see");
    }

    /// Append the text with a new line if necessary for the Markdown syntax
    private void appendWithNewLineIfNeeded(String text) {
      appendBreaks(LINE_BREAK);
      result.append(text);
    }

    /// Appends text, removing trailing line breaks if we already have a paragraph break
    /// This case mostly happens because we preemptively add the minimum spacing for Markdown
    /// without knowing the next input from the user.
    ///
    /// @see [#appendWithNewLineIfNeeded(String)]
    private void appendWithLineBreakLimits(String text) {
      result.append(hasParagraphBreak() ? text.stripLeading() : text);
    }
    
    /// Append text with a controlled indent space on each line.
    /// 
    /// The indent is only applied at the **start** of a line,
    /// meaning if there is already text, the first line will not have a controlled indent 
    private void appendWithIndent(int indent, String text) {
      int lastNewLine = CharArrayUtil.lastIndexOf(result, "\n", result.length() - 1);
      int lineStart = CharArrayUtil.shiftBackward(result, result.length() - 1, " ");
      
      boolean isStartLineBlank = lineStart == lastNewLine;
      
      if (isStartLineBlank) {
        // Adjust the builder start spaces
        int indentToAdd = indent - (result.length() - 1 - lineStart);
        if(indentToAdd > 0) {
          result.repeat(" ", indentToAdd);
        } else if (indentToAdd < 0) {
          result.delete(result.length() + indentToAdd, result.length());
        }
        
        text = text.stripLeading();
      }
      
      // Adjust the text start space on each following line
      text = text.replaceAll("\n *", "\n" + " ".repeat(indent));
      
      result.append(text);
    }
    
    /// @return The raw string to be inserted.
    /// @see #appendRaw
    private static String prepareRaw(Node node) {
      // Sometimes some things are considered raw twice due to certain constructs (eg. <pre>{@code...)
      if(node.childNodeSize() == 1 && node.childNode(0).nameIs(INTERNAL_TAG_INLINE_RAW)) {
        node = node.childNode(0);
      }

      StringBuilder subBuilder = node.html(new StringBuilder());
      return StringUtil.unescapeXmlEntities(subBuilder.toString().replaceAll("</?%s>".formatted(INTERNAL_TAG_INLINE_RAW), ""));
    }
    
    /// Appends raw data, making sure to unescape anything that requires it
    private void appendRaw(Node node) {
      result.append(prepareRaw(node));
    }
    
    /// Appends a code block, making sure not to have empty lines
    private void appendCodeBlock(Node node) {
      String codeContent = prepareRaw(node);
      int startBreakIndex = codeContent.indexOf("\n");
      boolean needStartBreak = startBreakIndex == -1 
                               || !CharArrayUtil.isEmptyOrSpaces(codeContent, 0, startBreakIndex + 1);      
      
      appendWithNewLineIfNeeded(needStartBreak ? "```\n" : "```");
      result.append(codeContent.stripTrailing());
      appendWithNewLineIfNeeded("```\n");
    }

    /// Append until enough line breaks are present
    /// @param lineBreaks The number of line breaks to have, see [#PARAGRAPH_BREAK] and [#LINE_BREAK]
    private void appendBreaks(int lineBreaks) {
      
      // List behavior, ensure content is present on the first line by not adding line breaks
      if (!listContext.isEmpty()) {
        if (Strings.endsWith(result, "- ") || Strings.endsWith(result, listContext.peek() + ". ")) {
          return;
        }
      }
      
      // Default behavior
      while (!hasEnoughLineBreaks(result, lineBreaks)) {
        result.append("\n");
      }
    }

    /// @return `true` if there are enough line breaks or if we are at the start of a blank string
    private static boolean hasEnoughLineBreaks(CharSequence sequence, int lineBreaks) {
      return hasEnoughLineBreaks(sequence, lineBreaks, false);
    }

    /// @return `true` if there are enough line breaks
    /// @param strictCheck If set to `false`, a string that is blank passes the check. Useful if your input may be blank.
    private static boolean hasEnoughLineBreaks(CharSequence sequence, int lineBreaks, boolean strictCheck) {
      if (lineBreaks <= 0 || (!strictCheck && CharArrayUtil.isEmptyOrSpaces(sequence, 0, sequence.length()))) return true;

      int lastLineIndex = sequence.length() + 1;
      for (int i = 0; i < lineBreaks; i++) {
        lastLineIndex = CharArrayUtil.lastIndexOf(sequence, "\n", Math.min(lastLineIndex - 1, sequence.length()));
        
        if(lastLineIndex == -1) return false;
        if (lastLineIndex == 0) {
          if ((i+1) < lineBreaks) return false;
          break;
        } 
      }

      if (CharArrayUtil.isEmptyOrSpaces(sequence, Math.max(lastLineIndex,0), sequence.length())) return true;
      return false;
    }

    /// @return `true` if there is a paragraph break
    private boolean hasParagraphBreak() {
      return hasEnoughLineBreaks(result, PARAGRAPH_BREAK);
    }
    
    /// It is desired for this function to produce false positives rather than false negatives.
    /// @return Whether the node should be converted.
    private static boolean shouldSkipConversion(Node node) {
      if (node.attributesSize() > 0) {
        if (node.nameIs("a")) return !checkSupported(node, "href");
        if (node.nameIs("img")) return !checkSupported(node, "src", "alt");
        return true;
      }
      if (node.nameIs("ol") || node.nameIs("ul")) {
        return ContainerUtil.exists(node.childNodes(), HtmlToMarkdownVisitor::shouldSkipConversion);
      }
      return false;
    }
    
    private static boolean checkSupported(Node node, String... attributes) {
      if (node.attributesSize() > attributes.length) return false;
      return ContainerUtil.and(node.attributes(), (attribute -> ArrayUtil.contains(attribute.getKey(), attributes)));
    }
    
    /// Appends the user input with no conversion
    private void appendWithoutConversion(Node node) {
      if(node instanceof Element element) {
        if (element.tag().isBlock()) {
          appendBreaks(LINE_BREAK);
          result.append(node.outerHtml());
          appendBreaks(LINE_BREAK);
        } else {
          result.append(element.outerHtml());
        }
      }
    }


    
    private String getResult() {
      return result.toString();
    }
  }
}
