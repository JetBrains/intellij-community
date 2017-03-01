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


public interface ITypedChooserBuilder<T> extends IPopupChooserBuilder {

  ITypedChooserBuilder<T> setRenderer(ListCellRenderer renderer);

  @NotNull
  ITypedChooserBuilder<T> setItemChoosenCallback(@NotNull Consumer<T> callback);

  @NotNull
  ITypedChooserBuilder<T> setItemsChoosenCallback(@NotNull Consumer<Set<T>> callback);

  @NotNull
  @Override
  ITypedChooserBuilder<T> setItemChoosenCallback(@NotNull Runnable runnable);

  @Override
  ITypedChooserBuilder<T> setCancelOnClickOutside(boolean cancelOnClickOutside);

  @Override
  JScrollPane getScrollPane();

  @NotNull
  @Override
  ITypedChooserBuilder<T> setTitle(@NotNull @Nls String title);

  @NotNull
  @Override
  ITypedChooserBuilder<T> addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke);

  @NotNull
  @Override
  ITypedChooserBuilder<T> setSouthComponent(@NotNull JComponent cmp);

  @NotNull
  @Override
  ITypedChooserBuilder<T> setCouldPin(@Nullable Processor<JBPopup> callback);

  @NotNull
  @Override
  ITypedChooserBuilder<T> setEastComponent(@NotNull JComponent cmp);

  @Override
  ITypedChooserBuilder<T> setRequestFocus(boolean requestFocus);

  @Override
  ITypedChooserBuilder<T> setResizable(boolean forceResizable);

  @Override
  ITypedChooserBuilder<T> setMovable(boolean forceMovable);

  @Override
  ITypedChooserBuilder<T> setDimensionServiceKey(@NonNls String key);

  @Override
  ITypedChooserBuilder<T> setUseDimensionServiceForXYLocation(boolean use);

  @Override
  ITypedChooserBuilder<T> setCancelCallback(Computable<Boolean> callback);

  @Override
  ITypedChooserBuilder<T> setCommandButton(@NotNull ActiveComponent commandButton);

  @Override
  ITypedChooserBuilder<T> setAlpha(float alpha);

  @Override
  ITypedChooserBuilder<T> setAutoselectOnMouseMove(boolean doAutoSelect);

  @Override
  ITypedChooserBuilder<T> setFilteringEnabled(Function<Object, String> namer);

  @Override
  ITypedChooserBuilder<T> setModalContext(boolean modalContext);

  @NotNull
  @Override
  JBPopup createPopup();

  @Override
  ITypedChooserBuilder<T> setMinSize(Dimension dimension);

  @Override
  ITypedChooserBuilder<T> registerKeyboardAction(KeyStroke keyStroke, ActionListener actionListener);

  @Override
  ITypedChooserBuilder<T> setAutoSelectIfEmpty(boolean autoselect);

  @Override
  ITypedChooserBuilder<T> setCancelKeyEnabled(boolean enabled);

  @Override
  ITypedChooserBuilder<T> addListener(JBPopupListener listener);

  @Override
  ITypedChooserBuilder<T> setSettingButton(Component abutton);

  @Override
  ITypedChooserBuilder<T> setMayBeParent(boolean mayBeParent);

  @Override
  ITypedChooserBuilder<T> setCloseOnEnter(boolean closeOnEnter);

  @NotNull
  @Override
  ITypedChooserBuilder<T> setFocusOwners(@NotNull Component[] focusOwners);

  @NotNull
  @Override
  ITypedChooserBuilder<T> setAdText(String ad);

  @Override
  ITypedChooserBuilder<T> setAdText(String ad, int alignment);

  @Override
  ITypedChooserBuilder<T> setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);

  ITypedChooserBuilder<T> setSelectionMode(int selection);

  ITypedChooserBuilder<T> setSelectedValue(T preselection, boolean shouldScroll);

  ITypedChooserBuilder<T> setAccessibleName(String title);


  ITypedChooserBuilder<T> setItemSelectedCallback(Consumer<T> c);

  ITypedChooserBuilder<T> withHintUpdateSupply();

  ITypedChooserBuilder<T> setFont(Font f);
}
