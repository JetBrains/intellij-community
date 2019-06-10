// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 *
 * This tracker helps to avoid visual overlapping when several 'popups' has to be shown simultaneously
 * 'Popups' here are abstract and may have different nature like Balloons (virtual popups in owner's LayeredPane) and AbstractPopups (real heavyweight windows)
 * Non-modal dialogs also could be tracked if need
 *
 * Example of overlapping: completion in editor together with Javadoc on mouse over (or with inspection hint)
 */
@ApiStatus.Experimental
public class ScreenAreaTracker {
  public interface ScreenAreaConsumer extends Disposable {
    @NotNull
    Rectangle getConsumedScreenBounds();

    Component getUnderlyingAreaOwner();
  }

  private static final Collection<ScreenAreaConsumer> ourAreaConsumers = new LinkedHashSet<>();

  public static boolean register(@NotNull ScreenAreaConsumer consumer) {
    if (!Registry.is("ide.use.screen.area.tracker", false)) {
      return true;
    }
    if (!Disposer.isDisposed(consumer) && ourAreaConsumers.add(consumer)) {
      Disposer.register(consumer, new Disposable() {
        @Override
        public void dispose() {
          ourAreaConsumers.remove(consumer);
        }
      });
      return true;
    }
    return false;
  }

  public static boolean canRectangleBeUsed(@NotNull Component parent, @NotNull Rectangle desiredScreenBounds, @Nullable ScreenAreaConsumer excludedConsumer) {
    if (!Registry.is("ide.use.screen.area.tracker", false)) {
      return true;
    }
    Window window = UIUtil.getWindow(parent);
    if (window != null) {
      for (ScreenAreaConsumer consumer : ourAreaConsumers) {
        if (consumer == excludedConsumer) continue;

        if (window == UIUtil.getWindow(consumer.getUnderlyingAreaOwner())) {
          Rectangle area = consumer.getConsumedScreenBounds();
          if (area.intersects(desiredScreenBounds)) {
            return false;
          }
        }
      }
    }
    return true;
  }
}
