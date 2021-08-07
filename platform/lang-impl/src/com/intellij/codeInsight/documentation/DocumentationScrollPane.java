// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class DocumentationScrollPane extends JBScrollPane {

  DocumentationScrollPane() {
    super(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    setBorder(JBUI.Borders.empty());
  }

  @Override
  public Border getViewportBorder() {
    return null;
  }

  static @NotNull Map<KeyStroke, ActionListener> keyboardActions(@NotNull JScrollPane target) {
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
