// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/** @deprecated use {@link com.intellij.openapi.ui.Messages} or {@link com.intellij.openapi.ui.MessageDialogBuilder} */
@Deprecated(forRemoval = true)
@SuppressWarnings({"HardCodedStringLiteral", "unused"})
public class MacMessages {
  private static final MacMessages INSTANCE = new MacMessages();

  public static @NotNull MacMessages getInstance() {
    return INSTANCE;
  }

  public int showMessageDialog(@NotNull String title, String message, String @NotNull [] buttons, boolean errorStyle, @Nullable Window window,
                               int defaultOptionIndex, int focusedOptionIndex, @Nullable DoNotAskOption doNotAskDialogOption) {
    Icon icon = errorStyle ? UIUtil.getErrorIcon() : UIUtil.getInformationIcon();
    return MessagesService.getInstance().showMessageDialog(
      null, window, message, title, buttons, defaultOptionIndex, focusedOptionIndex, icon, doNotAskDialogOption, false, null);
  }

  public void showOkMessageDialog(@NotNull String title, String message, @NotNull String okText, @Nullable Window window) {
    MessagesService.getInstance().showMessageDialog(
      null, window, message, title, new String[]{okText}, 0, -1, UIUtil.getInformationIcon(), null, false, null);
  }

  public void showErrorDialog(@NotNull String title, String message, @NotNull String okButton, @Nullable Window window) {
    MessagesService.getInstance().showMessageDialog(
      null, window, message, title, new String[]{okButton}, 0, -1, UIUtil.getErrorIcon(), null, false, null);
  }
}
