// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.DocumentationImageResolver;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.impl.EditorCssFontResolver;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBHtmlEditorKit;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Map;

import static com.intellij.codeInsight.documentation.DocumentationComponent.SECTION_COLOR;

@Internal
public abstract class DocumentationEditorPane extends JEditorPane {

  private static final Color BACKGROUND_COLOR = new JBColor(() -> {
    ColorKey colorKey = DocumentationComponent.COLOR_KEY;
    EditorColorsScheme scheme = EditorColorsUtil.getColorSchemeForBackground(null);
    Color color;
    color = scheme.getColor(colorKey);
    if (color != null) {
      return color;
    }
    color = colorKey.getDefaultColor();
    if (color != null) {
      return color;
    }
    return scheme.getDefaultBackground();
  });

  private final Map<KeyStroke, ActionListener> myKeyboardActions;
  private final @NotNull DocumentationImageResolver myImageResolver;
  private @Nls String myText = ""; // getText() surprisingly crashes…, let's cache the text

  protected DocumentationEditorPane(
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull DocumentationImageResolver imageResolver
  ) {
    myKeyboardActions = keyboardActions;
    myImageResolver = imageResolver;
    enableEvents(AWTEvent.KEY_EVENT_MASK);
    setEditable(false);
    if (ScreenReader.isActive()) {
      // Note: Making the caret visible is merely for convenience
      getCaret().setVisible(true);
    }
    else {
      putClientProperty("caretWidth", 0); // do not reserve space for caret (making content one pixel narrower than component)
      UIUtil.doNotScrollToCaret(this);
    }
    setBackground(BACKGROUND_COLOR);
    JBHtmlEditorKit editorKit = new JBHtmlEditorKit(new DocumentationHtmlFactory(this), true);
    prepareCSS(editorKit);

    setEditorKit(editorKit);
    setBorder(JBUI.Borders.empty());
  }

  @Override
  public @Nls String getText() {
    return myText;
  }

  @Override
  public void setText(@Nls String t) {
    myText = t;
    super.setText(t);
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
    ActionListener listener = myKeyboardActions.get(keyStroke);
    if (listener != null) {
      listener.actionPerformed(new ActionEvent(this, 0, ""));
      e.consume();
      return;
    }
    super.processKeyEvent(e);
  }

