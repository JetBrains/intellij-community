// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.DocumentationImageResolver;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.impl.EditorCssFontResolver;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SmartList;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.html.UtilsKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.codeInsight.documentation.DocumentationHtmlUtil.*;
import static com.intellij.lang.documentation.DocumentationMarkup.*;
import static com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions;

@Internal
public abstract class DocumentationEditorPane extends JEditorPane implements Disposable {
  private static final Color BACKGROUND_COLOR = JBColor.lazy(() -> {
    ColorKey colorKey = EditorColors.DOCUMENTATION_COLOR;
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
  private StyleSheet myCurrentDefaultStyleSheet = null;
  private Dimension myCachedPreferredSize = null;
  private final MutableStateFlow<Color> editorBackgroundFlow;

  protected DocumentationEditorPane(
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull DocumentationImageResolver imageResolver,
    @NotNull Function<? super @NotNull String, ? extends @Nullable Icon> iconResolver
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
    editorBackgroundFlow = StateFlowKt.MutableStateFlow(getBackground());
    HTMLEditorKit editorKit = new HTMLEditorKitBuilder()
      .replaceViewFactoryExtensions(getIconsExtension(iconResolver),
                                    Extensions.BASE64_IMAGES,
                                    Extensions.INLINE_VIEW_EX,
                                    Extensions.PARAGRAPH_VIEW_EX,
                                    Extensions.LINE_VIEW_EX,
                                    Extensions.BLOCK_VIEW_EX,
                                    Extensions.FIT_TO_WIDTH_IMAGES,
                                    Extensions.WBR_SUPPORT)
      .withFontResolver(EditorCssFontResolver.getGlobalInstance()).build();
    updateDocumentationPaneDefaultCssRules(editorKit);

    addPropertyChangeListener(evt -> {
      var propertyName = evt.getPropertyName();
      if ("background".equals(propertyName) || "UI".equals(propertyName)) {
        updateDocumentationPaneDefaultCssRules(editorKit);
        editorBackgroundFlow.setValue(getBackground());
      }
    });

    setEditorKit(editorKit);
    setBorder(JBUI.Borders.empty());
  }

  @Override
  public void dispose() {
    getCaret().setVisible(false); // Caret, if blinking, has to be deactivated.
  }

  @Override
  public @Nls String getText() {
    return myText;
  }

  @Override
  public void setText(@Nls String t) {
    myText = t;
    myCachedPreferredSize = null;
    super.setText(t);
  }

  public StateFlow<Color> getEditorBackgroundFlow() {
    return editorBackgroundFlow;
  }

  private void updateDocumentationPaneDefaultCssRules(@NotNull HTMLEditorKit editorKit) {
    StyleSheet editorStyleSheet = editorKit.getStyleSheet();
    if (myCurrentDefaultStyleSheet != null) {
      editorStyleSheet.removeStyleSheet(myCurrentDefaultStyleSheet);
    }
    myCurrentDefaultStyleSheet = new StyleSheet();
    for (String rule : getDocumentationPaneDefaultCssRules(getBackground())) {
      myCurrentDefaultStyleSheet.addRule(rule);
    }
    editorStyleSheet.addStyleSheet(myCurrentDefaultStyleSheet);
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
    myCachedPreferredSize = null;
    doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
    if (doc instanceof StyledDocument) {
      doc.putProperty("imageCache", new DocumentationImageProvider(this, myImageResolver));
    }
  }

  @NotNull
  Dimension getPackedSize(int minWidth, int maxWidth) {
    int width = Math.min(
      Math.max(Math.max(definitionPreferredWidth(), getMinimumSize().width), minWidth),
      maxWidth
    );
    int height = getPreferredHeightByWidth(width);
    return new Dimension(width, height);
  }

  private int getPreferredHeightByWidth(int width) {
    if (myCachedPreferredSize != null && myCachedPreferredSize.width == width) {
      return myCachedPreferredSize.height;
    }
    setSize(width, Short.MAX_VALUE);
    Dimension result = getPreferredSize();
    myCachedPreferredSize = new Dimension(width, result.height);
    return myCachedPreferredSize.height;
  }

  int getPreferredWidth() {
    int definitionPreferredWidth = definitionPreferredWidth();
    return definitionPreferredWidth < 0 ? getPreferredSize().width
                                        : Math.max(definitionPreferredWidth, getMinimumSize().width);
  }

  private int definitionPreferredWidth() {
    int preferredDefinitionWidth = Math.max(getPreferredSectionsWidth(CLASS_DEFINITION),
                                            getPreferredSectionsWidth(CLASS_DEFINITION_SEPARATED));
    if (preferredDefinitionWidth < 0) {
      return -1;
    }
    int preferredLocationWidth = getPreferredSectionsWidth(CLASS_BOTTOM);
    int preferredCodeBlockWidth = getPreferredSectionsWidth("styled-code");
    int preferredContentWidth = getPreferredContentWidth(getDocument().getLength());
    return Math.max(Math.max(preferredCodeBlockWidth, preferredContentWidth), Math.max(preferredDefinitionWidth, preferredLocationWidth));
  }

  private int getPreferredSectionsWidth(String sectionClassName) {
    var definitions = findSections(getUI().getRootView(this), sectionClassName);
    return StreamEx.of(definitions).mapToInt(it -> getPreferredWidth(it)).max().orElse(-1);
  }

  private static int getPreferredWidth(View view) {
    var result = (int)view.getPreferredSpan(View.X_AXIS);
    if (result > 0) {
      result += UtilsKt.getWidth(UtilsKt.getCssMargin(view));
      var parent = view.getParent();
      while (parent != null) {
        result += UtilsKt.getWidth(UtilsKt.getCssMargin(parent)) + UtilsKt.getWidth(UtilsKt.getCssPadding(parent));
        parent = parent.getParent();
      }
    }
    return result;
  }

  private static @NotNull List<View> findSections(@NotNull View view, String sectionClassName) {
    var queue = new ArrayList<View>();
    queue.add(view);

    var result = new SmartList<View>();
    while (!queue.isEmpty()) {
      var cur = queue.remove(queue.size() - 1);
      if (cur == null) continue;
      if (sectionClassName.equals(cur.getElement().getAttributes().getAttribute(HTML.Attribute.CLASS))) {
        result.add(cur);
      }
      for (int i = 0; i < cur.getViewCount(); i++) {
        queue.add(cur.getView(i));
      }
    }
    return result;
  }

  private static int getPreferredContentWidth(int textLength) {
    // Heuristics to calculate popup width based on the amount of the content.
    // The proportions are set for 4 chars/1px in range between 200 and 1000 chars.
    // 200 chars and less is 300px, 1000 chars and more is 500px.
    // These values were calculated based on experiments with varied content and manual resizing to comfortable width.
    final int contentLengthPreferredSize;
    if (textLength < 200) {
      contentLengthPreferredSize = getDocPopupPreferredMinWidth();
    }
    else if (textLength > 200 && textLength < 1000) {
      contentLengthPreferredSize = getDocPopupPreferredMinWidth() +
                                   (textLength - 200) * (getDocPopupPreferredMaxWidth() - getDocPopupPreferredMinWidth()) / (1000 - 200);
    }
    else {
      contentLengthPreferredSize = getDocPopupPreferredMaxWidth();
    }
    return JBUIScale.scale(contentLengthPreferredSize);
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
          //noinspection deprecation
          scrollRectToVisible(modelToView(startOffset));
        }
      }
      catch (BadLocationException e) {
        Logger.getInstance(DocumentationEditorPane.class).warn("Error highlighting link", e);
      }
    }
    else if (myHighlightedTag != null) {
      highlighter.removeHighlight(myHighlightedTag);
      myHighlightedTag = null;
    }
  }

  @Nullable
  String getLinkHref(int n) {
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
}
