// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.ide.nls.NlsMessages;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.javadoc.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class SnippetMarkup {
  private final @NotNull List<@NotNull MarkupNode> myNodes;
  private final @NotNull Map<@NotNull String, @NotNull MarkupNode> myRegionStarts;
  private final @NotNull PsiElement myContext;
  private final @NotNull BitSet myTextOffsets = new BitSet();

  private static final Map<String, Set<String>> ALLOWED_ATTRS =
    Map.of(
      "start", Set.of("region"),
      "end", Set.of("region"),
      "highlight", Set.of("substring", "regex", "region", "type"),
      "replace", Set.of("substring", "regex", "region", "replacement"),
      "link", Set.of("substring", "regex", "region", "target", "type")
    );

  private static final String ALLOWED_TAGS = "start|end|highlight|replace|link";
  private static final Pattern MARKUP_SPEC = Pattern.compile("(?://|#|rem|REM|')\\s*(@(?:" + ALLOWED_TAGS + ")(?:\\s.+)?)$");

  private static final Pattern MARKUP_TAG = Pattern.compile("@(" + ALLOWED_TAGS + ")\\s*");

  private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)\\s*(=\\s*('([^']*)'|\"([^\"]*)\"|(\\S*)))?\\s*");

  private SnippetMarkup(@NotNull List<@NotNull MarkupNode> nodes, @NotNull PsiElement context) {
    myNodes = nodes;
    myRegionStarts = StreamEx.of(nodes)
      .mapToEntry(n -> n instanceof StartRegion start ? start.region() :
                       n instanceof LocationMarkupNode node ? node.region() :
                       null, Function.identity())
      .removeKeys(k -> k == null || k.isEmpty())
      .distinctKeys()
      .toImmutableMap();
    myContext = context;
    StreamEx.of(nodes)
      .select(PlainText.class)
      .forEach(text -> myTextOffsets.set(text.range().getStartOffset(), text.range().getEndOffset()));
  }

  public @NotNull PsiElement getContext() {
    return myContext;
  }

  /**
   * @param region region
   * @return starting node of the region
   */
  public @Nullable MarkupNode getRegionStart(@NotNull String region) {
    return myRegionStarts.get(region);
  }

  /**
   * @param region region to process; null for the whole snippet
   * @return common indent of all the lines in the region
   */
  public int getCommonIndent(@Nullable String region) {
    var visitor = new SnippetVisitor() {
      int maxIndent = Integer.MAX_VALUE;

      @Override
      public void visitPlainText(@NotNull PlainText plainText, @NotNull List<@NotNull LocationMarkupNode> activeNodes) {
        if (maxIndent == 0) return;
        String content = plainText.content();
        if (content.isBlank()) return;
        int curIndent = 0;
        while (curIndent < content.length() && content.charAt(curIndent) != '\n' && Character.isWhitespace(content.charAt(curIndent))) {
          curIndent++;
        }
        maxIndent = Math.min(maxIndent, curIndent);
      }
    };
    visitSnippet(region, visitor);
    return visitor.maxIndent == Integer.MAX_VALUE ? 0 : visitor.maxIndent;
  }
  
  /**
   * @param region region to test; null for the whole snippet
   * @return true if any of {@code @replacement}, {@code @highlight}, or {@code @link} tags exist within the region
   */
  public boolean hasMarkup(@Nullable String region) {
    var visitor = new SnippetVisitor() {
      boolean hasMarkup = false;

      @Override
      public void visitPlainText(@NotNull PlainText plainText, @NotNull List<@NotNull LocationMarkupNode> activeNodes) {
        hasMarkup |= !activeNodes.isEmpty();
      }
    };
    visitSnippet(region, visitor);
    return visitor.hasMarkup;
  }

  public @Nullable TextRange getRegionRange(@Nullable String region) {
    var visitor = new SnippetVisitor() {
      TextRange myRange = null;

      @Override
      public void visitPlainText(@NotNull PlainText plainText, @NotNull List<@NotNull LocationMarkupNode> activeNodes) {
        myRange = myRange == null ? plainText.range() : myRange.union(plainText.range());
      }
    };
    visitSnippet(region, visitor);
    return visitor.myRange;
  }

  /**
   * @param range range to test
   * @return true if the range completely or partially contains plain text chunk(s) to display
   */
  public boolean isTextPart(@NotNull TextRange range) {
    int nextSetBit = myTextOffsets.nextSetBit(range.getStartOffset());
    return nextSetBit != -1 && nextSetBit < range.getEndOffset();
  }

  /**
   * @param region region to extract; null for the whole snippet
   * @return text of a given region, ignoring markup
   */
  public String getTextWithoutMarkup(@Nullable String region) {
    StringBuilder result = new StringBuilder();
    visitSnippet(region, new SnippetVisitor() {
      @Override
      public void visitPlainText(@NotNull PlainText plainText, @NotNull List<@NotNull LocationMarkupNode> activeNodes) {
        result.append(plainText.content());
      }
    });
    return result.toString();
  }

  public Set<String> getRegions() {
    return myRegionStarts.keySet();
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
    @Contract(pure = true)
    @NotNull TextRange range();
  }

  /**
   * An interface that specifies to which part of text content the {@link LocationMarkupNode} is applied
   */
  public sealed interface Selector {
    /**
     * @param string string to find the ranges
     * @return non-overlapping sorted ranges inside the string matched by this selector
     */
    @NotNull List<TextRange> ranges(String string);
  }

  /**
   * Substring-selector; applicable to a specific substring
   * @param substring substring to apply to
   */
  public record Substring(@NotNull String substring) implements Selector {
    @Override
    public @NotNull List<TextRange> ranges(String string) {
      int pos = 0;
      List<TextRange> ranges = new ArrayList<>();
      while (true) {
        int nextPos = string.indexOf(substring, pos);
        if (nextPos == -1) break;
        ranges.add(TextRange.from(nextPos, substring.length()));
        pos = nextPos + substring.length() + 1;
      }
      return ranges;
    }
  }

  /**
   * Regex-selector; applicable to a specific regex
   * @param pattern pattern to apply to
   */
  public record Regex(@NotNull Pattern pattern) implements Selector {
    @Override
    public @NotNull List<TextRange> ranges(String string) {
      Matcher matcher = pattern.matcher(string);
      List<TextRange> ranges = new ArrayList<>();
      while (matcher.find()) {
        ranges.add(TextRange.create(matcher.start(), matcher.end()));
      }
      return ranges;
    }
  }

  /**
   * Selector which is applicable to the whole line
   */
  public record WholeLine() implements Selector {
    @Override
    public @NotNull List<TextRange> ranges(String string) {
      return List.of(TextRange.create(0, string.length()));
    }
  }

  /**
   * A markup tag applicable to a particular part of original text
   */
  public sealed interface LocationMarkupNode extends MarkupNode {
    /**
     * @return selector to which this node is applicable
     */
    @Contract(pure = true)
    @NotNull Selector selector();

    /**
     * @return if present, defines a region name to which this tag is applicable. If null, then the tag is applicable to the next 
     * {@link PlainText} node.
     */
    @Contract(pure = true)
    @Nullable String region();
  }

  /**
   * A node that represents a chunk of plain text, ending with a linebreak
   * 
   * @param content text content (excluding the final linebreak)
   */
  public record PlainText(@NotNull TextRange range, @NotNull String content) implements MarkupNode {
  }

  private record Attribute(@NotNull TextRange range, @NotNull String key, @NotNull String value) implements MarkupNode {
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
  public record Highlight(@NotNull TextRange range, @NotNull Selector selector, @Nullable String region,
                   @NotNull HighlightType type) implements LocationMarkupNode {
  }

  /**
   * Represents a {@code @replace} tag
   *
   * @param replacement replacement text
   */
  public record Replace(@NotNull TextRange range, @NotNull Selector selector, @Nullable String region,
                 @NotNull String replacement) implements LocationMarkupNode {
    public Replace withReplacement(@NotNull String replacement) {
      return replacement.equals(this.replacement) ? this : new Replace(range, selector, region, replacement);
    }
  }

  /**
   * Represents a {@code @link} tag
   *
   * @param target link target
   * @param linkType link type
   */
  public record Link(@NotNull TextRange range, @NotNull Selector selector, @Nullable String region, @NotNull String target,
              @NotNull LinkType linkType) implements LocationMarkupNode {
  }

  @TestOnly
  public static @NotNull SnippetMarkup parse(@NotNull String text) {
    return parse(preparse(text), new FakePsiElement() {
      @Override
      public PsiElement getParent() {
        return null;
      }
    });
  }

  /**
   * @param element {@link PsiFile} or {@link PsiSnippetDocTagBody}
   * @return markup for a given element
   */
  public static @NotNull SnippetMarkup fromElement(@NotNull PsiElement element) {
    if (!(element instanceof PsiFile) && !(element instanceof PsiSnippetDocTagBody)) {
      throw new IllegalArgumentException();
    }
    return CachedValuesManager.getCachedValue(element, () -> {
      SnippetMarkup markup = element instanceof PsiSnippetDocTagBody body ? parse(preparse(body), body) :
                             parse(preparse(element.getText()), element);
      return new CachedValueProvider.Result<>(markup, element);
    });
  }

  /**
   * @param snippet snippet to create a markup for
   * @return a markup from snippet. For hybrid snippet, markup is created from the external snippet.
   * Returns null if there's no snippet body and no external snippet resolved
   */
  public static @Nullable SnippetMarkup fromSnippet(@NotNull PsiSnippetDocTagValue snippet) {
    SnippetMarkup markup = fromExternalSnippet(snippet);
    if (markup != null) {
      return markup;
    }
    PsiSnippetDocTagBody body = snippet.getBody();
    return body != null ? fromElement(body) : null;
  }
  
  /**
   * @param snippet snippet to create a markup for
   * @return markup from external snippet (ignoring body), null if there's no external snippet resolved.
   */
  public static @Nullable SnippetMarkup fromExternalSnippet(@NotNull PsiSnippetDocTagValue snippet) {
    PsiSnippetAttributeList list = snippet.getAttributeList();
    PsiSnippetAttribute refAttribute = list.getAttribute(PsiSnippetAttribute.CLASS_ATTRIBUTE);
    if (refAttribute == null) {
      refAttribute = list.getAttribute(PsiSnippetAttribute.FILE_ATTRIBUTE);
    }
    if (refAttribute == null) return null;
    PsiSnippetAttributeValue attrValue = refAttribute.getValue();
    if (attrValue == null) return null;
    PsiReference ref = attrValue.getReference();
    if (ref == null || !(ref.resolve() instanceof PsiFile file)) return null;
    return fromElement(file);
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
        output.add(new PlainText(TextRange.create(pos, nextPos + 1), text.substring(pos, nextPos + 1)));
        pos = nextPos + 1;
      }
    }
    return output;
  }

  private static @NotNull List<@NotNull PlainText> preparse(@NotNull PsiSnippetDocTagBody body) {
    List<PlainText> output = new ArrayList<>();
    PsiElement[] children = body.getChildren();
    boolean first = true;
    for (PsiElement element : children) {
      if (element instanceof PsiDocToken token) {
        IElementType tokenType = token.getTokenType();
        if (tokenType.equals(JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)) continue;
        if (tokenType.equals(JavaDocTokenType.DOC_COMMENT_DATA)) {
          output.add(new PlainText(element.getTextRangeInParent(), element.getText()));
        }
      }
      else if (element instanceof PsiWhiteSpace) {
        String text = element.getText();
        if (text.contains("\n")) {
          int idx = output.size() - 1;
          if (idx >= 0 && !output.get(idx).content().endsWith("\n")) {
            output.set(idx, new PlainText(output.get(idx).range(), output.get(idx).content() + "\n"));
          }
          else if (first) {
            first = false;
          }
          else {
            output.add(new PlainText(element.getTextRange(), "\n"));
          }
        }
      }
    }
    return output;
  }

  private static @NotNull SnippetMarkup parse(@NotNull List<@NotNull PlainText> preparsed, @NotNull PsiElement context) {
    return new SnippetMarkup(preparsed.stream().flatMap(SnippetMarkup::parseLine).toList(), context);
  }

  private static Stream<MarkupNode> parseLine(@NotNull PlainText text) {
    String content = text.content();
    Matcher matcher = MARKUP_SPEC.matcher(content);
    if (!matcher.find()) return Stream.of(text);
    int offset = text.range().getStartOffset();
    String markupSpec = matcher.group(1).stripTrailing();
    boolean hasColon = markupSpec.endsWith(":");
    if (hasColon) {
      markupSpec = markupSpec.substring(0, markupSpec.length() - 1);
    }
    int start = matcher.start(0);
    PlainText prev = new PlainText(TextRange.create(offset, offset + start), text.content.substring(0, start) + "\n");
    start = matcher.start(1);
    List<MarkupNode> markupNodes = new ArrayList<>();
    while (!markupSpec.isEmpty()) {
      Matcher tagMatcher = MARKUP_TAG.matcher(markupSpec);
      if (!tagMatcher.find()) {
        markupNodes.add(new ErrorMarkup(TextRange.create(0, markupSpec.length()).shiftRight(start + offset),
                                        JavaBundle.message("javadoc.snippet.error.markup.tag.expected")));
        break;
      }
      if (tagMatcher.start() != 0) {
        markupNodes.add(new ErrorMarkup(TextRange.create(0, tagMatcher.start()).shiftRight(start + offset),
                                        JavaBundle.message("javadoc.snippet.error.markup.tag.expected")));
        break;
      }
      String tagName = tagMatcher.group(1);

      Set<String> allowedAttrs = ALLOWED_ATTRS.get(tagName);
      Map<String, Attribute> attrs = new HashMap<>();
      int attrPos = tagMatcher.end();
      int attrEnd = attrPos;
      while (attrPos < markupSpec.length()) {
        Matcher attrMatcher = ATTRIBUTE.matcher(markupSpec);
        boolean found = attrMatcher.find(attrPos);
        if (!found || attrMatcher.start() != attrPos) break;
        String key = attrMatcher.group(1);
        attrEnd = attrMatcher.end(2);
        if (attrEnd == -1) {
          attrEnd = attrMatcher.end(1);
        }
        TextRange attrRange = TextRange.create(attrMatcher.start(), attrEnd).shiftRight(start + offset);
        if (!allowedAttrs.contains(key)) {
          markupNodes.add(new ErrorMarkup(attrRange, JavaBundle.message("javadoc.snippet.error.unsupported.attribute", tagName, key)));
        }
        else if (attrs.containsKey(key)) {
          markupNodes.add(new ErrorMarkup(attrRange, JavaBundle.message("javadoc.snippet.error.duplicate.attribute", tagName, key)));
        }
        else {
          String value = attrMatcher.group(4);
          if (value == null) value = attrMatcher.group(5);
          if (value == null) value = attrMatcher.group(6);
          if (value == null) value = "";
          attrs.put(key, new Attribute(attrRange, key, value));
        }
        attrPos = attrMatcher.end();
      }
      while (attrEnd > 0 && Character.isWhitespace(markupSpec.charAt(attrEnd - 1))) {
        attrEnd--;
      }
      TextRange range = TextRange.create(0, attrEnd).shiftRight(start + offset);
      validateAndAddTag(markupNodes, tagName, attrs, range);
      start += attrPos;
      markupSpec = markupSpec.substring(attrPos);
    }
    if (prev.content().isBlank() && !prev.content().isEmpty()) {
      prev = new PlainText(TextRange.from(text.range().getEndOffset(), 0), "");
    }
    if (hasColon) {
      markupNodes.add(0, prev);
    }
    else {
      markupNodes.add(prev);
    }
    return markupNodes.stream();
  }

  private static void validateAndAddTag(@NotNull List<MarkupNode> markupNodes,
                                        @NotNull String tagName,
                                        @NotNull Map<String, Attribute> attrs,
                                        @NotNull TextRange range) {
    String region = getRegion(attrs);
    switch (tagName) {
      case "start" -> {
        region = getRequiredString(markupNodes, attrs, tagName, range, "region");
        if (region != null) {
          markupNodes.add(new StartRegion(range, region));
        }
      }
      case "end" -> markupNodes.add(new EndRegion(range, region));
      case "highlight" -> {
        Attribute typeAttr = attrs.get("type");
        HighlightType type = getEnumValue(markupNodes, "highlight", typeAttr, HighlightType.class, HighlightType.HIGHLIGHTED);
        Selector selector = getSelector(markupNodes, attrs, tagName);
        if (selector != null) {
          markupNodes.add(new Highlight(range, selector, region, type));
        }
        else if (region != null) {
          markupNodes.add(new StartRegion(range, region));
        }
      }
      case "replace" -> {
        String replacement = getRequiredString(markupNodes, attrs, tagName, range, "replacement");
        Selector selector = getSelector(markupNodes, attrs, tagName);
        if (replacement != null && selector != null) {
          markupNodes.add(new Replace(range, selector, region, replacement));
        }
        else if (region != null) {
          markupNodes.add(new StartRegion(range, region));
        }
      }
      case "link" -> {
        String target = getRequiredString(markupNodes, attrs, tagName, range, "target");
        Attribute typeAttr = attrs.get("type");
        LinkType type = getEnumValue(markupNodes, "link", typeAttr, LinkType.class, LinkType.LINK);
        Selector selector = getSelector(markupNodes, attrs, tagName);
        if (target != null && selector != null) {
          markupNodes.add(new Link(range, selector, region, target, type));
        }
        else if (region != null) {
          markupNodes.add(new StartRegion(range, region));
        }
      }
      default -> throw new AssertionError("Unexpected tag: " + tagName);
    }
  }

  private static <T extends Enum<T>> @NotNull T getEnumValue(@NotNull List<MarkupNode> markupNodes, @NotNull String tagName,
                                                    @Nullable Attribute attribute, @NotNull Class<T> enumClass, @NotNull T defaultValue) {
    if (attribute == null) return defaultValue;
    T value = ContainerUtil.find(enumClass.getEnumConstants(), val -> val.name().toLowerCase(Locale.ROOT).equals(attribute.value()));
    if (value == null) {
      markupNodes.add(
        new ErrorMarkup(attribute.range(), JavaBundle.message(
          "javadoc.snippet.error.unknown.enum.value",
          tagName, attribute.key(), attribute.value(),
          StreamEx.of(enumClass.getEnumConstants()).map(val -> "'" + val.name().toLowerCase(Locale.ROOT) + "'")
            .collect(NlsMessages.joiningAnd()))));
      return defaultValue;
    }
    return value;
  }

  private static @Nullable String getRegion(@NotNull Map<String, Attribute> attrs) {
    Attribute attr = attrs.get("region");
    return attr == null ? null : attr.value();
  }

  private static @Nullable String getRequiredString(@NotNull List<MarkupNode> markupNodes, @NotNull Map<String, Attribute> attrs,
                                                    @NotNull String tagName, @NotNull TextRange range, @NotNull String attrName) {
    Attribute attr = attrs.get(attrName);
    if (attr == null) {
      markupNodes.add(new ErrorMarkup(range, JavaBundle.message("javadoc.snippet.error.missing.required.attribute", tagName, attrName)));
      return null;
    }
    return attr.value();
  }
  
  private static @Nullable Selector getSelector(@NotNull List<MarkupNode> markupNodes, @NotNull Map<String, Attribute> attrs,
                                                @NotNull String tagName) {
    Attribute substring = attrs.get("substring");
    Attribute regex = attrs.get("regex");
    if (substring == null && regex == null) return new WholeLine();
    if (substring != null && regex != null) {
      markupNodes.add(new ErrorMarkup(regex.range(), JavaBundle.message("javadoc.snippet.error.both.substring.and.regex", tagName)));
      return null;
    }
    if (regex == null) return new Substring(substring.value());
    try {
      return new Regex(Pattern.compile(regex.value()));
    }
    catch (PatternSyntaxException e) {
      markupNodes.add(new ErrorMarkup(regex.range(),
                                      JavaBundle.message("javadoc.snippet.error.malformed.regular.expression", tagName, e.getMessage().replace("\r\n", "\n"))));
      return null;
    }
  }

  public interface SnippetVisitor {
    /**
     * Called for every plain text chunk
     *
     * @param plainText   text chunk
     * @param activeNodes active markup nodes to apply to this chunk
     */
    default void visitPlainText(@NotNull PlainText plainText, @NotNull List<@NotNull LocationMarkupNode> activeNodes) { }

    /**
     * Called for every markup error visited
     * @param errorMarkup error information
     */
    default void visitError(@NotNull ErrorMarkup errorMarkup) {}
  }

  /**
   * Visit elements of the snippet within given region
   * 
   * @param region region to visit
   * @param visitor visitor to use
   */
  public void visitSnippet(@Nullable String region, @NotNull SnippetVisitor visitor) {
    visitSnippet(region, false, visitor);
  }

  /**
   * Visit elements of the snippet within given region
   *
   * @param region     region to visit
   * @param preprocess if true, {@code @replace} tags will be processed automatically and not passed to the visitor;
   *                   also common indent will be stripped automatically from {@link PlainText} nodes. This option
   *                   is useful if you want to actually render markup. In this case, you only have to process
   *                   {@link Highlight} and {@link Link} tags.
   * @param visitor    visitor to use
   */
  public void visitSnippet(@Nullable String region, boolean preprocess, @NotNull SnippetVisitor visitor) {
    Stack<String> regions = new Stack<>();
    Map<String, List<LocationMarkupNode>> active = new LinkedHashMap<>();
    int commonIndent = preprocess ? getCommonIndent(region) : 0;
    for (MarkupNode node : myNodes) {
      if (node instanceof StartRegion start) {
        regions.push(start.region());
      }
      else if (node instanceof EndRegion end) {
        if (end.region() == null) {
          if (!regions.isEmpty()) {
            active.remove(regions.pop());
          }
        }
        else {
          regions.remove(end.region());
          active.remove(end.region());
        }
      }
      else if (node instanceof LocationMarkupNode loc) {
        String regionName = loc.region();
        if (regionName != null) {
          regions.push(regionName);
        }
        active.computeIfAbsent(regionName, k -> new ArrayList<>()).add(loc);
      }
      else if (node instanceof PlainText plainText) {
        if (region == null || regions.contains(region)) {
          plainText = stripIndent(plainText, commonIndent);
          List<LocationMarkupNode> flatActive = StreamEx.ofValues(active).toFlatList(Function.identity());
          if (preprocess) {
            processReplacements(visitor, plainText, flatActive);
          }
          else {
            visitor.visitPlainText(plainText, flatActive);
          }
        }
        active.values().forEach(
          list -> list.replaceAll(
            n -> n instanceof Replace repl && repl.selector() instanceof WholeLine ? repl.withReplacement("") : n));
        active.remove(null);
      }
      else if (node instanceof ErrorMarkup errorMarkup) {
        if (region == null || regions.contains(region)) {
          visitor.visitError(errorMarkup);
        }
      }
    }
  }

  private static PlainText stripIndent(PlainText plainText, int commonIndent) {
    if (commonIndent <= 0) return plainText;
    String content = plainText.content();
    int curIndent = 0;
    while (curIndent < commonIndent &&
           curIndent < content.length() &&
           content.charAt(curIndent) != '\n' &&
           Character.isWhitespace(content.charAt(curIndent))) {
      curIndent++;
    }
    if (curIndent == 0) return plainText;
    content = content.substring(curIndent);
    return new PlainText(TextRange.create(plainText.range().getStartOffset() + curIndent, plainText.range().getEndOffset()), content);
  }

  private static void processReplacements(@NotNull SnippetVisitor visitor, @NotNull PlainText plainText, @NotNull List<LocationMarkupNode> flatActive) {
    Map<Boolean, List<LocationMarkupNode>> map = StreamEx.of(flatActive).partitioningBy(n -> n instanceof Replace);
    flatActive = map.get(false);
    String content = plainText.content();
    for (LocationMarkupNode markupNode : map.get(true)) {
      Replace replace = (Replace)markupNode;
      Selector selector = replace.selector();
      String replacement = replace.replacement();
      if (selector instanceof Regex regex) {
        boolean addLineBreak = false;
        if (content.endsWith("\n")) {
          content = content.substring(0, content.length() - 1);
          addLineBreak = true;
        }
        try {
          content = regex.pattern().matcher(StringUtil.newBombedCharSequence(content, 1000)).replaceAll(replacement);
        }
        catch (StackOverflowError | ProcessCanceledException e) {
          ErrorMarkup replacementError = new ErrorMarkup(
            replace.range(), JavaBundle.message("javadoc.snippet.error.regex.too.complex", "replace", regex.pattern().pattern()));
          visitor.visitError(replacementError);
        }
        catch (IllegalArgumentException | IndexOutOfBoundsException e) {
          ErrorMarkup replacementError = new ErrorMarkup(
            replace.range(), JavaBundle.message("javadoc.snippet.error.malformed.replacement", "replace", replacement, e.getMessage()));
          visitor.visitError(replacementError);
        }
        if (addLineBreak) {
          content += "\n";
        }
      }
      else if (selector instanceof Substring substring) {
        content = content.replace(substring.substring(), replacement);
      }
      else {
        content = replacement;
      }
    }
    visitor.visitPlainText(new PlainText(plainText.range(), content), flatActive);
  }

  @Override
  public String toString() {
    return StringUtil.join(myNodes, "\n");
  }
}
