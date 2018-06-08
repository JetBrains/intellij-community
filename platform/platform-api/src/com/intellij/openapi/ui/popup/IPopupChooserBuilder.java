// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.ListComponentUpdater;
import com.intellij.openapi.util.Computable;
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

  IPopupChooserBuilder<T> setRenderer(ListCellRenderer renderer);

  @NotNull
  IPopupChooserBuilder<T> setItemChosenCallback(@NotNull Consumer<T> callback);

  @NotNull
  IPopupChooserBuilder<T> setItemsChosenCallback(@NotNull Consumer<Set<T>> callback);

  IPopupChooserBuilder<T> setCancelOnClickOutside(boolean cancelOnClickOutside);

  @NotNull
  IPopupChooserBuilder<T> setTitle(@NotNull @Nls String title);

  @NotNull
  IPopupChooserBuilder<T> setCouldPin(@Nullable Processor<JBPopup> callback);

  IPopupChooserBuilder<T> setRequestFocus(boolean requestFocus);

  IPopupChooserBuilder<T> setResizable(boolean forceResizable);

  IPopupChooserBuilder<T> setMovable(boolean forceMovable);

  IPopupChooserBuilder<T> setDimensionServiceKey(@NonNls String key);

  IPopupChooserBuilder<T> setUseDimensionServiceForXYLocation(boolean use);

  IPopupChooserBuilder<T> setCancelCallback(Computable<Boolean> callback);

  IPopupChooserBuilder<T> setAlpha(float alpha);

  IPopupChooserBuilder<T> setAutoselectOnMouseMove(boolean doAutoSelect);

  IPopupChooserBuilder<T> setNamerForFiltering(Function<T, String> namer);

  IPopupChooserBuilder<T> setAutoPackHeightOnFiltering(boolean autoPackHeightOnFiltering);

  IPopupChooserBuilder<T> setModalContext(boolean modalContext);

  @NotNull
  JBPopup createPopup();

  IPopupChooserBuilder<T> setMinSize(Dimension dimension);

  IPopupChooserBuilder<T> registerKeyboardAction(KeyStroke keyStroke, ActionListener actionListener);

  IPopupChooserBuilder<T> setAutoSelectIfEmpty(boolean autoselect);

  IPopupChooserBuilder<T> setCancelKeyEnabled(boolean enabled);

  IPopupChooserBuilder<T> addListener(JBPopupListener listener);

  IPopupChooserBuilder<T> setSettingButton(Component abutton);

  IPopupChooserBuilder<T> setMayBeParent(boolean mayBeParent);

  IPopupChooserBuilder<T> setCloseOnEnter(boolean closeOnEnter);

  @NotNull
  IPopupChooserBuilder<T> setAdText(String ad);

  IPopupChooserBuilder<T> setAdText(String ad, int alignment);

  IPopupChooserBuilder<T> setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);

  IPopupChooserBuilder<T> setSelectionMode(int selection);

  IPopupChooserBuilder<T> setSelectedValue(T preselection, boolean shouldScroll);

  IPopupChooserBuilder<T> setAccessibleName(String title);

  IPopupChooserBuilder<T> setItemSelectedCallback(Consumer<T> c);

  IPopupChooserBuilder<T> withHintUpdateSupply();

  IPopupChooserBuilder<T> setFont(Font f);

  IPopupChooserBuilder<T> setVisibleRowCount(int visibleRowCount);

  ListComponentUpdater getBackgroundUpdater();
}
