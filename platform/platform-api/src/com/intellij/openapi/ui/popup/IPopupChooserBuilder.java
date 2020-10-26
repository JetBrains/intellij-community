// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.ListComponentUpdater;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Set;

public interface IPopupChooserBuilder<T> {
  IPopupChooserBuilder<T> setRenderer(ListCellRenderer<? super T> renderer);

  IPopupChooserBuilder<T> setItemChosenCallback(@NotNull Consumer<? super T> callback);

  IPopupChooserBuilder<T> setItemsChosenCallback(@NotNull Consumer<? super Set<? extends T>> callback);

  IPopupChooserBuilder<T> setCancelOnClickOutside(boolean cancelOnClickOutside);

  IPopupChooserBuilder<T> setTitle(@NotNull @NlsContexts.PopupTitle String title);

  IPopupChooserBuilder<T> setCouldPin(@Nullable Processor<? super JBPopup> callback);

  IPopupChooserBuilder<T> setRequestFocus(boolean requestFocus);

  IPopupChooserBuilder<T> setResizable(boolean forceResizable);

  IPopupChooserBuilder<T> setMovable(boolean forceMovable);

  IPopupChooserBuilder<T> setDimensionServiceKey(@NonNls String key);

  IPopupChooserBuilder<T> setUseDimensionServiceForXYLocation(boolean use);

  IPopupChooserBuilder<T> setCancelCallback(Computable<Boolean> callback);

  IPopupChooserBuilder<T> setAlpha(float alpha);

  IPopupChooserBuilder<T> setAutoselectOnMouseMove(boolean doAutoSelect);

  IPopupChooserBuilder<T> setNamerForFiltering(Function<? super T, String> namer);

  IPopupChooserBuilder<T> setAutoPackHeightOnFiltering(boolean autoPackHeightOnFiltering);

  IPopupChooserBuilder<T> setModalContext(boolean modalContext);

  IPopupChooserBuilder<T> setMinSize(Dimension dimension);

  IPopupChooserBuilder<T> registerKeyboardAction(KeyStroke keyStroke, ActionListener actionListener);

  IPopupChooserBuilder<T> setAutoSelectIfEmpty(boolean autoSelect);

  IPopupChooserBuilder<T> setCancelKeyEnabled(boolean enabled);

  IPopupChooserBuilder<T> addListener(JBPopupListener listener);

  IPopupChooserBuilder<T> setSettingButton(Component button);

  IPopupChooserBuilder<T> setMayBeParent(boolean mayBeParent);

  IPopupChooserBuilder<T> setCloseOnEnter(boolean closeOnEnter);

  IPopupChooserBuilder<T> setAdText(@PopupAdvertisement String ad);

  IPopupChooserBuilder<T> setAdText(@PopupAdvertisement String ad, int alignment);

  IPopupChooserBuilder<T> setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);

  IPopupChooserBuilder<T> setSelectionMode(int selection);

  IPopupChooserBuilder<T> setSelectedValue(T preselection, boolean shouldScroll);

  IPopupChooserBuilder<T> setAccessibleName(@Nls String title);

  IPopupChooserBuilder<T> setItemSelectedCallback(Consumer<? super T> c);

  IPopupChooserBuilder<T> withHintUpdateSupply();

  IPopupChooserBuilder<T> setFont(Font f);

  IPopupChooserBuilder<T> setVisibleRowCount(int visibleRowCount);

  @NotNull JBPopup createPopup();

  ListComponentUpdater getBackgroundUpdater();
}
