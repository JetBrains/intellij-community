// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.GenericListComponentUpdater;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Set;

import static javax.swing.ListSelectionModel.*;
import static javax.swing.SwingConstants.*;

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

  IPopupChooserBuilder<T> setFilterAlwaysVisible(boolean state);

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

  IPopupChooserBuilder<T> setAdText(@PopupAdvertisement String ad,
                                    @MagicConstant(intValues = {LEFT, RIGHT, CENTER, LEADING, TRAILING}) int alignment);

  IPopupChooserBuilder<T> setAdvertiser(@Nullable JComponent advertiser);

  IPopupChooserBuilder<T> setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);

  IPopupChooserBuilder<T> setCancelOnOtherWindowOpen(boolean cancelOnWindow);

  IPopupChooserBuilder<T> setSelectionMode(
    @MagicConstant(intValues = {SINGLE_SELECTION, SINGLE_INTERVAL_SELECTION, MULTIPLE_INTERVAL_SELECTION}) int selection);

  IPopupChooserBuilder<T> setSelectedValue(T preselection, boolean shouldScroll);

  IPopupChooserBuilder<T> setAccessibleName(@Nls String title);

  IPopupChooserBuilder<T> setItemSelectedCallback(Consumer<? super @UnknownNullability T> c);

  IPopupChooserBuilder<T> withHintUpdateSupply();

  IPopupChooserBuilder<T> setFont(Font f);

  IPopupChooserBuilder<T> setVisibleRowCount(int visibleRowCount);

  IPopupChooserBuilder<T> withFixedRendererSize(@NotNull Dimension dimension);

  @NotNull JBPopup createPopup();

  GenericListComponentUpdater<T> getBackgroundUpdater();
}
