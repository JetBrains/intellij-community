// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.impl.EditorCssFontResolver;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.html.UtilsKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import javax.swing.text.View;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.function.Function;

import static com.intellij.util.ui.html.UtilsKt.getCssMargin;
import static com.intellij.util.ui.html.UtilsKt.getCssPadding;

public class JBHtmlPane extends JEditorPane implements Disposable {

  public record Configuration(
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull Function<? super @NotNull JBHtmlPane, ? extends @Nullable Dictionary<URL, Image>> imageResolverFactory,
    @NotNull Function<? super @NotNull String, ? extends @Nullable Icon> iconResolver,
    @NotNull Function<@NotNull Color, @NotNull List<@NotNull StyleSheet>> additionalStyleSheetProvider,
    @Nullable CSSFontResolver fontResolver,
    @NotNull List<ExtendableHTMLViewFactory.Extension> extensions
  ) {
  }

  public static Configuration defaultConfiguration() {
    return new Configuration(
      Collections.emptyMap(), pane -> null, url -> null, color -> Collections.emptyList(), null, Collections.emptyList()
    );
  }

  private @Nls String myText = ""; // getText() surprisingly crashes…, let's cache the text
  private StyleSheet myCurrentDefaultStyleSheet = null;
  private final MutableStateFlow<Color> editorBackgroundFlow;

  private final @NotNull JBHtmlPaneStyleSheetRulesProvider.Configuration myStylesConfiguration;
  private final @NotNull Configuration myPaneConfiguration;

  public JBHtmlPane(
    @NotNull JBHtmlPaneStyleSheetRulesProvider.Configuration stylesConfiguration,
    @NotNull Configuration paneConfiguration
  ) {
    myStylesConfiguration = stylesConfiguration;
    myPaneConfiguration = paneConfiguration;
    enableEvents(AWTEvent.KEY_EVENT_MASK);
    setEditable(false);
    if (ScreenReader.isActive()) {
      // Note: Making the caret visible is merely for convenience
      getCaret().setVisible(true);
    }
    else {
      putClientProperty("caretWidth", 0); // do not reserve space for caret (making content one pixel narrower than the component)
      UIUtil.doNotScrollToCaret(this);
    }
    editorBackgroundFlow = StateFlowKt.MutableStateFlow(getBackground());
    ArrayList<ExtendableHTMLViewFactory.Extension> extensions = new ArrayList<>(myPaneConfiguration.extensions);
    extensions.addAll(Arrays.asList(
      ExtendableHTMLViewFactory.Extensions.icons(key -> myPaneConfiguration.iconResolver.apply(key)),
      ExtendableHTMLViewFactory.Extensions.BASE64_IMAGES,
      ExtendableHTMLViewFactory.Extensions.INLINE_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.PARAGRAPH_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.LINE_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.BLOCK_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.WBR_SUPPORT
    ));
    if (!myPaneConfiguration.extensions.contains(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_IMAGES)) {
      extensions.add(ExtendableHTMLViewFactory.Extensions.HIDPI_IMAGES);
    }

    HTMLEditorKit editorKit = new HTMLEditorKitBuilder()
      .replaceViewFactoryExtensions(extensions.toArray(ExtendableHTMLViewFactory.Extension[]::new))
      .withViewFactoryExtensions()
      .withFontResolver(myPaneConfiguration.fontResolver != null ? myPaneConfiguration.fontResolver
                                                                 : EditorCssFontResolver.getGlobalInstance())
      .build();
    updateDocumentationPaneDefaultCssRules(editorKit);

    addPropertyChangeListener(evt -> {
      var propertyName = evt.getPropertyName();
      if ("background".equals(propertyName) || "UI".equals(propertyName)) {
        updateDocumentationPaneDefaultCssRules(editorKit);
        editorBackgroundFlow.setValue(getBackground());
      }
    });

    super.setEditorKit(editorKit);
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
    super.setText(t);
  }

  @Override
  public void setEditorKit(EditorKit kit) {
    throw new UnsupportedOperationException("Cannot change EditorKit for JBHtmlPane");
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
    Color background = getBackground();
    myCurrentDefaultStyleSheet.addStyleSheet(JBHtmlPaneStyleSheetRulesProvider.getStyleSheet(background, myStylesConfiguration));
    for (StyleSheet styleSheet : myPaneConfiguration.additionalStyleSheetProvider.apply(background)) {
      myCurrentDefaultStyleSheet.addStyleSheet(styleSheet);
    }
    editorStyleSheet.addStyleSheet(myCurrentDefaultStyleSheet);
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
    ActionListener listener = myPaneConfiguration.keyboardActions.get(keyStroke);
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
      doc.putProperty("imageCache", myPaneConfiguration.imageResolverFactory.apply(this));
    }
  }

  protected int getPreferredSectionWidth(String sectionClassName) {
    View definition = findSection(getUI().getRootView(this), sectionClassName);
    var result = definition == null ? -1 : (int)definition.getPreferredSpan(View.X_AXIS);
    if (result > 0) {
      result += UtilsKt.getWidth(getCssMargin(definition));
      var parent = definition.getParent();
      while (parent != null) {
        result += UtilsKt.getWidth(getCssMargin(parent)) + UtilsKt.getWidth(getCssPadding(parent));
        parent = parent.getParent();
      }
    }
    return result;
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
}
