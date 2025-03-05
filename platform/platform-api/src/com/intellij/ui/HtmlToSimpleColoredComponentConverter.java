// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.awt.*;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.ui.SimpleTextAttributes.*;
import static javax.swing.text.html.HTML.Attribute.STYLE;
import static javax.swing.text.html.HTML.Tag.*;

/**
 * {@code HtmlToSimpleColoredComponentConverter} allows you to convert HTML-marked string to list of colored fragments and
 * append this fragments to {@link SimpleColoredComponent}.
 * <br>
 * By default supports following HTML tags:
 * <ul>
 * <li><b>&lt;b&gt</b> tag shows text as bold</li>
 * <li><b>&lt;i&gt</b> tag shows text as italic</li>
 * <li><b>&lt;u&gt</b> tag shows text as underlined</li>
 * <li><b>&lt;s&gt</b> tag shows text with strikethrough</li>
 * <li><b>&lt;a&gt</b> tag shows text as a link. This tag only applies style, no actions will be performed when this text is clicked</li>
 * <li>you also able to specify custom style for text using tags <b>&lt;div&gt</b> and <b>&lt;span&gt</b> with 'style' parameter. Following CSS attributes
 * are supported for 'style': <ul>
 *   <li>color</li>
 *   <li>background-color</li>
 * </ul></li>
 * </ul>
 *
 * Supported tags list can be extended by using custom {@link StyleTagHandler}
 */
@ApiStatus.Experimental
public class HtmlToSimpleColoredComponentConverter {

  private static final Logger LOG = Logger.getInstance(HtmlToSimpleColoredComponentConverter.class);

  public static final StyleTagHandler DEFAULT_TAG_HANDLER = new DefaultStyleTagHandler();

  private final StyleTagHandler myStyleTagHandler;

  public HtmlToSimpleColoredComponentConverter(StyleTagHandler mapper) {
    myStyleTagHandler = mapper;
  }

  public HtmlToSimpleColoredComponentConverter() {
    this(DEFAULT_TAG_HANDLER);
  }

  /**
   * Calculate list of {@link Fragment} from given {@code htmlString} using {@code defaultAttributes} as base {@link SimpleTextAttributes}
   * @param htmlString HTML string to parse
   * @param defaultAttributes {@link SimpleTextAttributes} used as base style. Non-styled text (without tags) will be shown used this style
   * @return list of {@link Fragment} which can be added to {@link SimpleColoredComponent}
   */
  public List<Fragment> convert(@NotNull @Nls String htmlString, SimpleTextAttributes defaultAttributes) {
    TextAttributesStack attributesStack = new TextAttributesStack(defaultAttributes);
    List<Fragment> res = new ArrayList<>();

    HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
      @Override
      public void handleText(char[] data, int pos) {
        res.add(new Fragment(new String(data), attributesStack.peek()));
      }

      @Override
      public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        String tagName = t.toString();
        SimpleTextAttributes attributesByTag = myStyleTagHandler.calcAttributes(t, a);
        if (attributesByTag != null) {
          SimpleTextAttributes newAttributes = merge( attributesStack.peek(), attributesByTag);
          attributesStack.push(tagName, newAttributes);
        }
      }

      @Override
      public void handleEndTag(HTML.Tag t, int pos) {
        attributesStack.removeFromTop(t.toString());
      }

