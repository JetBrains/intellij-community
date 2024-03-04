// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.Disposable;
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
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions;
import static com.intellij.util.ui.html.UtilsKt.getCssMargin;
import static com.intellij.util.ui.html.UtilsKt.getCssPadding;

public class JBHtmlPane extends JEditorPane implements Disposable {

  private final Map<KeyStroke, ActionListener> myKeyboardActions;
  private final Function<? super @NotNull JBHtmlPane, ? extends @Nullable Dictionary<URL, Image>> myImageResolverFactory;
  private final Function<@NotNull Color, @NotNull List<@NotNull String>> myAdditionalCssRulesProvider;
  private @Nls String myText = ""; // getText() surprisingly crashes…, let's cache the text
  private StyleSheet myCurrentDefaultStyleSheet = null;
  private final MutableStateFlow<Color> editorBackgroundFlow;

  protected JBHtmlPane(
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull Function<? super @NotNull JBHtmlPane, ? extends @Nullable Dictionary<URL, Image>> imageResolverFactory,
    @NotNull Function<? super @NotNull String, ? extends @Nullable Icon> iconResolver,
    @NotNull Function<@NotNull Color, @NotNull List<@NotNull String>> additionalCssRulesProvider,
    @Nullable CSSFontResolver fontResolver
  ) {
    myKeyboardActions = keyboardActions;
    myImageResolverFactory = imageResolverFactory;
    myAdditionalCssRulesProvider = additionalCssRulesProvider;
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
    editorBackgroundFlow = StateFlowKt.MutableStateFlow(getBackground());
    HTMLEditorKitBuilder builder = new HTMLEditorKitBuilder()
      .replaceViewFactoryExtensions(Extensions.icons(key -> iconResolver.apply(key)),
                                    Extensions.BASE64_IMAGES,
                                    Extensions.INLINE_VIEW_EX,
                                    Extensions.PARAGRAPH_VIEW_EX,
                                    Extensions.LINE_VIEW_EX,
                                    Extensions.BLOCK_VIEW_EX,
                                    Extensions.FIT_TO_WIDTH_IMAGES,
                                    Extensions.WBR_SUPPORT);
    if (fontResolver != null) {
      builder.withFontResolver(fontResolver);
    }
    HTMLEditorKit editorKit = builder.build();
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
    Color background = getBackground();
    for (String rule : myAdditionalCssRulesProvider.apply(background)) {
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
    doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
    if (doc instanceof StyledDocument) {
      doc.putProperty("imageCache", myImageResolverFactory.apply(this));
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
