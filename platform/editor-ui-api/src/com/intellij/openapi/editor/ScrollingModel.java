/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Provides services for getting the visible area of the editor and scrolling the editor.
 *
 * @see Editor#getScrollingModel()
 */
public interface ScrollingModel {
  @NotNull
  Rectangle getVisibleArea();
  @NotNull
  Rectangle getVisibleAreaOnScrollingFinished();

  void scrollToCaret(@NotNull ScrollType scrollType);
  void scrollTo(@NotNull LogicalPosition pos, @NotNull ScrollType scrollType);

  void runActionOnScrollingFinished(@NotNull Runnable action);
  void disableAnimation();
  void enableAnimation();

  int getVerticalScrollOffset();
  int getHorizontalScrollOffset();

  void scrollVertically(int scrollOffset);
  void scrollHorizontally(int scrollOffset);
  void scroll(int horizontalOffset, int verticalOffset);

  void addVisibleAreaListener(@NotNull VisibleAreaListener listener);
  void removeVisibleAreaListener(@NotNull VisibleAreaListener listener);
  default void addVisibleAreaListener(@NotNull VisibleAreaListener listener, @NotNull Disposable disposable) {
    addVisibleAreaListener(listener);
    Disposer.register(disposable, () -> removeVisibleAreaListener(listener));
  }
}
