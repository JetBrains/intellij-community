// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.DocumentationImageResolver;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.options.FontSize;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBHtmlPane;
import com.intellij.ui.components.JBHtmlPaneConfiguration;
import com.intellij.ui.components.impl.JBHtmlPaneImageResolver;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.ExtendableHTMLViewFactory;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.codeInsight.documentation.DocumentationHtmlUtil.*;
import static com.intellij.lang.documentation.QuickDocHighlightingHelper.getDefaultDocStyleOptions;

@Internal
public abstract class DocumentationEditorPane extends JBHtmlPane implements Disposable {
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

  private Dimension myCachedPreferredSize = null;

  protected DocumentationEditorPane(
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull DocumentationImageResolver imageResolver,
    @NotNull Function<? super @NotNull String, ? extends @Nullable Icon> iconResolver
  ) {
    super(
      getDefaultDocStyleOptions(() -> EditorColorsManager.getInstance().getGlobalScheme(), false),
      JBHtmlPaneConfiguration.builder()
        .keyboardActions(keyboardActions)
        .imageResolverFactory(component -> new JBHtmlPaneImageResolver(component, it -> imageResolver.resolveImage(it)))
        .iconResolver(name -> iconResolver.apply(name))
        .customStyleSheetProvider(pane -> getDocumentationPaneAdditionalCssRules(num -> (int)(pane.getContentsScaleFactor() * num)))
        .extensions(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_IMAGES)
        .build()
    );
    setBackground(BACKGROUND_COLOR);
  }

  @Override
  public void setText(@Nls String t) {
    myCachedPreferredSize = null;
    super.setText(t);
  }

  @Override
  public void setDocument(Document doc) {
    super.setDocument(doc);
    myCachedPreferredSize = null;
  }

  @Override
  public void invalidate() {
    myCachedPreferredSize = null;
    super.invalidate();
  }

  @NotNull
  Dimension getPackedSize(int minWidth, int maxWidth) {
    int width = Math.min(
      Math.max(Math.max(contentsPreferredWidth(), getMinimumSize().width), minWidth),
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
    int definitionPreferredWidth = contentsPreferredWidth();
    return definitionPreferredWidth < 0 ? getPreferredSize().width
                                        : Math.max(definitionPreferredWidth, getMinimumSize().width);
  }

  private int contentsPreferredWidth() {
    int elementsPreferredWidth = new DocumentationPanePreferredWidthProvider(getUI().getRootView(this)).get();
    int preferredContentWidth = getPreferredContentWidth(getDocument().getLength());
    return Math.max(elementsPreferredWidth, preferredContentWidth);
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

  private FontSize myFontSize;

  @Internal
  public void applyFontProps(@NotNull FontSize size) {
    Document document = getDocument();
    if (!(document instanceof StyledDocument)) {
      return;
    }
    String fontName = Registry.is("documentation.component.editor.font")
                      ? EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName()
                      : getFont().getFontName();

    myFontSize = size;

    // changing font will change the doc's CSS as myEditorPane has JEditorPane.HONOR_DISPLAY_PROPERTIES via UIUtil.getHTMLEditorKit
    setFont(UIUtil.getFontWithFallback(fontName, Font.PLAIN, JBUIScale.scale(size.getSize())));
  }

  @Override
  public float getContentsScaleFactor() {
    return myFontSize != null ? ((float)myFontSize.getSize()) / FontSize.SMALL.getSize() : 1f;
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
