// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SnippetMarkup {
  private final @NotNull List<@NotNull MarkupNode> myNodes;
  
  // \\S+ = language-dependent comment start token, like "//" or "#"
  private static final Pattern MARKUP_TAG = Pattern.compile("\\S+\\s*@(start|end|highlight|replace|link)\\s*(\\S.+?)?\\s*(:?)\\s*$");

  private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)\\s*(=\\s*(['\"]?)(\\S+)\\3)?");

  private static final Map<String, Set<String>> ALLOWED_ATTRS =
    Map.of(
      "start", Set.of("region"),
      "end", Set.of("region"),
      "highlight", Set.of("substring", "regex", "region", "type"),
      "replace", Set.of("substring", "regex", "region", "replacement"),
      "link", Set.of("substring", "regex", "region", "target", "type")
    );

  private SnippetMarkup(@NotNull List<@NotNull MarkupNode> nodes) { 
    myNodes = nodes;
  }

  /**
   * Type of the highlighting
   * @see Highlight
   */
  public enum HighlightType {
    BOLD, ITALIC, HIGHLIGHTED
  }

  /**
   * Type of the link
   * @see Link
   */
  public enum LinkType {
    LINK, LINKPLAIN
  }

  /**
   * Element of snippet text
   */
  public sealed interface MarkupNode {
    /**
     * @return range in the parent PSI element that corresponds to this element
     */
    @NotNull TextRange range();
  }

  /**
   * A markup tag applicable to a particular part of original text
   */
  public sealed interface LocationMarkupNode extends MarkupNode {
    /**
     * @return substring text to which this node is applicable
     */
    @Nullable String substring();

    /**
     * @return regular expression, so this node is applicable to the matching substrings
     */
    @Nullable String regex();

    /**
     * @return if present, defines a region name to which this tag is applicable. If null, then the tag is applicable to the next 
     * {@link PlainText} node.
     */
    @Nullable String region();
  }

  /**
   * A node that represents a chunk of plain text, ending with a linebreak
   * 
   * @param content text content (excluding the final linebreak)
   */
  public record PlainText(@NotNull TextRange range, @NotNull String content) implements MarkupNode {
    public PlainText {
      if (range.getLength() != content.length()) {
        throw new IllegalArgumentException();
      }
    }

    /**
     * @param start start offset of substring
     * @param end end offset of substring
     * @return PlainText object that corresponds to the substring of this object
     */
    @NotNull PlainText substring(@SuppressWarnings("SameParameterValue") int start, int end) {
      int offset = range().getStartOffset();
      return new PlainText(TextRange.create(offset + start, offset + end), content.substring(start, end));
    }
  }

  private record Attribute(@NotNull TextRange range, @NotNull String key, @Nullable String value) implements MarkupNode {
  }

  /**
   * Represents a {@code @start} tag
   * 
   * @param region name of the region
   */
  public record StartRegion(@NotNull TextRange range, @NotNull String region) implements MarkupNode {
  }

  /**
   * Represents an {@code @end} tag
   * @param region name of the region to close. If null then the most recently open region is closed by this tag 
   */
  public record EndRegion(@NotNull TextRange range, @Nullable String region) implements MarkupNode {
  }

  /**
   * Represents a malformed markup chunk
   *  
   * @param message localized error message
   */
  public record ErrorMarkup(@NotNull TextRange range, @NlsContexts.ParsingError @NotNull String message) implements MarkupNode {
  }

  /**
   * Represents a {@code @highlight} tag
   * 
   * @param type highlighting type
   */
  public record Highlight(@NotNull TextRange range, @Nullable String substring, @Nullable String regex, @Nullable String region,
                   @NotNull HighlightType type) implements LocationMarkupNode {
  }

  /**
   * Represents a {@code @replace} tag
   *
   * @param replacement replacement text
   */
  public record Replace(@NotNull TextRange range, @Nullable String substring, @Nullable String regex, @Nullable String region,
                 @NotNull String replacement) implements LocationMarkupNode {
  }

  /**
   * Represents a {@code @link} tag
   *
   * @param target link target
   * @param linkType link type
   */
  public record Link(@NotNull TextRange range, @Nullable String substring, @Nullable String regex, @Nullable String region, @NotNull String target,
              @NotNull LinkType linkType) implements LocationMarkupNode {
  }

  public static @NotNull SnippetMarkup parse(@NotNull String text) {
    return parse(preparse(text));
  }

  public static @NotNull SnippetMarkup parse(@NotNull PsiSnippetDocTagBody body) {
    return parse(preparse(body));
  }

  private static @NotNull List<@NotNull PlainText> preparse(@NotNull String text) {
    List<PlainText> output = new ArrayList<>();
    int pos = 0;
    while (true) {
      int nextPos = text.indexOf('\n', pos);
      if (nextPos == -1) {
        output.add(new PlainText(TextRange.create(pos, text.length()), text.substring(pos)));
        break;
      }
      else {
        output.add(new PlainText(TextRange.create(pos, nextPos), text.substring(pos, nextPos)));
        pos = nextPos + 1;
      }
    }
    return output;
  }

  private static @NotNull List<@NotNull PlainText> preparse(@NotNull PsiSnippetDocTagBody body) {
    List<PlainText> output = new ArrayList<>();
    for (PsiElement element : body.getContent()) {
      output.add(new PlainText(element.getTextRangeInParent(), element.getText()));
    }
    return output;
  }

  private static @NotNull SnippetMarkup parse(@NotNull List<@NotNull PlainText> preparsed) {
    return new SnippetMarkup(preparsed.stream().flatMap(SnippetMarkup::parseLine).toList());
  }

  private static Stream<MarkupNode> parseLine(@NotNull PlainText text) {
    String content = text.content();
    Matcher matcher = MARKUP_TAG.matcher(content);
    if (!matcher.find()) return Stream.of(text);
    int start = matcher.start();
    PlainText prev = text.substring(0, start);
    String tagName = matcher.group(1);
    List<MarkupNode> attrs = parseAttributes(text.range().getStartOffset() + matcher.start(2), matcher.group(2),
                                             ALLOWED_ATTRS.get(tagName));
    List<ErrorMarkup> errors = new ArrayList<>(ContainerUtil.filterIsInstance(attrs, ErrorMarkup.class));
    Map<String, String> attrValues = StreamEx.of(attrs).select(Attribute.class).toMap(Attribute::key, Attribute::value);
    TextRange range = TextRange.create(matcher.start(1), matcher.end()).shiftRight(text.range().getStartOffset());
    boolean hasColon = !matcher.group(3).isEmpty();
    MarkupNode node = switch (tagName) {
      case "start" -> {
        String region = attrValues.get("region");
        if (region == null) {
          yield new ErrorMarkup(range, JavaBundle.message("javadoc.snippet.error.missing.required.attribute", "@start", "region"));
        }
        yield new StartRegion(range, region);
      }
      case "end" -> new EndRegion(range, attrValues.get("region"));
      case "highlight" -> {
        Attribute typeAttr =
          (Attribute)ContainerUtil.find(attrs, n -> n instanceof Attribute attr && attr.key().equals("type"));
        HighlightType type = null;
        if (typeAttr != null) {
          type = ContainerUtil.find(HighlightType.values(),
                                    ht -> ht.name().toLowerCase(Locale.ROOT).equals(typeAttr.value()));
          if (type == null) {
            errors.add(new ErrorMarkup(typeAttr.range(), JavaBundle.message("javadoc.snippet.error.unknown.highlight.type", typeAttr.value())));
          }
        }
        yield new Highlight(range, attrValues.get("substring"), attrValues.get("regex"), attrValues.get("region"),
                            Objects.requireNonNullElse(type, HighlightType.HIGHLIGHTED));
      }
      case "replace" -> {
        String replacement = attrValues.get("replacement");
        if (replacement == null) {
          errors.add(new ErrorMarkup(range, JavaBundle.message("javadoc.snippet.error.missing.required.attribute", "@replace", "replacement")));
          replacement = "";
        }
        yield new Replace(range, attrValues.get("substring"), attrValues.get("regex"), attrValues.get("region"), replacement);
      }
      case "link" -> {
        String target = attrValues.get("target");
        if (target == null) {
          errors.add(new ErrorMarkup(range, JavaBundle.message("javadoc.snippet.error.missing.required.attribute", "@link", "target")));
          target = "";
        }
        Attribute typeAttr =
          (Attribute)ContainerUtil.find(attrs, n -> n instanceof Attribute attr && attr.key().equals("type"));
        LinkType type = null;
        if (typeAttr != null) {
          type = ContainerUtil.find(LinkType.values(),
                                    ht -> ht.name().toLowerCase(Locale.ROOT).equals(typeAttr.value()));
          if (type == null) {
            errors.add(new ErrorMarkup(typeAttr.range(), JavaBundle.message("javadoc.snippet.error.unknown.link.type", typeAttr.value())));
          }
        }
        yield new Link(range, attrValues.get("substring"), attrValues.get("regex"), attrValues.get("region"), 
                       target, Objects.requireNonNullElse(type, LinkType.LINK));
      }
      default -> throw new AssertionError("Unexpected tag: " + tagName);
    };
    return prev.content().isBlank() ? StreamEx.of(node).append(errors) :
            hasColon ? StreamEx.of(prev, node).append(errors) : 
            StreamEx.of(node).append(errors).append(prev);
  }

  private static List<MarkupNode> parseAttributes(int offset, @Nullable String attrs, Set<String> allowedAttrs) {
    if (attrs == null) {
      return List.of();
    }
    Matcher matcher = ATTRIBUTE.matcher(attrs);
    int pos = 0;
    List<MarkupNode> result = new ArrayList<>();
    Set<String> used = new HashSet<>();
    while (true) {
      boolean found = matcher.find(pos);
      if (!found) {
        if (pos < attrs.length()) {
          result.add(new ErrorMarkup(TextRange.create(offset + pos, offset + attrs.length()),
                                     JavaBundle.message("javadoc.snippet.error.malformed.attribute")));
        }
        break;
      }
      if (!attrs.substring(pos, matcher.start()).isBlank()) {
        result.add(new ErrorMarkup(TextRange.create(offset + pos, offset + matcher.start()),
                                   JavaBundle.message("javadoc.snippet.error.malformed.attribute")));
        break;
      }
      String key = matcher.group(1);
      TextRange range = TextRange.create(offset + matcher.start(), offset + matcher.end());
      if (!allowedAttrs.contains(key)) {
        result.add(new ErrorMarkup(range, JavaBundle.message("javadoc.snippet.error.unsupported.attribute", key)));
      }
      else if (!used.add(key)) {
        result.add(new ErrorMarkup(range, JavaBundle.message("javadoc.snippet.error.duplicate.attribute", key)));
      }
      else {
        result.add(new Attribute(range, key, Objects.requireNonNullElse(matcher.group(4), "")));
      }
      pos = matcher.end();
    }
    return result;
  }
  
  public interface SnippetVisitor {
    void visitPlainText(@NotNull PlainText plainText, @NotNull List<@NotNull LocationMarkupNode> activeNodes);

    void visitError(@NotNull ErrorMarkup errorMarkup);
  }

  public void visitSnippet(@Nullable String region, SnippetVisitor visitor) {
    Stack<String> regions = new Stack<>();
    Map<String, List<LocationMarkupNode>> active = new LinkedHashMap<>();
    for (int i = 0; i < myNodes.size(); i++) {
      MarkupNode node = myNodes.get(i);
      if (node instanceof StartRegion start) {
        regions.push(start.region());
      } else if (node instanceof EndRegion end) {
        if (end.region() == null) {
          active.remove(regions.pop());
        } else {
          regions.remove(end.region());
          active.remove(end.region());
        }
      } else if (node instanceof LocationMarkupNode loc) {
        String regionName = loc.region();
        if (regionName != null) {
          regions.push(regionName);
        }
        active.computeIfAbsent(regionName, k -> new ArrayList<>()).add(loc);
      }
      else if (node instanceof PlainText plainText) {
        if (region == null || regions.contains(region)) {
          visitor.visitPlainText(plainText, StreamEx.ofValues(active).toFlatList(Function.identity()));
        }
        active.remove(null);
      }
      else if (node instanceof ErrorMarkup errorMarkup) {
        if (region == null || regions.contains(region)) {
          visitor.visitError(errorMarkup);
        }
      }
    }
  }

  @Override
  public String toString() {
    return StringUtil.join(myNodes, "\n");
  }
}
