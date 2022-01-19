// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.ui.components.JBScrollPane;
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

import static com.intellij.codeInsight.documentation.DocumentationComponent.MAX_DEFAULT;
import static com.intellij.codeInsight.documentation.DocumentationComponent.MIN_DEFAULT;
import static com.intellij.codeInsight.documentation.QuickDocUtil.isDocumentationV2Enabled;
import static com.intellij.lang.documentation.ide.ui.UiKt.FORCED_WIDTH;

@Internal
public final class DocumentationScrollPane extends JBScrollPane {

  public DocumentationScrollPane() {
    super(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    setBorder(JBUI.Borders.empty());
    setViewportBorder(null);
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isDocumentationV2Enabled()) {
      return super.getPreferredSize();
    }
    Integer forcedWidth = UIUtil.getClientProperty(this, FORCED_WIDTH);
    int minWidth = forcedWidth == null ? MIN_DEFAULT.width() : forcedWidth;
    return getPreferredSize(minWidth, MAX_DEFAULT.width(), MAX_DEFAULT.height());
  }

  private @NotNull Dimension getPreferredSize(int minWidth, int maxWidth, int maxHeight) {
    Dimension paneSize = ((DocumentationEditorPane)getViewport().getView()).getPackedSize(minWidth, maxWidth);

    JScrollBar hBar = getHorizontalScrollBar();
    boolean hasHBar = paneSize.width > maxWidth && hBar.isOpaque();
    int hBarHeight = hasHBar ? hBar.getPreferredSize().height : 0;

    JScrollBar vBar = getVerticalScrollBar();
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
