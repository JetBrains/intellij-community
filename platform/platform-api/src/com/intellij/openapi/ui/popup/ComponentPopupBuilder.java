// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

public interface ComponentPopupBuilder {
  @NotNull
  ComponentPopupBuilder setTitle(@NlsContexts.PopupTitle String title);

  @NotNull
  ComponentPopupBuilder setResizable(boolean forceResizable);

  @NotNull
  ComponentPopupBuilder setMovable(boolean forceMovable);

  @NotNull
  ComponentPopupBuilder setRequestFocus(boolean requestFocus);

  @NotNull
  ComponentPopupBuilder setFocusable(boolean focusable);

  @NotNull
  ComponentPopupBuilder setRequestFocusCondition(@NotNull Project project, @NotNull Condition<? super Project> condition);

  /**
   * @see com.intellij.openapi.util.DimensionService
   */
  @NotNull
  ComponentPopupBuilder setDimensionServiceKey(@Nullable Project project, @NonNls String key, boolean useForXYLocation);

  @NotNull
  ComponentPopupBuilder setCancelCallback(@NotNull Computable<Boolean> shouldProceed);

  @NotNull
  ComponentPopupBuilder setCancelOnClickOutside(boolean cancel);

  @NotNull
  ComponentPopupBuilder addListener(@NotNull JBPopupListener listener);

  @NotNull
  ComponentPopupBuilder setCancelOnMouseOutCallback(@NotNull MouseChecker shouldCancel);

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
  ComponentPopupBuilder setFocusOwners(Component @NotNull [] focusOwners);

  /**
   * Adds "advertising" text to the bottom (e.g.: hints in code completion popup).
   */
  @NotNull
  ComponentPopupBuilder setAdText(@Nullable @PopupAdvertisement String text);

  @NotNull
  ComponentPopupBuilder setAdText(@Nullable @PopupAdvertisement String text, int textAlignment);

  @NotNull
  ComponentPopupBuilder setShowShadow(boolean show);

  @NotNull
  ComponentPopupBuilder setCommandButton(@NotNull ActiveComponent commandButton);

  @NotNull
  ComponentPopupBuilder setCouldPin(@Nullable Processor<? super JBPopup> callback);

  @NotNull
  ComponentPopupBuilder setKeyboardActions(@NotNull List<? extends Pair<ActionListener, KeyStroke>> keyboardActions);

  @NotNull
  ComponentPopupBuilder setSettingButtons(@NotNull Component button);

  @NotNull
  ComponentPopupBuilder setMayBeParent(boolean mayBeParent);

  ComponentPopupBuilder setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);

  /**
   * Allows defining custom strategy for processing {@link JBPopup#dispatchKeyEvent(KeyEvent)}.
   */
  @NotNull ComponentPopupBuilder setKeyEventHandler(@NotNull BooleanFunction<? super KeyEvent> handler);

  @NotNull
  ComponentPopupBuilder setShowBorder(boolean show);

  @NotNull
  ComponentPopupBuilder setNormalWindowLevel(boolean b);

  @NotNull
  default ComponentPopupBuilder setBorderColor(Color color) {
    return this;
  }

  /**
   * Set a handler to be called when popup is closed via {@link JBPopup#closeOk(InputEvent)}.
   */
  @NotNull
  ComponentPopupBuilder setOkHandler(@Nullable Runnable okHandler);
}
