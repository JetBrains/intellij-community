// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class MergedHorizontalScrollBarModel extends DefaultBoundedRangeModel {
  private volatile boolean myInternalChange;
  private final JScrollBar myBar;
  private final EditorEx myFirstEditor;
  private final EditorEx mySecondEditor;
  private int myFirstValue;
  private int mySecondValue;

  public MergedHorizontalScrollBarModel(@NotNull JScrollBar bar, @NotNull EditorEx first, @NotNull EditorEx second) {
    myBar = bar;
    myFirstEditor = first;
    mySecondEditor = second;
    addChangeListener(event -> onChange());
    first.getScrollPane().getViewport().addChangeListener(event -> onUpdate(event.getSource()));
    second.getScrollPane().getViewport().addChangeListener(event -> onUpdate(event.getSource()));
  }

  private boolean isInternal() {
    return myInternalChange || !myFirstEditor.getComponent().isVisible() || !mySecondEditor.getComponent().isVisible();
  }

  private void onChange() {
    if (isInternal()) return;
    myInternalChange = true;
    setValue(myFirstEditor.getScrollPane().getViewport(), getValue());
    setValue(mySecondEditor.getScrollPane().getViewport(), getValue());
    myInternalChange = false;
  }

  private void onUpdate(Object source) {
    if (isInternal()) return;
    JViewport first = myFirstEditor.getScrollPane().getViewport();
    JViewport second = mySecondEditor.getScrollPane().getViewport();
    int value = getValue();
    if (source == first) {
      Point position = first.getViewPosition();
      if (position.x != myFirstValue) {
        myFirstValue = value = position.x;
      }
    }
    else {
      Point position = second.getViewPosition();
      if (position.x != mySecondValue) {
        mySecondValue = value = position.x;
      }
    }
    int ext = Math.min(first.getExtentSize().width, second.getExtentSize().width);
    int max = Math.max(first.getViewSize().width, second.getViewSize().width);
    setRangeProperties(value, ext, 0, max, false);
    myBar.setEnabled(ext < max);
  }

  private static void setValue(JViewport viewport, int value) {
    Point position = viewport.getViewPosition();
    position.x = Math.max(0, Math.min(value, viewport.getViewSize().width - viewport.getExtentSize().width));
    viewport.setViewPosition(position);
  }

  public void setEnabled(boolean enabled) {
    myFirstEditor.getScrollPane().getHorizontalScrollBar().setEnabled(!enabled);
    mySecondEditor.getScrollPane().getHorizontalScrollBar().setEnabled(!enabled);
    myBar.setVisible(enabled);
  }

  public static @NotNull MergedHorizontalScrollBarModel create(@NotNull JScrollBar bar, @NotNull EditorEx first, @NotNull EditorEx second) {
    MergedHorizontalScrollBarModel model = new MergedHorizontalScrollBarModel(bar, first, second);
    bar.setModel(model);
    model.setEnabled(true);
    return model;
  }
}