      @Override
      public void handleError(String errorMsg, int pos) {
        //since <body> tag may be skipped for this parser
        if (errorMsg.startsWith("start.missing body")) return;

        // style and class attributes can be processed by StyleTagHandler
        if (errorMsg.startsWith("invalid.tagatt style")) return;
        if (errorMsg.startsWith("invalid.tagatt class")) return;

        LOG.error("Cannot parse HTML: [" + htmlString + "]", errorMsg);
      }
    };

    Reader reader = new StringReader(htmlString);
    try {
      new ParserDelegator().parse(reader, callback, true);
    }
    catch (IOException e) {
      LOG.error("Cannot parse HTML", e);
      return Collections.singletonList(new Fragment(StringUtil.removeHtmlTags(htmlString), defaultAttributes));
    }

    return res;
  }

  /**
   * Convert given HTML string to list of {@link Fragment} and add them to specified {@link SimpleColoredComponent}
   * @param component {@link SimpleColoredComponent} which parsed fragments should be appended to
   * @param html HTML string to parse
   * @param defaultAttributes {@link SimpleTextAttributes} used as base style. Non-styled text (without tags) will be shown used this style
   */
  public void appendHtml(@NotNull SimpleColoredComponent component, @NotNull @Nls String html,
                                @NotNull SimpleTextAttributes defaultAttributes) {
    convert(html, defaultAttributes).forEach(fragment -> component.append(fragment.myText, fragment.myAttributes));
  }

  /**
   * Describes colored fragment (part of {@link SimpleColoredComponent} component)
   */
  public static class Fragment {
    private final @NotNull @Nls String myText;
    private final @NotNull SimpleTextAttributes myAttributes;

    public Fragment(@NotNull @Nls String text, @NotNull SimpleTextAttributes attributes) {
      myText = text;
      myAttributes = attributes;
    }

    /**
     * @return fragment text
     */
    public @NotNull @Nls String getText() {
      return myText;
    }

    /**
     * @return fragment style
     */
    public @NotNull SimpleTextAttributes getAttributes() {
      return myAttributes;
    }
  }

  /**
   * Handler which converts HTML tag to {@link SimpleTextAttributes}
   */
  public interface StyleTagHandler {

    /**
     * Convert HTML tag to {@link SimpleTextAttributes}
     * @param t tag description
     * @param a attributes description
     * @return {@link SimpleTextAttributes} for given tag or {@code null} if tag is not supported
     */
    SimpleTextAttributes calcAttributes(HTML.Tag t, MutableAttributeSet a);

    /**
     * Extend current {@code StyleTagHandler} with custom handler to support extra tags
     */
    default StyleTagHandler extendWith(StyleTagHandler anotherMapper) {
      return (tag, attr) -> {
        SimpleTextAttributes anotherRes = anotherMapper.calcAttributes(tag, attr);
        return anotherRes != null ? anotherRes : calcAttributes(tag, attr);
      };
    }
  }

  private static class TextAttributesStack {
    private final Stack<Pair<String, SimpleTextAttributes>> myStack = new Stack<>();

    private TextAttributesStack(@NotNull SimpleTextAttributes defaultAttributes) {
      myStack.push(Pair.create(null, defaultAttributes));
    }

    public void push(@NotNull String tagName, @NotNull SimpleTextAttributes attributes) {
      myStack.push(Pair.create(tagName, attributes));
    }

    public void removeFromTop(@NotNull String tagName) {
      if (tagName.equalsIgnoreCase(myStack.peek().first)) myStack.pop();
    }

    public SimpleTextAttributes peek() {
      return myStack.peek().second;
    }
  }

  private static class DefaultStyleTagHandler implements StyleTagHandler {

    @Override
    public SimpleTextAttributes calcAttributes(javax.swing.text.html.HTML.Tag tag, MutableAttributeSet attr) {
      SimpleTextAttributes styleAttributes = parseStyleIfPossible(attr);

      if (tag == DIV || tag == SPAN) return styleAttributes;

      SimpleTextAttributes tagAttributes = null;
      if (tag == B) tagAttributes = REGULAR_ATTRIBUTES;
      if (tag == I) tagAttributes = REGULAR_ITALIC_ATTRIBUTES;
      if (tag == U) tagAttributes = new SimpleTextAttributes(STYLE_UNDERLINE, null);
      if (tag == S) tagAttributes = new SimpleTextAttributes(STYLE_STRIKEOUT, null);
      if (tag == A) tagAttributes = LINK_PLAIN_ATTRIBUTES;

      if (tagAttributes != null && styleAttributes != null) {
        return merge(tagAttributes, styleAttributes);
      }

      return tagAttributes;
    }

    private static SimpleTextAttributes parseStyleIfPossible(MutableAttributeSet attr) {
      Object attribute = attr.getAttribute(STYLE);
      if (attribute == null) return null;
      String styleStr = attribute.toString();
      Map<String, String> styles = Arrays.stream(styleStr.split("\\s*;\\s*"))
        .map(s -> s.split("\\s*:\\s*"))
        .collect(Collectors.toMap(s -> s[0], s -> s[1]));

      String colorString = styles.get("color");
      String backgroundString = styles.get("background-color");

      return new SimpleTextAttributes(parseColor(backgroundString), parseColor(colorString), null, STYLE_PLAIN);
    }

    private static Color parseColor(String colorStr) {
      if (colorStr == null) return null;

      colorStr = StringUtil.trimStart(colorStr, "#");
      if (colorStr.length() != 6) return null;
      return ColorUtil.fromHex(colorStr);
    }
  }
}
