/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ActiveComponent;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author max
 */
public interface ComponentPopupBuilder {
  @NotNull
  ComponentPopupBuilder setTitle(String title);

  @NotNull
  ComponentPopupBuilder setResizable(boolean forceResizable);

  @NotNull
  ComponentPopupBuilder setMovable(boolean forceMovable);

  @NotNull
  ComponentPopupBuilder setRequestFocus(boolean requestFocus);

  @NotNull
  ComponentPopupBuilder setFocusable(boolean focusable);

  @NotNull
  ComponentPopupBuilder setRequestFocusCondition(Project project, Condition<Project> condition);

  /**
   * @see com.intellij.openapi.util.DimensionService
   */
  @NotNull
  ComponentPopupBuilder setDimensionServiceKey(@Nullable Project project, @NonNls String key, boolean useForXYLocation);

  @NotNull
  ComponentPopupBuilder setCancelCallback(Computable<Boolean> shouldProceed);

  @NotNull
  ComponentPopupBuilder setCancelOnClickOutside(boolean cancel);

  @NotNull
  ComponentPopupBuilder addListener(JBPopupListener listener);

  @NotNull
  ComponentPopupBuilder setCancelOnMouseOutCallback(MouseChecker shouldCancel);

  @NotNull
  JBPopup createPopup();

  @NotNull
  ComponentPopupBuilder setCancelButton(@NotNull IconButton cancelButton);

  @NotNull
  ComponentPopupBuilder setCancelOnOtherWindowOpen(boolean cancelOnWindow);

  @NotNull
  ComponentPopupBuilder setTitleIcon(@NotNull ActiveIcon icon);

  @NotNull
  ComponentPopupBuilder setCancelKeyEnabled(boolean enabled);

  @NotNull
  ComponentPopupBuilder setLocateByContent(boolean byContent);

  @NotNull
  ComponentPopupBuilder setLocateWithinScreenBounds(boolean within);

  @NotNull
  ComponentPopupBuilder setMinSize(Dimension minSize);

  /**
   * Use this method to customize shape of popup window (e.g. to use bounded corners).
   */
  @SuppressWarnings("UnusedDeclaration")//used in 'Presentation Assistant' plugin
  @NotNull
  ComponentPopupBuilder setMaskProvider(MaskProvider maskProvider);

  @NotNull
  ComponentPopupBuilder setAlpha(float alpha);

  @NotNull
  ComponentPopupBuilder setBelongsToGlobalPopupStack(boolean isInStack);

  @NotNull
  ComponentPopupBuilder setProject(Project project);

  @NotNull
  ComponentPopupBuilder addUserData(Object object);

  @NotNull
  ComponentPopupBuilder setModalContext(boolean modal);

  @NotNull
  ComponentPopupBuilder setFocusOwners(@NotNull Component[] focusOwners);

  /**
   * Adds "advertising" text to the bottom (e.g.: hints in code completion popup).
   */
  @NotNull
  ComponentPopupBuilder setAdText(@Nullable String text);

  @NotNull
  ComponentPopupBuilder setAdText(@Nullable String text, int textAlignment);

  @NotNull
  ComponentPopupBuilder setShowShadow(boolean show);

  @NotNull
  ComponentPopupBuilder setCommandButton(@NotNull ActiveComponent commandButton);

  @NotNull
  ComponentPopupBuilder setCouldPin(@Nullable Processor<JBPopup> callback);

  @NotNull
  ComponentPopupBuilder setKeyboardActions(@NotNull List<Pair<ActionListener, KeyStroke>> keyboardActions);

  @NotNull
  ComponentPopupBuilder setSettingButtons(@NotNull Component button);

  @NotNull
  ComponentPopupBuilder setMayBeParent(boolean mayBeParent);

  ComponentPopupBuilder setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);

  /**
   * Allows to define custom strategy for processing {@link JBPopup#dispatchKeyEvent(KeyEvent)}.
   */
  @NotNull
  ComponentPopupBuilder setKeyEventHandler(@NotNull BooleanFunction<KeyEvent> handler);

  @NotNull
  ComponentPopupBuilder setShowBorder(boolean show);

  @NotNull
  default ComponentPopupBuilder setBorderColor(Color color) {
    return this;
  }
}
