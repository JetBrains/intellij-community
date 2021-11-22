// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.popup.AbstractPopup;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Internal
public final class PopupBridge {

  private AbstractPopup popup;
  private List<Consumer<? super AbstractPopup>> consumers = new ArrayList<>();

  void setPopup(@NotNull AbstractPopup popup) {
    assert this.popup == null;
    this.popup = popup;
    consumers.forEach(c -> c.accept(popup));
    consumers = null;
  }

  @Nullable AbstractPopup getPopup() {
    return popup;
  }

  public void performWhenAvailable(@NotNull Consumer<? super @NotNull AbstractPopup> consumer) {
    if (popup == null) {
      consumers.add(consumer);
    }
    else {
      consumer.accept(popup);
    }
  }

  void performOnCancel(@NotNull Runnable runnable) {
    performWhenAvailable(popup -> popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        runnable.run();
      }
    }));
  }
}
