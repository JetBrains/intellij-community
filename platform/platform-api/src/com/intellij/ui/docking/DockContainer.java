// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.docking;

import com.intellij.openapi.Disposable;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ui.update.Activatable;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface DockContainer extends Disposable, Activatable {
  enum ContentResponse {ACCEPT_MOVE, ACCEPT_COPY, DENY;
    public boolean canAccept() {
      return this != DENY;
    }
  }

  RelativeRectangle getAcceptArea();

  /**
   * This area is used when nothing was found with getAcceptArea
   */
  RelativeRectangle getAcceptAreaFallback();

  @NotNull
  ContentResponse getContentResponse(@NotNull DockableContent<?> content, RelativePoint point);

  JComponent getContainerComponent();

  void add(@NotNull DockableContent<?> content, RelativePoint dropTarget);

  /**
   * Closes all contained editors.
   */
  default void closeAll() {
  }

  default void addListener(Listener listener, Disposable parent) {
  }

  boolean isEmpty();

  @Nullable
  default Image startDropOver(@SuppressWarnings("unused") @NotNull DockableContent<?> content, @SuppressWarnings("unused") RelativePoint point) {
    return null;
  }

  @Nullable
  default Image processDropOver(@NotNull DockableContent<?> content, RelativePoint point) {
    return null;
  }

  default void resetDropOver(@NotNull DockableContent<?> content) {
  }

  boolean isDisposeWhenEmpty();

  interface Dialog extends DockContainer {}

  interface Persistent extends DockContainer {

    String getDockContainerType();

    Element getState();
  }

  interface Listener {
    void contentAdded(@NotNull Object key);
    void contentRemoved(Object key);

    class Adapter implements Listener {
      @Override
      public void contentAdded(@NotNull Object key) {
      }

      @Override
      public void contentRemoved(Object key) {
      }
    }
  }
}
