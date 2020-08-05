// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author pegov
 */
public abstract class MacMessages {
  public interface MacMessageManagerProvider {
    @NotNull MacMessages getMessageManager();
  }

  @Messages.YesNoCancelResult
  public abstract int showYesNoCancelDialog(@NotNull String title,
                                            @NotNull String message,
                                            @NotNull String yesText,
                                            @NotNull String noText,
                                            @NotNull String cancelText,
                                            @Nullable Window window,
                                            @Nullable DialogWrapper.DoNotAskOption doNotAskOption);
  public static @NotNull MacMessages getInstance() {
    return ApplicationManager.getApplication().getService(MacMessageManagerProvider.class).getMessageManager();
  }

  /**
   * Buttons are placed starting near the right side of the alert and going toward the left side
   * (for languages that read left to right). The first three buttons are identified positionally as
   * NSAlertFirstButtonReturn, NSAlertSecondButtonReturn, NSAlertThirdButtonReturn in the return-code parameter evaluated by the modal
   * delegate. Subsequent buttons are identified as NSAlertThirdButtonReturn +n, where n is an integer
   *
   * By default, the first button has a key equivalent of Return,
   * any button with a title of "Cancel" has a key equivalent of Escape,
   * and any button with the title "Don't Save" has a key equivalent of Command-D (but only if it is not the first button).
   *
   * http://developer.apple.com/library/mac/#documentation/Cocoa/Reference/ApplicationKit/Classes/NSAlert_Class/Reference/Reference.html
   *
   * Please, note that Cancel is supposed to be the last button!
   *
   * @return number of button pressed: from 0 up to buttons.length-1 inclusive, or -1 for Cancel
   */
  public abstract int showMessageDialog(@NotNull String title, String message, String @NotNull [] buttons, boolean errorStyle,
                                        @Nullable Window window, int defaultOptionIndex, int focusedOptionIndex,
                                        @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption);

  public abstract void showOkMessageDialog(@NotNull String title, String message, @NotNull String okText, @Nullable Window window);

  public abstract boolean showYesNoDialog(@NotNull String title,
                                          @NotNull String message,
                                          @NotNull String yesText,
                                          @NotNull String noText,
                                          @Nullable Window window,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption);

  public abstract void showErrorDialog(@NotNull String title, String message, @NotNull String okButton, @Nullable Window window);
}