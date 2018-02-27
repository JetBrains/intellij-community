/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.ListComponentUpdater;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.ActiveComponent;
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
  IPopupChooserBuilder<T> setItemChoosenCallback(@NotNull Consumer<T> callback);

  @NotNull
  IPopupChooserBuilder<T> setItemsChoosenCallback(@NotNull Consumer<Set<T>> callback);

  @NotNull
  IPopupChooserBuilder<T> setItemChoosenCallback(@NotNull Runnable runnable);

  IPopupChooserBuilder<T> setCancelOnClickOutside(boolean cancelOnClickOutside);

  JScrollPane getScrollPane();

  @NotNull
  IPopupChooserBuilder<T> setTitle(@NotNull @Nls String title);

  @NotNull
  IPopupChooserBuilder<T> addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke);

  @NotNull
  IPopupChooserBuilder<T> setSouthComponent(@NotNull JComponent cmp);

  @NotNull
  IPopupChooserBuilder<T> setCouldPin(@Nullable Processor<JBPopup> callback);

  @NotNull
  IPopupChooserBuilder<T> setEastComponent(@NotNull JComponent cmp);

  IPopupChooserBuilder<T> setRequestFocus(boolean requestFocus);

  IPopupChooserBuilder<T> setResizable(boolean forceResizable);

  IPopupChooserBuilder<T> setMovable(boolean forceMovable);

  IPopupChooserBuilder<T> setDimensionServiceKey(@NonNls String key);

  IPopupChooserBuilder<T> setUseDimensionServiceForXYLocation(boolean use);

  IPopupChooserBuilder<T> setCancelCallback(Computable<Boolean> callback);

  IPopupChooserBuilder<T> setCommandButton(@NotNull ActiveComponent commandButton);

  IPopupChooserBuilder<T> setAlpha(float alpha);

  IPopupChooserBuilder<T> setAutoselectOnMouseMove(boolean doAutoSelect);

  IPopupChooserBuilder<T> setFilteringEnabled(Function<Object, String> namer);

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
  IPopupChooserBuilder<T> setFocusOwners(@NotNull Component[] focusOwners);

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

  ListComponentUpdater getBackgroundUpdater();
}
