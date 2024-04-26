// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.codeInsight.documentation.DocumentationHtmlUtil.*;
import static com.intellij.lang.documentation.ide.ui.UiKt.FORCED_WIDTH;
import static com.intellij.ui.scale.JBUIScale.scale;

@Internal
public final class DocumentationScrollPane extends JBScrollPane {

  public DocumentationScrollPane() {
    super(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    setBorder(JBUI.Borders.empty());
    setViewportBorder(null);
  }

  @Override
  public Dimension getPreferredSize() {
    Integer forcedWidth = UIUtil.getClientProperty(this, FORCED_WIDTH);
    int minWidth = forcedWidth == null ? scale(getDocPopupMinWidth()) : forcedWidth;
    int maxWidth = forcedWidth == null ? scale(getDocPopupMaxWidth()) : forcedWidth;
    return getPreferredSize(minWidth, maxWidth, scale(getDocPopupMaxHeight()));
  }

  public void setViewportView(@NotNull DocumentationEditorPane editorPane,
                              @NotNull JLabel locationLabel) {
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public Dimension getPreferredSize() {
        JBViewport parent = (JBViewport)getParent();
        Dimension minimumSize = editorPane.getMinimumSize();
        Insets insets = getInsets();
        int insetWidth = insets.left + insets.right;
        int width = Math.max(Math.max(minimumSize.width, parent.getWidth() - insetWidth), scale(200));
        Dimension editorPaneSize = editorPane.getPackedSize(width, width);
        Dimension locationLabelSize = locationLabel.isVisible() ? locationLabel.getPreferredSize() : new Dimension();
        return new Dimension(width + insetWidth, editorPaneSize.height + locationLabelSize.height + insets.top + insets.bottom);
      }
    };
    panel.add(editorPane, BorderLayout.CENTER);
    panel.add(locationLabel, BorderLayout.SOUTH);
    panel.setOpaque(true);
    locationLabel.setOpaque(true);
    setViewportView(panel);
  }

  private @NotNull Dimension getPreferredSize(int minWidth, int maxWidth, int maxHeight) {
    Component view = getViewport().getView();
    Dimension paneSize;

    JScrollBar hBar = getHorizontalScrollBar();
    JScrollBar vBar = getVerticalScrollBar();
    int insetWidth = 0;
    if (view instanceof DocumentationEditorPane editorPane) {
      paneSize = editorPane.getPackedSize(minWidth, maxWidth);
    }
    else if (view instanceof JPanel panel) {
      Component[] components = panel.getComponents();
      Insets viewInsets = panel.getInsets();
      insetWidth = viewInsets.left + viewInsets.right;
      Dimension editorPaneSize = ((DocumentationEditorPane)components[0]).getPackedSize(minWidth, maxWidth);
      int locationLabelSizeHeight = components.length > 1 && panel.getComponents()[1].isVisible()
                                    ? panel.getComponents()[1].getPreferredSize().height
                                    : Math.max(JBUI.scale(getContentOuterPadding() - getSpaceAfterParagraph()), 0);
      paneSize = new Dimension(editorPaneSize.width + vBar.getPreferredSize().width + insetWidth,
                               editorPaneSize.height + locationLabelSizeHeight + viewInsets.top + viewInsets.bottom);
    }
    else {
      throw new IllegalStateException(view.getClass().getName());
    }
    boolean hasHBar = paneSize.width - vBar.getPreferredSize().width - insetWidth > maxWidth && hBar.isOpaque();
    int hBarHeight = hasHBar ? hBar.getPreferredSize().height : 0;

    boolean hasVBar = paneSize.height + hBarHeight > maxHeight && vBar.isOpaque();
    int vBarWidth = hasVBar ? vBar.getPreferredSize().width : 0;

    Insets insets = getInsets();
    int preferredWidth = paneSize.width + vBarWidth + insets.left + insets.right;
    int preferredHeight = paneSize.height + hBarHeight + insets.top + insets.bottom;
    return new Dimension(
      Math.min(preferredWidth, maxWidth),
      Math.min(preferredHeight, maxHeight)
    );
  }

  public static @NotNull Map<KeyStroke, ActionListener> keyboardActions(@NotNull JScrollPane target) {
    if (ScreenReader.isActive()) {
      // With screen readers, we want the default keyboard behavior inside
      // the document text editor, i.e. the caret moves with cursor keys, etc.
      return Collections.emptyMap();
    }
    Map<KeyStroke, ActionListener> result = new HashMap<>(10);
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), e -> {
      JScrollBar scrollBar = target.getVerticalScrollBar();
      int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
      value = Math.max(value, 0);
      scrollBar.setValue(value);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), e -> {
      JScrollBar scrollBar = target.getVerticalScrollBar();
      int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
      value = Math.min(value, scrollBar.getMaximum());
      scrollBar.setValue(value);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), e -> {
      JScrollBar scrollBar = target.getHorizontalScrollBar();
      int value = scrollBar.getValue() - scrollBar.getUnitIncrement(-1);
      value = Math.max(value, 0);
      scrollBar.setValue(value);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), e -> {
      JScrollBar scrollBar = target.getHorizontalScrollBar();
      int value = scrollBar.getValue() + scrollBar.getUnitIncrement(+1);
      value = Math.min(value, scrollBar.getMaximum());
      scrollBar.setValue(value);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), e -> {
      JScrollBar scrollBar = target.getVerticalScrollBar();
      int value = scrollBar.getValue() - scrollBar.getBlockIncrement(-1);
      value = Math.max(value, 0);
      scrollBar.setValue(value);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), e -> {
      JScrollBar scrollBar = target.getVerticalScrollBar();
      int value = scrollBar.getValue() + scrollBar.getBlockIncrement(+1);
      value = Math.min(value, scrollBar.getMaximum());
      scrollBar.setValue(value);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), e -> {
      JScrollBar scrollBar = target.getHorizontalScrollBar();
      scrollBar.setValue(0);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), e -> {
      JScrollBar scrollBar = target.getHorizontalScrollBar();
      scrollBar.setValue(scrollBar.getMaximum());
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.CTRL_MASK), e -> {
      JScrollBar scrollBar = target.getVerticalScrollBar();
      scrollBar.setValue(0);
    });
    result.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.CTRL_MASK), e -> {
      JScrollBar scrollBar = target.getVerticalScrollBar();
      scrollBar.setValue(scrollBar.getMaximum());
    });
    return result;
  }
}
