// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class TouchbarActionCustomizations {
  private boolean myShowText = false;
  private boolean myShowImage = true;
  private boolean myCrossEsc = false;
  private boolean myPrincipal = false;
  private boolean myDefault = false;
  private JComponent myComponent = null;

  public boolean isShowText() { return myShowText; }

  public boolean isShowImage() { return myShowImage; }

  public boolean isCrossEsc() { return myCrossEsc; }

  public boolean isPrincipal() { return myPrincipal; }

  public boolean isDefault() { return myDefault; }

  public JComponent getComponent() { return myComponent; }

  public TouchbarActionCustomizations setShowText(boolean showText) {
    myShowText = showText;
    return this;
  }

  public TouchbarActionCustomizations setShowImage(boolean showImage) {
    myShowImage = showImage;
    return this;
  }

  public TouchbarActionCustomizations setCrossEsc(boolean crossEsc) {
    myCrossEsc = crossEsc;
    return this;
  }

  public TouchbarActionCustomizations setPrincipal(boolean principal) {
    myPrincipal = principal;
    return this;
  }

  public TouchbarActionCustomizations setDefault(boolean aDefault) {
    myDefault = aDefault;
    return this;
  }

  public TouchbarActionCustomizations setComponent(JComponent component) {
    myComponent = component;
    return this;
  }

  //
  // Static API
  //

  public static @Nullable TouchbarActionCustomizations getCustomizations(@NotNull AnAction action) {
    return getClientProperty(action, false);
  }

  public static @NotNull TouchbarActionCustomizations setShowText(@NotNull AnAction action, boolean showText) {
    return getClientProperty(action, true).setShowText(showText);
  }

  public static @NotNull TouchbarActionCustomizations setDefault(@NotNull AnAction action, boolean isDefault) {
    return getClientProperty(action, true).setDefault(isDefault);
  }

  public static @NotNull TouchbarActionCustomizations setPrincipal(@NotNull AnAction action, boolean principal) {
    return getClientProperty(action, true).setPrincipal(principal);
  }

  public static @NotNull TouchbarActionCustomizations setComponent(@NotNull AnAction action, JComponent contextComponent) {
    return getClientProperty(action, true).setComponent(contextComponent);
  }

  //
  // Private
  //

  private static final Key<TouchbarActionCustomizations> ACTION_CUSTOMIZATIONS_KEY = Key.create("TouchbarActionCustomizations.key");

  private static @Nullable TouchbarActionCustomizations getClientProperty(@NotNull AnAction action, boolean forceCreate) {
    TouchbarActionCustomizations result = action.getTemplatePresentation().getClientProperty(ACTION_CUSTOMIZATIONS_KEY);
    if (forceCreate && result == null) {
      action.getTemplatePresentation().putClientProperty(ACTION_CUSTOMIZATIONS_KEY, result = new TouchbarActionCustomizations());
    }
    return result;
  }
}
