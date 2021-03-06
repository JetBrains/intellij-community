// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ImaginaryScrollingModel implements ScrollingModel {
  private final ImaginaryEditor myEditor;

  public ImaginaryScrollingModel(ImaginaryEditor editor) {
    myEditor = editor;
  }

  private RuntimeException notImplemented() {
    return myEditor.notImplemented();
  }

  @Override
  public @NotNull Rectangle getVisibleArea() {
    return new Rectangle(0, 0);
  }

  @Override
  public @NotNull Rectangle getVisibleAreaOnScrollingFinished() {
    return new Rectangle(0, 0);
  }

  @Override
  public void scrollToCaret(@NotNull ScrollType scrollType) {
    // no-op
  }

  @Override
  public void scrollTo(@NotNull LogicalPosition pos, @NotNull ScrollType scrollType) {
    // no-op
  }

  @Override
  public void runActionOnScrollingFinished(@NotNull Runnable action) {
    action.run();
  }

  @Override
  public void disableAnimation() {
    // no-op
  }

  @Override
  public void enableAnimation() {
    // no-op
  }

  @Override
  public int getVerticalScrollOffset() {
    throw notImplemented();
  }

  @Override
  public int getHorizontalScrollOffset() {
    throw notImplemented();
  }

  @Override
  public void scrollVertically(int scrollOffset) {
    // no-op
  }

  @Override
  public void scrollHorizontally(int scrollOffset) {
    // no-op
  }

  @Override
  public void scroll(int horizontalOffset, int verticalOffset) {
    // no-op
  }

  @Override
  public void addVisibleAreaListener(@NotNull VisibleAreaListener listener) {
    // no-op
  }

  @Override
  public void removeVisibleAreaListener(@NotNull VisibleAreaListener listener) {
    // no-op
  }
}
