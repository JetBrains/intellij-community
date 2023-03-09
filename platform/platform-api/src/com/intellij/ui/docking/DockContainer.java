// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * Implement {@link Activatable} if needed.
 */
public interface DockContainer {
  enum ContentResponse {
    ACCEPT_MOVE, ACCEPT_COPY, DENY;

    public boolean canAccept() {
      return this != DENY;
    }
  }

  @NotNull
  RelativeRectangle getAcceptArea();

  /**
   * This area is used when nothing was found with getAcceptArea
   */
  default @NotNull RelativeRectangle getAcceptAreaFallback() {
    return getAcceptArea();
  }

  @NotNull
  ContentResponse getContentResponse(@NotNull DockableContent<?> content, RelativePoint point);

  @NotNull
  JComponent getContainerComponent();

  void add(@NotNull DockableContent<?> content, @Nullable RelativePoint dropTarget);

  /**
   * Closes all contained editors.
   */
  default void closeAll() {
  }

  default void addListener(@NotNull Listener listener, Disposable parent) {
  }

  boolean isEmpty();

  default @Nullable Image startDropOver(@SuppressWarnings("unused") @NotNull DockableContent<?> content, @SuppressWarnings("unused") RelativePoint point) {
    return null;
  }

  default @Nullable Image processDropOver(@NotNull DockableContent<?> content, RelativePoint point) {
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
    default void contentAdded(@SuppressWarnings("unused") @NotNull Object key) {
    }

    default void contentRemoved(Object key) {
    }
  }
}
