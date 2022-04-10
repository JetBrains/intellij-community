// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.options.FontSize;
import com.intellij.reference.SoftReference;
import com.intellij.ui.FontSizePopup;
import com.intellij.ui.FontSizePopupData;
import com.intellij.ui.components.JBSlider;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * @deprecated Unused in v2 implementation.
 */
@Deprecated
@ApiStatus.Internal
public final class DocFontSizePopup {

  private static WeakReference<JBSlider> ourCurrentSlider;

  public static void show(@NotNull Component parentComponent, @NotNull Runnable changeCallback) {
    show(parentComponent, size -> changeCallback.run());
  }

  public static void show(@NotNull Component parentComponent, @NotNull Consumer<? super @NotNull FontSize> changeCallback) {
    FontSizePopupData popupData = FontSizePopup.showFontSizePopup(
      parentComponent,
      DocumentationComponent.getQuickDocFontSize(),
      Arrays.asList(FontSize.values()),
      () -> {
        ourCurrentSlider = null;
        return Unit.INSTANCE;
      },
      size -> {
        DocumentationComponent.setQuickDocFontSize(size);
        changeCallback.accept(size);
        return Unit.INSTANCE;
      }
    );
    ourCurrentSlider = new WeakReference<>(popupData.getSlider());
  }

  public static void update(@NotNull FontSize size) {
    JBSlider slider = SoftReference.dereference(ourCurrentSlider);
    if (slider != null && slider.isShowing()) {
      slider.setValueWithoutEvents(size.ordinal());
    }
  }
}
