package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.Computable;
import com.intellij.ui.ActiveComponent;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public interface IPopupChooserBuilder {
  IPopupChooserBuilder setCancelOnClickOutside(boolean cancelOnClickOutside);

  JScrollPane getScrollPane();

  @NotNull
  IPopupChooserBuilder setTitle(@NotNull @Nls String title);

  @NotNull
  IPopupChooserBuilder addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke);

  @NotNull
  IPopupChooserBuilder setItemChoosenCallback(@NotNull Runnable runnable);

  @NotNull
  IPopupChooserBuilder setSouthComponent(@NotNull JComponent cmp);

  @NotNull
  IPopupChooserBuilder setCouldPin(@Nullable Processor<JBPopup> callback);

  @NotNull
  IPopupChooserBuilder setEastComponent(@NotNull JComponent cmp);

  IPopupChooserBuilder setRequestFocus(boolean requestFocus);

  IPopupChooserBuilder setResizable(boolean forceResizable);

  IPopupChooserBuilder setMovable(boolean forceMovable);

  IPopupChooserBuilder setDimensionServiceKey(@NonNls String key);

  IPopupChooserBuilder setUseDimensionServiceForXYLocation(boolean use);

  IPopupChooserBuilder setCancelCallback(Computable<Boolean> callback);

  IPopupChooserBuilder setCommandButton(@NotNull ActiveComponent commandButton);

  IPopupChooserBuilder setAlpha(float alpha);

  IPopupChooserBuilder setAutoselectOnMouseMove(boolean doAutoSelect);

  IPopupChooserBuilder setFilteringEnabled(Function<Object, String> namer);

  IPopupChooserBuilder setModalContext(boolean modalContext);

  @NotNull
  JBPopup createPopup();

  IPopupChooserBuilder setMinSize(Dimension dimension);

  IPopupChooserBuilder registerKeyboardAction(KeyStroke keyStroke, ActionListener actionListener);

  IPopupChooserBuilder setAutoSelectIfEmpty(boolean autoselect);

  IPopupChooserBuilder setCancelKeyEnabled(boolean enabled);

  IPopupChooserBuilder addListener(JBPopupListener listener);

  IPopupChooserBuilder setSettingButton(Component abutton);

  IPopupChooserBuilder setMayBeParent(boolean mayBeParent);

  IPopupChooserBuilder setCloseOnEnter(boolean closeOnEnter);

  @NotNull
  IPopupChooserBuilder setFocusOwners(@NotNull Component[] focusOwners);

  @NotNull
  IPopupChooserBuilder setAdText(String ad);

  IPopupChooserBuilder setAdText(String ad, int alignment);

  IPopupChooserBuilder setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);
}