  @Override
  protected void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    super.paintComponent(g);
  }

  @Override
  public void setDocument(Document doc) {
    super.setDocument(doc);
    doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
    if (doc instanceof StyledDocument) {
      doc.putProperty("imageCache", new DocumentationImageProvider(this, myImageResolver));
    }
  }

  @NotNull Dimension getPackedSize(int minWidth, int maxWidth) {
    int width = Math.max(Math.max(definitionPreferredWidth(), getMinimumSize().width), minWidth);
    int height = getPreferredHeightByWidth(Math.min(width, maxWidth));
    return new Dimension(width, height);
  }

  private int getPreferredHeightByWidth(int width) {
    setSize(width, Short.MAX_VALUE);
    return getPreferredSize().height;
  }

  int getPreferredWidth() {
    int definitionPreferredWidth = definitionPreferredWidth();
    return definitionPreferredWidth < 0 ? getPreferredSize().width
                                        : Math.max(definitionPreferredWidth, getMinimumSize().width);
  }

  private int definitionPreferredWidth() {
    int preferredDefinitionWidth = getPreferredSectionWidth("definition");
    int preferredLocationWidth = Math.max(getPreferredSectionWidth("bottom-no-content"), getPreferredSectionWidth("bottom"));
    if (preferredDefinitionWidth < 0) {
      return -1;
    }
    int preferredContentWidth = getPreferredContentWidth(getDocument().getLength());
    return Math.max(preferredContentWidth, Math.max(preferredDefinitionWidth, preferredLocationWidth));
  }

  private int getPreferredSectionWidth(String sectionClassName) {
    View definition = findSection(getUI().getRootView(this), sectionClassName);
    return definition == null ? -1 : (int)definition.getPreferredSpan(View.X_AXIS);
  }

  private static int getPreferredContentWidth(int textLength) {
    // Heuristics to calculate popup width based on the amount of the content.
    // The proportions are set for 4 chars/1px in range between 200 and 1000 chars.
    // 200 chars and less is 300px, 1000 chars and more is 500px.
    // These values were calculated based on experiments with varied content and manual resizing to comfortable width.
    final int contentLengthPreferredSize;
    if (textLength < 200) {
      contentLengthPreferredSize = JBUIScale.scale(300);
    }
    else if (textLength > 200 && textLength < 1000) {
      contentLengthPreferredSize = JBUIScale.scale(300) + JBUIScale.scale(1) * (textLength - 200) * (500 - 300) / (1000 - 200);
    }
    else {
      contentLengthPreferredSize = JBUIScale.scale(500);
    }
    return contentLengthPreferredSize;
  }

  private static @Nullable View findSection(@NotNull View view, @NotNull String sectionClassName) {
    if (sectionClassName.equals(view.getElement().getAttributes().getAttribute(HTML.Attribute.CLASS))) {
      return view;
    }
    for (int i = 0; i < view.getViewCount(); i++) {
      View definition = findSection(view.getView(i), sectionClassName);
      if (definition != null) {
        return definition;
      }
    }
    return null;
  }

  @Internal
  public void applyFontProps(@NotNull FontSize size) {
    Document document = getDocument();
    if (!(document instanceof StyledDocument)) {
      return;
    }
    String fontName = Registry.is("documentation.component.editor.font")
                      ? EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName()
                      : getFont().getFontName();

    // changing font will change the doc's CSS as myEditorPane has JEditorPane.HONOR_DISPLAY_PROPERTIES via UIUtil.getHTMLEditorKit
    setFont(UIUtil.getFontWithFallback(fontName, Font.PLAIN, JBUIScale.scale(size.getSize())));
  }

  private Object myHighlightedTag;

  private @Nullable HTMLDocument.Iterator getLink(int n) {
    if (n >= 0) {
      HTMLDocument document = (HTMLDocument)getDocument();
      int linkCount = 0;
      for (HTMLDocument.Iterator it = document.getIterator(HTML.Tag.A); it.isValid(); it.next()) {
        if (it.getAttributes().isDefined(HTML.Attribute.HREF) && linkCount++ == n) {
          return it;
        }
      }
    }
    return null;
  }

  void highlightLink(int n) {
    Highlighter highlighter = getHighlighter();
    HTMLDocument.Iterator link = getLink(n);
    if (link != null) {
      int startOffset = link.getStartOffset();
      int endOffset = link.getEndOffset();
      try {
        if (myHighlightedTag == null) {
          myHighlightedTag = highlighter.addHighlight(startOffset, endOffset, DocumentationLinkHighlightPainter.INSTANCE);
        }
        else {
          highlighter.changeHighlight(myHighlightedTag, startOffset, endOffset);
        }
        setCaretPosition(startOffset);
        if (!ScreenReader.isActive()) {
          // scrolling to target location explicitly, as we've disabled auto-scrolling to caret
          scrollRectToVisible(modelToView(startOffset));
        }
      }
      catch (BadLocationException e) {
        DocumentationManager.LOG.warn("Error highlighting link", e);
      }
    }
    else if (myHighlightedTag != null) {
      highlighter.removeHighlight(myHighlightedTag);
      myHighlightedTag = null;
    }
  }

  @Nullable String getLinkHref(int n) {
    HTMLDocument.Iterator link = getLink(n);
    return link != null
           ? (String)link.getAttributes().getAttribute(HTML.Attribute.HREF)
           : null;
  }

  int getLinkCount() {
    HTMLDocument document = (HTMLDocument)getDocument();
    int linkCount = 0;
    for (HTMLDocument.Iterator it = document.getIterator(HTML.Tag.A); it.isValid(); it.next()) {
      if (it.getAttributes().isDefined(HTML.Attribute.HREF)) linkCount++;
    }
    return linkCount;
  }

  /**
   * Swing HTML Editor Kit processes values in percents of 'font-size' css property really weirdly
   * and even in not a cross-platform way.
   * So we have to do some hacks to align fonts.
   */
  private static int getMonospaceFontSizeCorrection() {
    return SystemInfo.isWin10OrNewer && !ApplicationManager.getApplication().isUnitTestMode() ? 96 : 100;
  }

  private static void prepareCSS(@NotNull JBHtmlEditorKit editorKit) {
    editorKit.setFontResolver(EditorCssFontResolver.getGlobalInstance());

    int leftPadding = 8;
    int definitionTopPadding = 4;
    String linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED);
    String borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor());
    String sectionColor = ColorUtil.toHtmlColor(SECTION_COLOR);
    String editorFontStyle = "{font-family:\"" + EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER +
                             "\";font-size:" + getMonospaceFontSizeCorrection() + "%;}";

    StyleSheet styleSheet = editorKit.getStyleSheet();
    styleSheet.addRule("tt" + editorFontStyle);
    styleSheet.addRule("code" + editorFontStyle);
    styleSheet.addRule("pre" + editorFontStyle);
    styleSheet.addRule(".pre" + editorFontStyle);

    styleSheet.addRule("html { padding-bottom: 8px; }");
    styleSheet.addRule("h1, h2, h3, h4, h5, h6 { margin-top: 0; padding-top: 1px; }");
    styleSheet.addRule("a { color: " + linkColor + "; text-decoration: none;}");
    styleSheet.addRule(".definition { padding: " + definitionTopPadding + "px 17px 1px " + leftPadding +
                       "px; border-bottom: thin solid " + borderColor + "; }");
    styleSheet.addRule(".definition-only { padding: " + definitionTopPadding + "px 17px 0 " + leftPadding + "px; }");
    styleSheet.addRule(".definition-only pre { margin-bottom: 0 }");
    styleSheet.addRule(".content { padding: 5px 16px 0 " + leftPadding + "px; max-width: 100% }");
    styleSheet.addRule(".content-separated { padding: 5px 16px 5px " + leftPadding + "px; max-width: 100%;" +
                       "                     border-bottom: thin solid " + borderColor + "; }");
    styleSheet.addRule(".content-only { padding: 8px 16px 0 " + leftPadding + "px; max-width: 100% }");
    styleSheet.addRule(".bottom { padding: 3px 16px 0 " + leftPadding + "px; }");
    styleSheet.addRule(".bottom-no-content { padding: 5px 16px 0 " + leftPadding + "px; }");
    styleSheet.addRule("p { padding: 1px 0 2px 0; }");
    styleSheet.addRule("ol { padding: 0 16px 0 0; }");
    styleSheet.addRule("ul { padding: 0 16px 0 0; }");
    styleSheet.addRule("li { padding: 1px 0 2px 0; }");
    styleSheet.addRule(".grayed { color: #909090; display: inline;}");
    styleSheet.addRule(".centered { text-align: center}");

    // sections table
    styleSheet.addRule(".sections { padding: 0 16px 0 " + leftPadding + "px; border-spacing: 0; }");
    styleSheet.addRule("tr { margin: 0 0 0 0; padding: 0 0 0 0; }");
    styleSheet.addRule("table p { padding-bottom: 0}");
    styleSheet.addRule("td { margin: 4px 0 0 0; padding: 0 0 0 0; }");
    styleSheet.addRule("th { text-align: left; }");
    styleSheet.addRule("td pre { padding: 1px 0 0 0; margin: 0 0 0 0 }");
    styleSheet.addRule(".section { color: " + sectionColor + "; padding-right: 4px; white-space:nowrap;}");
  }
}
