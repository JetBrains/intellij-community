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
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SmartList;
import com.intellij.util.ui.ExtendableHTMLViewFactory;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.html.UtilsKt;
import com.intellij.util.ui.html.image.AdaptiveImageView;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.codeInsight.documentation.DocumentationHtmlUtil.*;
import static com.intellij.lang.documentation.DocumentationMarkup.CLASS_BOTTOM;
import static com.intellij.lang.documentation.DocumentationMarkup.CLASS_DEFINITION;
import static com.intellij.lang.documentation.QuickDocHighlightingHelper.getDefaultDocStyleOptions;
import static com.intellij.ui.components.impl.JBHtmlPaneStyleSheetRulesProviderKt.CODE_BLOCK_CLASS;

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
      getDefaultDocStyleOptions(EditorColorsManager.getInstance().getGlobalScheme(), false),
      JBHtmlPaneConfiguration.builder()
        .keyboardActions(keyboardActions)
        .imageResolverFactory(component -> new DocumentationImageProvider(component, imageResolver))
        .iconResolver(name -> iconResolver.apply(name))
        .customStyleSheetProvider(bg -> getDocumentationPaneAdditionalCssRules())
        .extensions(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_ADAPTIVE_IMAGE_EXTENSION)
        //.extensions(ExtendableHTMLViewFactory.Extensions.ADAPTIVE_IMAGE_EXTENSION)
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
    doc.putProperty(AdaptiveImageView.ADAPTIVE_IMAGES_MANAGER_PROPERTY, CachingAdaptiveImageManagerService.getInstance());
    super.setDocument(doc);
    myCachedPreferredSize = null;
  }

  @Override
  public void revalidate() {
    myCachedPreferredSize = null;
    super.revalidate();
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
    int preferredDefinitionWidth = getPreferredSectionsWidth(CLASS_DEFINITION);
    if (preferredDefinitionWidth < 0) {
      return -1;
    }
    int preferredLocationWidth = getPreferredSectionsWidth(CLASS_BOTTOM);
    int preferredCodeBlockWidth = getPreferredSectionsWidth(CODE_BLOCK_CLASS);
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
