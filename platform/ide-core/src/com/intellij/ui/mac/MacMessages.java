// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.ui.MessageConstants.YesNoCancelResult;

/**
 * @author pegov
 */
public abstract class MacMessages {
  public interface MacMessageManagerProvider {
    @NotNull MacMessages getMessageManager();
  }

  @YesNoCancelResult
  public abstract int showYesNoCancelDialog(@NlsContexts.DialogTitle @NotNull String title,
                                            @NlsContexts.DialogMessage @NotNull String message,
                                            @NlsContexts.Button @NotNull String yesText,
                                            @NlsContexts.Button @NotNull String noText,
                                            @NlsContexts.Button @NotNull String cancelText,
                                            @Nullable Window window,
                                            @Nullable DoNotAskOption doNotAskOption,
                                            @Nullable Icon icon,
                                            @Nullable String helpId);
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
  public abstract int showMessageDialog(@NlsContexts.DialogTitle @NotNull String title,
                                        @NlsContexts.DialogMessage String message,
                                        @NlsContexts.Button String @NotNull [] buttons,
                                        @Nullable Window window,
                                        int defaultOptionIndex,
                                        int focusedOptionIndex,
                                        @Nullable DoNotAskOption doNotAskDialogOption,
                                        @Nullable Icon icon,
                                        @Nullable String helpId);

  /**
   * @deprecated Use {@link #showMessageDialog(String, String, String[], Window, int, int, DoNotAskOption, Icon, String)}
   */
  @Deprecated
  public int showMessageDialog(@NlsContexts.DialogTitle @NotNull String title,
                               @NlsContexts.DialogMessage String message,
                               @NlsContexts.Button String @NotNull [] buttons, boolean errorStyle,
                               @Nullable Window window, int defaultOptionIndex, int focusedOptionIndex,
                               @Nullable DoNotAskOption doNotAskDialogOption) {
    return showMessageDialog(title, message, buttons, window, defaultOptionIndex, focusedOptionIndex, doNotAskDialogOption,
                             errorStyle ? UIUtil.getErrorIcon() : UIUtil.getInformationIcon(), null);
  }

  public abstract void showOkMessageDialog(@NlsContexts.DialogTitle @NotNull String title,
                                           @NlsContexts.DialogMessage String message,
                                           @NlsContexts.Button @NotNull String okText, @Nullable Window window);

  public abstract boolean showYesNoDialog(@NlsContexts.DialogTitle @NotNull String title,
                                          @NlsContexts.DialogMessage @NotNull String message,
                                          @NlsContexts.Button @NotNull String yesText,
                                          @NlsContexts.Button @NotNull String noText,
                                          @Nullable Window window,
                                          @Nullable DoNotAskOption doNotAskDialogOption,
                                          @Nullable Icon icon,
                                          @Nullable String helpId);

  public abstract void showErrorDialog(@NlsContexts.DialogTitle @NotNull String title,
                                       @NlsContexts.DialogMessage String message,
                                       @NlsContexts.Button @NotNull String okButton,
                                       @Nullable Window window);
}
